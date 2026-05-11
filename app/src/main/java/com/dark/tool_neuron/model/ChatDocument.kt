package com.dark.tool_neuron.model

data class ChatDocument(
    val id: String,
    val chatId: String?,
    val sourceId: String,
    val name: String,
    val mimeType: String,
    val chunkCount: Int,
    val sizeBytes: Long,
    val addedAt: Long = System.currentTimeMillis(),
    val isDeepIndexed: Boolean = false,
    val isRaptorIndexed: Boolean = false,
)
