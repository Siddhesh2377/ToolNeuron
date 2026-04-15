package com.dark.tool_neuron.engine

import android.content.Context
import android.util.Log
import com.dark.gguf_lib.GGMLEngine
import com.dark.gguf_lib.toolcalling.GrammarMode
import com.dark.gguf_lib.toolcalling.ToolCallingConfig
import com.dark.gguf_lib.toolcalling.ToolDefinitionBuilder
import com.dark.tool_neuron.global.DeviceTuner
import com.dark.tool_neuron.global.HardwareScanner
import com.dark.tool_neuron.models.engine_schema.DecodingMetrics
import com.dark.tool_neuron.models.engine_schema.GgufEngineSchema
import com.dark.tool_neuron.models.engine_schema.GgufLoadingParams
import com.dark.tool_neuron.models.engine_schema.toLocal
import com.dark.tool_neuron.models.table_schema.Model
import com.dark.tool_neuron.models.table_schema.ModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.dark.gguf_lib.models.GenerationEvent as LibGenerationEvent

class GGUFEngine {
    private val engine = GGMLEngine()
    private var currentModelId: String? = null

    private var currentToolsJson: String? = null
    private var currentToolCallingConfig: ToolCallingConfig? = null

    val isLoaded: Boolean get() = engine.isLoaded

    suspend fun load(model: Model, config: ModelConfig?): Boolean = withContext(Dispatchers.IO) {
        if (engine.isLoaded) unload()

        val schema = GgufEngineSchema.fromJson(
            config?.modelLoadingParams,
            config?.modelInferenceParams
        )

        val loading = schema.loadingParams
        val inference = schema.inferenceParams

        val success = try {
            engine.load(
                path = model.modelPath,
                contextSize = loading.ctxSize,
                threads = loading.threads,
                flashAttn = loading.flashAttn,
                cacheTypeK = cacheTypeIntToString(loading.cacheTypeK),
                cacheTypeV = cacheTypeIntToString(loading.cacheTypeV)
            )
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM loading model", e)
            try { engine.unload() } catch (_: Throwable) {}
            false
        }

        if (success) {
            engine.setSampling(
                temperature = inference.temperature,
                topK = inference.topK,
                topP = inference.topP,
                minP = inference.minP,
                mirostat = inference.mirostat,
                mirostatTau = inference.mirostatTau,
                mirostatEta = inference.mirostatEta,
                seed = inference.seed
            )

            currentModelId = model.id

            if (inference.systemPrompt.isNotEmpty()) {
                engine.setSystemPrompt(inference.systemPrompt)
            }
            if (inference.chatTemplate.isNotEmpty()) {
                engine.setChatTemplate(inference.chatTemplate)
            }
        }

        success
    }

    suspend fun loadFromFd(fd: Int, config: ModelConfig? = null): Boolean = withContext(Dispatchers.IO) {
        if (engine.isLoaded) unload()

        val schema = GgufEngineSchema.fromJson(
            config?.modelLoadingParams,
            config?.modelInferenceParams
        )

        val loading = schema.loadingParams
        val inference = schema.inferenceParams

        val success = try {
            engine.loadFromFd(
                fd = fd,
                contextSize = loading.ctxSize,
                threads = loading.threads,
                flashAttn = loading.flashAttn,
                cacheTypeK = cacheTypeIntToString(loading.cacheTypeK),
                cacheTypeV = cacheTypeIntToString(loading.cacheTypeV)
            )
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM loading model from FD", e)
            try { engine.unload() } catch (_: Throwable) {}
            false
        }

        if (success) {
            engine.setSampling(
                temperature = inference.temperature,
                topK = inference.topK,
                topP = inference.topP,
                minP = inference.minP,
                mirostat = inference.mirostat,
                mirostatTau = inference.mirostatTau,
                mirostatEta = inference.mirostatEta,
                seed = inference.seed
            )

            currentModelId = "fd_$fd"

            if (inference.systemPrompt.isNotEmpty()) {
                engine.setSystemPrompt(inference.systemPrompt)
            }
            if (inference.chatTemplate.isNotEmpty()) {
                engine.setChatTemplate(inference.chatTemplate)
            }
        }

        success
    }

