package com.dark.tool_neuron.repo.hf

import com.dark.tool_neuron.data.AppPreferences
import com.dark.tool_neuron.repo.HfApiError
import com.dark.tool_neuron.repo.HfFilters
import com.dark.tool_neuron.repo.HuggingFaceApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private inline fun <T> rethrowingCancel(block: () -> T): Result<T> =
    runCatching(block).onFailure { if (it is CancellationException) throw it }

@Singleton
class HfClient @Inject constructor(
    private val api: HuggingFaceApi,
    private val prefs: AppPreferences,
) {

    @Volatile private var cachedTags: HfTagsCatalog? = null
    private val tagsLock = Mutex()

    suspend fun searchModels(
        query: String,
        filters: HfFilters,
        limit: Int = 30,
    ): Result<List<HfModelSummary>> = withContext(Dispatchers.IO) {
        rethrowingCancel {
            val url = api.searchUrl(query, filters, limit)
            android.util.Log.i("HfClient", "searchUrl=$url")
            val arr = api.fetchJsonArray(url).getOrThrow()
            android.util.Log.i("HfClient", "raw array length=${arr.length()}")
            val parsed = (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i)
                if (obj == null) {
                    android.util.Log.w("HfClient", "row $i is not a JSONObject")
                    return@mapNotNull null
                }
                val summary = HfJsonParse.parseSummary(obj)
                if (summary == null) {
                    android.util.Log.w("HfClient", "row $i parseSummary returned null; id=${obj.optString("id", "?")}")
                }
                summary
            }.distinctBy { it.id }
            android.util.Log.i("HfClient", "parsed count=${parsed.size}")
            parsed
        }
    }

    suspend fun quickSearch(query: String, limit: Int = 12): Result<List<HfQuickResult>> =
        withContext(Dispatchers.IO) {
            rethrowingCancel {
                if (query.isBlank()) return@rethrowingCancel emptyList()
                val arr = api.fetchJsonArray(api.quickSearchUrl(query, "model", limit)).getOrThrow()
                (0 until arr.length()).mapNotNull { i ->
                    arr.optJSONObject(i)?.let { HfJsonParse.parseQuick(it) }
                }
            }
        }

    suspend fun trending(limit: Int = 12): Result<List<HfTrendingItem>> =
        withContext(Dispatchers.IO) {
            rethrowingCancel {
                val obj = api.fetchJson(api.trendingUrl("model", limit)).getOrThrow()
                val arr = obj.optJSONArray("recentlyTrending") ?: return@rethrowingCancel emptyList()
                (0 until arr.length()).mapNotNull { i ->
                    arr.optJSONObject(i)?.let { HfJsonParse.parseTrendingItem(it) }
                }
            }
        }

    suspend fun modelDetail(repoPath: String): Result<HfModelDetail> =
        withContext(Dispatchers.IO) {
            rethrowingCancel {
                val obj = api.fetchJson(api.modelInfoUrl(repoPath)).getOrThrow()
                val detail = HfJsonParse.parseDetail(obj)
                    ?: throw HfApiError.Parse("missing repo id")
                if (detail.files.isEmpty()) {
                    val tree = api.fetchJsonArray(api.modelTreeUrl(repoPath)).getOrNull()
                    detail.copy(files = HfJsonParse.parseSiblings(tree))
                } else detail
            }
        }

    suspend fun readme(repoPath: String): Result<String> = withContext(Dispatchers.IO) {
        api.fetchRaw(api.rawFileUrl(repoPath, "README.md"))
    }

    suspend fun tagsCatalog(forceRefresh: Boolean = false): Result<HfTagsCatalog> =
        withContext(Dispatchers.IO) {
            tagsLock.withLock {
                if (!forceRefresh) {
                    cachedTags?.let { return@withLock Result.success(it) }
                    loadCachedTags()?.let {
                        cachedTags = it
                        return@withLock Result.success(it)
                    }
                }
                rethrowingCancel {
                    val obj = api.fetchJson(api.tagsByTypeUrl()).getOrThrow()
                    val parsed = HfJsonParse.parseTagsCatalog(obj)
                    cachedTags = parsed
                    persistTags(obj, parsed)
                    parsed
                }
            }
        }

    private fun loadCachedTags(): HfTagsCatalog? {
        val raw = prefs.hfTagsCatalogJson.takeIf { it.isNotBlank() } ?: return null
        val savedAt = prefs.hfTagsCatalogSavedAt
        if (savedAt > 0L && System.currentTimeMillis() - savedAt > TAGS_TTL_MS) return null
        return runCatching { HfJsonParse.parseTagsCatalog(JSONObject(raw)) }
            .getOrNull()?.takeIf { !it.isEmpty }
    }

    private fun persistTags(raw: JSONObject, parsed: HfTagsCatalog) {
        if (parsed.isEmpty) return
        prefs.hfTagsCatalogJson = raw.toString()
        prefs.hfTagsCatalogSavedAt = System.currentTimeMillis()
    }

    companion object {
        private const val TAGS_TTL_MS = 24L * 60L * 60L * 1000L
    }
}
