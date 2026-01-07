package com.memoryvault.core

import kotlin.math.sqrt

object VectorUtils {
    
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vectors must have same dimension" }
        
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        
        normA = sqrt(normA)
        normB = sqrt(normB)
        
        if (normA == 0f || normB == 0f) return 0f
        
        return dotProduct / (normA * normB)
    }

    fun euclideanDistance(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vectors must have same dimension" }
        
        var sum = 0f
        for (i in a.indices) {
            val diff = a[i] - b[i]
            sum += diff * diff
        }
        
        return sqrt(sum)
    }

    fun dotProduct(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vectors must have same dimension" }
        
        var result = 0f
        for (i in a.indices) {
            result += a[i] * b[i]
        }
        
        return result
    }

    fun normalize(vector: FloatArray): FloatArray {
        var norm = 0f
        for (v in vector) {
            norm += v * v
        }
        norm = sqrt(norm)
        
        if (norm == 0f) return vector
        
        return FloatArray(vector.size) { i -> vector[i] / norm }
    }

    fun quantizeToInt8(vector: FloatArray): Pair<ByteArray, FloatArray> {
        val min = vector.minOrNull() ?: 0f
        val max = vector.maxOrNull() ?: 0f
        val range = max - min
        
        if (range == 0f) {
            return Pair(ByteArray(vector.size), floatArrayOf(min, max))
        }
        
        val quantized = ByteArray(vector.size) { i ->
            val normalized = (vector[i] - min) / range
            (normalized * 255).toInt().coerceIn(0, 255).toByte()
        }
        
        return Pair(quantized, floatArrayOf(min, max))
    }

    fun dequantizeFromInt8(quantized: ByteArray, scale: FloatArray): FloatArray {
        val min = scale[0]
        val max = scale[1]
        val range = max - min
        
        return FloatArray(quantized.size) { i ->
            val normalized = (quantized[i].toInt() and 0xFF) / 255f
            min + normalized * range
        }
    }
}