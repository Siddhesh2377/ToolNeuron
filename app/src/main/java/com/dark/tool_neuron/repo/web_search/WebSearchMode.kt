package com.dark.tool_neuron.repo.web_search

enum class WebSearchMode(
    val label: String,
    val maxRounds: Int,
    val maxQueries: Int,
    val initialQueries: Int,
    val followUpQueries: Int,
    val resultsPerQuery: Int,
    val fetchPages: Boolean,
    val pagesPerRound: Int,
    val timeBudgetMs: Long,
) {
    QUICK("Quick", maxRounds = 1, maxQueries = 3, initialQueries = 3, followUpQueries = 0, resultsPerQuery = 6, fetchPages = false, pagesPerRound = 0, timeBudgetMs = 90_000),
    NORMAL("Normal", maxRounds = 3, maxQueries = 10, initialQueries = 4, followUpQueries = 3, resultsPerQuery = 8, fetchPages = false, pagesPerRound = 0, timeBudgetMs = 240_000),
    DEEP("Deep", maxRounds = 10, maxQueries = 40, initialQueries = 5, followUpQueries = 4, resultsPerQuery = 8, fetchPages = true, pagesPerRound = 6, timeBudgetMs = 300_000),
    EXHAUSTIVE("Exhaustive", maxRounds = 20, maxQueries = 80, initialQueries = 6, followUpQueries = 5, resultsPerQuery = 8, fetchPages = true, pagesPerRound = 8, timeBudgetMs = 600_000);

    val key: String get() = name.lowercase()

    companion object {
        const val AUTO_KEY = "auto"

        val SELECTABLE_KEYS = listOf(AUTO_KEY, "quick", "normal", "deep", "exhaustive")

        fun fromPref(value: String?): WebSearchMode? = when (value?.trim()?.lowercase()) {
            "quick" -> QUICK
            "normal" -> NORMAL
            "deep" -> DEEP
            "exhaustive" -> EXHAUSTIVE
            else -> null
        }

        fun sanitizePref(value: String?): String {
            val v = value?.trim()?.lowercase()
            return if (v != null && v in SELECTABLE_KEYS) v else AUTO_KEY
        }

        fun labelForKey(key: String): String =
            if (key.trim().equals(AUTO_KEY, ignoreCase = true)) "Auto" else (fromPref(key)?.label ?: "Auto")

        fun resolve(rawText: String, default: WebSearchMode? = null): Resolved {
            val text = rawText.trim()
            val lower = text.lowercase()

            forcedPrefix(lower, "/exhaustive")?.let { return Resolved(EXHAUSTIVE, strip(text, it)) }
            forcedPrefix(lower, "/research")?.let { return Resolved(DEEP, strip(text, it)) }
            forcedPrefix(lower, "/deep")?.let { return Resolved(DEEP, strip(text, it)) }
            forcedPrefix(lower, "/search")?.let {
                val q = strip(text, it)
                return Resolved(default ?: classifyAuto(q), q)
            }

            return Resolved(default ?: classifyAuto(text), text)
        }

        private fun classifyAuto(query: String): WebSearchMode {
            val lower = query.lowercase()
            if (lower.contains("exhaustive") || lower.contains("deep research")) return EXHAUSTIVE
            return classify(query)
        }

        fun classify(query: String): WebSearchMode {
            val q = query.trim()
            if (q.isEmpty()) return NORMAL
            val lower = q.lowercase()

            if (lower.count { it == '?' } >= 2) return DEEP
            if (DEEP_SIGNALS.any { lower.contains(it) }) return DEEP

            val words = q.split(Regex("\\s+")).filter { it.isNotBlank() }.size
            return if (words <= 5) QUICK else NORMAL
        }

        private fun forcedPrefix(lower: String, prefix: String): String? =
            if (lower == prefix || lower.startsWith("$prefix ") || lower.startsWith("$prefix\n")) prefix else null

        private fun strip(text: String, prefix: String): String =
            text.substring(prefix.length).trim()

        private val DEEP_SIGNALS = listOf(
            "compare", "comparison", "versus", " vs ", " vs. ", "difference between",
            "pros and cons", "advantages and disadvantages", "best ", "top ",
            "latest", "newest", "up to date", "up-to-date", "alternatives",
            "benchmark", "in depth", "in-depth", "comprehensive", "detailed",
            "review", "research", "everything about", "deep dive",
        )
    }

    data class Resolved(val mode: WebSearchMode, val query: String)
}
