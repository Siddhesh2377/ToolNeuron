package com.dark.neuroverse.viewModel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dark.ai_module.data.ModelsList
import com.dark.ai_module.model.ModelsData
import com.dark.ai_module.workers.ModelManager
import com.dark.neuroverse.BuildConfig
import com.dark.neuroverse.model.ChatINFO
import com.dark.neuroverse.model.Message
import com.dark.neuroverse.model.Role
import com.dark.neuroverse.model.RunningTool
import com.dark.neuroverse.util.extractPureJson
import com.dark.plugins.manager.PluginManager
import com.dark.plugins.model.Tools
import com.dark.plugins.worker.ToolRunner
import com.dark.userdata.addNewChat
import com.dark.userdata.getDefaultChatHistory
import com.dark.userdata.ntds.getOrCreateHardwareBackedAesKey
import com.dark.userdata.ntds.neuron_tree.NeuronTree
import com.dark.userdata.readBrainFile
import com.dark.userdata.saveTree
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

/**
 * Factory
 */
class ChattingViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ChatScreenViewModel(context.applicationContext) as T
    }
}

/**
 * Chat VM – optimized, de-duplicated, and sectioned.
 */
class ChatScreenViewModel(private val appContext: Context) : ViewModel() {

    //region Constants & Logging
    companion object {
        private const val TAG = "ChatVM"
        private const val UI_POST_MS = 35L
        private const val MAX_THINK_CHARS = 16_000
        private const val MAX_THOUGHT_SAVE = 6_000
    }
    //endregion

    enum class DecodeType { NORMAL, REGENERATE }

    data class DecodingEvent(
        val type: DecodeType,
        val chatId: String,
        val modelId: String,
        val startedAtNs: Long,
        val firstTokenAtNs: Long,
        val durationMs: Long
    )

    private val _decodingEvents = MutableSharedFlow<DecodingEvent>(extraBufferCapacity = 8)
    val decodingEvents: SharedFlow<DecodingEvent> = _decodingEvents.asSharedFlow()

    private val _lastDecodingMs = MutableStateFlow<Long?>(null)
    val lastDecodingMs: StateFlow<Long?> = _lastDecodingMs.asStateFlow()

    private fun emitDecodeNow(
        type: DecodeType,
        startedAtNs: Long,
        firstTokenAtNs: Long
    ) {
        val ms = (firstTokenAtNs - startedAtNs) / 1_000_000
        _lastDecodingMs.value = ms
        _decodingEvents.tryEmit(
            DecodingEvent(
                type = type,
                chatId = chatId.value,
                modelId = "${selectedModel.value.id}", // you already compare by id elsewhere
                startedAtNs = startedAtNs,
                firstTokenAtNs = firstTokenAtNs,
                durationMs = ms
            )
        )
    }


    //region Dispatchers
    private val io: CoroutineDispatcher = Dispatchers.IO
    private val cpu: CoroutineDispatcher = Dispatchers.Default
    //endregion

    //region Backing State
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    private val _chatTitle = MutableStateFlow("")
    private val _chatList = MutableStateFlow<List<ChatINFO>>(emptyList())
    private val _modelLoadingState =
        MutableStateFlow<ModelManager.LoadState>(ModelManager.LoadState.Idle)
    private val _isGenerating = MutableStateFlow(false)
    private val _generationState = MutableStateFlow(GenerationState.IDLE)

    private val _isDecoding = MutableStateFlow(false)
    private val _decodingMessageId = MutableStateFlow<String?>(null)




    // Exposed immutable state
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()
    val chatTitle: StateFlow<String> = _chatTitle.asStateFlow()
    val chatList: StateFlow<List<ChatINFO>> = _chatList.asStateFlow()
    val modelLoadingState: StateFlow<ModelManager.LoadState> = _modelLoadingState.asStateFlow()
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()
    val generationState: StateFlow<GenerationState> = _generationState.asStateFlow()
    val isDecoding: StateFlow<Boolean> = _isDecoding.asStateFlow()
    val decodingMessageId: StateFlow<String?> = _decodingMessageId.asStateFlow()

