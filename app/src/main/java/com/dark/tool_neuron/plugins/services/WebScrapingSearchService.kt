package com.dark.tool_neuron.plugins.services

import android.util.Log
import com.dark.tool_neuron.models.plugins.DuckDuckGoSearchResponse
import com.dark.tool_neuron.models.plugins.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Google-scraping search service that replaces the broken DuckDuckGo endpoints.
 *
 * Strategy 1 (fast): HTTP GET google.com/search with mobile User-Agent, parse HTML
 * Strategy 2 (fallback): Lite Google search endpoint with different UA pool
 *
 * Returns [DuckDuckGoSearchResponse] for drop-in compatibility with WebSearchPlugin.
 */
class WebScrapingSearchService {

    companion object {
        private const val TAG = "WebScrapingSearch"
        private const val MAX_RETRIES = 2
        private const val INITIAL_RETRY_DELAY_MS = 1500L
        private const val MIN_QUERY_LENGTH = 1
        private const val MAX_QUERY_LENGTH = 500
    }

    // ── HTTP Client ──

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    // ── User-Agent Pools ──

    private val mobileUserAgents = listOf(
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.3 Mobile/15E148 Safari/604.1",
        "Mozilla/5.0 (Linux; Android 13; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/121.0.6167.171 Mobile/15E148 Safari/604.1"
    )

