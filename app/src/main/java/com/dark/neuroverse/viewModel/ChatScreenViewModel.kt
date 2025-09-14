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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

class ChattingViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ChatScreenViewModel(context) as T
    }
}

class ChatScreenViewModel(context: Context) : ViewModel() {
    //Define State Variables
    private val appContext = context.applicationContext
    private var _messages = MutableStateFlow<List<Message>>(emptyList())
    private val key = MutableStateFlow(getOrCreateHardwareBackedAesKey(BuildConfig.ALIAS))
    private val rootNode = MutableStateFlow(readBrainFile(key.value, context))

    // --- State exposure: keep mutable private, expose immutable
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _chatTitle = MutableStateFlow("")
    val chatTitle: StateFlow<String> = _chatTitle.asStateFlow()

    private val _chatList = MutableStateFlow<List<ChatINFO>>(emptyList())
    val chatList: StateFlow<List<ChatINFO>> = _chatList.asStateFlow()

    // Selected tools/model lists are also observable; keep them consistent
    val toolList: MutableStateFlow<List<Pair<String, List<Tools>>>> = MutableStateFlow(emptyList())
    val selectedTools: MutableStateFlow<Pair<String, Tools>> = MutableStateFlow(Pair("", Tools()))
    val modelList: MutableStateFlow<List<ModelsData>> = MutableStateFlow(emptyList())
    val selectedModel: MutableStateFlow<ModelsData> = MutableStateFlow(ModelsData())
    val _modelLoadingState = MutableStateFlow<ModelManager.LoadState>(ModelManager.LoadState.Idle)
    val modelLoadingState: StateFlow<ModelManager.LoadState> = _modelLoadingState.asStateFlow()
    val chatId = MutableStateFlow("")
    val _isGenerating = MutableStateFlow(false)
    val _generationState = MutableStateFlow(GenerationState.IDLE)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()
    val generationState: StateFlow<GenerationState> = _generationState.asStateFlow()
    val currentRunningToolName = MutableStateFlow("")

    // near your buffers
    private val MAX_THINK_CHARS = 16000

    private var streamingMsgIndex = -1
    private var streamingMsgId = "-1"

    // Throttle gate: post at most every 30–40ms
    @Volatile
    private var lastUiPost = 0L
    private fun shouldPost(now: Long, everyMs: Long = 35L) = (now - lastUiPost) >= everyMs

