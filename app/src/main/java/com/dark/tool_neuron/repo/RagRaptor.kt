package com.dark.tool_neuron.repo

import com.dark.tool_neuron.service.inference.InferenceClient
import com.dark.tool_neuron.service.inference.InferenceEvent
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

data class RaptorTree(val levels: List<List<String>>)

@Singleton
class RagRaptor @Inject constructor() {

    suspend fun buildTree(
        sourceText: String,
        docName: String,
        onProgress: (level: Int, completed: Int, total: Int) -> Unit = { _, _, _ -> },
    ): RaptorTree? {
        if (!InferenceClient.isModelLoaded.value) return null

        val baseChunks = chunk(sourceText)
        if (baseChunks.size < MIN_CHUNKS_FOR_TREE) return null

        val levels = mutableListOf<List<String>>()
        levels += baseChunks

        var current: List<String> = baseChunks
        var levelIdx = 1
        while (current.size > STOP_AT_NODES && levelIdx <= MAX_DEPTH) {
            val groups = adjacencyClusters(current, CLUSTER_SIZE)
            val summaries = mutableListOf<String>()
            groups.forEachIndexed { i, group ->
                onProgress(levelIdx, i, groups.size)
                val summary = summarizeGroup(group, docName, levelIdx) ?: return@forEachIndexed
                summaries += summary
            }
            onProgress(levelIdx, groups.size, groups.size)
            if (summaries.isEmpty() || summaries.size >= current.size) break
            levels += summaries
            current = summaries
            levelIdx += 1
        }

        return if (levels.size > 1) RaptorTree(levels) else null
    }

    private suspend fun summarizeGroup(
        chunks: List<String>,
        docName: String,
        levelIdx: Int,
    ): String? {
        val joined = chunks.withIndex().joinToString("\n\n") { (i, c) ->
            "[chunk ${i + 1}]\n${c.trim()}"
        }
        val prompt = buildPrompt(joined, docName, levelIdx)
        val collected = StringBuilder()
        val ok = withTimeoutOrNull(SUMMARY_TIMEOUT_MS) {
            InferenceClient.generate(prompt, SUMMARY_MAX_TOKENS)
                .transformWhile { event ->
                    emit(event)
                    event !is InferenceEvent.Done && event !is InferenceEvent.Error
                }
                .collect { event ->
                    when (event) {
                        is InferenceEvent.Token -> collected.append(event.text)
                        is InferenceEvent.Error -> return@collect
                        else -> Unit
                    }
                }
            true
        }
        if (ok != true) return null
        val text = sanitize(collected.toString())
        return text.takeIf { it.length >= MIN_SUMMARY_CHARS }
    }

    private fun buildPrompt(content: String, docName: String, levelIdx: Int): String {
        val role = if (levelIdx == 1) {
            "You are summarizing consecutive passages from \"$docName\". Produce a single concise summary that preserves key claims, names, numbers, and topics from these passages."
        } else {
            "You are summarizing intermediate summaries from \"$docName\" at abstraction level $levelIdx. Produce a single higher-level summary that preserves the most important themes, claims, and entities."
        }
        return """
$role

Output 100-200 words. No preamble, no headings, no bullet points. Plain prose only.

Source passages:
$content

Summary:
""".trimIndent()
    }

    private fun sanitize(raw: String): String {
        val trimmed = raw.trim()
        val withoutFence = trimmed
            .removePrefix("```").removeSuffix("```")
            .trim()
        val withoutLabel = withoutFence
            .lineSequence()
            .dropWhile { it.isBlank() || it.trim().startsWith("Summary", ignoreCase = true) && it.contains(":") }
            .joinToString("\n")
            .trim()
        return withoutLabel
    }

    private fun adjacencyClusters(items: List<String>, target: Int): List<List<String>> {
        if (items.isEmpty()) return emptyList()
        val groupCount = ((items.size + target - 1) / target).coerceAtLeast(1)
        val baseSize = items.size / groupCount
        val remainder = items.size % groupCount
        val out = mutableListOf<List<String>>()
        var idx = 0
        repeat(groupCount) { g ->
            val size = baseSize + if (g < remainder) 1 else 0
            if (size <= 0) return@repeat
            out += items.subList(idx, idx + size).toList()
            idx += size
        }
        return out
    }

    private fun chunk(text: String): List<String> {
        val cleaned = text.replace("\r\n", "\n").trim()
        if (cleaned.isEmpty()) return emptyList()
        if (cleaned.length <= TARGET_CHUNK_CHARS) return listOf(cleaned)
        val out = mutableListOf<String>()
        recursiveSplit(cleaned, SEPARATORS, 0, out)
        return out.filter { it.length >= MIN_CHUNK_CHARS }
    }

    private fun recursiveSplit(
        text: String,
        seps: List<String>,
        depth: Int,
        out: MutableList<String>,
    ) {
        val t = text.trim()
        if (t.isEmpty()) return
        if (t.length <= TARGET_CHUNK_CHARS) { out += t; return }
        if (depth >= seps.size) {
            var i = 0
            while (i < t.length) {
                val end = (i + TARGET_CHUNK_CHARS).coerceAtMost(t.length)
                out += t.substring(i, end)
                i = end
            }
            return
        }
        val sep = seps[depth]
        val parts = if (sep.isEmpty()) listOf(t) else t.split(sep)
        val acc = StringBuilder()
        for (part in parts) {
            val piece = if (sep.isEmpty()) part else part + sep
            if (acc.length + piece.length > TARGET_CHUNK_CHARS && acc.isNotEmpty()) {
                val flushed = acc.toString().trim()
                if (flushed.length > TARGET_CHUNK_CHARS) {
                    recursiveSplit(flushed, seps, depth + 1, out)
                } else {
                    out += flushed
                }
                acc.setLength(0)
            }
            if (piece.length > TARGET_CHUNK_CHARS) {
                if (acc.isNotEmpty()) {
                    val flushed = acc.toString().trim()
                    out += flushed
                    acc.setLength(0)
                }
                recursiveSplit(piece, seps, depth + 1, out)
            } else {
                acc.append(piece)
            }
        }
        val tail = acc.toString().trim()
        if (tail.isNotEmpty()) out += tail
    }

    private companion object {
        const val TARGET_CHUNK_CHARS = 1024
        const val MIN_CHUNK_CHARS = 80
        const val CLUSTER_SIZE = 8
        const val STOP_AT_NODES = 4
        const val MAX_DEPTH = 5
        const val MIN_CHUNKS_FOR_TREE = 6
        const val SUMMARY_MAX_TOKENS = 320
        const val SUMMARY_TIMEOUT_MS = 60_000L
        const val MIN_SUMMARY_CHARS = 80

        val SEPARATORS = listOf("\n\n", "\n", ". ", "! ", "? ", "; ", ", ", " ", "")
    }
}
