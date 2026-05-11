package com.dark.tool_neuron.repo

import android.util.Log
import com.dark.tool_neuron.service.inference.InferenceClient
import com.dark.tool_neuron.service.inference.InferenceEvent
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RagQueryRewriter @Inject constructor() {

    suspend fun generateVariants(query: String, count: Int = 3): List<String> {
        if (query.isBlank() || count <= 0) return emptyList()
        if (!InferenceClient.isModelLoaded.value) return emptyList()

        val prompt = buildPrompt(query, count)
        val response = withTimeoutOrNull(TIMEOUT_MS) {
            collectGeneration(prompt, maxTokens = MAX_TOKENS)
        } ?: return emptyList()

        val variants = parseVariants(response, query)
            .filter { it.isNotBlank() && !it.equals(query, ignoreCase = true) }
            .distinct()
            .take(count)
        Log.i(TAG, "multi-query variants: $variants")
        return variants
    }

    private fun buildPrompt(query: String, count: Int): String = buildString {
        append("Rewrite the user's search query into $count distinct alternative phrasings ")
        append("that capture the same intent but use different words. ")
        append("Output exactly $count lines, each starting with '- '. No numbering, no commentary.\n\n")
        append("Query: ").append(query.trim()).append("\n\n")
        append("Variants:\n")
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

    private fun parseVariants(response: String, original: String): List<String> {
        return response.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line ->
                line.removePrefix("-").removePrefix("•").removePrefix("*")
                    .trimStart()
                    .trimStart { it.isDigit() || it == '.' || it == ')' || it == ' ' }
                    .trim()
            }
            .filter { it.length in 4..256 }
            .toList()
    }

    companion object {
        private const val TAG = "RagQueryRewriter"
        private const val TIMEOUT_MS = 8_000L
        private const val MAX_TOKENS = 200
    }
}
