package com.dark.tool_neuron.model

data class Chat(
    val id: String,
    val title: String,
    val modelId: String,
    val modelName: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messageCount: Int = 0,
    val isPinned: Boolean = false,
    val forkedFromChatId: String? = null,
)

enum class MessageKind {
    Text,
    Image,
    ToolResult;

    companion object {
        fun from(id: Int): MessageKind = entries.getOrNull(id) ?: Text
    }
}

data class TextMetrics(
    val tokensPerSecond: Double = 0.0,
    val timeToFirstTokenMs: Long = 0L,
    val totalTimeMs: Long = 0L,
    val promptTokens: Int = 0,
    val generatedTokens: Int = 0,
) {
    companion object
}

data class MemoryMetrics(
    val modelSizeMB: Double = 0.0,
    val contextSizeMB: Double = 0.0,
    val peakMemoryMB: Double = 0.0,
    val currentMemoryMB: Double = 0.0,
    val usagePercent: Double = 0.0,
) {
    companion object
}

data class ChatMessage(
    val id: String,
    val chatId: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val tokenCount: Int = 0,
    val thinkingContent: String = "",
    val imageUris: List<String> = emptyList(),
    val kind: MessageKind = MessageKind.Text,
    val modelName: String = "",
    val textMetrics: TextMetrics? = null,
    val memoryMetrics: MemoryMetrics? = null,
    val citations: List<Citation> = emptyList(),
    val researchRunId: String? = null,
    val researchState: String = "",
)
