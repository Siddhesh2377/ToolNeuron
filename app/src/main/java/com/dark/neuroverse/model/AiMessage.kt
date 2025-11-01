package com.dark.neuroverse.model

import com.mp.data_hub_lib.model.RagResult
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ChatList(
    val id: String, val name: String,   val timestamp: Long = System.currentTimeMillis()
)

@Serializable
enum class Role { User, Assistant, Tool }

@Serializable
data class RunningTool(
    val toolName: String, val toolPreview: String, val toolOutput: ToolOutput
)

@Serializable
data class ToolOutput(
    val pluginName: String = "",
    val type: ToolOutputType = ToolOutputType.Text,
    val output: String = ""
)

@Serializable
enum class ToolOutputType {
    File, Text, Url
}

@Serializable
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val timeStamp: Long = System.currentTimeMillis(),
    val role: Role,
    val text: String,
    val thought: String? = null,
    val tool: RunningTool? = null,
    val ragResult: RagResult? = null,
    val codeCanvas: List<CodeCanvas>? = null,
    val decodingMetrics: DecodingMetrics = DecodingMetrics()
)

/**
 * Decoding metrics for performance tracking
 */
@Serializable
data class DecodingMetrics(
    val type: DecodeType = DecodeType.NORMAL,
    val chatId: String = "",
    val modelName: String = "",
    val startedAtNs: Long = 0,
    val firstTokenAtNs: Long = 0,
    val durationMs: Long = 0
)

@Serializable
enum class DecodeType { NORMAL, REGENERATE }

@Serializable
data class CodeCanvas(
    val id: String = UUID.randomUUID().toString(),
    val code: String,
    val language: String
)