    // ── Generation ──

    fun generateFlow(prompt: String, maxTokens: Int): Flow<GenerationEvent> =
        engine.generateFlow(prompt, maxTokens).map { it.toLocal() }

    fun generateMultiTurnFlow(messagesJson: String, maxTokens: Int): Flow<GenerationEvent> =
        engine.generateMultiTurnFlow(messagesJson, maxTokens).map { it.toLocal() }

    fun stopGeneration() {
        engine.stopGeneration()
    }

    suspend fun unload() = withContext(Dispatchers.IO) {
        if (engine.isLoaded) {
            engine.unload()
            currentModelId = null
            currentToolsJson = null
            currentToolCallingConfig = null
        }
    }

    fun isModelLoaded(modelId: String): Boolean =
        engine.isLoaded && currentModelId == modelId

    fun getModelInfo(): String? =
        if (engine.isLoaded) engine.getModelInfoJson() else null

    // ── Tool Calling ──

    fun isToolCallingSupported(): Boolean {
        if (!engine.isLoaded) return false
        return try {
            engine.isToolCallingSupported()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Enable tool calling with actual ToolDefinitionBuilder objects (same-process direct call).
     * This properly configures grammar constraints via the native engine.
     */
    fun enableToolCallingDirect(
        toolDefs: List<ToolDefinitionBuilder>,
        config: ToolCallingConfig
    ): Boolean {
        if (!engine.isLoaded) return false

        return try {
            val builtDefs = toolDefs.map { it.build() }
            engine.enableToolCalling(builtDefs, config)
            currentToolCallingConfig = config
            currentToolsJson = null // invalidate JSON cache
            Log.d(TAG, "Tool calling enabled: ${builtDefs.size} tools, grammar=${config.grammarMode.name}, typed=${config.useTypedGrammar}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable tool calling", e)
            false
        }
    }

    /**
     * Legacy JSON-based enableToolCalling (for AIDL compatibility).
     * Falls back to setToolsJson only — grammar not enforced.
     */
    fun enableToolCalling(
        toolsJson: String,
        grammarMode: Int = GrammarMode.LAZY.value,
        useTypedGrammar: Boolean = true
    ): Boolean {
        if (!engine.isLoaded) return false

        return try {
            engine.setToolsJson(toolsJson)
            currentToolsJson = toolsJson
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set tools JSON", e)
            false
        }
    }

    fun setToolsJson(toolsJson: String): Boolean {
        if (!engine.isLoaded) return false
        if (toolsJson == currentToolsJson) return true

        return try {
            engine.setToolsJson(toolsJson)
            currentToolsJson = toolsJson
            true
        } catch (_: Exception) {
            false
        }
    }

    // ── Persona Engine ──

    fun updateSamplerParams(paramsJson: String): Boolean {
        if (!engine.isLoaded) return false
        return try {
            engine.updateSamplerParams(paramsJson)
        } catch (_: Exception) { false }
    }

    fun setLogitBias(biasJson: String): Boolean {
        if (!engine.isLoaded) return false
        return try {
            engine.setLogitBias(biasJson)
            true
        } catch (_: Exception) { false }
    }

    fun loadControlVectors(vectorsJson: String): Boolean {
        if (!engine.isLoaded) return false
        return try {
            engine.loadControlVectors(vectorsJson)
        } catch (_: Exception) { false }
    }

    fun clearControlVector(): Boolean {
        if (!engine.isLoaded) return false
        return try {
            engine.clearControlVector()
            true
        } catch (_: Exception) { false }
    }

    // ── KV Cache State Persistence ──

    fun getStateSize(): Long {
        if (!engine.isLoaded) return 0
        return try {
            engine.getStateSize()
        } catch (_: Exception) { 0 }
    }

    fun stateSaveToFile(path: String): Boolean {
        if (!engine.isLoaded) return false
        return try {
            engine.stateSaveToFile(path)
        } catch (_: Exception) { false }
    }

    fun stateLoadFromFile(path: String): Boolean {
        if (!engine.isLoaded) return false
        return try {
            engine.stateLoadFromFile(path)
        } catch (_: Exception) { false }
    }

    fun clearTools() {
        if (engine.isLoaded) {
            try {
                engine.clearTools()
                currentToolsJson = null
                currentToolCallingConfig = null
            } catch (_: Exception) { }
        }
    }

    // ── New Optimizations ──

    fun setSpeculativeDecoding(enabled: Boolean, nDraft: Int = 4, ngramSize: Int = 4) {
        if (engine.isLoaded) {
            try {
                engine.setSpeculativeDecoding(enabled, nDraft, ngramSize)
            } catch (_: Exception) { }
        }
    }

    fun setPromptCacheDir(path: String) {
        if (engine.isLoaded) {
            try {
                engine.setPromptCacheDir(path)
            } catch (_: Exception) { }
        }
    }

    fun warmUp(): Boolean {
        if (!engine.isLoaded) return false
        return try {
            engine.warmUp()
        } catch (_: Exception) { false }
    }

    fun supportsThinking(): Boolean {
        if (!engine.isLoaded) return false
        return try {
            engine.supportsThinking()
        } catch (_: Exception) { false }
    }

    fun setThinkingEnabled(enabled: Boolean) {
        if (engine.isLoaded) {
            try {
                engine.setThinkingEnabled(enabled)
            } catch (_: Exception) { }
        }
    }

    fun getContextUsage(): Float {
        if (!engine.isLoaded) return 0f
        return try {
            engine.getContextUsage()
        } catch (_: Exception) { 0f }
    }

    // ── Context Window Tracking ──

    fun getContextInfo(prompt: String? = null): com.dark.gguf_lib.ContextInfo {
        if (!engine.isLoaded) return com.dark.gguf_lib.ContextInfo(0, 0, 0, -1, -1)
        return try {
            engine.getContextInfo(prompt)
        } catch (_: Exception) { com.dark.gguf_lib.ContextInfo(0, 0, 0, -1, -1) }
    }

    // ── Character Engine ──

    private val characterEngine by lazy { com.dark.gguf_lib.CharacterEngine(engine) }

    fun setPersonality(personalityJson: String): Boolean {
        if (!engine.isLoaded) return false
        return try {
            val j = org.json.JSONObject(personalityJson)
            characterEngine.setPersonality(com.dark.gguf_lib.Personality(
                name = j.optString("name", ""),
                persona = j.optString("persona", ""),
                temperature = j.optDouble("temperature", 0.7).toFloat(),
                topP = j.optDouble("topP", 0.9).toFloat(),
                repetitionPenalty = j.optDouble("repetitionPenalty", 1.1).toFloat(),
                creativity = j.optDouble("creativity", 0.5).toFloat(),
                verbosity = j.optDouble("verbosity", 0.5).toFloat(),
                formality = j.optDouble("formality", 0.5).toFloat(),
                topK = j.optInt("topK", -1),
                minP = j.optDouble("minP", -1.0).toFloat(),
            ))
            true
        } catch (_: Exception) { false }
    }

    fun setMood(mood: Int): Boolean {
        if (!engine.isLoaded) return false
        return try {
            characterEngine.setMood(com.dark.gguf_lib.Mood.entries[mood])
            true
        } catch (_: Exception) { false }
    }

    fun setCustomMood(tempMod: Float, topPMod: Float, repPenaltyMod: Float): Boolean {
        if (!engine.isLoaded) return false
        return try {
            characterEngine.setCustomMood(tempMod, topPMod, repPenaltyMod)
            true
        } catch (_: Exception) { false }
    }

    fun getCharacterContext(): String {
        if (!engine.isLoaded) return ""
        return try {
            characterEngine.getContext()
        } catch (_: Exception) { "" }
    }

    fun buildPrompt(userPrompt: String): String {
        if (!engine.isLoaded) return userPrompt
        return try {
            characterEngine.buildPrompt(userPrompt)
        } catch (_: Exception) { userPrompt }
    }

    fun setUncensored(enabled: Boolean): Boolean {
        if (!engine.isLoaded) return false
        return try {
            characterEngine.setUncensored(enabled)
            true
        } catch (_: Exception) { false }
    }

    fun isUncensored(): Boolean {
        if (!engine.isLoaded) return false
        return try {
            characterEngine.isUncensored
        } catch (_: Exception) { false }
    }

    // ── Activation Steering ──

    fun calcVectors(prompt: String, onProgress: ((Float) -> Unit)? = null): FloatArray? {
        if (!engine.isLoaded) return null
        return try {
            characterEngine.calcVectors(prompt, onProgress)
        } catch (_: Exception) { null }
    }

    fun applyVectors(data: FloatArray, strength: Float = 1.0f, ilStart: Int = -1, ilEnd: Int = -1): Boolean {
        if (!engine.isLoaded) return false
        return try {
            characterEngine.applyVectors(data, strength, ilStart, ilEnd)
        } catch (_: Exception) { false }
    }

    fun clearVectors(): Boolean {
        if (!engine.isLoaded) return false
        return try {
            characterEngine.clearVectors()
            true
        } catch (_: Exception) { false }
    }

    // ── VLM (Vision Language Model) ──

    fun loadVlmProjector(path: String, threads: Int = 0): Boolean {
        if (!engine.isLoaded) return false
        return try {
            engine.loadVlmProjector(path, threads)
        } catch (_: Exception) { false }
    }

    fun loadVlmProjectorFromFd(fd: Int, threads: Int = 0): Boolean {
        if (!engine.isLoaded) return false
        return try {
            engine.loadVlmProjectorFromFd(fd, threads)
        } catch (_: Exception) { false }
    }

    fun releaseVlmProjector() {
        try { engine.releaseVlmProjector() } catch (_: Exception) { }
    }

    val isVlmLoaded: Boolean get() = engine.isVlmLoaded

    fun getVlmDefaultMarker(): String = engine.getVlmDefaultMarker()

    fun generateVlmFlow(
        messagesJson: String,
        imageData: List<ByteArray>,
        maxTokens: Int
    ): Flow<GenerationEvent> =
        engine.generateVlmFlow(messagesJson, imageData, maxTokens).map { it.toLocal() }

    companion object {
        private const val TAG = "GGUFEngine"

        fun getRecommendedParams(context: Context): GgufLoadingParams {
            val profile = HardwareScanner.scan(context)
            // Default to BALANCED — callers with access to coroutine scope should
            // read performanceMode from DataStore themselves
            return DeviceTuner.tune(profile, modelSizeMB = 0, mode = com.dark.tool_neuron.global.PerformanceMode.BALANCED)
        }

        fun getRecommendedContextSize(context: Context, modelSizeMB: Int, modelName: String = ""): Int {
            return DeviceTuner.recommendContextSize(context, modelSizeMB, modelName)
        }

        /** Convert old Int cache type to new String format */
        private fun cacheTypeIntToString(type: Int): String = when (type) {
            0 -> "f32"
            1 -> "f16"
            8 -> "q5_1"
            9 -> "q8_0"
            10 -> "q4_0"
            11 -> "q4_1"
            12 -> "q5_0"
            else -> "q8_0"
        }
    }
}

// ── Local GenerationEvent (keeps .args for backward compat) ──

sealed class GenerationEvent {
    data class Token(val text: String) : GenerationEvent()
    data class ToolCall(val name: String, val args: String) : GenerationEvent()
    data object Done : GenerationEvent()
    data class Error(val message: String) : GenerationEvent()
    data class Metrics(val metrics: DecodingMetrics) : GenerationEvent()
    data class Progress(val progress: Float) : GenerationEvent()
}

/** Map library GenerationEvent → local GenerationEvent */
private fun LibGenerationEvent.toLocal(): GenerationEvent = when (this) {
    is LibGenerationEvent.Token -> GenerationEvent.Token(text)
    is LibGenerationEvent.ToolCall -> GenerationEvent.ToolCall(name, argsJson)
    is LibGenerationEvent.Done -> GenerationEvent.Done
    is LibGenerationEvent.Error -> GenerationEvent.Error(message)
    is LibGenerationEvent.Metrics -> GenerationEvent.Metrics(metrics.toLocal())
    is LibGenerationEvent.Progress -> GenerationEvent.Progress(progress)
}
