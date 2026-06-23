package com.dark.tool_neuron.model

sealed class WebSearchEvent {
    abstract val runId: String

    data class Plan(
        override val runId: String,
        val userQuery: String,
        val mode: String = "",
    ) : WebSearchEvent()

    data class RoundStart(
        override val runId: String,
        val round: Int,
        val maxRounds: Int,
        val focus: String = "",
    ) : WebSearchEvent()

    data class Status(
        override val runId: String,
        val message: String,
        val coverage: Int = -1,
    ) : WebSearchEvent()

    data class QueriesGenerated(
        override val runId: String,
        val queries: List<String>,
    ) : WebSearchEvent()

    data class SearchStart(
        override val runId: String,
        val queryIndex: Int,
        val query: String,
    ) : WebSearchEvent()

    data class SearchHits(
        override val runId: String,
        val queryIndex: Int,
        val hits: List<WebSearchHit>,
    ) : WebSearchEvent()

    data class SynthesizeStart(
        override val runId: String,
    ) : WebSearchEvent()

    data class Done(
        override val runId: String,
        val answer: String,
        val sources: List<WebSearchHit>,
    ) : WebSearchEvent()

    data class Cancelled(
        override val runId: String,
        val reason: String,
    ) : WebSearchEvent()

    data class Failed(
        override val runId: String,
        val message: String,
    ) : WebSearchEvent()
}

data class WebSearchHit(
    val title: String,
    val url: String,
    val snippet: String,
    val sourceQueryIndex: Int = 0,
)
