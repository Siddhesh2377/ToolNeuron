package com.memoryvault.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class VectorIndex(private val dimension: Int) {
    private val mutex = Mutex()
    private val vectors = mutableMapOf<UUID, FloatArray>()

    suspend fun add(blockId: UUID, vector: FloatArray) = mutex.withLock {
        require(vector.size == dimension) { "Vector dimension mismatch: expected $dimension, got ${vector.size}" }
        vectors[blockId] = vector
    }

    suspend fun remove(blockId: UUID) = mutex.withLock {
        vectors.remove(blockId)
    }

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

    suspend fun searchKNN(queryVector: FloatArray, k: Int): List<ScoredResult> {
        require(queryVector.size == dimension) { "Query vector dimension mismatch" }
        
        val results = vectors.map { (blockId, vector) ->
            val similarity = VectorUtils.cosineSimilarity(queryVector, vector)
            ScoredResult(blockId, similarity)
        }
        
        return results.sortedByDescending { it.score }.take(k)
    }

    suspend fun size(): Int = vectors.size

    suspend fun clear() = mutex.withLock {
        vectors.clear()
    }
}

data class ScoredResult(
    val blockId: UUID,
    val score: Float
)