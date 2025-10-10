package com.dark.ai_module.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.util.UUID


@Entity(tableName = "local_models")
data class ModelData(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    var modelName: String = "",
    var providerName: String = "",
    var modelPath: String = "",
    var threads: Int = (Runtime.getRuntime().availableProcessors().coerceAtLeast(2)) / 2,
    var gpuLayers: Int = 0,
    var useMMAP: Boolean = true,
    var useMLOCK: Boolean = false,
    var ctxSize: Int = 4_048,
    var temp: Float = 0.7f,
    var topK: Int = 20,
    var topP: Float = 0.5f,
    var minP: Float = 0.0f,
    var maxTokens: Int = 2048,
    var isImported: Boolean = false,
    var modelUrl: String? = null,
    var isToolCalling: Boolean = false,
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
    LocalGGUF
}