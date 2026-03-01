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
            nCtx = loading.ctxSize,
            nThreads = loading.threads,
            flashAttn = loading.flashAttn,
            cacheTypeK = cacheTypeIntToString(loading.cacheTypeK),
            cacheTypeV = cacheTypeIntToString(loading.cacheTypeV)
        )

        if (success) {
            isLoaded = true
            currentModelId = model.id

            // Set sampling parameters separately (new SDK splits load/sampling)
            nativeLib.nativeSetSampling(
                temperature = inference.temperature,
                topK = inference.topK,
                topP = inference.topP,
                minP = inference.minP,
                mirostat = inference.mirostat,
                mirostatTau = inference.mirostatTau,
                mirostatEta = inference.mirostatEta,
                seed = inference.seed
            )

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
            nCtx = loading.ctxSize,
            nThreads = loading.threads,
            flashAttn = loading.flashAttn,
            cacheTypeK = cacheTypeIntToString(loading.cacheTypeK),
            cacheTypeV = cacheTypeIntToString(loading.cacheTypeV)
        )

        if (success) {
            isLoaded = true
            currentModelId = "fd_$fd"

            nativeLib.nativeSetSampling(
                temperature = inference.temperature,
                topK = inference.topK,
                topP = inference.topP,
                minP = inference.minP,
                mirostat = inference.mirostat,
                mirostatTau = inference.mirostatTau,
                mirostatEta = inference.mirostatEta,
                seed = inference.seed
            )

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

            override fun onMetrics(
                tps: Float, ttftMs: Float, totalMs: Float,
                tokensEvaluated: Int, tokensPredicted: Int,
                modelMB: Float, ctxMB: Float, peakMB: Float, memPct: Float
            ) {
                trySend(GenerationEvent.Metrics(DecodingMetrics(
                    tokensPerSecond = tps, timeToFirstTokenMs = ttftMs, totalTimeMs = totalMs,
                    tokensEvaluated = tokensEvaluated, tokensPredicted = tokensPredicted,
                    modelSizeMB = modelMB, contextSizeMB = ctxMB,
                    peakMemoryMB = peakMB, memoryUsagePercent = memPct
                )))
            }
        }

        try {
            val success = nativeLib.nativeGenerateStream(prompt, maxTokens, callback)
            if (!success) {
                trySend(GenerationEvent.Error("Native generation returned false (model not ready or callback init failed)"))
                close()
            }
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

            override fun onMetrics(
                tps: Float, ttftMs: Float, totalMs: Float,
                tokensEvaluated: Int, tokensPredicted: Int,
                modelMB: Float, ctxMB: Float, peakMB: Float, memPct: Float
            ) {
                trySend(GenerationEvent.Metrics(DecodingMetrics(
                    tokensPerSecond = tps, timeToFirstTokenMs = ttftMs, totalTimeMs = totalMs,
                    tokensEvaluated = tokensEvaluated, tokensPredicted = tokensPredicted,
                    modelSizeMB = modelMB, contextSizeMB = ctxMB,
                    peakMemoryMB = peakMB, memoryUsagePercent = memPct
                )))
            }
        }

        try {
            val success = nativeLib.nativeGenerateStreamMultiTurn(messagesJson, maxTokens, callback)
            if (!success) {
                trySend(GenerationEvent.Error("Native multi-turn generation returned false (model not ready or callback init failed)"))
                close()
            }
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

    // ========================================================================
    // PERSONA ENGINE: Dynamic Sampling + Logit Bias + Control Vectors
    // ========================================================================

    fun updateSamplerParams(paramsJson: String): Boolean {
        if (!isLoaded) return false
        return try {
            nativeLib.nativeUpdateSamplerParams(paramsJson)
        } catch (_: Exception) { false }
    }

    fun setLogitBias(biasJson: String): Boolean {
        if (!isLoaded) return false
        return try {
            nativeLib.nativeSetLogitBias(biasJson)
            true
        } catch (_: Exception) { false }
    }

    fun loadControlVectors(vectorsJson: String): Boolean {
        if (!isLoaded) return false
        return try {
            nativeLib.nativeLoadControlVectors(vectorsJson)
        } catch (_: Exception) { false }
    }

    fun clearControlVector(): Boolean {
        if (!isLoaded) return false
        return try {
            nativeLib.nativeClearControlVector()
            true
        } catch (_: Exception) { false }
    }

    // ========================================================================
    // CHARACTER ENGINE (llama.cpp engine/ API)
    // ========================================================================

    /**
     * Set character personality via the native CharacterEngine.
     * Adjusts sampling parameters (temp, top_p, rep_penalty) based on personality traits.
     * @param paramsJson JSON with: name, persona, temperature, topP, repetitionPenalty, creativity, verbosity, formality
     */
    fun setPersonality(paramsJson: String): Boolean {
        if (!isLoaded) return false
        return try {
            nativeLib.nativeSetPersonality(paramsJson)
            true
        } catch (_: Exception) { false }
    }

    /**
     * Set character mood via the native CharacterEngine.
     * Mood adjusts sampling params on top of personality.
     * @param mood 0=NEUTRAL, 1=HAPPY, 2=SAD, 3=EXCITED, 4=CALM, 5=ANGRY, 6=CURIOUS, 7=CREATIVE, 8=FOCUSED, 9=CUSTOM
     */
    fun setMood(mood: Int): Boolean {
        if (!isLoaded) return false
        return try {
            nativeLib.nativeSetMood(mood)
            true
        } catch (_: Exception) { false }
    }

    /**
     * Get character context string from the native CharacterEngine.
     * Returns a formatted context block describing the active personality and mood.
     */
    fun getCharacterContext(): String {
        if (!isLoaded) return ""
        return try {
            nativeLib.nativeGetCharacterContext()
        } catch (_: Exception) { "" }
    }

    /**
     * Enable/disable uncensored mode in the native CharacterEngine.
     * When enabled, suppresses refusal patterns at the logit level and
     * injects uncensored context into the prompt.
     */
    fun setUncensored(enabled: Boolean): Boolean {
        if (!isLoaded) return false
        return try {
            nativeLib.nativeSetUncensored(enabled)
            true
        } catch (_: Exception) { false }
    }

    fun isUncensored(): Boolean {
        if (!isLoaded) return false
        return try {
            nativeLib.nativeGetUncensored()
        } catch (_: Exception) { false }
    }

    fun supportsThinking(): Boolean {
        if (!isLoaded) return false
        return try {
            nativeLib.nativeSupportsThinking()
        } catch (_: Exception) { false }
    }

    // ========================================================================
    // KV CACHE STATE PERSISTENCE
    // ========================================================================

    fun getStateSize(): Long {
        if (!isLoaded) return 0
        return try {
            nativeLib.nativeGetStateSize()
        } catch (_: Exception) { 0 }
    }

    fun stateSaveToFile(path: String): Boolean {
        if (!isLoaded) return false
        return try {
            nativeLib.nativeStateSaveToFile(path)
        } catch (_: Exception) { false }
    }

    fun stateLoadFromFile(path: String): Boolean {
        if (!isLoaded) return false
        return try {
            nativeLib.nativeStateLoadFromFile(path)
        } catch (_: Exception) { false }
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
        /** Convert GGML_TYPE int to string name for cache type. */
        private fun cacheTypeIntToString(type: Int): String = when (type) {
            0 -> "f32"; 1 -> "f16"; 8 -> "q5_1"; 9 -> "q8_0"
            14 -> "q4_0"; 15 -> "q4_1"; 16 -> "q5_0"
            else -> "q8_0"
        }

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
    /** Status update from backend (e.g. "Tokenizing...", "Building KV cache...") */
    data class Status(val message: String) : GenerationEvent()
}
