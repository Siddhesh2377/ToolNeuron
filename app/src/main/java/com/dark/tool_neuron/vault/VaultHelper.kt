package com.dark.tool_neuron.vault

import android.content.Context
import com.dark.tool_neuron.models.messages.Messages
import com.dark.tool_neuron.models.vault.ChatData
import com.dark.tool_neuron.models.vault.ChatExport
import com.dark.tool_neuron.models.vault.ChatInfo
import com.dark.tool_neuron.models.vault.MessageSearchResult
import com.dark.tool_neuron.models.vault.VaultStatistics
import com.memoryvault.MemoryVault
import com.memoryvault.MessageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.UUID

object VaultHelper {
    private lateinit var vault: MemoryVault
    private val mutex = Mutex()
    private var initialized = false

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun initialize(context: Context) {
        mutex.withLock {
            if (!initialized) {
                try {
                    vault = MemoryVault(context)
                    vault.initialize()
                    initialized = true
                } catch (e: Exception) {
                    e.printStackTrace()

                    val vaultDir = java.io.File(context.filesDir, "memory_vault")
                    vaultDir.deleteRecursively()

                    vault = MemoryVault(context)
                    vault.initialize()
                    initialized = true
                }
            }
        }
    }

    suspend fun close() {
        mutex.withLock {
            if (initialized) {
                vault.close()
                initialized = false
            }
        }
    }

    suspend fun clearVault(context: Context) {
        mutex.withLock {
            if (initialized) {
                vault.close()
            }

            val vaultDir = java.io.File(context.filesDir, "memory_vault")
            vaultDir.listFiles()?.forEach { it.delete() }

            vault = MemoryVault(context)
            vault.initialize()
            initialized = true
        }
    }

    suspend fun createChat(chatId: String = UUID.randomUUID().toString()): String = withContext(Dispatchers.IO) {
        val chatData = ChatData(chatId = chatId, createdAt = System.currentTimeMillis())
        val jsonString = json.encodeToString(chatData)

        vault.addCustomData(
            dataType = "chat",
            data = org.json.JSONObject(jsonString),
            category = "chats",
            tags = setOf("chat", chatId)
        )

        chatId
    }

    suspend fun addMessage(chatId: String, message: Messages): String = withContext(Dispatchers.IO) {
        val messageJson = json.encodeToString(message)

        vault.addMessage(
            content = messageJson,
            category = chatId,
            tags = setOf("message", message.msgId, message.role.name.lowercase())
        )
    }

