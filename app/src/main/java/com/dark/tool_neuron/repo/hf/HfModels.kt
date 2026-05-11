package com.dark.tool_neuron.repo.hf

import androidx.compose.runtime.Immutable

enum class HfGated { OPEN, GATED, AUTO }

@Immutable
data class HfModelSummary(
    val id: String,
    val author: String,
    val downloads: Long,
    val likes: Long,
    val gated: HfGated,
    val tags: List<String>,
    val pipelineTag: String?,
    val libraryName: String?,
    val lastModified: String?,
    val createdAt: String?,
) {
    val idLowercase: String = id.lowercase()
    val nameOnly: String = id.substringAfter('/')
}

data class HfSibling(
    val path: String,
    val sizeBytes: Long,
    val sha256: String? = null,
)

data class HfGgufMeta(
    val architecture: String?,
    val contextLength: Long?,
    val totalBytes: Long?,
    val bosToken: String?,
    val eosToken: String?,
)

data class HfCardData(
    val license: String?,
    val baseModel: List<String>,
    val languages: List<String>,
    val pipelineTag: String?,
    val tags: List<String>,
    val gatedPrompt: String?,
)

data class HfModelDetail(
    val summary: HfModelSummary,
    val files: List<HfSibling>,
    val ggufMeta: HfGgufMeta?,
    val cardData: HfCardData?,
    val spaces: List<String>,
) {
    val ggufFiles: List<HfSibling>
        get() = files.filter { it.path.endsWith(".gguf", ignoreCase = true) }

    val totalGgufBytes: Long
        get() = ggufFiles.sumOf { it.sizeBytes }

    val mmprojFiles: List<HfSibling>
        get() = files.filter { it.path.contains("mmproj", ignoreCase = true) }
}

data class HfQuickResult(
    val id: String,
    val author: String,
    val type: String,
)

@Immutable
data class HfTrendingItem(
    val id: String,
    val author: String,
    val downloads: Long,
    val likes: Long,
    val pipelineTag: String?,
    val numParameters: Long?,
    val gated: HfGated,
    val lastModified: String?,
)

data class HfTagEntry(
    val id: String,
    val label: String,
    val type: String,
)

data class HfTagsCatalog(
    val pipelineTags: List<HfTagEntry>,
    val libraries: List<HfTagEntry>,
    val licenses: List<HfTagEntry>,
    val languages: List<HfTagEntry>,
    val regions: List<HfTagEntry>,
    val other: List<HfTagEntry>,
) {
    val isEmpty: Boolean
        get() = pipelineTags.isEmpty() && libraries.isEmpty() &&
            licenses.isEmpty() && languages.isEmpty() &&
            regions.isEmpty() && other.isEmpty()

    companion object {
        val EMPTY = HfTagsCatalog(
            pipelineTags = emptyList(),
            libraries = emptyList(),
            licenses = emptyList(),
            languages = emptyList(),
            regions = emptyList(),
            other = emptyList(),
        )
    }
}