    // Other observable state
    val toolList: MutableStateFlow<List<Pair<String, List<Tools>>>> = MutableStateFlow(emptyList())
    val selectedTools: MutableStateFlow<Pair<String, Tools>> = MutableStateFlow("" to Tools())
    val modelList: MutableStateFlow<List<ModelsData>> = MutableStateFlow(emptyList())
    val selectedModel: MutableStateFlow<ModelsData> = MutableStateFlow(ModelsData())
    val chatId = MutableStateFlow("")
    val currentRunningToolName = MutableStateFlow("")

    // Crypto & brain
    private val key = MutableStateFlow(getOrCreateHardwareBackedAesKey(BuildConfig.ALIAS))
    private val rootNode = MutableStateFlow(readBrainFile(key.value, appContext))
    //endregion

    //region Streaming control
    private var streamingMsgIndex = -1
    @Volatile
    private var lastUiPost = 0L
    private fun shouldPost(nowMs: Long, everyMs: Long = UI_POST_MS) =
        (nowMs - lastUiPost) >= everyMs
    //endregion

    //region Init
    init {
        viewModelScope.launch(io) {
            // Keys & brain
            key.value = getOrCreateHardwareBackedAesKey(BuildConfig.ALIAS)
            rootNode.value = readBrainFile(key.value, appContext)
            rootNode.value.printTree()

            // Load latest chat if present
            val root = rootNode.value.getNodeDirect("root")
            val chatHistory = getDefaultChatHistory(root)
            val validChats = NeuronTree(chatHistory).getAllChildrenRecursive()
                .filter { it.data.content.isNotBlank() }
            if (validChats.isNotEmpty()) {
                val firstChat = validChats.first()
                loadChatById(firstChat.id)
            }
            updateChatList()

            // Load first model eagerly
            ModelManager.getFirstModel()?.let { model ->
                ModelManager.loadModelAwait(
                    modelData = model,
                    defaults = ModelManager.ManagerDefaults(systemPrompt = ModelsList.generalPurposeSystemPrompt),
                    chatTemplate = ModelsList.chatTemplate,
                    forceReload = true
                ) { state ->
                    _modelLoadingState.value = state
                    selectedModel.value = model
                }
            }

            // Load Tools & Models
            toolList.value = PluginManager.toolsList.value
            modelList.value = ModelManager.getAllModels()
        }
    }
    //endregion

    //region Model & Tool Selection
    fun selectModel(model: ModelsData) {
        ModelManager.unLoadModel()
        viewModelScope.launch(io) {
            val sysPrompt = if (selectedTools.value.first.isBlank()) {
                ModelsList.generalPurposeSystemPrompt
            } else {
                ModelsList.toolCallSYSTEMP
            }
            ModelManager.loadModelAwait(
                modelData = model,
                defaults = ModelManager.ManagerDefaults(systemPrompt = sysPrompt),
                chatTemplate = ModelsList.chatTemplate,
                forceReload = true
            ) { state ->
                _modelLoadingState.value = state
                selectedModel.value = model
            }
        }
    }

    fun selectTool(tool: Pair<String, Tools>) {
        selectedTools.value = tool
        ModelManager.setSystemPrompt(ModelsList.toolCallSYSTEMP)
    }

    fun unSelectTool() {
        selectedTools.value = "" to Tools()
        ModelManager.setSystemPrompt(ModelsList.generalPurposeSystemPrompt)
    }
    //endregion

