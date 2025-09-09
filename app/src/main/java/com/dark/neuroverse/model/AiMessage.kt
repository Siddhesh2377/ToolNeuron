package com.dark.neuroverse.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ChatINFO(
    val id: String, val name: String
)

@Serializable
enum class Role { User, Assistant, Error }

@Serializable
data class RunningTool(
    val toolName: String,
    val toolPreview: String
)

@Serializable
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val text: String,
    val thought: String? = null,
    val tool: RunningTool? = null
)

