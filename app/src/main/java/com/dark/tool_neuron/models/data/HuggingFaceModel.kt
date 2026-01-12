package com.dark.tool_neuron.models.data


data class HuggingFaceModel(
    val id: String,
    val name: String,
    val description: String,
    val fileUri: String,
    val approximateSize: String,
    val modelType: ModelType,
    val isZip: Boolean,
    val chipsetSuffix: String? = null,
    val runOnCpu: Boolean = false,
    val textEmbeddingSize: Int = 768
)