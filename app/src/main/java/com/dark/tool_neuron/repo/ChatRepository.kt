package com.dark.tool_neuron.repo

import com.dark.tool_neuron.data.VaultManager
import com.dark.tool_neuron.models.messages.ContentType
import com.dark.tool_neuron.models.messages.MessageContent
import com.dark.tool_neuron.models.messages.Messages
import com.dark.tool_neuron.models.messages.Role
import com.dark.tool_neuron.models.vault.ChatExport
import com.dark.tool_neuron.models.vault.ChatInfo
import com.dark.tool_neuron.models.vault.VaultStatistics
import com.dark.tool_neuron.models.engine_schema.DecodingMetrics
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

    suspend fun getChat(chatId: String): Result<ChatInfo?> = withContext(Dispatchers.IO) {
        try {
            val chat = chatRepo.getAllChats().find { it.chatId == chatId }
            Result.success(chat)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteChat(chatId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            chatRepo.deleteChat(chatId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addMessage(chatId: String, message: Messages): Result<String> = withContext(Dispatchers.IO) {
        try {
            val messageId = chatRepo.addMessage(chatId, message)
            Result.success(messageId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addUserMessage(
        chatId: String,
        content: String,
        contentType: ContentType = ContentType.Text
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val message = Messages(
                role = Role.User,
                content = MessageContent(contentType = contentType, content = content)
            )
            val messageId = chatRepo.addMessage(chatId, message)
            Result.success(messageId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addAssistantMessage(
        chatId: String,
        content: String,
        contentType: ContentType = ContentType.Text,
        decodingMetrics: DecodingMetrics? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val message = Messages(
                role = Role.Assistant,
                content = MessageContent(contentType = contentType, content = content),
                decodingMetrics = decodingMetrics
            )
            val messageId = chatRepo.addMessage(chatId, message)
            Result.success(messageId)
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

    suspend fun getMessage(messageId: String): Result<Messages?> = withContext(Dispatchers.IO) {
        try {
            val message = chatRepo.getMessage(messageId)
            Result.success(message)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateMessage(chatId: String, message: Messages): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val updated = chatRepo.updateMessage(chatId, message)
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteMessage(messageId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            chatRepo.deleteMessage(messageId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchMessages(query: String): Result<List<Messages>> = withContext(Dispatchers.IO) {
        try {
            val results = chatRepo.searchMessages(query)
            Result.success(results.map { it.message })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchInChat(chatId: String, query: String): Result<List<Messages>> = withContext(Dispatchers.IO) {
        try {
            val results = chatRepo.searchInChat(chatId, query)
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getStatistics(): Result<VaultStatistics> = withContext(Dispatchers.IO) {
        try {
            val stats = chatRepo.getVaultStats()
            Result.success(stats)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportChat(chatId: String, exportPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val export = chatRepo.exportChat(chatId)
            val jsonString = kotlinx.serialization.json.Json.encodeToString(
                ChatExport.serializer(), export
            )
            java.io.File(exportPath).writeText(jsonString)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importChat(importPath: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val jsonString = java.io.File(importPath).readText()
            val export = kotlinx.serialization.json.Json.decodeFromString(
                ChatExport.serializer(), jsonString
            )
            val chatId = chatRepo.importChat(export)
            Result.success(chatId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
