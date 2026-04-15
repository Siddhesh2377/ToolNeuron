package com.memoryvault.core

import java.util.TreeSet

class FreeSpaceManager {
    private val freeSpans = TreeSet<FreeSpan>(compareBy { it.size })

    suspend fun getTotalFreeSpace(): Long {
        return freeSpans.sumOf { it.size }
    }
}

data class FreeSpan(
    val offset: Long,
    val size: Long,
    val timestamp: Long
)
