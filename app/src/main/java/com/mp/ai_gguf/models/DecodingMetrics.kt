package com.mp.ai_gguf.models

/**
 * Performance metrics from a generation run.
 */
data class DecodingMetrics(
    val tokensPerSecond: Float = 0f,
    val timeToFirstTokenMs: Float = 0f,
    val totalTimeMs: Float = 0f,
    val tokensEvaluated: Int = 0,
    val tokensPredicted: Int = 0,
    val modelSizeMB: Float = 0f,
    val contextSizeMB: Float = 0f,
    val peakMemoryMB: Float = 0f,
    val memoryUsagePercent: Float = 0f
)
