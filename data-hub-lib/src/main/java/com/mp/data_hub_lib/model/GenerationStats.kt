package com.mp.data_hub_lib.model

data class GenerationStats(
    val tokenCount: Int,      // Not LLM tokens here, but number of docs considered
    val totalTime: Long,      // Time taken for search
    val tokensPerSecond: Float
)