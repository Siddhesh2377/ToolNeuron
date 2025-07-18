package com.dark.neuroverse.model

import kotlinx.serialization.Serializable


@Serializable
data class Message(
    val role: ROLE, var content: String, val timeStamp: String, val document: DOC? = null
)

@Serializable
data class DOC(
    val path: String, val name: String, val content: String, val type: String
)

@Serializable
enum class ROLE {
    USER, SYSTEM // or whatever roles you use
}

data class ChatINFO(
    val id: String, val name: String
)