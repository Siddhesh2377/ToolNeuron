package com.dark.tool_neuron.vault

import android.content.Context
import android.util.Log
import com.dark.tool_neuron.BuildConfig
import com.dark.tool_neuron.global.AppPaths
import com.dark.tool_neuron.global.formatBytes
import com.dark.tool_neuron.models.messages.Messages
import com.dark.tool_neuron.models.vault.ChatData
import com.dark.tool_neuron.models.vault.ChatInfo
import com.dark.tool_neuron.ui.screen.memory.LogLevel
import com.dark.tool_neuron.ui.screen.memory.VaultLogger
import com.memoryvault.*
import kotlinx.coroutines.Dispatchers
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

object VaultHelper {
    private lateinit var vault: MemoryVault
    private val mutex = Mutex()
    private var initialized = false

    // Store context for re-initialization
    private lateinit var appContext: Context

    // StateFlow to observe vault readiness
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private const val TAG = "VaultHelper"

    fun isInitialized(): Boolean = initialized

    private fun getKeyAlias(): String {
        val alias = BuildConfig.ALIAS
        return if (alias.isNullOrBlank() || alias == "sample_val" || alias.contains(" ")) {
            "tool_neuron_default_vault_key"
        } else {
            alias
        }
    }

    fun getVault(): MemoryVault {
        if (!initialized) {
            throw IllegalStateException("VaultHelper is not initialized. Call initialize() first.")
        }
        return vault
    }

