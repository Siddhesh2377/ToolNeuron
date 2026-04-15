package com.dark.tool_neuron.repo.ums

import com.dark.tool_neuron.data.Tags
import com.dark.tool_neuron.data.UmsCollections
import com.dark.tool_neuron.models.messages.ContentType
import com.dark.tool_neuron.models.messages.ImageGenerationMetrics
import com.dark.tool_neuron.models.messages.MemoryMetrics
import com.dark.tool_neuron.models.messages.MessageContent
import com.dark.tool_neuron.models.messages.Messages
import com.dark.tool_neuron.models.messages.RagResultItem
import com.dark.tool_neuron.models.messages.Role
import com.dark.tool_neuron.models.messages.ToolChainStepData
import com.dark.tool_neuron.models.plugins.PluginExecutionMetrics
import com.dark.tool_neuron.models.plugins.PluginResultData
import com.dark.tool_neuron.models.vault.ChatExport
import com.dark.tool_neuron.models.vault.ChatInfo
import com.dark.tool_neuron.models.vault.MessageSearchResult
import com.dark.tool_neuron.models.vault.VaultStatistics
import com.dark.ums.UmsRecord
import com.dark.ums.UnifiedMemorySystem
import com.dark.tool_neuron.models.engine_schema.DecodingMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class UmsChatRepository(private val ums: UnifiedMemorySystem) {

    private val chats = UmsCollections.CHATS
    private val messages = UmsCollections.MESSAGES
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady

    fun init() {
        ums.ensureCollection(chats)
        ums.ensureCollection(messages)
        ums.addIndex(chats, Tags.Chat.CHAT_ID, UnifiedMemorySystem.WIRE_BYTES)
        ums.addIndex(chats, Tags.Chat.LAST_MESSAGE_AT, UnifiedMemorySystem.WIRE_FIXED64)
        ums.addIndex(messages, Tags.Message.MSG_ID, UnifiedMemorySystem.WIRE_BYTES)
        ums.addIndex(messages, Tags.Message.CHAT_ID, UnifiedMemorySystem.WIRE_BYTES)
        ums.addIndex(messages, Tags.Message.TIMESTAMP, UnifiedMemorySystem.WIRE_FIXED64)
        _isReady.value = true
    }

    // ── Chat Management ──

    suspend fun createChat(chatId: String = UUID.randomUUID().toString()): String =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val record = UmsRecord.create()
                .putString(Tags.Chat.CHAT_ID, chatId)
                .putTimestamp(Tags.Chat.CREATED_AT, now)
                .putString(Tags.Chat.TITLE, "")
                .putTimestamp(Tags.Chat.LAST_MESSAGE_AT, now)
                .putInt(Tags.Chat.MESSAGE_COUNT, 0)
                .build()
            ums.put(chats, record)
            chatId
        }

    suspend fun deleteChat(chatId: String) = withContext(Dispatchers.IO) {
        // Delete all messages for this chat
        ums.queryString(messages, Tags.Message.CHAT_ID, chatId)
            .forEach { ums.delete(messages, it.id) }
        // Delete chat record
        ums.queryString(chats, Tags.Chat.CHAT_ID, chatId)
            .forEach { ums.delete(chats, it.id) }
    }

    suspend fun getAllChats(): List<ChatInfo> = withContext(Dispatchers.IO) {
        ums.getAll(chats).map { rec ->
            val chatId = rec.getString(Tags.Chat.CHAT_ID) ?: ""
            ChatInfo(
                chatId = chatId,
                createdAt = rec.getTimestamp(Tags.Chat.CREATED_AT) ?: 0L,
                messageCount = rec.getInt(Tags.Chat.MESSAGE_COUNT) ?: 0,
                lastMessageTime = rec.getTimestamp(Tags.Chat.LAST_MESSAGE_AT)
            )
        }.sortedByDescending { it.lastMessageTime ?: it.createdAt }
    }

    // ── Message Operations ──

    suspend fun addMessage(chatId: String, message: Messages): String = withContext(Dispatchers.IO) {
        val record = message.toRecord(chatId)
        ums.put(messages, record)
        updateChatStats(chatId)
        message.msgId
    }

    suspend fun updateMessage(chatId: String, message: Messages): Boolean = withContext(Dispatchers.IO) {
        val existing = ums.queryString(messages, Tags.Message.MSG_ID, message.msgId)
            .firstOrNull() ?: return@withContext false
        ums.put(messages, message.toRecord(chatId, existing.id))
        true
    }

    suspend fun deleteMessage(messageId: String) = withContext(Dispatchers.IO) {
        val rec = ums.queryString(messages, Tags.Message.MSG_ID, messageId).firstOrNull()
        if (rec != null) {
            val chatId = rec.getString(Tags.Message.CHAT_ID) ?: ""
            ums.delete(messages, rec.id)
            if (chatId.isNotEmpty()) updateChatStats(chatId)
        }
    }

    suspend fun getMessagesForChat(chatId: String, limit: Int = 1000): List<Messages> =
        withContext(Dispatchers.IO) {
            ums.queryString(messages, Tags.Message.CHAT_ID, chatId)
                .map { it.toMessages() }
                .sortedBy { it.timestamp ?: 0L }
                .takeLast(limit)
        }

    // ── Search ──

    suspend fun searchMessages(query: String): List<MessageSearchResult> = withContext(Dispatchers.IO) {
        val lower = query.lowercase()
        ums.getAll(messages).map { it.toMessages() to (it.getString(Tags.Message.CHAT_ID) ?: "") }
            .filter { (msg, _) -> msg.content.content.lowercase().contains(lower) }
            .map { (msg, chatId) ->
                MessageSearchResult(chatId, msg, msg.timestamp ?: 0L)
            }
    }

    // ── Statistics ──

    suspend fun getVaultStats(): VaultStatistics = withContext(Dispatchers.IO) {
        val allMsgs = ums.getAll(messages)
        val timestamps = allMsgs.mapNotNull { it.getTimestamp(Tags.Message.TIMESTAMP) }
        VaultStatistics(
            totalChats = ums.count(chats),
            totalMessages = allMsgs.size,
            totalSizeBytes = 0L,
            compressionRatio = 0f,
            oldestMessage = timestamps.minOrNull() ?: 0L,
            newestMessage = timestamps.maxOrNull() ?: 0L
        )
    }

    // ── Import/Export ──

    suspend fun exportChat(chatId: String): ChatExport = withContext(Dispatchers.IO) {
        val chatRecord = ums.queryString(chats, Tags.Chat.CHAT_ID, chatId).firstOrNull()
        val msgs = getMessagesForChat(chatId)
        ChatExport(
            chatId = chatId,
            createdAt = chatRecord?.getTimestamp(Tags.Chat.CREATED_AT) ?: 0L,
            messages = msgs,
            exportedAt = System.currentTimeMillis()
        )
    }

    suspend fun importChat(export: ChatExport): String = withContext(Dispatchers.IO) {
        createChat(export.chatId)
        export.messages.forEach { addMessage(export.chatId, it) }
        export.chatId
    }

    // ── Helpers ──

    private fun updateChatStats(chatId: String) {
        val chatRecords = ums.queryString(chats, Tags.Chat.CHAT_ID, chatId)
        val chatRec = chatRecords.firstOrNull() ?: return
        val msgRecords = ums.queryString(messages, Tags.Message.CHAT_ID, chatId)
        val count = msgRecords.size
        val lastTime = msgRecords.mapNotNull { it.getTimestamp(Tags.Message.TIMESTAMP) }.maxOrNull()
            ?: System.currentTimeMillis()

        val updated = UmsRecord.create()
            .id(chatRec.id)
            .putString(Tags.Chat.CHAT_ID, chatId)
            .putTimestamp(Tags.Chat.CREATED_AT, chatRec.getTimestamp(Tags.Chat.CREATED_AT) ?: 0L)
            .putString(Tags.Chat.TITLE, chatRec.getString(Tags.Chat.TITLE) ?: "")
            .putTimestamp(Tags.Chat.LAST_MESSAGE_AT, lastTime)
            .putInt(Tags.Chat.MESSAGE_COUNT, count)
        val modelId = chatRec.getString(Tags.Chat.PRIMARY_MODEL_ID)
        if (modelId != null) updated.putString(Tags.Chat.PRIMARY_MODEL_ID, modelId)
        val personaId = chatRec.getString(Tags.Chat.PRIMARY_PERSONA_ID)
        if (personaId != null) updated.putString(Tags.Chat.PRIMARY_PERSONA_ID, personaId)
        ums.put(chats, updated.build())
    }

    // ── Message serialization ──

    private fun Messages.toRecord(chatId: String, existingId: Int = 0): UmsRecord {
        val b = UmsRecord.create()
        if (existingId != 0) b.id(existingId)
        b.putString(Tags.Message.MSG_ID, msgId)
        b.putString(Tags.Message.CHAT_ID, chatId)
        b.putInt(Tags.Message.ROLE, if (role == Role.User) 0 else 1)
        b.putString(Tags.Message.CONTENT_TYPE, content.contentType.name)
        b.putString(Tags.Message.CONTENT, content.content)
        if (content.imageData != null) b.putString(Tags.Message.IMAGE_DATA, content.imageData)
        if (content.imagePrompt != null) b.putString(Tags.Message.IMAGE_PROMPT, content.imagePrompt)
        if (content.imageSeed != null) b.putTimestamp(Tags.Message.IMAGE_SEED, content.imageSeed)
        if (timestamp != null) b.putTimestamp(Tags.Message.TIMESTAMP, timestamp)
        if (decodingMetrics != null) b.putString(Tags.Message.DECODING_METRICS, json.encodeToString(decodingMetrics))
        if (imageMetrics != null) b.putString(Tags.Message.IMAGE_METRICS, json.encodeToString(imageMetrics))
        if (memoryMetrics != null) b.putString(Tags.Message.MEMORY_METRICS, json.encodeToString(memoryMetrics))
        if (ragResults != null) b.putString(Tags.Message.RAG_RESULTS, json.encodeToString(ragResults))
        if (pluginMetrics != null) b.putString(Tags.Message.PLUGIN_METRICS, json.encodeToString(pluginMetrics))
        if (toolChainSteps != null) b.putString(Tags.Message.TOOL_CHAIN_STEPS, json.encodeToString(toolChainSteps))
        if (agentPlan != null) b.putString(Tags.Message.AGENT_PLAN, agentPlan)
        if (agentSummary != null) b.putString(Tags.Message.AGENT_SUMMARY, agentSummary)
        if (modelId != null) b.putString(Tags.Message.MODEL_ID, modelId)
        if (personaId != null) b.putString(Tags.Message.PERSONA_ID, personaId)
        if (content.pluginResultData != null) {
            b.putString(Tags.Message.PLUGIN_RESULT_DATA, json.encodeToString(content.pluginResultData))
        }
        return b.build()
    }

    private fun UmsRecord.toMessages(): Messages {
        val roleInt = getInt(Tags.Message.ROLE) ?: 1
        val contentType = getString(Tags.Message.CONTENT_TYPE)?.let { name ->
            runCatching { ContentType.valueOf(name) }.getOrNull()
        } ?: getInt(Tags.Message.CONTENT_TYPE)?.let { ordinal ->
            // Fallback: read legacy ordinal-based values
            ContentType.entries.getOrElse(ordinal) { ContentType.None }
        } ?: ContentType.None

        val pluginData = getString(Tags.Message.PLUGIN_RESULT_DATA)?.let {
            runCatching { json.decodeFromString<PluginResultData>(it) }.getOrNull()
        }

        return Messages(
            msgId = getString(Tags.Message.MSG_ID) ?: "",
            role = if (roleInt == 0) Role.User else Role.Assistant,
            content = MessageContent(
                contentType = contentType,
                content = getString(Tags.Message.CONTENT) ?: "",
                imageData = getString(Tags.Message.IMAGE_DATA),
                imagePrompt = getString(Tags.Message.IMAGE_PROMPT),
                imageSeed = getTimestamp(Tags.Message.IMAGE_SEED),
                pluginResultData = pluginData
            ),
            timestamp = getTimestamp(Tags.Message.TIMESTAMP),
            modelId = getString(Tags.Message.MODEL_ID),
            personaId = getString(Tags.Message.PERSONA_ID),
            decodingMetrics = getString(Tags.Message.DECODING_METRICS)?.let {
                runCatching { json.decodeFromString<DecodingMetrics>(it) }.getOrNull()
            },
            imageMetrics = getString(Tags.Message.IMAGE_METRICS)?.let {
                runCatching { json.decodeFromString<ImageGenerationMetrics>(it) }.getOrNull()
            },
            memoryMetrics = getString(Tags.Message.MEMORY_METRICS)?.let {
                runCatching { json.decodeFromString<MemoryMetrics>(it) }.getOrNull()
            },
            ragResults = getString(Tags.Message.RAG_RESULTS)?.let {
                runCatching { json.decodeFromString<List<RagResultItem>>(it) }.getOrNull()
            },
            pluginMetrics = getString(Tags.Message.PLUGIN_METRICS)?.let {
                runCatching { json.decodeFromString<PluginExecutionMetrics>(it) }.getOrNull()
            },
            toolChainSteps = getString(Tags.Message.TOOL_CHAIN_STEPS)?.let {
                runCatching { json.decodeFromString<List<ToolChainStepData>>(it) }.getOrNull()
            },
            agentPlan = getString(Tags.Message.AGENT_PLAN),
            agentSummary = getString(Tags.Message.AGENT_SUMMARY)
        )
    }
}
