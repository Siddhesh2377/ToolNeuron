package com.dark.tool_neuron.model

import org.json.JSONArray
import org.json.JSONObject

data class ResearchUrlEntry(val url: String, val ok: Boolean?)

data class IterationStep(
    val iteration: Int,
    val query: String = "",
    val resultCount: Int = 0,
    val urls: List<ResearchUrlEntry> = emptyList(),
    val rawBytes: Long = 0L,
    val compressedBytes: Long = 0L,
    val questions: List<String> = emptyList(),
)

data class ResearchUiState(
    val phase: String = PHASE_PLAN,
    val iteration: Int = 0,
    val maxIterations: Int = 0,
    val query: String = "",
    val resultCount: Int = 0,
    val urls: List<ResearchUrlEntry> = emptyList(),
    val rawBytes: Long = 0L,
    val compressedBytes: Long = 0L,
    val questions: List<String> = emptyList(),
    val docId: String = "",
    val title: String = "",
    val summary: String = "",
    val message: String = "",
    val history: List<IterationStep> = emptyList(),
) {
    fun isInFlight(): Boolean = phase !in TERMINAL_PHASES && phase != PHASE_STOPPING

    fun isStopping(): Boolean = phase == PHASE_STOPPING

    fun applyEvent(event: ResearchEvent): ResearchUiState {
        if (phase == PHASE_STOPPING) {
            return when (event) {
                is ResearchEvent.Done -> copy(phase = PHASE_DONE, docId = event.docId, title = event.title, summary = event.summary)
                is ResearchEvent.Cancelled -> copy(phase = PHASE_CANCELLED, message = event.reason)
                is ResearchEvent.Failed -> copy(phase = PHASE_FAILED, message = event.message)
                else -> this
            }
        }
        return applyEventInternal(event)
    }

    private fun applyEventInternal(event: ResearchEvent): ResearchUiState = when (event) {
        is ResearchEvent.Plan -> copy(phase = PHASE_PLAN)
        is ResearchEvent.Search -> copy(
            phase = PHASE_SEARCH,
            iteration = event.iteration,
            maxIterations = event.maxIterations,
            query = event.query,
            resultCount = event.resultCount,
            history = upsertHistory(event.iteration) { step ->
                step.copy(query = event.query, resultCount = event.resultCount)
            },
        )
        is ResearchEvent.FetchStart -> copy(
            phase = PHASE_FETCH,
            iteration = event.iteration,
            maxIterations = event.maxIterations,
            urls = event.urls.map { ResearchUrlEntry(it, null) },
            history = upsertHistory(event.iteration) { step ->
                step.copy(urls = event.urls.map { ResearchUrlEntry(it, null) })
            },
        )
        is ResearchEvent.FetchProgress -> {
            val merged = if (urls.any { it.url == event.url }) {
                urls.map { if (it.url == event.url) it.copy(ok = event.ok) else it }
            } else {
                urls + ResearchUrlEntry(event.url, event.ok)
            }
            copy(
                phase = PHASE_FETCH,
                iteration = event.iteration,
                maxIterations = event.maxIterations,
                urls = merged,
                history = upsertHistory(event.iteration) { step ->
                    val stepUrls = if (step.urls.any { it.url == event.url }) {
                        step.urls.map { if (it.url == event.url) it.copy(ok = event.ok) else it }
                    } else {
                        step.urls + ResearchUrlEntry(event.url, event.ok)
                    }
                    step.copy(urls = stepUrls)
                },
            )
        }
        is ResearchEvent.Compress -> copy(
            phase = PHASE_COMPRESS,
            iteration = event.iteration,
            maxIterations = event.maxIterations,
            rawBytes = event.rawBytes,
            compressedBytes = event.compressedBytes,
            history = upsertHistory(event.iteration) { step ->
                step.copy(rawBytes = event.rawBytes, compressedBytes = event.compressedBytes)
            },
        )
        is ResearchEvent.QuestionGen -> copy(
            phase = PHASE_QUESTION_GEN,
            iteration = event.iteration,
            maxIterations = event.maxIterations,
            questions = event.questions,
            history = upsertHistory(event.iteration) { step ->
                step.copy(questions = event.questions)
            },
        )
        is ResearchEvent.FinalStart, is ResearchEvent.FinalProgress -> copy(phase = PHASE_FINAL)
        is ResearchEvent.Done -> copy(
            phase = PHASE_DONE,
            docId = event.docId,
            title = event.title,
            summary = event.summary,
        )
        is ResearchEvent.Cancelled -> copy(phase = PHASE_CANCELLED, message = event.reason)
        is ResearchEvent.Failed -> copy(phase = PHASE_FAILED, message = event.message)
    }

    private fun upsertHistory(
        iter: Int,
        transform: (IterationStep) -> IterationStep,
    ): List<IterationStep> {
        val existing = history.firstOrNull { it.iteration == iter }
        return if (existing == null) {
            history + transform(IterationStep(iteration = iter))
        } else {
            history.map { if (it.iteration == iter) transform(it) else it }
        }
    }

    fun toJson(): String {
        val obj = JSONObject()
        obj.put("phase", phase)
        if (iteration != 0) obj.put("iter", iteration)
        if (maxIterations != 0) obj.put("max", maxIterations)
        if (query.isNotEmpty()) obj.put("query", query)
        if (resultCount != 0) obj.put("rc", resultCount)
        if (urls.isNotEmpty()) {
            val arr = JSONArray()
            urls.forEach { e ->
                val o = JSONObject().put("u", e.url)
                if (e.ok != null) o.put("ok", e.ok)
                arr.put(o)
            }
            obj.put("urls", arr)
        }
        if (rawBytes != 0L) obj.put("rb", rawBytes)
        if (compressedBytes != 0L) obj.put("cb", compressedBytes)
        if (questions.isNotEmpty()) {
            val arr = JSONArray()
            questions.forEach { arr.put(it) }
            obj.put("qs", arr)
        }
        if (docId.isNotEmpty()) obj.put("did", docId)
        if (title.isNotEmpty()) obj.put("t", title)
        if (summary.isNotEmpty()) obj.put("s", summary)
        if (message.isNotEmpty()) obj.put("m", message)
        if (history.isNotEmpty()) {
            val arr = JSONArray()
            history.forEach { step ->
                val o = JSONObject().put("i", step.iteration)
                if (step.query.isNotEmpty()) o.put("q", step.query)
                if (step.resultCount > 0) o.put("rc", step.resultCount)
                if (step.urls.isNotEmpty()) {
                    val urlArr = JSONArray()
                    step.urls.forEach { e ->
                        val uo = JSONObject().put("u", e.url)
                        if (e.ok != null) uo.put("ok", e.ok)
                        urlArr.put(uo)
                    }
                    o.put("urls", urlArr)
                }
                if (step.rawBytes > 0) o.put("rb", step.rawBytes)
                if (step.compressedBytes > 0) o.put("cb", step.compressedBytes)
                if (step.questions.isNotEmpty()) {
                    val qsArr = JSONArray()
                    step.questions.forEach { qsArr.put(it) }
                    o.put("qs", qsArr)
                }
                arr.put(o)
            }
            obj.put("hist", arr)
        }
        return obj.toString()
    }

    companion object {
        const val PHASE_PLAN = "Plan"
        const val PHASE_SEARCH = "Search"
        const val PHASE_FETCH = "Fetch"
        const val PHASE_COMPRESS = "Compress"
        const val PHASE_QUESTION_GEN = "QuestionGen"
        const val PHASE_FINAL = "Final"
        const val PHASE_STOPPING = "Stopping"
        const val PHASE_DONE = "Done"
        const val PHASE_CANCELLED = "Cancelled"
        const val PHASE_FAILED = "Failed"

        private val TERMINAL_PHASES = setOf(PHASE_DONE, PHASE_CANCELLED, PHASE_FAILED)

        fun fromJson(json: String): ResearchUiState {
            if (json.isBlank()) return ResearchUiState()
            return runCatching {
                val o = JSONObject(json)
                val urlsArr = o.optJSONArray("urls")
                val urls = if (urlsArr != null) {
                    List(urlsArr.length()) { i ->
                        val it = urlsArr.optJSONObject(i) ?: return@List null
                        ResearchUrlEntry(
                            url = it.optString("u"),
                            ok = if (it.has("ok")) it.optBoolean("ok") else null,
                        )
                    }.filterNotNull()
                } else emptyList()
                val qArr = o.optJSONArray("qs")
                val questions = if (qArr != null) {
                    List(qArr.length()) { i -> qArr.optString(i) }.filter { it.isNotBlank() }
                } else emptyList()
                val histArr = o.optJSONArray("hist")
                val history = if (histArr != null) {
                    List(histArr.length()) { i ->
                        val ho = histArr.optJSONObject(i) ?: return@List null
                        val stepUrlsArr = ho.optJSONArray("urls")
                        val stepUrls = if (stepUrlsArr != null) {
                            List(stepUrlsArr.length()) { ui ->
                                val uo = stepUrlsArr.optJSONObject(ui) ?: return@List null
                                ResearchUrlEntry(
                                    url = uo.optString("u"),
                                    ok = if (uo.has("ok")) uo.optBoolean("ok") else null,
                                )
                            }.filterNotNull()
                        } else emptyList()
                        val stepQsArr = ho.optJSONArray("qs")
                        val stepQuestions = if (stepQsArr != null) {
                            List(stepQsArr.length()) { qi -> stepQsArr.optString(qi) }
                                .filter { it.isNotBlank() }
                        } else emptyList()
                        IterationStep(
                            iteration = ho.optInt("i", 0),
                            query = ho.optString("q"),
                            resultCount = ho.optInt("rc", 0),
                            urls = stepUrls,
                            rawBytes = ho.optLong("rb", 0L),
                            compressedBytes = ho.optLong("cb", 0L),
                            questions = stepQuestions,
                        )
                    }.filterNotNull()
                } else emptyList()

                ResearchUiState(
                    phase = o.optString("phase", PHASE_PLAN).ifBlank { PHASE_PLAN },
                    iteration = o.optInt("iter", 0),
                    maxIterations = o.optInt("max", 0),
                    query = o.optString("query"),
                    resultCount = o.optInt("rc", 0),
                    urls = urls,
                    rawBytes = o.optLong("rb", 0L),
                    compressedBytes = o.optLong("cb", 0L),
                    questions = questions,
                    docId = o.optString("did"),
                    title = o.optString("t"),
                    summary = o.optString("s"),
                    message = o.optString("m"),
                    history = history,
                )
            }.getOrDefault(ResearchUiState())
        }
    }
}
