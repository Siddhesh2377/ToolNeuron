package com.dark.tool_neuron.util

import com.dark.tool_neuron.model.Chat
import com.dark.tool_neuron.model.ChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ExportFormat(val extension: String, val mimeType: String) {
    PlainText("txt", "text/plain"),
    Markdown("md", "text/markdown"),
}

object ChatExporter {

    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    private val dateOnlyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun format(chat: Chat, messages: List<ChatMessage>, format: ExportFormat): String =
        when (format) {
            ExportFormat.Markdown -> formatMarkdown(chat, messages)
            ExportFormat.PlainText -> formatPlainText(chat, messages)
        }

    fun suggestedFilename(chat: Chat, format: ExportFormat): String {
        val safe = chat.title
            .replace(Regex("[^A-Za-z0-9 _-]"), "")
            .trim()
            .replace(Regex("\\s+"), "_")
            .ifEmpty { "chat" }
            .take(40)
        return "${safe}_${dateOnlyFormat.format(Date(chat.updatedAt))}.${format.extension}"
    }

    private fun formatMarkdown(chat: Chat, messages: List<ChatMessage>): String = buildString {
        append("# ").append(chat.title).append('\n').append('\n')
        append("Model: ").append(chat.modelName).append("  \n")
        append("Created: ").append(timestampFormat.format(Date(chat.createdAt))).append("  \n")
        append("Messages: ").append(messages.size).append('\n').append('\n')
        append("---").append('\n').append('\n')
        for (msg in messages) {
            val author = when (msg.role) {
                "user" -> "You"
                "assistant" -> chat.modelName.ifBlank { "Assistant" }
                else -> msg.role.replaceFirstChar { it.uppercase() }
            }
            append("## ").append(author)
            append(" — ").append(timestampFormat.format(Date(msg.timestamp))).append('\n').append('\n')
            if (msg.thinkingContent.isNotBlank()) {
                append("> _Thinking:_ ").append(msg.thinkingContent.trim()).append('\n').append('\n')
            }
            append(msg.content.trim()).append('\n').append('\n')
        }
    }

    private fun formatPlainText(chat: Chat, messages: List<ChatMessage>): String = buildString {
        append("=== ").append(chat.title).append(" ===\n")
        append("Model: ").append(chat.modelName).append('\n')
        append("Date: ").append(timestampFormat.format(Date(chat.createdAt))).append('\n')
        append("Messages: ").append(messages.size).append('\n').append('\n')
        for (msg in messages) {
            val author = when (msg.role) {
                "user" -> "You"
                "assistant" -> chat.modelName.ifBlank { "Assistant" }
                else -> msg.role.replaceFirstChar { it.uppercase() }
            }
            append("--- ").append(author)
            append(" (").append(timestampFormat.format(Date(msg.timestamp))).append(") ---\n")
            val cleaned = MarkdownStripper.strip(msg.content)
            append(cleaned).append('\n').append('\n')
        }
    }
}
