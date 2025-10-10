package com.dark.neuroverse.viewModel.chatViewModel

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dark.ai_module.data.ModelsList
import com.dark.ai_module.model.LoadState
import com.dark.ai_module.model.ModelData
import com.dark.ai_module.workers.ModelManager
import com.dark.neuroverse.BuildConfig
import com.dark.neuroverse.model.ChatINFO
import com.dark.neuroverse.model.Message
import com.dark.neuroverse.model.Role
import com.dark.neuroverse.model.RunningTool
import com.dark.neuroverse.model.ToolOutput
import com.dark.neuroverse.util.extractPureJson
import com.dark.neuroverse.util.writeToolOutputJson
import com.dark.plugins.manager.PluginManager
import com.dark.plugins.model.Tools
import com.dark.plugins.worker.ToolRunner
import com.dark.neuroverse.userdata.addNewChat
import com.dark.neuroverse.userdata.getDefaultChatHistory
import com.dark.neuroverse.userdata.helpers.MemoryDataTags
import com.dark.neuroverse.userdata.helpers.getMemoryByTags
import com.dark.neuroverse.userdata.helpers.updateMemory
import com.dark.neuroverse.userdata.ntds.getOrCreateHardwareBackedAesKey
import com.dark.neuroverse.userdata.ntds.neuron_tree.NeuronTree
import com.dark.neuroverse.userdata.readBrainFile
import com.dark.neuroverse.userdata.saveTree
import com.mp.ai_core.NativeLib
import com.mp.data_hub_lib.manager.DataHubManager
import com.mp.data_hub_lib.model.BrainDoc
import com.mp.data_hub_lib.model.RagResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

data class StreamingState(
    val messageId: String,
    val visibleBuffer: StringBuilder = StringBuilder(),
    val thoughtBuffer: StringBuilder = StringBuilder(),
    val rawBuffer: StringBuilder = StringBuilder(),
    var inThinkTag: Boolean = false,
    val lastBatchTime: Long = System.currentTimeMillis()
)

/**
 * Unified UI State - Single source of truth
 */
sealed class ChatUiState {
    object Idle : ChatUiState()

    data class Loading(
        val operation: String,
        val progress: Float? = null
    ) : ChatUiState()

    data class Generating(
        val messageId: String,
        val isFirstToken: Boolean = false
    ) : ChatUiState()

    data class DecodingStream(
        val messageId: String,
        val startTimeNs: Long
    ) : ChatUiState()

    data class ExecutingTool(
        val toolName: String,
        val messageId: String
    ) : ChatUiState()

    data class Error(
        val message: String,
        val isRetryable: Boolean = true,
        val cause: Throwable? = null
    ) : ChatUiState()

    data object GeneratingTitle : ChatUiState()
}

/**
 * Decoding metrics for performance tracking
 */
data class DecodingMetrics(
    val type: DecodeType,
    val chatId: String,
    val modelId: String,
    val startedAtNs: Long,
    val firstTokenAtNs: Long,
    val durationMs: Long
)

