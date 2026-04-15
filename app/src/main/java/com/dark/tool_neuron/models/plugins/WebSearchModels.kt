package com.dark.tool_neuron.models.plugins

/**
 * Data model for DuckDuckGo search results
 */
data class SearchResult(
    val title: String,
    val snippet: String,
    val url: String,
    val position: Int
)

/**
 * Data model for DuckDuckGo search response
 */
data class DuckDuckGoSearchResponse(
    val query: String,
    val results: List<SearchResult>,
    val totalResults: Int,
    val searchTime: Long
)

/**
 * Data model for web scraping content
 */
data class ScrapedContent(
    val url: String,
    val title: String,
    val content: String,
    val contentLength: Int,
    val fetchTime: Long,
    val metadata: Map<String, String> = emptyMap()
)
