package com.dark.tool_neuron.model

import com.squareup.moshi.JsonClass

data class HuggingFaceModel(
    val id: String,
    val modelId: String? = null,
    val tags: List<String>? = null,
    val siblings: List<HFSibling>? = null
)

@JsonClass(generateAdapter = true)
data class HFSibling(
    val rfilename: String
)
