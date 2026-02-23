package com.dark.tool_neuron.models.engine_schema

import android.app.ActivityManager
import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class DeviceTier {
    LOW_END,    // < 4GB RAM
    MID_RANGE,  // 4-8GB RAM
    HIGH_END    // > 8GB RAM
}

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
) {
    companion object {
        fun forDeviceTier(tier: DeviceTier): GgufLoadingParams = when (tier) {
            DeviceTier.LOW_END -> GgufLoadingParams(
                threads = 0,
                ctxSize = 2048,
                batchSize = 256,
                useMmap = true,
                useMlock = false
            )
            DeviceTier.MID_RANGE -> GgufLoadingParams(
                threads = 0,
                ctxSize = 4096,
                batchSize = 512,
                useMmap = true,
                useMlock = false
            )
            DeviceTier.HIGH_END -> GgufLoadingParams(
                threads = 0,
                ctxSize = 8192,
                batchSize = 512,
                useMmap = true,
                useMlock = false
            )
        }

        fun recommendedContextSize(availableMemoryMB: Int, modelSizeMB: Int): Int {
            val freeAfterModel = availableMemoryMB - modelSizeMB
            return when {
                freeAfterModel < 1024 -> 1024
                freeAfterModel < 2048 -> 2048
                freeAfterModel < 4096 -> 4096
                else -> 8192
            }
        }
    }
}

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