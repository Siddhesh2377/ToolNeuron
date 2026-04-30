package com.dark.tool_neuron.repo.research

import com.dark.gguf_lib.TextDigest
import com.dark.tool_neuron.model.DocSection
import com.dark.tool_neuron.model.DocSource
import com.dark.tool_neuron.model.FetchedDoc
import com.dark.tool_neuron.model.IterationLogEntry
import com.dark.tool_neuron.model.ResearchContext
import com.dark.tool_neuron.model.StructuredDoc
import com.dark.tool_neuron.service.inference.InferenceClient
import com.dark.tool_neuron.service.inference.InferenceEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatLlmResearchClient @Inject constructor() : ResearchModelClient {

    override suspend fun generateQuestions(context: ResearchContext, max: Int): List<String> {
        if (!InferenceClient.isModelLoaded.first { true }) return emptyList()
        val prompt = ResearchPrompts.questionGen(context, max)
        val raw = runInference(prompt, maxTokens = QUESTION_GEN_MAX_TOKENS)
        return parseQuestions(raw, context.previousQuestions, max)
    }

    override suspend fun compress(blobs: List<FetchedDoc>, question: String): String {
        if (blobs.isEmpty()) return ""
        val parts = StringBuilder()
        val opts = TextDigest.Options(targetTokens = DIGEST_TARGET_TOKENS)
        for (blob in blobs) {
            if (blob.extractedText.isBlank()) continue
            val digest = TextDigest.compress(
                text = blob.extractedText,
                query = question,
                options = opts,
            )
            if (digest.isBlank()) continue
            val title = safeMdInline(blob.title.take(120).ifBlank { blob.url })
            parts.append("### [")
            parts.append(title)
            parts.append("](")
            parts.append(blob.url)
            parts.append(")\n\n")
            parts.append(digest.trim())
            parts.append("\n\n")
        }
        return parts.toString().trim()
    }

    private fun safeMdInline(s: String): String =
        s.replace('\n', ' ')
            .replace('\r', ' ')
            .replace('[', ' ')
            .replace(']', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()

    override suspend fun finalDocument(
        allCompressed: String,
        question: String,
        sources: List<FetchedDoc>,
        iterationsUsed: Int,
        modelName: String,
        totalFetchedBytes: Long,
        durationMs: Long,
    ): StructuredDoc {
        val fallback = fallbackDocument(
            question = question,
            allCompressed = allCompressed,
            sources = sources,
            iterationsUsed = iterationsUsed,
            modelName = modelName,
            totalFetchedBytes = totalFetchedBytes,
            durationMs = durationMs,
        )
        if (!InferenceClient.isModelLoaded.first { true }) return fallback

        val prompt = ResearchPrompts.finalDocument(allCompressed, question, sources)
        val raw = runInference(prompt, maxTokens = FINAL_MAX_TOKENS)
        val parsed = parseFinalJson(raw) ?: return fallback

        val title = parsed.optString("title").ifBlank { question.take(80) }
        val summary = parsed.optString("summary")
        val sectionsJson = parsed.optJSONArray("sections")
        val sections = if (sectionsJson != null) {
            List(sectionsJson.length()) { idx ->
                val o = sectionsJson.optJSONObject(idx) ?: return@List null
                val heading = o.optString("heading").trim()
                val body = o.optString("body").trim()
                if (heading.isEmpty() && body.isEmpty()) null else DocSection(heading, body)
            }.filterNotNull()
        } else emptyList()

        return fallback.copy(
            title = title,
            summary = summary.ifBlank { fallback.summary },
            sections = sections.ifEmpty { fallback.sections },
        )
    }

    private fun fallbackDocument(
        question: String,
        allCompressed: String,
        sources: List<FetchedDoc>,
        iterationsUsed: Int,
        modelName: String,
        totalFetchedBytes: Long,
        durationMs: Long,
    ): StructuredDoc {
        val uniqueSources = sources.distinctBy { it.url }
        val syntheticSummary = if (allCompressed.isBlank()) {
            "No findings could be extracted for *${question}*."
        } else {
            "Compiled extractive findings from ${uniqueSources.size} source" +
                (if (uniqueSources.size == 1) "" else "s") +
                " across $iterationsUsed iteration" +
                (if (iterationsUsed == 1) "" else "s") +
                " for the question: *${question}*."
        }
        return StructuredDoc(
            title = question.take(80).ifBlank { "Research" },
            summary = syntheticSummary,
            sections = if (allCompressed.isBlank()) emptyList()
            else listOf(DocSection("Findings", allCompressed)),
            sources = uniqueSources.map {
                DocSource(it.url, it.title.ifBlank { it.url }, it.iteration)
            },
            iterationLog = emptyList(),
            modelName = modelName,
            iterationsUsed = iterationsUsed,
            totalFetchedBytes = totalFetchedBytes,
            durationMs = durationMs,
        )
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

    private fun parseQuestions(raw: String, seen: List<String>, max: Int): List<String> {
        if (raw.isBlank()) return emptyList()
        val seenLower = seen.map { it.lowercase().trim() }.toSet()
        val out = mutableListOf<String>()
        for (rawLine in raw.lines()) {
            val cleaned = rawLine.trim()
                .removePrefix("-").removePrefix("*").trim()
                .removePrefix("•").trim()
                .removePrefix("Q:").trim()
            if (cleaned.isEmpty()) continue
            val withoutNumber = cleaned.replace(Regex("^\\d+[).:\\s-]+"), "").trim()
            if (withoutNumber.length < 4) continue
            val key = withoutNumber.lowercase()
            if (key in seenLower) continue
            if (out.any { it.lowercase() == key }) continue
            out.add(withoutNumber)
            if (out.size >= max) break
        }
        return out
    }

    private fun parseFinalJson(raw: String): JSONObject? {
        if (raw.isBlank()) return null
        val cleaned = stripFences(raw).trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(cleaned.substring(start, end + 1)) }.getOrNull()
    }

    private fun stripFences(input: String): String {
        var s = input.trim()
        if (s.startsWith("```")) {
            val nl = s.indexOf('\n')
            if (nl > 0) s = s.substring(nl + 1)
        }
        if (s.endsWith("```")) s = s.removeSuffix("```").trim()
        return s
    }

    @Suppress("unused")
    private fun JSONArray.size(): Int = length()

    companion object {
        private const val QUESTION_GEN_MAX_TOKENS = 256
        private const val FINAL_MAX_TOKENS = 1500
        private const val DIGEST_TARGET_TOKENS = 200
    }
}
