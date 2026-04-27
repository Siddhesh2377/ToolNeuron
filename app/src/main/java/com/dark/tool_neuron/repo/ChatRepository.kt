package com.dark.tool_neuron.repo

import android.content.Context
import com.dark.hxs.HexStorage
import com.dark.hxs.HxsRecord
import com.dark.tool_neuron.model.Chat
import com.dark.tool_neuron.model.ChatMessage
import com.dark.tool_neuron.model.MemoryMetrics
import com.dark.tool_neuron.model.MessageKind
import com.dark.tool_neuron.model.TextMetrics
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val storage = HexStorage()
    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats.asStateFlow()

    init {
        val dir = File(context.filesDir, "chat_store")
        dir.mkdirs()
        val path = dir.absolutePath
        if (storage.exists(path)) {
            storage.openPlaintext(path)
        } else {
            storage.createPlaintext(path)
        }
        storage.ensureCollection(COL_CHATS)
        storage.ensureCollection(COL_MESSAGES)
        storage.addIndex(COL_CHATS, TAG_ID, HexStorage.WIRE_BYTES)
        storage.addIndex(COL_MESSAGES, TAG_MSG_ID, HexStorage.WIRE_BYTES)
        storage.addIndex(COL_MESSAGES, TAG_MSG_CHAT_ID, HexStorage.WIRE_BYTES)
        refresh()
    }

    fun refresh() {
        _chats.value = storage.getAll(COL_CHATS)
            .map { it.toChat() }
            .sortedByDescending { it.updatedAt }
    }

    fun createChat(modelId: String, modelName: String): Chat {
        val now = System.currentTimeMillis()
        val chat = Chat(
            id = UUID.randomUUID().toString(),
            title = "New Chat",
            modelId = modelId,
            modelName = modelName,
            createdAt = now,
            updatedAt = now,
        )
        storage.put(COL_CHATS, chat.toRecord())
        storage.flush(COL_CHATS)
        refresh()
        return chat
    }

    fun updateChat(chat: Chat) {
        val records = storage.queryString(COL_CHATS, TAG_ID, chat.id)
        records.forEach { storage.delete(COL_CHATS, it.id) }
        storage.put(COL_CHATS, chat.toRecord())
        storage.flush(COL_CHATS)
        refresh()
    }

    fun deleteChat(chatId: String) {
        val chatRecords = storage.queryString(COL_CHATS, TAG_ID, chatId)
        chatRecords.forEach { storage.delete(COL_CHATS, it.id) }
        val msgRecords = storage.queryString(COL_MESSAGES, TAG_MSG_CHAT_ID, chatId)
        msgRecords.forEach { storage.delete(COL_MESSAGES, it.id) }
        storage.flushAll()
        refresh()
    }

    fun pinChat(chatId: String, pinned: Boolean) {
        val chat = getChatById(chatId) ?: return
        updateChat(chat.copy(isPinned = pinned))
    }

    fun getMessages(chatId: String): List<ChatMessage> {
        return storage.queryString(COL_MESSAGES, TAG_MSG_CHAT_ID, chatId)
            .map { it.toChatMessage() }
            .sortedBy { it.timestamp }
    }

    fun addMessage(message: ChatMessage) {
        storage.put(COL_MESSAGES, message.toRecord())
        storage.flush(COL_MESSAGES)
        val chat = getChatById(message.chatId) ?: return
        updateChat(chat.copy(
            updatedAt = System.currentTimeMillis(),
            messageCount = chat.messageCount + 1,
        ))
    }

    fun deleteMessage(messageId: String) {
        val records = storage.queryString(COL_MESSAGES, TAG_MSG_ID, messageId)
        records.forEach { storage.delete(COL_MESSAGES, it.id) }
        storage.flush(COL_MESSAGES)
    }

    fun updateMessage(message: ChatMessage) {
        val records = storage.queryString(COL_MESSAGES, TAG_MSG_ID, message.id)
        records.forEach { storage.delete(COL_MESSAGES, it.id) }
        storage.put(COL_MESSAGES, message.toRecord())
        storage.flush(COL_MESSAGES)
    }

    fun getChatById(chatId: String): Chat? {
        return storage.queryString(COL_CHATS, TAG_ID, chatId)
            .firstOrNull()?.toChat()
    }

    fun getLastMessage(chatId: String): ChatMessage? {
        return storage.queryString(COL_MESSAGES, TAG_MSG_CHAT_ID, chatId)
            .map { it.toChatMessage() }
            .maxByOrNull { it.timestamp }
    }

    fun autoTitle(chatId: String, firstMessage: String) {
        val chat = getChatById(chatId) ?: return
        val title = if (firstMessage.length > 40) firstMessage.take(40) + "…" else firstMessage
        updateChat(chat.copy(title = title))
    }

    fun forkChat(sourceChatId: String, atMessageId: String): Chat? {
        val source = getChatById(sourceChatId) ?: return null
        val all = getMessages(sourceChatId)
        val cutIndex = all.indexOfFirst { it.id == atMessageId }
        if (cutIndex < 0) return null
        val keep = all.subList(0, cutIndex + 1)

        val now = System.currentTimeMillis()
        val newId = UUID.randomUUID().toString()
        val newTitle = if (source.title.endsWith(" (fork)")) source.title else "${source.title} (fork)"
        val newChat = Chat(
            id = newId,
            title = newTitle,
            modelId = source.modelId,
            modelName = source.modelName,
            createdAt = now,
            updatedAt = now,
            messageCount = keep.size,
            isPinned = false,
            forkedFromChatId = sourceChatId,
        )
        storage.put(COL_CHATS, newChat.toRecord())
        keep.forEachIndexed { idx, msg ->
            val cloned = msg.copy(
                id = UUID.randomUUID().toString(),
                chatId = newId,
                timestamp = now + idx,
            )
            storage.put(COL_MESSAGES, cloned.toRecord())
        }
        storage.flushAll()
        refresh()
        return newChat
    }

    companion object {
        private const val COL_CHATS = "chats"
        private const val COL_MESSAGES = "messages"

        private const val TAG_ID = 1
        private const val TAG_TITLE = 2
        private const val TAG_MODEL_ID = 3
        private const val TAG_MODEL_NAME = 4
        private const val TAG_CREATED_AT = 5
        private const val TAG_UPDATED_AT = 6
        private const val TAG_MESSAGE_COUNT = 7
        private const val TAG_IS_PINNED = 8
        private const val TAG_FORKED_FROM = 9

        private const val TAG_MSG_ID = 1
        private const val TAG_MSG_CHAT_ID = 2
        private const val TAG_MSG_ROLE = 3
        private const val TAG_MSG_CONTENT = 4
        private const val TAG_MSG_TIMESTAMP = 5
        private const val TAG_MSG_TOKEN_COUNT = 6
        private const val TAG_MSG_THINKING = 7
        private const val TAG_MSG_IMAGES = 8
        private const val TAG_MSG_KIND = 9
        private const val TAG_MSG_MODEL_NAME = 10
        private const val TAG_MSG_TEXT_METRICS = 11
        private const val TAG_MSG_MEMORY_METRICS = 12
    }

    private fun Chat.toRecord(): HxsRecord {
        val c = this
        return HxsRecord.build {
            putString(TAG_ID, c.id)
            putString(TAG_TITLE, c.title)
            putString(TAG_MODEL_ID, c.modelId)
            putString(TAG_MODEL_NAME, c.modelName)
            putTimestamp(TAG_CREATED_AT, c.createdAt)
            putTimestamp(TAG_UPDATED_AT, c.updatedAt)
            putTimestamp(TAG_MESSAGE_COUNT, c.messageCount.toLong())
            putBool(TAG_IS_PINNED, c.isPinned)
            c.forkedFromChatId?.takeIf { it.isNotBlank() }?.let { putString(TAG_FORKED_FROM, it) }
        }
    }

    private fun HxsRecord.toChat(): Chat = Chat(
        id = getString(TAG_ID),
        title = getString(TAG_TITLE),
        modelId = getString(TAG_MODEL_ID),
        modelName = getString(TAG_MODEL_NAME),
        createdAt = getTimestamp(TAG_CREATED_AT),
        updatedAt = getTimestamp(TAG_UPDATED_AT),
        messageCount = getTimestamp(TAG_MESSAGE_COUNT).toInt(),
        isPinned = getBool(TAG_IS_PINNED),
        forkedFromChatId = getString(TAG_FORKED_FROM).takeIf { it.isNotBlank() },
    )

    private fun ChatMessage.toRecord(): HxsRecord {
        val m = this
        return HxsRecord.build {
            putString(TAG_MSG_ID, m.id)
            putString(TAG_MSG_CHAT_ID, m.chatId)
            putString(TAG_MSG_ROLE, m.role)
            putString(TAG_MSG_CONTENT, m.content)
            putTimestamp(TAG_MSG_TIMESTAMP, m.timestamp)
            putTimestamp(TAG_MSG_TOKEN_COUNT, m.tokenCount.toLong())
            putString(TAG_MSG_THINKING, m.thinkingContent)
            putString(TAG_MSG_IMAGES, JSONArray(m.imageUris).toString())
            putInt(TAG_MSG_KIND, m.kind.ordinal.toLong())
            putString(TAG_MSG_MODEL_NAME, m.modelName)
            m.textMetrics?.let { putString(TAG_MSG_TEXT_METRICS, it.toJson()) }
            m.memoryMetrics?.let { putString(TAG_MSG_MEMORY_METRICS, it.toJson()) }
        }
    }

    private fun HxsRecord.toChatMessage(): ChatMessage {
        val imagesJson = getString(TAG_MSG_IMAGES)
        val uris = if (imagesJson.isNotEmpty()) {
            val arr = JSONArray(imagesJson)
            (0 until arr.length()).map { arr.getString(it) }
        } else {
            emptyList()
        }
        val textJson = getString(TAG_MSG_TEXT_METRICS)
        val memoryJson = getString(TAG_MSG_MEMORY_METRICS)
        return ChatMessage(
            id = getString(TAG_MSG_ID),
            chatId = getString(TAG_MSG_CHAT_ID),
            role = getString(TAG_MSG_ROLE),
            content = getString(TAG_MSG_CONTENT),
            timestamp = getTimestamp(TAG_MSG_TIMESTAMP),
            tokenCount = getTimestamp(TAG_MSG_TOKEN_COUNT).toInt(),
            thinkingContent = getString(TAG_MSG_THINKING),
            imageUris = uris,
            kind = MessageKind.from(getInt(TAG_MSG_KIND, 0L).toInt()),
            modelName = getString(TAG_MSG_MODEL_NAME),
            textMetrics = textJson.takeIf { it.isNotEmpty() }?.let { TextMetrics.fromJson(it) },
            memoryMetrics = memoryJson.takeIf { it.isNotEmpty() }?.let { MemoryMetrics.fromJson(it) },
        )
    }

    private fun TextMetrics.toJson(): String = JSONObject().apply {
        put("tps", tokensPerSecond)
        put("ttft", timeToFirstTokenMs)
        put("total", totalTimeMs)
        put("prompt", promptTokens)
        put("gen", generatedTokens)
    }.toString()

    private fun MemoryMetrics.toJson(): String = JSONObject().apply {
        put("model", modelSizeMB)
        put("ctx", contextSizeMB)
        put("peak", peakMemoryMB)
        put("usage", usagePercent)
    }.toString()

    private fun TextMetrics.Companion.fromJson(json: String): TextMetrics {
        val o = JSONObject(json)
        return TextMetrics(
            tokensPerSecond = o.optDouble("tps", 0.0),
            timeToFirstTokenMs = o.optLong("ttft", 0L),
            totalTimeMs = o.optLong("total", 0L),
            promptTokens = o.optInt("prompt", 0),
            generatedTokens = o.optInt("gen", 0),
        )
    }

    private fun MemoryMetrics.Companion.fromJson(json: String): MemoryMetrics {
        val o = JSONObject(json)
        return MemoryMetrics(
            modelSizeMB = o.optDouble("model", 0.0),
            contextSizeMB = o.optDouble("ctx", 0.0),
            peakMemoryMB = o.optDouble("peak", 0.0),
            usagePercent = o.optDouble("usage", 0.0),
        )
    }
}
