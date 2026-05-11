package com.dark.tool_neuron.repo

data class RagDebugHit(
    val docId: String,
    val chunkIndex: Int,
    val score: Float,
    val text: String,
)

data class RagDebugResult(
    val query: String,
    val isReady: Boolean,
    val activeChatId: String?,
    val dense: List<RagDebugHit>,
    val bm25: List<RagDebugHit>,
    val fused: List<RagDebugHit>,
    val contextBlock: String,
    val approxContextTokens: Int,
    val engineInfo: String,
)
