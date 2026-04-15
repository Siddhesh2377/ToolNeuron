package com.dark.tool_neuron.worker

import android.util.Log
import com.dark.tool_neuron.models.table_schema.AiMemory
import com.dark.tool_neuron.repo.ums.UmsMemoryRepository
import kotlin.math.exp
import kotlin.math.min

/**
 * Manages the AI memory lifecycle: staleness detection and cleanup.
 */
class MemoryExtractor(
    private val memoryRepo: UmsMemoryRepository
) {
    companion object {
        private const val TAG = "MemoryExtractor"
        private const val RECENCY_DECAY_RATE = 0.01f
    }

    /**
     * Compute memory strength for display/pruning purposes.
     * strength = recency_factor * access_factor
     */
    fun computeStrength(memory: AiMemory): Float {
        val now = System.currentTimeMillis()
        val dayMs = 86_400_000L
        val daysSinceUpdate = ((now - memory.updatedAt).toFloat() / dayMs).coerceAtLeast(0f)
        val recencyFactor = exp(-RECENCY_DECAY_RATE * daysSinceUpdate)
        val accessFactor = min(1f, memory.accessCount / 5f)
        return recencyFactor * accessFactor.coerceAtLeast(0.1f)
    }

    /**
     * Check if a memory is considered stale (strength < 0.2).
     */
    fun isStale(memory: AiMemory): Boolean {
        return computeStrength(memory) < 0.2f
    }

    /**
     * Delete all stale memories (strength < 0.2).
     * Returns count of deleted memories.
     */
    suspend fun clearStaleMemories(): Int {
        val allMemories = memoryRepo.getAllOnce()
        val stale = allMemories.filter { isStale(it) }
        for (memory in stale) {
            memoryRepo.delete(memory)
        }
        Log.d(TAG, "Cleared ${stale.size} stale memories")
        return stale.size
    }
}
