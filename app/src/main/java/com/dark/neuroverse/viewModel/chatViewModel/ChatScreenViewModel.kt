package com.dark.neuroverse.viewModel.chatViewModel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dark.ai_module.data.ModelsList
import com.dark.ai_module.model.LoadState
import com.dark.ai_module.model.ModelData
import com.dark.ai_module.workers.ModelManager
import com.dark.ai_module.workers.ModelManager.service
import com.dark.neuroverse.BuildConfig
import com.dark.neuroverse.model.ChatINFO
import com.dark.neuroverse.model.ChatUiState
import com.dark.neuroverse.model.DecodeType
import com.dark.neuroverse.model.DecodingMetrics
import com.dark.neuroverse.model.Message
import com.dark.neuroverse.model.Role
import com.dark.neuroverse.model.RunningTool
import com.dark.neuroverse.model.StreamingState
import com.dark.neuroverse.model.ToolOutput
import com.dark.neuroverse.userdata.addNewChat
import com.dark.neuroverse.userdata.getDefaultChatHistory
import com.dark.neuroverse.userdata.helpers.MemoryDataTags
import com.dark.neuroverse.userdata.helpers.ModelStateHelper
import com.dark.neuroverse.userdata.helpers.StateStatistics
import com.dark.neuroverse.userdata.helpers.getMemoryByTags
import com.dark.neuroverse.userdata.helpers.updateMemory
import com.dark.neuroverse.userdata.ntds.getOrCreateHardwareBackedAesKey
import com.dark.neuroverse.userdata.ntds.neuron_tree.NeuronTree
import com.dark.neuroverse.userdata.readBrainFile
import com.dark.neuroverse.userdata.saveTree
import com.dark.neuroverse.util.extractPureJson
import com.dark.neuroverse.util.writeToolOutputJson
import com.dark.plugins.model.Tools
import com.mp.ai_core.NativeLib
import com.mp.data_hub_lib.manager.DataHubManager
import com.mp.data_hub_lib.model.BrainDoc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

class ChattingViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ChatScreenViewModel(context.applicationContext) as T
    }
}

class ChatScreenViewModel(private val appContext: Context) : ViewModel() {

    companion object {
        private const val TAG = "ChatVM"
        private const val UI_UPDATE_THROTTLE_MS = 35L
        private const val MAX_THINK_DISPLAY_CHARS = 16_000
        private const val MAX_THOUGHT_SAVE_CHARS = 6_000
        private const val SAVE_DEBOUNCE_MS = 1000L
        private const val MAX_CONCURRENT_OPERATIONS = 3
        private const val BATCH_INTERVAL_MS = 300L // 300ms batch interval
    }

    // Coroutine Management
    private val operationSemaphore = Semaphore(MAX_CONCURRENT_OPERATIONS)
    private var currentGenerationJob: Job? = null
    var currentStreamingState: StreamingState? = null
    private var batchingJob: Job? = null

    // Debounced save channel
    private val saveChannel = Channel<Unit>(Channel.CONFLATED)

