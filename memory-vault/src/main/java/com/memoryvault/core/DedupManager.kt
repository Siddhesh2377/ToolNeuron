package com.memoryvault.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class DedupManager {
    private val mutex = Mutex()
    private val hashToBlockId = ConcurrentHashMap<String, UUID>()
    private val refCounts = ConcurrentHashMap<UUID, Int>()

    suspend fun findDuplicate(contentHash: String): UUID? {
        return hashToBlockId[contentHash]
    }

    suspend fun register(contentHash: String, blockId: UUID) = mutex.withLock {
        hashToBlockId[contentHash] = blockId
        refCounts[blockId] = 1
    }

    suspend fun addReference(blockId: UUID) = mutex.withLock {
        refCounts[blockId] = (refCounts[blockId] ?: 0) + 1
    }

    suspend fun removeReference(blockId: UUID): Boolean = mutex.withLock {
        val count = refCounts[blockId] ?: return@withLock true
        val newCount = count - 1
        
        if (newCount <= 0) {
            refCounts.remove(blockId)
            hashToBlockId.values.removeIf { it == blockId }
            true
        } else {
            refCounts[blockId] = newCount
            false
        }
    }

    suspend fun getRefCount(blockId: UUID): Int {
        return refCounts[blockId] ?: 0
    }

    suspend fun clear() = mutex.withLock {
        hashToBlockId.clear()
        refCounts.clear()
    }
}