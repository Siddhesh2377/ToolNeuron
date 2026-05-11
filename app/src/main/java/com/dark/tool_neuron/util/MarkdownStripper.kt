package com.dark.tool_neuron.util

object MarkdownStripper {

    fun strip(input: String): String {
        var s = input

        s = s.replace(Regex("```[a-zA-Z0-9_+\\-]*\\n([\\s\\S]*?)```"), "$1")
        s = s.replace(Regex("`([^`\\n]+?)`"), "$1")

        s = s.replace(Regex("\\\\\\[([\\s\\S]*?)\\\\\\]"), "$1")
        s = s.replace(Regex("\\\\\\(([\\s\\S]*?)\\\\\\)"), "$1")
        s = s.replace(Regex("\\$\\$([\\s\\S]*?)\\$\\$"), "$1")
        s = s.replace(Regex("(?<![\\\\$])\\$([^$\\n]+?)\\$"), "$1")

        s = s.replace(Regex("!\\[([^\\]]*)\\]\\([^)]*\\)"), "$1")
        s = s.replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1")

        s = s.replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
        s = s.replace(Regex("^>\\s?", RegexOption.MULTILINE), "")
        s = s.replace(Regex("^[-*+]\\s+", RegexOption.MULTILINE), "- ")
        s = s.replace(Regex("^\\d+\\.\\s+", RegexOption.MULTILINE), "")
        s = s.replace(Regex("^\\s*[-*_]{3,}\\s*$", RegexOption.MULTILINE), "")

        s = s.replace(Regex("\\*\\*([^*]+?)\\*\\*"), "$1")
        s = s.replace(Regex("__([^_]+?)__"), "$1")
        s = s.replace(Regex("(?<!\\*)\\*([^*\\n]+?)\\*(?!\\*)"), "$1")
        s = s.replace(Regex("(?<!_)_([^_\\n]+?)_(?!_)"), "$1")
        s = s.replace(Regex("~~([^~]+?)~~"), "$1")

        s = s.replace(Regex("<[^>]+>"), "")

        s = s.replace(Regex("\\n{3,}"), "\n\n")

        return s.trim()
    }
}
