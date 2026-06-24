package com.dark.tool_neuron.repo.web_search

import com.dark.tool_neuron.model.WebSearchHit

object WebSearchPrompts {
    fun initialQueries(userQuery: String, count: Int): String = buildString {
        val n = count.coerceIn(1, 8)
        append("You are a search-query generator. The user wants information about the topic below.")
        append("\n\nWrite exactly $n concise web search queries that together surface the most useful pages to answer the user.")
        append(" Each query should target a different angle (overview, specifics, recent/latest, comparisons, official source).")
        append(" Each query should be 3-8 words. Keep the user's key terms. Do not repeat a query in different words.")
        append("\n\nReturn ONLY the queries as a numbered list, one per line:")
        for (i in 1..n) append("\n$i. <query>")
        append("\n\nNo preamble, no explanation, no extra text.")
        append("\n\nUser topic: ")
        append(userQuery.trim())
    }

    fun roundDigest(
        userQuery: String,
        priorSummary: String,
        findings: List<WebSearchHit>,
        excerpts: Map<String, String>,
        followUpCount: Int,
    ): String = buildString {
        val k = followUpCount.coerceIn(1, 8)
        append("You are researching a question using web search results.")
        append("\n\nQuestion: ")
        append(userQuery.trim())
        append("\n\nWhat we already know:\n")
        append(priorSummary.ifBlank { "(nothing yet)" })
        append("\n\nNew findings from the latest search:\n")
        findings.forEachIndexed { i, h ->
            append("\n")
            append(i + 1)
            append(". ")
            append(h.title.ifBlank { h.url })
            val body = excerpts[h.url]?.takeIf { it.isNotBlank() } ?: h.snippet
            if (body.isNotBlank()) {
                append(" — ")
                append(body.trim().replace('\n', ' ').take(EVIDENCE_CHAR_CAP))
            }
            append('\n')
        }
        append("\nUsing ONLY these findings (do not invent facts), reply in EXACTLY this format:")
        append("\nSUMMARY:")
        append("\n<updated summary of facts that help answer the question>")
        append("\nMISSING:")
        append("\n<what is still missing, or 'none'>")
        append("\nQUERIES:")
        append("\n<up to $k new 3-8 word search queries for the missing parts, numbered; or 'none'>")
        append("\nCOVERAGE:")
        append("\n<a number 0-100 for how fully the question is answered so far>")
    }

    fun synthesize(
        userQuery: String,
        summary: String,
        hits: List<WebSearchHit>,
        excerpts: Map<String, String>,
    ): String = buildString {
        append("Answer the user's question using the research notes and sources below. ")
        append("Write a direct, complete answer first, then add useful context. ")
        append("If the user asked for a link or download page, give the best official URL first. ")
        append("Use only the information provided — do not invent details. ")
        append("You may cite sources inline as [1], [2] where helpful. ")
        append("If the sources don't fully cover the question, answer what you can and say what's missing.")
        append("\n\nQuestion: ")
        append(userQuery.trim())
        if (summary.isNotBlank()) {
            append("\n\nResearch notes:\n")
            append(summary.trim().take(SUMMARY_CHAR_CAP))
        }
        append("\n\nSources:\n")
        hits.forEachIndexed { i, h ->
            append("\n")
            append(i + 1)
            append(". ")
            append(h.title.ifBlank { h.url })
            val body = excerpts[h.url]?.takeIf { it.isNotBlank() } ?: h.snippet
            if (body.isNotBlank()) {
                append(" — ")
                append(body.trim().replace('\n', ' ').take(EVIDENCE_CHAR_CAP))
            }
            append('\n')
        }
        append("\nAnswer:\n")
    }

    private const val EVIDENCE_CHAR_CAP = 700
    private const val SUMMARY_CHAR_CAP = 1600

    val QUERY_LINE_REGEX = Regex("^\\s*(?:\\d+[.)\\-:]|[-*•])\\s+(.+)$")
}
