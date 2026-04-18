package com.dark.tool_neuron.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatCompletionRequest(
    val model: String? = null,
    val messages: List<ChatCompletionMessage>,
    val stream: Boolean = false,
    val temperature: Float? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    @SerialName("top_p") val topP: Float? = null,
    @SerialName("frequency_penalty") val frequencyPenalty: Float? = null,
    @SerialName("presence_penalty") val presencePenalty: Float? = null,
    val stop: List<String>? = null
)

@Serializable
data class ChatCompletionMessage(
    val role: String,
    val content: String
)

@Serializable
data class ChatCompletionDelta(
    val role: String? = null,
    val content: String? = null
)

@Serializable
data class ChatCompletionResponse(
    val id: String,
    @SerialName("object") val obj: String = "chat.completion",
    val created: Long,
    val model: String,
    val choices: List<ChatChoice>,
    val usage: Usage? = null
)

@Serializable
data class ChatChoice(
    val index: Int,
    val message: ChatCompletionMessage? = null,
    val delta: ChatCompletionDelta? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int,
    @SerialName("completion_tokens") val completionTokens: Int,
    @SerialName("total_tokens") val totalTokens: Int
)

@Serializable
data class ImageGenerationRequest(
    val prompt: String,
    val model: String? = null,
    @SerialName("n") val count: Int = 1,
    val size: String = "512x512",
    @SerialName("response_format") val responseFormat: String = "b64_json",
    val user: String? = null
)

@Serializable
data class ImageResponse(
    val created: Long,
    val data: List<ImageData>
)

@Serializable
data class ImageData(
    val url: String? = null,
    @SerialName("b64_json") val b64Json: String? = null,
    @SerialName("revised_prompt") val revisedPrompt: String? = null
)

@Serializable
data class ImageModelsResponse(
    val `object`: String = "list",
    val data: List<ImageModelData>
)

@Serializable
data class ImageModelData(
    val id: String,
    val `object`: String = "model",
    val created: Long,
    @SerialName("owned_by") val ownedBy: String = "toolneuron-local"
)

@Serializable
data class ModelsResponse(
    val `object`: String = "list",
    val data: List<ModelData>
)

@Serializable
data class ModelData(
    val id: String,
    val `object`: String = "model",
    val created: Long,
    @SerialName("owned_by") val ownedBy: String = "toolneuron-local",
    val permission: List<String> = emptyList(),
    val root: String? = null,
    val parent: String? = null
)

@Serializable
data class ProcessResponse(
    val models: List<RunningModel>,
    val hardware: com.dark.tool_neuron.global.HardwareProfile? = null
)

@Serializable
data class RunningModel(
    val name: String,
    val model: String,
    val size: Long,
    val digest: String? = null,
    val details: RunningModelDetails,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("size_vram") val sizeVram: Long
)

@Serializable
data class RunningModelDetails(
    val parent_model: String = "",
    val format: String,
    val family: String? = null,
    val families: List<String>? = null,
    val parameter_size: String? = null,
    val quantization_level: String? = null,
    val backend: String? = null
)
