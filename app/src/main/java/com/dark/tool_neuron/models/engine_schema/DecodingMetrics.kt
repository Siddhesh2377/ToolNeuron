package com.dark.tool_neuron.models.engine_schema

import kotlinx.serialization.Serializable

/**
 * Local serializable copy of com.dark.gguf_lib.models.DecodingMetrics.
 * The library version is not @Serializable, so we keep our own for
 * persistence in Messages and chat vaults.
 */
@Serializable
data class DecodingMetrics(
    val tokensPerSecond: Float = 0f,
    val timeToFirstTokenMs: Float = 0f,
    val totalTimeMs: Float = 0f,
    val tokensEvaluated: Int = 0,
    val tokensPredicted: Int = 0,
    val modelSizeMB: Float = 0f,
    val contextSizeMB: Float = 0f,
    val peakMemoryMB: Float = 0f,
    val memoryUsagePercent: Float = 0f,
    val contextTokensUsed: Int = 0,
    val contextTokensMax: Int = 0,
    val contextUsagePercent: Float = 0f,
)

/** Convert library DecodingMetrics to local serializable version */
fun com.dark.gguf_lib.models.DecodingMetrics.toLocal(): DecodingMetrics = DecodingMetrics(
    tokensPerSecond = tokensPerSecond,
    timeToFirstTokenMs = timeToFirstTokenMs,
    totalTimeMs = totalTimeMs,
    tokensEvaluated = tokensEvaluated,
    tokensPredicted = tokensPredicted,
    modelSizeMB = modelSizeMB,
    contextSizeMB = contextSizeMB,
    peakMemoryMB = peakMemoryMB,
    memoryUsagePercent = memoryUsagePercent
)
