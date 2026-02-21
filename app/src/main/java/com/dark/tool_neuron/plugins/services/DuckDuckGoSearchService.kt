package com.dark.tool_neuron.plugins.services

import com.dark.tool_neuron.models.plugins.DuckDuckGoSearchResponse
import com.dark.tool_neuron.models.plugins.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Improved DuckDuckGo web search service with better handling for simple queries
 * Key improvements:
 * - Multiple API endpoints (HTML, Lite, JSON)
 * - Better CSS selectors for current DuckDuckGo structure
 * - Improved error handling and logging
 * - Better rate limiting and delays
 * - Query validation and sanitization
 */
class DuckDuckGoSearchService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    // Updated user agents (more current and varied)
    private val userAgents = listOf(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1 Safari/605.1.15",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0"
    )

    companion object {
        const val MAX_RETRIES = 3
        const val INITIAL_RETRY_DELAY = 1500L // Increased delay to avoid rate limiting
        const val MIN_QUERY_LENGTH = 1
        const val MAX_QUERY_LENGTH = 500
    }

    /**
     * Main search function with improved error handling and multiple endpoint fallback
     */
    suspend fun search(
        query: String,
        maxResults: Int = 5,
        safeSearch: Boolean = true,
        region: String? = null,
        timeRange: String? = null
    ): Result<DuckDuckGoSearchResponse> = withContext(Dispatchers.IO) {
        // Validate and sanitize query
        val sanitizedQuery = sanitizeQuery(query)
        if (sanitizedQuery.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Query is empty or invalid after sanitization")
            )
        }

        if (sanitizedQuery.length < MIN_QUERY_LENGTH) {
            return@withContext Result.failure(
                IllegalArgumentException("Query too short: minimum $MIN_QUERY_LENGTH characters")
            )
        }

        if (sanitizedQuery.length > MAX_QUERY_LENGTH) {
            return@withContext Result.failure(
                IllegalArgumentException("Query too long: maximum $MAX_QUERY_LENGTH characters")
            )
        }

        val validatedMaxResults = maxResults.coerceIn(1, 25)

        // Try multiple endpoints in order of preference
        val endpoints = listOf<suspend () -> Result<DuckDuckGoSearchResponse>>(
            { searchHtmlEndpoint(sanitizedQuery, validatedMaxResults, safeSearch, region, timeRange) },
            { searchLiteEndpoint(sanitizedQuery, validatedMaxResults, safeSearch, region, timeRange) },
            { searchFallbackEndpoint(sanitizedQuery, validatedMaxResults) }
        )

        var lastException: Exception? = null

        for (endpoint in endpoints) {
            try {
                val result = endpoint() // Now it knows endpoint is suspend
                if (result.isSuccess) {
                    return@withContext result
                } else {
                    lastException = result.exceptionOrNull() as? Exception
                }
            } catch (e: Exception) {
                lastException = e
            }
            // Delay between endpoint attempts
            delay(500)
        }

        Result.failure(
            lastException ?: IOException("All search endpoints failed for query: $sanitizedQuery")
        )
    }

    /**
     * Sanitize query to prevent encoding issues
     */
    private fun sanitizeQuery(query: String): String {
        return query
            .trim()
            .replace(Regex("\\s+"), " ") // Normalize whitespace
            .replace(Regex("[\\x00-\\x1F\\x7F]"), "") // Remove control characters
    }

    /**
     * Primary search using HTML endpoint with improved selectors
     */
    private suspend fun searchHtmlEndpoint(
        query: String,
        maxResults: Int,
        safeSearch: Boolean,
        region: String?,
        timeRange: String?
    ): Result<DuckDuckGoSearchResponse> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null

        repeat(MAX_RETRIES) { attempt ->
            try {
                val startTime = System.currentTimeMillis()
                val encodedQuery = URLEncoder.encode(query, "UTF-8")

                // Build URL with proper parameters
                val url = buildString {
                    append("https://html.duckduckgo.com/html/?q=")
                    append(encodedQuery)
                    if (safeSearch) append("&kp=1") else append("&kp=-2")
                    region?.let { append("&kl=$it") }
                    timeRange?.let { append("&df=$it") }
                    append("&ia=web")
                }

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", userAgents.random())
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("DNT", "1")
                    .header("Connection", "keep-alive")
                    .header("Upgrade-Insecure-Requests", "1")
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "none")
                    .header("Cache-Control", "max-age=0")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}: ${response.message}")
                    }

                    val html = response.body.string()

                    // Check if we got blocked or captcha
                    if (html.contains("detected unusual traffic") ||
                        html.contains("captcha") ||
                        html.length < 500) {
                        throw IOException("Possible rate limiting or blocking detected")
                    }

                    val results = parseHtmlResults(html, maxResults)
                    val searchTime = System.currentTimeMillis() - startTime

                    if (results.isEmpty()) {
                        throw IOException("No results parsed from HTML")
                    }

                    return@withContext Result.success(
                        DuckDuckGoSearchResponse(
                            query = query,
                            results = results,
                            totalResults = results.size,
                            searchTime = searchTime
                        )
                    )
                }
            } catch (e: Exception) {
                lastException = e
                if (attempt < MAX_RETRIES - 1) {
                    val delayTime = INITIAL_RETRY_DELAY * (1 shl attempt) + Random.nextLong(0, 1000)
                    delay(delayTime)
                }
            }
        }

        Result.failure(lastException ?: IOException("HTML endpoint failed"))
    }

    /**
     * Fallback search using Lite endpoint (simpler HTML)
     */
    private suspend fun searchLiteEndpoint(
        query: String,
        maxResults: Int,
        safeSearch: Boolean,
        region: String?,
        timeRange: String?
    ): Result<DuckDuckGoSearchResponse> = withContext(Dispatchers.IO) {
        try {
            val startTime = System.currentTimeMillis()
            val encodedQuery = URLEncoder.encode(query, "UTF-8")

            val url = buildString {
                append("https://lite.duckduckgo.com/lite/?q=")
                append(encodedQuery)
                if (safeSearch) append("&kp=1")
                region?.let { append("&kl=$it") }
                timeRange?.let { append("&df=$it") }
            }

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgents.random())
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}")
                }

                val html = response.body.string()
                val results = parseLiteResults(html, maxResults)
                val searchTime = System.currentTimeMillis() - startTime

                if (results.isEmpty()) {
                    throw IOException("No results from Lite endpoint")
                }

                return@withContext Result.success(
                    DuckDuckGoSearchResponse(
                        query = query,
                        results = results,
                        totalResults = results.size,
                        searchTime = searchTime
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Ultimate fallback using direct API (limited results but reliable)
     */
    private suspend fun searchFallbackEndpoint(
        query: String,
        maxResults: Int
    ): Result<DuckDuckGoSearchResponse> = withContext(Dispatchers.IO) {
        try {
            val startTime = System.currentTimeMillis()
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://api.duckduckgo.com/?q=$encodedQuery&format=json&no_html=1"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgents.random())
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}")
                }

                val jsonString = response.body.string()
                val results = parseJsonResults(jsonString, query, maxResults)
                val searchTime = System.currentTimeMillis() - startTime

                return@withContext Result.success(
                    DuckDuckGoSearchResponse(
                        query = query,
                        results = results,
                        totalResults = results.size,
                        searchTime = searchTime
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Improved HTML parsing with updated selectors for current DuckDuckGo structure
     */
    private fun parseHtmlResults(html: String, maxResults: Int): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val seenUrls = mutableSetOf<String>()

        try {
            val doc: Document = Jsoup.parse(html)

            // Updated selectors for current DuckDuckGo HTML structure
            val selectors = listOf(
                ".result",           // Standard result class
                ".web-result",       // Web result class
                ".results_links",    // Legacy class
                "div[class*='result']", // Any div with 'result' in class
                ".links_main"        // Main links container
            )

            for (selector in selectors) {
                val elements = doc.select(selector)

                for (element in elements) {
                    if (results.size >= maxResults) break

                    try {
                        // Multiple strategies to find the link
                        val linkElement = element.selectFirst(
                            "a.result__a, a.result__url, h2 a, .result__title a, a[class*='result']"
                        ) ?: element.selectFirst("a[href^='http']") ?: continue

                        val title = linkElement.text().trim()
                        if (title.isBlank() || title.length < 3) continue

                        // Extract URL
                        var href = linkElement.attr("href")

                        // Handle DuckDuckGo redirects
                        if (href.startsWith("//duckduckgo.com/l/?") || href.startsWith("/l/?")) {
                            href = extractRedirectUrl(href) ?: continue
                        }

                        // Validate URL
                        if (!href.startsWith("http")) continue
                        if (seenUrls.contains(href)) continue
                        seenUrls.add(href)

                        // Extract snippet
                        val snippet = element.selectFirst(
                            ".result__snippet, .result__description, .snippet, a.result__snippet"
                        )?.text()?.trim() ?: ""

                        results.add(
                            SearchResult(
                                title = cleanText(title),
                                snippet = cleanText(snippet),
                                url = href,
                                position = results.size + 1
                            )
                        )
                    } catch (e: Exception) {
                        continue
                    }
                }

                if (results.size >= maxResults) break
            }

            // If still no results, try finding any http links
            if (results.isEmpty()) {
                val links = doc.select("a[href^='http']")
                    .filter { !it.attr("href").contains("duckduckgo.com") }
                    .filter { it.text().trim().length > 5 }

                for (link in links) {
                    if (results.size >= maxResults) break

                    val href = link.attr("href")
                    if (seenUrls.contains(href)) continue
                    seenUrls.add(href)

                    val title = link.text().trim()
                    val snippet = link.parent()?.text()?.take(150) ?: ""

                    results.add(
                        SearchResult(
                            title = cleanText(title),
                            snippet = cleanText(snippet),
                            url = href,
                            position = results.size + 1
                        )
                    )
                }
            }

        } catch (e: Exception) {
            // Log error but don't fail completely
            println("Error parsing HTML: ${e.message}")
        }

        return results
    }

    /**
     * Parse Lite endpoint results (simpler HTML structure)
     */
    private fun parseLiteResults(html: String, maxResults: Int): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val seenUrls = mutableSetOf<String>()

        try {
            val doc = Jsoup.parse(html)

            // Lite uses table structure
            val rows = doc.select("tr")

            for (row in rows) {
                if (results.size >= maxResults) break

                val links = row.select("a.result-link")
                if (links.isEmpty()) continue

                for (link in links) {
                    if (results.size >= maxResults) break

                    val href = link.attr("href")
                    if (!href.startsWith("http")) continue
                    if (seenUrls.contains(href)) continue
                    seenUrls.add(href)

                    val title = link.text().trim()
                    if (title.isBlank()) continue

                    val snippet = row.select("td.result-snippet").text().trim()

                    results.add(
                        SearchResult(
                            title = cleanText(title),
                            snippet = cleanText(snippet),
                            url = href,
                            position = results.size + 1
                        )
                    )
                }
            }
        } catch (e: Exception) {
            println("Error parsing Lite results: ${e.message}")
        }

        return results
    }

    /**
     * Parse JSON API results (fallback, limited but reliable)
     */
    private fun parseJsonResults(jsonString: String, query: String, maxResults: Int): List<SearchResult> {
        val results = mutableListOf<SearchResult>()

        try {
            val json = JSONObject(jsonString)

            // Try to get results from RelatedTopics
            if (json.has("RelatedTopics")) {
                val topics = json.getJSONArray("RelatedTopics")

                for (i in 0 until minOf(topics.length(), maxResults)) {
                    try {
                        val topic = topics.get(i)

                        if (topic is JSONObject) {
                            val text = topic.optString("Text", "")
                            val url = topic.optString("FirstURL", "")

                            if (text.isNotBlank() && url.isNotBlank()) {
                                results.add(
                                    SearchResult(
                                        title = text.take(100),
                                        snippet = text,
                                        url = url,
                                        position = results.size + 1
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {
                        continue
                    }
                }
            }

            // If no results, create an informational result
            if (results.isEmpty()) {
                results.add(
                    SearchResult(
                        title = "Search for: $query",
                        snippet = "Limited results available via API. Try the web search for more results.",
                        url = "https://duckduckgo.com/?q=${URLEncoder.encode(query, "UTF-8")}",
                        position = 1
                    )
                )
            }

        } catch (e: Exception) {
            println("Error parsing JSON: ${e.message}")
        }

        return results
    }

    /**
     * Extract URL from DuckDuckGo redirect links
     */
    private fun extractRedirectUrl(redirectUrl: String): String? {
        return try {
            // Handle different redirect formats
            val patterns = listOf(
                "uddg=",
                "kh=-1&uddg=",
                "?uddg="
            )

            for (pattern in patterns) {
                if (redirectUrl.contains(pattern)) {
                    val encoded = redirectUrl.substringAfter(pattern).substringBefore("&")
                    if (encoded.isNotBlank()) {
                        return java.net.URLDecoder.decode(encoded, "UTF-8")
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Clean and normalize text
     */
    private fun cleanText(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace("&ndash;", "–")
            .replace("&mdash;", "—")
            .replace("&hellip;", "…")
            .replace(Regex("<[^>]+>"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Instant answer search with improved error handling
     */
    suspend fun instantAnswerSearch(query: String): Result<String> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null

        repeat(MAX_RETRIES) { attempt ->
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val url = "https://api.duckduckgo.com/?q=$encodedQuery&format=json&no_html=1&skip_disambig=1"

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", userAgents.random())
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}: ${response.message}")
                    }

                    val jsonString = response.body.string()
                    val json = JSONObject(jsonString)

                    val answer = buildString {
                        if (json.has("AbstractText") && json.getString("AbstractText").isNotEmpty()) {
                            appendLine(json.getString("AbstractText"))
                        }

                        if (json.has("Definition") && json.getString("Definition").isNotEmpty()) {
                            appendLine("Definition: ${json.getString("Definition")}")
                        }

                        if (json.has("Answer") && json.getString("Answer").isNotEmpty()) {
                            appendLine("Answer: ${json.getString("Answer")}")
                        }

                        if (json.has("RelatedTopics")) {
                            val topics = json.getJSONArray("RelatedTopics")
                            if (topics.length() > 0) {
                                appendLine("\nRelated:")
                                for (i in 0 until minOf(3, topics.length())) {
                                    val topic = topics.optJSONObject(i)
                                    topic?.let {
                                        if (it.has("Text") && it.getString("Text").isNotEmpty()) {
                                            appendLine("- ${it.getString("Text")}")
                                        }
                                    }
                                }
                            }
                        }
                    }.trim()

                    return@withContext Result.success(
                        if (answer.isNotBlank()) answer else "No instant answer available for this query"
                    )
                }
            } catch (e: Exception) {
                lastException = e
                if (attempt < MAX_RETRIES - 1) {
                    delay(INITIAL_RETRY_DELAY * (1 shl attempt))
                }
            }
        }

        Result.failure(lastException ?: IOException("Instant answer search failed"))
    }

    /**
     * Advanced search with custom operators
     */
    suspend fun advancedSearch(
        baseQuery: String,
        site: String? = null,
        fileType: String? = null,
        inTitle: String? = null,
        inUrl: String? = null,
        exclude: List<String>? = null,
        maxResults: Int = 5
    ): Result<DuckDuckGoSearchResponse> {
        val query = buildString {
            append(baseQuery)
            site?.let { append(" site:$it") }
            fileType?.let { append(" filetype:$it") }
            inTitle?.let { append(" intitle:$it") }
            inUrl?.let { append(" inurl:$it") }
            exclude?.forEach { append(" -$it") }
        }

        return search(query.trim(), maxResults)
    }

    /**
     * Batch search with improved rate limiting
     */
    suspend fun batchSearch(
        queries: List<String>,
        maxResults: Int = 3
    ): Result<Map<String, DuckDuckGoSearchResponse>> = withContext(Dispatchers.IO) {
        try {
            val results = mutableMapOf<String, DuckDuckGoSearchResponse>()

            for ((index, query) in queries.withIndex()) {
                val searchResult = search(query, maxResults)
                if (searchResult.isSuccess) {
                    results[query] = searchResult.getOrThrow()
                }

                // Progressive delay to avoid rate limiting
                if (index < queries.size - 1) {
                    delay(1000 + Random.nextLong(0, 1000))
                }
            }

            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Enhanced search with query improvements
     */
    suspend fun enhancedSearch(
        query: String,
        maxResults: Int = 5,
        addQuotes: Boolean = false,
        includeVariations: Boolean = false
    ): Result<DuckDuckGoSearchResponse> {
        val enhancedQuery = buildString {
            if (addQuotes && !query.contains("\"")) {
                append("\"$query\"")
            } else {
                append(query)
            }

            if (includeVariations) {
                val words = query.split(" ")
                if (words.size > 1) {
                    append(" OR ")
                    append(words.joinToString(" OR "))
                }
            }
        }

        return search(enhancedQuery.trim(), maxResults)
    }
}