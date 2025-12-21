package com.mp.ai_engine.models.llm_models

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.File
import java.util.UUID

@Serializable
@Entity(tableName = "gguf_models")
data class GGUFDatabaseModel(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),

    // Basic Info
    val modelName: String,
    val modelDescription: String = "",
    val modelType: ModelType, // TEXT, VLM, or EMBEDDING
    val modelPath: String,
    val modelFileSize: String = "",
    val architecture: Architecture = Architecture.LLAMA,

    // Performance Settings
    val threads: Int = (Runtime.getRuntime().availableProcessors().coerceAtLeast(2)) / 2,
    val gpuLayers: Int = 0,
    val ctxSize: Int = 4096,
    val useMMAP: Boolean = true,
    val useMLOCK: Boolean = false,

    // Generation Parameters
    val maxTokens: Int = 2048,
    val temp: Float = 0.7f,
    val topK: Int = 20,
    val topP: Float = 0.5f,
    val minP: Float = 0.0f,

    // Advanced Sampling
    val mirostat: Int = 0, // 0=off, 1=v1, 2=v2
    val mirostatTau: Float = 5.0f,
    val mirostatEta: Float = 0.1f,

    // Control
    val seed: Int = -1, // -1 = random

    // Prompting
    val systemPrompt: String = "You are a helpful assistant.",
    val chatTemplate: String = "",

    // Tags & Metadata
    val tags: String = "", // Comma-separated: "coding,fast,uncensored"
    val isImported: Boolean = false,

    // Timestamps
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis()
) {
    companion object {
        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /**
         * Create GGUFDatabaseModel from JSON string
         */
        fun fromJson(jsonString: String): GGUFDatabaseModel {
            return json.decodeFromString<GGUFDatabaseModel>(jsonString)
        }
    }

    /**
     * Convert GGUFDatabaseModel to JSON string
     */
    fun toJson(): String {
        return json.encodeToString(this)
    }
}

/**
 * Check if model file exists
 */
fun GGUFDatabaseModel.fileExists(): Boolean {
    return File(modelPath).exists()
}

/**
 * Get actual file size in human-readable format
 */
fun GGUFDatabaseModel.getActualFileSize(): String? {
    val file = File(modelPath)
    if (!file.exists()) return null

    val bytes = file.length()
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.2f KB".format(bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
    }
}

/**
 * Get tags as list
 */
fun GGUFDatabaseModel.getTagsList(): List<String> {
    return tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

/**
 * Create a copy with updated last used timestamp
 */
fun GGUFDatabaseModel.markAsUsed(): GGUFDatabaseModel {
    return copy(lastUsedAt = System.currentTimeMillis())
}

/**
 * Create download-ready copy with safe defaults
 */
fun GGUFDatabaseModel.toDownloadModel(): GGUFDatabaseModel {
    return copy(
        threads = 4, gpuLayers = 0, useMMAP = true, useMLOCK = false
    )
}

/**
 * Check if model supports specific capability
 */
fun GGUFDatabaseModel.supportsVision(): Boolean {
    return modelType == ModelType.VLM
}

fun GGUFDatabaseModel.supportsEmbedding(): Boolean {
    return modelType == ModelType.EMBEDDING
}