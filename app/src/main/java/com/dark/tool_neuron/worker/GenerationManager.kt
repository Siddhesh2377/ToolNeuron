package com.dark.tool_neuron.worker

import android.graphics.Bitmap
import com.dark.tool_neuron.engine.GenerationEvent
import com.dark.tool_neuron.models.messages.Messages
import com.dark.tool_neuron.models.messages.Role
import com.dark.tool_neuron.worker.LlmModelWorker.DiffusionGenerationEvent
import kotlinx.coroutines.flow.Flow

class GenerationManager {

    enum class ModelType {
        TEXT_GENERATION,
        IMAGE_GENERATION,
        AUDIO_GENERATION
    }

    private var currentModelType: ModelType = ModelType.TEXT_GENERATION

    // ==================== Model Management ====================

    fun getCurrentModelType(): ModelType = currentModelType

    fun setCurrentModelType(type: ModelType) {
        currentModelType = type
    }

    fun isTextModelLoaded(): Boolean {
        return LlmModelWorker.isGgufModelLoaded.value
    }

    fun isImageModelLoaded(): Boolean {
        return LlmModelWorker.isDiffusionModelLoaded.value
    }

    fun isAnyModelLoaded(): Boolean {
        return isTextModelLoaded() || isImageModelLoaded()
    }

    // ==================== Text Generation ====================

    fun generateTextStreaming(prompt: String, maxTokens: Int = 2048): Flow<GenerationEvent> {
        return LlmModelWorker.ggufGenerateStreaming(prompt, maxTokens)
    }

    /**
     * Multi-turn streaming generation using full conversation history.
     * Used for multi-turn tool calling flows.
     *
     * @param messagesJson JSON array of {role, content} message objects
     * @param maxTokens Maximum tokens per turn
     */
    fun generateMultiTurnStreaming(messagesJson: String, maxTokens: Int = 2048): Flow<GenerationEvent> {
        return LlmModelWorker.ggufGenerateMultiTurnStreaming(messagesJson, maxTokens)
    }

    fun stopTextGeneration() {
        LlmModelWorker.ggufStopGeneration()
    }

    // ==================== Image Generation ====================

    fun generateImageStreaming(
        prompt: String,
        negativePrompt: String = "blurry, low quality, distorted",
        steps: Int = 28,
        cfgScale: Float = 7.5f,
        seed: Long = -1L,
        width: Int = 512,
        height: Int = 512,
        scheduler: String = "dpm",
        inputImage: String? = null,
        mask: String? = null,
        denoiseStrength: Float = 0.6f,
        showDiffusionProcess: Boolean = true,
        showDiffusionStride: Int = 1
    ): Flow<DiffusionGenerationEvent> {
        return LlmModelWorker.generateDiffusionImage(
            prompt = prompt,
            negativePrompt = negativePrompt,
            steps = steps,
            cfgScale = cfgScale,
            seed = seed,
            width = width,
            height = height,
            scheduler = scheduler,
            inputImage = inputImage,
            mask = mask,
            denoiseStrength = denoiseStrength,
            showDiffusionProcess = showDiffusionProcess,
            showDiffusionStride = showDiffusionStride
        )
    }

    fun stopImageGeneration() {
        LlmModelWorker.stopDiffusionGeneration()
    }

    // ==================== Prompt Building ====================

    fun buildPromptFromHistory(messages: List<Messages>): String {
        val promptBuilder = StringBuilder()

        messages.forEach { message ->
            when (message.role) {
                Role.User -> {
                    promptBuilder.append("User: ${message.content.content}\n")
                }
                Role.Assistant -> {
                    if (message.content.contentType != com.dark.tool_neuron.models.messages.ContentType.Image) {
                        promptBuilder.append("Assistant: ${message.content.content}\n")
                    }
                }
            }
        }

        return promptBuilder.toString()
    }

    fun buildSinglePrompt(userMessage: String): String {
        return "User: $userMessage\nAssistant:"
    }

    fun buildConversationPrompt(history: List<Messages>, currentPrompt: String): String {
        val builder = StringBuilder()

        history.forEach { message ->
            when (message.role) {
                Role.User -> {
                    builder.append("User: ${message.content.content}\n")
                }
                Role.Assistant -> {
                    if (message.content.contentType != com.dark.tool_neuron.models.messages.ContentType.Image) {
                        builder.append("Assistant: ${message.content.content}\n")
                    }
                }
            }
        }

        builder.append("User: $currentPrompt\nAssistant:")

        return builder.toString()
    }

    // ==================== Utility ====================

    fun bitmapToBase64(bitmap: Bitmap): String {
        return LlmModelWorker.bitmapToBase64(bitmap)
    }

    fun bitmapToRgbBase64(bitmap: Bitmap): String {
        return LlmModelWorker.bitmapToRgbBase64(bitmap)
    }
}