package com.dark.tool_neuron.plugins.services

import com.dark.tool_neuron.models.plugins.ScrapedContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Aggressive web scraping service with advanced features
 * Features: retry logic, user agent rotation, cookie support, multiple extraction strategies,
 * structured data parsing, readability algorithms, and robust error handling
 */
class WebScrapingService {

    // Cookie jar for session persistence
    private val cookieJar = object : CookieJar {
        private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookieStore[url.host] = cookies.toMutableList()
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cookieStore[url.host] ?: emptyList()
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .cookieJar(cookieJar)
        .build()

    // Aggressive user agent rotation
    private val userAgents = listOf(
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1 Mobile/15E148 Safari/604.1",
        "Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Android 13; Mobile; rv:120.0) Gecko/120.0 Firefox/120.0",
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.43 Mobile Safari/537.36"
    )

    companion object {
        const val MAX_RETRIES = 3
        const val INITIAL_RETRY_DELAY = 1000L

        // Common content selectors for readability
        val CONTENT_SELECTORS = listOf(
            "article", "main", "[role=main]", ".post-content", ".entry-content",
            ".article-content", ".content", "#content", ".post", ".article"
        )

        // Noise selectors to remove
        val NOISE_SELECTORS = listOf(
            "script", "style", "nav", "header", "footer", "aside", ".sidebar",
            ".ads", ".advertisement", ".social-share", ".comments", "#comments",
            ".cookie-banner", ".popup", ".modal", "iframe[src*=ads]"
        )
    }

    /**
     * Aggressive scraping with retry logic, multiple extraction strategies, and rich metadata
     * @param url The URL to scrape
     * @param selector Optional CSS selector to extract specific content
     * @param maxLength Maximum content length in characters
     * @param useReadability Use readability algorithm for better content extraction
     * @param extractStructuredData Extract JSON-LD and microdata
     * @return ScrapedContent with the extracted data
     */
    suspend fun scrape(
        url: String,
        selector: String? = null,
        maxLength: Int = 5000,
        useReadability: Boolean = true,
        extractStructuredData: Boolean = true
    ): Result<ScrapedContent> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null

        // Retry with exponential backoff
        repeat(MAX_RETRIES) { attempt ->
            try {
                val startTime = System.currentTimeMillis()

                // Validate URL
                if (!isValidUrl(url)) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Invalid URL format: $url")
                    )
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

                    // Detect charset
                    val contentType = response.header("Content-Type")
                    val charset = detectCharset(contentType)

                    val bodyBytes = response.body.bytes()
                    val html = String(bodyBytes, charset)
                    val fetchTime = System.currentTimeMillis() - startTime

                    // Parse HTML with JSoup
                    val document: Document = Jsoup.parse(html, url)

                    // Extract content with multiple strategies
                    val extractedContent = when {
                        !selector.isNullOrBlank() -> extractBySelector(document, selector)
                        useReadability -> extractWithReadability(document)
                        else -> extractMainContent(document)
                    }

                    // Get enhanced metadata
                    val metadata = extractEnhancedMetadata(
                        document,
                        selector,
                        extractStructuredData,
                        html.length
                    )

                    // Create response
                    val content = ScrapedContent(
                        url = url,
                        title = extractBestTitle(document),
                        content = extractedContent.take(maxLength),
                        contentLength = extractedContent.length,
                        fetchTime = fetchTime,
                        metadata = metadata
                    )

                    return@withContext Result.success(content)
                }
            } catch (e: Exception) {
                lastException = e
                if (attempt < MAX_RETRIES - 1) {
                    val delayTime = INITIAL_RETRY_DELAY * (1 shl attempt)
                    delay(delayTime + Random.nextLong(0, 500))
                }
            }
        }

        Result.failure(lastException ?: IOException("Scraping failed after $MAX_RETRIES attempts"))
    }

    /**
     * Detect charset from content type header
     */
    private fun detectCharset(contentType: String?): Charset {
        return try {
            contentType?.let {
                val charsetMatch = Regex("charset=([^;\\s]+)").find(it)
                charsetMatch?.groupValues?.get(1)?.let { cs ->
                    Charset.forName(cs)
                }
            } ?: Charsets.UTF_8
        } catch (e: Exception) {
            Charsets.UTF_8
        }
    }

    /**
     * Extract best title from multiple sources
     */
    private fun extractBestTitle(document: Document): String {
        // Try multiple sources in priority order
        return listOfNotNull(
            document.selectFirst("meta[property=og:title]")?.attr("content"),
            document.selectFirst("meta[name=twitter:title]")?.attr("content"),
            document.selectFirst("h1")?.text(),
            document.title()
        ).firstOrNull { it.isNotBlank() } ?: "Untitled"
    }

    /**
     * Extract content using CSS selector with fallbacks
     */
    private fun extractBySelector(document: Document, selector: String): String {
        return try {
            val elements = document.select(selector)
            if (elements.isEmpty()) {
                "No elements found matching selector: $selector"
            } else {
                buildString {
                    elements.forEach { element ->
                        // Extract text preserving structure
                        appendLine(extractElementContent(element))
                        appendLine()
                    }
                }.trim()
            }
        } catch (e: Exception) {
            "Error extracting content with selector '$selector': ${e.message}"
        }
    }

    /**
     * Extract content from an element preserving structure
     */
    private fun extractElementContent(element: Element): String {
        return buildString {
            // Headers
            element.select("h1, h2, h3, h4, h5, h6").forEach {
                appendLine("## ${it.text().trim()}")
            }

            // Paragraphs
            element.select("p").forEach {
                val text = it.text().trim()
                if (text.length > 10) {
                    appendLine(text)
                    appendLine()
                }
            }

            // Lists
            element.select("ul, ol").forEach { list ->
                list.select("li").forEach {
                    appendLine("• ${it.text().trim()}")
                }
                appendLine()
            }

            // Tables
            element.select("table").forEach { table ->
                appendLine(extractTable(table))
                appendLine()
            }

            // Code blocks
            element.select("pre, code").forEach {
                appendLine("```")
                appendLine(it.text().trim())
                appendLine("```")
                appendLine()
            }

            // Blockquotes
            element.select("blockquote").forEach {
                appendLine("> ${it.text().trim()}")
                appendLine()
            }
        }.trim()
    }

    /**
     * Extract table in readable format
     */
    private fun extractTable(table: Element): String {
        return buildString {
            table.select("tr").forEach { row ->
                val cells = row.select("th, td")
                appendLine(cells.joinToString(" | ") { it.text().trim() })
            }
        }
    }

    /**
     * Extract main content from the page (fallback when no selector is provided)
     */
    private fun extractMainContent(document: Document): String {
        // Clone document to avoid modifying original
        val doc = document.clone()

        // Remove noise
        NOISE_SELECTORS.forEach { selector ->
            doc.select(selector).remove()
        }

        // Try to find main content area
        val mainContent = CONTENT_SELECTORS
            .firstNotNullOfOrNull { doc.selectFirst(it) }
            ?: doc.body()

        return buildString {
            // Get all relevant elements
            val elements = mainContent.select("h1, h2, h3, h4, h5, h6, p, li, blockquote, pre")

            for (element in elements) {
                val text = element.text().trim()
                if (text.length > 15) { // Filter out very short snippets
                    when (element.tagName()) {
                        in listOf("h1", "h2", "h3", "h4", "h5", "h6") -> {
                            appendLine("\n## $text")
                        }
                        "blockquote" -> {
                            appendLine("\n> $text")
                        }
                        "pre" -> {
                            appendLine("\n```\n$text\n```")
                        }
                        else -> {
                            appendLine(text)
                            appendLine()
                        }
                    }
                }
            }
        }.trim()
    }

    /**
     * Advanced readability-based extraction
     * Scores elements and extracts the most content-rich section
     */
    private fun extractWithReadability(document: Document): String {
        val doc = document.clone()

        // Remove noise
        NOISE_SELECTORS.forEach { selector ->
            doc.select(selector).remove()
        }

        // Score all potential content containers
        val scoredElements = mutableListOf<Pair<Element, Int>>()

        doc.select("div, article, section, main").forEach { element ->
            var score = 0

            // Text content score
            val textLength = element.text().length
            score += textLength / 100

            // Paragraph density
            val paragraphs = element.select("p")
            score += paragraphs.size * 10

            // Link density (penalize high link density)
            val links = element.select("a")
            val linkTextLength = links.sumOf { it.text().length }
            val linkDensity = if (textLength > 0) linkTextLength.toFloat() / textLength else 0f
            score -= (linkDensity * 50).toInt()

            // Positive indicators
            if (element.classNames().any { it.contains("content") || it.contains("article") || it.contains("post") }) {
                score += 25
            }

            // Negative indicators
            if (element.classNames().any { it.contains("comment") || it.contains("footer") || it.contains("sidebar") }) {
                score -= 25
            }

            if (score > 0) {
                scoredElements.add(element to score)
            }
        }

        // Get the highest scoring element
        val bestElement = scoredElements.maxByOrNull { it.second }?.first

        return if (bestElement != null) {
            extractElementContent(bestElement)
        } else {
            extractMainContent(document)
        }
    }

    /**
     * Extract comprehensive metadata from the document
     */
    private fun extractEnhancedMetadata(
        document: Document,
        selector: String?,
        extractStructuredData: Boolean,
        htmlSize: Int
    ): Map<String, String> {
        val metadata = mutableMapOf<String, String>()

        // Selector used
        metadata["selector"] = selector ?: "auto"

        // Basic meta tags
        metadata["description"] = document.selectFirst("meta[name=description]")?.attr("content")
            ?: document.selectFirst("meta[property=og:description]")?.attr("content")
            ?: ""

        metadata["author"] = document.selectFirst("meta[name=author]")?.attr("content")
            ?: document.selectFirst("meta[property=article:author]")?.attr("content")
            ?: ""

        metadata["keywords"] = document.selectFirst("meta[name=keywords]")?.attr("content") ?: ""

        // Open Graph metadata
        document.selectFirst("meta[property=og:type]")?.attr("content")?.let {
            metadata["og:type"] = it
        }

        document.selectFirst("meta[property=og:image]")?.attr("content")?.let {
            metadata["og:image"] = it
        }

        document.selectFirst("meta[property=og:url]")?.attr("content")?.let {
            metadata["og:url"] = it
        }

        document.selectFirst("meta[property=og:site_name]")?.attr("content")?.let {
            metadata["og:site_name"] = it
        }

        // Twitter Card metadata
        document.selectFirst("meta[name=twitter:card]")?.attr("content")?.let {
            metadata["twitter:card"] = it
        }

        document.selectFirst("meta[name=twitter:site]")?.attr("content")?.let {
            metadata["twitter:site"] = it
        }

        // Publication metadata
        document.selectFirst("meta[property=article:published_time]")?.attr("content")?.let {
            metadata["published_time"] = it
        }

        document.selectFirst("meta[property=article:modified_time]")?.attr("content")?.let {
            metadata["modified_time"] = it
        }

        document.selectFirst("meta[property=article:section]")?.attr("content")?.let {
            metadata["section"] = it
        }

        document.selectFirst("meta[property=article:tag]")?.attr("content")?.let {
            metadata["tags"] = it
        }

        // Canonical and alternate URLs
        document.selectFirst("link[rel=canonical]")?.attr("href")?.let {
            metadata["canonical_url"] = it
        }

        document.selectFirst("link[rel=alternate]")?.attr("href")?.let {
            metadata["alternate_url"] = it
        }

        // Language
        metadata["language"] = document.selectFirst("html")?.attr("lang") ?: "unknown"

        // Counts
        metadata["paragraph_count"] = document.select("p").size.toString()
        metadata["heading_count"] = document.select("h1, h2, h3, h4, h5, h6").size.toString()
        metadata["image_count"] = document.select("img").size.toString()
        metadata["link_count"] = document.select("a").size.toString()
        metadata["table_count"] = document.select("table").size.toString()
        metadata["list_count"] = document.select("ul, ol").size.toString()
        metadata["code_block_count"] = document.select("pre, code").size.toString()

        // Size metrics
        metadata["html_size_bytes"] = htmlSize.toString()
        metadata["text_length"] = document.text().length.toString()

        // Structured data extraction
        if (extractStructuredData) {
            val structuredData = extractStructuredData(document)
            if (structuredData.isNotEmpty()) {
                metadata["structured_data"] = structuredData
            }
        }

        // Favicon
        document.selectFirst("link[rel*=icon]")?.attr("href")?.let {
            metadata["favicon"] = it
        }

        // RSS/Atom feeds
        document.selectFirst("link[type='application/rss+xml']")?.attr("href")?.let {
            metadata["rss_feed"] = it
        }

        document.selectFirst("link[type='application/atom+xml']")?.attr("href")?.let {
            metadata["atom_feed"] = it
        }

        return metadata.filter { it.value.isNotBlank() }
    }

    /**
     * Extract structured data (JSON-LD, Microdata)
     */
    private fun extractStructuredData(document: Document): String {
        return buildString {
            // JSON-LD
            document.select("script[type='application/ld+json']").forEach { script ->
                try {
                    val jsonLd = script.data().trim()
                    if (jsonLd.isNotEmpty()) {
                        appendLine("JSON-LD:")
                        appendLine(jsonLd)
                        appendLine()
                    }
                } catch (e: Exception) {
                    // Ignore parsing errors
                }
            }

            // Microdata (basic extraction)
            document.select("[itemscope]").take(5).forEach { item ->
                val itemType = item.attr("itemtype")
                if (itemType.isNotEmpty()) {
                    appendLine("Microdata type: $itemType")
                    item.select("[itemprop]").forEach { prop ->
                        val propName = prop.attr("itemprop")
                        val propValue = prop.attr("content").ifBlank { prop.text() }
                        if (propName.isNotEmpty() && propValue.isNotEmpty()) {
                            appendLine("  $propName: $propValue")
                        }
                    }
                    appendLine()
                }
            }
        }.trim()
    }

    /**
     * Validate URL format
     */
    private fun isValidUrl(url: String): Boolean {
        return try {
            val lowercaseUrl = url.lowercase()
            lowercaseUrl.startsWith("http://") || lowercaseUrl.startsWith("https://")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Aggressively extract all links with retry and filtering
     */
    suspend fun extractLinks(
        url: String,
        filterInternal: Boolean = false,
        filterExternal: Boolean = false
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null

        repeat(MAX_RETRIES) { attempt ->
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", userAgents.random())
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}: ${response.message}")
                    }

                    val html = response.body.string()
                    val document: Document = Jsoup.parse(html, url)
                    val baseUri = java.net.URI(url)

                    val links = document.select("a[href]")
                        .mapNotNull { element ->
                            val href = element.absUrl("href")
                            if (href.isNotBlank() && (href.startsWith("http://") || href.startsWith("https://"))) {
                                href
                            } else null
                        }
                        .filter { link ->
                            val linkUri = try { java.net.URI(link) } catch (e: Exception) { null }
                            when {
                                linkUri == null -> false
                                filterInternal && linkUri.host == baseUri.host -> false
                                filterExternal && linkUri.host != baseUri.host -> false
                                else -> true
                            }
                        }
                        .distinct()
                        .sorted()

                    return@withContext Result.success(links)
                }
            } catch (e: Exception) {
                lastException = e
                if (attempt < MAX_RETRIES - 1) {
                    delay(INITIAL_RETRY_DELAY * (1 shl attempt))
                }
            }
        }

        Result.failure(lastException ?: IOException("Link extraction failed"))
    }

    /**
     * Aggressively extract all images with metadata
     */
    suspend fun extractImages(
        url: String,
        minWidth: Int = 0,
        minHeight: Int = 0
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null

        repeat(MAX_RETRIES) { attempt ->
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", userAgents.random())
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}: ${response.message}")
                    }

                    val html = response.body.string()
                    val document: Document = Jsoup.parse(html, url)

                    val images = mutableListOf<String>()

                    // Extract from img tags
                    document.select("img[src]").forEach { img ->
                        val src = img.absUrl("src")
                        if (src.isNotBlank() && (src.startsWith("http://") || src.startsWith("https://"))) {
                            // Filter by size if specified
                            val width = img.attr("width").toIntOrNull() ?: 0
                            val height = img.attr("height").toIntOrNull() ?: 0

                            if (width >= minWidth && height >= minHeight) {
                                images.add(src)
                            } else if (minWidth == 0 && minHeight == 0) {
                                images.add(src)
                            }
                        }

                        // Also check srcset
                        val srcset = img.attr("srcset")
                        if (srcset.isNotBlank()) {
                            srcset.split(",").forEach { entry ->
                                val srcsetUrl = entry.trim().split(" ").firstOrNull()
                                if (!srcsetUrl.isNullOrBlank()) {
                                    val absoluteUrl = img.absUrl(srcsetUrl)
                                    if (absoluteUrl.startsWith("http")) {
                                        images.add(absoluteUrl)
                                    }
                                }
                            }
                        }
                    }

                    // Extract from Open Graph
                    document.selectFirst("meta[property=og:image]")?.attr("content")?.let {
                        if (it.startsWith("http")) images.add(it)
                    }

                    // Extract from Twitter Card
                    document.selectFirst("meta[name=twitter:image]")?.attr("content")?.let {
                        if (it.startsWith("http")) images.add(it)
                    }

                    // Extract from link tags (favicon, apple-touch-icon, etc.)
                    document.select("link[rel*=icon]").forEach { link ->
                        val href = link.absUrl("href")
                        if (href.startsWith("http")) {
                            images.add(href)
                        }
                    }

                    return@withContext Result.success(images.distinct().sorted())
                }
            } catch (e: Exception) {
                lastException = e
                if (attempt < MAX_RETRIES - 1) {
                    delay(INITIAL_RETRY_DELAY * (1 shl attempt))
                }
            }
        }

        Result.failure(lastException ?: IOException("Image extraction failed"))
    }

    /**
     * Extract all resources (CSS, JS, fonts, etc.)
     */
    suspend fun extractResources(url: String): Result<Map<String, List<String>>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgents.random())
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("HTTP ${response.code}: ${response.message}")
                    )
                }

                val html = response.body.string()
                val document: Document = Jsoup.parse(html, url)

                val resources = mutableMapOf<String, MutableList<String>>()

                // CSS files
                resources["css"] = document.select("link[rel=stylesheet]")
                    .mapNotNull { it.absUrl("href").takeIf { url -> url.startsWith("http") } }
                    .toMutableList()

                // JavaScript files
                resources["js"] = document.select("script[src]")
                    .mapNotNull { it.absUrl("src").takeIf { url -> url.startsWith("http") } }
                    .toMutableList()

                // Fonts
                resources["fonts"] = document.select("link[rel=preload][as=font]")
                    .mapNotNull { it.absUrl("href").takeIf { url -> url.startsWith("http") } }
                    .toMutableList()

                // Videos
                resources["videos"] = document.select("video source[src]")
                    .mapNotNull { it.absUrl("src").takeIf { url -> url.startsWith("http") } }
                    .toMutableList()

                // Audio
                resources["audio"] = document.select("audio source[src]")
                    .mapNotNull { it.absUrl("src").takeIf { url -> url.startsWith("http") } }
                    .toMutableList()

                Result.success(resources.mapValues { it.value.distinct() })
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Extract page headers for analysis
     */
    suspend fun extractHeaders(url: String): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgents.random())
                .head() // HEAD request for just headers
                .build()

            client.newCall(request).execute().use { response ->
                val headers = mutableMapOf<String, String>()

                response.headers.names().forEach { name ->
                    headers[name] = response.header(name) ?: ""
                }

                Result.success(headers)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Batch scrape multiple URLs
     */
    suspend fun batchScrape(
        urls: List<String>,
        maxLength: Int = 3000,
        delayBetweenRequests: Long = 1000
    ): Result<Map<String, ScrapedContent>> = withContext(Dispatchers.IO) {
        try {
            val results = mutableMapOf<String, ScrapedContent>()

            for (url in urls) {
                val result = scrape(url, maxLength = maxLength)
                if (result.isSuccess) {
                    results[url] = result.getOrThrow()
                }
                if (url != urls.last()) {
                    delay(delayBetweenRequests)
                }
            }

            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
