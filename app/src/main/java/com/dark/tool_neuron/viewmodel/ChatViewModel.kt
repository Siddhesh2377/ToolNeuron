package com.dark.tool_neuron.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.data.AppSettingsDataStore
import com.dark.tool_neuron.di.AppContainer
import com.dark.tool_neuron.engine.GenerationEvent
import com.dark.tool_neuron.models.messages.ContentType
import com.dark.tool_neuron.models.messages.ImageGenerationMetrics
import com.dark.tool_neuron.models.messages.MessageContent
import com.dark.tool_neuron.models.messages.Messages
import com.dark.tool_neuron.models.messages.RagResultItem
import com.dark.tool_neuron.models.messages.Role
import com.dark.tool_neuron.models.plugins.PluginExecutionMetrics
import com.dark.tool_neuron.models.plugins.PluginResultData
import com.dark.tool_neuron.plugins.PluginExecutionResult
import com.dark.tool_neuron.plugins.PluginManager
import com.dark.tool_neuron.state.AppStateManager
import com.dark.tool_neuron.worker.ChatManager
import com.dark.tool_neuron.worker.DiffusionConfig
import com.dark.tool_neuron.worker.DiffusionInferenceParams
import com.dark.tool_neuron.worker.GenerationManager
import com.dark.tool_neuron.tts.TTSManager
import com.dark.tool_neuron.tts.TTSSettings
import com.dark.tool_neuron.worker.LlmModelWorker
import com.mp.ai_gguf.models.DecodingMetrics
import com.mp.ai_gguf.toolcalling.ToolCall
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val chatManager: ChatManager,
    private val generationManager: GenerationManager
) : ViewModel() {

    private val appSettings = AppSettingsDataStore(context)
    private val ttsDataStore = com.dark.tool_neuron.tts.TTSDataStore(context)

    val streamingEnabled: StateFlow<Boolean> = appSettings.streamingEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val chatMemoryEnabled: StateFlow<Boolean> = appSettings.chatMemoryEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _messages = mutableStateListOf<Messages>()
    val messages: SnapshotStateList<Messages> = _messages

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _currentChatId = MutableStateFlow<String?>(null)
    val currentChatId: StateFlow<String?> = _currentChatId

    private val isNewConversation: Boolean get() = _currentChatId.value == null

    // Streaming state
    private val _streamingUserMessage = MutableStateFlow<String?>(null)
    val streamingUserMessage: StateFlow<String?> = _streamingUserMessage

    private val _streamingAssistantMessage = MutableStateFlow("")
    val streamingAssistantMessage: StateFlow<String> = _streamingAssistantMessage

    // Image generation state
    private val _streamingImage = MutableStateFlow<Bitmap?>(null)
    val streamingImage: StateFlow<Bitmap?> = _streamingImage

    private val _imageGenerationProgress = MutableStateFlow(0f)
    val imageGenerationProgress: StateFlow<Float> = _imageGenerationProgress

    private val _imageGenerationStep = MutableStateFlow("")
    val imageGenerationStep: StateFlow<String> = _imageGenerationStep

    // Tool calling state
    private val _currentToolName = MutableStateFlow<String?>(null)
    val currentToolName: StateFlow<String?> = _currentToolName

    // Track generation job for proper cancellation
    private var generationJob: Job? = null

    // Track current generation state
    private var currentUserMessage: Messages? = null
    private var currentGeneratedContent: String = ""
    private var currentMetrics: DecodingMetrics? = null
    private var currentImageMetrics: ImageGenerationMetrics? = null
    private var currentGeneratedImage: Bitmap? = null
    private var imageGenerationStartTime: Long = 0

    // Track if user message was already added to prevent duplicates
    private var userMessageAdded = false

    // UI state
    private val _showDynamicWindow = MutableStateFlow(false)
    val showDynamicWindow: StateFlow<Boolean> = _showDynamicWindow

    private val _showModelList = MutableStateFlow(false)
    val showModelList: StateFlow<Boolean> = _showModelList

    private val _currentGenerationType = MutableStateFlow(GenerationManager.ModelType.TEXT_GENERATION)
    val currentGenerationType: StateFlow<GenerationManager.ModelType> = _currentGenerationType

    // Model state
    val isTextModelLoaded = LlmModelWorker.isGgufModelLoaded
    val isImageModelLoaded = LlmModelWorker.isDiffusionModelLoaded

    // TTS state
    val ttsPlayingMsgId = TTSManager.currentPlayingMsgId
    val ttsIsPlaying = TTSManager.isPlaying
    val ttsSynthesizing = TTSManager.isSynthesizing
    val ttsModelLoaded = TTSManager.isModelLoaded
    val ttsAvailableVoices = TTSManager.availableVoices

    // RAG state
    private val _isRagEnabled = MutableStateFlow(false)
    val isRagEnabled: StateFlow<Boolean> = _isRagEnabled

    private val _currentRagContext = MutableStateFlow<String?>(null)
    val currentRagContext: StateFlow<String?> = _currentRagContext

    private val _currentRagResults = MutableStateFlow<List<RagQueryDisplayResult>>(emptyList())
    val currentRagResults: StateFlow<List<RagQueryDisplayResult>> = _currentRagResults

    // Processing phase indicator
    private val _currentProcessingPhase = MutableStateFlow<String?>(null)
    val currentProcessingPhase: StateFlow<String?> = _currentProcessingPhase

    fun setProcessingPhase(phase: String?) {
        _currentProcessingPhase.value = phase
    }

    // Memory state
    private val _currentMemoryContext = MutableStateFlow<String?>(null)
    val currentMemoryContext: StateFlow<String?> = _currentMemoryContext

    private val _currentMemoryResults = MutableStateFlow<List<com.dark.tool_neuron.worker.ScoredVaultContent>>(emptyList())
    val currentMemoryResults: StateFlow<List<com.dark.tool_neuron.worker.ScoredVaultContent>> = _currentMemoryResults

    // ==================== RAG Controls ====================

    fun setRagEnabled(enabled: Boolean) {
        _isRagEnabled.value = enabled
    }

    fun setRagContext(context: String?, results: List<RagQueryDisplayResult> = emptyList()) {
        _currentRagContext.value = context
        _currentRagResults.value = results
    }

    fun clearRagContext() {
        _currentRagContext.value = null
        _currentRagResults.value = emptyList()
    }

    // ==================== Memory Controls ====================

    fun setMemoryContext(context: String, results: List<com.dark.tool_neuron.worker.ScoredVaultContent>) {
        _currentMemoryContext.value = context
        _currentMemoryResults.value = results
    }

    fun clearMemoryContext() {
        _currentMemoryContext.value = null
        _currentMemoryResults.value = emptyList()
    }

    // ==================== Chat Management ====================

    fun startNewConversation() {
        _currentChatId.value = null
        _messages.clear()
        _streamingUserMessage.value = null
        _streamingAssistantMessage.value = ""
        _streamingImage.value = null
        _imageGenerationProgress.value = 0f
        _imageGenerationStep.value = ""
        currentUserMessage = null
        currentGeneratedContent = ""
        currentGeneratedImage = null
        currentMetrics = null
        currentImageMetrics = null
        userMessageAdded = false
        _error.value = null
        _currentToolName.value = null
        AppStateManager.setHasMessages(false)
    }

    fun loadChat(chatId: String) {
        viewModelScope.launch {
            _currentChatId.value = chatId
            chatManager.getChatMessages(chatId).onSuccess { loadedMessages ->
                _messages.clear()
                _messages.addAll(loadedMessages)
                AppStateManager.setHasMessages(loadedMessages.isNotEmpty())
            }.onFailure { e ->
                _error.value = "Failed to load chat: ${e.message}"
                AppStateManager.setError("Failed to load chat: ${e.message}")
            }
        }
    }

    // ==================== Model Selection ====================

    fun switchToTextGeneration() {
        if (!generationManager.isTextModelLoaded()) {
            _error.value = "Text generation model not loaded"
            return
        }
        _currentGenerationType.value = GenerationManager.ModelType.TEXT_GENERATION
        generationManager.setCurrentModelType(GenerationManager.ModelType.TEXT_GENERATION)
    }

    fun switchToImageGeneration() {
        if (!generationManager.isImageModelLoaded()) {
            _error.value = "Image generation model not loaded"
            return
        }
        _currentGenerationType.value = GenerationManager.ModelType.IMAGE_GENERATION
        generationManager.setCurrentModelType(GenerationManager.ModelType.IMAGE_GENERATION)
    }

    // ==================== Text Generation ====================

    fun sendTextMessage(prompt: String, maxTokens: Int = 512) {
        if (!generationManager.isTextModelLoaded()) {
            _error.value = "Please load a text generation model first"
            AppStateManager.setError("Please load a text generation model first")
            return
        }

        if (_isGenerating.value) return

        _streamingUserMessage.value = prompt
        userMessageAdded = false

        viewModelScope.launch {
            if (isNewConversation) {
                currentUserMessage = Messages(
                    msgId = "",
                    role = Role.User,
                    content = MessageContent(
                        contentType = ContentType.Text,
                        content = prompt
                    ),
                    decodingMetrics = null
                )
                AppStateManager.setHasMessages(true)

                if (PluginManager.hasEnabledTools()) {
                    generateTextWithToolsNewChat(prompt, maxTokens)
                } else {
                    generateTextForNewChat(prompt, maxTokens)
                }
            } else {
                val chatId = _currentChatId.value
                if (chatId == null) {
                    _error.value = "No chat selected"
                    AppStateManager.setError("No chat selected")
                    return@launch
                }

                chatManager.addUserMessage(chatId, prompt).onSuccess { userMessage ->
                    currentUserMessage = userMessage
                    AppStateManager.setHasMessages(true)

                    if (PluginManager.hasEnabledTools()) {
                        generateTextWithToolsExisting(chatId, userMessage, maxTokens)
                    } else {
                        generateText(chatId, userMessage, maxTokens)
                    }
                }.onFailure { e ->
                    _error.value = "Failed to save message: ${e.message}"
                    _streamingUserMessage.value = null
                    currentUserMessage = null
                    AppStateManager.setError("Failed to save message: ${e.message}")
                }
            }
        }
    }

    // ==================== Tool-Calling Generation (single tool call) ====================

    /**
     * Generate text with tool calling support for new chats.
     * Single pass: generate → if tool call detected → execute → done.
     */
    private fun generateTextWithToolsNewChat(prompt: String, maxTokens: Int) {
        generationJob = viewModelScope.launch {
            _isGenerating.value = true
            _error.value = null
            _streamingAssistantMessage.value = ""
            currentGeneratedContent = ""
            currentMetrics = null

            AppStateManager.setGeneratingText()

            val isStreaming = streamingEnabled.value
            val hasMemory = chatMemoryEnabled.value
            val config = PluginManager.getToolCallingConfig()

            // Build conversation messages
            val conversationMessages = mutableListOf<JSONObject>()
            if (hasMemory) {
                _messages.forEach { msg ->
                    when (msg.role) {
                        Role.User -> conversationMessages.add(
                            JSONObject().put("role", "user").put("content", msg.content.content)
                        )
                        Role.Assistant -> {
                            if (msg.content.contentType == ContentType.Text) {
                                conversationMessages.add(
                                    JSONObject().put("role", "assistant").put("content", msg.content.content)
                                )
                            }
                        }
                    }
                }
            }

            val finalPrompt = _currentRagContext.value?.let { "$it\n\n### User Query:\n$prompt" } ?: prompt
            conversationMessages.add(JSONObject().put("role", "user").put("content", finalPrompt))

            var toolCallDetected = false
            var toolCallName = ""
            var toolCallArgs = ""

            try {
                generationManager.generateMultiTurnStreaming(
                    JSONArray(conversationMessages).toString(), config.maxTokensPerTurn
                ).collect { event ->
                    when (event) {
                        is GenerationEvent.Token -> {
                            currentGeneratedContent += event.text
                            if (isStreaming) {
                                _streamingAssistantMessage.value = currentGeneratedContent
                            }
                        }
                        is GenerationEvent.ToolCall -> {
                            toolCallDetected = true
                            toolCallName = event.name
                            toolCallArgs = event.args
                        }
                        is GenerationEvent.Done -> { /* handled after collect */ }
                        is GenerationEvent.Metrics -> { currentMetrics = event.metrics }
                        is GenerationEvent.Error -> { _error.value = event.message }
                    }
                }
            } catch (e: Exception) {
                handleTextGenerationException(prompt, e)
                return@launch
            }

            // Fallback: try parsing tool call from content
            if (!toolCallDetected && currentGeneratedContent.isNotBlank()) {
                tryParseToolCallFromContent(currentGeneratedContent)?.let { (name, args) ->
                    toolCallDetected = true
                    toolCallName = name
                    toolCallArgs = args
                    Log.d("ChatViewModel", "Fallback tool call detection: $name")
                }
            }

            // Execute tool if detected, then push AI to interpret the result
            if (toolCallDetected) {
                _streamingAssistantMessage.value = ""
                currentGeneratedContent = ""
                handlePluginToolCall(toolCallName, toolCallArgs)

                // Get the tool result data from the last plugin result message
                val lastPluginResult = _messages.lastOrNull {
                    it.content.contentType == ContentType.PluginResult
                }
                val toolResultData = lastPluginResult?.content?.pluginResultData?.resultData

                if (toolResultData != null) {
                    // Push AI to generate interpretation of the tool result
                    generateResponseFromToolResult(conversationMessages, toolResultData, toolCallName)
                }
            }

            _currentToolName.value = null
            val filteredContent = filterToolCallSyntax(currentGeneratedContent)
            _streamingAssistantMessage.value = filteredContent

            if (filteredContent.isNotBlank()) {
                createChatWithMessages(prompt, filteredContent, currentMetrics)
            } else if (_messages.any { it.content.contentType == ContentType.PluginResult }) {
                // Tool executed but no text — persist chat silently
                chatManager.createNewChat().onSuccess { newChatId ->
                    _currentChatId.value = newChatId
                    chatManager.addUserMessage(newChatId, prompt)
                    _messages.filter { it.content.contentType == ContentType.PluginResult }
                        .forEach { chatManager.addMessage(newChatId, it) }
                }
                AppStateManager.setGenerationComplete()
                AppStateManager.chatRefreshed()
                resetStreamingState()
            } else {
                AppStateManager.setGenerationComplete()
                resetStreamingState()
            }
        }
    }

    /**
     * Generate text with tool calling support for existing chats.
     * Single pass: generate → if tool call detected → execute → done.
     */
    private fun generateTextWithToolsExisting(chatId: String, userMessage: Messages, maxTokens: Int) {
        generationJob = viewModelScope.launch {
            _isGenerating.value = true
            _error.value = null
            _streamingAssistantMessage.value = ""
            currentGeneratedContent = ""
            currentMetrics = null

            AppStateManager.setGeneratingText()

            val isStreaming = streamingEnabled.value
            val hasMemory = chatMemoryEnabled.value
            val config = PluginManager.getToolCallingConfig()

            // Build conversation messages
            val conversationMessages = mutableListOf<JSONObject>()
            if (hasMemory) {
                _messages.forEach { msg ->
                    when (msg.role) {
                        Role.User -> conversationMessages.add(
                            JSONObject().put("role", "user").put("content", msg.content.content)
                        )
                        Role.Assistant -> {
                            if (msg.content.contentType == ContentType.Text) {
                                conversationMessages.add(
                                    JSONObject().put("role", "assistant").put("content", msg.content.content)
                                )
                            }
                        }
                    }
                }
            }

            var userPrompt = userMessage.content.content
            _currentRagContext.value?.let { userPrompt = "$it\n\n$userPrompt" }
            conversationMessages.add(JSONObject().put("role", "user").put("content", userPrompt))

            var toolCallDetected = false
            var toolCallName = ""
            var toolCallArgs = ""

            try {
                generationManager.generateMultiTurnStreaming(
                    JSONArray(conversationMessages).toString(), config.maxTokensPerTurn
                ).collect { event ->
                    when (event) {
                        is GenerationEvent.Token -> {
                            currentGeneratedContent += event.text
                            if (isStreaming) {
                                _streamingAssistantMessage.value = currentGeneratedContent
                            }
                        }
                        is GenerationEvent.ToolCall -> {
                            toolCallDetected = true
                            toolCallName = event.name
                            toolCallArgs = event.args
                        }
                        is GenerationEvent.Done -> { /* handled after collect */ }
                        is GenerationEvent.Metrics -> { currentMetrics = event.metrics }
                        is GenerationEvent.Error -> { _error.value = event.message }
                    }
                }
            } catch (e: Exception) {
                handleTextGenerationExceptionExisting(chatId, userMessage, e)
                return@launch
            }

            // Fallback: try parsing tool call from content
            if (!toolCallDetected && currentGeneratedContent.isNotBlank()) {
                tryParseToolCallFromContent(currentGeneratedContent)?.let { (name, args) ->
                    toolCallDetected = true
                    toolCallName = name
                    toolCallArgs = args
                    Log.d("ChatViewModel", "Fallback tool call detection: $name")
                }
            }

            // Execute tool if detected, then push AI to interpret the result
            if (toolCallDetected) {
                _streamingAssistantMessage.value = ""
                currentGeneratedContent = ""
                handlePluginToolCall(toolCallName, toolCallArgs)

                // Get the tool result data from the last plugin result message
                val lastPluginResult = _messages.lastOrNull {
                    it.content.contentType == ContentType.PluginResult
                }
                val toolResultData = lastPluginResult?.content?.pluginResultData?.resultData

                if (toolResultData != null) {
                    // Push AI to generate interpretation of the tool result
                    generateResponseFromToolResult(conversationMessages, toolResultData, toolCallName)
                }
            }

            _currentToolName.value = null
            val filteredContent = filterToolCallSyntax(currentGeneratedContent)
            _streamingAssistantMessage.value = filteredContent

            // Add user message
            if (!userMessageAdded) {
                _messages.add(userMessage)
                userMessageAdded = true
            }

            val ragResultItems = _currentRagResults.value.takeIf { it.isNotEmpty() }?.map { result ->
                RagResultItem(
                    ragName = result.ragName,
                    content = result.content,
                    score = result.score,
                    nodeId = result.nodeId
                )
            }

            if (filteredContent.isNotBlank()) {
                val assistantMessage = Messages(
                    role = Role.Assistant,
                    content = MessageContent(
                        contentType = ContentType.Text,
                        content = filteredContent
                    ),
                    decodingMetrics = currentMetrics,
                    ragResults = ragResultItems
                )
                _messages.add(assistantMessage)
                chatManager.addAssistantMessage(chatId, filteredContent, currentMetrics, ragResultItems)
                AppStateManager.setGenerationComplete()
                AppStateManager.chatRefreshed()
                val spokenMsgId = assistantMessage.msgId
                resetStreamingState()
                viewModelScope.launch { autoSpeakIfEnabled(filteredContent, spokenMsgId) }
            } else {
                AppStateManager.setGenerationComplete()
                AppStateManager.chatRefreshed()
                resetStreamingState()
            }
        }
    }

    // ==================== Single-Turn Generation (backward compatible) ====================

    private fun generateTextForNewChat(prompt: String, maxTokens: Int) {
        generationJob = viewModelScope.launch {
            _isGenerating.value = true
            _error.value = null
            _streamingAssistantMessage.value = ""
            currentGeneratedContent = ""
            currentMetrics = null

            AppStateManager.setGeneratingText()

            val isStreaming = streamingEnabled.value
            var tokenBuffer = StringBuilder()
            var tokenCount = 0
            var lastUpdateTime = System.currentTimeMillis()
            val updateIntervalMs = 50L
            val tokenBatchSize = 3

            try {
                // Prepend RAG context if available
                val finalPrompt = _currentRagContext.value?.let { ragContext ->
                    "$ragContext\n\n### User Query:\n$prompt"
                } ?: prompt

                generationManager.generateTextStreaming(finalPrompt, maxTokens).collect { event ->
                    when (event) {
                        is GenerationEvent.Token -> {
                            currentGeneratedContent += event.text
                            tokenBuffer.append(event.text)
                            tokenCount++

                            if (isStreaming) {
                                val currentTime = System.currentTimeMillis()
                                val shouldUpdate = tokenCount >= tokenBatchSize ||
                                        (currentTime - lastUpdateTime) >= updateIntervalMs

                                if (shouldUpdate) {
                                    _streamingAssistantMessage.value = currentGeneratedContent
                                    tokenBuffer.clear()
                                    tokenCount = 0
                                    lastUpdateTime = currentTime
                                }
                            }
                        }

                        is GenerationEvent.Done -> {
                            _streamingAssistantMessage.value = currentGeneratedContent
                            createChatWithMessages(prompt, currentGeneratedContent, currentMetrics)
                        }

                        is GenerationEvent.Error -> {
                            handleTextGenerationError(prompt, event.message)
                        }

                        is GenerationEvent.Metrics -> {
                            currentMetrics = event.metrics
                        }

                        is GenerationEvent.ToolCall -> {
                            Log.e("ChatViewModel", "Tool call received in single-turn (new chat): ${event.name}")
                        }
                    }
                }
            } catch (e: Exception) {
                handleTextGenerationException(prompt, e)
            }
        }
    }

    private fun generateText(chatId: String, userMessage: Messages, maxTokens: Int) {
        generationJob = viewModelScope.launch {
            _isGenerating.value = true
            _error.value = null
            _streamingAssistantMessage.value = ""
            currentGeneratedContent = ""
            currentMetrics = null

            AppStateManager.setGeneratingText()

            val isStreaming = streamingEnabled.value
            val hasMemory = chatMemoryEnabled.value
            val tokenBuffer = StringBuilder()
            var tokenCount = 0
            var lastUpdateTime = System.currentTimeMillis()
            val updateIntervalMs = 50L
            val tokenBatchSize = 3

            try {
                var conversationPrompt = if (hasMemory) {
                    generationManager.buildConversationPrompt(
                        _messages, userMessage.content.content
                    )
                } else {
                    generationManager.buildConversationPrompt(
                        emptyList(), userMessage.content.content
                    )
                }

                // Prepend RAG context if available
                _currentRagContext.value?.let { ragContext ->
                    conversationPrompt = "$ragContext\n\n$conversationPrompt"
                }

                generationManager.generateTextStreaming(conversationPrompt, maxTokens)
                    .collect { event ->
                        when (event) {
                            is GenerationEvent.Token -> {
                                currentGeneratedContent += event.text
                                tokenBuffer.append(event.text)
                                tokenCount++

                                if (isStreaming) {
                                    val currentTime = System.currentTimeMillis()
                                    val shouldUpdate = tokenCount >= tokenBatchSize ||
                                            (currentTime - lastUpdateTime) >= updateIntervalMs

                                    if (shouldUpdate) {
                                        _streamingAssistantMessage.value = currentGeneratedContent
                                        tokenBuffer.clear()
                                        tokenCount = 0
                                        lastUpdateTime = currentTime
                                    }
                                }
                            }

                            is GenerationEvent.Done -> {
                                _streamingAssistantMessage.value = currentGeneratedContent

                                // Add user message first if not already added
                                if (!userMessageAdded) {
                                    _messages.add(userMessage)
                                    userMessageAdded = true
                                }

                                // Convert current RAG results to serializable format
                                val ragResultItems = _currentRagResults.value.takeIf { it.isNotEmpty() }?.map { result ->
                                    RagResultItem(
                                        ragName = result.ragName,
                                        content = result.content,
                                        score = result.score,
                                        nodeId = result.nodeId
                                    )
                                }

                                // Filter out tool call syntax from the generated content
                                val filteredContent = filterToolCallSyntax(currentGeneratedContent)

                                // Only add assistant message if there's actual content
                                if (filteredContent.isNotBlank()) {
                                    val assistantMessage = Messages(
                                        role = Role.Assistant,
                                        content = MessageContent(
                                            contentType = ContentType.Text,
                                            content = filteredContent
                                        ),
                                        decodingMetrics = currentMetrics,
                                        ragResults = ragResultItems
                                    )
                                    _messages.add(assistantMessage)

                                    // Save to vault
                                    chatManager.addAssistantMessage(
                                        chatId, filteredContent, currentMetrics, ragResultItems
                                    )
                                    AppStateManager.setGenerationComplete()
                                    AppStateManager.chatRefreshed()
                                    val spokenMsgId = assistantMessage.msgId
                                    resetStreamingState()
                                    viewModelScope.launch { autoSpeakIfEnabled(filteredContent, spokenMsgId) }
                                } else {
                                    Log.d("ChatViewModel", "Skipped empty assistant message (was only tool call syntax)")
                                    AppStateManager.setGenerationComplete()
                                    AppStateManager.chatRefreshed()
                                    resetStreamingState()
                                }
                            }

                            is GenerationEvent.Error -> {
                                handleTextGenerationErrorExisting(chatId, userMessage, event.message)
                            }

                            is GenerationEvent.Metrics -> {
                                currentMetrics = event.metrics
                            }

                            is GenerationEvent.ToolCall -> {
                                Log.e("ChatViewModel", "Tool call received in single-turn (existing chat): ${event.name}")
                            }
                        }
                    }
            } catch (e: Exception) {
                handleTextGenerationExceptionExisting(chatId, userMessage, e)
            }
        }
    }

    // ==================== Image Generation ====================

    fun sendImageRequest(
        prompt: String,
        negativePrompt: String? = null,
        steps: Int? = null,
        cfgScale: Float? = null,
        seed: Long = -1L,
        width: Int? = null,
        height: Int? = null,
        scheduler: String? = null
    ) {
        if (!generationManager.isImageModelLoaded()) {
            _error.value = "Please load an image generation model first"
            AppStateManager.setError("Please load an image generation model first")
            return
        }

        if (_isGenerating.value) return

        viewModelScope.launch {
            // Get current diffusion model ID
            val modelId = LlmModelWorker.currentDiffusionModelId.value
            if (modelId == null) {
                _error.value = "Model configuration not found"
                return@launch
            }

            // Get config from repository
            val config = getModelConfig(modelId)

            val inferenceParams = if (config != null) {
                DiffusionInferenceParams.fromJson(config.modelInferenceParams)
            } else {
                DiffusionInferenceParams()
            }

            val diffusionConfig = if (config != null) {
                parseDiffusionConfig(config)
            } else {
                DiffusionConfig()
            }

            // Use provided values or fall back to stored config
            val finalNegativePrompt = negativePrompt ?: inferenceParams.negativePrompt
            val finalSteps = steps ?: inferenceParams.steps
            val finalCfgScale = cfgScale ?: inferenceParams.cfgScale
            val finalWidth = width ?: diffusionConfig.width
            val finalHeight = height ?: diffusionConfig.height
            val finalScheduler = scheduler ?: inferenceParams.scheduler

            _streamingUserMessage.value = prompt
            imageGenerationStartTime = System.currentTimeMillis()
            userMessageAdded = false

            if (isNewConversation) {
                currentUserMessage = Messages(
                    msgId = "",
                    role = Role.User,
                    content = MessageContent(
                        contentType = ContentType.Text,
                        content = "Generate image: $prompt"
                    )
                )
                AppStateManager.setHasMessages(true)
                generateImageForNewChat(
                    prompt,
                    finalNegativePrompt,
                    finalSteps,
                    finalCfgScale,
                    seed,
                    finalWidth,
                    finalHeight,
                    finalScheduler
                )
            } else {
                val chatId = _currentChatId.value
                if (chatId == null) {
                    _error.value = "No chat selected"
                    return@launch
                }

                chatManager.addUserMessage(chatId, "Generate image: $prompt").onSuccess { userMessage ->
                    currentUserMessage = userMessage
                    AppStateManager.setHasMessages(true)
                    generateImage(
                        chatId,
                        userMessage,
                        prompt,
                        finalNegativePrompt,
                        finalSteps,
                        finalCfgScale,
                        seed,
                        finalWidth,
                        finalHeight,
                        finalScheduler
                    )
                }.onFailure { e ->
                    _error.value = "Failed to save message: ${e.message}"
                    resetStreamingState()
                }
            }
        }
    }

    // Also add this helper if you don't have it
    suspend fun getModelConfig(modelId: String): com.dark.tool_neuron.models.table_schema.ModelConfig? {
        return AppContainer.getModelRepository().getConfigByModelId(modelId)
    }

    private fun parseDiffusionConfig(config: com.dark.tool_neuron.models.table_schema.ModelConfig): DiffusionConfig {
        if (config.modelLoadingParams == null) {
            return DiffusionConfig()
        }

        return try {
            val json = org.json.JSONObject(config.modelLoadingParams)
            DiffusionConfig(
                textEmbeddingSize = json.optInt("text_embedding_size", 768),
                runOnCpu = json.optBoolean("run_on_cpu", false),
                useCpuClip = json.optBoolean("use_cpu_clip", true),
                isPony = json.optBoolean("is_pony", false),
                httpPort = json.optInt("http_port", 8081),
                safetyMode = json.optBoolean("safety_mode", false),
                width = json.optInt("width", 512),
                height = json.optInt("height", 512)
            )
        } catch (e: Exception) {
            DiffusionConfig()
        }
    }

    private fun generateImageForNewChat(
        prompt: String,
        negativePrompt: String,
        steps: Int,
        cfgScale: Float,
        seed: Long,
        width: Int,
        height: Int,
        scheduler: String
    ) {
        generationJob = viewModelScope.launch {
            _isGenerating.value = true
            _error.value = null
            _streamingImage.value = null
            _imageGenerationProgress.value = 0f
            currentGeneratedImage = null

            AppStateManager.setGeneratingImage()

            try {
                generationManager.generateImageStreaming(
                    prompt, negativePrompt, steps, cfgScale, seed, width, height, scheduler
                ).collect { event ->
                    when (event) {
                        is LlmModelWorker.DiffusionGenerationEvent.Progress -> {
                            _imageGenerationProgress.value = event.progress
                            _imageGenerationStep.value = "Step ${event.currentStep}/${event.totalSteps}"
                            event.intermediateImage?.let {
                                _streamingImage.value = it
                            }
                        }

                        is LlmModelWorker.DiffusionGenerationEvent.Complete -> {
                            _imageGenerationProgress.value = 1f
                            _streamingImage.value = event.image
                            currentGeneratedImage = event.image

                            val generationTime = System.currentTimeMillis() - imageGenerationStartTime
                            currentImageMetrics = ImageGenerationMetrics(
                                steps = steps,
                                cfgScale = cfgScale,
                                seed = event.seed,
                                width = event.width,
                                height = event.height,
                                scheduler = scheduler,
                                generationTimeMs = generationTime
                            )

                            _isGenerating.value = false

                            // Convert image to base64
                            val imageBase64 = generationManager.bitmapToBase64(event.image)
                            createChatWithImageMessage(
                                "Generate image: $prompt",
                                imageBase64,
                                prompt,
                                event.seed
                            )
                        }

                        is LlmModelWorker.DiffusionGenerationEvent.Error -> {
                            handleImageGenerationError(prompt, event.message)
                        }
                    }
                }
            } catch (e: Exception) {
                handleImageGenerationException(prompt, e)
            }
        }
    }

    private fun generateImage(
        chatId: String,
        userMessage: Messages,
        prompt: String,
        negativePrompt: String,
        steps: Int,
        cfgScale: Float,
        seed: Long,
        width: Int,
        height: Int,
        scheduler: String
    ) {
        generationJob = viewModelScope.launch {
            _isGenerating.value = true
            _error.value = null
            _streamingImage.value = null
            _imageGenerationProgress.value = 0f

            AppStateManager.setGeneratingImage()

            try {
                generationManager.generateImageStreaming(
                    prompt, negativePrompt, steps, cfgScale, seed, width, height, scheduler
                ).collect { event ->
                    when (event) {
                        is LlmModelWorker.DiffusionGenerationEvent.Progress -> {
                            _imageGenerationProgress.value = event.progress
                            _imageGenerationStep.value = "Step ${event.currentStep}/${event.totalSteps}"
                            event.intermediateImage?.let {
                                _streamingImage.value = it
                            }
                        }

                        is LlmModelWorker.DiffusionGenerationEvent.Complete -> {
                            _imageGenerationProgress.value = 1f
                            _streamingImage.value = event.image
                            _isGenerating.value = false

                            val generationTime = System.currentTimeMillis() - imageGenerationStartTime
                            currentImageMetrics = ImageGenerationMetrics(
                                steps = steps,
                                cfgScale = cfgScale,
                                seed = event.seed,
                                width = event.width,
                                height = event.height,
                                scheduler = scheduler,
                                generationTimeMs = generationTime
                            )

                            if (!userMessageAdded) {
                                _messages.add(userMessage)
                                userMessageAdded = true
                            }

                            val imageBase64 = generationManager.bitmapToBase64(event.image)
                            val imageMessage = Messages(
                                role = Role.Assistant,
                                content = MessageContent(
                                    contentType = ContentType.Image,
                                    content = "Generated image for: $prompt",
                                    imageData = imageBase64,
                                    imagePrompt = prompt,
                                    imageSeed = event.seed
                                ),
                                imageMetrics = currentImageMetrics
                            )
                            _messages.add(imageMessage)

                            // Save to vault
                            chatManager.addImageMessage(chatId, imageBase64, prompt, event.seed, currentImageMetrics)

                            AppStateManager.setGenerationComplete()
                            AppStateManager.chatRefreshed()
                            resetStreamingState()
                        }

                        is LlmModelWorker.DiffusionGenerationEvent.Error -> {
                            handleImageGenerationErrorExisting(chatId, userMessage, prompt, event.message)
                        }
                    }
                }
            } catch (e: Exception) {
                handleImageGenerationExceptionExisting(chatId, userMessage, prompt, e)
            }
        }
    }

    // ==================== Helper Functions ====================

    private suspend fun createChatWithMessages(
        userPrompt: String,
        assistantResponse: String,
        metrics: DecodingMetrics?
    ) {
        // Filter out tool call syntax
        val filteredResponse = filterToolCallSyntax(assistantResponse)

        // Convert current RAG results to serializable format before clearing
        val ragResultItems = _currentRagResults.value.takeIf { it.isNotEmpty() }?.map { result ->
            RagResultItem(
                ragName = result.ragName,
                content = result.content,
                score = result.score,
                nodeId = result.nodeId
            )
        }

        // Get any plugin results that were added during generation
        val pluginResults = _messages.filter { it.content.contentType == ContentType.PluginResult }
        Log.d("ChatViewModel", "Creating new chat with ${pluginResults.size} plugin results, filtered response: '${filteredResponse.take(50)}'")

        chatManager.createNewChat().onSuccess { newChatId ->
            _currentChatId.value = newChatId
            Log.d("ChatViewModel", "Created chat: $newChatId")

            chatManager.addUserMessage(newChatId, userPrompt).onSuccess { userMessage ->
                Log.d("ChatViewModel", "Saved user message")

                // Save plugin results first
                pluginResults.forEachIndexed { index, pluginMsg ->
                    chatManager.addMessage(newChatId, pluginMsg).onSuccess {
                        Log.d("ChatViewModel", "Saved plugin result ${index + 1}/${pluginResults.size}")
                    }
                }

                // Save assistant message if there's actual content
                if (filteredResponse.isNotBlank()) {
                    chatManager.addAssistantMessage(
                        newChatId, filteredResponse, metrics, ragResultItems
                    ).onSuccess {
                        Log.d("ChatViewModel", "Saved assistant message")
                    }
                }

                // Now reload the chat to get all messages with proper IDs
                chatManager.getChatMessages(newChatId).onSuccess { loadedMessages ->
                    Log.d("ChatViewModel", "Reloaded ${loadedMessages.size} messages from vault")
                    _messages.clear()
                    _messages.addAll(loadedMessages)
                    AppStateManager.setGenerationComplete()
                    AppStateManager.chatRefreshed()
                    val spokenMsgId = loadedMessages.lastOrNull { it.role == Role.Assistant }?.msgId
                    resetStreamingState()
                    viewModelScope.launch { autoSpeakIfEnabled(filteredResponse, spokenMsgId) }
                }.onFailure { e ->
                    Log.e("ChatViewModel", "Failed to reload chat: ${e.message}")
                    AppStateManager.setGenerationComplete()
                    resetStreamingState()
                }
            }.onFailure { e ->
                _error.value = "Failed to save chat: ${e.message}"
                AppStateManager.setError("Failed to save chat: ${e.message}")
            }
        }.onFailure { e ->
            _error.value = "Failed to create chat: ${e.message}"
            AppStateManager.setError("Failed to create chat: ${e.message}")
        }
    }

    private suspend fun createChatWithImageMessage(
        userPrompt: String,
        imageBase64: String,
        imagePrompt: String,
        seed: Long
    ) {
        chatManager.createNewChat().onSuccess { newChatId ->
            _currentChatId.value = newChatId

            chatManager.addUserMessage(newChatId, userPrompt).onSuccess { userMessage ->
                _messages.add(userMessage)
                userMessageAdded = true

                chatManager.addImageMessage(
                    newChatId, imageBase64, imagePrompt, seed, currentImageMetrics
                ).onSuccess { imageMessage ->
                    _messages.add(imageMessage)
                    AppStateManager.setGenerationComplete()
                    AppStateManager.chatRefreshed()
                    resetStreamingState()
                }
            }.onFailure { e ->
                _error.value = "Failed to save chat: ${e.message}"
            }
        }.onFailure { e ->
            _error.value = "Failed to create chat: ${e.message}"
        }
    }

    private fun handleTextGenerationError(prompt: String, errorMessage: String) {
        _streamingAssistantMessage.value = currentGeneratedContent
        _isGenerating.value = false
        _error.value = errorMessage
        AppStateManager.setError(errorMessage)

        if (currentGeneratedContent.isNotEmpty()) {
            viewModelScope.launch {
                createChatWithMessages(prompt, "Error: $errorMessage", null)
            }
        }
        resetStreamingState()
    }

    private fun handleTextGenerationException(prompt: String, exception: Exception) {
        _isGenerating.value = false
        _error.value = exception.message
        AppStateManager.setError(exception.message ?: "Unknown error")

        if (currentGeneratedContent.isNotEmpty()) {
            viewModelScope.launch {
                createChatWithMessages(prompt, "$currentGeneratedContent [incomplete]", currentMetrics)
            }
        }
        resetStreamingState()
    }

    private fun handleTextGenerationErrorExisting(chatId: String, userMessage: Messages, errorMessage: String) {
        _streamingAssistantMessage.value = currentGeneratedContent
        _isGenerating.value = false
        _error.value = errorMessage
        AppStateManager.setError(errorMessage)

        if (!userMessageAdded) {
            _messages.add(userMessage)
            userMessageAdded = true
        }

        val errorMsg = Messages(
            role = Role.Assistant,
            content = MessageContent(
                contentType = ContentType.Text,
                content = "Error: $errorMessage"
            )
        )
        _messages.add(errorMsg)
        resetStreamingState()
    }

    private fun handleTextGenerationExceptionExisting(chatId: String, userMessage: Messages, exception: Exception) {
        _isGenerating.value = false
        _error.value = exception.message
        AppStateManager.setError(exception.message ?: "Unknown error")

        if (currentGeneratedContent.isNotEmpty() && currentUserMessage != null) {
            if (!userMessageAdded) {
                _messages.add(currentUserMessage!!)
                userMessageAdded = true
            }

            val partialMessage = Messages(
                role = Role.Assistant,
                content = MessageContent(
                    contentType = ContentType.Text,
                    content = "$currentGeneratedContent [incomplete]"
                )
            )
            _messages.add(partialMessage)

            viewModelScope.launch {
                chatManager.addAssistantMessage(chatId, "$currentGeneratedContent [incomplete]", null)
            }
        }
        resetStreamingState()
    }

    private fun handleImageGenerationError(prompt: String, errorMessage: String) {
        _isGenerating.value = false
        _error.value = errorMessage
        AppStateManager.setError(errorMessage)
        resetStreamingState()
    }

    private fun handleImageGenerationException(prompt: String, exception: Exception) {
        _isGenerating.value = false
        _error.value = exception.message
        AppStateManager.setError(exception.message ?: "Unknown error")
        resetStreamingState()
    }

    private fun handleImageGenerationErrorExisting(chatId: String, userMessage: Messages, prompt: String, errorMessage: String) {
        _isGenerating.value = false
        _error.value = errorMessage
        AppStateManager.setError(errorMessage)

        if (!userMessageAdded) {
            _messages.add(userMessage)
            userMessageAdded = true
        }

        val errorMsg = Messages(
            role = Role.Assistant,
            content = MessageContent(
                contentType = ContentType.Text,
                content = "Error generating image: $errorMessage"
            )
        )
        _messages.add(errorMsg)
        resetStreamingState()
    }

    private fun handleImageGenerationExceptionExisting(chatId: String, userMessage: Messages, prompt: String, exception: Exception) {
        _isGenerating.value = false
        _error.value = exception.message
        AppStateManager.setError(exception.message ?: "Unknown error")

        if (!userMessageAdded) {
            _messages.add(userMessage)
            userMessageAdded = true
        }

        resetStreamingState()
    }

    private fun resetStreamingState() {
        _isGenerating.value = false
        _streamingUserMessage.value = null
        _streamingAssistantMessage.value = ""
        _streamingImage.value = null
        _imageGenerationProgress.value = 0f
        _imageGenerationStep.value = ""
        currentUserMessage = null
        currentGeneratedContent = ""
        currentGeneratedImage = null
        currentMetrics = null
        currentImageMetrics = null
        userMessageAdded = false
        _currentToolName.value = null
        _currentProcessingPhase.value = null
        // Clear RAG context and results after message is saved
        _currentRagContext.value = null
        _currentRagResults.value = emptyList()
    }

    // ==================== Generation Control ====================

    fun stop() {
        if (TTSManager.isPlaying.value) { TTSManager.stopPlayback() }
        when (_currentGenerationType.value) {
            GenerationManager.ModelType.TEXT_GENERATION -> {
                generationManager.stopTextGeneration()
                handleTextStop()
            }
            GenerationManager.ModelType.IMAGE_GENERATION -> {
                generationManager.stopImageGeneration()
                handleImageStop()
            }
            GenerationManager.ModelType.AUDIO_GENERATION -> {
                stopTTS()
            }
        }

        generationJob?.cancel()
        generationJob = null
        _isGenerating.value = false
        AppStateManager.setGenerationComplete()
    }

    private fun handleTextStop() {
        val chatId = _currentChatId.value

        if (chatId != null && currentUserMessage != null && currentGeneratedContent.isNotEmpty()) {
            viewModelScope.launch {
                if (!userMessageAdded) {
                    _messages.add(currentUserMessage!!)
                    userMessageAdded = true
                }

                val assistantMessage = Messages(
                    role = Role.Assistant,
                    content = MessageContent(
                        contentType = ContentType.Text,
                        content = "$currentGeneratedContent [stopped]"
                    ),
                    decodingMetrics = currentMetrics
                )
                _messages.add(assistantMessage)

                chatManager.addAssistantMessage(
                    chatId, "$currentGeneratedContent [stopped]", currentMetrics
                )
            }
        } else if (currentUserMessage != null && !userMessageAdded) {
            _messages.add(currentUserMessage!!)
            userMessageAdded = true
        }

        resetStreamingState()
    }

    private fun handleImageStop() {
        val chatId = _currentChatId.value

        if (chatId != null && currentUserMessage != null && currentGeneratedImage != null) {
            viewModelScope.launch {
                // Only add user message if it hasn't been added yet
                if (!userMessageAdded) {
                    _messages.add(currentUserMessage!!)
                    userMessageAdded = true
                }

                val imageBase64 = generationManager.bitmapToBase64(currentGeneratedImage!!)
                val imageMessage = Messages(
                    role = Role.Assistant,
                    content = MessageContent(
                        contentType = ContentType.Image,
                        content = "Image generation stopped",
                        imageData = imageBase64
                    ),
                    imageMetrics = currentImageMetrics
                )
                _messages.add(imageMessage)

                chatManager.addImageMessage(chatId, imageBase64, "", -1L, currentImageMetrics)
            }
        } else if (currentUserMessage != null && !userMessageAdded) {
            _messages.add(currentUserMessage!!)
            userMessageAdded = true
        }

        resetStreamingState()
    }

    // ==================== TTS Controls ====================

    private suspend fun autoSpeakIfEnabled(text: String, msgId: String? = null) {
        if (text.isBlank()) return
        val settings = ttsDataStore.settings.first()
        if (!settings.autoSpeak) return

        if (!TTSManager.isLoaded()) {
            val modelDir = TTSManager.getModelDirectory() ?: return
            withContext(Dispatchers.IO) {
                TTSManager.loadModel(modelDir, settings.useNNAPI)
            }
            if (!TTSManager.isLoaded()) return
        }

        TTSManager.speak(text = text, settings = settings, msgId = msgId)
    }

    fun speakMessage(message: Messages) {
        if (message.content.contentType != ContentType.Text) return
        val text = message.content.content
        if (text.isBlank()) return

        viewModelScope.launch {
            // Auto-load TTS model if not loaded
            if (!TTSManager.isLoaded()) {
                val modelDir = TTSManager.getModelDirectory() ?: return@launch
                val settings = ttsDataStore.settings.first()
                withContext(Dispatchers.IO) {
                    TTSManager.loadModel(modelDir, settings.useNNAPI)
                }
                if (!TTSManager.isLoaded()) return@launch
            }

            val settings = ttsDataStore.settings.first()
            TTSManager.speak(
                text = text,
                settings = settings,
                msgId = message.msgId
            )
        }
    }

    fun stopTTS() {
        TTSManager.stopPlayback()
    }

    // ==================== UI Controls ====================

    fun clearMessages() {
        _messages.clear()
        resetStreamingState()
        _error.value = null
        AppStateManager.setHasMessages(false)
    }

    fun clearError() {
        _error.value = null
        AppStateManager.clearError()
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            chatManager.deleteMessage(messageId).onSuccess {
                _messages.removeIf { it.msgId == messageId }
            }.onFailure { e ->
                _error.value = "Failed to delete message: ${e.message}"
            }
        }
    }

    fun showDynamicWindow() {
        _showDynamicWindow.value = _showDynamicWindow.value.not()
    }

    fun hideDynamicWindow() {
        _showDynamicWindow.value = false
    }

    fun showModelList() {
        _showModelList.value = true
    }

    fun hideModelList() {
        _showModelList.value = false
    }

    // ==================== Plugin Tool Call Handling (Legacy single-turn) ====================

    private suspend fun handlePluginToolCall(toolName: String, argsJson: String) {
        Log.d("ChatViewModel", "Tool call intercepted! Tool: $toolName, Args: $argsJson")

        var pluginName = "Unknown Plugin"
        var actualToolName = toolName

        try {
            // Parse the JSON properly - it comes wrapped in a tool_calls array
            val argsObject = JSONObject(argsJson)
            val toolCallsArray = argsObject.optJSONArray("tool_calls")

            if (toolCallsArray == null || toolCallsArray.length() == 0) {
                Log.e("ChatViewModel", "No tool_calls found in args: $argsJson")
                AppStateManager.setError("Invalid tool call format")
                return
            }

            // Get the first tool call
            val firstToolCall = toolCallsArray.getJSONObject(0)
            val rawToolName = firstToolCall.getString("name")
            val arguments = firstToolCall.getJSONObject("arguments")

            Log.d("ChatViewModel", "Parsed tool: $rawToolName, arguments: $arguments")

            // Normalize tool name (handle cases like "Web Scraping" -> "web_scraping")
            actualToolName = normalizeToolName(rawToolName)
            Log.d("ChatViewModel", "Normalized tool name: $rawToolName -> $actualToolName")

            // Get plugin name
            val plugin = PluginManager.getPlugin(
                PluginManager.registeredPlugins.value.firstOrNull { pluginInfo ->
                    pluginInfo.toolDefinitionBuilder.any {
                        it.name.equals(actualToolName, ignoreCase = true)
                    }
                }?.name ?: ""
            )
            pluginName = plugin?.getPluginInfo()?.name ?: "Unknown Plugin"

            // Set ExecutingPlugin state
            AppStateManager.setExecutingPlugin(pluginName, actualToolName)

            // Create ToolCall object
            val toolCall = ToolCall(name = actualToolName, arguments = arguments)

            // Execute via PluginManager
            val result = PluginManager.executeTool(toolCall)

            // Add result message to chat (or queue it for new conversations)
            when (result) {
                is PluginExecutionResult.Success -> {
                    Log.d("ChatViewModel", "Tool executed successfully")

                    // Set completion state with success
                    AppStateManager.setPluginExecutionComplete(
                        pluginName = pluginName,
                        toolName = actualToolName,
                        success = true,
                        executionTimeMs = result.metrics.executionTimeMs
                    )

                    // Wait a bit to show the success state
                    kotlinx.coroutines.delay(800)

                    addPluginResultMessage(result)

                    // Return to GeneratingText state since we're still in text generation
                    AppStateManager.setGeneratingText()
                }
                is PluginExecutionResult.Failure -> {
                    Log.e("ChatViewModel", "Tool execution failed: ${result.metrics.errorMessage}")

                    // Set completion state with error
                    AppStateManager.setPluginExecutionComplete(
                        pluginName = pluginName,
                        toolName = actualToolName,
                        success = false,
                        executionTimeMs = result.metrics.executionTimeMs,
                        errorMessage = result.metrics.errorMessage
                    )

                    // Wait a bit to show the error state
                    kotlinx.coroutines.delay(1500)

                    // Add error result to messages as a plugin result with failure status
                    addPluginErrorMessage(
                        pluginName = pluginName,
                        toolName = actualToolName,
                        errorMessage = result.metrics.errorMessage ?: "Unknown error",
                        executionTimeMs = result.metrics.executionTimeMs
                    )

                    // Return to GeneratingText state
                    AppStateManager.setGeneratingText()
                }
            }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Exception in handlePluginToolCall", e)

            // Set completion state with error
            AppStateManager.setPluginExecutionComplete(
                pluginName = pluginName,
                toolName = actualToolName,
                success = false,
                executionTimeMs = 0,
                errorMessage = e.message ?: "Unknown error"
            )

            kotlinx.coroutines.delay(1500)

            _error.value = "Error executing plugin tool: ${e.message}"
            AppStateManager.setError("Error executing plugin tool: ${e.message}")
        }
    }

    /**
     * Normalize tool name to match the actual tool definition
     * Converts "Web Scraping" -> "web_scraping", "DuckDuckGo Search" -> "duckduckgo_search"
     */
    private fun normalizeToolName(toolName: String): String {
        return toolName
            .lowercase()
            .replace(" ", "_")
            .replace("-", "_")
    }

    /**
     * After a tool executes, feed the result back to the LLM so it generates
     * a natural-language interpretation for the user.
     */
    private suspend fun generateResponseFromToolResult(
        conversationMessages: MutableList<JSONObject>,
        toolResultData: String,
        toolName: String
    ) {
        // Append the tool result as an assistant context message
        conversationMessages.add(
            JSONObject()
                .put("role", "assistant")
                .put("content", "Tool '$toolName' returned the following result:\n$toolResultData\n\nPlease summarize and explain this result to the user.")
        )

        val isStreaming = streamingEnabled.value
        val config = PluginManager.getToolCallingConfig()

        _streamingAssistantMessage.value = ""
        currentGeneratedContent = ""
        currentMetrics = null

        AppStateManager.setGeneratingText()

        try {
            generationManager.generateMultiTurnStreaming(
                JSONArray(conversationMessages).toString(), config.maxTokensPerTurn
            ).collect { event ->
                when (event) {
                    is GenerationEvent.Token -> {
                        currentGeneratedContent += event.text
                        if (isStreaming) {
                            _streamingAssistantMessage.value = currentGeneratedContent
                        }
                    }
                    is GenerationEvent.ToolCall -> { /* ignore nested tool calls from interpretation */ }
                    is GenerationEvent.Done -> { /* handled after collect */ }
                    is GenerationEvent.Metrics -> { currentMetrics = event.metrics }
                    is GenerationEvent.Error -> { _error.value = event.message }
                }
            }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Error generating response from tool result", e)
        }

        val filteredContent = filterToolCallSyntax(currentGeneratedContent)
        _streamingAssistantMessage.value = filteredContent
    }

    /**
     * Try to parse a tool call from generated token content.
     * The native engine may not emit onToolCall; instead tool call syntax
     * arrives as regular tokens. This parses Qwen's <tool_call> XML format
     * and the JSON tool_calls array format.
     *
     * @return Pair(toolName, argsJson) or null if no tool call found
     */
    private fun tryParseToolCallFromContent(content: String): Pair<String, String>? {
        try {
            // Format 1: Qwen <tool_call> XML tags
            // <tool_call>\n{"name": "...", "arguments": {...}}\n</tool_call>
            val toolCallXmlRegex = Regex(
                "<tool_call>\\s*(\\{.*?\\})\\s*</tool_call>",
                RegexOption.DOT_MATCHES_ALL
            )
            val xmlMatch = toolCallXmlRegex.find(content)
            if (xmlMatch != null) {
                val jsonStr = xmlMatch.groupValues[1]
                val json = JSONObject(jsonStr)
                val name = json.getString("name")
                // Wrap in tool_calls array format for executeMultiTurnToolCall compatibility
                val argsJson = JSONObject().apply {
                    put("tool_calls", JSONArray().apply {
                        put(JSONObject().apply {
                            put("name", name)
                            put("arguments", json.getJSONObject("arguments"))
                        })
                    })
                }.toString()
                Log.d("ChatViewModel", "Parsed tool call from <tool_call> XML: $name")
                return Pair(name, argsJson)
            }

            // Format 2: JSON with tool_calls array
            // {"tool_calls": [{"name": "...", "arguments": {...}}]}
            val toolCallsJsonRegex = Regex(
                "\\{\\s*\"tool_calls\"\\s*:\\s*\\[.*?\\]\\s*\\}",
                RegexOption.DOT_MATCHES_ALL
            )
            val jsonMatch = toolCallsJsonRegex.find(content)
            if (jsonMatch != null) {
                val jsonStr = jsonMatch.value
                val json = JSONObject(jsonStr)
                val toolCallsArray = json.getJSONArray("tool_calls")
                if (toolCallsArray.length() > 0) {
                    val firstCall = toolCallsArray.getJSONObject(0)
                    val name = firstCall.getString("name")
                    Log.d("ChatViewModel", "Parsed tool call from JSON array: $name")
                    return Pair(name, jsonStr)
                }
            }

            // Format 3: Direct JSON object with name and arguments
            // {"name": "...", "arguments": {...}}
            val directJsonRegex = Regex(
                "\\{\\s*\"name\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"arguments\"\\s*:\\s*(\\{.*?\\})\\s*\\}",
                RegexOption.DOT_MATCHES_ALL
            )
            val directMatch = directJsonRegex.find(content)
            if (directMatch != null) {
                val name = directMatch.groupValues[1]
                val argsJson = JSONObject().apply {
                    put("tool_calls", JSONArray().apply {
                        put(JSONObject().apply {
                            put("name", name)
                            put("arguments", JSONObject(directMatch.groupValues[2]))
                        })
                    })
                }.toString()
                Log.d("ChatViewModel", "Parsed tool call from direct JSON: $name")
                return Pair(name, argsJson)
            }
        } catch (e: Exception) {
            Log.w("ChatViewModel", "Failed to parse tool call from content: ${e.message}")
        }
        return null
    }

    /**
     * Filter out tool call syntax and code blocks from generated text
     */
    private fun filterToolCallSyntax(content: String): String {
        var filtered = content

        // Remove Qwen <tool_call>...</tool_call> XML tags
        filtered = filtered.replace(Regex("<tool_call>\\s*\\{.*?\\}\\s*</tool_call>", RegexOption.DOT_MATCHES_ALL), "")

        // Remove ```json code blocks
        filtered = filtered.replace(Regex("```json\\s*\\{[^`]*```", RegexOption.DOT_MATCHES_ALL), "")
        filtered = filtered.replace(Regex("```\\s*\\{[^`]*```", RegexOption.DOT_MATCHES_ALL), "")

        // Remove standalone JSON objects that look like tool calls
        filtered = filtered.replace(Regex("\\{\\s*\"tool_calls\"\\s*:[^}]*\\}\\s*", RegexOption.DOT_MATCHES_ALL), "")

        // Remove direct tool call JSON objects
        filtered = filtered.replace(Regex("\\{\\s*\"name\"\\s*:\\s*\"[^\"]+\"\\s*,\\s*\"arguments\"\\s*:\\s*\\{.*?\\}\\s*\\}", RegexOption.DOT_MATCHES_ALL), "")

        // Clean up excessive whitespace
        filtered = filtered.trim()
        filtered = filtered.replace(Regex("\\n{3,}"), "\n\n")

        return filtered
    }

    private suspend fun addPluginResultMessage(result: PluginExecutionResult.Success) {
        try {
            if (!userMessageAdded && currentUserMessage != null) {
                _messages.add(currentUserMessage!!)
                userMessageAdded = true
            }

            val message = Messages(
                role = Role.Assistant,
                content = MessageContent(
                    contentType = ContentType.PluginResult,
                    content = formatPluginResult(result),
                    pluginResultData = result.resultData
                ),
                pluginMetrics = result.metrics
            )

            _messages.add(message)

            val chatId = _currentChatId.value
            if (chatId != null) {
                chatManager.addMessage(chatId, message).onFailure { e ->
                    Log.e("ChatViewModel", "Failed to save plugin result to vault: ${e.message}")
                    _error.value = "Failed to save plugin result: ${e.message}"
                }
            }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Error adding plugin result", e)
            _error.value = "Error adding plugin result: ${e.message}"
        }
    }

    private fun formatPluginResult(result: PluginExecutionResult.Success): String {
        return "Plugin '${result.metrics.pluginName}' executed tool '${result.metrics.toolName}' " +
                "in ${result.metrics.executionTimeMs}ms"
    }

    private suspend fun addPluginErrorMessage(
        pluginName: String,
        toolName: String,
        errorMessage: String,
        executionTimeMs: Long
    ) {
        try {
            if (!userMessageAdded && currentUserMessage != null) {
                _messages.add(currentUserMessage!!)
                userMessageAdded = true
            }

            val message = Messages(
                role = Role.Assistant,
                content = MessageContent(
                    contentType = ContentType.PluginResult,
                    content = "Plugin '$pluginName' tool '$toolName' failed: $errorMessage (${executionTimeMs}ms)",
                    pluginResultData = PluginResultData(
                        success = false,
                        pluginName = pluginName,
                        toolName = toolName,
                        resultData = errorMessage,
                        inputParams = "{}"
                    )
                ),
                pluginMetrics = null
            )

            _messages.add(message)

            val chatId = _currentChatId.value
            if (chatId != null) {
                chatManager.addMessage(chatId, message).onFailure { e ->
                    Log.e("ChatViewModel", "Failed to save plugin error to vault: ${e.message}")
                    _error.value = "Failed to save plugin error: ${e.message}"
                }
            }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Error adding plugin error message", e)
            _error.value = "Error adding plugin error message: ${e.message}"
        }
    }
}
