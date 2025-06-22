package com.dark.neuroverse.neurov.mcp.chat.models

data class Message(val role: ROLE, val content: String, val timeStamp: String)

enum class ROLE {
    SYSTEM, USER
}