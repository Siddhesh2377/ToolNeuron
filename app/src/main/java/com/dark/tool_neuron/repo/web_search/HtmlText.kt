package com.dark.tool_neuron.repo.web_search

object HtmlText {

    private val DOT = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)

    private val SCRIPT = Regex("<script\\b[^>]*>.*?</script>", DOT)
    private val STYLE = Regex("<style\\b[^>]*>.*?</style>", DOT)
    private val NOSCRIPT = Regex("<noscript\\b[^>]*>.*?</noscript>", DOT)
    private val HEAD = Regex("<head\\b[^>]*>.*?</head>", DOT)
    private val COMMENT = Regex("<!--.*?-->", setOf(RegexOption.DOT_MATCHES_ALL))
    private val BLOCK_END = Regex("(?i)</(p|div|li|h[1-6]|tr|section|article|header|footer|ul|ol|table)>")
    private val BR = Regex("(?i)<br\\s*/?>")
    private val TAG = Regex("<[^>]+>")
    private val MULTI_NL = Regex("\\n{2,}")
    private val INLINE_WS = Regex("[ \\t]+")
    private val NUM_ENTITY = Regex("&#(x?[0-9a-fA-F]+);")

    private val NAMED = mapOf(
        "&amp;" to "&", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"",
        "&apos;" to "'", "&#39;" to "'", "&nbsp;" to " ", "&mdash;" to "—",
        "&ndash;" to "–", "&hellip;" to "…", "&rsquo;" to "'", "&lsquo;" to "'",
        "&ldquo;" to "\"", "&rdquo;" to "\"", "&trade;" to "™", "&reg;" to "®",
    )

    fun extract(html: String, maxChars: Int): String {
        if (html.isBlank()) return ""
        var s = html
        s = SCRIPT.replace(s, " ")
        s = STYLE.replace(s, " ")
        s = NOSCRIPT.replace(s, " ")
        s = HEAD.replace(s, " ")
        s = COMMENT.replace(s, " ")
        s = BR.replace(s, "\n")
        s = BLOCK_END.replace(s, "\n")
        s = TAG.replace(s, " ")
        s = decodeEntities(s)
        s = INLINE_WS.replace(s, " ")
        s = s.lines().joinToString("\n") { it.trim() }
        s = MULTI_NL.replace(s, "\n").trim()
        if (s.length > maxChars) s = s.substring(0, maxChars).substringBeforeLast(' ').trim()
        return s
    }

    private fun decodeEntities(input: String): String {
        var s = input
        NAMED.forEach { (k, v) -> if (s.contains(k, ignoreCase = true)) s = s.replace(k, v, ignoreCase = true) }
        s = NUM_ENTITY.replace(s) { m ->
            val raw = m.groupValues[1]
            val code = if (raw.startsWith("x") || raw.startsWith("X")) {
                raw.drop(1).toIntOrNull(16)
            } else {
                raw.toIntOrNull()
            }
            if (code != null && code in 32..0x10FFFF) {
                runCatching { String(Character.toChars(code)) }.getOrDefault(m.value)
            } else {
                m.value
            }
        }
        return s
    }
}
