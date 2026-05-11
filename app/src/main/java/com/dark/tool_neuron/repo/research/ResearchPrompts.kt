package com.dark.tool_neuron.repo.research

import com.dark.tool_neuron.model.FetchedDoc
import com.dark.tool_neuron.model.ResearchContext

internal object ResearchPrompts {

    fun questionGen(ctx: ResearchContext, max: Int): String {
        val seenBlock = if (ctx.previousQuestions.isEmpty()) "(none)"
        else ctx.previousQuestions.joinToString("\n") { "- $it" }
        val summary = ctx.accumulatedSummary.ifBlank { "(no findings yet)" }
        return """
You are helping research the question:
"${ctx.originalQuestion}"

Findings so far (compressed):
$summary

Questions already asked (do not repeat):
$seenBlock

Generate up to $max NEW follow-up questions that, if answered by web search, would meaningfully fill gaps in the findings. Return ONLY the questions, one per line, no numbering, no commentary. If no useful follow-ups remain, return an empty response.
""".trim()
    }

    fun finalDocument(allCompressed: String, question: String, sources: List<FetchedDoc>): String {
        val sourceList = sources.take(MAX_SOURCES_IN_PROMPT)
            .mapIndexed { i, s -> "[${i + 1}] ${s.title} — ${s.url}" }
            .joinToString("\n")
        return """
You are writing a research document for the question:
"$question"

You have the following compressed findings collected across multiple iterations of search and read:

$allCompressed

Sources:
$sourceList

Produce a JSON object on a single line (no surrounding text, no markdown fences) with this exact shape:
{"title":"...","summary":"...","sections":[{"heading":"...","body":"..."}]}

Rules:
- title: a concise document title (under 80 chars)
- summary: 1-2 paragraphs answering the question directly
- sections: 2 to 6 sections, each with a heading and a body paragraph
- Output ONLY the JSON object. No commentary.
""".trim()
    }

    private const val MAX_SOURCES_IN_PROMPT = 30
}
