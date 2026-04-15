package com.memoryvault.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.TreeMap
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class VaultIndex {
    private val mutex = Mutex()

    private val primaryIndex = ConcurrentHashMap<UUID, BlockMetadata>()
    private val timestampIndex = TreeMap<Long, MutableList<UUID>>()
    private val typeIndex = ConcurrentHashMap<BlockType, MutableList<UUID>>()
    private val categoryIndex = ConcurrentHashMap<String, MutableList<UUID>>()
    private val tagIndex = ConcurrentHashMap<String, MutableList<UUID>>()

    private val lruCache = LRUCache<UUID, BlockMetadata>(1000)

    suspend fun add(metadata: BlockMetadata) = mutex.withLock {
        primaryIndex[metadata.blockId] = metadata
        lruCache.put(metadata.blockId, metadata)

        timestampIndex.getOrPut(metadata.timestamp) { mutableListOf() }.add(metadata.blockId)
        typeIndex.getOrPut(metadata.blockType) { mutableListOf() }.add(metadata.blockId)

        metadata.category?.let { cat ->
            categoryIndex.getOrPut(cat) { mutableListOf() }.add(metadata.blockId)
        }

        metadata.tags.forEach { tag ->
            tagIndex.getOrPut(tag) { mutableListOf() }.add(metadata.blockId)
        }
    }

    suspend fun remove(blockId: UUID) = mutex.withLock {
        val metadata = primaryIndex.remove(blockId) ?: return@withLock
        lruCache.remove(blockId)

        timestampIndex[metadata.timestamp]?.remove(blockId)
        typeIndex[metadata.blockType]?.remove(blockId)
        metadata.category?.let { categoryIndex[it]?.remove(blockId) }
        metadata.tags.forEach { tagIndex[it]?.remove(blockId) }
    }

    suspend fun get(blockId: UUID): BlockMetadata? {
        lruCache.get(blockId)?.let { return it }
        return primaryIndex[blockId]?.also { lruCache.put(blockId, it) }
    }

    suspend fun getByType(type: BlockType): List<BlockMetadata> {
        val ids = typeIndex[type] ?: return emptyList()
        return ids.mapNotNull { primaryIndex[it] }
    }

    suspend fun getByCategory(category: String): List<BlockMetadata> {
        val ids = categoryIndex[category] ?: return emptyList()
        return ids.mapNotNull { primaryIndex[it] }
    }

    suspend fun getAllMetadata(): List<BlockMetadata> {
        return primaryIndex.values.toList()
    }

    suspend fun clear() = mutex.withLock {
        primaryIndex.clear()
        timestampIndex.clear()
        typeIndex.clear()
        categoryIndex.clear()
        tagIndex.clear()
        lruCache.clear()
    }
}

class LRUCache<K, V>(private val maxSize: Int) {
    private val cache = LinkedHashMap<K, V>(maxSize, 0.75f, true)

    @Synchronized
    fun get(key: K): V? = cache[key]

    @Synchronized
    fun put(key: K, value: V) {
        if (cache.size >= maxSize) {
            val firstKey = cache.keys.first()
            cache.remove(firstKey)
        }
        cache[key] = value
    }

    @Synchronized
    fun remove(key: K) {
        cache.remove(key)
    }

    @Synchronized
    fun clear() {
        cache.clear()
    }
}