    //region Chat CRUD
    fun loadChatById(chatIdToLoad: String) {
        viewModelScope.launch(io) {
            try {
                val root = rootNode.value.getNodeDirect("root")
                val chatHistory = getDefaultChatHistory(root)
                val node = NeuronTree(chatHistory).getNodeDirect(chatIdToLoad)
                if (node.data.content.isBlank()) return@launch

                val json = JSONObject(node.data.content)
                val title = json.optString("title", "")
                val conversations = Json.decodeFromString<List<Message>>(
                    json.getJSONArray("conversations").toString()
                )

                withContext(Dispatchers.Main) {
                    _chatTitle.value = title
                    _messages.value = conversations
                    chatId.value = chatIdToLoad
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed loading chat $chatIdToLoad", e)
            }
        }
    }

    fun updateChatList() {
        try {
            val chatInfo = mutableListOf<ChatINFO>()
            val root = rootNode.value.getNodeDirect("root")
            val history = getDefaultChatHistory(root)

            NeuronTree(history).getAllChildrenRecursive().forEach { node ->
                if (node.data.content.isNotBlank()) {
                    val title = runCatching {
                        JSONObject(node.data.content).optString(
                            "title",
                            "Untitled"
                        )
                    }.getOrElse { "Untitled" }
                    chatInfo.add(ChatINFO(node.id, title))
                }
            }
            _chatList.value = chatInfo
        } catch (e: Exception) {
            Log.e(TAG, "Failed loading chat titles", e)
        }
    }

    fun newChat() {
        viewModelScope.launch(Dispatchers.Main) {
            _messages.value = emptyList()
            _chatTitle.value = ""
            chatId.value = ""
            ModelManager.stopGeneration()
        }
    }

    fun deleteChatById(id: String) {
        viewModelScope.launch(io) {
            try {
                rootNode.value.deleteNodeById(id)
                saveTree(rootNode.value, appContext, BuildConfig.ALIAS)
                updateChatList()

                if (chatId.value == id) {
                    withContext(Dispatchers.Main) {
                        _messages.value = emptyList()
                        _chatTitle.value = ""
                        chatId.value = ""
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete chat $id", e)
            }
        }
    }
    //endregion

    //region Sending & Streaming
    fun sendMessage(input: String) {
        _messages.update { it + Message(role = Role.User, text = input) }
        _isGenerating.value = true
        _isDecoding.value = true
        _decodingMessageId.value = "-1"

        _generationState.value = GenerationState.GENERATING

        // Create streaming placeholder on Main
        viewModelScope.launch(Dispatchers.Main) {
            val list = _messages.value.toMutableList()
            streamingMsgIndex = list.size
            val isTool = selectedTools.value.first.isNotBlank()
            list += Message(
                role = if (isTool) Role.Tool else Role.Assistant,
                text = "",
                id = "-1",
                tool = if (isTool) RunningTool(
                    toolName = selectedTools.value.second.toolName,
                    toolPreview = ""
                ) else null
            )
            _messages.value = list

            // Heavy work off main
            viewModelScope.launch(cpu) {
                streamAndRender(prompt = input, enableTools = isTool)
            }
        }
    }

    fun sendInternalReasoningMessage(input: String) {
        _isGenerating.value = true
        unSelectTool()

        viewModelScope.launch(Dispatchers.Main) {
            val list = _messages.value.toMutableList()
            streamingMsgIndex = list.size
            list += Message(role = Role.Assistant, text = "", id = "-1")
            _messages.value = list

            viewModelScope.launch(cpu) {
                streamAndRender(prompt = input, enableTools = false)
            }
        }
    }

    private suspend fun streamAndRender(prompt: String, enableTools: Boolean) {
        // Local buffers
        val visibleSb = StringBuilder()
        val thoughtSb = StringBuilder()
        val rawSb = StringBuilder()
        var inThink = false
        val decodeStartNs = System.nanoTime()
        var firstTokenSeen = false


        // Change detection
        var lastPostedVisibleLen = 0
        var lastPostedThoughtLen = 0

        fun coalescedPost() {
            val now = System.nanoTime() / 1_000_000
            if (!shouldPost(now)) return

            val visible = visibleSb.toString()
            val thinking =
                if (thoughtSb.isNotEmpty()) thoughtSb.toString().takeLast(MAX_THINK_CHARS) else null

            val vLen = visible.length
            val tLen = thinking?.length ?: 0
            if (vLen == lastPostedVisibleLen && tLen == lastPostedThoughtLen) return
            lastPostedVisibleLen = vLen
            lastPostedThoughtLen = tLen
            lastUiPost = now

            viewModelScope.launch(Dispatchers.Main.immediate) {
                val idx = streamingMsgIndex
                if (idx in _messages.value.indices) {
                    val cur = _messages.value.toMutableList()
                    val m = cur[idx]
                    cur[idx] = m.copy(text = visible, thought = thinking)
                    _messages.value = cur
                }
            }
        }

        suspend fun applyFinal(text: String, thought: String?) {
            withContext(Dispatchers.Main.immediate) {
                val idx = streamingMsgIndex
                if (idx in _messages.value.indices) {
                    val cur = _messages.value.toMutableList()
                    val m = cur[idx]
                    cur[idx] = m.copy(
                        id = UUID.randomUUID().toString(),
                        text = text,
                        thought = thought?.take(MAX_THOUGHT_SAVE)
                    )
                    _messages.value = cur
                }
            }
        }

        fun splitReasoning(raw: String): Pair<String, String?> {
            // 1) JSON {"final": "...", "thought": "..."}
            runCatching {
                val json = extractPureJson(raw)
                val obj = JSONObject(json)
                val final = obj.optString("final", obj.optString("answer", ""))
                val thought = obj.optString("thought", obj.optString("reasoning", null))
                if (final.isNotBlank() || thought != null) return final to thought
            }
            // 2) <think>…</think>
            val tagRegex = Regex("(?is)<think>(.*?)</think>")
            val thoughtTag = tagRegex.find(raw)?.groupValues?.getOrNull(1)
            val visible = raw.replace(tagRegex, "").trim()
            if (thoughtTag != null) return visible to thoughtTag
            // 3) Reasoning: … Answer: …
            val delim =
                Regex("(?is)(?:reasoning|thoughts?)\\s*:\\s*(.+?)\\s*(?:final|answer)\\s*:\\s*(.+)")
            delim.find(raw)?.let { m ->
                val t = m.groupValues[1].trim()
                val v = m.groupValues[2].trim()
                return v to t
            }
            return raw to null
        }

        try {
            ModelManager.generateStreaming(
                prompt = prompt,
                toolJson = if (enableTools) convertToolsToJson(selectedTools.value.second).toString() else null,
                onToken = { tok ->
                    if (!firstTokenSeen) {
                        firstTokenSeen = true
                        _isDecoding.value = false
                        _decodingMessageId.value = null
                        emitDecodeNow(
                            type = DecodeType.NORMAL,
                            startedAtNs = decodeStartNs,
                            firstTokenAtNs = System.nanoTime()
                        )
                    }

                    rawSb.append(tok)
                    val lower = tok.lowercase()
                    when {
                        inThink && lower.contains("</think>") -> {
                            val before = tok.substringBefore("</think>", tok)
                            val after = tok.substringAfter("</think>", tok)
                            thoughtSb.append(before)
                            inThink = false
                            visibleSb.append(after)
                        }

                        inThink -> thoughtSb.append(tok)
                        lower.contains("<think>") -> {
                            val before = tok.substringBefore("<think>", tok)
                            val after = tok.substringAfter("<think>", tok)
                            visibleSb.append(before)
                            inThink = true
                            thoughtSb.append(after)
                        }

                        else -> visibleSb.append(tok)
                    }
                    coalescedPost()
                },
                onToolCalled = { nativeName: String, argsJson: String ->
                    Log.d(TAG, "Tool called: $nativeName args=$argsJson")

                    fun isSchemaEcho(obj: JSONObject?): Boolean {
                        if (obj == null) return true
                        return obj.has("type") || obj.has("properties") || obj.has("required")
                    }

                    val lastUserQuery =
                        messages.value.lastOrNull { it.role == Role.User }?.text?.trim().orEmpty()
                    val selectedToolRealName = selectedTools.value.second.toolName.orEmpty()
                    val fallbackTool = nativeName.ifBlank { selectedToolRealName }

                    val repaired = try {
                        val root = JSONObject(argsJson)                 // may throw if malformed
                        val calls = root.optJSONArray("tool_calls")
                        val first = calls?.optJSONObject(0)
                        val toolName = first?.optString("name").orEmpty().ifBlank { fallbackTool }
                        val argObj = first?.optJSONObject("arguments")

                        if (isSchemaEcho(argObj)) {
                            // Build minimal args the plugin understands; here we assume `query`
                            JSONObject().put("tool", toolName)
                                .put("args", JSONObject().put("query", lastUserQuery))
                        } else {
                            JSONObject().put("tool", toolName).put("args", argObj)
                        }
                    } catch (_: Throwable) {
                        // argsJson malformed → keep the native/fallback tool name, synthesize args
                        JSONObject().put("tool", fallbackTool)
                            .put("args", JSONObject().put("query", lastUserQuery))
                    }

                    Log.d(TAG, "Repaired tool call: $repaired")

                    // Run through plugin
                    val loaded = PluginManager.runPlugin(
                        appContext,
                        selectedTools.value.first,
                        repaired.toString()
                    )
                    currentRunningToolName.value = repaired.optString("tool")

                    viewModelScope.launch(io) {
                        ToolRunner.run(loaded, appContext, repaired) { result ->
                            viewModelScope.launch(Dispatchers.Main) {
                                _generationState.value = GenerationState.DONE
                            }
                            viewModelScope.launch(cpu) {
                                ModelManager.setSystemPrompt("You are a crisp summarizer. Be concise, factual.")
                                val rawOutput = result.toString()
                                if (rawOutput.isNotBlank()) {
                                    sendInternalReasoningMessage(
                                        "Summarize the tool output in 5–6 tight lines. Preserve entities, numbers, urls.\n$rawOutput"
                                    )
                                }
                                PluginManager.stopPlugin(
                                    PluginManager.currentPlugin.value?.manifest?.name
                                        ?: "Unknown Plugin"
                                )
                            }
                        }
                    }
                }

            )

            // 2nd pass to pick up JSON or missed tags
            var finalText = visibleSb.toString()
            var finalThought = thoughtSb.takeIf { it.isNotEmpty() }?.toString()
            splitReasoning(rawSb.toString()).let { (v, t) ->
                if (v.isNotBlank()) finalText = v
                if (!t.isNullOrBlank()) finalThought = t
            }

            applyFinal(finalText, finalThought)
            generateTitle()
            updateConversation()
            updateChatList()
        } catch (ce: CancellationException) {
            Log.w(TAG, "stream cancelled", ce)
            throw ce
        } catch (t: Throwable) {
            Log.e(TAG, "stream failed", t)
            // best-effort flush
            applyFinal(visibleSb.toString(), thoughtSb.toString().ifBlank { null })
        } finally {
            _isGenerating.value = false
            _isDecoding.value = false
            _decodingMessageId.value = null
            if (_generationState.value != GenerationState.DONE) _generationState.value =
                GenerationState.IDLE
        }
    }
    //endregion

    //region Tool Preview
    fun writeToolPreviewByID(id: String, runningTool: String) {
        val selectedToolName = selectedTools.value.second.toolName
        Log.d(TAG, "writePreview(id=$id, tool=$selectedToolName, bytes=${runningTool.length})")

        viewModelScope.launch(io) {
            val beforeSize = _messages.value.size
            var hits = 0
            try {
                _messages.update { list ->
                    list.map { message ->
                        if (message.id == id) {
                            hits++
                            message.copy(
                                tool = RunningTool(
                                    toolName = selectedToolName,
                                    toolPreview = runningTool
                                )
                            )
                        } else message
                    }
                }
                if (hits > 0) {
                    updateConversation() // persist preview to disk
                } else {
                    Log.w(TAG, "no-op: message id not found (id=$id) size=$beforeSize")
                }
            } catch (ce: CancellationException) {
                Log.w(TAG, "writePreview cancelled", ce)
                throw ce
            } catch (e: Exception) {
                Log.e(TAG, "writePreview failed", e)
            }
        }
    }
    //endregion

    //region Controls
    fun stopGenerating() {
        ModelManager.stopGeneration()
        _isGenerating.value = false
    }
    //endregion

    //region Persistence & Title
    private fun generateTitle() {
        if (_chatTitle.value.isNotBlank()) return
        val firstUser = _messages.value.firstOrNull { it.role == Role.User }?.text.orEmpty().trim()
        if (firstUser.isBlank()) return
        _chatTitle.value = firstUser.take(48)
    }

    private fun updateConversation() {
        try {
            val root = rootNode.value.getNodeDirect("root")
            val history = getDefaultChatHistory(root)
            val tree = NeuronTree(history)

            val currentList = _messages.value
            val jsonData = JSONObject().apply {
                put("title", _chatTitle.value)
                put("conversations", JSONArray(Json.encodeToString(currentList)))
            }

            val existing = chatId.value.takeIf { it.isNotBlank() }?.let { id ->
                runCatching { tree.getNodeDirect(id) }.getOrNull()
            }

            if (existing != null) {
                existing.data.content = jsonData.toString()
            } else {
                val newNode = addNewChat(history, jsonData)
                chatId.value = newNode.id
            }

            saveTree(rootNode.value, appContext, BuildConfig.ALIAS)
        } catch (e: Exception) {
            Log.e(TAG, "Failed updating chat", e)
        }
    }
    //endregion

    //region Tools → JSON Schema
    private fun convertToolsToJson(tools: Tools): JSONArray {
        val properties = JSONObject()
        val required = mutableListOf<String>()
        tools.args.forEach { (k, v) ->
            properties.put(
                k, JSONObject().put(
                    "type", when (v) {
                        is Int, is Double, is Float -> "number"; is Boolean -> "boolean"; else -> "string"
                    }
                )
            )
            if (v != null) required.add(k)
        }

        val parameters = JSONObject().put("type", "object").put("properties", properties)
            .put("required", JSONArray(required))

        val function =
            JSONObject().put("name", tools.toolName)                      // e.g., "searchWeb"
                .put("description", tools.description ?: "").put("parameters", parameters)

        return JSONArray().put(
            JSONObject().put("type", "function").put("function", function)
        )
    }

    //Regenerate Response
    // Drop-in replacement for the method inside ChatScreenViewModel
    // Regenerates an existing assistant message *in place* with streaming UI updates,
    // optional model switch, and safe handling of <think> blocks.
    fun regenerateResponse(modelsData: ModelsData?, messageId: String) {
        if (modelsData == null) return

        val idx = _messages.value.indexOfFirst { it.id == messageId }
        if (idx == -1) return

        val target = _messages.value[idx]

        // Only regenerate Assistant/Tool replies; ignore user messages
        if (target.role != Role.Assistant && target.role != Role.Tool) return

        _isGenerating.value = true
        _generationState.value = GenerationState.GENERATING

        _isDecoding.value = true
        _decodingMessageId.value = messageId

        val decodeStartNs = System.nanoTime()
        var firstTokenSeen = false


        // Prepare context: most recent user message *before* this assistant reply
        val userCtx = _messages.value.take(idx).lastOrNull { it.role == Role.User }?.text.orEmpty()

        // Empty out the visible text for streaming replacement (keep same message id)
        viewModelScope.launch(Dispatchers.Main) {
            val cur = _messages.value.toMutableList()
            cur[idx] = cur[idx].copy(text = "", thought = null)
            _messages.value = cur
        }

        viewModelScope.launch(io) {
            // Load/switch model if needed + set a focused system prompt
            val sys = "You are a Message Optimization Assistant. Improve clarity, concision, and factuality without altering meaning. Keep code, numbers, and URLs intact. Output only the final improved message in the same language."

            // If model differs, (re)load it; otherwise just update system prompt
            if (selectedModel.value.id != modelsData.id) {
                ModelManager.unLoadModel()
                ModelManager.loadModelAwait(
                    modelData = modelsData,
                    defaults = ModelManager.ManagerDefaults(systemPrompt = sys),
                    chatTemplate = ModelsList.chatTemplate,
                    forceReload = true
                ) { state ->
                    _modelLoadingState.value = state
                    selectedModel.value = modelsData
                }
            } else {
                ModelManager.setSystemPrompt(sys)
            }

            // Streaming buffers
            val visibleSb = StringBuilder()
            val thoughtSb = StringBuilder()
            val rawSb = StringBuilder()
            var inThink = false
            var lastPostedVisibleLen = 0
            var lastPostedThoughtLen = 0

            fun coalescedPost() {
                val now = System.nanoTime() / 1_000_000
                if (!shouldPost(now)) return
                val visible = visibleSb.toString()
                val thinking = if (thoughtSb.isNotEmpty()) thoughtSb.toString().takeLast(MAX_THINK_CHARS) else null
                val vLen = visible.length
                val tLen = thinking?.length ?: 0
                if (vLen == lastPostedVisibleLen && tLen == lastPostedThoughtLen) return
                lastPostedVisibleLen = vLen
                lastPostedThoughtLen = tLen
                lastUiPost = now

                viewModelScope.launch(Dispatchers.Main.immediate) {
                    if (idx in _messages.value.indices) {
                        val cur = _messages.value.toMutableList()
                        val existing = cur[idx]
                        cur[idx] = existing.copy(text = visible, thought = thinking)
                        _messages.value = cur
                    }
                }
            }

            suspend fun applyFinal(text: String, thought: String?) {
                withContext(Dispatchers.Main.immediate) {
                    if (idx in _messages.value.indices) {
                        val cur = _messages.value.toMutableList()
                        val existing = cur[idx]
                        cur[idx] = existing.copy(text = text, thought = thought?.take(MAX_THOUGHT_SAVE))
                        _messages.value = cur
                    }
                }
            }

            fun splitReasoning(raw: String): Pair<String, String?> {
                // 1) JSON {"final": "...", "thought": "..."}
                runCatching {
                    val json = extractPureJson(raw)
                    val obj = JSONObject(json)
                    val final = obj.optString("final", obj.optString("answer", ""))
                    val thought = obj.optString("thought", obj.optString("reasoning", null))
                    if (final.isNotBlank() || thought != null) return final to thought
                }
                // 2) <think>…</think>
                val tagRegex = Regex("(?is)<think>(.*?)</think>")
                val thoughtTag = tagRegex.find(raw)?.groupValues?.getOrNull(1)
                val visible = raw.replace(tagRegex, "").trim()
                if (thoughtTag != null) return visible to thoughtTag
                // 3) Reasoning: … Answer: …
                val delim = Regex("(?is)(?:reasoning|thoughts?)\\s*:\\s*(.+?)\\s*(?:final|answer)\\s*:\\s*(.+)")
                delim.find(raw)?.let { m ->
                    val t = m.groupValues[1].trim()
                    val v = m.groupValues[2].trim()
                    return v to t
                }
                return raw to null
            }

            try {
                // Instruction for regeneration; uses user context and the original assistant text
                val optimizePrompt = buildString {
                    appendLine("Improve the following assistant reply for the user's last message.")
                    appendLine("- Preserve meaning, facts, numbers, code, and URLs.")
                    appendLine("- Be tighter, clearer, and well-structured.")
                    appendLine("- Do not add meta commentary or disclaimers.")
                    appendLine()
                    if (userCtx.isNotBlank()) {
                        appendLine("[User message for context]")
                        appendLine(userCtx)
                        appendLine()
                    }
                    appendLine("[Original assistant reply to improve]")
                    append(target.text)
                }

                ModelManager.generateStreaming(
                    prompt = optimizePrompt,
                    gen = ModelManager.GenerationParams(),
                    toolJson = null,
                    onToolCalled = { _, _ -> },
                    onToken = { tok ->
                        if (!firstTokenSeen) {
                            firstTokenSeen = true
                            _isDecoding.value = false
                            _decodingMessageId.value = null
                            emitDecodeNow(
                                type = DecodeType.REGENERATE,
                                startedAtNs = decodeStartNs,
                                firstTokenAtNs = System.nanoTime()
                            )
                        }

                        rawSb.append(tok)
                        val lower = tok.lowercase()
                        when {
                            inThink && lower.contains("</think>") -> {
                                val before = tok.substringBefore("</think>", tok)
                                val after = tok.substringAfter("</think>", tok)
                                thoughtSb.append(before)
                                inThink = false
                                visibleSb.append(after)
                            }
                            inThink -> thoughtSb.append(tok)
                            lower.contains("<think>") -> {
                                val before = tok.substringBefore("<think>", tok)
                                val after = tok.substringAfter("<think>", tok)
                                visibleSb.append(before)
                                inThink = true
                                thoughtSb.append(after)
                            }
                            else -> visibleSb.append(tok)
                        }
                        coalescedPost()
                    }
                )

                var finalText = visibleSb.toString()
                var finalThought = thoughtSb.takeIf { it.isNotEmpty() }?.toString()
                splitReasoning(rawSb.toString()).let { (v, t) ->
                    if (v.isNotBlank()) finalText = v
                    if (!t.isNullOrBlank()) finalThought = t
                }

                applyFinal(finalText, finalThought)
                updateConversation()
            } catch (ce: CancellationException) {
                Log.w(TAG, "regenerate cancelled", ce)
                throw ce
            } catch (t: Throwable) {
                Log.e(TAG, "regenerate failed", t)
                // best-effort flush of whatever we have
                applyFinal(visibleSb.toString(), thoughtSb.toString().ifBlank { null })
            } finally {
                _isGenerating.value = false
                _isDecoding.value = false
                _decodingMessageId.value = null

                if (_generationState.value != GenerationState.DONE) _generationState.value = GenerationState.IDLE
            }
        }
    }


    //endregion
}

enum class GenerationState { IDLE, GENERATING, DONE }