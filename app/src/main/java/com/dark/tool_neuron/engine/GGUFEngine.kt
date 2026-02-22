package com.dark.tool_neuron.engine

import android.app.ActivityManager
import android.content.Context
import com.dark.tool_neuron.models.table_schema.Model
import com.dark.tool_neuron.models.table_schema.ModelConfig
import com.dark.tool_neuron.models.engine_schema.DeviceTier
import com.dark.tool_neuron.models.engine_schema.GgufEngineSchema
import com.dark.tool_neuron.models.engine_schema.GgufLoadingParams
import com.mp.ai_gguf.GGUFNativeLib
import com.mp.ai_gguf.models.DecodingMetrics
import com.mp.ai_gguf.models.StreamCallback
import com.mp.ai_gguf.toolcalling.GrammarMode
import com.mp.ai_gguf.toolcalling.ToolCallingConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class GGUFEngine {
    private val nativeLib = GGUFNativeLib()
    private var isLoaded = false
    private var currentModelId: String? = null
    private var currentToolsJson: String? = null  // Cache for grammar optimization
    private var currentToolCallingConfig: ToolCallingConfig? = null

    suspend fun load(model: Model, config: ModelConfig?): Boolean = withContext(Dispatchers.IO) {
        if (isLoaded) unload()

        val schema = GgufEngineSchema.fromJson(
            config?.modelLoadingParams,
            config?.modelInferenceParams
        )

        val loading = schema.loadingParams
        val inference = schema.inferenceParams

        val success = nativeLib.nativeLoadModel(
            path = model.modelPath,
            threads = loading.threads,
            ctxSize = loading.ctxSize,
            temp = inference.temperature,
            topK = inference.topK,
            topP = inference.topP,
            minP = inference.minP,
            mirostat = inference.mirostat,
            mirostatTau = inference.mirostatTau,
            mirostatEta = inference.mirostatEta,
            seed = inference.seed
        )

        if (success) {
            isLoaded = true
            currentModelId = model.id

            if (inference.systemPrompt.isNotEmpty()) {
                nativeLib.nativeSetSystemPrompt(inference.systemPrompt)
            }
            if (inference.chatTemplate.isNotEmpty()) {
                nativeLib.nativeSetChatTemplate(inference.chatTemplate)
            }
        }

        success
    }

    /**
     * Load model from file descriptor (for SAF/content:// URIs)
     * This allows loading models without MANAGE_EXTERNAL_STORAGE permission
     *
     * @param fd File descriptor from contentResolver.openFileDescriptor(uri, "r").detachFd()
     * @param config Optional model configuration
     * @return true if model loaded successfully
     */
    suspend fun loadFromFd(fd: Int, config: ModelConfig? = null): Boolean = withContext(Dispatchers.IO) {
        if (isLoaded) unload()

        val schema = GgufEngineSchema.fromJson(
            config?.modelLoadingParams,
            config?.modelInferenceParams
        )

        val loading = schema.loadingParams
        val inference = schema.inferenceParams

        val success = nativeLib.nativeLoadModelFromFd(
            fd = fd,
            threads = loading.threads,
            ctxSize = loading.ctxSize,
            temp = inference.temperature,
            topK = inference.topK,
            topP = inference.topP,
            minP = inference.minP,
            mirostat = inference.mirostat,
            mirostatTau = inference.mirostatTau,
            mirostatEta = inference.mirostatEta,
            seed = inference.seed
        )

        if (success) {
            isLoaded = true
            currentModelId = "fd_$fd"

            if (inference.systemPrompt.isNotEmpty()) {
                nativeLib.nativeSetSystemPrompt(inference.systemPrompt)
            }
            if (inference.chatTemplate.isNotEmpty()) {
                nativeLib.nativeSetChatTemplate(inference.chatTemplate)
            }
        }

        success
    }

    /**
     * Generate tokens as a Flow using callbackFlow (single-turn)
     *
     * This properly handles the callback-to-flow conversion without
     * violating Flow invariants. The native callback runs on whatever
     * thread llama.cpp uses, and we safely send events to the channel.
     *
     * The flow is consumed on IO dispatcher and buffered for smooth streaming.
     */
    fun generateFlow(prompt: String, maxTokens: Int): Flow<GenerationEvent> = callbackFlow {
        if (!isLoaded) {
            trySend(GenerationEvent.Error("Model not loaded"))
            close()
            return@callbackFlow
        }

        val callback = object : StreamCallback {
            override fun onToken(token: String) {
                trySend(GenerationEvent.Token(token))
            }

            override fun onToolCall(name: String, argsJson: String) {
                trySend(GenerationEvent.ToolCall(name, argsJson))
            }

            override fun onDone() {
                trySend(GenerationEvent.Done)
                close()
            }

            override fun onError(message: String) {
                trySend(GenerationEvent.Error(message))
                close()
            }

            override fun onMetrics(metrics: DecodingMetrics) {
                trySend(GenerationEvent.Metrics(metrics))
            }
        }

        try {
            nativeLib.nativeGenerateStream(prompt, maxTokens, callback)
        } catch (e: Exception) {
            trySend(GenerationEvent.Error(e.message ?: "Generation failed"))
            close()
        }

        awaitClose { }
    }
        .buffer(Channel.UNLIMITED)
        .flowOn(Dispatchers.IO)

    /**
     * Multi-turn generation flow using full conversation history.
     * Processes a JSON array of {role, content} messages and generates the next response.
     * Supports tool call detection — when a tool call is detected, the flow emits
     * GenerationEvent.ToolCall and the caller should execute the tool, append the result
     * to the conversation, and call this method again for the next turn.
     *
     * @param messagesJson JSON array of {role, content} objects representing the conversation
     * @param maxTokens Maximum tokens to generate for this turn
     */
    fun generateMultiTurnFlow(messagesJson: String, maxTokens: Int): Flow<GenerationEvent> = callbackFlow {
        if (!isLoaded) {
            trySend(GenerationEvent.Error("Model not loaded"))
            close()
            return@callbackFlow
        }

        val callback = object : StreamCallback {
            override fun onToken(token: String) {
                trySend(GenerationEvent.Token(token))
            }

            override fun onToolCall(name: String, argsJson: String) {
                trySend(GenerationEvent.ToolCall(name, argsJson))
            }

            override fun onDone() {
                trySend(GenerationEvent.Done)
                close()
            }

            override fun onError(message: String) {
                trySend(GenerationEvent.Error(message))
                close()
            }

            override fun onMetrics(metrics: DecodingMetrics) {
                trySend(GenerationEvent.Metrics(metrics))
            }
        }

        try {
            nativeLib.nativeGenerateStreamMultiTurn(messagesJson, maxTokens, callback)
        } catch (e: Exception) {
            trySend(GenerationEvent.Error(e.message ?: "Multi-turn generation failed"))
            close()
        }

        awaitClose { }
    }
        .buffer(Channel.UNLIMITED)
        .flowOn(Dispatchers.IO)

    fun stopGeneration() {
        nativeLib.nativeStopGeneration()
    }

    suspend fun unload() = withContext(Dispatchers.IO) {
        if (isLoaded) {
            nativeLib.nativeRelease()
            isLoaded = false
            currentModelId = null
            currentToolsJson = null
            currentToolCallingConfig = null
        }
    }

    fun isModelLoaded(modelId: String): Boolean =
        isLoaded && currentModelId == modelId

    fun getModelInfo(): String? =
        if (isLoaded) nativeLib.nativeGetModelInfo() else null

    /**
     * Check if the loaded model supports tool calling.
     * Now returns true for any model with a built-in chat template (model-agnostic).
     */
    fun isToolCallingSupported(): Boolean {
        if (!isLoaded) return false
        return try {
            nativeLib.nativeIsToolCallingSupported()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Enable tool calling with grammar configuration.
     * Sets up tools JSON, grammar mode, and typed grammar enforcement.
     *
     * @param toolsJson JSON array of tool definitions in OpenAI format
     * @param grammarMode 0=STRICT (forces JSON), 1=LAZY (model chooses text or tool call)
     * @param useTypedGrammar Whether to enforce exact param names/types/enums
     * @return true if tool calling was enabled successfully
     */
    fun enableToolCalling(
        toolsJson: String,
        grammarMode: Int = GrammarMode.LAZY.value,
        useTypedGrammar: Boolean = true
    ): Boolean {
        if (!isLoaded) return false

        return try {
            // Set tools JSON (grammar caching: skip rebuild if unchanged)
            if (toolsJson != currentToolsJson) {
                nativeLib.nativeSetToolsJson(toolsJson)
                currentToolsJson = toolsJson
            }

            // Configure grammar mode and typed grammar
            nativeLib.nativeSetGrammarMode(grammarMode)
            nativeLib.nativeSetTypedGrammar(useTypedGrammar)

            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Set tools JSON for function calling support (backward-compatible).
     * Uses grammar caching — only rebuilds grammar when tools JSON changes.
     *
     * @param toolsJson JSON array of tool definitions, or empty string to disable
     * @return true if tools were set successfully
     */
    fun setToolsJson(toolsJson: String): Boolean {
        if (!isLoaded) return false

        if (toolsJson == currentToolsJson) return true

        return try {
            nativeLib.nativeSetToolsJson(toolsJson)
            currentToolsJson = toolsJson
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Set grammar enforcement mode.
     * @param mode 0=STRICT (forces JSON tool call), 1=LAZY (model chooses text or tool call)
     */
    fun setGrammarMode(mode: Int) {
        if (isLoaded) {
            try {
                nativeLib.nativeSetGrammarMode(mode)
            } catch (_: Exception) { }
        }
    }

    /**
     * Enable or disable parameter-aware typed grammar.
     * When enabled, grammar enforces exact parameter names, types, and enum values per tool.
     */
    fun setTypedGrammar(enabled: Boolean) {
        if (isLoaded) {
            try {
                nativeLib.nativeSetTypedGrammar(enabled)
            } catch (_: Exception) { }
        }
    }

    /**
     * Clear tools configuration and disable function calling
     */
    fun clearTools() {
        if (isLoaded) {
            try {
                nativeLib.nativeSetToolsJson("")
                currentToolsJson = null
                currentToolCallingConfig = null
            } catch (_: Exception) { }
        }
    }

    /**
     * Check if tools/function calling is currently enabled
     */
    fun hasToolsEnabled(): Boolean = !currentToolsJson.isNullOrEmpty()

    companion object {
        /**
         * Detect device tier based on available RAM
         */
        fun detectDeviceTier(context: Context): DeviceTier {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)

            val totalRamGB = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)

            return when {
                totalRamGB < 4.0 -> DeviceTier.LOW_END
                totalRamGB < 8.0 -> DeviceTier.MID_RANGE
                else -> DeviceTier.HIGH_END
            }
        }

        /**
         * Get recommended loading params for the current device
         */
        fun getRecommendedParams(context: Context): GgufLoadingParams {
            val tier = detectDeviceTier(context)
            return GgufLoadingParams.forDeviceTier(tier)
        }

        /**
         * Calculate recommended context size based on available memory and model size
         */
        fun getRecommendedContextSize(context: Context, modelSizeMB: Int): Int {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)

            val availableMemoryMB = (memInfo.availMem / (1024 * 1024)).toInt()
            return GgufLoadingParams.recommendedContextSize(availableMemoryMB, modelSizeMB)
        }
    }
}

sealed class GenerationEvent {
    data class Token(val text: String) : GenerationEvent()
    data class ToolCall(val name: String, val args: String) : GenerationEvent()
    data object Done : GenerationEvent()
    data class Error(val message: String) : GenerationEvent()
    data class Metrics(val metrics: DecodingMetrics) : GenerationEvent()
}
