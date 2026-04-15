package com.memoryvault.core

import java.util.UUID

class VectorIndex(private val dimension: Int) {
    private val vectors = mutableMapOf<UUID, FloatArray>()

    suspend fun search(queryVector: FloatArray, limit: Int = 10, threshold: Float = 0.7f): List<ScoredResult> {
        require(queryVector.size == dimension) { "Query vector dimension mismatch" }

        val results = mutableListOf<ScoredResult>()

        vectors.forEach { (blockId, vector) ->
            val similarity = VectorUtils.cosineSimilarity(queryVector, vector)
            if (similarity >= threshold) {
                results.add(ScoredResult(blockId, similarity))
            }
        }

        results.sortByDescending { it.score }
        return results.take(limit)
    }
}

data class ScoredResult(
    val blockId: UUID,
    val score: Float
)
