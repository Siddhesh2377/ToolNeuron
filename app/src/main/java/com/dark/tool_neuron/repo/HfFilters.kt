package com.dark.tool_neuron.repo

import androidx.compose.runtime.Immutable

enum class HfSort(val apiKey: String, val label: String) {
    TRENDING("trendingScore", "Trending"),
    DOWNLOADS("downloads", "Downloads"),
    LIKES("likes", "Likes"),
    RECENT_UPDATED("lastModified", "Recently updated"),
    RECENT_CREATED("createdAt", "Recently created"),
}

enum class GatedFilter(val label: String) {
    ANY("Any"),
    ONLY_OPEN("Open only"),
    ONLY_GATED("Gated only"),
}

@Immutable
data class HfFilters(
    val libraries: Set<String> = setOf("gguf"),
    val author: String = "",
    val gated: GatedFilter = GatedFilter.ANY,
    val paramsMinMillions: Long = 0L,
    val paramsMaxMillions: Long = 0L,
    val sort: HfSort = HfSort.TRENDING,
) {
    val activeCount: Int
        get() {
            var n = 0
            n += libraries.size
            if (author.isNotBlank()) n++
            if (gated != GatedFilter.ANY) n++
            if (paramsMinMillions > 0L || paramsMaxMillions > 0L) n++
            return n
        }
}
