package com.dark.tool_neuron.repo

import android.util.Log
import com.dark.tool_neuron.service.inference.InferenceClient
import com.dark.tool_neuron.service.inference.InferenceEvent
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RagDocSummarizer @Inject constructor() {

    suspend fun summarize(name: String, mimeType: String, sample: String): String? {
        if (!InferenceClient.isModelLoaded.value) return null
        if (sample.isBlank()) return null

        val prompt = buildPrompt(name, mimeType, sample)
        val response = withTimeoutOrNull(TIMEOUT_MS) {
            collectGeneration(prompt, MAX_TOKENS)
        } ?: return null
        val cleaned = response.trim().take(MAX_SUMMARY_CHARS)
        return cleaned.ifBlank { null }
    }

    private fun buildPrompt(name: String, mimeType: String, sample: String): String = buildString {
        append("Write a single sentence (under 60 words) summarizing what this document is about. ")
        append("State the topic, format, and primary content. No preamble, no markdown, no quotes.\n\n")
        append("Filename: ").append(name).append("\n")
        append("Mime: ").append(mimeType).append("\n\n")
        append("First section:\n")
        append(sample.take(SAMPLE_CHARS).replace("\n", " ").trim())
        append("\n\nSummary: ")
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
        Log.i(TAG, "summary length=${sb.length}")
        return sb.toString()
    }

    companion object {
        private const val TAG = "RagDocSummarizer"
        private const val TIMEOUT_MS = 30_000L
        private const val MAX_TOKENS = 200
        private const val MAX_SUMMARY_CHARS = 320
        private const val SAMPLE_CHARS = 2000
    }
}
