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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.tool_neuron.models.plugins.DuckDuckGoSearchResponse
import com.dark.tool_neuron.models.plugins.PluginInfo
import com.dark.tool_neuron.models.plugins.ScrapedContent
import com.dark.tool_neuron.models.plugins.SearchResult
import com.dark.tool_neuron.plugins.api.SuperPlugin
import com.dark.tool_neuron.plugins.services.DuckDuckGoSearchService
import com.dark.tool_neuron.plugins.services.WebScrapingService
import com.dark.tool_neuron.plugins.ui.WebSearchPluginUI
import com.dark.tool_neuron.plugins.viewmodel.WebSearchViewModel
import com.dark.tool_neuron.ui.theme.rDp
import com.mp.ai_gguf.toolcalling.ToolCall
import com.mp.ai_gguf.toolcalling.ToolDefinitionBuilder
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.json.JSONObject

class WebSearchPlugin : SuperPlugin {

    private val searchService = DuckDuckGoSearchService()
    private val scrapingService = WebScrapingService()

    companion object {
        const val TOOL_DUCKDUCKGO_SEARCH = "duckduckgo_search"
        const val TOOL_WEB_SCRAPING = "web_scraping"
    }

    override fun getPluginInfo(): PluginInfo {
        return PluginInfo(
            name = "Web Search",
            description = "Search the web and scrape content from websites",
            author = "ToolNeuron",
            version = "1.0.0",
            toolDefinitionBuilder = listOf(
                // DuckDuckGo Search Tool
                ToolDefinitionBuilder(
                    TOOL_DUCKDUCKGO_SEARCH,
                    "Search the web using DuckDuckGo search engine"
                )
                    .stringParam("query", "The search query", required = true)
                    .numberParam("max_results", "Maximum number of results to return (1-10)", required = false)
                    .booleanParam("safe_search", "Enable safe search filtering", required = false),

                // Web Scraping Tool
                ToolDefinitionBuilder(
                    TOOL_WEB_SCRAPING,
                    "Scrape and extract content from a given URL"
                )
                    .stringParam("url", "The URL to scrape", required = true)
                    .stringParam("selector", "Optional CSS selector to extract specific content", required = false)
                    .numberParam("max_length", "Maximum content length in characters (default: 5000)", required = false)
            )
        )
    }

    override suspend fun executeTool(toolCall: ToolCall): Result<Any> {
        return try {
            when (toolCall.name) {
                TOOL_DUCKDUCKGO_SEARCH -> executeDuckDuckGoSearch(toolCall)
                TOOL_WEB_SCRAPING -> executeWebScraping(toolCall)
                else -> Result.failure(IllegalArgumentException("Unknown tool: ${toolCall.name}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun executeDuckDuckGoSearch(toolCall: ToolCall): Result<DuckDuckGoSearchResponse> {
        val query = toolCall.getString("query")
        val maxResults = toolCall.getInt("max_results", 5).coerceIn(5, 10)
        val safeSearch = toolCall.getBoolean("safe_search", true)

        // Execute actual DuckDuckGo search
        return searchService.search(query, maxResults, safeSearch)
    }

    private suspend fun executeWebScraping(toolCall: ToolCall): Result<ScrapedContent> {
        val url = toolCall.getString("url")
        val selector = toolCall.getString("selector", "").takeIf { it.isNotBlank() }
        val maxLength = toolCall.getInt("max_length", 5000)

        // Execute actual web scraping
        return scrapingService.scrape(url, selector, maxLength)
    }

    @Composable
    override fun ToolCallUI() {
        val viewModel: WebSearchViewModel = viewModel()
        WebSearchPluginUI(viewModel = viewModel)
    }

    @Composable
    override fun CacheToolUI(data: JSONObject) {
        // Parse the tool name from the data
        val toolName = data.optString("tool_name", "")

        when {
            toolName.isEmpty() -> {
                // Try to infer from data structure
                if (data.has("query") && data.has("results")) {
                    DuckDuckGoSearchResultUI(data)
                } else if (data.has("url") && data.has("content")) {
                    WebScrapingResultUI(data)
                } else {
                    // Fallback: display raw JSON
                    Text(
                        text = data.toString(2),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(rDp(8.dp))
                    )
                }
            }
            toolName == TOOL_DUCKDUCKGO_SEARCH -> {
                DuckDuckGoSearchResultUI(data)
            }
            toolName == TOOL_WEB_SCRAPING -> {
                WebScrapingResultUI(data)
            }
            else -> {
                Text(
                    text = "Unknown tool: $toolName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    @Composable
    private fun DuckDuckGoSearchResultUI(data: JSONObject) {
        val query = data.optString("query", "")
        val resultsArray = data.optJSONArray("results")
        val totalResults = data.optInt("totalResults", 0)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDp(8.dp)),
            verticalArrangement = Arrangement.spacedBy(rDp(8.dp))
        ) {
            // Header
            Text(
                text = "Search: \"$query\"",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Found $totalResults results",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Results
            if (resultsArray != null && resultsArray.length() > 0) {
                Log.d("WebSearchPlugin", "Results Array: $resultsArray")
                for (i in 0 until resultsArray.length()) {
                    val result = resultsArray.getJSONObject(i)
                    SearchResultItem(
                        title = result.optString("title", ""),
                        snippet = result.optString("snippet", ""),
                        url = result.optString("url", ""),
                        position = result.optInt("position", i + 1)
                    )

                    if (i < resultsArray.length() - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = rDp(4.dp)),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun SearchResultItem(
        title: String,
        snippet: String,
        url: String,
        position: Int
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(rDp(6.dp)),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        ) {
            Column(
                modifier = Modifier.padding(rDp(8.dp)),
                verticalArrangement = Arrangement.spacedBy(rDp(4.dp))
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
                        modifier = Modifier.weight(1f)
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
            }
        }
    }

    @Composable
    private fun WebScrapingResultUI(data: JSONObject) {
        val url = data.optString("url", "")
        val title = data.optString("title", "")
        val content = data.optString("content", "")
        val contentLength = data.optInt("contentLength", content.length)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDp(8.dp)),
            verticalArrangement = Arrangement.spacedBy(rDp(8.dp))
        ) {
            // Header
            Text(
                text = "Scraped Content",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Metadata
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(rDp(6.dp)),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
            ) {
                Column(
                    modifier = Modifier.padding(rDp(8.dp)),
                    verticalArrangement = Arrangement.spacedBy(rDp(4.dp))
                ) {
                    if (title.isNotEmpty()) {
                        Text(
                            text = "Title: $title",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = "URL: $url",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )

                    Text(
                        text = "Content Length: $contentLength characters",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Content
            if (content.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(rDp(6.dp)),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ) {
                    Text(
                        text = content.take(500) + if (content.length > 500) "..." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(rDp(8.dp))
                    )
                }
            }
        }
    }
}