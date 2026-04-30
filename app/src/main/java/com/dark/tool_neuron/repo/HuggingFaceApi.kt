package com.dark.tool_neuron.repo

import android.content.Context
import com.dark.networking.WebNative
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HuggingFaceApi @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    fun modelInfoUrl(repoPath: String): String =
        "$BASE/api/models/$repoPath"

    fun modelTreeUrl(repoPath: String, recursive: Boolean = true): String =
        "$BASE/api/models/$repoPath/tree/main" + if (recursive) "?recursive=true" else ""

    fun resolveFileUrl(repoPath: String, filePath: String): String =
        "$BASE/$repoPath/resolve/main/$filePath"

    fun rawFileUrl(repoPath: String, filePath: String, branch: String = "main"): String =
        "$BASE/$repoPath/raw/$branch/$filePath"

    fun searchUrl(query: String, filters: HfFilters, limit: Int): String {
        val params = mutableListOf<String>()
        if (query.isNotBlank()) params += "search=${enc(query.trim())}"
        if (filters.author.isNotBlank()) params += "author=${enc(filters.author.trim())}"

        filters.libraries.forEach { params += "filter=${enc(it)}" }

        when (filters.gated) {
            GatedFilter.ONLY_GATED -> params += "gated=true"
            GatedFilter.ONLY_OPEN -> params += "gated=false"
            GatedFilter.ANY -> {}
        }

        val pmin = filters.paramsMinMillions
        val pmax = filters.paramsMaxMillions
        if (pmin > 0L || pmax > 0L) {
            val parts = mutableListOf<String>()
            if (pmin > 0L) parts += "min:${formatParams(pmin)}"
            if (pmax > 0L) parts += "max:${formatParams(pmax)}"
            params += "num_parameters=${enc(parts.joinToString(","))}"
        }

        params += "sort=${filters.sort.apiKey}"
        params += "limit=$limit"

        return "$BASE/api/models?${params.joinToString("&")}"
    }

    fun quickSearchUrl(query: String, type: String = "model", limit: Int = 20): String =
        "$BASE/api/quicksearch?q=${enc(query)}&type=${enc(type)}&limit=$limit"

    fun trendingUrl(type: String = "model", limit: Int = 20): String =
        "$BASE/api/trending?type=${enc(type)}&limit=$limit"

    fun tagsByTypeUrl(): String = "$BASE/api/models-tags-by-type"

    suspend fun fetchJson(url: String): Result<JSONObject> = runFetch(url).mapCatching { resp ->
        if (!resp.isSuccess) throw HfApiError.fromResponse(resp, url)
        runCatching { JSONObject(resp.body) }
            .getOrElse { throw HfApiError.Parse(it.message ?: "json object parse failed") }
    }

    suspend fun fetchJsonArray(url: String): Result<JSONArray> = runFetch(url).mapCatching { resp ->
        android.util.Log.i("HfApi", "fetchJsonArray status=${resp.status} bodyLen=${resp.body.length} err=${resp.error ?: "none"}")
        if (!resp.isSuccess) throw HfApiError.fromResponse(resp, url)
        runCatching { JSONArray(resp.body) }
            .getOrElse {
                android.util.Log.e("HfApi", "JSONArray parse failed: ${it.message}; bodyHead=${resp.body.take(200)}")
                throw HfApiError.Parse(it.message ?: "json array parse failed")
            }
    }

    suspend fun fetchRaw(url: String): Result<String> = runFetch(url).mapCatching { resp ->
        if (!resp.isSuccess) throw HfApiError.fromResponse(resp, url)
        resp.body
    }

    suspend fun probe(url: String): Result<Int> = runFetch(url).map { it.status }

    private suspend fun runFetch(url: String): Result<com.dark.networking.WebResponse> {
        WebNative.ensureReady(context)
        return WebNative.fetch(
            url = url,
            timeoutMs = TIMEOUT_MS,
            headers = JSON_HEADERS,
        )
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun formatParams(millions: Long): String = when {
        millions >= 1_000L -> "${millions / 1_000L}B"
        else -> "${millions}M"
    }

    companion object {
        const val BASE = "https://huggingface.co"
        private const val TIMEOUT_MS = 15_000

        private val JSON_HEADERS = mapOf(
            "Accept" to "application/json",
            "Accept-Encoding" to "gzip",
        )
    }
}