    private val desktopUserAgents = listOf(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:123.0) Gecko/20100101 Firefox/123.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Safari/605.1.15"
    )

    // ── Public API ──

    /**
     * Search the web by scraping Google results.
     * Tries mobile Google first, falls back to desktop Google with different parsing.
     */
    suspend fun search(
        query: String,
        maxResults: Int = 5,
        safeSearch: Boolean = true,
        @Suppress("UNUSED_PARAMETER") region: String? = null,
        @Suppress("UNUSED_PARAMETER") timeRange: String? = null
    ): Result<DuckDuckGoSearchResponse> = withContext(Dispatchers.IO) {
        val sanitized = sanitizeQuery(query)
        if (sanitized.length < MIN_QUERY_LENGTH) {
            return@withContext Result.failure(
                IllegalArgumentException("Query too short (min $MIN_QUERY_LENGTH chars)")
            )
        }
        if (sanitized.length > MAX_QUERY_LENGTH) {
            return@withContext Result.failure(
                IllegalArgumentException("Query too long (max $MAX_QUERY_LENGTH chars)")
            )
        }

        val capped = maxResults.coerceIn(1, 10)

        // Strategy 1: Mobile Google HTML scrape
        val mobileResult = scrapeGoogleMobile(sanitized, capped, safeSearch)
        if (mobileResult.isSuccess) {
            val response = mobileResult.getOrThrow()
            if (response.results.isNotEmpty()) {
                Log.d(TAG, "Mobile strategy returned ${response.results.size} results")
                return@withContext Result.success(response)
            }
        }
        Log.w(TAG, "Mobile strategy failed: ${mobileResult.exceptionOrNull()?.message}")

        // Small delay before fallback
        delay(300 + Random.nextLong(0, 300))

        // Strategy 2: Desktop Google HTML scrape with different selectors
        val desktopResult = scrapeGoogleDesktop(sanitized, capped, safeSearch)
        if (desktopResult.isSuccess) {
            val response = desktopResult.getOrThrow()
            if (response.results.isNotEmpty()) {
                Log.d(TAG, "Desktop strategy returned ${response.results.size} results")
                return@withContext Result.success(response)
            }
        }
        Log.w(TAG, "Desktop strategy failed: ${desktopResult.exceptionOrNull()?.message}")

        Result.failure(
            IOException("All Google search strategies failed for: $sanitized")
        )
    }

    // ── Strategy 1: Mobile Google ──

    private suspend fun scrapeGoogleMobile(
        query: String,
        maxResults: Int,
        safeSearch: Boolean
    ): Result<DuckDuckGoSearchResponse> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null

        repeat(MAX_RETRIES) { attempt ->
            try {
                val startTime = System.currentTimeMillis()
                val encoded = URLEncoder.encode(query, "UTF-8")

                val url = buildString {
                    append("https://www.google.com/search?q=")
                    append(encoded)
                    append("&num=").append(maxResults + 2) // request extra to account for filtering
                    append("&hl=en")
                    if (safeSearch) append("&safe=active")
                }

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", mobileUserAgents.random())
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Accept-Encoding", "gzip, deflate")
                    .header("DNT", "1")
                    .header("Connection", "keep-alive")
                    .header("Upgrade-Insecure-Requests", "1")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.code == 429) {
                        throw RateLimitException("Google rate limited (429)")
                    }
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}: ${response.message}")
                    }

                    val html = response.body.string()

                    if (isBlockedOrCaptcha(html)) {
                        throw BlockedException("Google CAPTCHA / unusual traffic detected")
                    }

                    val results = parseGoogleResults(html, maxResults)
                    val elapsed = System.currentTimeMillis() - startTime

                    return@withContext Result.success(
                        DuckDuckGoSearchResponse(
                            query = query,
                            results = results,
                            totalResults = results.size,
                            searchTime = elapsed
                        )
                    )
                }
            } catch (e: Exception) {
                lastException = e
                if (attempt < MAX_RETRIES - 1) {
                    val backoff = INITIAL_RETRY_DELAY_MS * (1 shl attempt) + Random.nextLong(0, 1000)
                    delay(backoff)
                }
            }
        }

        Result.failure(lastException ?: IOException("Mobile Google scrape failed"))
    }

    // ── Strategy 2: Desktop Google ──

    private suspend fun scrapeGoogleDesktop(
        query: String,
        maxResults: Int,
        safeSearch: Boolean
    ): Result<DuckDuckGoSearchResponse> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null

        repeat(MAX_RETRIES) { attempt ->
            try {
                val startTime = System.currentTimeMillis()
                val encoded = URLEncoder.encode(query, "UTF-8")

                val url = buildString {
                    append("https://www.google.com/search?q=")
                    append(encoded)
                    append("&num=").append(maxResults + 2)
                    append("&hl=en")
                    if (safeSearch) append("&safe=active")
                }

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", desktopUserAgents.random())
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Accept-Encoding", "gzip, deflate")
                    .header("DNT", "1")
                    .header("Connection", "keep-alive")
                    .header("Upgrade-Insecure-Requests", "1")
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "none")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.code == 429) {
                        throw RateLimitException("Google rate limited (429)")
                    }
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}: ${response.message}")
                    }

                    val html = response.body.string()

                    if (isBlockedOrCaptcha(html)) {
                        throw BlockedException("Google CAPTCHA / unusual traffic detected")
                    }

                    val results = parseGoogleResults(html, maxResults)
                    val elapsed = System.currentTimeMillis() - startTime

                    return@withContext Result.success(
                        DuckDuckGoSearchResponse(
                            query = query,
                            results = results,
                            totalResults = results.size,
                            searchTime = elapsed
                        )
                    )
                }
            } catch (e: Exception) {
                lastException = e
                if (attempt < MAX_RETRIES - 1) {
                    val backoff = INITIAL_RETRY_DELAY_MS * (1 shl attempt) + Random.nextLong(0, 500)
                    delay(backoff)
                }
            }
        }

        Result.failure(lastException ?: IOException("Desktop Google scrape failed"))
    }

    // ── HTML Parsing ──

    /**
     * Parse Google search result HTML using Jsoup.
     * Tries multiple selector strategies because Google changes its markup frequently.
     */
    private fun parseGoogleResults(html: String, maxResults: Int): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val seenUrls = mutableSetOf<String>()

        try {
            val doc = Jsoup.parse(html)

            // Strategy A: Standard result divs with <h3> headings
            val resultDivs = doc.select("div.g, div[data-hveid] div.g, div.Gx5Zad, div.tF2Cxc")
            for (div in resultDivs) {
                if (results.size >= maxResults) break

                val heading = div.selectFirst("h3") ?: continue
                val title = heading.text().trim()
                if (title.isBlank() || title.length < 3) continue

                val url = extractGoogleUrl(div) ?: continue
                if (url in seenUrls) continue
                seenUrls.add(url)

                val snippet = extractSnippet(div)

                results.add(
                    SearchResult(
                        title = title,
                        snippet = snippet,
                        url = url,
                        position = results.size + 1
                    )
                )
            }

            // Strategy B: Find all <h3> tags and walk up to parent link
            if (results.isEmpty()) {
                val headings = doc.select("h3")
                for (h3 in headings) {
                    if (results.size >= maxResults) break

                    val title = h3.text().trim()
                    if (title.isBlank() || title.length < 3) continue

                    // Walk up to find enclosing <a>
                    val link = h3.closest("a")
                        ?: h3.parent()?.selectFirst("a[href]")
                        ?: continue

                    val rawHref = link.attr("href")
                    val url = resolveGoogleHref(rawHref) ?: continue
                    if (url in seenUrls) continue
                    seenUrls.add(url)

                    // Snippet: look at the sibling/parent container
                    val container = h3.closest("div[class]") ?: h3.parent()
                    val snippet = container?.let { extractSnippet(it) } ?: ""

                    results.add(
                        SearchResult(
                            title = title,
                            snippet = snippet,
                            url = url,
                            position = results.size + 1
                        )
                    )
                }
            }

            // Strategy C: Broad fallback -- any <a> with /url?q= hrefs
            if (results.isEmpty()) {
                val links = doc.select("a[href*=/url?q=]")
                for (link in links) {
                    if (results.size >= maxResults) break

                    val rawHref = link.attr("href")
                    val url = resolveGoogleHref(rawHref) ?: continue
                    if (url in seenUrls) continue

                    val title = link.text().trim()
                    if (title.isBlank() || title.length < 5) continue

                    // Skip Google-internal pages
                    if (url.contains("google.com/") || url.contains("accounts.google")) continue

                    seenUrls.add(url)

                    results.add(
                        SearchResult(
                            title = title,
                            snippet = "",
                            url = url,
                            position = results.size + 1
                        )
                    )
                }
            }

        } catch (e: Exception) {
            Log.w(TAG, "Error parsing Google HTML: ${e.message}")
        }

        return results
    }

    /**
     * Extract a clean URL from a Google result div.
     */
    private fun extractGoogleUrl(div: org.jsoup.nodes.Element): String? {
        // Try <a> with href containing /url?q=
        val redirectLink = div.selectFirst("a[href*=/url?q=]")
        if (redirectLink != null) {
            val resolved = resolveGoogleHref(redirectLink.attr("href"))
            if (resolved != null) return resolved
        }

        // Try plain <a href="https://...">
        val plainLink = div.selectFirst("a[href^=https://], a[href^=http://]")
        if (plainLink != null) {
            val href = plainLink.attr("href")
            if (!href.contains("google.com/") && !href.contains("accounts.google")) {
                return href
            }
        }

        return null
    }

    /**
     * Resolve a Google redirect href (/url?q=...) to the target URL.
     */
    private fun resolveGoogleHref(rawHref: String): String? {
        return try {
            when {
                rawHref.contains("/url?q=") -> {
                    val encoded = rawHref.substringAfter("/url?q=").substringBefore("&")
                    val decoded = URLDecoder.decode(encoded, "UTF-8")
                    if (decoded.startsWith("http") && !decoded.contains("google.com/")) decoded else null
                }
                rawHref.startsWith("http") && !rawHref.contains("google.com/") -> rawHref
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extract snippet text from a result container.
     */
    private fun extractSnippet(container: org.jsoup.nodes.Element): String {
        // Try common snippet selectors
        val selectors = listOf(
            "div.VwiC3b", "span.st", "div[data-sncf]", "div.IsZvec",
            "div.s", "span.aCOpRe"
        )
        for (sel in selectors) {
            val el = container.selectFirst(sel)
            if (el != null) {
                val text = el.text().trim()
                if (text.length > 15) return cleanText(text)
            }
        }

        // Fallback: grab all <span> text that looks like a snippet
        val spans = container.select("span")
        for (span in spans) {
            val text = span.text().trim()
            if (text.length > 40 && !text.contains("http")) {
                return cleanText(text.take(300))
            }
        }

        return ""
    }

    // ── Utilities ──

    private fun sanitizeQuery(query: String): String {
        return query
            .trim()
            .replace(Regex("\\s+"), " ")
            .replace(Regex("[\\x00-\\x1F\\x7F]"), "")
    }

    private fun isBlockedOrCaptcha(html: String): Boolean {
        val lower = html.lowercase()
        return lower.contains("unusual traffic") ||
                lower.contains("captcha") ||
                lower.contains("sorry/index") ||
                lower.contains("recaptcha") ||
                (html.length < 500 && lower.contains("blocked"))
    }

    private fun cleanText(text: String): String {
        return text
            .replace(Regex("<[^>]+>"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    // ── Custom Exceptions ──

    private class RateLimitException(message: String) : IOException(message)
    private class BlockedException(message: String) : IOException(message)
}
