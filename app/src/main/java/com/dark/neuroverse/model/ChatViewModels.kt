package com.dark.neuroverse.model

data class StreamingState(
    val messageId: String,
    val visibleBuffer: StringBuilder = StringBuilder(),
    val thoughtBuffer: StringBuilder = StringBuilder(),
    val rawBuffer: StringBuilder = StringBuilder(),
    var inThinkTag: Boolean = false,
    val lastBatchTime: Long = System.currentTimeMillis()
)

/**
 * Unified UI State - Single source of truth
 */
sealed class ChatUiState {
    object Idle : ChatUiState()

    data class Loading(
        val operation: String,
        val progress: Float? = null
    ) : ChatUiState()

    data class Generating(
        val messageId: String,
        val isFirstToken: Boolean = false
    ) : ChatUiState()

    data class DecodingStream(
        val messageId: String,
        val startTimeNs: Long
    ) : ChatUiState()

    data class ExecutingTool(
        val toolName: String,
        val messageId: String
    ) : ChatUiState()

    data class Error(
        val message: String,
        val isRetryable: Boolean = true,
        val cause: Throwable? = null
    ) : ChatUiState()

    data object GeneratingTitle : ChatUiState()
}

/**
 * Decoding metrics for performance tracking
 */
data class DecodingMetrics(
    val type: DecodeType,
    val chatId: String,
    val modelId: String,
    val startedAtNs: Long,
    val firstTokenAtNs: Long,
    val durationMs: Long
)

enum class DecodeType { NORMAL, REGENERATE }