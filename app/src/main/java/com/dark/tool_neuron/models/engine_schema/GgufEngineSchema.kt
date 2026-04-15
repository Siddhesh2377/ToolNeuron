package com.dark.tool_neuron.models.engine_schema

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class GgufLoadingParams(
    val threads: Int = 0,  // 0 = auto-detect
    val ctxSize: Int = 4096,
    val batchSize: Int = 512,
    val useMmap: Boolean = true,
    val useMlock: Boolean = false,
    val flashAttn: Boolean = true,      // Flash attention (reduces memory bandwidth)
    val cacheTypeK: Int = 9,            // GGML_TYPE_Q8_0 (quantized KV cache keys)
    val cacheTypeV: Int = 9             // GGML_TYPE_Q8_0 (quantized KV cache values)
)

@Serializable
data class GgufInferenceParams(
    val temperature: Float = 0.7f,
    val topK: Int = 40,
    val topP: Float = 0.9f,
    val minP: Float = 0.05f,
    val mirostat: Int = 0,
    val mirostatTau: Float = 5.0f,
    val mirostatEta: Float = 0.1f,
    val seed: Int = -1,
    val maxTokens: Int = 4096,
    val systemPrompt: String = "",
    val chatTemplate: String = "",
    val toolsJson: String = ""  // JSON array of tool definitions
)

@Serializable
data class GgufEngineSchema(
    val loadingParams: GgufLoadingParams = GgufLoadingParams(),
    val inferenceParams: GgufInferenceParams = GgufInferenceParams()
) {
    fun toLoadingJson(): String = json.encodeToString(loadingParams)

    fun toInferenceJson(): String = json.encodeToString(inferenceParams)

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun fromJson(loadingJson: String?, inferenceJson: String?): GgufEngineSchema {
            val loading = loadingJson?.takeIf { it.isNotBlank() }?.let {
                try {
                    json.decodeFromString<GgufLoadingParams>(it)
                } catch (e: Exception) {
                    GgufLoadingParams()
                }
            } ?: GgufLoadingParams()

            val inference = inferenceJson?.takeIf { it.isNotBlank() }?.let {
                try {
                    json.decodeFromString<GgufInferenceParams>(it)
                } catch (e: Exception) {
                    GgufInferenceParams()
                }
            } ?: GgufInferenceParams()

            return GgufEngineSchema(loading, inference)
        }
    }
}