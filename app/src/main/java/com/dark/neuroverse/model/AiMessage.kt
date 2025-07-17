package com.dark.neuroverse.model

data class Message(val role: ROLE, var content: String, val timeStamp: String, val isThinking: Boolean = false)

enum class ROLE {
    SYSTEM, USER
}