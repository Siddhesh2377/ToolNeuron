package com.dark.tool_neuron.vault

import com.dark.tool_neuron.models.messages.Messages
import com.dark.tool_neuron.models.messages.Role
import com.dark.tool_neuron.models.vault.ChatInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

suspend fun VaultHelper.addUserMessage(chatId: String, content: String, contentType: com.dark.tool_neuron.models.messages.ContentType = com.dark.tool_neuron.models.messages.ContentType.Text): String {
    val message = Messages(
        role = Role.User,
        content = com.dark.tool_neuron.models.messages.MessageContent(
            contentType = contentType,
            content = content
        )
    )
    return addMessage(chatId, message)
}

suspend fun VaultHelper.addAssistantMessage(
    chatId: String,
    content: String,
    contentType: com.dark.tool_neuron.models.messages.ContentType = com.dark.tool_neuron.models.messages.ContentType.Text,
    decodingMetrics: com.mp.ai_gguf.models.DecodingMetrics? = null
): String {
    val message = Messages(
        role = Role.Assistant,
        content = com.dark.tool_neuron.models.messages.MessageContent(
            contentType = contentType,
            content = content
        ),
        decodingMetrics = decodingMetrics
    )
    return addMessage(chatId, message)
}

fun VaultHelper.getMessagesFlow(chatId: String, limit: Int = 1000): Flow<List<Messages>> = flow {
    emit(getMessagesForChat(chatId, limit))
}

fun VaultHelper.getChatListFlow(): Flow<List<ChatInfo>> = flow {
    emit(getAllChats())
}

suspend fun VaultHelper.getChatWithMessages(chatId: String): ChatWithMessages? {
    val chatInfo = getAllChats().find { it.chatId == chatId } ?: return null
    val messages = getMessagesForChat(chatId)

    return ChatWithMessages(
        chatInfo = chatInfo,
        messages = messages
    )
}

suspend fun VaultHelper.deleteLastMessage(chatId: String): Boolean {
    val messages = getMessagesForChat(chatId, limit = 1)
    return if (messages.isNotEmpty()) {
        deleteMessage(messages.first().msgId)
        true
    } else {
        false
    }
}

suspend fun VaultHelper.clearChat(chatId: String) {
    val messages = getMessagesForChat(chatId)
    messages.forEach { message ->
        deleteMessage(message.msgId)
    }
}

suspend fun VaultHelper.getUserMessages(chatId: String): List<Messages> {
    return getMessagesForChat(chatId).filter { it.role == Role.User }
}

suspend fun VaultHelper.getAssistantMessages(chatId: String): List<Messages> {
    return getMessagesForChat(chatId).filter { it.role == Role.Assistant }
}

suspend fun VaultHelper.getMessagesByRole(chatId: String, role: Role): List<Messages> {
    return getMessagesForChat(chatId).filter { it.role == role }
}

data class ChatWithMessages(
    val chatInfo: ChatInfo,
    val messages: List<Messages>
)