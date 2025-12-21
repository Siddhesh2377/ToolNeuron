package com.mp.ai_engine.models.llm_models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "openrouter_models")
data class OpenRouterDatabaseModel(
    @PrimaryKey val id: String, // Use OpenRouter's model ID

    // Basic Info
    val modelName: String,
    val modelDescription: String = "",
    val modelType: ModelType = ModelType.TEXT, // Mostly TEXT or VLM

    // API Configuration
    val endpoint: String = "https://openrouter.ai/api/v1/chat/completions",
    val modelId: String, // e.g., "anthropic/claude-3.5-sonnet"

    // Generation Parameters
    val maxTokens: Int = 2048,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val frequencyPenalty: Float = 0.0f,
    val presencePenalty: Float = 0.0f,

    // Context & Capabilities
    val ctxSize: Int = 4096,
    val supportsTools: Boolean = false,
    val supportsVision: Boolean = false,
    val supportsStreaming: Boolean = true,

    // Pricing (optional, can track costs)
    val promptCostPer1M: Float = 0.0f, // USD per 1M tokens
    val completionCostPer1M: Float = 0.0f,

    // Tags & Metadata
    val tags: String = "", // "fast,cheap,uncensored"
    val isFavorite: Boolean = false,

    // Timestamps
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis()
)

/**
 * Get tags as list
 */
fun OpenRouterDatabaseModel.getTagsList(): List<String> {
    return tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

/**
 * Mark as used
 */
fun OpenRouterDatabaseModel.markAsUsed(): OpenRouterDatabaseModel {
    return copy(lastUsedAt = System.currentTimeMillis())
}

/**
 * Calculate estimated cost for a conversation
 */
fun OpenRouterDatabaseModel.estimateCost(promptTokens: Int, completionTokens: Int): Float {
    val promptCost = (promptTokens / 1_000_000f) * promptCostPer1M
    val completionCost = (completionTokens / 1_000_000f) * completionCostPer1M
    return promptCost + completionCost
}

/**
 * Get display name for UI
 */
fun OpenRouterDatabaseModel.getDisplayName(): String {
    return modelName.ifEmpty { modelId.split("/").lastOrNull() ?: id }
}

/**
 * Check if model is free tier
 */
fun OpenRouterDatabaseModel.isFree(): Boolean {
    return promptCostPer1M == 0.0f && completionCostPer1M == 0.0f
}