    /**
     * Waits for vault to be ready with a timeout.
     * Returns true if ready, false if timeout reached.
     */
    suspend fun awaitReady(timeoutMs: Long = 30000): Boolean {
        if (initialized) return true

        return kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            _isReady.first { it }
            true
        } ?: false
    }

    /**
     * Wraps a vault operation with automatic recovery.
     * If the operation fails due to vault state issues, attempts re-initialization and retry.
     */
    private suspend fun <T> withVaultRecovery(
        operation: String,
        block: suspend () -> T
    ): T {
        // Ensure vault is initialized
        if (!initialized) {
            if (::appContext.isInitialized) {
                Log.w(TAG, "$operation: Vault not initialized, attempting initialization...")
                initialize(appContext)
            }
            if (!initialized) {
                throw IllegalStateException("VaultHelper is not initialized. Call initialize() first.")
            }
        }

        return try {
            block()
        } catch (e: Exception) {
            // Don't retry for serialization/parsing errors - those won't be fixed by reinit
            if (e is kotlinx.serialization.SerializationException ||
                e is IllegalArgumentException) {
                throw e
            }

            Log.w(TAG, "$operation failed, attempting vault recovery: ${e.message}")
            VaultLogger.log(LogLevel.WARNING, "RECOVERY", "$operation failed, attempting recovery: ${e.message}")

            try {
                reinitialize()
                // Retry the operation once after recovery
                block()
            } catch (retryException: Exception) {
                Log.e(TAG, "$operation failed after recovery attempt: ${retryException.message}")
                VaultLogger.log(LogLevel.ERROR, "RECOVERY", "$operation failed after recovery: ${retryException.message}", retryException.stackTraceToString())
                throw retryException
            }
        }
    }

    /**
     * Close and re-initialize the vault. Used for recovery from corrupt/stale state.
     */
    private suspend fun reinitialize() {
        if (!::appContext.isInitialized) {
            throw IllegalStateException("Cannot reinitialize: no context available")
        }

        VaultLogger.log(LogLevel.WARNING, "RECOVERY", "Reinitializing vault...")
        mutex.withLock {
            try {
                if (initialized) {
                    vault.close()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error closing vault during reinit: ${e.message}")
            }
            initialized = false
            _isReady.value = false
        }

        // Re-initialize (this acquires its own lock)
        initialize(appContext)
    }

    private inline fun <T> logOperation(
        operation: String,
        block: () -> T
    ): T {
        val startTime = System.currentTimeMillis()
        return try {
            val result = block()
            val duration = System.currentTimeMillis() - startTime
            VaultLogger.log(LogLevel.INFO, "VAULT", "✓ $operation (${duration}ms)")
            result
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            VaultLogger.log(LogLevel.ERROR, "VAULT", "✗ $operation failed (${duration}ms): ${e.message}", e.stackTraceToString())
            throw e
        }
    }

    private fun logCrypto(operation: String, bytesProcessed: Int, duration: Long) {
        val throughput = if (duration > 0) (bytesProcessed / duration.toFloat() * 1000).toInt() else 0
        VaultLogger.log(
            LogLevel.DEBUG,
            "CRYPTO",
            "$operation: ${formatBytes(bytesProcessed)} (${throughput}KB/s, ${duration}ms)"
        )
    }


    suspend fun initialize(context: Context) {
        appContext = context.applicationContext
        mutex.withLock {
            if (!initialized) {
                try {
                    VaultLogger.log(LogLevel.INFO, "INIT", "Initializing Memory Vault...")
                    vault = MemoryVault(
                        context = context,
                        keyAlias = BuildConfig.ALIAS,
                        migrationListener = object : MigrationListener {
                            override fun onMigrationStarted() {
                                Log.d("VaultHelper", "Migration started")
                                VaultLogger.log(LogLevel.WARNING, "MIGRATION", "Starting encryption key migration...")
                            }

                            override fun onMigrationProgress(percent: Float) {
                                Log.d("VaultHelper", "Migration progress: ${(percent * 100).toInt()}%")
                                VaultLogger.log(LogLevel.INFO, "MIGRATION", "Progress: ${(percent * 100).toInt()}%")
                            }

                            override fun onMigrationComplete() {
                                Log.d("VaultHelper", "Migration completed successfully")
                                VaultLogger.log(LogLevel.INFO, "MIGRATION", "✓ Migration completed successfully")
                            }

                            override fun onMigrationFailed(error: Exception) {
                                Log.e("VaultHelper", "Migration failed", error)
                                VaultLogger.log(LogLevel.CRITICAL, "MIGRATION", "✗ Migration failed", error.stackTraceToString())
                            }
                        }
                    )

                    VaultLogger.log(LogLevel.DEBUG, "CRYPTO", "Loading and decrypting vault index...")
                    val initStart = System.currentTimeMillis()
                    vault.initialize()
                    val initDuration = System.currentTimeMillis() - initStart
                    VaultLogger.log(LogLevel.DEBUG, "CRYPTO", "✓ Vault index decrypted and loaded (${initDuration}ms)")

                    initialized = true
                    _isReady.value = true
                    VaultLogger.log(LogLevel.INFO, "INIT", "✓ Memory Vault initialized successfully")
                } catch (e: Exception) {
                    e.printStackTrace()
                    VaultLogger.log(LogLevel.ERROR, "INIT", "Vault initialization failed: ${e.message}")
                    initialized = false
                    _isReady.value = false
                }
            } else {
                // Already initialized, ensure isReady reflects this
                _isReady.value = true
            }
        }
    }

    suspend fun close() {
        mutex.withLock {
            if (initialized) {
                vault.close()
                initialized = false
                _isReady.value = false
            }
        }
    }

    suspend fun getMessagesForChat(
        chatId: String,
        limit: Int = 1000
    ): List<Messages> = withContext(Dispatchers.IO) {
        withVaultRecovery("GET_MESSAGES_FOR_CHAT") {
            VaultLogger.log(LogLevel.DEBUG, "CRYPTO", "Decrypting messages for chat: ${chatId.take(8)}...")
            val decryptStart = System.currentTimeMillis()

            val items = vault.getMessages(
                category = chatId,
                limit = limit
            )

            val messages = items.mapNotNull { item ->
                try {
                    val message = json.decodeFromString<Messages>(item.content)
                    if (message.timestamp == null) {
                        message.copy(timestamp = item.timestamp)
                    } else {
                        message
                    }
                } catch (e: Exception) {
                    VaultLogger.log(LogLevel.ERROR, "VAULT", "Failed to decode message in chat: ${e.message}")
                    null
                }
            }.sortedBy { it.timestamp ?: 0L }

            val decryptDuration = System.currentTimeMillis() - decryptStart
            VaultLogger.log(LogLevel.DEBUG, "CRYPTO", "✓ Decrypted ${messages.size} messages (${decryptDuration}ms)")

            messages
        }
    }

    suspend fun clearVault(context: Context) {
        VaultLogger.log(LogLevel.WARNING, "VAULT", "⚠ CLEAR_VAULT requested - deleting all data")
        val alias = getKeyAlias()
        mutex.withLock {
            if (initialized) {
                vault.close()
            }

            val vaultDir = java.io.File(context.filesDir, "memory_vault")
            val filesDeleted = vaultDir.listFiles()?.size ?: 0
            vaultDir.listFiles()?.forEach { it.delete() }

            vault = MemoryVault(
                context = context,
                keyAlias = alias
            )
            vault.initialize()
            initialized = true
            VaultLogger.log(LogLevel.INFO, "VAULT", "✓ Vault cleared ($filesDeleted files deleted)")
        }
    }

    suspend fun getAllChats(): List<ChatInfo> = withContext(Dispatchers.IO) {
        withVaultRecovery("GET_ALL_CHATS") {
            VaultLogger.log(LogLevel.DEBUG, "CRYPTO", "Decrypting chat metadata...")
            val decryptStart = System.currentTimeMillis()

            val chatItems = vault.getByCategory("chats")

            val chats = chatItems.mapNotNull { item ->
                if (item is com.memoryvault.CustomDataItem) {
                    try {
                        val chatData = json.decodeFromString<ChatData>(item.data.toString())
                        val messageCount = vault.getMessages(category = chatData.chatId, limit = Int.MAX_VALUE).size
                        val lastMessageTime = vault.getMessages(category = chatData.chatId, limit = 1).firstOrNull()?.timestamp

                        ChatInfo(
                            chatId = chatData.chatId,
                            createdAt = chatData.createdAt,
                            messageCount = messageCount,
                            lastMessageTime = lastMessageTime
                        )
                    } catch (e: Exception) {
                        null
                    }
                } else {
                    null
                }
            }.sortedByDescending { it.lastMessageTime ?: it.createdAt }

            val decryptDuration = System.currentTimeMillis() - decryptStart
            VaultLogger.log(LogLevel.DEBUG, "CRYPTO", "✓ Decrypted ${chats.size} chat metadata entries (${decryptDuration}ms)")

            chats
        }
    }

    suspend fun createChat(chatId: String = UUID.randomUUID().toString()): String = withContext(Dispatchers.IO) {
        withVaultRecovery("CREATE_CHAT") {
            logOperation("CREATE_CHAT ${chatId.take(8)}...") {
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
        }
    }

    suspend fun addMessage(chatId: String, message: Messages): String = withContext(Dispatchers.IO) {
        withVaultRecovery("ADD_MESSAGE") {
            logOperation("ADD_MESSAGE chat=${chatId.take(8)}... role=${message.role}") {
                val messageJson = json.encodeToString(message)
                val messageSize = messageJson.toByteArray().size
                VaultLogger.log(LogLevel.DEBUG, "CRYPTO", "Encrypting message block: ${formatBytes(messageSize)}")

                val encryptStart = System.currentTimeMillis()
                val result = vault.addMessage(
                    content = messageJson,
                    category = chatId,
                    tags = setOf("message", message.msgId, message.role.name.lowercase())
                )
                val encryptDuration = System.currentTimeMillis() - encryptStart
                VaultLogger.log(LogLevel.DEBUG, "CRYPTO", "✓ Message encrypted and stored (${encryptDuration}ms)")

                result
            }
        }
    }

    suspend fun getMessage(messageId: String): Messages? = withContext(Dispatchers.IO) {
        withVaultRecovery("GET_MESSAGE") {
            VaultLogger.log(LogLevel.DEBUG, "CRYPTO", "Decrypting message: ${messageId.take(8)}...")
            val decryptStart = System.currentTimeMillis()

            val item = vault.getById(messageId) as? MessageItem ?: return@withVaultRecovery null

            val decryptDuration = System.currentTimeMillis() - decryptStart
            VaultLogger.log(LogLevel.DEBUG, "CRYPTO", "✓ Message decrypted (${decryptDuration}ms)")

            try {
                val message = json.decodeFromString<Messages>(item.content)
                if (message.timestamp == null) {
                    message.copy(timestamp = item.timestamp)
                } else {
                    message
                }
            } catch (e: Exception) {
                VaultLogger.log(LogLevel.ERROR, "VAULT", "Failed to decode message: ${e.message}")
                null
            }
        }
    }

}
