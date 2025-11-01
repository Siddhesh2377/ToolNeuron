package com.dark.neuroverse.model

import kotlinx.serialization.Serializer

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
        val message: String,
    ) : ChatUiState()

    data class Generating(
        val messageId: String, val isFirstToken: Boolean = false
    ) : ChatUiState()

    data class DecodingStream(
        val messageId: String, val startTimeNs: Long,  val stage: DecodingStage = DecodingStage.Decoding
    ) : ChatUiState()

    data class ExecutingTool(
        val toolName: String,
        val messageId: String,
    ) : ChatUiState()

    data class Error(
        val message: String, val isRetryable: Boolean = true, val cause: Throwable? = null
    ) : ChatUiState()

    data object GeneratingTitle : ChatUiState()

    data object DecodingTool : ChatUiState()
}