package com.dark.tool_neuron.repo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

data class ExplorerRepo(
    val id: String,
    val author: String,
    val downloads: Long,
    val likes: Long,
    val gated: Boolean,
)

@Singleton
class HuggingFaceExplorer @Inject constructor() {

    suspend fun searchGgufRepos(query: String, limit: Int = 20): Result<List<ExplorerRepo>> =
        withContext(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(query.trim(), "UTF-8")
                val url = "https://huggingface.co/api/models?filter=gguf&search=$encoded&sort=downloads&direction=-1&limit=$limit"
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000; readTimeout = 15_000
                    setRequestProperty("Accept", "application/json")
                }
                val body = try {
                    if (conn.responseCode != 200)
                        return@withContext Result.failure(Exception("Search failed (${conn.responseCode})"))
                    conn.inputStream.bufferedReader().use { it.readText() }
                } finally { conn.disconnect() }

                val arr = JSONArray(body)
                val repos = (0 until arr.length()).mapNotNull { i ->
                    val obj = arr.getJSONObject(i)
                    val id = obj.optString("id", "")
                    if (id.isBlank() || !id.contains("/")) return@mapNotNull null
                    ExplorerRepo(
                        id = id,
                        author = obj.optString("author", id.substringBefore("/")),
                        downloads = obj.optLong("downloads", 0),
                        likes = obj.optLong("likes", 0),
                        gated = obj.opt("gated")?.let { it != false && it.toString() != "false" } ?: false,
                    )
                }.distinctBy { it.id }

                Result.success(repos)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
