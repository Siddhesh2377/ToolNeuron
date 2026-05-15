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

            val queries = generateQueries(userQuery).take(MAX_QUERIES)
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

            if (allHits.isEmpty()) {
                emit(WebSearchEvent.Failed(runId, "No search results found"))
                return
            }

            emit(WebSearchEvent.SynthesizeStart(runId))
            val answer = runInference(WebSearchPrompts.synthesize(userQuery, allHits), SYNTHESIZE_MAX_TOKENS)
            emit(WebSearchEvent.Done(runId, answer, allHits))
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
    }
}
