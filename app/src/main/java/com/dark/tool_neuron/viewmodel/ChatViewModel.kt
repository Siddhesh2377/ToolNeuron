package com.dark.tool_neuron.viewmodel

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.engine.GenerationEvent
import com.dark.tool_neuron.models.messages.ContentType
import com.dark.tool_neuron.models.messages.ImageGenerationMetrics
import com.dark.tool_neuron.models.messages.MessageContent
import com.dark.tool_neuron.models.messages.Messages
import com.dark.tool_neuron.models.messages.Role
import com.dark.tool_neuron.state.AppStateManager
import com.dark.tool_neuron.worker.ChatManager
import com.dark.tool_neuron.worker.GenerationManager
import com.dark.tool_neuron.worker.LlmModelWorker
import com.mp.ai_gguf.models.DecodingMetrics
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatManager: ChatManager,
    private val generationManager: GenerationManager
) : ViewModel() {

    private val _messages = mutableStateListOf<Messages>()
    val messages: SnapshotStateList<Messages> = _messages

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _currentChatId = MutableStateFlow<String?>(null)
    val currentChatId: StateFlow<String?> = _currentChatId

    private var isNewConversation = true

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

    // Track generation job for proper cancellation
    private var generationJob: Job? = null

    // Track current generation state
    private var currentUserMessage: Messages? = null
    private var currentGeneratedContent: String = ""
    private var currentMetrics: DecodingMetrics? = null
    private var currentImageMetrics: ImageGenerationMetrics? = null
    private var currentGeneratedImage: Bitmap? = null
    private var imageGenerationStartTime: Long = 0

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
        _error.value = null
        isNewConversation = true
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
                generateTextForNewChat(prompt, maxTokens)
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
                    generateText(chatId, userMessage, maxTokens)
                }.onFailure { e ->
                    _error.value = "Failed to save message: ${e.message}"
                    _streamingUserMessage.value = null
                    currentUserMessage = null
                    AppStateManager.setError("Failed to save message: ${e.message}")
                }
            }
        }
    }

    private fun generateTextForNewChat(prompt: String, maxTokens: Int) {
        generationJob = viewModelScope.launch {
            _isGenerating.value = true
            _error.value = null
            _streamingAssistantMessage.value = ""
            currentGeneratedContent = ""
            currentMetrics = null

            AppStateManager.setGeneratingText()

            var tokenBuffer = StringBuilder()
            var tokenCount = 0
            var lastUpdateTime = System.currentTimeMillis()
            val updateIntervalMs = 50L
            val tokenBatchSize = 3

            try {
                generationManager.generateTextStreaming(prompt, maxTokens).collect { event ->
                    when (event) {
                        is GenerationEvent.Token -> {
                            currentGeneratedContent += event.text
                            tokenBuffer.append(event.text)
                            tokenCount++

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

                        is GenerationEvent.Done -> {
                            _streamingAssistantMessage.value = currentGeneratedContent
                            _isGenerating.value = false
                            createChatWithMessages(prompt, currentGeneratedContent, currentMetrics)
                        }

                        is GenerationEvent.Error -> {
                            handleTextGenerationError(prompt, event.message)
                        }

                        is GenerationEvent.Metrics -> {
                            currentMetrics = event.metrics
                        }

                        is GenerationEvent.ToolCall -> {}
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

            val tokenBuffer = StringBuilder()
            var tokenCount = 0
            var lastUpdateTime = System.currentTimeMillis()
            val updateIntervalMs = 50L
            val tokenBatchSize = 3

            try {
                val conversationPrompt = generationManager.buildConversationPrompt(
                    _messages, userMessage.content.content
                )

                generationManager.generateTextStreaming(conversationPrompt, maxTokens)
                    .collect { event ->
                        when (event) {
                            is GenerationEvent.Token -> {
                                currentGeneratedContent += event.text
                                tokenBuffer.append(event.text)
                                tokenCount++

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

                            is GenerationEvent.Done -> {
                                _streamingAssistantMessage.value = currentGeneratedContent
                                _isGenerating.value = false

                                _messages.add(userMessage)
                                val assistantMessage = Messages(
                                    role = Role.Assistant,
                                    content = MessageContent(
                                        contentType = ContentType.Text,
                                        content = currentGeneratedContent
                                    ),
                                    decodingMetrics = currentMetrics
                                )
                                _messages.add(assistantMessage)

                                chatManager.addAssistantMessage(
                                    chatId, currentGeneratedContent, currentMetrics
                                )

                                AppStateManager.setGenerationComplete()
                                resetStreamingState()
                            }

                            is GenerationEvent.Error -> {
                                handleTextGenerationErrorExisting(chatId, userMessage, event.message)
                            }

                            is GenerationEvent.Metrics -> {
                                currentMetrics = event.metrics
                            }

                            is GenerationEvent.ToolCall -> {}
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
        negativePrompt: String = "blurry, low quality, distorted",
        steps: Int = 28,
        cfgScale: Float = 7.5f,
        seed: Long = -1L,
        width: Int = 512,
        height: Int = 512,
        scheduler: String = "dpm"
    ) {
        if (!generationManager.isImageModelLoaded()) {
            _error.value = "Please load an image generation model first"
            AppStateManager.setError("Please load an image generation model first")
            return
        }

        if (_isGenerating.value) return

        _streamingUserMessage.value = prompt
        imageGenerationStartTime = System.currentTimeMillis()

        viewModelScope.launch {
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
                generateImageForNewChat(prompt, negativePrompt, steps, cfgScale, seed, width, height, scheduler)
            } else {
                val chatId = _currentChatId.value
                if (chatId == null) {
                    _error.value = "No chat selected"
                    return@launch
                }

                chatManager.addUserMessage(chatId, "Generate image: $prompt").onSuccess { userMessage ->
                    currentUserMessage = userMessage
                    AppStateManager.setHasMessages(true)
                    generateImage(chatId, userMessage, prompt, negativePrompt, steps, cfgScale, seed, width, height, scheduler)
                }.onFailure { e ->
                    _error.value = "Failed to save message: ${e.message}"
                    resetStreamingState()
                }
            }
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

            AppStateManager.setGeneratingText()

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

            AppStateManager.setGeneratingText()

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

                            _messages.add(userMessage)

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
        chatManager.createNewChat().onSuccess { newChatId ->
            _currentChatId.value = newChatId
            isNewConversation = false

            chatManager.addUserMessage(newChatId, userPrompt).onSuccess { userMessage ->
                _messages.add(userMessage)

                chatManager.addAssistantMessage(
                    newChatId, assistantResponse, metrics
                ).onSuccess { assistantMessage ->
                    _messages.add(assistantMessage)
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
            isNewConversation = false

            chatManager.addUserMessage(newChatId, userPrompt).onSuccess { userMessage ->
                _messages.add(userMessage)

                chatManager.addImageMessage(
                    newChatId, imageBase64, imagePrompt, seed, currentImageMetrics
                ).onSuccess { imageMessage ->
                    _messages.add(imageMessage)
                    AppStateManager.setGenerationComplete()
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

        _messages.add(userMessage)
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
            _messages.add(currentUserMessage!!)
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

        _messages.add(userMessage)
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

        _messages.add(userMessage)
        resetStreamingState()
    }

    private fun resetStreamingState() {
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
    }

    // ==================== Generation Control ====================

    fun stop() {
        when (_currentGenerationType.value) {
            GenerationManager.ModelType.TEXT_GENERATION -> {
                generationManager.stopTextGeneration()
                handleTextStop()
            }
            GenerationManager.ModelType.IMAGE_GENERATION -> {
                generationManager.stopImageGeneration()
                handleImageStop()
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
                _messages.add(currentUserMessage!!)

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
        } else if (currentUserMessage != null) {
            _messages.add(currentUserMessage!!)
        }

        resetStreamingState()
    }

    private fun handleImageStop() {
        val chatId = _currentChatId.value

        if (chatId != null && currentUserMessage != null && currentGeneratedImage != null) {
            viewModelScope.launch {
                _messages.add(currentUserMessage!!)

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
        } else if (currentUserMessage != null) {
            _messages.add(currentUserMessage!!)
        }

        resetStreamingState()
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
}