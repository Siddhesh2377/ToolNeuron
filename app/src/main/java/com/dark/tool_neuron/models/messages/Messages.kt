package com.dark.tool_neuron.models.messages

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import com.dark.tool_neuron.models.plugins.PluginExecutionMetrics
import com.dark.tool_neuron.models.plugins.PluginResultData
import com.dark.tool_neuron.models.engine_schema.DecodingMetrics
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.UUID

@Immutable
@Serializable
data class Messages(
    val msgId: String = UUID.randomUUID().toString(),
    val role: Role = Role.Assistant,
    val content: MessageContent = MessageContent(),
    val timestamp: Long? = null, // Nullable for backward compatibility with old messages
    val modelId: String? = null,
    val personaId: String? = null,
    val decodingMetrics: DecodingMetrics? = null,
    val imageMetrics: ImageGenerationMetrics? = null,
    val memoryMetrics: MemoryMetrics? = null,
    val ragResults: List<RagResultItem>? = null,
    val pluginMetrics: PluginExecutionMetrics? = null,
    val toolChainSteps: List<ToolChainStepData>? = null,
    val agentPlan: String? = null,
    val agentSummary: String? = null
)

@Serializable
data class ToolChainStepData(
    val round: Int,
    val toolName: String,
    val pluginName: String,
    val args: String,
    val result: String,
    val executionTimeMs: Long,
    val success: Boolean
)

/**
 * Serializable RAG result item for storing with messages
 */
@Serializable
data class RagResultItem(
    val ragName: String,
    val content: String,
    val score: Float,
    val nodeId: String
)

@Immutable
@Serializable
data class MessageContent(
    val contentType: ContentType = ContentType.None,
    val content: String = "",
    val imageData: String? = null, // Base64 encoded image
    val imagePrompt: String? = null, // Original prompt used for image
    val imageSeed: Long? = null, // Seed used for image generation
    val pluginResultData: PluginResultData? = null
)

@Serializable
data class ImageGenerationMetrics(
    val steps: Int,
    val cfgScale: Float,
    val seed: Long,
    val width: Int,
    val height: Int,
    val scheduler: String,
    val generationTimeMs: Long
)

@Serializable
data class MemoryMetrics(
    val modelSizeMB: Int = 0,
    val contextSizeMB: Int = 0,
    val peakMemoryMB: Int = 0,
    val memoryUsagePercent: Float = 0f
)

@Serializable
enum class ContentType {
    None,
    Text,
    Image,
    TextWithImage, // For messages that contain both text and image
    PluginResult
}

@Serializable
enum class Role {
    User,
    Assistant
}