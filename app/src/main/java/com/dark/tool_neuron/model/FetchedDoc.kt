package com.dark.tool_neuron.model

data class FetchedDoc(
    val url: String,
    val title: String,
    val extractedText: String,
    val byteCount: Long,
    val iteration: Int,
    val ok: Boolean = true,
    val error: String? = null,
)
