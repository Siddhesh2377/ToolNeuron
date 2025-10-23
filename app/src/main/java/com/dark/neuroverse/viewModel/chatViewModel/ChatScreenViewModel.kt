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
import com.dark.neuroverse.model.ChatUiState
import com.dark.neuroverse.model.Message
import com.dark.neuroverse.model.Role
import com.dark.neuroverse.model.RunningTool
import com.dark.neuroverse.model.ToolOutput
import com.dark.neuroverse.userdata.helpers.ModelStateHelper
import com.dark.neuroverse.userdata.ntds.neuron_tree.NeuronTree
import com.dark.neuroverse.worker.ChatManager
import com.dark.neuroverse.worker.RAGManager
import com.dark.neuroverse.worker.TextGenerationWorker
import com.dark.neuroverse.worker.ToolCallingManager
import com.dark.neuroverse.worker.UIStateManager
import com.dark.neuroverse.worker.UserDataManager
import com.dark.plugins.model.Tools
import com.mp.data_hub_lib.manager.DataHubManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

class ChattingViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ChatScreenViewModel(context.applicationContext) as T
    }
}

/**
 * Main ViewModel for Chat Screen.
 * Delegates most operations to worker objects for better separation of concerns.
 */
class ChatScreenViewModel(private val appContext: Context) : ViewModel() {

    companion object {
        private const val TAG = "ChatVM"
    }

    // Model state
    private val _modelLoadingState = MutableStateFlow<LoadState>(LoadState.Idle)
    val modelLoadingState: StateFlow<LoadState> = _modelLoadingState.asStateFlow()

    val modelList: MutableStateFlow<List<ModelData>> = MutableStateFlow(emptyList())
    val selectedModel: MutableStateFlow<ModelData> = MutableStateFlow(ModelData())

    // RAG state
    private val _isRag = MutableStateFlow(false)
    val isRag: StateFlow<Boolean> = _isRag.asStateFlow()

    // Coroutine management
    private var currentGenerationJob: Job? = null

    // Expose worker states
    val chatList = ChatManager.chatList
    val messages = ChatManager.messages
    val chatTitle = ChatManager.currentChatTitle
    val chatId = ChatManager.currentChatId

    val uiState = UIStateManager.uiState
    val decodingMetrics = TextGenerationWorker.decodingMetrics
    val lastDecodingMs = TextGenerationWorker.lastDecodingMs
    val currentMsgId = TextGenerationWorker.currentMsgId

    val toolList = ToolCallingManager.toolList
    val selectedTool = ToolCallingManager.selectedTool

    val isGenerating: StateFlow<Boolean> = uiState.map { state ->
        state is ChatUiState.Generating ||
                state is ChatUiState.DecodingStream ||
                state is ChatUiState.ExecutingTool
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        initializeViewModel()
    }

    //region Initialization
    private fun initializeViewModel() {
        viewModelScope.launch {
            try {
                UIStateManager.setStateLoading("Initializing...")

                // Load chat history
                UserDataManager.refreshChatListFromDisk { error ->
                    UIStateManager.setStateError("Failed to load chat history", cause = error)
                }

                // Initialize tools
                ToolCallingManager.initViewModel()

                // Load models
                modelList.value = ModelManager.getAllModels()

                UIStateManager.setStateIdle()
                Log.d(TAG, "ViewModel initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Initialization failed", e)
                UIStateManager.setStateError("Initialization failed", cause = e)
            }
        }
    }
    //endregion

