package com.dark.neuroverse.viewModel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dark.ai_module.ai.Neuron
import com.dark.ai_module.data.ModelsList
import com.dark.ai_module.model.ModelsData
import com.dark.ai_module.workers.ModelManager
import com.dark.neuroverse.BuildConfig
import com.dark.neuroverse.model.ChatINFO
import com.dark.neuroverse.model.Message
import com.dark.neuroverse.model.Role
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
import javax.crypto.SecretKey

class ChattingViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ChatScreenViewModel(context) as T
    }
}

class ChatScreenViewModel(context: Context) : ViewModel() {
    //Define State Variables
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
    val selectedTools: MutableStateFlow<List<Tools>> = MutableStateFlow(emptyList())
    val modelList: MutableStateFlow<List<ModelsData>> = MutableStateFlow(emptyList())
    val chatId = MutableStateFlow("")
    val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val streamBuffer = StringBuilder()
    private var streamingMsgIndex = -1
    private var streamingMsgId    = "-1"

    // Throttle gate: post at most every 30–40ms
    @Volatile private var lastUiPost = 0L
    private fun shouldPost(now: Long, everyMs: Long = 35L) = (now - lastUiPost) >= everyMs

    init {
        viewModelScope.launch(Dispatchers.IO) {
            // Keys & brain
            key.value = getOrCreateHardwareBackedAesKey(BuildConfig.ALIAS)
            rootNode.value = readBrainFile(key.value, context)

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
                ModelManager.loadModel(
                    modelData = model,
                    defaults = ModelManager.ManagerDefaults(systemPrompt = ModelsList.generalPurposeSystemPrompt),
                    chatTemplate = ModelsList.chatTemplate,
                    forceReload = true
                ) { Log.d("Model", "Model loaded successfully $model") }
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
            ModelManager.loadModel(
                modelData = model,
                defaults = ModelManager.ManagerDefaults(
                    systemPrompt = if (selectedTools.value.isEmpty())
                        ModelsList.generalPurposeSystemPrompt
                    else
                        ModelsList.getToolCallSystemPrompt(
                            buildToolsListForPrompt = selectedTools.value.joinToString {
                                it.toolName + ":" + it.args.entries.joinToString { (k, v) -> "$k:$v" }
                            }
                        )
                ),
                chatTemplate = ModelsList.chatTemplate,
                forceReload = true
            ) {
                Log.d("Model", "Model loaded successfully ${model.modeName}")
            }
        }
    }

    fun selectTool(tool: Tools) {
        // ensure new list instance
        selectedTools.update { it + tool }
        Neuron.setSystemPrompt(
            ModelsList.getToolCallSystemPrompt(
                buildToolsListForPrompt = selectedTools.value.joinToString {
                    it.toolName + ":" + it.args.entries.joinToString { (k, v) -> "$k:$v" }
                }
            )
        )
    }

    fun sendMessage(input: String, context: Context) {
        _messages.update { it + Message(role = Role.User, text = input) }
        _isGenerating.value = true

        viewModelScope.launch(Dispatchers.Main) {
            // 1) Create streaming placeholder exactly once
            val list = _messages.value.toMutableList()
            streamingMsgIndex = list.size
            streamingMsgId = "-1"
            list += Message(
                role = Role.Assistant,
                text = "",
                id   = streamingMsgId,
                viaPlugin = if (selectedTools.value.isNotEmpty())
                    selectedTools.value.joinToString { t -> t.toolName } else ""
            )
            _messages.value = list

            // 2) Start generation on IO, but push tokens via a throttled updater
            withContext(Dispatchers.IO) {
                streamBuffer.setLength(0)

                val final = Neuron.generateStreaming(
                    prompt = input,
                    onToken = { tok ->
                        streamBuffer.append(tok)
                        val now = System.nanoTime() / 1_000_000
                        if (shouldPost(now)) {
                            lastUiPost = now
                            // Post a coalesced chunk to Main
                            viewModelScope.launch(Dispatchers.Main.immediate) {
                                if (streamingMsgIndex >= 0) {
                                    val cur = _messages.value.toMutableList()
                                    // mutate only the one item
                                    val m = cur[streamingMsgIndex]
                                    cur[streamingMsgIndex] = m.copy(text = streamBuffer.toString())
                                    _messages.value = cur
                                }
                            }
                        }
                    }
                )

                // Final apply (ensures last few tokens render)
                withContext(Dispatchers.Main.immediate) {
                    if (streamingMsgIndex >= 0) {
                        val cur = _messages.value.toMutableList()
                        val m = cur[streamingMsgIndex]
                        cur[streamingMsgIndex] = m.copy(
                            id = UUID.randomUUID().toString(),
                            text = streamBuffer.toString()
                        )
                        _messages.value = cur
                    }
                }

                // Tool call (if any)
                if (selectedTools.value.isNotEmpty()) {
                    runCatching {
                        val json = extractPureJson(final)
                        val loaded = PluginManager.runPlugin(context, "Web-Searching", json)
                        ToolRunner.run(loaded, context, JSONObject(json))
                    }.onFailure { Log.e("ToolCall", "failed", it) }
                }

                generateTitle()
                updateConversation(context)
                _isGenerating.value = false
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
            Neuron.stopGeneration()
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


    fun stopGenerating() {
        Neuron.stopGeneration().let {
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