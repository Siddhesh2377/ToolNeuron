package com.dark.tool_neuron.viewmodel

import com.dark.tool_neuron.model.WebSearchEvent
import com.dark.tool_neuron.model.WebSearchHit
import com.dark.tool_neuron.repo.web_search.WebSearchPrompts
import com.dark.tool_neuron.repo.web_search.WebSearcher
import com.dark.tool_neuron.service.inference.InferenceClient
import com.dark.tool_neuron.service.inference.InferenceEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

@Singleton
class WebSearchCoordinator @Inject constructor(
    private val webSearcher: WebSearcher,
) {
    private val _events = MutableSharedFlow<WebSearchEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<WebSearchEvent> = _events.asSharedFlow()

    private val _activeRuns = MutableStateFlow<Set<String>>(emptySet())
    val activeRuns: StateFlow<Set<String>> = _activeRuns.asStateFlow()

    private val jobs = mutableMapOf<String, Job>()

    fun start(
        scope: CoroutineScope,
        userQuery: String,
    ): String {
        val runId = "ws_" + UUID.randomUUID().toString()
        markActive(runId, true)
        val job = scope.launch(Dispatchers.IO) {
            run(runId = runId, userQuery = userQuery)
        }
        jobs[runId] = job
        job.invokeOnCompletion {
            jobs.remove(runId)
            markActive(runId, false)
        }
        return runId
    }

    fun cancel(runId: String, reason: String = "Cancelled") {
        jobs[runId]?.cancel(CancellationException(reason))
    }

    fun cancelAll(reason: String = "Cancelled") {
        jobs.keys.toList().forEach { cancel(it, reason) }
    }

    private suspend fun run(runId: String, userQuery: String) {
        try {
            emit(WebSearchEvent.Plan(runId, userQuery))

            if (!InferenceClient.isModelLoaded.value) {
                emit(WebSearchEvent.Failed(runId, "Load a chat model first"))
                return
            }

            val requestedUrl = extractUrl(userQuery)
            val queries = if (requestedUrl != null) {
                buildUrlFocusedQueries(userQuery, requestedUrl)
            } else if (wantsPlayStore(userQuery)) {
                buildPlayStoreQueries(userQuery)
            } else {
                generateQueries(userQuery).take(MAX_QUERIES)
            }
            if (queries.isEmpty()) {
                emit(WebSearchEvent.Failed(runId, "Couldn't generate search queries"))
                return
            }
            emit(WebSearchEvent.QueriesGenerated(runId, queries))

            val allHits = mutableListOf<WebSearchHit>()
            val seenUrls = mutableSetOf<String>()
            queries.forEachIndexed { idx, q ->
                // Throttle between back-to-back queries. DDG flags 3 sequential
                // POSTs from the same IP in <1s as bot traffic and answers with
                // an HTTP 202 anti-bot challenge.
                if (idx > 0) delay(SEARCH_THROTTLE_MS)
                emit(WebSearchEvent.SearchStart(runId, idx, q))
                val result = webSearcher.search(q, RESULTS_PER_QUERY, idx)
                val hits = result.getOrDefault(emptyList())
                val deduped = hits.filter { it.url.isNotBlank() && seenUrls.add(it.url) }
                emit(WebSearchEvent.SearchHits(runId, idx, deduped))
                allHits.addAll(deduped)
            }

            val filteredHits = filterAndRankHits(userQuery, requestedUrl, allHits)
            if (filteredHits.isEmpty()) {
                emit(WebSearchEvent.Failed(runId, "No search results found"))
                return
            }

            emit(WebSearchEvent.SynthesizeStart(runId))
            val answer = directAnswerIfPossible(userQuery, filteredHits)
                ?: runInference(WebSearchPrompts.synthesize(userQuery, filteredHits), SYNTHESIZE_MAX_TOKENS)
            emit(WebSearchEvent.Done(runId, answer, filteredHits))
        } catch (ce: CancellationException) {
            _events.tryEmit(WebSearchEvent.Cancelled(runId, ce.message ?: "Cancelled"))
            throw ce
        } catch (t: Throwable) {
            _events.tryEmit(WebSearchEvent.Failed(runId, t.message ?: "Web search failed"))
        }
    }

    private suspend fun generateQueries(userQuery: String): List<String> {
        val raw = runInference(WebSearchPrompts.generateQueries(userQuery), QUERY_GEN_MAX_TOKENS)
        val parsed = parseQueries(raw)
        // Small chat models (LFM2-350M, Qwen 0.5B) often ignore the "exactly 3
        // numbered" format and emit one terse query or unstructured text. The
        // regex won't match those, leaving us with zero queries and no way to
        // search. Fall back to the user's original query as a single search so
        // the flow always produces something.
        if (parsed.isEmpty()) return listOf(userQuery)
        return parsed
    }

    private fun parseQueries(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        val out = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        for (line in raw.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            // First try the strict numbered/bullet format
            val m = WebSearchPrompts.QUERY_LINE_REGEX.matchEntire(trimmed)
            val candidate = if (m != null) {
                m.groupValues[1].trim().trim('"', '\'')
            } else {
                // Fall back to "any line that looks like a search phrase":
                // 3+ words, no trailing colon (skips section headers like
                // "Queries:"), no markdown emphasis chars dominating.
                val cleaned = trimmed.trim('"', '\'', '*', '_', '`')
                if (cleaned.endsWith(':')) continue
                if (cleaned.split(Regex("\\s+")).size < 2) continue
                if (cleaned.length > 120) continue
                cleaned
            }
            if (candidate.length < 3) continue
            val key = candidate.lowercase()
            if (seen.add(key)) out.add(candidate)
            if (out.size >= MAX_QUERIES) break
        }
        return out
    }

    private fun extractUrl(text: String): String? =
        Regex("""https?://[^\s<>"']+""").find(text)?.value?.trimEnd('.', ',', ')', ']')

    private fun buildUrlFocusedQueries(userQuery: String, url: String): List<String> {
        val domain = domainOf(url).orEmpty()
        val cleaned = userQuery.replace(url, "").trim().ifBlank { "summary" }
        return listOfNotNull(
            url,
            domain.takeIf { it.isNotBlank() }?.let { "site:$it $cleaned" },
            domain.takeIf { it.isNotBlank() }?.let { "$it ${pathWords(url)} $cleaned" },
        ).distinct().take(MAX_QUERIES)
    }

    private fun buildPlayStoreQueries(userQuery: String): List<String> {
        val appName = userQuery
            .replace(Regex("""(?i)\b(give|me|the|direct|exact|download|link|url|for|to|on|google|play|store|app|please)\b"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .ifBlank { userQuery.trim() }
        return listOf(
            "$appName site:play.google.com/store/apps/details",
            "$appName Google Play Store",
            "$appName official Android app",
        ).distinct().take(MAX_QUERIES)
    }

    private fun filterAndRankHits(
        userQuery: String,
        requestedUrl: String?,
        hits: List<WebSearchHit>,
    ): List<WebSearchHit> {
        val requestedDomain = requestedUrl?.let(::domainOf)
        val wantsPlayStore = wantsPlayStore(userQuery)
        val wantsDirectLink = userQuery.contains("link", ignoreCase = true) ||
            userQuery.contains("download", ignoreCase = true)

        val scored = hits.map { hit ->
            val domain = domainOf(hit.url).orEmpty()
            var score = 0
            if (!requestedDomain.isNullOrBlank() && domain == requestedDomain) score += 100
            if (!requestedUrl.isNullOrBlank() && hit.url.startsWith(requestedUrl, ignoreCase = true)) score += 80
            if (wantsPlayStore && domain == "play.google.com") score += 120
            if (wantsDirectLink && (domain.startsWith("www.") || domain.isNotBlank())) score += 10
            if (hit.title.contains("official", ignoreCase = true) || hit.snippet.contains("official", ignoreCase = true)) score += 20
            if (hit.url.contains("support.google.com") && wantsPlayStore) score += 20
            hit to score
        }

        val narrowed = if (!requestedDomain.isNullOrBlank()) {
            scored.filter { domainOf(it.first.url) == requestedDomain }.ifEmpty { scored }
        } else if (wantsPlayStore) {
            scored.filter { domainOf(it.first.url) == "play.google.com" || domainOf(it.first.url) == "support.google.com" }
                .ifEmpty { scored }
        } else {
            scored
        }

        return narrowed
            .sortedWith(compareByDescending<Pair<WebSearchHit, Int>> { it.second }.thenBy { it.first.sourceQueryIndex })
            .map { it.first }
            .distinctBy { normalizeUrl(it.url) }
            .take(MAX_FILTERED_SOURCES)
    }

    private fun wantsPlayStore(userQuery: String): Boolean =
        userQuery.contains("play store", ignoreCase = true) ||
            userQuery.contains("google play", ignoreCase = true)

    private fun directAnswerIfPossible(userQuery: String, hits: List<WebSearchHit>): String? {
        val wantsDirectLink = userQuery.contains("link", ignoreCase = true) ||
            userQuery.contains("url", ignoreCase = true) ||
            userQuery.contains("download", ignoreCase = true)
        if (!wantsDirectLink) return null

        val playStoreHit = hits.firstOrNull { hit ->
            domainOf(hit.url) == "play.google.com" &&
                hit.url.contains("/store/apps/details", ignoreCase = true)
        }
        if (playStoreHit != null) {
            return buildString {
                append("Direct Play Store link: ")
                append(playStoreHit.url)
                if (playStoreHit.title.isNotBlank()) {
                    append("\n\n")
                    append(playStoreHit.title)
                }
            }
        }

        val officialHit = hits.firstOrNull { hit ->
            hit.title.contains("official", ignoreCase = true) ||
                hit.snippet.contains("official", ignoreCase = true)
        } ?: hits.firstOrNull()
        return officialHit?.let { "Best matching link: ${it.url}" }
    }

    private fun domainOf(url: String): String? = runCatching {
        URI(url).host?.lowercase()?.removePrefix("www.")
    }.getOrNull()

    private fun pathWords(url: String): String = runCatching {
        URI(url).path.orEmpty()
            .replace(Regex("""[/_\-.]+"""), " ")
            .trim()
            .take(80)
    }.getOrDefault("")

    private fun normalizeUrl(url: String): String =
        url.substringBefore('#').trimEnd('/')

    private suspend fun runInference(prompt: String, maxTokens: Int): String {
        val sb = StringBuilder()
        try {
            InferenceClient.generate(prompt, maxTokens)
                .takeWhile { ev ->
                    when (ev) {
                        is InferenceEvent.Token -> { sb.append(ev.text); true }
                        InferenceEvent.Done -> false
                        is InferenceEvent.Error -> false
                        else -> true
                    }
                }.toList()
        } catch (ce: CancellationException) {
            runCatching { InferenceClient.stopGeneration() }
            throw ce
        }
        return sb.toString().trim()
    }

    private suspend fun emit(event: WebSearchEvent) {
        withContext(Dispatchers.Default) { _events.emit(event) }
    }

    private fun markActive(runId: String, active: Boolean) {
        _activeRuns.value = if (active) _activeRuns.value + runId else _activeRuns.value - runId
    }

    companion object {
        private const val MAX_QUERIES = 3
        private const val RESULTS_PER_QUERY = 5
        private const val QUERY_GEN_MAX_TOKENS = 200
        private const val SYNTHESIZE_MAX_TOKENS = 1024
        private const val SEARCH_THROTTLE_MS = 1800L
        private const val MAX_FILTERED_SOURCES = 8
    }
}
