package com.mp.ai_gguf.models

/**
 * Result from text embedding generation.
 */
data class EmbeddingResult(
    val embeddings: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EmbeddingResult) return false
        return embeddings.contentEquals(other.embeddings)
    }

    override fun hashCode(): Int = embeddings.contentHashCode()
}
