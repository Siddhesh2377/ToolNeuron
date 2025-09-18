package com.dark.neuroverse.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ChatINFO(
    val id: String, val name: String
)

@Serializable
enum class Role { User, Assistant, Tool }

@Serializable
data class RunningTool(
    val toolName: String,
    val toolPreview: String,
    val toolOutput: ToolOutput
)
@Serializable
data class ToolOutput(
    val toolName: String = "",
    val type: ToolOutputType = ToolOutputType.Text,
    val output: String = ""
)

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
    val tool: RunningTool? = null
)

