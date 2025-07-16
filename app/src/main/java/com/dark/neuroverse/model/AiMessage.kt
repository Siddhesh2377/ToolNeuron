package com.dark.neuroverse.model

data class Message(val role: ROLE, val content: String, val timeStamp: String)

enum class ROLE {
    SYSTEM, USER
}