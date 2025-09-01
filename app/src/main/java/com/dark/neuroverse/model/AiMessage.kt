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
data class Message(
    val id: String =  UUID.randomUUID().toString(), val role: Role, val text: String, val viaPlugin: String? = null
)
