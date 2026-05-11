package com.dark.plugins.expense

import com.dark.plugin_api.api.HxsApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class Categorizer(
    private val embedder: Embedder,
    private val hxs: HxsApi,
) {

    private var centroids: Map<String, FloatArray> = emptyMap()

    suspend fun ensureCentroids() {
        if (centroids.isNotEmpty()) return
        val loaded = HashMap<String, FloatArray>(Categories.all.size)
        var dirty = false
        for (cat in Categories.all) {
            val key = centroidKey(cat.id)
            val existing = hxs.get(key)
            val vec = if (existing != null && existing.size == embedder.dimension * 4) {
                bytesToFloats(existing)
            } else {
                val fresh = embedder.embed(cat.description)
                hxs.put(key, floatsToBytes(fresh))
                dirty = true
                fresh
            }
            loaded[cat.id] = vec
        }
        centroids = loaded
        if (dirty) hxs.put("centroids:meta", byteArrayOf(VERSION))
    }

    suspend fun rank(text: String): List<CategoryScore> {
        ensureCentroids()
        val q = embedder.embed(text)
        val scored = Categories.all.map { cat ->
            val centroid = centroids[cat.id] ?: return@map CategoryScore(cat, 0f)
            CategoryScore(cat, cosineSimilarity(q, centroid))
        }
        return scored.sortedByDescending { it.score }
    }

    private fun centroidKey(id: String) = "centroids:$id"

    private fun floatsToBytes(arr: FloatArray): ByteArray {
        val bb = ByteBuffer.allocate(arr.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (f in arr) bb.putFloat(f)
        return bb.array()
    }

    private fun bytesToFloats(bytes: ByteArray): FloatArray {
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val out = FloatArray(bytes.size / 4)
        for (i in out.indices) out[i] = bb.float
        return out
    }

    companion object {
        private const val VERSION: Byte = 1
    }
}
