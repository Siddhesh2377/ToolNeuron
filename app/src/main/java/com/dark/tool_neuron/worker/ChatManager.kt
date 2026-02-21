package com.dark.tool_neuron.worker

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
import com.dark.tool_neuron.vault.VaultHelper
import com.mp.ai_gguf.models.DecodingMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

class ChatManager {

    /**
     * Ensures vault is ready before executing an operation.
     * Waits up to 30 seconds for vault initialization.
     * If vault is not initialized, triggers re-initialization via AppContainer.
     */
    private suspend fun <T> withVaultReady(block: suspend () -> T): Result<T> {
        return try {
            if (!VaultHelper.isInitialized()) {
                // Trigger re-initialization if needed
                com.dark.tool_neuron.di.AppContainer.ensureVaultInitialized()
                val ready = VaultHelper.awaitReady(timeoutMs = 30000)
                if (!ready) {
                    return Result.failure(IllegalStateException("Vault initialization timed out"))
                }
            }
            Result.success(block())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createNewChat(): Result<String> = withContext(Dispatchers.IO) {
        withVaultReady {
            val chatId = VaultHelper.createChat()
            AppStateManager.chatRefreshed()
            chatId
        }
    }

    suspend fun getAllChats(): Result<List<ChatInfo>> = withContext(Dispatchers.IO) {
        withVaultReady {
            VaultHelper.getAllChats()
        }
    }

    suspend fun getChatMessages(chatId: String): Result<List<Messages>> =
        withContext(Dispatchers.IO) {
            withVaultReady {
                VaultHelper.getMessagesForChat(chatId)
            }
        }

    suspend fun addUserMessage(chatId: String, content: String): Result<Messages> =
        withContext(Dispatchers.IO) {
            withVaultReady {
                val message = Messages(
                    role = Role.User,
                    content = MessageContent(
                        contentType = ContentType.Text,
                        content = content
                    )
                )
                VaultHelper.addMessage(chatId, message)
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
        withVaultReady {
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
            VaultHelper.addMessage(chatId, message)
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
        withVaultReady {
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
            VaultHelper.addMessage(chatId, message)
            message
        }
    }

    suspend fun addMessage(chatId: String, message: Messages): Result<Messages> =
        withContext(Dispatchers.IO) {
            withVaultReady {
                VaultHelper.addMessage(chatId, message)
                message
            }
        }

    suspend fun updateMessage(chatId: String, message: Messages): Result<Unit> =
        withContext(Dispatchers.IO) {
            withVaultReady {
                VaultHelper.updateMessage(chatId, message)
                Unit
            }
        }

    suspend fun deleteMessage(messageId: String): Result<Unit> = withContext(Dispatchers.IO) {
        withVaultReady {
            VaultHelper.deleteMessage(messageId)
        }
    }

    suspend fun deleteChat(chatId: String): Result<Unit> = withContext(Dispatchers.IO) {
        withVaultReady {
            VaultHelper.deleteChat(chatId)
        }
    }

    suspend fun searchMessages(query: String): Result<List<Messages>> =
        withContext(Dispatchers.IO) {
            withVaultReady {
                val results = VaultHelper.searchMessages(query)
                results.map { it.message }
            }
        }

    suspend fun exportChat(chatId: String, exportPath: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            withVaultReady {
                val export = VaultHelper.exportChat(chatId)
                val jsonString = kotlinx.serialization.json.Json.encodeToString(
                    ChatExport.serializer(), export
                )
                java.io.File(exportPath).writeText(jsonString)
            }
        }

    suspend fun importChat(importPath: String): Result<String> = withContext(Dispatchers.IO) {
        withVaultReady {
            val jsonString = java.io.File(importPath).readText()
            val export = kotlinx.serialization.json.Json.decodeFromString(
                ChatExport.serializer(), jsonString
            )
            VaultHelper.importChat(export)
        }
    }

    fun getMessagesFlow(chatId: String): Flow<List<Messages>> = flow {
        // Wait for vault to be ready before emitting
        if (VaultHelper.awaitReady(timeoutMs = 10000)) {
            try {
                val messages = VaultHelper.getMessagesForChat(chatId)
                emit(messages)
            } catch (e: Exception) {
                emit(emptyList())
            }
        } else {
            emit(emptyList())
        }
    }

    fun getChatsFlow(): Flow<List<ChatInfo>> = flow {
        // Wait for vault to be ready before emitting
        if (VaultHelper.awaitReady(timeoutMs = 10000)) {
            try {
                val chats = VaultHelper.getAllChats()
                emit(chats)
            } catch (e: Exception) {
                emit(emptyList())
            }
        } else {
            emit(emptyList())
        }
    }
}