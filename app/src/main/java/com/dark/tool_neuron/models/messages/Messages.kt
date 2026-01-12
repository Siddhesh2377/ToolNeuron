package com.dark.tool_neuron.models.messages

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import com.mp.ai_gguf.models.DecodingMetrics
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.UUID

@Immutable
@Serializable
data class Messages(
    val msgId: String = UUID.randomUUID().toString(),
    val role: Role = Role.Assistant,
    val content: MessageContent = MessageContent(),
    val decodingMetrics: DecodingMetrics? = null,
    val imageMetrics: ImageGenerationMetrics? = null
)

@Immutable
@Serializable
data class MessageContent(
    val contentType: ContentType = ContentType.None,
    val content: String = "",
    val imageData: String? = null, // Base64 encoded image
    val imagePrompt: String? = null, // Original prompt used for image
    val imageSeed: Long? = null // Seed used for image generation
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
enum class ContentType {
    None,
    Text,
    Image,
    TextWithImage // For messages that contain both text and image
}

@Serializable
enum class Role {
    User,
    Assistant
}