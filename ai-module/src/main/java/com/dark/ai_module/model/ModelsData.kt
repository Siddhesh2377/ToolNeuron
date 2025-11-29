package com.dark.ai_module.model

import android.content.Intent
import android.os.Bundle
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import org.json.JSONObject
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

fun Intent.putModelData(key: String = "model_data", modelData: ModelData): Intent {
    putExtra("${key}_id", modelData.id)
    putExtra("${key}_name", modelData.modelName)
    putExtra("${key}_provider", modelData.providerName)
    putExtra("${key}_type", modelData.modelType.name)
    putExtra("${key}_path", modelData.modelPath)
    putExtra("${key}_architecture", modelData.architecture)
    putExtra("${key}_threads", modelData.threads)
    putExtra("${key}_gpu_layers", modelData.gpuLayers)
    putExtra("${key}_use_mmap", modelData.useMMAP)
    putExtra("${key}_use_mlock", modelData.useMLOCK)
    putExtra("${key}_ctx_size", modelData.ctxSize)
    putExtra("${key}_temp", modelData.temp)
    putExtra("${key}_top_k", modelData.topK)
    putExtra("${key}_top_p", modelData.topP)
    putExtra("${key}_min_p", modelData.minP)
    putExtra("${key}_max_tokens", modelData.maxTokens)
    putExtra("${key}_mirostat", modelData.mirostat)
    putExtra("${key}_mirostat_tau", modelData.mirostatTau)
    putExtra("${key}_mirostat_eta", modelData.mirostatEta)
    putExtra("${key}_seed", modelData.seed)
    putExtra("${key}_is_imported", modelData.isImported)
    putExtra("${key}_model_url", modelData.modelUrl)
    putExtra("${key}_is_tool_calling", modelData.isToolCalling)
    putExtra("${key}_system_prompt", modelData.systemPrompt)
    putExtra("${key}_chat_template", modelData.chatTemplate)
    return this
}

/**
 * Extracts ModelData from Intent extras.
 */
fun Intent.getModelData(key: String = "model_data"): ModelData? {
    return try {
        ModelData(
            id = getStringExtra("${key}_id") ?: return null,
            modelName = getStringExtra("${key}_name") ?: "",
            providerName = getStringExtra("${key}_provider") ?: "",
            modelType = getStringExtra("${key}_type")?.let { ModelType.valueOf(it) } ?: ModelType.TEXT,
            modelPath = getStringExtra("${key}_path") ?: "",
            architecture = getStringExtra("${key}_architecture") ?: "",
            threads = getIntExtra("${key}_threads", Runtime.getRuntime().availableProcessors() / 2),
            gpuLayers = getIntExtra("${key}_gpu_layers", 0),
            useMMAP = getBooleanExtra("${key}_use_mmap", true),
            useMLOCK = getBooleanExtra("${key}_use_mlock", false),
            ctxSize = getIntExtra("${key}_ctx_size", 4048),
            temp = getFloatExtra("${key}_temp", 0.7f),
            topK = getIntExtra("${key}_top_k", 20),
            topP = getFloatExtra("${key}_top_p", 0.5f),
            minP = getFloatExtra("${key}_min_p", 0.0f),
            maxTokens = getIntExtra("${key}_max_tokens", 2048),
            mirostat = getIntExtra("${key}_mirostat", 1),
            mirostatTau = getFloatExtra("${key}_mirostat_tau", 5.0f),
            mirostatEta = getFloatExtra("${key}_mirostat_eta", 0.1f),
            seed = getIntExtra("${key}_seed", -1),
            isImported = getBooleanExtra("${key}_is_imported", false),
            modelUrl = getStringExtra("${key}_model_url"),
            isToolCalling = getBooleanExtra("${key}_is_tool_calling", false),
            systemPrompt = getStringExtra("${key}_system_prompt") ?: "You are a helpful assistant.",
            chatTemplate = getStringExtra("${key}_chat_template")
        )
    } catch (e: Exception) {
        null
    }
}

/* ========================================================================= */
/* JSON SERIALIZATION - For storage and network transfer                    */
/* ========================================================================= */

/**
 * Converts ModelData to JSON string for storage or transfer.
 */
