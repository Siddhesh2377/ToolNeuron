package com.dark.neuroverse.model

import kotlinx.serialization.Serializable
import java.util.UUID


@Serializable
data class Message(
    val role: ROLE, var content: String, val timeStamp: String, val document: MutableList<FileAttachment> = mutableListOf()
)

@Serializable
data class DOC(
    val path: String, val name: String, val content: String, val type: String
)

@Serializable
enum class ROLE {
    USER, SYSTEM // or whatever roles you use
}
@Serializable
data class ChatINFO(
    val id: String, val name: String
)
@Serializable
data class FileAttachment(
    val id: String = UUID.randomUUID().toString(),
    val doc: DOC = DOC("", "", "", ""),
    val isLoading: Boolean = true
)

