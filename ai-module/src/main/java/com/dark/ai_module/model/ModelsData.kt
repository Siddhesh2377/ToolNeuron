package com.dark.ai_module.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.util.UUID


@Entity(tableName = "local_models")
data class ModelData(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),

    // Basic info
    var modelName: String = "",
    var providerName: String = "",
    var modelType: ModelType = ModelType.TEXT,
    var modelPath: String = "",
    var architecture: String = "",

    // Performance settings
    var threads: Int = (Runtime.getRuntime().availableProcessors().coerceAtLeast(2)) / 2,
    var gpuLayers: Int = 0,
    var useMMAP: Boolean = true,
    var useMLOCK: Boolean = false,
    var ctxSize: Int = 4_048,

    // Sampling settings
    var temp: Float = 0.7f,
    var topK: Int = 20,
    var topP: Float = 0.5f,
    var minP: Float = 0.0f,
    var maxTokens: Int = 2048,

    // Text behavior tuning
    var mirostat: Int = 1,                  // 0=off, 1=v1, 2=v2 (adaptive sampling)
    var mirostatTau: Float = 5.0f,          // target entropy
    var mirostatEta: Float = 0.1f,          // learning rate for mirostat

    // Misc control
    var seed: Int = -1,                     // -1=random, else fixed generation
    var isImported: Boolean = false,
    var modelUrl: String? = null,
    var isToolCalling: Boolean = false,

    // Prompt configuration
    var systemPrompt: String = "You are a helpful assistant.",
    var chatTemplate: String? = null
)


@Serializable
data class OpenRouterModel(
    val id: String,
    val name: String,
    val ctxSize: Int,
    val temperature: Float,
    val topP: Float,
    val supportsTools: Boolean = false
)

fun OpenRouterModel.toModelData(): ModelData {
    return ModelData(
        id = id,
        modelName = name,
        providerName = ModelProvider.OpenRouter.toString(),
        modelUrl = id,
        ctxSize = ctxSize,
        temp = temperature,
        topP = topP,
        isToolCalling = supportsTools
    )
}

sealed class LoadState {
    object Idle : LoadState()
    data class Loading(val progress: Float) : LoadState()
    data class OnLoaded(val model: ModelData) : LoadState()
    data class Error(val message: String) : LoadState()
}

data class GenerationParams(val maxTokens: Int = 2048)

enum class ModelProvider {
    OpenRouter,
    LocalGGUF,
    SherpaONNX,
    HuggingFace
}

enum class ModelType {
    TEXT,
    TTS,
    STT,
    VLM,
    EMBEDDING
}