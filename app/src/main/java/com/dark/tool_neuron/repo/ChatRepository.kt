package com.dark.tool_neuron.repo

import com.dark.tool_neuron.data.VaultManager
import com.dark.tool_neuron.models.messages.Messages
import com.dark.tool_neuron.models.vault.ChatInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChatRepository {

    private val chatRepo get() = VaultManager.chatRepo ?: error("VaultManager not initialized")

    suspend fun createChat(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val chatId = chatRepo.createChat()
            Result.success(chatId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllChats(): Result<List<ChatInfo>> = withContext(Dispatchers.IO) {
        try {
            val chats = chatRepo.getAllChats()
            Result.success(chats)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMessages(chatId: String, limit: Int = 1000): Result<List<Messages>> = withContext(Dispatchers.IO) {
        try {
            val messages = chatRepo.getMessagesForChat(chatId, limit)
            Result.success(messages)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
