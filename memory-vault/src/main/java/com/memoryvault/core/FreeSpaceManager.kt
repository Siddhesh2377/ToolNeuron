package com.memoryvault.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.TreeSet

class FreeSpaceManager {
    private val mutex = Mutex()
    private val freeSpans = TreeSet<FreeSpan>(compareBy { it.size })

    suspend fun addFreeSpace(offset: Long, size: Long) = mutex.withLock {
        val span = FreeSpan(offset, size, System.currentTimeMillis())
        freeSpans.add(span)
        
        mergeFreeSpans()
    }

    suspend fun allocate(size: Long): Long? = mutex.withLock {
        val suitable = freeSpans.firstOrNull { it.size >= size }
        
        suitable?.let { span ->
            freeSpans.remove(span)
            
            if (span.size > size) {
                val remaining = FreeSpan(
                    offset = span.offset + size,
                    size = span.size - size,
                    timestamp = System.currentTimeMillis()
                )
                freeSpans.add(remaining)
            }
            
            span.offset
        }
    }

    suspend fun getTotalFreeSpace(): Long {
        return freeSpans.sumOf { it.size }
    }

    suspend fun getFreeSpanCount(): Int = freeSpans.size

    suspend fun clear() = mutex.withLock {
        freeSpans.clear()
    }

    private fun mergeFreeSpans() {
        val sorted = freeSpans.sortedBy { it.offset }
        freeSpans.clear()
        
        if (sorted.isEmpty()) return
        
        var current = sorted[0]
        
        for (i in 1 until sorted.size) {
            val next = sorted[i]
            
            if (current.offset + current.size == next.offset) {
                current = FreeSpan(
                    offset = current.offset,
                    size = current.size + next.size,
                    timestamp = System.currentTimeMillis()
                )
            } else {
                freeSpans.add(current)
                current = next
            }
        }
        
        freeSpans.add(current)
    }
}

data class FreeSpan(
    val offset: Long,
    val size: Long,
    val timestamp: Long
)