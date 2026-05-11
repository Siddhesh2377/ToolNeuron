package com.dark.tool_neuron.repo

data class RagChunk(
    val docId: String,
    val sourceId: String,
    val chunkIndex: Int,
    val score: Float,
    val text: String,
    val name: String,
    val mimeType: String,
)

data class RagAugmentation(
    val augmentedPrompt: String,
    val chunks: List<RagChunk>,
) {
    val didAugment: Boolean get() = chunks.isNotEmpty()

    companion object {
        val NONE = RagAugmentation(augmentedPrompt = "", chunks = emptyList())
    }
}
