package com.dark.tool_neuron.repo

import com.dark.tool_neuron.repo.hf.HfClient
import com.dark.tool_neuron.repo.hf.HfGated
import com.dark.tool_neuron.repo.hf.HfModelDetail
import com.dark.tool_neuron.repo.hf.HfModelSummary
import javax.inject.Inject
import javax.inject.Singleton

data class ExplorerRepo(
    val id: String,
    val author: String,
    val downloads: Long,
    val likes: Long,
    val gated: Boolean,
    val tags: List<String> = emptyList(),
    val pipelineTag: String? = null,
    val lastModified: String? = null,
)

data class RepoFile(
    val path: String,
    val sizeBytes: Long,
)

data class HfRepoDetail(
    val id: String,
    val author: String,
    val downloads: Long,
    val likes: Long,
    val gated: Boolean,
    val files: List<RepoFile>,
) {
    val ggufFiles: List<RepoFile>
        get() = files.filter { it.path.endsWith(".gguf", ignoreCase = true) }

    val totalGgufBytes: Long
        get() = ggufFiles.sumOf { it.sizeBytes }
}

@Singleton
class HuggingFaceExplorer @Inject constructor(
    private val client: HfClient,
) {

    suspend fun searchModels(
        query: String,
        filters: HfFilters = HfFilters(),
        limit: Int = 30,
    ): Result<List<ExplorerRepo>> = client.searchModels(query, filters, limit)
        .map { list -> list.map(HfModelSummary::toExplorerRepo) }

    suspend fun searchGgufRepos(query: String, limit: Int = 30): Result<List<ExplorerRepo>> =
        searchModels(query, HfFilters(libraries = setOf("gguf")), limit)

    suspend fun fetchRepoDetail(repoPath: String): Result<HfRepoDetail> =
        client.modelDetail(repoPath).map { it.toLegacyDetail() }
}

private fun HfModelSummary.toExplorerRepo(): ExplorerRepo = ExplorerRepo(
    id = id,
    author = author,
    downloads = downloads,
    likes = likes,
    gated = gated != HfGated.OPEN,
    tags = tags,
    pipelineTag = pipelineTag,
    lastModified = lastModified,
)

private fun HfModelDetail.toLegacyDetail(): HfRepoDetail = HfRepoDetail(
    id = summary.id,
    author = summary.author,
    downloads = summary.downloads,
    likes = summary.likes,
    gated = summary.gated != HfGated.OPEN,
    files = files.map { RepoFile(path = it.path, sizeBytes = it.sizeBytes) },
)
