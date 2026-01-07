package com.dark.tool_neuron.worker

import com.dark.tool_neuron.models.messages.ContentType
import com.dark.tool_neuron.models.messages.MessageContent
import com.dark.tool_neuron.models.messages.Messages
import com.dark.tool_neuron.models.messages.Role
import com.dark.tool_neuron.models.vault.ChatExport
import com.dark.tool_neuron.models.vault.ChatInfo
import com.dark.tool_neuron.vault.VaultHelper
import com.mp.ai_gguf.models.DecodingMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

class ChatManager {

    suspend fun createNewChat(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val chatId = VaultHelper.createChat()
            Result.success(chatId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllChats(): Result<List<ChatInfo>> = withContext(Dispatchers.IO) {
        try {
            val chats = VaultHelper.getAllChats()
            Result.success(chats)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getChatMessages(chatId: String): Result<List<Messages>> =
        withContext(Dispatchers.IO) {
            try {
                val messages = VaultHelper.getMessagesForChat(chatId)
                Result.success(messages)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun addUserMessage(chatId: String, content: String): Result<Messages> =
        withContext(Dispatchers.IO) {
            try {
                val message = Messages(
                    role = Role.User, content = MessageContent(
                        contentType = ContentType.Text, content = content
                    )
                )
                VaultHelper.addMessage(chatId, message)
                Result.success(message)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun addAssistantMessage(
        chatId: String, content: String, decodingMetrics: DecodingMetrics? = null
    ): Result<Messages> = withContext(Dispatchers.IO) {
        try {
            val message = Messages(
                role = Role.Assistant, content = MessageContent(
                    contentType = ContentType.Text, content = content
                ), decodingMetrics = decodingMetrics
            )
            VaultHelper.addMessage(chatId, message)
            Result.success(message)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateMessage(chatId: String, message: Messages): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                VaultHelper.updateMessage(chatId, message)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun deleteMessage(messageId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            VaultHelper.deleteMessage(messageId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteChat(chatId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            VaultHelper.deleteChat(chatId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchMessages(query: String): Result<List<Messages>> =
        withContext(Dispatchers.IO) {
            try {
                val results = VaultHelper.searchMessages(query)
                Result.success(results.map { it.message })
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun exportChat(chatId: String, exportPath: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val export = VaultHelper.exportChat(chatId)
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
            val chatId = VaultHelper.importChat(export)
            Result.success(chatId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getMessagesFlow(chatId: String): Flow<List<Messages>> = flow {
        try {
            val messages = VaultHelper.getMessagesForChat(chatId)
            emit(messages)
        } catch (e: Exception) {
            emit(emptyList())
        }
    }

    fun getChatsFlow(): Flow<List<ChatInfo>> = flow {
        try {
            val chats = VaultHelper.getAllChats()
            emit(chats)
        } catch (e: Exception) {
            emit(emptyList())
        }
    }
}