package com.dark.tool_neuron.repo

import com.dark.tool_neuron.model.Citation

object RagCitationMatcher {

    private const val SNIPPET_LEN = 220
    private const val NGRAM = 4
    private const val POST_HOC_THRESHOLD = 3
    private val WHITESPACE = Regex("\\s+")
    private val EXPLICIT_CITATION = Regex("\\[(\\d+)]")
    private val WORD = Regex("[A-Za-z0-9_]+")

    fun match(response: String, chunks: List<RagChunk>): List<Citation> {
        if (chunks.isEmpty() || response.isBlank()) return emptyList()
        val explicit = parseExplicit(response, chunks.size)
        return chunks.mapIndexed { idx, chunk ->
            val byMarker = idx in explicit
            val byOverlap = !byMarker && hasNgramOverlap(response, chunk.text)
            chunk.toCitation(cited = byMarker || byOverlap)
        }
    }

    private fun parseExplicit(response: String, total: Int): Set<Int> {
        val out = mutableSetOf<Int>()
        EXPLICIT_CITATION.findAll(response).forEach { m ->
            val n = m.groupValues[1].toIntOrNull() ?: return@forEach
            val idx = n - 1
            if (idx in 0 until total) out += idx
        }
        return out
    }

    private fun hasNgramOverlap(response: String, chunk: String): Boolean {
        val responseTokens = WORD.findAll(response.lowercase()).map { it.value }.toList()
        if (responseTokens.size < NGRAM) return false
        val responseGrams = HashSet<String>(responseTokens.size)
        for (i in 0..responseTokens.size - NGRAM) {
            responseGrams += responseTokens.subList(i, i + NGRAM).joinToString(" ")
        }
        val chunkTokens = WORD.findAll(chunk.lowercase()).map { it.value }.toList()
        if (chunkTokens.size < NGRAM) return false
        var hits = 0
        for (i in 0..chunkTokens.size - NGRAM) {
            if (responseGrams.contains(chunkTokens.subList(i, i + NGRAM).joinToString(" "))) {
                hits++
                if (hits >= POST_HOC_THRESHOLD) return true
            }
        }
        return false
    }

    private fun RagChunk.toCitation(cited: Boolean): Citation = Citation(
        sourceId = sourceId,
        docId = docId,
        chunkIndex = chunkIndex,
        score = score,
        name = name,
        mimeType = mimeType,
        snippet = text.collapseWhitespace().take(SNIPPET_LEN),
        cited = cited,
    )

    private fun String.collapseWhitespace(): String = trim().replace(WHITESPACE, " ")
}
