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
import com.dark.ai_module.model.ModelsData
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
import com.dark.userdata.addNewChat
import com.dark.userdata.getDefaultChatHistory
import com.dark.userdata.ntds.getOrCreateHardwareBackedAesKey
import com.dark.userdata.ntds.neuron_tree.NeuronTree
import com.dark.userdata.readBrainFile
import com.dark.userdata.saveTree
import com.mp.ai_core.NativeLib
import com.mp.data_hub_lib.DataNativeLib
import com.mp.data_hub_lib.manager.DataHubManager
import com.mp.data_hub_lib.model.RagResult
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
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

    private val _modelLoadingState = MutableStateFlow<ModelManager.LoadState>(ModelManager.LoadState.Idle)
    val modelLoadingState: StateFlow<ModelManager.LoadState> = _modelLoadingState.asStateFlow()

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
    val modelList: MutableStateFlow<List<ModelsData>> = MutableStateFlow(emptyList())
    val selectedModel: MutableStateFlow<ModelsData> = MutableStateFlow(ModelsData())
    val chatId = MutableStateFlow("")
    val currentRunningToolName = MutableStateFlow("")
    private val _isRag = MutableStateFlow(false)

    // Crypto & Storage
    private val encryptionKey = MutableStateFlow(getOrCreateHardwareBackedAesKey(BuildConfig.ALIAS))
    private val rootNode = MutableStateFlow(readBrainFile(encryptionKey.value, appContext))
    //endregion

    //region Streaming State
    private var streamingMessageIndex = -1
    @Volatile private var lastUiUpdateMs = 0L

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
                    try {
                        performSave()
                    } catch (e: Exception) {
                        Log.e(TAG, "Debounced save failed", e)
                    }
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
                rootNode.value.printTree()

                // Load chat history
                loadInitialChat()
                updateChatList()

                // Load first model
                loadInitialModel()

                // Load tools and models
                toolList.value = PluginManager.toolsList.value
                modelList.value = ModelManager.getAllModels()

                _uiState.value = ChatUiState.Idle
            } catch (e: Exception) {
                Log.e(TAG, "Initialization failed", e)
                _uiState.value = ChatUiState.Error(
                    "Failed to initialize: ${e.message}",
                    isRetryable = true,
                    cause = e
                )
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

    private suspend fun loadInitialModel() {
        ModelManager.getFirstModel()?.let { model ->
            ModelManager.loadModelAwait(
                modelData = model,
                defaults = ModelManager.ManagerDefaults(
                    systemPrompt = ModelsList.generalPurposeSystemPrompt
                ),
                chatTemplate = ModelsList.chatTemplate,
                forceReload = true
            ) { state ->
                _modelLoadingState.value = state
                selectedModel.value = model
            }
        }
    }

    //region Public API - Model & Tool Selection
    fun selectModel(model: ModelsData) {
        viewModelScope.launch {
            if (_uiState.value is ChatUiState.Generating) {
                Log.w(TAG, "Cannot change model during generation")
                return@launch
            }

            try {
                _uiState.value = ChatUiState.Loading("Loading model...")
                ModelManager.unLoadModel()

                val systemPrompt = if (selectedTools.value.first.isBlank()) {
                    ModelsList.generalPurposeSystemPrompt
                } else {
                    ModelsList.toolCallSYSTEMP
                }

                ModelManager.loadModelAwait(
                    modelData = model,
                    defaults = ModelManager.ManagerDefaults(systemPrompt = systemPrompt),
                    chatTemplate = ModelsList.chatTemplate,
                    forceReload = true
                ) { state ->
                    _modelLoadingState.value = state
                    selectedModel.value = model
                }

                _uiState.value = ChatUiState.Idle
            } catch (e: Exception) {
                Log.e(TAG, "Model selection failed", e)
                _uiState.value = ChatUiState.Error("Failed to load model: ${e.message}")
            }
        }
    }

    fun setRag(enabled: Boolean) {
        _isRag.value = enabled
        Log.d(TAG, "RAG set to: $enabled")
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
                    _uiState.value = ChatUiState.Error("Chat not found or empty")
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
                Log.e(TAG, "Failed loading chat $chatIdToLoad", e)
                _uiState.value = ChatUiState.Error("Failed to load chat: ${e.message}")
            }
        }
    }

    fun updateChatList() {
        viewModelScope.launch {
            try {
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
                Log.e(TAG, "Failed updating chat list", e)
            }
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

    fun deleteChatById(id: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Deleting chat $id")

                // Delete from tree
                rootNode.value.deleteNodeById(id)

                // Save tree immediately (don't use debounced save)
                performTreeSave()

                // Reload tree from disk to ensure consistency
                rootNode.value = readBrainFile(encryptionKey.value, appContext)

                // Update chat list
                updateChatList()

                // Clear current chat if it was deleted
                if (chatId.value == id) {
                    withContext(Dispatchers.Main) {
                        newChat()
                    }
                }

                Log.d(TAG, "Chat $id deleted successfully")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete chat $id", e)
                _uiState.value = ChatUiState.Error("Failed to delete chat: ${e.message}")
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
                    executeSendMessage(input)
                } catch (e: CancellationException) {
                    Log.d(TAG, "Message sending cancelled")
                    _uiState.value = ChatUiState.Idle
                } catch (e: Exception) {
                    Log.e(TAG, "Error in sendMessage", e)
                    _uiState.value = ChatUiState.Error(
                        "Failed to send message: ${e.message}",
                        cause = e
                    )
                }
            }
        }
    }

    private suspend fun executeSendMessage(input: String) {
        // Add user message immediately
        _messages.update { it + Message(role = Role.User, text = input) }

        val messageId = UUID.randomUUID().toString()
        _uiState.value = ChatUiState.Generating(messageId)

        // Prepare assistant message for streaming
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
            handleRAGRequest(input, isTool, messageId)
        } else {
            streamAndRender(prompt = input, enableTools = isTool, messageId = messageId)
        }
    }

    fun regenerateResponse(model: ModelsData?, messageId: String) {
        if (model == null) return

        val messageIndex = _messages.value.indexOfFirst { it.id == messageId }
        if (messageIndex == -1) return

        val targetMessage = _messages.value[messageIndex]
        if (targetMessage.role != Role.Assistant && targetMessage.role != Role.Tool) return

        // Cancel any existing generation
        currentGenerationJob?.cancel()

        currentGenerationJob = viewModelScope.launch {
            operationSemaphore.withPermit {
                try {
                    executeRegenerate(model, messageId, messageIndex, targetMessage)
                } catch (e: CancellationException) {
                    Log.d(TAG, "Regeneration cancelled")
                    _uiState.value = ChatUiState.Idle
                } catch (e: Exception) {
                    Log.e(TAG, "Regeneration failed", e)
                    _uiState.value = ChatUiState.Error(
                        "Failed to regenerate: ${e.message}",
                        cause = e
                    )
                }
            }
        }
    }

    private suspend fun executeRegenerate(
        model: ModelsData,
        messageId: String,
        messageIndex: Int,
        targetMessage: Message
    ) {
        _uiState.value = ChatUiState.Generating(messageId)

        // Get context from previous user message
        val userContext = _messages.value.take(messageIndex)
            .lastOrNull { it.role == Role.User }?.text.orEmpty()

        // Clear the message for streaming
        _messages.update { messages ->
            messages.toMutableList().apply {
                set(messageIndex, targetMessage.copy(text = "", thought = null))
            }
        }
        streamingMessageIndex = messageIndex

        // Switch model if needed
        if (selectedModel.value.id != model.id) {
            _uiState.value = ChatUiState.Loading("Switching model...")
            ModelManager.unLoadModel()
            ModelManager.loadModelAwait(
                modelData = model,
                defaults = ModelManager.ManagerDefaults(
                    systemPrompt = "You are a helpful assistant that improves message clarity and accuracy."
                ),
                chatTemplate = ModelsList.chatTemplate,
                forceReload = true
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
            isRegeneration = true
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
    private suspend fun handleRAGRequest(input: String, isTool: Boolean, messageId: String) {
        val currentDataset = DataHubManager.currentDataSet.value
        if (currentDataset == null) {
            Log.d(TAG, "No dataset loaded, fallback to normal generation")
            streamAndRender(prompt = input, enableTools = isTool, messageId = messageId)
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
                            streamAndRender(prompt = finalPrompt, enableTools = isTool, messageId = messageId)
                        }
                    }
                } else {
                    streamAndRender(prompt = input, enableTools = isTool, messageId = messageId)
                }
            } else {
                throw Exception("RAG failed: $error")
            }
        } catch (e: Exception) {
            Log.e(TAG, "RAG processing failed", e)
            streamAndRender(prompt = input, enableTools = isTool, messageId = messageId)
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
            Log.e(TAG, "Failed to extract RAG context", e)
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
                    is ModelManager.LoadState.OnLoaded -> {
                        Log.d(TAG, "Generation model ready: ${loadState.model.modeName}")
                        onReady()
                    }
                    is ModelManager.LoadState.Error -> {
                        Log.e(TAG, "Generation model load failed: ${loadState.message}")
                        _uiState.value = ChatUiState.Error("Model load failed: ${loadState.message}")
                    }
                    else -> Log.d(TAG, "Generation model loading: $loadState")
                }
            }.onFailure { error ->
                _uiState.value = ChatUiState.Error("Model preparation failed: ${error.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error ensuring generation model ready", e)
            _uiState.value = ChatUiState.Error("Model preparation error: ${e.message}")
        }
    }
    //endregion

    //region Streaming & Rendering
    private suspend fun streamAndRender(
        prompt: String,
        enableTools: Boolean,
        messageId: String,
        isRegeneration: Boolean = false
    ) {
        val startTimeNs = System.nanoTime()
        var firstTokenReceived = false
        _currentMsgId.value = messageId

        _uiState.value = ChatUiState.DecodingStream(messageId, startTimeNs)

        // Initialize streaming state
        currentStreamingState = StreamingState(messageId = messageId)

        // Start batching coroutine
        startBatchedUIUpdates(messageId)

        fun finalizeMessage(text: String, thought: String?) {
            // Cancel batching and do final update
            batchingJob?.cancel()

            val finalThought = thought?.take(MAX_THOUGHT_SAVE_CHARS)
            updateStreamingMessage(messageId, text, finalThought, isFinal = true)

            generateTitleIfNeeded()
            requestSave()
            updateChatList()

            // Clear streaming state
            currentStreamingState = null
        }

        try {
            ModelManager.generateStreaming(
                prompt = prompt,
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

                    // Add token to streaming state (no immediate UI update)
                    currentStreamingState?.let { state ->
                        addTokenToBatch(token, state)
                    }
                },
                onToolCalled = { toolName, argsJson ->
                    handleToolExecution(toolName, argsJson, messageId)
                }
            )

            val finalState = currentStreamingState
            if (finalState != null) {
                // Post-process for any missed reasoning patterns
                val (finalText, finalThought) = processReasoningPatterns(
                    finalState.rawBuffer.toString(),
                    finalState.visibleBuffer.toString(),
                    finalState.thoughtBuffer.toString()
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
            Log.e(TAG, "Streaming failed", e)
            batchingJob?.cancel()
            _uiState.value = ChatUiState.Error("Generation failed: ${e.message}", cause = e)
            currentStreamingState?.let { state ->
                finalizeMessage(
                    state.visibleBuffer.toString(),
                    state.thoughtBuffer.toString().ifBlank { null }
                )
            }
        } finally {
            if (_uiState.value !is ChatUiState.ExecutingTool) {
                _uiState.value = ChatUiState.Idle
            }
        }
    }

    private fun addTokenToBatch(token: String, state: StreamingState) {
        // Add to raw buffer
        state.rawBuffer.append(token)

        // Process token for thinking tags
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

                    // Only update if there's new content
                    if (visibleText.isNotEmpty() || !thinkingText.isNullOrEmpty()) {
                        updateStreamingMessage(messageId, visibleText, thinkingText)
                    }
                }
            }
        }
    }

    private fun processStreamingToken(
        token: String,
        visibleBuffer: StringBuilder,
        thoughtBuffer: StringBuilder,
        inThink: Boolean,
        updateInThink: (Boolean) -> Unit
    ) {
        val lowerToken = token.lowercase()
        when {
            inThink && lowerToken.contains("</think>") -> {
                val beforeEnd = token.substringBefore("</think>")
                val afterEnd = token.substringAfter("</think>")
                thoughtBuffer.append(beforeEnd)
                updateInThink(false)
                visibleBuffer.append(afterEnd)
            }
            inThink -> thoughtBuffer.append(token)
            lowerToken.contains("<think>") -> {
                val beforeStart = token.substringBefore("<think>")
                val afterStart = token.substringAfter("<think>")
                visibleBuffer.append(beforeStart)
                updateInThink(true)
                thoughtBuffer.append(afterStart)
            }
            else -> visibleBuffer.append(token)
        }
    }

    private fun processReasoningPatterns(
        rawText: String,
        visibleText: String,
        thoughtText: String
    ): Pair<String, String?> {
        // Try JSON pattern first
        runCatching {
            val json = extractPureJson(rawText)
            val obj = JSONObject(json)
            val final = obj.optString("final", obj.optString("answer", ""))
            val thought = obj.optString("thought", obj.optString("reasoning", ""))
            if (final.isNotBlank() || thought.isNotBlank()) {
                return final to thought.takeIf { it.isNotBlank() }
            }
        }

        // Try think tags
        val thinkRegex = Regex("(?is)<think>(.*?)</think>")
        val thinkMatch = thinkRegex.find(rawText)
        if (thinkMatch != null) {
            val extractedThought = thinkMatch.groupValues[1]
            val cleanedVisible = rawText.replace(thinkRegex, "").trim()
            return cleanedVisible to extractedThought
        }

        // Try reasoning/answer pattern
        val reasoningRegex = Regex("(?is)(?:reasoning|thoughts?)\\s*:\\s*(.+?)\\s*(?:final|answer)\\s*:\\s*(.+)")
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
                                // Generate summary of tool output
                                val summaryPrompt = buildSummaryPrompt(result.toString())
                                executeInternalReasoning(summaryPrompt)
                            }

                            _uiState.value = ChatUiState.Idle
                        } catch (e: Exception) {
                            Log.e(TAG, "Tool result processing failed", e)
                            _uiState.value = ChatUiState.Error("Tool execution failed: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Tool execution failed", e)
                _uiState.value = ChatUiState.Error("Tool execution failed: ${e.message}")
            }
        }
    }

    private fun repairToolCall(toolName: String, argsJson: String): JSONObject {
        val lastUserQuery = messages.value.lastOrNull { it.role == Role.User }?.text?.trim().orEmpty()
        val selectedToolName = selectedTools.value.second.toolName
        val fallbackTool = toolName.ifBlank { selectedToolName }

        return try {
            val root = JSONObject(argsJson)
            val calls = root.optJSONArray("tool_calls")
            val firstCall = calls?.optJSONObject(0)
            val extractedToolName = firstCall?.optString("name").orEmpty().ifBlank { fallbackTool }
            val argObj = firstCall?.optJSONObject("arguments")

            // Check if it's a schema echo (contains schema properties instead of actual args)
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
            // Fallback for malformed JSON
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
            messageId = reasoningMessageId
        )
    }

    suspend fun updateToolPreview(messageId: String, toolOutput: ToolOutput) {
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

        val firstUserMessage = _messages.value
            .firstOrNull { it.role == Role.User }
            ?.text
            ?.trim()
            .orEmpty()

        if (firstUserMessage.isNotBlank()) {
            _chatTitle.value = firstUserMessage.take(48)
        }
    }

    // Replace your existing performSave() method with this:

    private suspend fun performSave() {
        try {
            Log.d(TAG, "=== Starting performSave ===")

            // Only save if we have actual content to save
            val currentMessages = _messages.value
            val currentTitle = _chatTitle.value
            val currentChatId = chatId.value

            // Don't create empty chats
            if (currentMessages.isEmpty() && currentTitle.isBlank()) {
                Log.d(TAG, "No content to save, skipping")
                return
            }

            Log.d(TAG, "Saving chat: id=$currentChatId, title='$currentTitle', messages=${currentMessages.size}")

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
                    it.id.isNotBlank() // Ensure it's a valid node
                }
            } else null

            if (existingNode != null) {
                Log.d(TAG, "Updating existing chat node: ${existingNode.id}")
                existingNode.data.content = jsonData.toString()
            } else if (currentMessages.isNotEmpty()) {
                // Only create new chat if we have actual messages
                Log.d(TAG, "Creating new chat node")
                val newNode = addNewChat(history, jsonData)
                chatId.value = newNode.id
                Log.d(TAG, "Created new chat with id: ${newNode.id}")
            }

            // Save the tree to disk
            saveTree(rootNode.value, appContext, BuildConfig.ALIAS)
            Log.d(TAG, "Chat saved successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Save operation failed", e)
        }
    }

    // Add this new method specifically for deletion saves
    private suspend fun performTreeSave() {
        try {
            Log.d(TAG, "=== Saving tree structure to disk ===")
            saveTree(rootNode.value, appContext, BuildConfig.ALIAS)
            Log.d(TAG, "Tree structure saved successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Tree save failed", e)
        }
    }

    // Update requestSave to only handle conversation saves
    private fun requestSave() {
        // Only send save request if we have content worth saving
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

    // Extension function to provide permits for semaphore
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
    // Update cleanup to cancel batching job
    override fun onCleared() {
        super.onCleared()
        currentGenerationJob?.cancel()
        batchingJob?.cancel()
        saveChannel.close()
        currentStreamingState = null
    }
    //endregion
}

fun ChatScreenViewModel.getTokenFlow(messageId: String): Flow<String> = flow {
    // Continuously emit tokens from the streaming state
    while (currentCoroutineContext().isActive) {
        val state = currentStreamingState
        if (state != null && state.messageId == messageId) {
            val newTokens = state.rawBuffer.toString().substring(state.lastEmittedIndex)
            if (newTokens.isNotEmpty()) {
                emit(newTokens)
                state.lastEmittedIndex += newTokens.length
            }
        }
        kotlinx.coroutines.delay(20) // small delay to avoid tight loop
    }
}

fun ChatScreenViewModel.getCurrentBuffer(messageId: String): String {
    val state = currentStreamingState
    return if (state != null && state.messageId == messageId) {
        state.visibleBuffer.toString()
    } else {
        ""
    }
}

// Extend StreamingState to track last emitted index for getTokenFlow
private var StreamingState.lastEmittedIndex: Int
    get() = this._lastEmittedIndex ?: 0
    set(value) { this._lastEmittedIndex = value }

private var StreamingState._lastEmittedIndex: Int? by mutableStateOf(null)