    suspend fun getMessage(messageId: String): Messages? = withContext(Dispatchers.IO) {
        val item = vault.getById(messageId) as? MessageItem ?: return@withContext null

        try {
            json.decodeFromString<Messages>(item.content)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getMessagesForChat(
        chatId: String,
        limit: Int = 1000
    ): List<Messages> = withContext(Dispatchers.IO) {
        val items = vault.getMessages(
            category = chatId,
            limit = limit
        )

        items.mapNotNull { item ->
            try {
                json.decodeFromString<Messages>(item.content)
            } catch (e: Exception) {
                null
            }
        }.sortedBy { it.msgId }
    }

    suspend fun getMessagesForChatPaged(
        chatId: String,
        fromTime: Long? = null,
        toTime: Long? = null,
        limit: Int = 50
    ): List<Messages> = withContext(Dispatchers.IO) {
        val items = vault.getMessages(
            category = chatId,
            fromTime = fromTime,
            toTime = toTime,
            limit = limit
        )

        items.mapNotNull { item ->
            try {
                json.decodeFromString<Messages>(item.content)
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun updateMessage(chatId: String, message: Messages): Boolean = withContext(Dispatchers.IO) {
        try {
            deleteMessage(message.msgId)
            addMessage(chatId, message)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteMessage(messageId: String) = withContext(Dispatchers.IO) {
        vault.delete(messageId)
    }

    suspend fun deleteChat(chatId: String) = withContext(Dispatchers.IO) {
        val messages = getMessagesForChat(chatId)
        messages.forEach { message ->
            vault.delete(message.msgId)
        }

        val chats = vault.getByCategory("chats")
        chats.forEach { chat ->
            if (chat.tags.contains(chatId)) {
                vault.delete(chat.id)
            }
        }
    }

    suspend fun getAllChats(): List<ChatInfo> = withContext(Dispatchers.IO) {
        val chatItems = vault.getByCategory("chats")

        chatItems.mapNotNull { item ->
            if (item is com.memoryvault.CustomDataItem) {
                try {
                    val chatData = json.decodeFromString<ChatData>(item.data.toString())
                    val messageCount = getMessageCount(chatData.chatId)

                    ChatInfo(
                        chatId = chatData.chatId,
                        createdAt = chatData.createdAt,
                        messageCount = messageCount,
                        lastMessageTime = getLastMessageTime(chatData.chatId)
                    )
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        }.sortedByDescending { it.lastMessageTime ?: it.createdAt }
    }

    suspend fun searchMessages(query: String): List<MessageSearchResult> = withContext(Dispatchers.IO) {
        val results = vault.textSearch(query)

        results.mapNotNull { item ->
            if (item is MessageItem && item.category != "chats") {
                try {
                    val message = json.decodeFromString<Messages>(item.content)
                    MessageSearchResult(
                        chatId = item.category ?: "",
                        message = message,
                        timestamp = item.timestamp
                    )
                } catch (e: Exception) {
                    null
                }
            } else null
        }
    }

    suspend fun searchInChat(chatId: String, query: String): List<Messages> = withContext(Dispatchers.IO) {
        val allMessages = getMessagesForChat(chatId)
        val queryLower = query.lowercase()

        allMessages.filter { message ->
            message.content.content.lowercase().contains(queryLower)
        }
    }

    suspend fun getMessageCount(chatId: String): Int = withContext(Dispatchers.IO) {
        vault.getMessages(category = chatId, limit = Int.MAX_VALUE).size
    }

    suspend fun getLastMessageTime(chatId: String): Long? = withContext(Dispatchers.IO) {
        val messages = vault.getMessages(category = chatId, limit = 1)
        messages.firstOrNull()?.timestamp
    }

    suspend fun exportChat(chatId: String): ChatExport = withContext(Dispatchers.IO) {
        val messages = getMessagesForChat(chatId)
        val chatInfo = getAllChats().find { it.chatId == chatId }

        ChatExport(
            chatId = chatId,
            createdAt = chatInfo?.createdAt ?: 0L,
            messages = messages,
            exportedAt = System.currentTimeMillis()
        )
    }

    suspend fun importChat(export: ChatExport): String = withContext(Dispatchers.IO) {
        val newChatId = createChat(export.chatId)

        export.messages.forEach { message ->
            addMessage(newChatId, message)
        }

        newChatId
    }

    suspend fun getVaultStats(): VaultStatistics = withContext(Dispatchers.IO) {
        val stats = vault.getStats()
        val chatCount = getAllChats().size

        VaultStatistics(
            totalChats = chatCount,
            totalMessages = stats.messageCount,
            totalSizeBytes = stats.totalSizeBytes,
            compressionRatio = stats.compressionRatio,
            oldestMessage = stats.oldestItem,
            newestMessage = stats.newestItem
        )
    }

    suspend fun performMaintenance() = withContext(Dispatchers.IO) {
        vault.defragment()
    }

    suspend fun createBackup(backupPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val backupFile = java.io.File(backupPath)
            val result = vault.backup(backupFile)
            result.success
        } catch (e: Exception) {
            false
        }
    }

    suspend fun restoreBackup(backupPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val backupFile = java.io.File(backupPath)
            vault.restore(backupFile)
            true
        } catch (e: Exception) {
            false
        }
    }
}
