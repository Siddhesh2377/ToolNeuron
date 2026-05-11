package com.dark.tool_neuron.model

data class Citation(
    val sourceId: String,
    val docId: String,
    val chunkIndex: Int,
    val score: Float,
    val name: String,
    val mimeType: String,
    val snippet: String,
    val cited: Boolean,
)
