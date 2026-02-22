package com.dark.tool_neuron.plugins.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.models.plugins.DuckDuckGoSearchResponse
import com.dark.tool_neuron.models.plugins.ScrapedContent
import com.dark.tool_neuron.models.plugins.ToolState
import com.dark.tool_neuron.plugins.services.DuckDuckGoSearchService
import com.dark.tool_neuron.plugins.services.WebScrapingService
import kotlinx.coroutines.launch

/**
 * ViewModel for managing Web Search Plugin state
 */
class WebSearchViewModel : ViewModel() {

    private val searchService = DuckDuckGoSearchService()
    private val scrapingService = WebScrapingService()

    // DuckDuckGo Search State
    private val _duckDuckGoState = mutableStateOf<ToolState>(ToolState.Idle)
    val duckDuckGoState: State<ToolState> = _duckDuckGoState

    private val _searchResponse = mutableStateOf<DuckDuckGoSearchResponse?>(null)
    val searchResponse: State<DuckDuckGoSearchResponse?> = _searchResponse

    // Web Scraping State
    private val _webScrapingState = mutableStateOf<ToolState>(ToolState.Idle)
    val webScrapingState: State<ToolState> = _webScrapingState

    private val _scrapedContent = mutableStateOf<ScrapedContent?>(null)
    val scrapedContent: State<ScrapedContent?> = _scrapedContent

    /**
     * Execute DuckDuckGo search
     */
    fun executeDuckDuckGoSearch(
        query: String,
        maxResults: Int = 5,
        safeSearch: Boolean = true
    ) {
        viewModelScope.launch {
            try {
                _duckDuckGoState.value = ToolState.InProgress("Searching DuckDuckGo for \"$query\"...")

                // Call the actual search service
                val result = searchService.search(
                    query = query,
                    maxResults = maxResults.coerceIn(1, 10),
                    safeSearch = safeSearch
                )

                result.onSuccess { response ->
                    _searchResponse.value = response
                    _duckDuckGoState.value = ToolState.Success(
                        data = response,
                        message = "Found ${response.totalResults} results in ${response.searchTime}ms"
                    )
                }.onFailure { error ->
                    _duckDuckGoState.value = ToolState.Error(
                        error = error,
                        message = "Search failed: ${error.message}"
                    )
                }
            } catch (e: Exception) {
                _duckDuckGoState.value = ToolState.Error(
                    error = e,
                    message = "Unexpected error: ${e.message}"
                )
            }
        }
    }

    /**
     * Execute web scraping
     */
    fun executeWebScraping(
        url: String,
        selector: String? = null,
        maxLength: Int = 5000
    ) {
        viewModelScope.launch {
            try {
                // Validate URL
                if (url.isBlank()) {
                    _webScrapingState.value = ToolState.Error(
                        error = IllegalArgumentException("URL cannot be empty"),
                        message = "Please provide a valid URL"
                    )
                    return@launch
                }

                _webScrapingState.value = ToolState.InProgress("Scraping content from $url...")

                // Call the actual scraping service
                val result = scrapingService.scrape(
                    url = url,
                    selector = selector,
                    maxLength = maxLength.coerceIn(100, 50000)
                )

                result.onSuccess { content ->
                    _scrapedContent.value = content
                    _webScrapingState.value = ToolState.Success(
                        data = content,
                        message = "Successfully scraped ${content.contentLength} characters in ${content.fetchTime}ms"
                    )
                }.onFailure { error ->
                    _webScrapingState.value = ToolState.Error(
                        error = error,
                        message = "Scraping failed: ${error.message}"
                    )
                }
            } catch (e: Exception) {
                _webScrapingState.value = ToolState.Error(
                    error = e,
                    message = "Unexpected error: ${e.message}"
                )
            }
        }
    }

    /**
     * Reset DuckDuckGo search state
     */
    fun resetDuckDuckGoSearch() {
        _duckDuckGoState.value = ToolState.Idle
        _searchResponse.value = null
    }

    /**
     * Reset web scraping state
     */
    fun resetWebScraping() {
        _webScrapingState.value = ToolState.Idle
        _scrapedContent.value = null
    }

    /**
     * Reset all plugin states
     */
    fun resetAll() {
        resetDuckDuckGoSearch()
        resetWebScraping()
    }

    /**
     * Cancel all ongoing operations
     */
    override fun onCleared() {
        super.onCleared()
        resetAll()
    }
}