    init {
        viewModelScope.launch(Dispatchers.IO) {
            // Keys & brain
            key.value = getOrCreateHardwareBackedAesKey(BuildConfig.ALIAS)
            rootNode.value = readBrainFile(key.value, context)
            rootNode.value.printTree()

            val root = rootNode.value.getNodeDirect("root")
            val chatHistory = getDefaultChatHistory(root)
            val validChats = NeuronTree(chatHistory).getAllChildrenRecursive()
                .filter { it.data.content.isNotBlank() }

            if (validChats.isNotEmpty()) {
                val firstChat = validChats.first()
                loadChatById(firstChat.id)
            }

            updateChatList()

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

    fun loadChatById(chatIdToLoad: String) {
        viewModelScope.launch(Dispatchers.IO) {
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
                Log.e("loadChatById", "Failed loading chat $chatIdToLoad", e)
            }
        }
    }

    fun selectModel(model: ModelsData) {
        ModelManager.unLoadModel()
        viewModelScope.launch(Dispatchers.IO) {
            ModelManager.loadModelAwait(
                modelData = model, defaults = ModelManager.ManagerDefaults(
                    systemPrompt = if (selectedTools.value.first.isEmpty()) ModelsList.generalPurposeSystemPrompt
                    else ModelsList.getToolCallSystemPrompt(
                        buildToolsListForPrompt = selectedTools.value.let {
                            it.second.toolName + ":" + it.second.args.entries.joinToString { (k, v) -> "$k:$v" }
                        })
                ), chatTemplate = ModelsList.chatTemplate, forceReload = true
            ) {
                _modelLoadingState.value = it
                selectedModel.value = model
            }
        }
    }

    fun selectTool(tool: Pair<String, Tools>) {
        // ensure new list instance
        selectedTools.value = tool
        ModelManager.setSystemPrompt(
            ModelsList.getToolCallSystemPrompt(
                buildToolsListForPrompt = selectedTools.value.let {
                    it.second.toolName + ":" + it.second.args.entries.joinToString { (k, v) -> "$k:$v" }
                })
        )
    }

    fun unSelectTool() {
        selectedTools.value = Pair("", Tools())
        ModelManager.setSystemPrompt(ModelsList.generalPurposeSystemPrompt)
    }

    // ===================== sendMessage() =====================
    fun sendMessage(input: String, context: Context) {
        _messages.update { it + Message(role = Role.User, text = input) }
        _isGenerating.value = true
        _generationState.value = GenerationState.GENERATING

        // Create streaming placeholder on Main once
        viewModelScope.launch(Dispatchers.Main) {
            val list = _messages.value.toMutableList()
            streamingMsgIndex = list.size
            streamingMsgId = "-1"
            list += Message(
                role = if (selectedTools.value.first.isNotEmpty()) Role.Tool else Role.Assistant,
                text = "",
                id = streamingMsgId,
                tool = if (selectedTools.value.first.isNotEmpty()) {
                    RunningTool(
                        toolName = selectedTools.value.second.toolName, toolPreview = ""
                    )
                } else null
            )
            _messages.value = list

            // All heavy work off the main thread (Neuron already runs on a BG dispatcher,
            // but we also keep our coordination off-main to avoid blocking animations)
            viewModelScope.launch(Dispatchers.Default) {
                streamAndRender(
                    prompt = input,
                    context = context,
                    enableTools = selectedTools.value.first.isNotEmpty()
                )
            }
        }
    }

    // ===================== sendInternalReasoningMessage() =====================
    fun sendInternalReasoningMessage(input: String, context: Context) {
        _isGenerating.value = true
        unSelectTool()

        viewModelScope.launch(Dispatchers.Main) {
            val list = _messages.value.toMutableList()
            streamingMsgIndex = list.size
            streamingMsgId = "-1"
            list += Message(
                role = Role.Assistant, text = "", id = streamingMsgId
            )
            _messages.value = list

            viewModelScope.launch(Dispatchers.Default) {
                streamAndRender(
                    prompt = input,
                    context = context,
                    enableTools = false // internal reasoning never triggers tools
                )
            }
        }
    }

    // ===================== Shared streaming routine =====================
    private suspend fun streamAndRender(prompt: String, context: Context, enableTools: Boolean) {
        // Local buffers for stream assembly
        val visibleSb = StringBuilder()
        val thoughtSb = StringBuilder()
        val rawSb = StringBuilder() // full raw stream for a second pass
        var inThink = false

        // UI throttle + change detection
        var lastPostedVisibleLen = 0
        var lastPostedThoughtLen = 0

        fun postCoalesced() {
            val now = System.nanoTime() / 1_000_000
            if (shouldPost(now)) {
                val visible = visibleSb.toString()
                val thinking = if (thoughtSb.isNotEmpty()) thoughtSb.toString()
                    .takeLast(MAX_THINK_CHARS) else null

                // Skip no-op posts (lengths unchanged)
                val vLen = visible.length
                val tLen = thinking?.length ?: 0
                if (vLen == lastPostedVisibleLen && tLen == lastPostedThoughtLen) return
                lastPostedVisibleLen = vLen
                lastPostedThoughtLen = tLen

                lastUiPost = now
                viewModelScope.launch(Dispatchers.Main.immediate) {
                    val idx = streamingMsgIndex
                    if (idx >= 0 && idx < _messages.value.size) {
                        val cur = _messages.value.toMutableList()
                        val m = cur[idx]
                        cur[idx] = m.copy(text = visible, thought = thinking)
                        _messages.value = cur
                    }
                }
            }
        }

        suspend fun applyFinal(text: String, thought: String?) {
            withContext(Dispatchers.Main.immediate) {
                val idx = streamingMsgIndex
                if (idx >= 0 && idx < _messages.value.size) {
                    val cur = _messages.value.toMutableList()
                    val m = cur[idx]
                    cur[idx] = m.copy(
                        id = UUID.randomUUID().toString(),
                        text = text,
                        thought = thought?.take(6000)
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
                if (final.isNotBlank() || thought != null) return final.ifBlank { "" } to thought
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
            // 🔌 Stream from the model
            val finalRaw = ModelManager.generateStreaming(
                prompt = prompt, onToken = { tok ->
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

                        inThink -> {
                            thoughtSb.append(tok)
                        }

                        lower.contains("<think>") -> {
                            val before = tok.substringBefore("<think>", tok)
                            val after = tok.substringAfter("<think>", tok)
                            visibleSb.append(before)
                            inThink = true
                            thoughtSb.append(after)
                        }

                        else -> visibleSb.append(tok)
                    }
                    postCoalesced()
                })

            // 2nd pass to pick up JSON or missed tags
            var finalText = visibleSb.toString()
            var finalThought = thoughtSb.takeIf { it.isNotEmpty() }?.toString()
            splitReasoning(rawSb.toString()).let { (v, t) ->
                if (v.isNotBlank()) finalText = v
                if (!t.isNullOrBlank()) finalThought = t
            }

            applyFinal(finalText, finalThought)

            if (enableTools) {
                runCatching {
                    val raw = extractPureJson(finalRaw)
                    val obj = runCatching { JSONObject(raw) }.getOrNull()

                    val selectedToolName = selectedTools.value.second.toolName
                    val toolName = obj?.optString("tool")?.takeIf { it.isNotBlank() }
                        ?: selectedToolName.takeIf { it.isNotBlank() }

                    if (toolName != null) {
                        val args = obj?.optJSONObject("args") ?: obj?.optJSONObject("arguments")
                        ?: JSONObject()

                        val payload = JSONObject().apply {
                            put("tool", toolName)
                            put("args", args)
                        }

                        val loaded = PluginManager.runPlugin(
                            context, selectedTools.value.first, payload.toString()
                        )
                        currentRunningToolName.value = toolName

                        // Run tool asynchronously; do not block stream thread
                        viewModelScope.launch(Dispatchers.Default) {
                            runCatching {
                                ToolRunner.run(loaded, context, payload) { data ->
                                    // When tool returns, mark done and optionally summarize
                                    viewModelScope.launch(Dispatchers.Main) {
                                        _generationState.value = GenerationState.DONE
                                    }
                                    // Optional summarization step
                                    viewModelScope.launch(Dispatchers.Default) {
                                        data
                                        ModelManager.setSystemPrompt(ModelsList.generalPurposeSystemPrompt)
                                        sendInternalReasoningMessage(
                                            """
                                        Summarize the following output:
                                        ${'$'}{rawOutput.toString()}
                                        """.trimIndent(), context
                                        )
                                    }
                                    PluginManager.stopPlugin(
                                        PluginManager.currentPlugin.value?.manifest?.name
                                            ?: "Unknown Plugin"
                                    )
                                }
                            }.onFailure { Log.e("ToolCall", "failed", it) }
                        }
                    } else {
                        Log.d(
                            "ToolCall",
                            "No tool call detected or no tool selected — skipping plugin run"
                        )
                    }
                }.onFailure { Log.e("ToolCall", "failed", it) }
            }

            generateTitle()
            updateConversation(context)
            updateChatList()
        } catch (ce: kotlinx.coroutines.CancellationException) {
            // propagate cancel but try to leave UI consistent
            throw ce
        } catch (t: Throwable) {
            Log.e("sendMessage", "stream failed", t)
            // best-effort flush of what we have
            applyFinal(visibleSb.toString().ifBlank { "" }, thoughtSb.toString().ifBlank { null })
        } finally {
            _isGenerating.value = false
            if (_generationState.value != GenerationState.DONE) {
                _generationState.value = GenerationState.IDLE
            }
        }
    }

    fun updateChatList() {
        try {
            _chatList.value = emptyList()
            val chatInfo = mutableListOf<ChatINFO>()
            val root = rootNode.value.getNodeDirect("root")
            val history = getDefaultChatHistory(root)

            NeuronTree(history).getAllChildrenRecursive().forEach { node ->
                if (node.data.content.isNotBlank()) {
                    val title = runCatching {
                        JSONObject(node.data.content).optString("title", "Untitled")
                    }.getOrElse { "Untitled" }

                    chatInfo.add(ChatINFO(node.id, title))
                }
            }

            _chatList.value = chatInfo

        } catch (e: Exception) {
            Log.e("updateChatList", "Failed loading chat titles", e)
        }
    }

    fun newChat() {
        viewModelScope.launch {
            withContext(Dispatchers.Main) {
                _messages.value = emptyList()
                _chatTitle.value = ""
                chatId.value = ""
            }
            ModelManager.stopGeneration()
        }
    }

    fun deleteChatById(id: String, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                rootNode.value.deleteNodeById(id)
                saveTree(rootNode.value, context, BuildConfig.ALIAS)
                updateChatList()

                if (chatId.value == id) {
                    withContext(Dispatchers.Main) {
                        _messages.value = emptyList()
                        _chatTitle.value = ""
                        chatId.value = ""
                    }
                }
            } catch (e: Exception) {
                Log.e("deleteChatById", "Failed to delete chat $id", e)
            }
        }
    }

    fun writeToolPreviewByID(id: String, runningTool: String) {
        val TAG = "writeToolPreviewByID"
        val selectedToolName = selectedTools.value.second.toolName
        Log.d(TAG, "start(id=$id, runningTool=$runningTool, selectedTool=$selectedToolName)")

        viewModelScope.launch(Dispatchers.IO) {
            val t0 = System.nanoTime()
            val beforeSize = _messages.value.size
            try {
                var hits = 0

                _messages.update { list ->
                    list.map { message ->
                        if (message.id == id) {
                            hits++
                            val updated = message.copy(
                                tool = RunningTool(
                                    toolName = selectedToolName, toolPreview = runningTool
                                )
                            )
                            Log.v(
                                TAG,
                                "updated messageId=${message.id} -> tool=$selectedToolName (len=${runningTool.length})"
                            )
                            updated
                        } else message
                    }
                }

                if (hits > 0) {
                    // 🔐 write the updated preview to disk so it survives process death
                    updateConversation(appContext)
                    Log.d(TAG, "persisted preview to tree")
                }

                val durationMs = (System.nanoTime() - t0) / 1_000_000
                val afterSize = _messages.value.size
                if (hits == 0) {
                    Log.w(TAG, "no-op: message id not found (id=$id). size=$beforeSize")
                } else {
                    Log.d(
                        TAG,
                        "done: hits=$hits, size $beforeSize->$afterSize, took=${durationMs}ms, thread=${Thread.currentThread().name}"
                    )
                }
            } catch (ce: CancellationException) {
                Log.w(TAG, "cancelled(id=$id)", ce); throw ce
            } catch (e: Exception) {
                Log.e(TAG, "failed(id=$id)", e)
            }
        }
    }

    fun stopGenerating() {
        ModelManager.stopGeneration().let {
            _isGenerating.value = false
        }
    }

    private fun generateTitle() {
        if (_chatTitle.value.isNotBlank()) return
        val firstUser = _messages.value.firstOrNull { it.role == Role.User }?.text.orEmpty()
        if (firstUser.isBlank()) return
        val title = firstUser.take(48)
        _chatTitle.value = title
    }

    private fun updateConversation(context: Context) {
        try {
            val root = rootNode.value.getNodeDirect("root")
            val history = getDefaultChatHistory(root)
            val tree = NeuronTree(history)

            val currentList = _messages.value // ← take the UI’s current truth
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

            saveTree(rootNode.value, context, BuildConfig.ALIAS)
        } catch (e: Exception) {
            Log.e("updateConversation", "Failed updating chat", e)
        }
    }
}

enum class GenerationState {
    IDLE, GENERATING, DONE
}

