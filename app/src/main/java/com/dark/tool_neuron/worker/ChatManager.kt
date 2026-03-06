package com.dark.tool_neuron.worker

import com.dark.tool_neuron.data.VaultManager
import com.dark.tool_neuron.models.messages.ContentType
import com.dark.tool_neuron.models.messages.ImageGenerationMetrics
import com.dark.tool_neuron.models.messages.MessageContent
import com.dark.tool_neuron.models.messages.Messages
import com.dark.tool_neuron.models.messages.RagResultItem
import com.dark.tool_neuron.models.messages.Role
import com.dark.tool_neuron.models.messages.ToolChainStepData
import com.dark.tool_neuron.models.vault.ChatExport
import com.dark.tool_neuron.models.vault.ChatInfo
import com.dark.tool_neuron.models.vault.MessageSearchResult
import com.dark.tool_neuron.state.AppStateManager
import com.dark.tool_neuron.models.engine_schema.DecodingMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

class ChatManager {

    private val chatRepo get() = VaultManager.chatRepo ?: error("VaultManager not initialized")

    private suspend fun <T> withUmsReady(block: suspend () -> T): Result<T> {
        return try {
            if (!VaultManager.isReady.value) {
                com.dark.tool_neuron.di.AppContainer.ensureVaultInitialized()
                if (!VaultManager.isReady.value) {
                    return Result.failure(IllegalStateException("UMS storage not initialized"))
                }
            }
            Result.success(block())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createNewChat(): Result<String> = withContext(Dispatchers.IO) {
        withUmsReady {
            val chatId = chatRepo.createChat()
            AppStateManager.chatRefreshed()
            chatId
        }
    }

    suspend fun getAllChats(): Result<List<ChatInfo>> = withContext(Dispatchers.IO) {
        withUmsReady {
            chatRepo.getAllChats()
        }
    }

    suspend fun getChatMessages(chatId: String): Result<List<Messages>> =
        withContext(Dispatchers.IO) {
            withUmsReady {
                chatRepo.getMessagesForChat(chatId)
            }
        }

    suspend fun addUserMessage(chatId: String, content: String): Result<Messages> =
        withContext(Dispatchers.IO) {
            withUmsReady {
                val message = Messages(
                    role = Role.User,
                    content = MessageContent(
                        contentType = ContentType.Text,
                        content = content
                    )
                )
                chatRepo.addMessage(chatId, message)
                message
            }
        }

    suspend fun addAssistantMessage(
        chatId: String,
        content: String,
        decodingMetrics: DecodingMetrics? = null,
        ragResults: List<RagResultItem>? = null,
        toolChainSteps: List<ToolChainStepData>? = null,
        agentPlan: String? = null,
        agentSummary: String? = null
    ): Result<Messages> = withContext(Dispatchers.IO) {
        withUmsReady {
            val message = Messages(
                role = Role.Assistant,
                content = MessageContent(
                    contentType = ContentType.Text,
                    content = content
                ),
                decodingMetrics = decodingMetrics,
                ragResults = ragResults,
                toolChainSteps = toolChainSteps,
                agentPlan = agentPlan,
                agentSummary = agentSummary
            )
            chatRepo.addMessage(chatId, message)
            message
        }
    }

    suspend fun addImageMessage(
        chatId: String,
        imageBase64: String,
        prompt: String,
        seed: Long,
        imageMetrics: ImageGenerationMetrics?
    ): Result<Messages> = withContext(Dispatchers.IO) {
        withUmsReady {
            val message = Messages(
                role = Role.Assistant,
                content = MessageContent(
                    contentType = ContentType.Image,
                    content = "Generated image: $prompt",
                    imageData = imageBase64,
                    imagePrompt = prompt,
                    imageSeed = seed
                ),
                imageMetrics = imageMetrics
            )
            chatRepo.addMessage(chatId, message)
            message
        }
    }

    suspend fun addMessage(chatId: String, message: Messages): Result<Messages> =
        withContext(Dispatchers.IO) {
            withUmsReady {
                chatRepo.addMessage(chatId, message)
                message
            }
        }

    suspend fun updateMessage(chatId: String, message: Messages): Result<Unit> =
        withContext(Dispatchers.IO) {
            withUmsReady {
                chatRepo.updateMessage(chatId, message)
                Unit
            }
        }

    suspend fun deleteMessage(messageId: String): Result<Unit> = withContext(Dispatchers.IO) {
        withUmsReady {
            chatRepo.deleteMessage(messageId)
        }
    }

    suspend fun deleteChat(chatId: String): Result<Unit> = withContext(Dispatchers.IO) {
        withUmsReady {
            chatRepo.deleteChat(chatId)
        }
    }

    suspend fun searchMessages(query: String): Result<List<Messages>> =
        withContext(Dispatchers.IO) {
            withUmsReady {
                val results = chatRepo.searchMessages(query)
                results.map { it.message }
            }
        }

    suspend fun exportChat(chatId: String, exportPath: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            withUmsReady {
                val export = chatRepo.exportChat(chatId)
                val jsonString = kotlinx.serialization.json.Json.encodeToString(
                    ChatExport.serializer(), export
                )
                java.io.File(exportPath).writeText(jsonString)
            }
        }

    suspend fun importChat(importPath: String): Result<String> = withContext(Dispatchers.IO) {
        withUmsReady {
            val jsonString = java.io.File(importPath).readText()
            val export = kotlinx.serialization.json.Json.decodeFromString(
                ChatExport.serializer(), jsonString
            )
            chatRepo.importChat(export)
        }
    }

    fun getMessagesFlow(chatId: String): Flow<List<Messages>> = flow {
        try {
            if (VaultManager.isReady.value) {
                val messages = chatRepo.getMessagesForChat(chatId)
                emit(messages)
            } else {
                emit(emptyList())
            }
        } catch (e: Exception) {
            emit(emptyList())
        }
    }

    fun getChatsFlow(): Flow<List<ChatInfo>> = flow {
        try {
            if (VaultManager.isReady.value) {
                val chats = chatRepo.getAllChats()
                emit(chats)
            } else {
                emit(emptyList())
            }
        } catch (e: Exception) {
            emit(emptyList())
        }
    }
}
