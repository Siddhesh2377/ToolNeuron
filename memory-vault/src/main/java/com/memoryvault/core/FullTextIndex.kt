package com.memoryvault.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class FullTextIndex {
    private val mutex = Mutex()
    private val invertedIndex = ConcurrentHashMap<String, MutableList<UUID>>()

    suspend fun addDocument(blockId: UUID, text: String) = mutex.withLock {
        val tokens = TextTokenizer.tokenize(text)
        tokens.forEach { token ->
            invertedIndex.getOrPut(token) { mutableListOf() }.add(blockId)
        }
    }

    suspend fun removeDocument(blockId: UUID, text: String) = mutex.withLock {
        val tokens = TextTokenizer.tokenize(text)
        tokens.forEach { token ->
            invertedIndex[token]?.remove(blockId)
        }
    }

    suspend fun search(query: String): List<UUID> {
        val tokens = TextTokenizer.tokenize(query)
        if (tokens.isEmpty()) return emptyList()
        
        val resultSets = tokens.mapNotNull { token ->
            invertedIndex[token]?.toSet()
        }
        
        if (resultSets.isEmpty()) return emptyList()
        
        return resultSets.reduce { acc, set -> acc.intersect(set) }.toList()
    }

    suspend fun searchOr(query: String): List<UUID> {
        val tokens = TextTokenizer.tokenize(query)
        if (tokens.isEmpty()) return emptyList()
        
        val results = mutableSetOf<UUID>()
        tokens.forEach { token ->
            invertedIndex[token]?.let { results.addAll(it) }
        }
        
        return results.toList()
    }

    suspend fun searchPrefix(prefix: String): List<UUID> {
        val normalized = prefix.lowercase()
        val results = mutableSetOf<UUID>()
        
        invertedIndex.keys.forEach { token ->
            if (token.startsWith(normalized)) {
                invertedIndex[token]?.let { results.addAll(it) }
            }
        }
        
        return results.toList()
    }

    suspend fun clear() = mutex.withLock {
        invertedIndex.clear()
    }

    suspend fun size(): Int = invertedIndex.size
}