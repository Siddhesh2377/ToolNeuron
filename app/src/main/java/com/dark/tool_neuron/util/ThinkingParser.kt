package com.dark.tool_neuron.util

data class ParsedContent(
    val thinkingText: String?,
    val visibleText: String,
    val isThinkingInProgress: Boolean = false
)

object ThinkingParser {

    private val completeThinkRegex =
        Regex("<think>(.*?)</think>", RegexOption.DOT_MATCHES_ALL)

    /**
     * Parse content that may contain `<think>...</think>` tags.
     *
     * Handles:
     * - No tags at all -> returns content as-is
     * - One or more complete `<think>...</think>` pairs -> extracts thinking, strips from visible
     * - Unclosed `<think>` at end (still streaming) -> everything after the tag is thinking,
     *   [isThinkingInProgress] = true, visible text is whatever came before the tag
     */
    fun parse(content: String): ParsedContent {
        if (!content.contains("<think>")) {
            return ParsedContent(
                thinkingText = null,
                visibleText = content.trim()
            )
        }

        val thinkingParts = mutableListOf<String>()
        var visible = content

        // Extract all complete <think>...</think> blocks
        completeThinkRegex.findAll(content).forEach { match ->
            val inner = match.groupValues[1].trim()
            if (inner.isNotEmpty()) {
                thinkingParts.add(inner)
            }
        }
        visible = visible.replace(completeThinkRegex, "")

        // Check for an unclosed <think> tag (streaming in progress)
        val unclosedIdx = visible.lastIndexOf("<think>")
        val isStreaming: Boolean
        if (unclosedIdx >= 0) {
            val afterTag = visible.substring(unclosedIdx + "<think>".length).trim()
            if (afterTag.isNotEmpty()) {
                thinkingParts.add(afterTag)
            }
            visible = visible.substring(0, unclosedIdx)
            isStreaming = true
        } else {
            isStreaming = false
        }

        val combinedThinking = thinkingParts.joinToString("\n\n").trim()

        return ParsedContent(
            thinkingText = combinedThinking.ifEmpty { null },
            visibleText = visible.trim(),
            isThinkingInProgress = isStreaming
        )
    }
}
