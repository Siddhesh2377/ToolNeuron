package com.dark.tool_neuron.model

import org.json.JSONArray
import org.json.JSONObject

data class WebSearchUiState(
    val phase: String = PHASE_PLAN,
    val userQuery: String = "",
    val queries: List<String> = emptyList(),
    val perQueryHits: Map<Int, List<WebSearchHit>> = emptyMap(),
    val currentQueryIndex: Int = -1,
    val answer: String = "",
    val sources: List<WebSearchHit> = emptyList(),
    val message: String = "",
) {
    fun isInFlight(): Boolean = phase !in TERMINAL_PHASES && phase != PHASE_STOPPING

    fun isStopping(): Boolean = phase == PHASE_STOPPING

    fun applyEvent(event: WebSearchEvent): WebSearchUiState {
        if (phase == PHASE_STOPPING) {
            return when (event) {
                is WebSearchEvent.Done -> copy(
                    phase = PHASE_DONE,
                    answer = event.answer,
                    sources = event.sources,
                )
                is WebSearchEvent.Cancelled -> copy(phase = PHASE_CANCELLED, message = event.reason)
                is WebSearchEvent.Failed -> copy(phase = PHASE_FAILED, message = event.message)
                else -> this
            }
        }
        return applyEventInternal(event)
    }

    private fun applyEventInternal(event: WebSearchEvent): WebSearchUiState = when (event) {
        is WebSearchEvent.Plan -> copy(phase = PHASE_PLAN, userQuery = event.userQuery)
        is WebSearchEvent.QueriesGenerated -> copy(
            phase = PHASE_QUERIES,
            queries = event.queries,
        )
        is WebSearchEvent.SearchStart -> copy(
            phase = PHASE_SEARCH,
            currentQueryIndex = event.queryIndex,
        )
        is WebSearchEvent.SearchHits -> copy(
            phase = PHASE_SEARCH,
            currentQueryIndex = event.queryIndex,
            perQueryHits = perQueryHits + (event.queryIndex to event.hits),
        )
        is WebSearchEvent.SynthesizeStart -> copy(phase = PHASE_SYNTHESIZE)
        is WebSearchEvent.Done -> copy(
            phase = PHASE_DONE,
            answer = event.answer,
            sources = event.sources,
        )
        is WebSearchEvent.Cancelled -> copy(phase = PHASE_CANCELLED, message = event.reason)
        is WebSearchEvent.Failed -> copy(phase = PHASE_FAILED, message = event.message)
    }

    fun toJson(): String {
        val obj = JSONObject()
        obj.put("phase", phase)
        if (userQuery.isNotEmpty()) obj.put("uq", userQuery)
        if (queries.isNotEmpty()) {
            val arr = JSONArray()
            queries.forEach { arr.put(it) }
            obj.put("qs", arr)
        }
        if (perQueryHits.isNotEmpty()) {
            val arr = JSONArray()
            perQueryHits.entries.sortedBy { it.key }.forEach { (idx, hits) ->
                val o = JSONObject().put("i", idx)
                val hitsArr = JSONArray()
                hits.forEach { h -> hitsArr.put(hitToJson(h)) }
                o.put("h", hitsArr)
                arr.put(o)
            }
            obj.put("pqh", arr)
        }
        if (currentQueryIndex >= 0) obj.put("cqi", currentQueryIndex)
        if (answer.isNotEmpty()) obj.put("a", answer)
        if (sources.isNotEmpty()) {
            val arr = JSONArray()
            sources.forEach { h -> arr.put(hitToJson(h)) }
            obj.put("src", arr)
        }
        if (message.isNotEmpty()) obj.put("m", message)
        return obj.toString()
    }

    private fun hitToJson(h: WebSearchHit): JSONObject = JSONObject()
        .put("t", h.title)
        .put("u", h.url)
        .put("s", h.snippet)
        .put("qi", h.sourceQueryIndex)

    companion object {
        const val PHASE_PLAN = "Plan"
        const val PHASE_QUERIES = "Queries"
        const val PHASE_SEARCH = "Search"
        const val PHASE_SYNTHESIZE = "Synthesize"
        const val PHASE_STOPPING = "Stopping"
        const val PHASE_DONE = "Done"
        const val PHASE_CANCELLED = "Cancelled"
        const val PHASE_FAILED = "Failed"

        private val TERMINAL_PHASES = setOf(PHASE_DONE, PHASE_CANCELLED, PHASE_FAILED)

        fun fromJson(json: String): WebSearchUiState {
            if (json.isBlank()) return WebSearchUiState()
            return runCatching {
                val o = JSONObject(json)
                val qsArr = o.optJSONArray("qs")
                val queries = if (qsArr != null) {
                    List(qsArr.length()) { i -> qsArr.optString(i) }.filter { it.isNotBlank() }
                } else emptyList()
                val pqhArr = o.optJSONArray("pqh")
                val perQueryHits = if (pqhArr != null) {
                    val map = mutableMapOf<Int, List<WebSearchHit>>()
                    for (i in 0 until pqhArr.length()) {
                        val entry = pqhArr.optJSONObject(i) ?: continue
                        val idx = entry.optInt("i", -1)
                        if (idx < 0) continue
                        val hitsArr = entry.optJSONArray("h") ?: continue
                        map[idx] = parseHits(hitsArr)
                    }
                    map.toMap()
                } else emptyMap()
                val srcArr = o.optJSONArray("src")
                val sources = if (srcArr != null) parseHits(srcArr) else emptyList()
                WebSearchUiState(
                    phase = o.optString("phase", PHASE_PLAN).ifBlank { PHASE_PLAN },
                    userQuery = o.optString("uq"),
                    queries = queries,
                    perQueryHits = perQueryHits,
                    currentQueryIndex = o.optInt("cqi", -1),
                    answer = o.optString("a"),
                    sources = sources,
                    message = o.optString("m"),
                )
            }.getOrDefault(WebSearchUiState())
        }

        private fun parseHits(arr: JSONArray): List<WebSearchHit> {
            val out = mutableListOf<WebSearchHit>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val title = o.optString("t")
                val url = o.optString("u")
                if (title.isBlank() && url.isBlank()) continue
                out.add(
                    WebSearchHit(
                        title = title,
                        url = url,
                        snippet = o.optString("s"),
                        sourceQueryIndex = o.optInt("qi", 0),
                    ),
                )
            }
            return out
        }
    }
}
