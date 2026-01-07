package com.memoryvault

data class ValidationReport(
    val headerValid: Boolean,
    val totalBlocks: Int,
    val validBlocks: Int,
    val corruptedBlocks: List<CorruptedBlock>,
    val indexValid: Boolean,
    val canRecover: Boolean,
    val recommendations: List<String>
)

data class CorruptedBlock(
    val offset: Long,
    val error: String
)