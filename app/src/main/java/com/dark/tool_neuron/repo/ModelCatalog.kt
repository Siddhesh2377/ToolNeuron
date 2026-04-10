package com.dark.tool_neuron.repo

import android.content.Context
import com.dark.tool_neuron.model.HFRepository
import com.dark.tool_neuron.model.HuggingFaceModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelCatalog @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cacheFile = File(context.filesDir, "cache/model_catalog.json")
    private var memoryCache: List<HuggingFaceModel>? = null

    suspend fun getModels(
        repos: List<HFRepository>,
        forceRefresh: Boolean = false
    ): List<HuggingFaceModel> {
        if (!forceRefresh) {
            memoryCache?.let { return it }
            loadDiskCache()?.let { memoryCache = it; return it }
        }
        val models = repos.filter { it.isEnabled }.flatMap { repo ->
            try { fetchRepo(repo) } catch (_: Exception) { emptyList() }
        }
        memoryCache = models
        saveDiskCache(models)
        return models
    }

    fun clearCache() {
        memoryCache = null
        cacheFile.delete()
    }

    private suspend fun fetchRepo(repo: HFRepository): List<HuggingFaceModel> =
        withContext(Dispatchers.IO) {
            val meta = fetchJson("https://huggingface.co/api/models/${repo.repoPath}")
                ?: return@withContext emptyList()
            val tree = fetchJsonArray("https://huggingface.co/api/models/${repo.repoPath}/tree/main")
                ?: return@withContext emptyList()

            val author = meta.optString("author", repo.repoPath.substringBefore("/"))
            val downloads = meta.optInt("downloads", 0)
            val likes = meta.optInt("likes", 0)
            val gated = meta.opt("gated")
            val isGated = gated != null && gated != false && gated.toString() != "false"

            val models = mutableListOf<HuggingFaceModel>()
            for (i in 0 until tree.length()) {
                val file = tree.getJSONObject(i)
                val path = file.optString("path", "")
                if (!path.endsWith(".gguf", ignoreCase = true)) continue
                if (path.contains("mmproj", ignoreCase = true)) continue
                if (path.contains("projector", ignoreCase = true)) continue

                val size = file.optLong("size", 0)
                val quant = extractQuantization(path)

                models.add(HuggingFaceModel(
                    id = "${repo.id}__$path",
                    name = buildDisplayName(repo.name, quant, path),
                    fileName = path,
                    fileUri = "https://huggingface.co/${repo.repoPath}/resolve/main/$path",
                    approximateSize = if (size > 0) formatSize(size) else "Unknown",
                    sizeBytes = size,
                    repoId = repo.id,
                    quantization = quant,
                    tags = buildList {
                        add(author)
                        if (isGated) add("Gated")
                        if (downloads > 1000) add("${downloads / 1000}k+ downloads")
                    }
                ))
            }
            models.sortedBy { it.sizeBytes }
        }

    private fun fetchJson(urlStr: String): JSONObject? {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (conn.responseCode != 200) return null
            JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } catch (_: Exception) { null }
        finally { conn.disconnect() }
    }

    private fun fetchJsonArray(urlStr: String): JSONArray? {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (conn.responseCode != 200) return null
            JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
        } catch (_: Exception) { null }
        finally { conn.disconnect() }
    }

    private fun buildDisplayName(repoName: String, quant: String, fileName: String): String {
        if (quant.isNotBlank()) return "$repoName $quant"
        return "$repoName (${fileName.removeSuffix(".gguf")})"
    }

    private fun extractQuantization(fileName: String): String {
        val patterns = listOf(
            Regex("""[_-](Q\d[\w_]*)""", RegexOption.IGNORE_CASE),
            Regex("""[_-](IQ\d[\w_]*)""", RegexOption.IGNORE_CASE),
            Regex("""[_-]([Bb][Ff]16)"""),
            Regex("""[_-]([Ff]16|[Ff]32)"""),
        )
        for (p in patterns) {
            val match = p.find(fileName)
            if (match != null) return match.groupValues[1]
        }
        return ""
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }

    // ── Disk cache ──

    private fun loadDiskCache(): List<HuggingFaceModel>? {
        if (!cacheFile.exists()) return null
        if (System.currentTimeMillis() - cacheFile.lastModified() > CACHE_TTL_MS) return null
        return try {
            val arr = JSONArray(cacheFile.readText())
            (0 until arr.length()).map { arr.getJSONObject(it).toModel() }
        } catch (_: Exception) { null }
    }

    private fun saveDiskCache(models: List<HuggingFaceModel>) {
        try {
            cacheFile.parentFile?.mkdirs()
            val arr = JSONArray()
            models.forEach { arr.put(it.toJson()) }
            cacheFile.writeText(arr.toString())
        } catch (_: Exception) {}
    }

    private fun HuggingFaceModel.toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("fileName", fileName)
        put("fileUri", fileUri); put("approximateSize", approximateSize)
        put("sizeBytes", sizeBytes); put("repoId", repoId)
        put("quantization", quantization)
        put("tags", JSONArray(tags))
    }

    private fun JSONObject.toModel(): HuggingFaceModel = HuggingFaceModel(
        id = getString("id"), name = getString("name"),
        fileName = getString("fileName"), fileUri = getString("fileUri"),
        approximateSize = optString("approximateSize", "Unknown"),
        sizeBytes = optLong("sizeBytes"),
        repoId = optString("repoId"),
        quantization = optString("quantization"),
        tags = optJSONArray("tags")?.let { arr ->
            (0 until arr.length()).map { arr.getString(it) }
        } ?: emptyList()
    )

    companion object {
        private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L
    }
}
