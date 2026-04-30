package com.dark.tool_neuron.model

sealed class ResearchEvent {
    abstract val runId: String

    data class Plan(
        override val runId: String,
        val question: String,
    ) : ResearchEvent()

    data class Search(
        override val runId: String,
        val iteration: Int,
        val maxIterations: Int,
        val query: String,
        val resultCount: Int,
    ) : ResearchEvent()

    data class FetchStart(
        override val runId: String,
        val iteration: Int,
        val maxIterations: Int,
        val urls: List<String>,
    ) : ResearchEvent()

    data class FetchProgress(
        override val runId: String,
        val iteration: Int,
        val maxIterations: Int,
        val url: String,
        val ok: Boolean,
    ) : ResearchEvent()

    data class Compress(
        override val runId: String,
        val iteration: Int,
        val maxIterations: Int,
        val rawBytes: Long,
        val compressedBytes: Long,
    ) : ResearchEvent()

    data class QuestionGen(
        override val runId: String,
        val iteration: Int,
        val maxIterations: Int,
        val questions: List<String>,
    ) : ResearchEvent()

    data class FinalStart(
        override val runId: String,
    ) : ResearchEvent()

    data class FinalProgress(
        override val runId: String,
        val tokensSoFar: Int,
        val previewTail: String,
    ) : ResearchEvent()

    data class Done(
        override val runId: String,
        val docId: String,
        val title: String,
        val summary: String,
    ) : ResearchEvent()

    data class Cancelled(
        override val runId: String,
        val reason: String,
    ) : ResearchEvent()

    data class Failed(
        override val runId: String,
        val message: String,
    ) : ResearchEvent()
}
