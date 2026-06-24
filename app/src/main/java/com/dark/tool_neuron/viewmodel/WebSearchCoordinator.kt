package com.dark.tool_neuron.viewmodel

import com.dark.tool_neuron.model.WebSearchEvent
import com.dark.tool_neuron.model.WebSearchHit
import com.dark.tool_neuron.repo.web_search.PageFetcher
import com.dark.tool_neuron.repo.web_search.WebSearchMode
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
    private val pageFetcher: PageFetcher,
) {
    private val _events = MutableSharedFlow<WebSearchEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<WebSearchEvent> = _events.asSharedFlow()

    private val _activeRuns = MutableStateFlow<Set<String>>(emptySet())
    val activeRuns: StateFlow<Set<String>> = _activeRuns.asStateFlow()

    private val jobs = mutableMapOf<String, Job>()

    fun start(
        scope: CoroutineScope,
        userQuery: String,
        mode: WebSearchMode,
    ): String {
        val runId = "ws_" + UUID.randomUUID().toString()
        markActive(runId, true)
        val job = scope.launch(Dispatchers.IO) {
            run(runId = runId, userQuery = userQuery, mode = mode)
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

    private suspend fun run(runId: String, userQuery: String, mode: WebSearchMode) {
        try {
            emit(WebSearchEvent.Plan(runId, userQuery, mode.label))

            if (!InferenceClient.isModelLoaded.value) {
                emit(WebSearchEvent.Failed(runId, "Load a chat model first"))
                return
            }

            val startTime = System.currentTimeMillis()
            val requestedUrl = extractUrl(userQuery)

            val allQueries = mutableListOf<String>()
            val seenUrls = mutableSetOf<String>()
            val evidence = mutableListOf<WebSearchHit>()
            val excerpts = mutableMapOf<String, String>()
            var summary = ""
            var coverage = -1
            var lowGainStreak = 0
            var globalIdx = 0
            var totalQueries = 0

            var nextQueries: List<String> = when {
                requestedUrl != null -> buildUrlFocusedQueries(userQuery, requestedUrl, mode.initialQueries)
                wantsPlayStore(userQuery) -> buildPlayStoreQueries(userQuery, mode.initialQueries)
                else -> generateInitialQueries(userQuery, mode.initialQueries)
            }
            if (nextQueries.isEmpty()) {
                emit(WebSearchEvent.Failed(runId, "Couldn't generate search queries"))
                return
            }

            var round = 0
            while (round < mode.maxRounds && nextQueries.isNotEmpty() && totalQueries < mode.maxQueries) {
                if (System.currentTimeMillis() - startTime > mode.timeBudgetMs) break

                emit(WebSearchEvent.RoundStart(
                    runId, round + 1, mode.maxRounds,
                    if (round == 0) "Searching" else "Refining search",
                ))

                val budgetLeft = mode.maxQueries - totalQueries
                val roundQueries = nextQueries.take(budgetLeft)
                allQueries.addAll(roundQueries)
                emit(WebSearchEvent.QueriesGenerated(runId, allQueries.toList()))

                val roundHits = mutableListOf<WebSearchHit>()
                for (q in roundQueries) {
                    if (totalQueries >= mode.maxQueries) break
                    if (totalQueries > 0) delay(SEARCH_THROTTLE_MS)
                    val idx = globalIdx
                    emit(WebSearchEvent.SearchStart(runId, idx, q))
                    val result = webSearcher.search(q, mode.resultsPerQuery, idx)
                    val hits = result.getOrDefault(emptyList())
                    val deduped = hits.filter { it.url.isNotBlank() && seenUrls.add(normalizeUrl(it.url)) }
                    emit(WebSearchEvent.SearchHits(runId, idx, deduped))
                    roundHits.addAll(deduped)
                    evidence.addAll(deduped)
                    globalIdx++
                    totalQueries++
                }

                val newGain = roundHits.size

                if (mode.fetchPages && roundHits.isNotEmpty()) {
                    emit(WebSearchEvent.Status(runId, "Reading pages…"))
                    val ranked = filterAndRankHits(userQuery, requestedUrl, evidence, RANK_POOL)
                    val toFetch = ranked
                        .filter { it.url.isNotBlank() && !excerpts.containsKey(it.url) }
                        .take(mode.pagesPerRound)
                        .map { it.url }
                    runCatching { pageFetcher.fetch(toFetch) }
                        .getOrDefault(emptyList())
                        .forEach { excerpts[it.url] = it.text }
                }

                round++

                if (mode == WebSearchMode.QUICK || mode.followUpQueries == 0) break

                emit(WebSearchEvent.Status(runId, "Reviewing results…"))
                val findings = filterAndRankHits(userQuery, requestedUrl, roundHits, DIGEST_FINDINGS)
                val digest = if (findings.isEmpty()) null
                else runDigest(userQuery, summary, findings, excerpts, mode.followUpQueries)

                if (digest != null) {
                    if (digest.summary.isNotBlank()) summary = digest.summary
                    if (digest.coverage in 0..100) coverage = digest.coverage
                    val seenLower = allQueries.map { it.lowercase() }.toSet()
                    nextQueries = digest.queries.filter { it.lowercase() !in seenLower }
                    emit(WebSearchEvent.Status(runId, "Round ${round} reviewed", coverage))
                } else {
                    nextQueries = emptyList()
                }

                if (newGain < LOW_GAIN_THRESHOLD) lowGainStreak++ else lowGainStreak = 0

                if (coverage >= COVERAGE_STOP) break
                if (lowGainStreak >= 2) break
                if (nextQueries.isEmpty()) break
            }

            val finalPool = filterAndRankHits(userQuery, requestedUrl, evidence, FINAL_SOURCES)
            if (finalPool.isEmpty()) {
                emit(WebSearchEvent.Failed(runId, "No search results found"))
                return
            }

            emit(WebSearchEvent.SynthesizeStart(runId))
            val synthSources = finalPool.take(SYNTH_SOURCES)
            val synthTokens = if (mode.fetchPages) SYNTH_TOKENS_DEEP else SYNTH_TOKENS_SHALLOW
            val answer = directAnswerIfPossible(userQuery, finalPool)
                ?: runInference(
                    WebSearchPrompts.synthesize(userQuery, summary, synthSources, excerpts),
                    synthTokens,
                )
            emit(WebSearchEvent.Done(runId, answer, finalPool))
        } catch (ce: CancellationException) {
            _events.tryEmit(WebSearchEvent.Cancelled(runId, ce.message ?: "Cancelled"))
            throw ce
        } catch (t: Throwable) {
            _events.tryEmit(WebSearchEvent.Failed(runId, t.message ?: "Web search failed"))
        }
    }

    private suspend fun generateInitialQueries(userQuery: String, count: Int): List<String> {
        val raw = runInference(WebSearchPrompts.initialQueries(userQuery, count), QUERY_GEN_MAX_TOKENS)
        val parsed = parseQueries(raw, count, allowLooseLines = true)
        if (parsed.isEmpty()) return listOf(userQuery)
        return parsed
    }

    private data class Digest(
        val summary: String,
        val queries: List<String>,
        val coverage: Int,
    )

    private suspend fun runDigest(
        userQuery: String,
        priorSummary: String,
        findings: List<WebSearchHit>,
        excerpts: Map<String, String>,
        followUpCount: Int,
    ): Digest {
        val raw = runInference(
            WebSearchPrompts.roundDigest(userQuery, priorSummary, findings, excerpts, followUpCount),
            DIGEST_MAX_TOKENS,
        )
        val summary = sectionBetween(raw, "SUMMARY:", listOf("MISSING:", "QUERIES:", "COVERAGE:"))
            .ifBlank { priorSummary }
        val queriesBlock = sectionBetween(raw, "QUERIES:", listOf("COVERAGE:"))
        val queries = if (queriesBlock.isBlank() || queriesBlock.trim().equals("none", ignoreCase = true)) {
            emptyList()
        } else {
            parseQueries(queriesBlock, followUpCount, allowLooseLines = false)
                .filter { !it.equals("none", ignoreCase = true) }
        }
        val coverage = Regex("COVERAGE:?\\s*(\\d{1,3})", RegexOption.IGNORE_CASE)
            .find(raw)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(0, 100) ?: -1
        return Digest(summary.trim(), queries, coverage)
    }

    private fun sectionBetween(text: String, startHeader: String, endHeaders: List<String>): String {
        val startIdx = text.indexOf(startHeader, ignoreCase = true)
        if (startIdx < 0) return ""
        val from = startIdx + startHeader.length
        var end = text.length
        for (h in endHeaders) {
            val e = text.indexOf(h, from, ignoreCase = true)
            if (e in 0 until end) end = e
        }
        return text.substring(from, end).trim()
    }

    private fun parseQueries(raw: String, max: Int, allowLooseLines: Boolean): List<String> {
        if (raw.isBlank()) return emptyList()
        val out = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        for (line in raw.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val m = WebSearchPrompts.QUERY_LINE_REGEX.matchEntire(trimmed)
            val candidate = if (m != null) {
                m.groupValues[1].trim().trim('"', '\'')
            } else if (allowLooseLines) {
                val cleaned = trimmed.trim('"', '\'', '*', '_', '`')
                if (cleaned.endsWith(':')) continue
                if (cleaned.split(Regex("\\s+")).size < 2) continue
                if (cleaned.length > 120) continue
                cleaned
            } else {
                continue
            }
            if (candidate.length < 3) continue
            val key = candidate.lowercase()
            if (seen.add(key)) out.add(candidate)
            if (out.size >= max) break
        }
        return out
    }

    private fun extractUrl(text: String): String? =
        Regex("""https?://[^\s<>"']+""").find(text)?.value?.trimEnd('.', ',', ')', ']')

    private fun buildUrlFocusedQueries(userQuery: String, url: String, max: Int): List<String> {
        val domain = domainOf(url).orEmpty()
        val cleaned = userQuery.replace(url, "").trim().ifBlank { "summary" }
        return listOfNotNull(
            url,
            domain.takeIf { it.isNotBlank() }?.let { "site:$it $cleaned" },
            domain.takeIf { it.isNotBlank() }?.let { "$it ${pathWords(url)} $cleaned" },
        ).distinct().take(max)
    }

    private fun buildPlayStoreQueries(userQuery: String, max: Int): List<String> {
        val appName = userQuery
            .replace(Regex("""(?i)\b(give|me|the|direct|exact|download|link|url|for|to|on|google|play|store|app|please)\b"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .ifBlank { userQuery.trim() }
        return listOf(
            "$appName site:play.google.com/store/apps/details",
            "$appName Google Play Store",
            "$appName official Android app",
        ).distinct().take(max)
    }

    private fun filterAndRankHits(
        userQuery: String,
        requestedUrl: String?,
        hits: List<WebSearchHit>,
        limit: Int,
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
            .take(limit)
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
        private const val SEARCH_THROTTLE_MS = 1200L
        private const val QUERY_GEN_MAX_TOKENS = 256
        private const val DIGEST_MAX_TOKENS = 384
        private const val SYNTH_TOKENS_SHALLOW = 1024
        private const val SYNTH_TOKENS_DEEP = 1536
        private const val DIGEST_FINDINGS = 5
        private const val FINAL_SOURCES = 12
        private const val SYNTH_SOURCES = 6
        private const val RANK_POOL = 24
        private const val LOW_GAIN_THRESHOLD = 2
        private const val COVERAGE_STOP = 85
    }
}
