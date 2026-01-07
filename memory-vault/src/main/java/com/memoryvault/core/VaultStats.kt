package com.memoryvault.core

data class VaultStats(
    val totalItems: Int,
    val totalSizeBytes: Long,
    val wastedSpaceBytes: Long,
    val messageCount: Int,
    val fileCount: Int,
    val embeddingCount: Int,
    val customDataCount: Int,
    val oldestItem: Long,
    val newestItem: Long,
    val indexSizeBytes: Long,
    val compressionRatio: Float
)