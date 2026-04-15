package com.memoryvault.core

object TextTokenizer {
    private val STOP_WORDS = setOf(
        "a", "an", "and", "are", "as", "at", "be", "by", "for", "from",
        "has", "he", "in", "is", "it", "its", "of", "on", "that", "the",
        "to", "was", "will", "with", "the", "this", "but", "they", "have"
    )

    fun tokenize(text: String, removeStopWords: Boolean = true): List<String> {
        val normalized = text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .trim()

        if (normalized.isEmpty()) return emptyList()

        val tokens = normalized.split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .filter { it.length > 1 }

        return if (removeStopWords) {
            tokens.filter { it !in STOP_WORDS }
        } else {
            tokens
        }
    }
}
