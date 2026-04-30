package com.dark.tool_neuron.viewmodel

import com.dark.tool_neuron.data.AppPreferences
import com.dark.tool_neuron.model.DocSource
import com.dark.tool_neuron.model.FetchedDoc
import com.dark.tool_neuron.model.IterationLogEntry
import com.dark.tool_neuron.model.ResearchContext
import com.dark.tool_neuron.model.ResearchDocument
import com.dark.tool_neuron.model.ResearchEvent
import com.dark.tool_neuron.model.ResearchPhase
import com.dark.tool_neuron.repo.ResearchRepository
import com.dark.tool_neuron.repo.research.DdgSearch
import com.dark.tool_neuron.repo.research.HtmlReadability
import com.dark.tool_neuron.repo.research.ResearchModelClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

@Singleton
class ResearchCoordinator @Inject constructor(
    private val ddgSearch: DdgSearch,
    private val client: ResearchModelClient,
    private val repository: ResearchRepository,
    private val prefs: AppPreferences,
) {
    private val _events = MutableSharedFlow<ResearchEvent>(extraBufferCapacity = 128)
    val events: SharedFlow<ResearchEvent> = _events.asSharedFlow()

    private val _activeRuns = MutableStateFlow<Set<String>>(emptySet())
    val activeRuns: StateFlow<Set<String>> = _activeRuns.asStateFlow()

    private val jobs = mutableMapOf<String, Job>()

    fun start(
        scope: CoroutineScope,
        chatId: String,
        messageId: String,
        question: String,
        modelId: String,
        modelName: String,
    ): String {
        val runId = "run_" + UUID.randomUUID().toString()
        markActive(runId, true)
        val job = scope.launch(Dispatchers.IO) {
            run(
                runId = runId,
                chatId = chatId,
                messageId = messageId,
                question = question,
                modelId = modelId,
                modelName = modelName,
            )
        }
        jobs[runId] = job
        job.invokeOnCompletion {
            jobs.remove(runId)
            markActive(runId, false)
        }
        return runId
    }

    fun cancel(runId: String, reason: String = "Cancelled") {
        val job = jobs[runId] ?: return
        job.cancel(CancellationException(reason))
    }

    fun cancelAll(reason: String = "Cancelled") {
        jobs.keys.toList().forEach { cancel(it, reason) }
    }

    private suspend fun run(
        runId: String,
        chatId: String,
        messageId: String,
        question: String,
        modelId: String,
        modelName: String,
    ) {
        val startedAt = System.currentTimeMillis()
        val maxIterations = prefs.researchMaxIterations
        val maxQuestionsPerIter = prefs.researchMaxQuestions
        val resultsPerSearch = prefs.researchResultsPerSearch

        val allCompressed = StringBuilder()
        val allFetched = mutableListOf<FetchedDoc>()
        val seenUrls = mutableSetOf<String>()
        val previousQuestions = mutableListOf<String>()
        val iterationLog = mutableListOf<IterationLogEntry>()
        var totalFetchedBytes = 0L
        var iterationsUsed = 0
        var currentQueries = listOf(question)

        repository.saveRunSnapshot(
            runId = runId,
            question = question,
            phase = ResearchPhase.Plan,
            startedAt = startedAt,
        )
        emit(ResearchEvent.Plan(runId, question))

        try {
            for (iter in 1..maxIterations) {
                if (currentQueries.isEmpty()) break
                iterationsUsed = iter

                val perIterFetched = mutableListOf<FetchedDoc>()

                for (query in currentQueries) {
                    val searchResult = ddgSearch.search(query, resultsPerSearch).getOrElse {
                        emit(ResearchEvent.Search(runId, iter, maxIterations, query, 0))
                        continue
                    }
                    emit(ResearchEvent.Search(runId, iter, maxIterations, query, searchResult.size))
                    repository.saveRunSnapshot(
                        runId, question, ResearchPhase.Search, startedAt,
                        iterationLogJson = serializeIterationLog(iterationLog),
                    )

                    val urls = searchResult.map { it.url }.filter { seenUrls.add(it) }
                    if (urls.isEmpty()) continue
                    emit(ResearchEvent.FetchStart(runId, iter, maxIterations, urls))
                    repository.saveRunSnapshot(
                        runId, question, ResearchPhase.Fetch, startedAt,
                        iterationLogJson = serializeIterationLog(iterationLog),
                    )

                    val titlesByUrl = searchResult.associateBy({ it.url }, { it.title })
                    val fetched = fetchAll(urls, iter, titlesByUrl, runId, maxIterations)
                    perIterFetched.addAll(fetched.filter { it.ok && it.extractedText.isNotBlank() })
                    totalFetchedBytes += fetched.sumOf { it.byteCount }
                }

                if (perIterFetched.isNotEmpty()) {
                    emit(
                        ResearchEvent.Compress(
                            runId, iter, maxIterations,
                            rawBytes = perIterFetched.sumOf { it.byteCount },
                            compressedBytes = 0L,
                        ),
                    )
                    repository.saveRunSnapshot(
                        runId, question, ResearchPhase.Compress, startedAt,
                        iterationLogJson = serializeIterationLog(iterationLog),
                    )
                    val compressed = client.compress(perIterFetched, question)
                    if (compressed.isNotBlank()) {
                        allCompressed.append(compressed).append("\n\n")
                    }
                    allFetched.addAll(perIterFetched)
                    emit(
                        ResearchEvent.Compress(
                            runId, iter, maxIterations,
                            rawBytes = perIterFetched.sumOf { it.byteCount },
                            compressedBytes = compressed.length.toLong(),
                        ),
                    )
                }

                if (iter == maxIterations) break

                emit(ResearchEvent.QuestionGen(runId, iter, maxIterations, emptyList()))
                repository.saveRunSnapshot(
                    runId, question, ResearchPhase.QuestionGen, startedAt,
                    iterationLogJson = serializeIterationLog(iterationLog),
                )
                val ctx = ResearchContext(
                    originalQuestion = question,
                    accumulatedSummary = allCompressed.toString(),
                    previousQuestions = previousQuestions.toList(),
                    iteration = iter,
                )
                val followUps = client.generateQuestions(ctx, maxQuestionsPerIter)
                emit(ResearchEvent.QuestionGen(runId, iter, maxIterations, followUps))
                iterationLog.add(IterationLogEntry(iter, followUps))
                if (followUps.isEmpty()) break
                previousQuestions.addAll(followUps)
                currentQueries = followUps
            }

            emit(ResearchEvent.FinalStart(runId))
            repository.saveRunSnapshot(
                runId, question, ResearchPhase.Final, startedAt,
                iterationLogJson = serializeIterationLog(iterationLog),
            )

            val durationSoFar = System.currentTimeMillis() - startedAt
            val structured = client.finalDocument(
                allCompressed = allCompressed.toString(),
                question = question,
                sources = allFetched,
                iterationsUsed = iterationsUsed,
                modelName = modelName,
                totalFetchedBytes = totalFetchedBytes,
                durationMs = durationSoFar,
            ).copy(
                iterationLog = iterationLog,
                sources = allFetched.distinctBy { it.url }.map {
                    DocSource(it.url, it.title.ifBlank { it.url }, it.iteration)
                },
            )

            val finishedAt = System.currentTimeMillis()
            val docId = "doc_" + UUID.randomUUID().toString()
            val doc = ResearchDocument(
                docId = docId,
                title = structured.title,
                originChatId = chatId,
                originMessageId = messageId,
                question = question,
                structured = structured.copy(durationMs = finishedAt - startedAt),
                createdAt = finishedAt,
                durationMs = finishedAt - startedAt,
                modelId = modelId,
                iterationsUsed = iterationsUsed,
            )
            repository.saveDocument(doc)
            repository.saveRunSnapshot(
                runId = runId,
                question = question,
                phase = ResearchPhase.Done,
                startedAt = startedAt,
                finishedAt = finishedAt,
                docId = docId,
                iterationLogJson = serializeIterationLog(iterationLog),
            )
            emit(ResearchEvent.Done(runId, docId, structured.title, structured.summary))
        } catch (ce: CancellationException) {
            val reason = ce.message ?: "Cancelled"
            runCatching {
                repository.saveRunSnapshot(
                    runId = runId,
                    question = question,
                    phase = ResearchPhase.Cancelled,
                    startedAt = startedAt,
                    finishedAt = System.currentTimeMillis(),
                    cancelReason = reason,
                    iterationLogJson = serializeIterationLog(iterationLog),
                )
            }
            _events.tryEmit(ResearchEvent.Cancelled(runId, reason))
            throw ce
        } catch (t: Throwable) {
            runCatching {
                repository.saveRunSnapshot(
                    runId = runId,
                    question = question,
                    phase = ResearchPhase.Failed,
                    startedAt = startedAt,
                    finishedAt = System.currentTimeMillis(),
                    cancelReason = t.message ?: "Failed",
                    iterationLogJson = serializeIterationLog(iterationLog),
                )
            }
            _events.tryEmit(ResearchEvent.Failed(runId, t.message ?: "Research failed"))
        }
    }

    private suspend fun fetchAll(
        urls: List<String>,
        iter: Int,
        titles: Map<String, String>,
        runId: String,
        maxIterations: Int,
    ): List<FetchedDoc> = coroutineScope {
        val sem = Semaphore(MAX_CONCURRENT_FETCH)
        urls.map { url ->
            async {
                sem.withPermit {
                    val titleHint = titles[url].orEmpty()
                    val res = ddgSearch.fetch(url, FETCH_TIMEOUT_MS)
                    val fd = res.fold(
                        onSuccess = { html ->
                            val extracted = HtmlReadability.extract(html)
                            FetchedDoc(
                                url = url,
                                title = extracted.title.ifBlank { titleHint },
                                extractedText = extracted.text,
                                byteCount = html.length.toLong(),
                                iteration = iter,
                                ok = true,
                            )
                        },
                        onFailure = {
                            FetchedDoc(
                                url = url,
                                title = titleHint,
                                extractedText = "",
                                byteCount = 0,
                                iteration = iter,
                                ok = false,
                                error = it.message,
                            )
                        },
                    )
                    emit(ResearchEvent.FetchProgress(runId, iter, maxIterations, url, fd.ok))
                    fd
                }
            }
        }.awaitAll()
    }

    private suspend fun emit(event: ResearchEvent) {
        withContext(Dispatchers.Default) { _events.emit(event) }
    }

    private fun markActive(runId: String, active: Boolean) {
        _activeRuns.value = if (active) _activeRuns.value + runId else _activeRuns.value - runId
    }

    private fun serializeIterationLog(log: List<IterationLogEntry>): String {
        val arr = JSONArray()
        log.forEach { entry ->
            val q = JSONArray()
            entry.questions.forEach { q.put(it) }
            arr.put(JSONObject().put("iteration", entry.iteration).put("questions", q))
        }
        return arr.toString()
    }

    companion object {
        private const val MAX_CONCURRENT_FETCH = 3
        private const val FETCH_TIMEOUT_MS = 15000
    }
}
