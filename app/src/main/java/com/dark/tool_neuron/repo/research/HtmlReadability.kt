package com.dark.tool_neuron.repo.research

internal object HtmlReadability {

    data class Extracted(val title: String, val text: String)

    fun extract(html: String): Extracted {
        if (html.isBlank()) return Extracted("", "")
        val noScripts = stripBlockTag(html, "script")
        val noStyles = stripBlockTag(noScripts, "style")
        val noNoscript = stripBlockTag(noStyles, "noscript")
        val noTemplate = stripBlockTag(noNoscript, "template")
        val noSvg = stripBlockTag(noTemplate, "svg")

        val title = extractTitle(noSvg)

        val mainSlice = pickMainSlice(noSvg)

        val withSpaces = mainSlice.replace(BLOCK_TAG_REGEX, " ")
        val stripped = withSpaces.replace(TAG_REGEX, "")
        val decoded = decodeEntities(stripped)
        val collapsed = decoded.replace(WS_REGEX, " ").trim()

        return Extracted(title.trim(), collapsed)
    }

    private fun extractTitle(html: String): String {
        val m = TITLE_REGEX.find(html) ?: return ""
        val raw = m.groupValues[1]
        return decodeEntities(raw.replace(TAG_REGEX, "")).trim()
    }

    private fun pickMainSlice(html: String): String {
        SECTION_REGEXES.forEach { rx ->
            rx.find(html)?.let { match ->
                val inner = match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() }
                if (inner != null) return inner
            }
        }
        val bodyMatch = BODY_REGEX.find(html) ?: return html
        return bodyMatch.groupValues[1].ifBlank { html }
    }

    private fun stripBlockTag(html: String, tag: String): String {
        val pattern = "(?is)<$tag\\b[^>]*>.*?</$tag\\s*>"
        return html.replace(Regex(pattern), " ")
            .replace(Regex("(?i)<$tag\\b[^>]*/>"), " ")
    }

    private fun decodeEntities(input: String): String {
        if (input.isEmpty() || '&' !in input) return input
        val sb = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            val ch = input[i]
            if (ch != '&') {
                sb.append(ch)
                i++
                continue
            }
            val end = input.indexOf(';', i + 1)
            if (end < 0 || end - i > 12) {
                sb.append(ch)
                i++
                continue
            }
            val entity = input.substring(i + 1, end)
            val replacement = resolveEntity(entity)
            if (replacement != null) {
                sb.append(replacement)
                i = end + 1
            } else {
                sb.append(ch)
                i++
            }
        }
        return sb.toString()
    }

    private fun resolveEntity(entity: String): String? {
        if (entity.isEmpty()) return null
        if (entity[0] == '#') {
            val codePoint = if (entity.length > 1 && (entity[1] == 'x' || entity[1] == 'X'))
                entity.substring(2).toIntOrNull(16)
            else
                entity.substring(1).toIntOrNull()
            return codePoint?.takeIf { it in 0..0x10FFFF }?.let { String(Character.toChars(it)) }
        }
        return NAMED_ENTITIES[entity]
    }

    private val NAMED_ENTITIES = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">",
        "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "ndash" to "–", "mdash" to "—", "hellip" to "…",
        "lsquo" to "'", "rsquo" to "'", "ldquo" to "“", "rdquo" to "”",
        "copy" to "©", "reg" to "®", "trade" to "™", "para" to "¶",
        "middot" to "·", "bull" to "•", "deg" to "°",
    )

    private val TITLE_REGEX = Regex("(?is)<title[^>]*>(.*?)</title>")
    private val BODY_REGEX = Regex("(?is)<body[^>]*>(.*?)</body>")
    private val SECTION_REGEXES = listOf(
        Regex("(?is)<article[^>]*>(.*?)</article>"),
        Regex("(?is)<main[^>]*>(.*?)</main>"),
        Regex("(?is)<section[^>]*>(.*?)</section>"),
    )
    private val TAG_REGEX = Regex("<[^>]+>")
    private val BLOCK_TAG_REGEX = Regex(
        "(?i)</?(p|div|br|li|h1|h2|h3|h4|h5|h6|tr|td|th|article|section|main|header|footer|aside|nav|figure|figcaption)\\b[^>]*>",
    )
    private val WS_REGEX = Regex("\\s+")
}
