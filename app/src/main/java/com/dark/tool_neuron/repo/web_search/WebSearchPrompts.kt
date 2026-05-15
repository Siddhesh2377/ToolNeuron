package com.dark.tool_neuron.repo.web_search

import com.dark.tool_neuron.model.WebSearchHit

object WebSearchPrompts {

    fun generateQueries(userQuery: String): String = buildString {
        append("You are a search-query generator. The user wants information about the topic below.")
        append("\n\nWrite exactly THREE concise web search queries that, together, will surface the most useful pages to answer the user.")
        append(" Each query should target a different angle (e.g. overview, recent news, specific examples).")
        append(" Each query should be 3-8 words. Do not repeat the same query in different words.")
        append("\n\nReturn ONLY the queries as a numbered list, one per line, in this exact format:")
        append("\n1. <first query>")
        append("\n2. <second query>")
        append("\n3. <third query>")
        append("\n\nNo preamble, no explanation, no extra text.")
        append("\n\nUser topic: ")
        append(userQuery.trim())
    }

    fun synthesize(userQuery: String, hits: List<WebSearchHit>): String = buildString {
        // Small chat models (LFM2-350M, Qwen 0.5B, etc.) struggle when asked
        // to write markdown AND emit inline citations AND append a sources
        // section all at once — they typically output only the sources block
        // or a 1-line deflection. Strip the prompt down to a single task:
        // write a short factual summary. The sources accordion in the UI
        // already lists every URL, so the model doesn't need to repeat them.
        append("Summarize the search results below to answer the user's question. ")
        append("Write 2 to 4 short paragraphs in plain language. ")
        append("Use only information from the snippets — do not invent details. ")
        append("If the snippets don't cover the question, say so in one sentence.")
        append("\n\nQuestion: ")
        append(userQuery.trim())
        append("\n\nSearch results:\n")
        hits.forEachIndexed { i, h ->
            append("\n")
            append(i + 1)
            append(". ")
            append(h.title.ifBlank { h.url })
            if (h.snippet.isNotBlank()) {
                append(" — ")
                append(h.snippet.trim().replace('\n', ' ').take(SNIPPET_CHAR_CAP))
            }
            append('\n')
        }
        append("\nSummary:\n")
    }

    private const val SNIPPET_CHAR_CAP = 320

    val QUERY_LINE_REGEX = Regex("^\\s*(?:\\d+[.)\\-:]|[-*•])\\s+(.+)$")
}