enum class DecodeType { NORMAL, REGENERATE }

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
 * Clean, refactored ChatScreenViewModel with proper state management
 */
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
    val toolList: MutableStateFlow<List<Pair<String, List<Tools>>>> = MutableStateFlow(emptyList())
    val selectedTools: MutableStateFlow<Pair<String, Tools>> = MutableStateFlow("" to Tools())
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
            saveChannel.consumeAsFlow()
                .debounce(SAVE_DEBOUNCE_MS)
                .collect {
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

                // Load tools and models
                toolList.value = PluginManager.toolsList.value
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

                val systemPrompt = if (selectedTools.value.first.isBlank()) {
                    ModelsList.defaultSystemPrompt
                } else {
                    ModelsList.toolCallingSystemPrompt
                }

                ModelManager.loadModelAwait(
                    modelData = model.copy(chatTemplate = ModelsList.defaultChatTemplate, systemPrompt = systemPrompt),
                ) { state ->
                    _modelLoadingState.value = state
                    selectedModel.value = model
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
        selectedTools.value = tool
        ModelManager.setSystemPrompt(ModelsList.toolCallingSystemPrompt)
    }

    fun unSelectTool() {
        selectedTools.value = "" to Tools()
        ModelManager.setSystemPrompt(ModelsList.defaultSystemPrompt)
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
            val freshList =
                NeuronTree(history).getAllChildrenRecursive()
                    .filter { it.data.content.isNotBlank() }
                    .map { node ->
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

    private suspend fun executeSendMessage(input: String, currentMessages: List<Message>) {
        _messages.update { it + Message(role = Role.User, text = input) }

        val messageId = UUID.randomUUID().toString()
        _uiState.value = ChatUiState.Generating(messageId)

        val isTool = selectedTools.value.second.toolName.isNotEmpty()
        val assistantMessage = Message(
            role = if (isTool) Role.Tool else Role.Assistant,
            text = "",
            id = messageId,
            tool = if (isTool) RunningTool(
                toolName = selectedTools.value.second.toolName,
                toolPreview = "",
                toolOutput = ToolOutput()
            ) else null
        )

        _messages.update { it + assistantMessage }
        streamingMessageIndex = _messages.value.size - 1

        if (_isRag.value) {
            handleRAGRequest(input, isTool, messageId, currentMessages)
        } else {
            streamAndRender(prompt = input, enableTools = isTool, messageId = messageId, existingMessages = currentMessages)
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
                    executeRegenerate(model, messageId, messageIndex, targetMessage, _messages.value)
                } catch (e: CancellationException) {
                    Log.d(TAG, "Regeneration cancelled")
                    _uiState.value = ChatUiState.Idle
                } catch (e: Exception) {
                    handleError("Regeneration failed", e)
                }
            }
        }
    }

    private suspend fun executeRegenerate(
        model: ModelData,
        messageId: String,
        messageIndex: Int,
        targetMessage: Message,
        currentMessages: List<Message>
    ) {
        _uiState.value = ChatUiState.Generating(messageId)

        val userContext = currentMessages.take(messageIndex)
            .lastOrNull { it.role == Role.User }?.text.orEmpty()

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
                    """.trimIndent(),
                    chatTemplate = ModelsList.toolCallingChatTemplate
                )
            ) { state ->
                _modelLoadingState.value = state
                selectedModel.value = model
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

    private fun buildOptimizePrompt(userContext: String, originalText: String): String = buildString {
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
    private suspend fun handleRAGRequest(input: String, isTool: Boolean, messageId: String, currentMessages: List<Message>) {
        val currentDataset = DataHubManager.currentDataSet.value
        if (currentDataset == null) {
            Log.d(TAG, "No dataset loaded, fallback to normal generation")
            streamAndRender(prompt = input, enableTools = isTool, messageId = messageId, existingMessages = currentMessages)
            return
        }

        try {
            _uiState.value = ChatUiState.Loading("Processing with RAG...")

            val initResult = DataHubManager.reinitializeEmbeddingModel()
            if (initResult.isFailure) {
                throw Exception("Embedding model initialization failed: ${initResult.exceptionOrNull()?.message}")
            }

            val (ragData, error) = suspendCancellableCoroutine { continuation ->
                DataHubManager.runRAG(query = input, topK = 5) { ragResult, ragError ->
                    continuation.resumeWith(Result.success(ragResult to ragError))
                }
            }

            if (ragData != null && error == null) {
                val ragContext = extractRAGContext(ragData)
                if (ragContext.isNotBlank()) {
                    val finalPrompt = buildRAGPrompt(ragContext, input)
                    ensureGenerationModelReady {
                        viewModelScope.launch {
                            streamAndRender(
                                prompt = finalPrompt,
                                enableTools = isTool,
                                messageId = messageId,
                                existingMessages = currentMessages
                            )
                        }
                    }
                } else {
                    streamAndRender(prompt = input, enableTools = isTool, messageId = messageId, existingMessages = currentMessages)
                }
            } else {
                throw Exception("RAG failed: $error")
            }
        } catch (e: Exception) {
            handleError("RAG processing failed", e)
            streamAndRender(prompt = input, enableTools = isTool, messageId = messageId, existingMessages = currentMessages)
        }
    }

    private fun buildRAGPrompt(context: String, input: String): String = buildString {
        append("Use the following context to answer:\n\n")
        append("Context:\n")
        append(context)
        append("\n\nQuestion: ")
        append(input)
    }

    private fun extractRAGContext(ragData: RagResult): String {
        return try {
            ragData.docs.joinToString("\n") { it.text }
        } catch (e: Exception) {
            handleError("Failed to extract RAG context", e)
            ""
        }
    }

    private suspend fun ensureGenerationModelReady(onReady: () -> Unit) {
        try {
            val currentModel = selectedModel.value
            val generationLib = NativeLib.getGenerationInstance()
            generationLib.nativeRelease()

            delay(500)

            ModelManager.loadModelAwait(currentModel) { loadState ->
                when (loadState) {
                    is LoadState.OnLoaded -> {
                        Log.d(TAG, "Generation model ready: ${loadState.model.modelName}")
                        onReady()
                    }

                    is LoadState.Error -> {
                        handleError("Generation model load failed: ${loadState.message}")
                    }

                    else -> Log.d(TAG, "Generation model loading: $loadState")
                }
            }.onFailure { error ->
                handleError("Model preparation failed: ${error.message}")
            }
        } catch (e: Exception) {
            handleError("Error ensuring generation model ready", e)
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
                toolJson = if (enableTools) convertToolsToJson(selectedTools.value.second).toString() else null,
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
                }
            )

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
                    state.visibleBuffer.toString(),
                    state.thoughtBuffer.toString().ifBlank { null }
                )
            }
            throw e
        } catch (e: Exception) {
            handleError("Streaming failed", e)
            batchingJob?.cancel()
            currentStreamingState?.let { state ->
                finalizeMessage(
                    state.visibleBuffer.toString(),
                    state.thoughtBuffer.toString().ifBlank { null }
                )
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
        val conversationHistory = existingMessages
            .filter { it.role == Role.User || it.role == Role.Assistant }
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
        rawText: String,
        visibleText: String,
        thoughtText: String
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
        thought: String?,
        isFinal: Boolean = false
    ) {
        _messages.update { messages ->
            messages.mapIndexed { index, message ->
                if (message.id == messageId || index == streamingMessageIndex) {
                    val finalId = if (isFinal && messageId == "-1") {
                        UUID.randomUUID().toString()
                    } else {
                        message.id
                    }
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
        type: DecodeType,
        startTimeNs: Long,
        firstTokenTimeNs: Long,
        messageId: String
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

                val repairedToolCall = repairToolCall(toolName, argsJson)
                Log.d(TAG, "Executing tool: $repairedToolCall")

                val pluginResult = PluginManager.runPlugin(
                    appContext,
                    selectedTools.value.first,
                    repairedToolCall.toString()
                )

                ToolRunner.run(pluginResult, appContext, repairedToolCall) { result ->
                    viewModelScope.launch {
                        try {
                            val toolOutput = writeToolOutputJson(result.toString()) ?: ToolOutput()
                            updateToolPreview(messageId, toolOutput)

                            if (result.toString().isNotBlank()) {
                                val summaryPrompt = buildSummaryPrompt(result.toString())
                                executeInternalReasoning(summaryPrompt)
                            }

                            _uiState.value = ChatUiState.Idle
                        } catch (e: Exception) {
                            handleError("Tool result processing failed", e)
                        }
                    }
                }
            } catch (e: Exception) {
                handleError("Tool execution failed", e)
            }
        }
    }

    private fun repairToolCall(toolName: String, argsJson: String): JSONObject {
        val lastUserQuery =
            messages.value.lastOrNull { it.role == Role.User }?.text?.trim().orEmpty()
        val selectedToolName = selectedTools.value.second.toolName
        val fallbackTool = toolName.ifBlank { selectedToolName }

        return try {
            val root = JSONObject(argsJson)
            val calls = root.optJSONArray("tool_calls")
            val firstCall = calls?.optJSONObject(0)
            val extractedToolName = firstCall?.optString("name").orEmpty().ifBlank { fallbackTool }
            val argObj = firstCall?.optJSONObject("arguments")

            val isSchemaEcho = argObj?.let { obj ->
                obj.has("type") || obj.has("properties") || obj.has("required")
            } ?: true

            if (isSchemaEcho) {
                JSONObject().apply {
                    put("tool", extractedToolName)
                    put("args", JSONObject().put("query", lastUserQuery))
                }
            } else {
                JSONObject().apply {
                    put("tool", extractedToolName)
                    put("args", argObj)
                }
            }
        } catch (e: Exception) {
            JSONObject().apply {
                put("tool", fallbackTool)
                put("args", JSONObject().put("query", lastUserQuery))
            }
        }
    }

    private fun buildSummaryPrompt(toolOutput: String): String = buildString {
        append("Summarize the tool output in 5-6 concise lines. ")
        append("Preserve entities, numbers, and URLs. Be factual and crisp.\n\n")
        append(toolOutput)
    }

    private suspend fun executeInternalReasoning(prompt: String) {
        unSelectTool()
        ModelManager.setSystemPrompt("You are a crisp summarizer. Be concise and factual.")

        val reasoningMessageId = UUID.randomUUID().toString()
        val reasoningMessage = Message(
            role = Role.Assistant,
            text = "",
            id = reasoningMessageId
        )

        _messages.update { it + reasoningMessage }
        streamingMessageIndex = _messages.value.size - 1

        streamAndRender(
            prompt = prompt,
            enableTools = false,
            messageId = reasoningMessageId,
            existingMessages = _messages.value
        )
    }

    fun updateToolPreview(messageId: String, toolOutput: ToolOutput) {
        val selectedToolName = selectedTools.value.second.toolName

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
        currentStreamingState = null
        _uiState.value = ChatUiState.Idle
    }

    private fun generateTitleIfNeeded() {
        if (_chatTitle.value.isNotBlank()) return

        val messages = _messages.value
        if (messages.size < 2) return

        val firstUserMessage = messages
            .firstOrNull { it.role == Role.User }
            ?.text
            ?.trim()
            .orEmpty()

        if (firstUserMessage.isNotBlank()) {
            viewModelScope.launch {
                try {
                    // the step that actually *shows* the UI state
                    _uiState.value = ChatUiState.GeneratingTitle

                    val generatedTitle = generateTitleFromConversation(firstUserMessage)
                    _chatTitle.value = generatedTitle
                    requestSave()
                } catch (e: Exception) {
                    /* fallback – title stays unchanged until a retry */
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
                toolJson = null,
                onToken = { token ->
                    if (tokenCount < 15) {
                        titleBuilder.append(token)
                        tokenCount++
                    }
                },
                onToolCalled = { _, _ -> }
            )

            ModelManager.setSystemPrompt(originalSystemPrompt)

            val rawTitle = titleBuilder.toString().trim()
            val cleanTitle = rawTitle
                .replace(Regex("^[\"']|[\"']$"), "")
                .replace(Regex("[.!?]+$"), "")
                .replace(Regex("\\s+"), " ")
                .take(50)
                .trim()

            if (cleanTitle.isBlank() || cleanTitle.length < 3) {
                throw Exception("Generated title too short")
            }
            cleanTitle
        } catch (e: Exception) {
            userMessage
                .split(" ")
                .take(6)
                .joinToString(" ")
                .take(48)
        }
    }

    private fun generateTitleFromUserMessage(
        userMessage: String,
    ) {
        try {
            val rawTitle = userMessage.trim()
            val cleanTitle = rawTitle
                .replace(Regex("^[\"']|[\"']$"), "")
                .replace(Regex("[.!?]+$"), "")
                .replace(Regex("\\s+"), " ")
                .take(50)
                .trim()

            if (cleanTitle.isBlank() || cleanTitle.length < 3) {
                throw Exception("Generated title too short")
            }

            _chatTitle.value = cleanTitle
            requestSave()
        } catch (e: Exception) {
            _chatTitle.value = userMessage
                .split(" ")
                .take(6)
                .joinToString(" ")
                .take(48)
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

        val parameters = JSONObject()
            .put("type", "object")
            .put("properties", properties)
            .put("required", JSONArray(required))

        val function = JSONObject()
            .put("name", tool.toolName)
            .put("description", tool.description ?: "")
            .put("parameters", parameters)

        return JSONArray().put(
            JSONObject()
                .put("type", "function")
                .put("function", function)
        )
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
    //endregion

    private fun handleError(message: String, cause: Throwable? = null) {
        cause?.let { Log.e(TAG, message, cause) } ?: Log.e(TAG, message)
        _uiState.value = ChatUiState.Error(message, cause = cause)
    }
}