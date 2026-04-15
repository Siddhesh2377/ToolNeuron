package com.dark.tool_neuron.plugins

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.models.plugins.PluginInfo
import com.dark.tool_neuron.plugins.api.SuperPlugin
import com.dark.tool_neuron.plugins.services.WebScrapingSearchService
import com.dark.tool_neuron.plugins.services.WebScrapingService
import com.dark.gguf_lib.toolcalling.ToolCall
import com.dark.gguf_lib.toolcalling.ToolDefinitionBuilder
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

// ── Response Models ──

data class WebSearchPipelineResult(
    val query: String,
    val results: List<ScrapedSearchResult>,
    val totalResults: Int,
    val searchTimeMs: Long
)

data class ScrapedSearchResult(
    val title: String,
    val url: String,
    val snippet: String,
    val content: String
)

// ── Plugin ──

class WebSearchPlugin : SuperPlugin {

    private val searchService = WebScrapingSearchService()
    private val scrapingService = WebScrapingService()

    companion object {
        private const val TAG = "WebSearchPlugin"
        const val TOOL_WEB_SEARCH = "web_search"
        private const val SCRAPE_TIMEOUT_MS = 10_000L
        private const val MAX_SCRAPE_CHARS = 1500
    }

    override fun getPluginInfo(): PluginInfo {
        return PluginInfo(
            name = "Web Search",
            description = "Search the web with automatic content scraping from top results",
            author = "ToolNeuron",
            version = "2.0.0",
            toolDefinitionBuilder = listOf(
                ToolDefinitionBuilder(
                    TOOL_WEB_SEARCH,
                    "Search the web and automatically scrape content from top results. Returns search results with scraped page content."
                )
                    .stringParam("query", "The search query", required = true)
                    .numberParam("max_results", "Number of results to scrape (1-5, default 3)", required = false)
            )
        )
    }

    // ── Serialization ──

    override fun serializeResult(data: Any): String = when (data) {
        is WebSearchPipelineResult -> JSONObject().apply {
            put("query", data.query)
            put("totalResults", data.totalResults)
            put("searchTimeMs", data.searchTimeMs)
            val arr = JSONArray()
            data.results.forEach { r ->
                arr.put(JSONObject().apply {
                    put("title", r.title)
                    put("url", r.url)
                    put("snippet", r.snippet)
                    put("content", r.content)
                })
            }
            put("results", arr)
        }.toString()
        else -> data.toString()
    }

    // ── Execution ──

    override suspend fun executeTool(toolCall: ToolCall): Result<Any> {
        return try {
            when (toolCall.name) {
                TOOL_WEB_SEARCH -> executePipelinedSearch(toolCall)
                else -> Result.failure(IllegalArgumentException("Unknown tool: ${toolCall.name}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun executePipelinedSearch(toolCall: ToolCall): Result<Any> {
        val query = toolCall.getString("query")
        val maxResults = toolCall.getInt("max_results", 3).coerceIn(1, 5)
        val startTime = System.currentTimeMillis()

        Log.d(TAG, "Pipeline search: '$query' (max $maxResults results)")

        // Step 1: Search
        val searchResult = searchService.search(query, maxResults, safeSearch = true)
        if (searchResult.isFailure) {
            Log.w(TAG, "Search failed for query '$query': ${searchResult.exceptionOrNull()?.message}")
            return Result.failure(searchResult.exceptionOrNull()
                ?: Exception("Search failed"))
        }

        val searchResponse = searchResult.getOrThrow()
        val urls = searchResponse.results.take(maxResults)

        if (urls.isEmpty()) {
            Log.w(TAG, "Search returned 0 results for query '$query' — all endpoints may be blocked")
        } else {
            Log.d(TAG, "Search returned ${urls.size} results, scraping...")
        }

        // Step 2: Scrape top results in parallel with timeout
        val scrapedResults = coroutineScope {
            urls.map { result ->
                async {
                    try {
                        val scraped = withTimeoutOrNull(SCRAPE_TIMEOUT_MS) {
                            scrapingService.scrape(result.url, maxLength = MAX_SCRAPE_CHARS + 500)
                        }
                        ScrapedSearchResult(
                            title = result.title,
                            url = result.url,
                            snippet = result.snippet,
                            content = scraped?.getOrNull()?.content?.take(MAX_SCRAPE_CHARS) ?: ""
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Scrape failed for ${result.url}: ${e.message}")
                        ScrapedSearchResult(
                            title = result.title,
                            url = result.url,
                            snippet = result.snippet,
                            content = ""
                        )
                    }
                }
            }.awaitAll()
        }

        val elapsed = System.currentTimeMillis() - startTime
        Log.d(TAG, "Pipeline complete in ${elapsed}ms: ${scrapedResults.size} results")

        return Result.success(WebSearchPipelineResult(
            query = query,
            results = scrapedResults,
            totalResults = scrapedResults.size,
            searchTimeMs = elapsed
        ))
    }

    // ── UI ──

    @Composable
    override fun ToolCallUI() {
        // No standalone UI — results shown via CacheToolUI
    }

    @Composable
    override fun CacheToolUI(data: JSONObject) {
        if (data.has("query") && data.has("results")) {
            PipelineResultUI(data)
        } else {
            Text(
                text = data.toString(2),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp)
            )
        }
    }

    @Composable
    private fun PipelineResultUI(data: JSONObject) {
        val query = data.optString("query", "")
        val resultsArray = data.optJSONArray("results")
        val totalResults = data.optInt("totalResults", 0)
        val searchTimeMs = data.optLong("searchTimeMs", 0)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Header ──
            Text(
                text = "Search: \"$query\"",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "$totalResults results · ${searchTimeMs}ms",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ── Results ──
            if (resultsArray != null && resultsArray.length() > 0) {
                for (i in 0 until resultsArray.length()) {
                    val result = resultsArray.getJSONObject(i)
                    SearchResultCard(
                        title = result.optString("title", ""),
                        snippet = result.optString("snippet", ""),
                        url = result.optString("url", ""),
                        content = result.optString("content", ""),
                        position = i + 1
                    )

                    if (i < resultsArray.length() - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 2.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun SearchResultCard(
        title: String,
        snippet: String,
        url: String,
        content: String,
        position: Int
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "#$position",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = snippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Scraped content preview
                if (content.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ) {
                        Text(
                            text = content.take(300) + if (content.length > 300) "…" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(6.dp),
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
