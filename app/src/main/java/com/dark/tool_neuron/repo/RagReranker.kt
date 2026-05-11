package com.dark.tool_neuron.repo

import android.util.Log
import com.dark.tool_neuron.service.inference.InferenceClient
import com.dark.tool_neuron.service.inference.InferenceEvent
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RagReranker @Inject constructor() {

    suspend fun rerank(query: String, chunks: List<RagChunk>): List<RagChunk> {
        if (chunks.size < 2) return chunks
        if (!InferenceClient.isModelLoaded.value) return chunks

        val prompt = buildPrompt(query, chunks)
        val response = withTimeoutOrNull(TIMEOUT_MS) {
            collectGeneration(prompt, maxTokens = MAX_TOKENS)
        } ?: return chunks
        if (response.isBlank()) return chunks

        val scores = parseScores(response, chunks.size)
        if (scores.all { it == 0 }) return chunks

        val annotated = chunks.mapIndexed { idx, chunk -> chunk to (scores.getOrElse(idx) { 0 }) }
        val resorted = annotated
            .sortedWith(compareByDescending<Pair<RagChunk, Int>> { it.second }.thenByDescending { it.first.score })
            .map { it.first }
        Log.i(TAG, "LLM reranker scores: ${scores.joinToString(",")}")
        return resorted
    }

    private fun buildPrompt(query: String, chunks: List<RagChunk>): String = buildString {
        append("You are a relevance scorer. ")
        append("Rate how well each passage helps answer the query, from 1 (irrelevant) to 5 (perfect match).\n")
        append("Output exactly ${chunks.size} lines, no extra text. Format: '<index>: <score>'.\n\n")
        append("Query: ").append(query.trim()).append("\n\n")
        append("Passages:\n")
        chunks.forEachIndexed { i, chunk ->
            append("[${i + 1}] ")
            append(chunk.text.take(MAX_CHUNK_CHARS).replace("\n", " ").trim())
            append("\n")
        }
        append("\nScores:\n")
    }

    private suspend fun collectGeneration(prompt: String, maxTokens: Int): String {
        val sb = StringBuilder()
        InferenceClient.generate(prompt, maxTokens)
            .transformWhile { event ->
                emit(event)
                event !is InferenceEvent.Done && event !is InferenceEvent.Error
            }
            .collect { event ->
                if (event is InferenceEvent.Token) sb.append(event.text)
            }
        return sb.toString()
    }

    private fun parseScores(response: String, expected: Int): List<Int> {
        val out = MutableList(expected) { 0 }
        SCORE_LINE.findAll(response).forEach { m ->
            val idx = m.groupValues[1].toIntOrNull()?.minus(1) ?: return@forEach
            val score = m.groupValues[2].toIntOrNull()?.coerceIn(1, 5) ?: return@forEach
            if (idx in 0 until expected) out[idx] = score
        }
        return out
    }

    companion object {
        private const val TAG = "RagReranker"
        private const val TIMEOUT_MS = 15_000L
        private const val MAX_TOKENS = 256
        private const val MAX_CHUNK_CHARS = 360
        private val SCORE_LINE = Regex("(\\d+)\\s*[:.\\-]\\s*(\\d)")
    }
}