fun ModelData.toJson(): String {
    return JSONObject().apply {
        put("id", id)
        put("modelName", modelName)
        put("providerName", providerName)
        put("modelType", modelType.name)
        put("modelPath", modelPath)
        put("architecture", architecture)
        put("threads", threads)
        put("gpuLayers", gpuLayers)
        put("useMMAP", useMMAP)
        put("useMLOCK", useMLOCK)
        put("ctxSize", ctxSize)
        put("temp", temp)
        put("topK", topK)
        put("topP", topP)
        put("minP", minP)
        put("maxTokens", maxTokens)
        put("mirostat", mirostat)
        put("mirostatTau", mirostatTau)
        put("mirostatEta", mirostatEta)
        put("seed", seed)
        put("isImported", isImported)
        put("modelUrl", modelUrl)
        put("isToolCalling", isToolCalling)
        put("systemPrompt", systemPrompt)
        put("chatTemplate", chatTemplate)
    }.toString()
}

/**
 * Creates ModelData from JSON string.
 */
fun ModelData.fromJson(json: String): ModelData? {
    return try {
        val obj = JSONObject(json)
        ModelData(
            id = obj.getString("id"),
            modelName = obj.getString("modelName"),
            providerName = obj.getString("providerName"),
            modelType = ModelType.valueOf(obj.getString("modelType")),
            modelPath = obj.getString("modelPath"),
            architecture = obj.getString("architecture"),
            threads = obj.getInt("threads"),
            gpuLayers = obj.getInt("gpuLayers"),
            useMMAP = obj.getBoolean("useMMAP"),
            useMLOCK = obj.getBoolean("useMLOCK"),
            ctxSize = obj.getInt("ctxSize"),
            temp = obj.getDouble("temp").toFloat(),
            topK = obj.getInt("topK"),
            topP = obj.getDouble("topP").toFloat(),
            minP = obj.getDouble("minP").toFloat(),
            maxTokens = obj.getInt("maxTokens"),
            mirostat = obj.getInt("mirostat"),
            mirostatTau = obj.getDouble("mirostatTau").toFloat(),
            mirostatEta = obj.getDouble("mirostatEta").toFloat(),
            seed = obj.getInt("seed"),
            isImported = obj.getBoolean("isImported"),
            modelUrl = obj.optString("modelUrl").takeIf { it.isNotBlank() },
            isToolCalling = obj.getBoolean("isToolCalling"),
            systemPrompt = obj.getString("systemPrompt"),
            chatTemplate = obj.optString("chatTemplate").takeIf { it.isNotBlank() }
        )
    } catch (e: Exception) {
        null
    }
}

/* ========================================================================= */
/* BUNDLE - For fragment arguments                                          */
/* ========================================================================= */

/**
 * Puts ModelData into a Bundle.
 */
fun Bundle.putModelData(key: String, modelData: ModelData) {
    putString("${key}_json", modelData.toJson())
}

/* ========================================================================= */
/* UTILITY EXTENSIONS                                                        */
/* ========================================================================= */

/**
 * Creates a copy of ModelData with minimal fields for download purposes.
 */
fun ModelData.toDownloadModel(): ModelData {
    return copy(
        threads = 4,
        gpuLayers = 0,
        useMMAP = true,
        useMLOCK = false
    )
}

/**
 * Checks if this model is a cloud/API-based model.
 */
fun ModelData.isCloudModel(): Boolean {
    return providerName == ModelProvider.OpenRouter.toString() ||
            providerName == ModelProvider.HuggingFace.toString()
}

/**
 * Checks if this model is locally stored.
 */
fun ModelData.isLocalModel(): Boolean {
    return providerName == ModelProvider.LocalGGUF.toString() ||
            providerName == ModelProvider.SherpaONNX.toString()
}

/**
 * Gets a user-friendly display name for the provider.
 */
fun ModelData.getProviderDisplayName(): String {
    return when (providerName) {
        ModelProvider.OpenRouter.toString() -> "OpenRouter"
        ModelProvider.LocalGGUF.toString() -> "Local GGUF"
        ModelProvider.SherpaONNX.toString() -> "Sherpa ONNX"
        ModelProvider.HuggingFace.toString() -> "HuggingFace"
        else -> providerName
    }
}

/**
 * Gets formatted model size if available from the path.
 */
fun ModelData.getFormattedSize(): String? {
    if (modelPath.isBlank()) return null

    return try {
        val file = java.io.File(modelPath)
        if (file.exists()) {
            val bytes = file.length()
            when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "%.2f KB".format(bytes / 1024.0)
                bytes < 1024 * 1024 * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024))
                else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
            }
        } else null
    } catch (e: Exception) {
        null
    }
}

// Required companion object for fromJson extension
fun ModelData.create() = ModelData()