    //region Core State
    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Idle)
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _chatTitle = MutableStateFlow("")
    val chatTitle: StateFlow<String> = _chatTitle.asStateFlow()

    private val _chatList = MutableStateFlow<List<ChatINFO>>(emptyList())
    val chatList: StateFlow<List<ChatINFO>> = _chatList.asStateFlow()

    private val _modelLoadingState = MutableStateFlow<LoadState>(LoadState.Idle)
    val modelLoadingState: StateFlow<LoadState> = _modelLoadingState.asStateFlow()

    private val _isModelLoading = MutableStateFlow(false)
    val isModelLoading: StateFlow<Boolean> = _isModelLoading.asStateFlow()

    private val _decodingMetrics = MutableSharedFlow<DecodingMetrics>(extraBufferCapacity = 8)
    val decodingMetrics: SharedFlow<DecodingMetrics> = _decodingMetrics.asSharedFlow()

    private val _lastDecodingMs = MutableStateFlow<Long?>(null)
    val lastDecodingMs: StateFlow<Long?> = _lastDecodingMs.asStateFlow()

    private val _currentMsgId = MutableStateFlow<String?>(null)
    val currentMsgId: StateFlow<String?> = _currentMsgId.asStateFlow()
    //endregion

    //region Configuration State
    val modelList: MutableStateFlow<List<ModelData>> = MutableStateFlow(emptyList())
    val selectedModel: MutableStateFlow<ModelData> = MutableStateFlow(ModelData())
    val chatId = MutableStateFlow("")
    val currentRunningToolName = MutableStateFlow("")
    private val _isRag = MutableStateFlow(false)

    // Crypto & Storage
    private val encryptionKey = MutableStateFlow(getOrCreateHardwareBackedAesKey(BuildConfig.ALIAS))
    private val rootNode = MutableStateFlow(readBrainFile(encryptionKey.value, appContext))
    //endregion

    //region Streaming State
    private var streamingMessageIndex = -1

    @Volatile
    private var lastUiUpdateMs = 0L

    private fun shouldUpdateUi(nowMs: Long): Boolean =
        (nowMs - lastUiUpdateMs) >= UI_UPDATE_THROTTLE_MS
    //endregion

    init {
        setupDebouncedSave()
        initializeViewModel()
    }

    @OptIn(FlowPreview::class)
    private fun setupDebouncedSave() {
        viewModelScope.launch {
            saveChannel.consumeAsFlow().debounce(SAVE_DEBOUNCE_MS).collect {
                performSave()
            }
        }
    }

    private fun initializeViewModel() {
        viewModelScope.launch {
            try {
                _uiState.value = ChatUiState.Loading("Initializing...")

                // Initialize crypto and brain
                encryptionKey.value = getOrCreateHardwareBackedAesKey(BuildConfig.ALIAS)
                rootNode.value = readBrainFile(encryptionKey.value, appContext)

                // Load chat history
                loadInitialChat()
                refreshChatListFromDisk()

                //models
                ToolCallingManager.initViewModel()
                modelList.value = ModelManager.getAllModels()

                _uiState.value = ChatUiState.Idle
            } catch (e: Exception) {
                handleError("Initialization failed", e)
            }
        }
    }

    private fun loadInitialChat() {
        val root = rootNode.value.getNodeDirect("root")
        val chatHistory = getDefaultChatHistory(root)
        val validChats = NeuronTree(chatHistory).getAllChildrenRecursive()
            .filter { it.data.content.isNotBlank() }

        if (validChats.isNotEmpty()) {
            loadChatById(validChats.first().id)
        }
    }

    //region Public API - Model & Tool Selection
    fun selectModel(model: ModelData) {
        viewModelScope.launch {
            if (_uiState.value is ChatUiState.Generating) {
                Log.w(TAG, "Cannot change model during generation")
                return@launch
            }

            if (selectedModel.value.id == model.id) {
                Log.w(TAG, "Unselecting model")
                ModelManager.unloadModel()
                selectedModel.value = ModelData()
                return@launch
            }

            toggleModelLoading(true)
            try {
                ModelManager.unloadModel()

                val systemPrompt = if (!ToolCallingManager.isToolSelected()) {
                    Log.d(TAG, "Tool calling selected")
                    ModelsList.defaultSystemPrompt
                } else {
                    ModelsList.toolCallingSystemPrompt
                }

                ModelManager.loadModelAwait(
                    modelData = model.copy(
                        chatTemplate = ModelsList.defaultChatTemplate, systemPrompt = systemPrompt
                    ),
                ) { state ->
                    _modelLoadingState.value = state
                    selectedModel.value = model
                    val root = rootNode.value.getNodeDirect("root")
                    if (ModelStateHelper.hasModelState(root, chatId.value)) {
                        val stateInfo = ModelStateHelper.getModelState(root, chatId.value)
                        if (stateInfo != null) {
                            val success = service?.loadStateFile(stateInfo.stateFilePath)
                            if (success == true) {
                                Log.i(TAG, "Model state loaded: ${stateInfo.size / 1024} KB")
                            } else {
                                Log.w(TAG, "Failed to load model state")
                            }
                        }
                    } else {
                        Log.d(TAG, "No saved model state for chat: $chatId.value")
                    }
                }

                toggleModelLoading(false)
            } catch (e: Exception) {
                handleError("Model selection failed", e)
                toggleModelLoading(false)
            }
        }
    }

    private fun toggleModelLoading(isLoading: Boolean) {
        _isModelLoading.value = isLoading
        if (isLoading) {
            _uiState.value = ChatUiState.Loading("Loading model...")
        } else {
            _uiState.value = ChatUiState.Idle
        }
    }

    fun setRag(enabled: Boolean) {
        _isRag.value = enabled
        Log.d(TAG, "RAG set to: $enabled")
    }

    fun selectTool(tool: Pair<String, Tools>) {
        ToolCallingManager.selectTool(tool)
    }

    fun unSelectTool() {
        ToolCallingManager.unSelectTool()
    }
    //endregion

    //region Public API - Chat Management
    fun loadChatById(chatIdToLoad: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Loading chat $chatIdToLoad")
                _uiState.value = ChatUiState.Loading("Loading chat...")

                val root = rootNode.value.getNodeDirect("root")
                val chatHistory = getDefaultChatHistory(root)
                val node = NeuronTree(chatHistory).getNodeDirect(chatIdToLoad)

                if (node.data.content.isBlank()) {
                    handleError("Chat not found or empty")
                    return@launch
                }

                val json = JSONObject(node.data.content)
                val title = json.optString("title", "")
                val conversations = Json.decodeFromString<List<Message>>(
                    json.getJSONArray("conversations").toString()
                )

                _chatTitle.value = title
                _messages.value = conversations
                chatId.value = chatIdToLoad

                _uiState.value = ChatUiState.Idle
            } catch (e: Exception) {
                handleError("Failed loading chat $chatIdToLoad", e)
            }
        }
    }

    private fun refreshChatListFromDisk() {
        try {
            val tree = readBrainFile(encryptionKey.value, appContext)
            val history = getDefaultChatHistory(tree.root)
            val freshList = NeuronTree(history).getAllChildrenRecursive()
                .filter { it.data.content.isNotBlank() }.map { node ->
                    val json = JSONObject(node.data.content)
                    val title = json.optString("title", "Untitled")
                    ChatINFO(node.id, title)
                }

            _chatList.value = freshList
        } catch (e: Exception) {
            handleError("Failed to refresh chat list", e)
        }
    }

    fun newChat() {
        if (_uiState.value is ChatUiState.Generating) {
            stopGenerating()
        }

        _messages.value = emptyList()
        _chatTitle.value = ""
        chatId.value = ""
        _uiState.value = ChatUiState.Idle
    }

    fun addMessageInMemory(memory: List<BrainDoc>, tag: MemoryDataTags) {
        viewModelScope.launch {
            try {
                val tempRawMemoryData = getMemoryByTags(rootNode.value.root, tag)?.data?.content
                val oldMemory: MutableList<BrainDoc> = mutableListOf()

                tempRawMemoryData?.let { content ->
                    val jsonObj = JSONObject(content)
                    if (jsonObj.has("messages")) {
                        val arr = jsonObj.getJSONArray("messages").toString()
                        oldMemory.addAll(Json.decodeFromString(arr))
                    }
                }

                oldMemory.addAll(memory)

                val outData = Json.encodeToString(oldMemory)
                val json = JSONObject().apply {
                    put("messages", JSONArray(outData))
                }

                updateMemory(rootNode.value.root, tag, json)
                performTreeSave()
                Log.d(TAG, "Memory updated: $json")
            } catch (e: Exception) {
                handleError("Failed to add message in memory", e)
            }
        }
    }

    fun deleteChatById(id: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Deleting chat $id")

                ModelStateHelper.deleteModelState(rootNode.value.root, id)


                rootNode.value.deleteNodeById(id)
                performTreeSave()
                rootNode.value = readBrainFile(encryptionKey.value, appContext)
                refreshChatListFromDisk()

                if (chatId.value == id) {
                    newChat()
                }

                Log.d(TAG, "Chat $id deleted successfully")
            } catch (e: Exception) {
                handleError("Failed to delete chat $id", e)
            }
        }
    }
    //endregion

    //region Public API - Message Sending
    fun sendMessage(input: String) {
        if (_uiState.value is ChatUiState.Generating) {
            Log.w(TAG, "Already generating, ignoring new message")
            return
        }

        // Cancel any existing generation
        currentGenerationJob?.cancel()

        currentGenerationJob = viewModelScope.launch {
            operationSemaphore.withPermit {
                try {
                    executeSendMessage(input, _messages.value)
                } catch (e: CancellationException) {
                    Log.d(TAG, "Message sending cancelled")
                    _uiState.value = ChatUiState.Idle
                } catch (e: Exception) {
                    handleError("Error in sendMessage", e)
                }
            }
        }
    }

    suspend fun continueGenerating(message: Message){
        try {
            val root = rootNode.value.getNodeDirect("root")
            if (ModelStateHelper.hasModelState(root, chatId.value)) {
                val stateInfo = ModelStateHelper.getModelState(root, chatId.value)
                if (stateInfo != null) {
                    val success = service?.loadStateFile(stateInfo.stateFilePath)
                    if (success == true) {
                        Log.i(TAG, "Model state loaded: ${stateInfo.size / 1024} KB")
                    } else {
                        Log.w(TAG, "Failed to load model state")
                    }
                }
            } else {
                Log.d(TAG, "No saved model state for chat: $chatId.value")
            }
        }catch (e: Exception) {
            Log.e(TAG, "Error loading model state", e)
        }

        streamAndRender(
            prompt = message.text,
            enableTools = message.tool != null,
            messageId = message.id,
            existingMessages = messages.value
        )
    }

    private suspend fun executeSendMessage(input: String, currentMessages: List<Message>) {
        _messages.update { it + Message(role = Role.User, text = input) }

        val messageId = UUID.randomUUID().toString()
        _uiState.value = ChatUiState.Generating(messageId)

        val isTool = ToolCallingManager.isToolSelected()
        val assistantMessage = Message(
            role = if (isTool) Role.Tool else Role.Assistant,
            text = "",
            id = messageId,
            tool = if (isTool) RunningTool(
                toolName = ToolCallingManager.getSelectedTool().toolName,
                toolPreview = "",
                toolOutput = ToolOutput()
            ) else null
        )

        _messages.update { it + assistantMessage }
        streamingMessageIndex = _messages.value.size - 1

        if (_isRag.value) {
            handleRAGRequest(input, isTool, messageId, currentMessages)
            viewModelScope.launch {
                val result = RAGManager.initRAG()
                if (result.has("error")) {
                    handleError(result.getString("error"))
                    streamAndRender(
                        prompt = input,
                        enableTools = isTool,
                        messageId = messageId,
                        existingMessages = currentMessages
                    )
                }
            }
        } else {
            streamAndRender(
                prompt = input,
                enableTools = isTool,
                messageId = messageId,
                existingMessages = currentMessages
            )
        }
    }

    fun regenerateResponse(model: ModelData?, messageId: String) {
        if (model == null) return

        val messageIndex = _messages.value.indexOfFirst { it.id == messageId }
        if (messageIndex == -1) return

        val targetMessage = _messages.value[messageIndex]
        if (targetMessage.role != Role.Assistant && targetMessage.role != Role.Tool) return

        currentGenerationJob?.cancel()

        currentGenerationJob = viewModelScope.launch {
            operationSemaphore.withPermit {
                try {
                    executeRegenerate(
                        model, messageId, messageIndex, targetMessage, _messages.value
                    )
                } catch (e: CancellationException) {
                    Log.d(TAG, "Regeneration cancelled")
                    _uiState.value = ChatUiState.Idle
                } catch (e: Exception) {
                    handleError("Regeneration failed", e)
                }
            }
        }
    }

    fun deleteMessage(messageId: String) {
        _messages.update { messages ->
            messages.filterNot { it.id == messageId }
        }
        requestSave()
        saveModelState()
    }

    private suspend fun executeRegenerate(
        model: ModelData,
        messageId: String,
        messageIndex: Int,
        targetMessage: Message,
        currentMessages: List<Message>
    ) {
        _uiState.value = ChatUiState.Generating(messageId)

        val userContext =
            currentMessages.take(messageIndex).lastOrNull { it.role == Role.User }?.text.orEmpty()

        _messages.update { messages ->
            messages.toMutableList().apply {
                set(messageIndex, targetMessage.copy(text = "", thought = null))
            }
        }
        streamingMessageIndex = messageIndex

        if (selectedModel.value.id != model.id) {
            _uiState.value = ChatUiState.Loading("Switching model...")
            ModelManager.unloadModel()
            ModelManager.loadModelAwait(
                modelData = model.copy(
                    systemPrompt = """
                        You are a helpful assistant that improves message clarity and accuracy.
                    """.trimIndent(), chatTemplate = ModelsList.toolCallingChatTemplate
                )
            ) { state ->
                _modelLoadingState.value = state
                selectedModel.value = model
                val root = rootNode.value.getNodeDirect("root")
                if (ModelStateHelper.hasModelState(root, chatId.value)) {
                    val stateInfo = ModelStateHelper.getModelState(root, chatId.value)
                    if (stateInfo != null) {
                        val success = service?.loadStateFile(stateInfo.stateFilePath)
                        if (success == true) {
                            Log.i(TAG, "Model state loaded: ${stateInfo.size / 1024} KB")
                        } else {
                            Log.w(TAG, "Failed to load model state")
                        }
                    }
                } else {
                    Log.d(TAG, "No saved model state for chat: $chatId.value")
                }
            }
        }

        val optimizePrompt = buildOptimizePrompt(userContext, targetMessage.text)
        streamAndRender(
            prompt = optimizePrompt,
            enableTools = false,
            messageId = messageId,
            isRegeneration = true,
            existingMessages = currentMessages
        )
    }

    private fun buildOptimizePrompt(userContext: String, originalText: String): String =
        buildString {
            appendLine("Improve the following assistant reply for better clarity and accuracy.")
            appendLine("- Preserve meaning, facts, numbers, code, and URLs.")
            appendLine("- Make it more concise and well-structured.")
            appendLine("- Do not add meta commentary.")
            appendLine()
            if (userContext.isNotBlank()) {
                appendLine("[User context]")
                appendLine(userContext)
                appendLine()
            }
            appendLine("[Original reply to improve]")
            append(originalText)
        }
    //endregion

    //region RAG Processing
    private suspend fun handleRAGRequest(
        input: String, isTool: Boolean, messageId: String, currentMessages: List<Message>
    ) {
        val currentDataset = DataHubManager.currentDataSet.value
        if (currentDataset == null) {
            Log.d(TAG, "No dataset loaded, fallback to normal generation")
            streamAndRender(input, isTool, messageId, false, currentMessages)
            return
        }

        try {
            _uiState.value = ChatUiState.Loading("Processing with RAG...")

            val ragResult = RAGManager.handleRAGRequest(input)

            if (ragResult.has("error")) {
                handleError(ragResult.getString("error"))
                streamAndRender(input, isTool, messageId, false, currentMessages)
                return
            }

            val finalPrompt = ragResult.getString("success")

            // Simply call it directly - no callback needed
            val result = ensureGenerationModelReady()

            if (result.has("error")) {
                return
            }
            // Then call streamAndRender after the model is ready
            streamAndRender(finalPrompt, isTool, messageId, false, currentMessages)

        } catch (e: Exception) {
            handleError("RAG processing failed", e)
            streamAndRender(input, isTool, messageId, false, currentMessages)
        }
    }

    private suspend fun ensureGenerationModelReady(): JSONObject = withContext(Dispatchers.IO) {
        return@withContext try {
            val currentModel = selectedModel.value
            val generationLib = NativeLib.getGenerationInstance()
            generationLib.nativeRelease()

            delay(500)

            // Call loadModelAwait directly - it already returns Result<Unit>
            val result = ModelManager.loadModelAwait(currentModel) { loadState ->
                when (loadState) {
                    is LoadState.OnLoaded -> {
                        Log.d(TAG, "Generation model ready: ${loadState.model.modelName}")
                    }

                    is LoadState.Error -> {
                        Log.e(TAG, "Generation model error: ${loadState.message}")
                    }

                    else -> Log.d(TAG, "Generation model loading: $loadState")
                }
            }
            if (result.isSuccess) {
                JSONObject().put("success", "Model loaded successfully")
            } else {
                JSONObject().put(
                    "error", "Generation model load failed: ${result.exceptionOrNull()?.message}"
                )
            }
        } catch (e: Exception) {
            handleError("Error ensuring generation model ready", e)
            JSONObject().put("error", "Error ensuring generation model ready $e")
        }
    }

    //endregion

    //region Streaming & Rendering
    private suspend fun streamAndRender(
        prompt: String,
        enableTools: Boolean,
        messageId: String,
        isRegeneration: Boolean = false,
        existingMessages: List<Message>
    ) {
        val startTimeNs = System.nanoTime()
        var firstTokenReceived = false
        _currentMsgId.value = messageId

        _uiState.value = ChatUiState.DecodingStream(messageId, startTimeNs)

        currentStreamingState = StreamingState(messageId = messageId)

        startBatchedUIUpdates(messageId)

        fun finalizeMessage(text: String, thought: String?) {
            batchingJob?.cancel()

            val finalThought = thought?.take(MAX_THOUGHT_SAVE_CHARS)
            updateStreamingMessage(messageId, text, finalThought, isFinal = true)

            if (finalThought == null) generateTitleIfNeeded()
            else generateTitleFromUserMessage(prompt)
            requestSave()
            refreshChatListFromDisk()

            currentStreamingState = null
        }

        try {
            val fullPrompt = buildFullPrompt(prompt, existingMessages)
            ModelManager.generateStreaming(
                prompt = fullPrompt,
                toolJson = if (enableTools) convertToolsToJson(ToolCallingManager.getSelectedTool()).toString() else "",
                onToken = { token ->
                    if (!firstTokenReceived) {
                        firstTokenReceived = true
                        val firstTokenTimeNs = System.nanoTime()
                        emitDecodingMetrics(
                            type = if (isRegeneration) DecodeType.REGENERATE else DecodeType.NORMAL,
                            startTimeNs = startTimeNs,
                            firstTokenTimeNs = firstTokenTimeNs,
                            messageId = messageId
                        )
                        _uiState.value = ChatUiState.Generating(messageId, isFirstToken = true)
                    }

                    currentStreamingState?.let { state ->
                        addTokenToBatch(token, state)
                    }
                },
                onToolCalled = { toolName, argsJson ->
                    handleToolExecution(toolName, argsJson, messageId)
                })

            currentStreamingState?.let { state ->
                val (finalText, finalThought) = processReasoningPatterns(
                    state.rawBuffer.toString(),
                    state.visibleBuffer.toString(),
                    state.thoughtBuffer.toString()
                )
                finalizeMessage(finalText, finalThought)
            }

        } catch (e: CancellationException) {
            Log.d(TAG, "Streaming cancelled")
            batchingJob?.cancel()
            currentStreamingState?.let { state ->
                finalizeMessage(
                    state.visibleBuffer.toString(), state.thoughtBuffer.toString().ifBlank { null })
            }
            throw e
        } catch (e: Exception) {
            handleError("Streaming failed", e)
            batchingJob?.cancel()
            currentStreamingState?.let { state ->
                finalizeMessage(
                    state.visibleBuffer.toString(), state.thoughtBuffer.toString().ifBlank { null })
            }
        } finally {
            refreshChatListFromDisk()
            _currentMsgId.value = ""
            if (_uiState.value !is ChatUiState.ExecutingTool) {
                _uiState.value = ChatUiState.Idle
            }
        }
    }

    private fun buildFullPrompt(prompt: String, existingMessages: List<Message>): String {
        val conversationHistory =
            existingMessages.filter { it.role == Role.User || it.role == Role.Assistant }
                .joinToString("\n") { "${it.role.name}: ${it.text}" }

        return buildString {
            appendLine("Conversation History:")
            appendLine(conversationHistory)
            appendLine("\nUser: $prompt")
        }
    }

    private fun addTokenToBatch(token: String, state: StreamingState) {
        state.rawBuffer.append(token)

        val lowerToken = token.lowercase()
        when {
            state.inThinkTag && lowerToken.contains("</think>") -> {
                val beforeEnd = token.substringBefore("</think>")
                val afterEnd = token.substringAfter("</think>")
                state.thoughtBuffer.append(beforeEnd)
                state.inThinkTag = false
                state.visibleBuffer.append(afterEnd)
            }

            state.inThinkTag -> state.thoughtBuffer.append(token)
            lowerToken.contains("<think>") -> {
                val beforeStart = token.substringBefore("<think>")
                val afterStart = token.substringAfter("<think>")
                state.visibleBuffer.append(beforeStart)
                state.inThinkTag = true
                state.thoughtBuffer.append(afterStart)
            }

            else -> state.visibleBuffer.append(token)
        }
    }

    private fun startBatchedUIUpdates(messageId: String) {
        batchingJob = viewModelScope.launch {
            while (currentCoroutineContext().isActive) {
                delay(BATCH_INTERVAL_MS)

                currentStreamingState?.let { state ->
                    val visibleText = state.visibleBuffer.toString()
                    val thinkingText = if (state.thoughtBuffer.isNotEmpty()) {
                        state.thoughtBuffer.toString().takeLast(MAX_THINK_DISPLAY_CHARS)
                    } else null

                    if (visibleText.isNotEmpty() || !thinkingText.isNullOrEmpty()) {
                        updateStreamingMessage(messageId, visibleText, thinkingText)
                    }
                }
            }
        }
    }

    private fun processReasoningPatterns(
        rawText: String, visibleText: String, thoughtText: String
    ): Pair<String, String?> {
        runCatching {
            val json = extractPureJson(rawText)
            val obj = JSONObject(json)
            val final = obj.optString("final", obj.optString("answer", ""))
            val thought = obj.optString("thought", obj.optString("reasoning", ""))
            if (final.isNotBlank() || thought.isNotBlank()) {
                return final to thought.takeIf { it.isNotBlank() }
            }
        }

        val thinkRegex = Regex("(?is)<think>(.*?)</think>")
        val thinkMatch = thinkRegex.find(rawText)
        if (thinkMatch != null) {
            val extractedThought = thinkMatch.groupValues[1]
            val cleanedVisible = rawText.replace(thinkRegex, "").trim()
            return cleanedVisible to extractedThought
        }

        val reasoningRegex =
            Regex("(?is)(?:reasoning|thoughts?)\\s*:\\s*(.+?)\\s*(?:final|answer)\\s*:\\s*(.+)")
        reasoningRegex.find(rawText)?.let { match ->
            val thought = match.groupValues[1].trim()
            val answer = match.groupValues[2].trim()
            return answer to thought
        }

        return visibleText to thoughtText.takeIf { it.isNotBlank() }
    }

    private fun updateStreamingMessage(
        messageId: String,
        text: String,
        thought: String? = null,
        toolError: String? = null,
        isFinal: Boolean = false
    ) {
        if (isFinal) {
            saveModelState()
        }
        _messages.update { messages ->
            messages.mapIndexed { index, message ->
                if (message.id == messageId || index == streamingMessageIndex) {
                    val finalId = if (isFinal && messageId == "-1") {
                        UUID.randomUUID().toString()
                    } else {
                        message.id
                    }

                    // Handle tool calling error FIRST
                    if (toolError != null && message.role == Role.Tool) {
                        return@mapIndexed message.copy(
                            id = finalId,
                            text = text.ifBlank { "Tool execution failed" },
                            thought = thought,
                            tool = message.tool?.copy(
                                toolPreview = "Error: $toolError",
                                toolOutput = ToolOutput(
                                    toolName = message.tool.toolName,
                                    output = JSONObject().apply {
                                        put("error", toolError)
                                        put("ok", false)
                                    }.toString()
                                )
                            )
                        )
                    }

                    // Handle empty text error
                    if (isFinal && text.isBlank()) {
                        return@mapIndexed message.copy(
                            id = finalId,
                            text = "Error: Empty text received",
                            thought = thought
                        )
                    }

                    // Normal update
                    message.copy(
                        id = finalId,
                        text = text,
                        thought = thought
                    )
                } else {
                    message
                }
            }
        }
    }


    private fun emitDecodingMetrics(
        type: DecodeType, startTimeNs: Long, firstTokenTimeNs: Long, messageId: String
    ) {
        val durationMs = (firstTokenTimeNs - startTimeNs) / 1_000_000
        _lastDecodingMs.value = durationMs

        val metrics = DecodingMetrics(
            type = type,
            chatId = chatId.value,
            modelId = messageId,
            startedAtNs = startTimeNs,
            firstTokenAtNs = firstTokenTimeNs,
            durationMs = durationMs
        )

        _decodingMetrics.tryEmit(metrics)
    }
    //endregion

    //region Tool Execution
    private fun handleToolExecution(toolName: String, argsJson: String, messageId: String) {
        viewModelScope.launch {
            try {
                _uiState.value = ChatUiState.ExecutingTool(toolName, messageId)
                currentRunningToolName.value = toolName

                ToolCallingManager.executeTool(
                    appContext, toolName, argsJson,
                    onExecute = { result ->
                        viewModelScope.launch {
                            try {
                                // Check for errors first
                                if (result.has("error")) {
                                    val errorMsg = result.getString("error")
                                    Log.e(TAG, "Tool execution error: $errorMsg")

                                    // Update message with error
                                    updateStreamingMessage(
                                        messageId = messageId,
                                        text = "",
                                        toolError = errorMsg,
                                        isFinal = true
                                    )

                                    _uiState.value = ChatUiState.Idle
                                    return@launch
                                }

                                // Success path
                                val toolOutput = writeToolOutputJson(result.toString()) ?: ToolOutput()
                                updateToolPreview(messageId, toolOutput)

                                if (result.toString().isNotBlank()) {
                                    Log.d("ToolOutput", "Tool output: $result")
                                    executeInternalReasoning()
                                }

                                _uiState.value = ChatUiState.Idle
                            } catch (e: Exception) {
                                handleError("Tool result processing failed", e)
                                updateStreamingMessage(
                                    messageId = messageId,
                                    text = "",
                                    toolError = e.message ?: "Unknown error",
                                    isFinal = true
                                )
                            }
                        }
                    })
            } catch (e: Exception) {
                handleError("Tool execution failed", e)
                updateStreamingMessage(
                    messageId = messageId,
                    text = "",
                    toolError = e.message ?: "Tool execution failed",
                    isFinal = true
                )
            }
        }
    }

    private fun executeInternalReasoning() {
        unSelectTool()

        val reasoningMessageId = UUID.randomUUID().toString()
        val reasoningMessage = Message(
            role = Role.Assistant, text = "Tool Executed Successfully..!", id = reasoningMessageId
        )

        _messages.update { it + reasoningMessage }
        streamingMessageIndex = _messages.value.size - 1
    }

    fun updateToolPreview(messageId: String, toolOutput: ToolOutput) {
        val selectedToolName = toolOutput.toolName

        _messages.update { messages ->
            messages.map { message ->
                if (message.id == messageId) {
                    message.copy(
                        tool = RunningTool(
                            toolName = selectedToolName,
                            toolPreview = toolOutput.toString(),
                            toolOutput = toolOutput
                        )
                    )
                } else {
                    message
                }
            }
        }

        requestSave()
    }
    //endregion

    //region Utility Functions
    fun stopGenerating() {
        currentGenerationJob?.cancel()
        batchingJob?.cancel()
        ModelManager.stopGeneration()

        // Handle incomplete tool calls
        val currentMsgId = _currentMsgId.value
        if (currentMsgId != null) {
            val currentMessage = _messages.value.find { it.id == currentMsgId }

            if (currentMessage?.role == Role.Tool) {
                // Update tool message with cancellation error
                val errorOutput = JSONObject().apply {
                    put("err", "Generation cancelled by user....")
                }
                updateStreamingMessage(
                    messageId = currentMsgId,
                    text = "Generation cancelled by user",
                    toolError = errorOutput.toString(),
                    isFinal = true
                )
            }
        }

        currentStreamingState = null
        _uiState.value = ChatUiState.Idle
        requestSave()
    }

    private fun generateTitleIfNeeded() {
        if (_chatTitle.value.isNotBlank()) return

        val messages = _messages.value
        if (messages.size < 2) return

        val firstUserMessage = messages.firstOrNull { it.role == Role.User }?.text?.trim().orEmpty()

        if (firstUserMessage.isNotBlank()) {
            viewModelScope.launch {
                try {
                    // the step that actually *shows* the UI state
                    _uiState.value = ChatUiState.GeneratingTitle

                    val generatedTitle = generateTitleFromConversation(firstUserMessage)
                    _chatTitle.value = generatedTitle
                    requestSave()
                } catch (e: Exception) {/* fallback – title stays unchanged until a retry */
                } finally {
                    _uiState.value = ChatUiState.Idle
                }
            }
        }
    }

    private suspend fun generateTitleFromConversation(
        userMessage: String,
    ): String = withContext(Dispatchers.IO) {
        try {
            val originalSystemPrompt = selectedModel.value.systemPrompt
            val titlePrompt = """
            Generate a concise, descriptive title (3-6 words) for this conversation.
            Rules:
            - Be specific and descriptive
            - Use title case
            - No quotes or punctuation at the end
            - Capture the main topic or question
            - Keep it under 50 characters
            
            User: $userMessage
            
            Title:
        """.trimIndent()

            ModelManager.setSystemPrompt("You are a title generator. Output only the title, nothing else.")
            ModelManager.setChatTemplate(ModelsList.defaultChatTemplate)

            val titleBuilder = StringBuilder()
            var tokenCount = 0


            ModelManager.generateStreaming(
                prompt = titlePrompt,
                toolJson = "",
                onToken = { token ->
                    if (tokenCount < 15) {
                        titleBuilder.append(token)
                        tokenCount++
                    }
                },
                onToolCalled = { _, _ -> })

            ModelManager.setSystemPrompt(originalSystemPrompt)

            val rawTitle = titleBuilder.toString().trim()
            val cleanTitle =
                rawTitle.replace(Regex("^[\"']|[\"']$"), "").replace(Regex("[.!?]+$"), "")
                    .replace(Regex("\\s+"), " ").take(50).trim()

            if (cleanTitle.isBlank() || cleanTitle.length < 3) {
                throw Exception("Generated title too short")
            }
            cleanTitle
        } catch (e: Exception) {
            userMessage.split(" ").take(6).joinToString(" ").take(48)
        }
    }

    private fun generateTitleFromUserMessage(
        userMessage: String,
    ) {
        try {
            val rawTitle = userMessage.trim()
            val cleanTitle =
                rawTitle.replace(Regex("^[\"']|[\"']$"), "").replace(Regex("[.!?]+$"), "")
                    .replace(Regex("\\s+"), " ").take(50).trim()

            if (cleanTitle.isBlank() || cleanTitle.length < 3) {
                throw Exception("Generated title too short")
            }

            _chatTitle.value = cleanTitle
            requestSave()
        } catch (e: Exception) {
            _chatTitle.value = userMessage.split(" ").take(6).joinToString(" ").take(48)
            requestSave()
        }
    }

    private fun performSave() {
        try {
            Log.d(TAG, "=== Starting performSave ===")

            val currentMessages = _messages.value
            val currentTitle = _chatTitle.value
            val currentChatId = chatId.value

            if (currentMessages.isEmpty() && currentTitle.isBlank()) {
                Log.d(TAG, "No content to save, skipping")
                return
            }

            Log.d(
                TAG,
                "Saving chat: id=$currentChatId, title='$currentTitle', messages=${currentMessages.size}"
            )

            val root = rootNode.value.getNodeDirect("root")
            val history = getDefaultChatHistory(root)
            val tree = NeuronTree(history)

            val jsonData = JSONObject().apply {
                put("title", currentTitle)
                put("conversations", JSONArray(Json.encodeToString(currentMessages)))
            }

            val existingNode = if (currentChatId.isNotBlank()) {
                runCatching {
                    tree.getNodeDirect(currentChatId)
                }.getOrNull()?.takeIf {
                    it.id.isNotBlank()
                }
            } else null

            if (existingNode != null) {
                Log.d(TAG, "Updating existing chat node: ${existingNode.id}")
                existingNode.data.content = jsonData.toString()
            } else if (currentMessages.isNotEmpty()) {
                Log.d(TAG, "Creating new chat node")
                val newNode = addNewChat(history, jsonData)
                chatId.value = newNode.id
                Log.d(TAG, "Created new chat with id: ${newNode.id}")
            }

            saveTree(rootNode.value, appContext, BuildConfig.ALIAS)
            Log.d(TAG, "Chat saved successfully")

        } catch (e: Exception) {
            handleError("Save operation failed", e)
        } finally {
            refreshChatListFromDisk()
        }
    }

    private fun performTreeSave() {
        try {
            Log.d(TAG, "=== Saving tree structure to disk ===")
            saveTree(rootNode.value, appContext, BuildConfig.ALIAS)
            Log.d(TAG, "Tree structure saved successfully")
        } catch (e: Exception) {
            handleError("Tree save failed", e)
        } finally {
            refreshChatListFromDisk()
        }
    }

    private fun requestSave() {
        if (_messages.value.isNotEmpty() || _chatTitle.value.isNotBlank()) {
            saveChannel.trySend(Unit)
        }
    }

    private fun convertToolsToJson(tool: Tools): JSONArray {
        val properties = JSONObject()
        val required = mutableListOf<String>()

        tool.args.forEach { (key, value) ->
            val type = when (value) {
                is Int, is Double, is Float -> "number"
                is Boolean -> "boolean"
                else -> "string"
            }
            properties.put(key, JSONObject().put("type", type))
            if (value != null) required.add(key)
        }

        val parameters = JSONObject().put("type", "object").put("properties", properties)
            .put("required", JSONArray(required))

        val function =
            JSONObject().put("name", tool.toolName).put("description", tool.description)
                .put("parameters", parameters)

        val temp = JSONArray().put(
            JSONObject().put("type", "function").put("function", function)
        )

        Log.d("Tools", "Tools: $temp")
        return temp
    }

    private suspend fun <T> Semaphore.withPermit(action: suspend () -> T): T {
        acquire()
        try {
            return action()
        } finally {
            release()
        }
    }
    //endregion

    //region Cleanup
    override fun onCleared() {
        super.onCleared()
        currentGenerationJob?.cancel()
        batchingJob?.cancel()
        saveChannel.close()
        currentStreamingState = null
    }

    /**
     * Cleanup old states periodically
     */
    fun cleanupModelStates() {
        viewModelScope.launch {
            try {
                val root = rootNode.value.root

                // Cleanup orphaned files
                val orphanedCount = ModelStateHelper.cleanupOrphanedStates(root, appContext)

                // Keep only 10 most recent states
                val oldCount = ModelStateHelper.cleanupOldStates(root, maxStates = 10)

                // Remove states larger than 50 MB
                val largeCount = ModelStateHelper.cleanupLargeStates(root)

                if (orphanedCount + oldCount + largeCount > 0) {
                    performTreeSave()
                    Log.i(TAG, "Cleanup: $orphanedCount orphaned, $oldCount old, $largeCount large")
                }
            } catch (e: Exception) {
                handleError("Error during cleanup", e)
            }
        }
    }

    /**
     * Get state statistics for UI
     */
    fun getStateStatistics(): StateStatistics {
        return ModelStateHelper.getStateStatistics(rootNode.value.root, appContext)
    }
    //endregion


    /**
     * Save model state for current chat
     */
    fun saveModelState() {
        val currentChatId = chatId.value
        if (currentChatId.isBlank()) {
            Log.w(TAG, "Cannot save model state: no active chat")
            return
        }


        val svc = service ?: run {
            Log.w(TAG, "Cannot save model state: service not available")
            return
        }

        viewModelScope.launch {
            try {
                val stateFilePath = File(
                    appContext.filesDir,
                    "model_states/$currentChatId.state"
                ).absolutePath

                // Save state via service
                val success = svc.saveStateFile(stateFilePath)

                if (success) {
                    // Update tree with state info
                    val stateFile = File(stateFilePath)
                    ModelStateHelper.createOrUpdateModelState(
                        root = rootNode.value.root,
                        context = appContext,
                        chatId = currentChatId,
                        stateSize = stateFile.length()
                    )

                    // Save tree
                    performTreeSave()

                    Log.i(TAG, "Model state saved: ${stateFile.length() / 1024} KB")
                } else {
                    Log.e(TAG, "Failed to save model state")
                }
            } catch (e: Exception) {
                handleError("Error saving model state", e)
            }
        }
    }

    private fun handleError(message: String, cause: Throwable? = null) {
        cause?.let { Log.e(TAG, message, cause) } ?: Log.e(TAG, message)
        _uiState.value = ChatUiState.Error(message, cause = cause)
    }

    private fun handleError(message: String) {
        _uiState.value = ChatUiState.Error(message)
    }
}