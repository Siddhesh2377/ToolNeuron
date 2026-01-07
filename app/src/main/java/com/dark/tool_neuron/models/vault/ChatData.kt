package com.dark.tool_neuron.models.vault

import com.dark.tool_neuron.models.messages.Messages
import kotlinx.serialization.Serializable

@Serializable
data class ChatData(
    val chatId: String,
    val createdAt: Long
)

data class ChatInfo(
    val chatId: String,
    val createdAt: Long,
    val messageCount: Int,
    val lastMessageTime: Long?
)

data class MessageSearchResult(
    val chatId: String,
    val message: Messages,
    val timestamp: Long
)

@Serializable
data class ChatExport(
    val chatId: String,
    val createdAt: Long,
    val messages: List<Messages>,
    val exportedAt: Long
)

data class VaultStatistics(
    val totalChats: Int,
    val totalMessages: Int,
    val totalSizeBytes: Long,
    val compressionRatio: Float,
    val oldestMessage: Long,
    val newestMessage: Long
)