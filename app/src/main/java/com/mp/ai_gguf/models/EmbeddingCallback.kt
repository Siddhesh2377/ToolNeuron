package com.mp.ai_gguf.models

/**
 * Callback interface for embedding generation.
 */
interface EmbeddingCallback {
    fun onComplete(result: EmbeddingResult)
    fun onError(message: String)
}
