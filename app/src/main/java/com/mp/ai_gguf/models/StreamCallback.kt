package com.mp.ai_gguf.models

/**
 * Callback interface for streaming token generation.
 * Called from native code during generation.
 */
interface StreamCallback {
    fun onToken(token: String)
    fun onToolCall(name: String, argsJson: String)
    fun onDone()
    fun onError(message: String)
    fun onMetrics(
        tps: Float, ttftMs: Float, totalMs: Float,
        tokensEvaluated: Int, tokensPredicted: Int,
        modelMB: Float, ctxMB: Float, peakMB: Float, memPct: Float
    )
}
