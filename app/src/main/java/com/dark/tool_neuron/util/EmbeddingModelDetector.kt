package com.dark.tool_neuron.util

private val EMBEDDING_PIPELINE_TAGS = setOf("feature-extraction", "sentence-similarity")

private val EMBEDDING_NAME_REGEX = Regex(
    "embed|(^|[/_.\\-])(bge|gte|e5|minilm|mxbai|arctic)([/_.\\-]|$)",
    RegexOption.IGNORE_CASE,
)

fun looksLikeEmbeddingModel(repoPath: String, fileName: String, pipelineTag: String = ""): Boolean {
    if (VlmPaths.isMmprojFileName(fileName)) return false
    if (pipelineTag.lowercase() in EMBEDDING_PIPELINE_TAGS) return true
    return EMBEDDING_NAME_REGEX.containsMatchIn("$repoPath/$fileName")
}