    //region Model Management
    fun selectModel(model: ModelData) {
        viewModelScope.launch {
            // Prevent model change during generation
            if (UIStateManager.isGenerating()) {
                Log.w(TAG, "Cannot change model during generation")
                return@launch
            }

            // Toggle off if same model selected
            if (selectedModel.value.id == model.id) {
                Log.d(TAG, "Unselecting model")
                ModelManager.unloadModel()
                selectedModel.value = ModelData()
                return@launch
            }

            UIStateManager.toggleStateModelLoading(true)
            try {
                ModelManager.unloadModel()

                // Set appropriate system prompt
                val systemPrompt = if (ToolCallingManager.isToolSelected()) {
                    ModelsList.toolCallingSystemPrompt
                } else {
                    ModelsList.defaultSystemPrompt
                }

                ModelManager.loadModelAwait(
                    modelData = model.copy(systemPrompt = systemPrompt)
                ) { state ->
                    _modelLoadingState.value = state
                    if (state is LoadState.OnLoaded) {
                        selectedModel.value = model
                        Log.d(TAG, "Model loaded: ${model.modelName}")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Model selection failed", e)
                UIStateManager.setStateError("Model selection failed", cause = e)
            } finally {
                UIStateManager.toggleStateModelLoading(false)
            }
        }
    }

    fun unloadModel() {
        viewModelScope.launch {
            ModelManager.unloadModel()
            selectedModel.value = ModelData()
            Log.d(TAG, "Model unloaded")
        }
    }
    //endregion

    //region RAG Management
    fun setRag(enabled: Boolean) {
        _isRag.value = enabled

        if (enabled && !RAGManager.isRAGReady()) {
            viewModelScope.launch {
                val result = RAGManager.initRAG()
                if (result.has("error")) {
                    Log.e(TAG, "RAG initialization failed: ${result.getString("error")}")
                    UIStateManager.setStateError("RAG initialization failed")
                    _isRag.value = false
                }
            }
        }

        Log.d(TAG, "RAG ${if (enabled) "enabled" else "disabled"}")
    }
    //endregion

    //region Tool Management
    fun selectTool(tool: Pair<String, Tools>) {
        ToolCallingManager.selectTool(tool)
        Log.d(TAG, "Tool selected: ${tool.second.toolName}")
    }

    fun unselectTool() {
        ToolCallingManager.unSelectTool()
        Log.d(TAG, "Tool unselected")
    }
    //endregion

    //region Chat Management
    fun loadChatById(id: String) {
        viewModelScope.launch {
            ChatManager.loadChatById(id)
        }
    }

    fun newChat() {
        if (UIStateManager.isGenerating()) {
            stopGenerating()
        }
        ChatManager.newChat()
        Log.d(TAG, "New chat created")
    }

    fun deleteChatById(id: String) {
        viewModelScope.launch {
            ChatManager.deleteChatById(id, appContext)
        }
    }

    fun deleteMessage(messageId: String) {
        ChatManager.deleteMessage(messageId)
        saveCurrentChat()
    }
    //endregion

    //region Message Sending
    fun sendMessage(input: String) {
        if (UIStateManager.isGenerating()) {
            Log.w(TAG, "Already generating, ignoring new message")
            return
        }

        currentGenerationJob?.cancel()
        currentGenerationJob = viewModelScope.launch {
            try {
                executeSendMessage(input)
            } catch (e: CancellationException) {
                Log.d(TAG, "Message sending cancelled")
                UIStateManager.setStateIdle()
            } catch (e: Exception) {
                Log.e(TAG, "Error sending message", e)
                UIStateManager.setStateError("Error sending message", cause = e)
            }
        }
    }

    private suspend fun executeSendMessage(input: String) {
        // Add user message
        ChatManager.addMessage(Message(
            role = Role.User,
            text = input,
            id = UUID.randomUUID().toString()
        ))

        // Prepare assistant/tool message
        val messageId = UUID.randomUUID().toString()
        val isTool = ToolCallingManager.isToolSelected()

        ChatManager.addMessage(Message(
            role = if (isTool) Role.Tool else Role.Assistant,
            text = "",
            id = messageId,
            tool = if (isTool) RunningTool(
                toolName = ToolCallingManager.getSelectedTool().toolName,
                toolPreview = "",
                toolOutput = ToolOutput()
            ) else null
        ))

        // Handle RAG if enabled
        if (_isRag.value) {
            handleRAGRequest(input, isTool, messageId)
        } else {
            streamMessage(input, isTool, messageId)
        }
    }

    private suspend fun handleRAGRequest(
        input: String,
        isTool: Boolean,
        messageId: String
    ) {
        if (!RAGManager.isRAGReady()) {
            Log.w(TAG, "RAG not ready, falling back to normal generation")
            streamMessage(input, isTool, messageId)
            return
        }

        try {
            UIStateManager.setStateLoading("Processing with RAG...")

            // Get augmented prompt
            val ragResult = RAGManager.handleRAGRequest(input)

            if (ragResult.has("error")) {
                Log.e(TAG, "RAG failed: ${ragResult.getString("error")}")
                streamMessage(input, isTool, messageId)
                return
            }

            val augmentedPrompt = ragResult.getString("success")

            // Ensure generation model is ready
            val modelResult = RAGManager.ensureGenerationModelReady(
                currentModel = selectedModel.value,
                onStateUpdate = { _modelLoadingState.value = it }
            )

            if (modelResult.has("error")) {
                Log.e(TAG, "Model switch failed: ${modelResult.getString("error")}")
                UIStateManager.setStateError("Model loading failed")
                return
            }

            // Stream with augmented prompt
            streamMessage(augmentedPrompt, isTool, messageId)

        } catch (e: Exception) {
            Log.e(TAG, "RAG processing failed", e)
            streamMessage(input, isTool, messageId)
        }
    }

    private suspend fun streamMessage(
        prompt: String,
        enableTools: Boolean,
        messageId: String
    ) {
        TextGenerationWorker.streamAndRender(
            prompt = prompt,
            appContext = appContext,
            enableTools = enableTools,
            messageId = messageId,
            isRegeneration = false,
            existingMessages = messages.value,
            onToolExecution = { result ->
                saveCurrentChat()
            }
        )

        // CRITICAL: Save and refresh chat list after streaming completes
        saveCurrentChat()
    }
    //endregion

    //region Regeneration
    fun regenerateResponse(model: ModelData?, messageId: String) {
        if (model == null) {
            Log.w(TAG, "Cannot regenerate: null model")
            return
        }

        val messageIndex = messages.value.indexOfFirst { it.id == messageId }
        if (messageIndex == -1) {
            Log.w(TAG, "Cannot regenerate: message not found")
            return
        }

        val targetMessage = messages.value[messageIndex]
        if (targetMessage.role != Role.Assistant && targetMessage.role != Role.Tool) {
            Log.w(TAG, "Cannot regenerate: invalid role")
            return
        }

        currentGenerationJob?.cancel()
        currentGenerationJob = viewModelScope.launch {
            try {
                executeRegenerate(model, messageId, messageIndex, targetMessage)
            } catch (e: CancellationException) {
                Log.d(TAG, "Regeneration cancelled")
                UIStateManager.setStateIdle()
            } catch (e: Exception) {
                Log.e(TAG, "Regeneration failed", e)
                UIStateManager.setStateError("Regeneration failed", cause = e)
            }
        }
    }

    private suspend fun executeRegenerate(
        model: ModelData,
        messageId: String,
        messageIndex: Int,
        targetMessage: Message
    ) {
        // Get user context
        val userContext = messages.value
            .take(messageIndex)
            .lastOrNull { it.role == Role.User }?.text.orEmpty()

        // Clear existing message
        ChatManager.updateStreamingMessage(
            messageId = messageId,
            text = "",
            thought = null,
            isFinal = false
        )

        // Switch model if needed
        if (selectedModel.value.id != model.id) {
            UIStateManager.toggleSwitchingModels(true)
            ModelManager.unloadModel()

            ModelManager.loadModelAwait(
                modelData = model.copy(
                    systemPrompt = "You are a helpful assistant that improves message clarity and accuracy.",
                    chatTemplate = ModelsList.defaultChatTemplate
                )
            ) { state ->
                _modelLoadingState.value = state
                if (state is LoadState.OnLoaded) {
                    selectedModel.value = model
                }
            }

            UIStateManager.toggleSwitchingModels(false)
        }

        // Build optimization prompt
        val optimizePrompt = buildString {
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
            append(targetMessage.text)
        }

        // Stream regenerated response
        TextGenerationWorker.streamAndRender(
            prompt = optimizePrompt,
            appContext = appContext,
            enableTools = false,
            messageId = messageId,
            isRegeneration = true,
            existingMessages = messages.value
        )

        saveCurrentChat()
    }
    //endregion

    //region Continue Generation
    suspend fun continueGenerating(message: Message) {
        try {
            // Try to load saved model state
            val root = UserDataManager.getRootNode()
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
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading model state", e)
        }

        // Continue streaming
        TextGenerationWorker.streamAndRender(
            prompt = message.text,
            appContext = appContext,
            enableTools = message.tool != null,
            messageId = message.id,
            isRegeneration = false,
            existingMessages = messages.value
        )

        saveCurrentChat()
    }
    //endregion

    //region Utility Functions
    fun stopGenerating() {
        currentGenerationJob?.cancel()
        TextGenerationWorker.stopGeneration()
        saveCurrentChat()
        Log.d(TAG, "Generation stopped")
    }

    fun saveCurrentChat() {
        viewModelScope.launch {
            try {
                ChatManager.saveChat(
                    messages = messages.value,
                    chatTitle = chatTitle.value,
                    chatId = chatId.value,
                    rootNode = UserDataManager.getRootNode(),
                    appContext = appContext
                )
                Log.d(TAG, "Chat saved successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving chat", e)
            }
        }
    }

    fun isMessageWaitingForFirstToken(messageId: String, messageText: String): Boolean {
        return messageText.isEmpty() && uiState.value.let { state ->
            state is ChatUiState.Generating && state.messageId == messageId
        }
    }

    fun isMessageExecutingTool(messageId: String): Boolean {
        return uiState.value.let { state ->
            state is ChatUiState.ExecutingTool && state.messageId == messageId
        }
    }
    //endregion

    //region Cleanup
    override fun onCleared() {
        super.onCleared()
        currentGenerationJob?.cancel()
        TextGenerationWorker.cleanup()
        Log.d(TAG, "ViewModel cleared")
    }
    //endregion
}