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

    suspend fun clear() = mutex.withLock {
        invertedIndex.clear()
    }
}
