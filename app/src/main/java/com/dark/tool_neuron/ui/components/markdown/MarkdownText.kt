package com.dark.tool_neuron.ui.components.markdown

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.ActionToggleButton
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.MapleMonoFontFamily
import com.dark.tool_neuron.util.SecureClipboard


val LocalCodeHighlightEnabled = compositionLocalOf { true }

val LocalMarkdownColors = compositionLocalOf {
    InlineColors(
        codeBg = Color.Transparent,
        highlightBg = Color.Transparent,
        mathColor = Color.Transparent,
        linkColor = Color.Transparent,
    )
}

@Immutable
data class InlineColors(
    val codeBg: Color,
    val highlightBg: Color,
    val mathColor: Color,
    val linkColor: Color,
)

@Immutable
data class MarkdownTypography(
    val bodyFontSize: TextUnit,
    val bodyLineHeight: TextUnit,
    val h1FontSize: TextUnit,
    val h2FontSize: TextUnit,
    val h3FontSize: TextUnit,
    val h4FontSize: TextUnit,
    val h5FontSize: TextUnit,
    val h6FontSize: TextUnit,
    val codeFontSize: TextUnit,
    val codeLineHeight: TextUnit,
    val inlineCodeFontSize: TextUnit,
    val tableCellFontSize: TextUnit,
    val tableLineHeight: TextUnit,
    val blockSpacing: Dp,
    val paragraphSpacing: Dp,
    val bulletIndentBase: Dp,
    val bulletIndentPerLevel: Dp,
)

object MarkdownTypographies {
    val Chat = MarkdownTypography(
        bodyFontSize = 14.sp,
        bodyLineHeight = 20.sp,
        h1FontSize = 24.sp,
        h2FontSize = 20.sp,
        h3FontSize = 17.sp,
        h4FontSize = 15.sp,
        h5FontSize = 14.sp,
        h6FontSize = 13.sp,
        codeFontSize = 12.sp,
        codeLineHeight = 18.sp,
        inlineCodeFontSize = 12.sp,
        tableCellFontSize = 12.sp,
        tableLineHeight = 17.sp,
        blockSpacing = 4.dp,
        paragraphSpacing = 4.dp,
        bulletIndentBase = 4.dp,
        bulletIndentPerLevel = 12.dp,
    )
    val Document = MarkdownTypography(
        bodyFontSize = 16.sp,
        bodyLineHeight = 26.sp,
        h1FontSize = 30.sp,
        h2FontSize = 24.sp,
        h3FontSize = 20.sp,
        h4FontSize = 17.sp,
        h5FontSize = 15.sp,
        h6FontSize = 14.sp,
        codeFontSize = 13.sp,
        codeLineHeight = 20.sp,
        inlineCodeFontSize = 13.sp,
        tableCellFontSize = 13.sp,
        tableLineHeight = 19.sp,
        blockSpacing = 10.dp,
        paragraphSpacing = 8.dp,
        bulletIndentBase = 8.dp,
        bulletIndentPerLevel = 18.dp,
    )
}

val LocalMarkdownTypography = compositionLocalOf { MarkdownTypographies.Chat }

@Composable
fun rememberMarkdownColors(): InlineColors {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) {
        InlineColors(
            codeBg = scheme.surfaceVariant.copy(alpha = 0.5f),
            highlightBg = scheme.primary.copy(alpha = 0.3f),
            mathColor = scheme.primary,
            linkColor = scheme.primary,
        )
    }
}

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    typography: MarkdownTypography = MarkdownTypographies.Chat,
) {
    val dimens = LocalDimens.current
    val parsedContent = remember(text) { parseMarkdown(text) }
    val colors = rememberMarkdownColors()
    CompositionLocalProvider(LocalMarkdownTypography provides typography) {
        Column(
            modifier = modifier.padding(horizontal = dimens.spacingXs),
            verticalArrangement = Arrangement.spacedBy(typography.blockSpacing),
        ) {
            parsedContent.forEach { element -> MarkdownElementView(element, colors) }
        }
    }
}

fun LazyListScope.lazyMarkdownItems(
    text: String,
    keyPrefix: String,
    modifier: Modifier = Modifier,
    typography: MarkdownTypography = MarkdownTypographies.Chat,
) {
    val elements = parseMarkdownCached(text)
    items(
        count = elements.size,
        key = { index -> "${keyPrefix}-md-$index" },
        contentType = { index -> elements[index]::class.simpleName }
    ) { index ->
        val element = elements[index]
        val colors = LocalMarkdownColors.current
        CompositionLocalProvider(LocalMarkdownTypography provides typography) {
            Box(modifier = modifier.padding(
                top = element.topSpacing(typography),
                bottom = element.bottomSpacing(typography),
            )) {
                MarkdownElementView(element, colors)
            }
        }
    }
}

private fun MarkdownElement.topSpacing(t: MarkdownTypography): Dp = when (this) {
    is MarkdownElement.Heading1 -> t.blockSpacing * 3.5f
    is MarkdownElement.Heading2 -> t.blockSpacing * 3f
    is MarkdownElement.Heading3 -> t.blockSpacing * 2.5f
    is MarkdownElement.Heading4, is MarkdownElement.Heading5, is MarkdownElement.Heading6 -> t.blockSpacing * 2f
    is MarkdownElement.CodeBlock, is MarkdownElement.Table, is MarkdownElement.MathBlock -> t.blockSpacing * 1.5f
    is MarkdownElement.Quote -> t.blockSpacing
    is MarkdownElement.Divider -> t.blockSpacing * 2f
    is MarkdownElement.BulletPoint, is MarkdownElement.NumberedPoint -> 1.dp
    else -> t.paragraphSpacing / 2
}

private fun MarkdownElement.bottomSpacing(t: MarkdownTypography): Dp = when (this) {
    is MarkdownElement.Heading1, is MarkdownElement.Heading2, is MarkdownElement.Heading3 -> t.blockSpacing * 0.75f
    is MarkdownElement.Heading4, is MarkdownElement.Heading5, is MarkdownElement.Heading6 -> t.blockSpacing * 0.5f
    is MarkdownElement.CodeBlock, is MarkdownElement.Table, is MarkdownElement.MathBlock -> t.blockSpacing * 1.5f
    is MarkdownElement.Quote -> t.blockSpacing
    is MarkdownElement.Divider -> t.blockSpacing * 2f
    is MarkdownElement.BulletPoint, is MarkdownElement.NumberedPoint -> 1.dp
    else -> t.paragraphSpacing / 2
}

private operator fun Dp.times(f: Float): Dp = (this.value * f).dp
private operator fun Dp.div(d: Int): Dp = (this.value / d).dp


internal sealed class MarkdownElement {
    data class Heading1(val text: String) : MarkdownElement()
    data class Heading2(val text: String) : MarkdownElement()
    data class Heading3(val text: String) : MarkdownElement()
    data class Heading4(val text: String) : MarkdownElement()
    data class Heading5(val text: String) : MarkdownElement()
    data class Heading6(val text: String) : MarkdownElement()
    data class Body(val text: String) : MarkdownElement()
    data class BulletPoint(val text: String, val level: Int = 0) : MarkdownElement()
    data class NumberedPoint(val text: String, val number: String) : MarkdownElement()
    data class Quote(val text: String, val level: Int = 1) : MarkdownElement()
    data class CodeBlock(val code: String, val language: String) : MarkdownElement()
    data class InlineCode(val text: String) : MarkdownElement()
    data class Table(
        val headers: List<String>,
        val rows: List<List<String>>,
        val alignments: List<Alignment>
    ) : MarkdownElement() {
        enum class Alignment { LEFT, CENTER, RIGHT }
    }
    data class MathBlock(val expression: String, val isTypst: Boolean = false) : MarkdownElement()
    data class InlineMath(val expression: String, val isTypst: Boolean = false) : MarkdownElement()
    data object Divider : MarkdownElement()
}


private val parseCache = object : LinkedHashMap<String, List<MarkdownElement>>(32, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<MarkdownElement>>): Boolean = size > 24
}

private fun parseMarkdownCached(text: String): List<MarkdownElement> {
    synchronized(parseCache) {
        return parseCache.getOrPut(text) { parseMarkdown(text) }
    }
}

private val BULLET_REGEX = Regex("^\\s*[+\\-*]\\s+.+")
private val NUMBERED_REGEX = Regex("^\\d+\\.\\s+.+")
private val TABLE_SEP_REGEX = Regex("^:?-{1,}:?$")
private val LATEX_BEGIN_REGEX = Regex("""\\{1,2}begin\s*\{(equation|align|gather|multline|displaymath|math)\*?\}""")
private val LATEX_NORM_FIX = Regex("""\\begin\s+\{""")
private val LATEX_ENV_REGEX = Regex("""\\begin\{(equation|align|gather|multline|displaymath|math)(\*?)\}""")


internal fun parseMarkdown(text: String): List<MarkdownElement> {
    val elements = mutableListOf<MarkdownElement>()
    val lines = text.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        when {
            // Block math: \[...\]
            line.trimStart().startsWith("\\[") || line.trimStart().startsWith("\\\\[") -> {
                val isDouble = line.contains("\\\\[")
                val startPat = if (isDouble) "\\\\[" else "\\["
                val startCol = line.indexOf(startPat)
                val after = line.substring(startCol + startPat.length)
                val endSingle = "\\]"; val endDouble = "\\\\]"
                val sameEnd = when {
                    after.contains(endDouble) -> after.indexOf(endDouble)
                    after.contains(endSingle) -> after.indexOf(endSingle)
                    else -> -1
                }
                if (sameEnd != -1) {
                    elements.add(MarkdownElement.MathBlock(after.substring(0, sameEnd).trim().replace("\\\\", "\\"), false))
                } else {
                    val mathLines = mutableListOf<String>()
                    if (after.isNotBlank()) mathLines.add(after.replace("\\\\", "\\"))
                    i++
                    while (i < lines.size && !lines[i].contains(endSingle) && !lines[i].contains(endDouble)) {
                        mathLines.add(lines[i].replace("\\\\", "\\")); i++
                    }
                    if (i < lines.size) {
                        val cl = lines[i]
                        val ci = if (cl.contains(endDouble)) cl.indexOf(endDouble) else cl.indexOf(endSingle)
                        if (ci > 0) mathLines.add(cl.substring(0, ci).replace("\\\\", "\\"))
                    }
                    elements.add(MarkdownElement.MathBlock(mathLines.joinToString("\n").trim(), false))
                }
            }

            // LaTeX math environments
            LATEX_BEGIN_REGEX.containsMatchIn(line) -> {
                val norm = line.replace("\\\\", "\\").replace(LATEX_NORM_FIX, "\\begin{")
                val envMatch = LATEX_ENV_REGEX.find(norm)
                val envName = envMatch?.groupValues?.get(1) ?: "equation"
                val starred = envMatch?.groupValues?.get(2) ?: ""
                val endRx = Regex("""\\{1,2}end\s*\{${Regex.escape(envName)}${Regex.escape(starred)}\}""")
                val mathLines = mutableListOf<String>()
                i++
                while (i < lines.size && !endRx.containsMatchIn(lines[i])) {
                    mathLines.add(lines[i].replace("\\\\", "\\")); i++
                }
                val expr = mathLines.joinToString("\n").trim()
                if (expr.isNotBlank()) elements.add(MarkdownElement.MathBlock(expr, false))
            }

            // Block math: $$...$$
            line.trimStart().startsWith("$$") -> {
                val startCol = line.indexOf("$$")
                val after = line.substring(startCol + 2)
                val sameEnd = after.indexOf("$$")
                if (sameEnd != -1) {
                    val expr = after.substring(0, sameEnd).trim().replace("\\\\", "\\")
                    elements.add(MarkdownElement.MathBlock(expr, expr.contains("#")))
                } else {
                    val mathLines = mutableListOf<String>()
                    if (after.isNotBlank()) mathLines.add(after.replace("\\\\", "\\"))
                    i++
                    while (i < lines.size && !lines[i].contains("$$")) {
                        mathLines.add(lines[i].replace("\\\\", "\\")); i++
                    }
                    val expr = mathLines.joinToString("\n").trim()
                    elements.add(MarkdownElement.MathBlock(expr, expr.contains("#")))
                }
            }

            // Fenced code block
            line.startsWith("```") -> {
                val language = line.removePrefix("```").trim()
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].startsWith("```")) { codeLines.add(lines[i]); i++ }
                elements.add(MarkdownElement.CodeBlock(codeLines.joinToString("\n"), language))
            }

            // Indented code block
            line.trimStart().startsWith("    ") && line.trim().isNotEmpty() -> {
                val codeLines = mutableListOf<String>()
                while (i < lines.size && (lines[i].trimStart().startsWith("    ") || lines[i].isBlank())) {
                    if (lines[i].trim().isNotEmpty()) codeLines.add(lines[i].removePrefix("    "))
                    i++
                }
                i--
                elements.add(MarkdownElement.CodeBlock(codeLines.joinToString("\n"), ""))
            }

            // Headings
            line.startsWith("###### ") -> elements.add(MarkdownElement.Heading6(line.removePrefix("###### ")))
            line.startsWith("##### ") -> elements.add(MarkdownElement.Heading5(line.removePrefix("##### ")))
            line.startsWith("#### ") -> elements.add(MarkdownElement.Heading4(line.removePrefix("#### ")))
            line.startsWith("### ") -> elements.add(MarkdownElement.Heading3(line.removePrefix("### ")))
            line.startsWith("## ") -> elements.add(MarkdownElement.Heading2(line.removePrefix("## ")))
            line.startsWith("# ") -> elements.add(MarkdownElement.Heading1(line.removePrefix("# ")))

            // Bullet points
            line.matches(BULLET_REGEX) -> {
                val level = line.takeWhile { it == ' ' }.length / 2
                elements.add(MarkdownElement.BulletPoint(line.trimStart().substring(2), level))
            }

            // Numbered lists
            line.matches(NUMBERED_REGEX) -> {
                elements.add(MarkdownElement.NumberedPoint(line.substringAfter(". "), line.substringBefore(".")))
            }

            // Block quotes
            line.startsWith(">") -> {
                val level = line.takeWhile { it == '>' }.length
                elements.add(MarkdownElement.Quote(line.substring(level).trim(), level))
            }

            // Table
            line.startsWith("|") && i + 1 < lines.size && isTableSeparator(lines[i + 1]) -> {
                val headers = parseTableRow(line)
                i++ // skip to separator
                val alignments = lines[i].split("|").filter { it.isNotBlank() }.map { cell ->
                    val t = cell.trim()
                    when {
                        t.startsWith(":") && t.endsWith(":") -> MarkdownElement.Table.Alignment.CENTER
                        t.endsWith(":") -> MarkdownElement.Table.Alignment.RIGHT
                        else -> MarkdownElement.Table.Alignment.LEFT
                    }
                }
                i++
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].trimStart().startsWith("|")) {
                    val row = parseTableRow(lines[i])
                    val normalized = when {
                        row.size < headers.size -> row + List(headers.size - row.size) { "" }
                        row.size > headers.size -> row.take(headers.size)
                        else -> row
                    }
                    rows.add(normalized); i++
                }
                i--
                val finalAlignments = when {
                    alignments.size < headers.size -> alignments + List(headers.size - alignments.size) { MarkdownElement.Table.Alignment.LEFT }
                    alignments.size > headers.size -> alignments.take(headers.size)
                    else -> alignments
                }
                elements.add(MarkdownElement.Table(headers, rows, finalAlignments))
            }

            // Divider
            line == "---" || line == "___" || line == "***" -> elements.add(MarkdownElement.Divider)

            // Body text
            line.isNotBlank() -> elements.add(MarkdownElement.Body(line))
        }
        i++
    }
    return elements
}

private fun isTableSeparator(line: String): Boolean {
    val trimmed = line.trim()
    if (!trimmed.contains("|")) return false
    val cells = trimmed.split("|").filter { it.isNotBlank() }
    return cells.isNotEmpty() && cells.all { it.trim().matches(TABLE_SEP_REGEX) }
}

private fun parseTableRow(line: String): List<String> =
    line.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }


/**
 * Find closing ** for bold, accounting for *** (italic close + bold close).
 * When a run of 3+ stars is found, the last 2 close bold, any preceding ones close italic.
 */
private fun findStarClose(text: String, from: Int): Int {
    var i = from
    while (i < text.length) {
        if (text[i] == '*') {
            var end = i
            while (end < text.length && text[end] == '*') end++
            if (end - i >= 2) return end - 2
            i = end
        } else i++
    }
    return -1
}

internal fun buildInlineFormatted(text: String, colors: InlineColors): AnnotatedString = buildAnnotatedString {
    appendFormatted(text, colors)
}

private fun linkStyles(colors: InlineColors) = TextLinkStyles(
    style = SpanStyle(color = colors.linkColor, textDecoration = TextDecoration.Underline),
)

private data class MdLink(val text: String, val url: String, val endExclusive: Int)

// Parse `[text](url)` starting at `from` where text[from] == '['. Returns null
// if the shape is invalid. Supports nested brackets and parentheses.
private fun tryParseMdLink(text: String, from: Int): MdLink? {
    var depth = 1
    var i = from + 1
    while (i < text.length && depth > 0) {
        val c = text[i]
        if (c == '\\' && i + 1 < text.length) { i += 2; continue }
        when (c) {
            '[' -> depth++
            ']' -> { depth--; if (depth == 0) break }
        }
        i++
    }
    if (depth != 0 || i + 1 >= text.length || text[i + 1] != '(') return null
    val linkText = text.substring(from + 1, i)
    val urlStart = i + 2
    var j = urlStart
    var parenDepth = 1
    while (j < text.length && parenDepth > 0) {
        when (text[j]) {
            '(' -> parenDepth++
            ')' -> { parenDepth--; if (parenDepth == 0) break }
        }
        j++
    }
    if (parenDepth != 0) return null
    val url = text.substring(urlStart, j).trim()
    if (url.isEmpty()) return null
    return MdLink(linkText, url, j + 1)
}

private fun isUrlChar(c: Char): Boolean =
    !c.isWhitespace() && c !in "<>\"'`"

private fun matchBareUrl(text: String, from: Int): Int {
    val isHttps = text.startsWith("https://", from)
    val isHttp  = !isHttps && text.startsWith("http://", from)
    if (!isHttps && !isHttp) return -1
    val schemeLen = if (isHttps) 8 else 7
    var end = from + schemeLen
    while (end < text.length && isUrlChar(text[end])) end++
    // Trim trailing punctuation unlikely to be part of the URL.
    while (end > from + schemeLen && text[end - 1] in ".,;:!?)]") end--
    return if (end > from + schemeLen) end else -1
}

private fun AnnotatedString.Builder.appendFormatted(text: String, colors: InlineColors) {
    var i = 0
    while (i < text.length) {
        when {
            // Bold+Italic ***...***
            i + 2 < text.length && text[i] == '*' && text[i + 1] == '*' && text[i + 2] == '*' -> {
                val end = text.indexOf("***", i + 3)
                if (end != -1) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic))
                    appendFormatted(text.substring(i + 3, end), colors)
                    pop(); i = end + 3
                } else { append(text[i]); i++ }
            }
            // Bold **...**
            i + 1 < text.length && text[i] == '*' && text[i + 1] == '*' -> {
                val end = findStarClose(text, i + 2)
                if (end != -1) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    appendFormatted(text.substring(i + 2, end), colors)
                    pop(); i = end + 2
                } else { append(text[i]); i++ }
            }
            // Bold __...__
            i + 1 < text.length && text[i] == '_' && text[i + 1] == '_' -> {
                val end = text.indexOf("__", i + 2)
                if (end != -1) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    appendFormatted(text.substring(i + 2, end), colors)
                    pop(); i = end + 2
                } else { append(text[i]); i++ }
            }
            // Italic *...*
            text[i] == '*' -> {
                val end = text.indexOf('*', i + 1)
                if (end != -1) {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    appendFormatted(text.substring(i + 1, end), colors)
                    pop(); i = end + 1
                } else { append(text[i]); i++ }
            }
            // Italic _..._
            text[i] == '_' -> {
                val end = text.indexOf('_', i + 1)
                if (end != -1) {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    appendFormatted(text.substring(i + 1, end), colors)
                    pop(); i = end + 1
                } else { append(text[i]); i++ }
            }
            // Strikethrough ~~...~~
            i + 1 < text.length && text[i] == '~' && text[i + 1] == '~' -> {
                val end = text.indexOf("~~", i + 2)
                if (end != -1) {
                    pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                    appendFormatted(text.substring(i + 2, end), colors)
                    pop(); i = end + 2
                } else { append(text[i]); i++ }
            }
            // Inline code `...`
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end != -1) {
                    pushStyle(SpanStyle(fontFamily = MapleMonoFontFamily, background = colors.codeBg, fontSize = 12.sp))
                    append(' '); append(text, i + 1, end); append(' ')
                    pop(); i = end + 1
                } else { append(text[i]); i++ }
            }
            // Highlight ==...==
            i + 1 < text.length && text[i] == '=' && text[i + 1] == '=' -> {
                val end = text.indexOf("==", i + 2)
                if (end != -1) {
                    pushStyle(SpanStyle(background = colors.highlightBg))
                    appendFormatted(text.substring(i + 2, end), colors)
                    pop(); i = end + 2
                } else { append(text[i]); i++ }
            }
            // Inline math \(...\)
            i + 1 < text.length && text[i] == '\\' && text[i + 1] == '(' -> {
                val endIdx = text.indexOf("\\)", i + 2)
                if (endIdx != -1) {
                    pushStyle(SpanStyle(fontFamily = MapleMonoFontFamily, fontStyle = FontStyle.Italic, color = colors.mathColor))
                    append(renderMathToUnicode(text.substring(i + 2, endIdx)))
                    pop(); i = endIdx + 2
                } else { append(text[i]); i++ }
            }
            // Inline math $...$
            text[i] == '$' && (i + 1 >= text.length || text[i + 1] != '$') -> {
                val end = text.indexOf('$', i + 1)
                if (end != -1 && end > i + 1) {
                    pushStyle(SpanStyle(fontFamily = MapleMonoFontFamily, fontStyle = FontStyle.Italic, color = colors.mathColor))
                    append(renderMathToUnicode(text.substring(i + 1, end)))
                    pop(); i = end + 1
                } else { append(text[i]); i++ }
            }
            // Markdown link [text](url)
            text[i] == '[' -> {
                val link = tryParseMdLink(text, i)
                if (link != null) {
                    withLink(LinkAnnotation.Url(url = link.url, styles = linkStyles(colors))) {
                        appendFormatted(link.text, colors)
                    }
                    i = link.endExclusive
                } else { append(text[i]); i++ }
            }
            // Autolink <https://...> or <http://...> or <mailto:...>
            text[i] == '<' -> {
                val close = text.indexOf('>', i + 1)
                val inner = if (close != -1) text.substring(i + 1, close) else ""
                val isUrl = inner.startsWith("https://") || inner.startsWith("http://") || inner.startsWith("mailto:")
                if (isUrl) {
                    withLink(LinkAnnotation.Url(url = inner, styles = linkStyles(colors))) {
                        append(inner)
                    }
                    i = close + 1
                } else { append(text[i]); i++ }
            }
            // Bare URL https://... or http://...
            (text[i] == 'h') && (text.startsWith("https://", i) || text.startsWith("http://", i)) -> {
                val end = matchBareUrl(text, i)
                if (end > 0) {
                    val url = text.substring(i, end)
                    withLink(LinkAnnotation.Url(url = url, styles = linkStyles(colors))) {
                        append(url)
                    }
                    i = end
                } else { append(text[i]); i++ }
            }
            // Default — handle surrogates
            else -> {
                val c = text[i]
                if (c.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()) {
                    append(c); append(text[i + 1]); i += 2
                } else { append(c); i++ }
            }
        }
    }
}

@Composable
private fun cachedInlineFormatting(text: String, colors: InlineColors): AnnotatedString =
    remember(text, colors) { buildInlineFormatted(text, colors) }


@Composable
private fun MarkdownElementView(element: MarkdownElement, colors: InlineColors) {
    val dimens = LocalDimens.current
    val typo = LocalMarkdownTypography.current
    when (element) {
        is MarkdownElement.Heading1 -> HeadingText(element.text, colors, typo.h1FontSize, FontWeight.Bold, 4.dp)
        is MarkdownElement.Heading2 -> HeadingText(element.text, colors, typo.h2FontSize, FontWeight.SemiBold, 3.dp)
        is MarkdownElement.Heading3 -> HeadingText(element.text, colors, typo.h3FontSize, FontWeight.SemiBold, 2.dp)
        is MarkdownElement.Heading4 -> HeadingText(element.text, colors, typo.h4FontSize, FontWeight.Medium, 2.dp)
        is MarkdownElement.Heading5 -> HeadingText(element.text, colors, typo.h5FontSize, FontWeight.Medium, 1.dp)
        is MarkdownElement.Heading6 -> HeadingText(element.text, colors, typo.h6FontSize, FontWeight.Medium, 1.dp, 0.87f)
        is MarkdownElement.Body -> BodyText(element.text, colors)
        is MarkdownElement.BulletPoint -> BulletPointView(element.text, element.level, colors)
        is MarkdownElement.NumberedPoint -> NumberedPointView(element.text, element.number, colors)
        is MarkdownElement.Quote -> BlockQuoteView(element.text, element.level, colors)
        is MarkdownElement.CodeBlock -> CodeBlockView(element.code, element.language)
        is MarkdownElement.InlineCode -> InlineCodeView(element.text)
        is MarkdownElement.Table -> TableView(element.headers, element.rows, element.alignments, colors)
        is MarkdownElement.MathBlock -> MathBlockView(element.expression, element.isTypst)
        is MarkdownElement.InlineMath -> InlineMathView(element.expression)
        is MarkdownElement.Divider -> HorizontalDivider(
            modifier = Modifier.padding(vertical = dimens.spacingXs),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@Composable
private fun HeadingText(
    text: String, colors: InlineColors,
    fontSize: TextUnit, fontWeight: FontWeight,
    verticalPad: Dp, alpha: Float = 1f
) {
    Text(
        text = cachedInlineFormatting(text, colors),
        style = MaterialTheme.typography.titleLarge,
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = LocalContentColor.current.copy(alpha = alpha),
        modifier = Modifier.padding(vertical = verticalPad)
    )
}

@Composable
private fun BodyText(text: String, colors: InlineColors) {
    val typo = LocalMarkdownTypography.current
    Text(
        text = cachedInlineFormatting(text, colors),
        style = MaterialTheme.typography.bodyMedium,
        fontSize = typo.bodyFontSize,
        color = LocalContentColor.current.copy(alpha = 0.87f),
        lineHeight = typo.bodyLineHeight,
    )
}

@Composable
private fun BulletPointView(text: String, level: Int, colors: InlineColors) {
    val typo = LocalMarkdownTypography.current
    Row(
        modifier = Modifier.padding(start = typo.bulletIndentBase + typo.bulletIndentPerLevel * level),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "\u2022",
            style = MaterialTheme.typography.bodyMedium,
            fontSize = typo.bodyFontSize,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 1.dp),
        )
        Text(
            text = cachedInlineFormatting(text, colors),
            style = MaterialTheme.typography.bodyMedium,
            fontSize = typo.bodyFontSize,
            color = LocalContentColor.current.copy(alpha = 0.87f),
            lineHeight = typo.bodyLineHeight,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun NumberedPointView(text: String, number: String, colors: InlineColors) {
    val typo = LocalMarkdownTypography.current
    Row(
        modifier = Modifier.padding(start = typo.bulletIndentBase),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "$number.",
            style = MaterialTheme.typography.bodyMedium,
            fontSize = typo.bodyFontSize,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 1.dp),
        )
        Text(
            text = cachedInlineFormatting(text, colors),
            style = MaterialTheme.typography.bodyMedium,
            fontSize = typo.bodyFontSize,
            color = LocalContentColor.current.copy(alpha = 0.87f),
            lineHeight = typo.bodyLineHeight,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BlockQuoteView(text: String, level: Int, colors: InlineColors) {
    val dimens = LocalDimens.current
    val typo = LocalMarkdownTypography.current
    val barColor = MaterialTheme.colorScheme.primary
    val barWidthPx = with(LocalDensity.current) { 2.dp.toPx() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ((level - 1) * 10).dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .drawBehind {
                drawRect(color = barColor, size = Size(barWidthPx, size.height))
            }
            .padding(start = dimens.spacingSm + 2.dp, end = dimens.spacingSm, top = dimens.spacingSm, bottom = dimens.spacingSm),
    ) {
        Text(
            text = cachedInlineFormatting(text, colors),
            style = MaterialTheme.typography.bodyMedium,
            fontSize = typo.bodyFontSize,
            color = LocalContentColor.current.copy(alpha = 0.87f),
            lineHeight = typo.bodyLineHeight,
        )
    }
}

@Composable
private fun InlineCodeView(text: String) {
    val typo = LocalMarkdownTypography.current
    Text(
        text = text,
        fontFamily = MapleMonoFontFamily,
        fontSize = typo.inlineCodeFontSize,
        color = LocalContentColor.current.copy(alpha = 0.85f),
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CodeBlockView(code: String, language: String) {
    var isExpanded by remember { mutableStateOf(false) }
    val codeScrollState = rememberScrollState()
    val context = LocalContext.current
    val dimens = LocalDimens.current
    val typo = LocalMarkdownTypography.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val headerBg = remember(isDark) { if (isDark) Color(0xFF282C34) else Color(0xFFF5F5F5) }
    val headerFg = remember(isDark) { if (isDark) Color(0xFFABB2BF) else Color(0xFF383A42) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(headerBg.copy(alpha = 0.85f))
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (language.isNotEmpty()) {
                    Text(
                        text = language.uppercase(),
                        fontFamily = MapleMonoFontFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = headerFg.copy(alpha = 0.5f)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs)) {
                ActionButton(
                    onClickListener = {
                        SecureClipboard.copy(context, language, code)
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                    },
                    icon = TnIcons.Copy,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = headerFg.copy(0.1f),
                        contentColor = headerFg.copy(0.7f)
                    )
                )
                ActionToggleButton(
                    checked = isExpanded,
                    onCheckedChange = { isExpanded = !isExpanded },
                    icon = if (isExpanded) TnIcons.ChevronUp else TnIcons.ChevronDown,
                    colors = IconButtonDefaults.filledIconToggleButtonColors(
                        containerColor = headerFg.copy(0.1f),
                        contentColor = headerFg.copy(0.7f),
                        checkedContainerColor = headerFg.copy(0.15f),
                        checkedContentColor = headerFg.copy(0.8f)
                    )
                )
            }
        }

        if (isExpanded) {
            val syntaxTheme = resolveSyntaxTheme()
            val highlighted = remember(code, language) {
                if (language.isNotBlank()) highlightCode(code, language, syntaxTheme) else null
            }
            HorizontalDivider(color = headerFg.copy(alpha = 0.1f))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(codeScrollState)
                    .padding(horizontal = 10.dp, vertical = dimens.spacingSm)
            ) {
                Text(
                    text = highlighted ?: AnnotatedString(code),
                    fontFamily = MapleMonoFontFamily,
                    fontSize = typo.codeFontSize,
                    lineHeight = typo.codeLineHeight,
                )
            }
        }
    }
}


@Composable
private fun TableView(
    headers: List<String>,
    rows: List<List<String>>,
    alignments: List<MarkdownElement.Table.Alignment>,
    colors: InlineColors
) {
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val headerBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val altRowBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.10f)
    val textColor = LocalContentColor.current
    val dimTextColor = textColor.copy(alpha = 0.87f)
    val colCount = headers.size.coerceAtLeast(1)
    val density = LocalDensity.current
    val cellPadH = with(density) { 8.dp.toPx() }
    val cellPadV = with(density) { 6.dp.toPx() }
    val dividerWidth = with(density) { 1.dp.toPx() }
    val cornerRadius = with(density) { 8.dp.toPx() }
    val textMeasurer = rememberTextMeasurer()

    val typo = LocalMarkdownTypography.current
    val baseTypo = MaterialTheme.typography.bodySmall
    val headerStyle = baseTypo.copy(fontSize = typo.tableCellFontSize, fontWeight = FontWeight.SemiBold, color = textColor, lineHeight = typo.tableLineHeight)
    val cellStyle = baseTypo.copy(fontSize = typo.tableCellFontSize, color = dimTextColor, lineHeight = typo.tableLineHeight)

    // Pre-build AnnotatedStrings once (width-independent)
    val headerFormatted = remember(headers, colors) { headers.map { buildInlineFormatted(it, colors) } }
    val rowsFormatted = remember(rows, colors) {
        rows.map { row -> (0 until colCount).map { ci -> buildInlineFormatted(row.getOrElse(ci) { "" }, colors) } }
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
    ) {
        val totalWidth = with(density) { maxWidth.toPx() }
        val colWidth = (totalWidth - dividerWidth * (colCount - 1)) / colCount
        val cellTextWidth = (colWidth - cellPadH * 2).coerceAtLeast(1f).toInt()

        val headerMeasured = remember(headerFormatted, cellTextWidth) {
            headerFormatted.map { textMeasurer.measure(it, headerStyle, maxLines = 3, constraints = Constraints(maxWidth = cellTextWidth)) }
        }
        val rowsMeasured = remember(rowsFormatted, cellTextWidth) {
            rowsFormatted.map { row -> row.map { textMeasurer.measure(it, cellStyle, maxLines = 5, constraints = Constraints(maxWidth = cellTextWidth)) } }
        }

        val headerRowHeight = remember(headerMeasured) { (headerMeasured.maxOfOrNull { it.size.height } ?: 0) + (cellPadV * 2) }
        val rowHeights = remember(rowsMeasured) { rowsMeasured.map { cells -> (cells.maxOfOrNull { it.size.height } ?: 0) + (cellPadV * 2) } }
        val totalHeight = headerRowHeight + rowHeights.sum() + dividerWidth * rows.size

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { totalHeight.toDp() })
                .drawBehind {
                    drawRoundRect(
                        color = outlineColor,
                        size = Size(totalWidth, totalHeight),
                        cornerRadius = CornerRadius(cornerRadius),
                        style = Stroke(width = dividerWidth)
                    )
                }
        ) {
            var y = 0f

            drawRect(color = headerBg, topLeft = Offset(0f, 0f), size = Size(totalWidth, headerRowHeight))
            drawTableRow(headerMeasured, colCount, colWidth, cellPadH, cellPadV, dividerWidth, y, headerRowHeight, alignments, outlineColor)
            y += headerRowHeight

            rowsMeasured.forEachIndexed { rowIndex, cells ->
                drawRect(color = outlineColor.copy(alpha = 0.4f), topLeft = Offset(0f, y), size = Size(totalWidth, dividerWidth))
                y += dividerWidth
                val rh = rowHeights[rowIndex]
                if (rowIndex % 2 == 1) drawRect(color = altRowBg, topLeft = Offset(0f, y), size = Size(totalWidth, rh))
                drawTableRow(cells, colCount, colWidth, cellPadH, cellPadV, dividerWidth, y, rh, alignments, outlineColor)
                y += rh
            }
        }
    }
}

private fun DrawScope.drawTableRow(
    measuredCells: List<TextLayoutResult>,
    colCount: Int, colWidth: Float, cellPadH: Float, cellPadV: Float,
    dividerWidth: Float, rowY: Float, rowHeight: Float,
    alignments: List<MarkdownElement.Table.Alignment>, outlineColor: Color
) {
    for (ci in 0 until colCount) {
        val cellX = ci * (colWidth + dividerWidth)
        if (ci > 0) drawRect(color = outlineColor.copy(alpha = 0.3f), topLeft = Offset(cellX - dividerWidth, rowY), size = Size(dividerWidth, rowHeight))
        if (ci < measuredCells.size) {
            val measured = measuredCells[ci]
            val align = alignments.getOrNull(ci) ?: MarkdownElement.Table.Alignment.LEFT
            val tw = measured.size.width.toFloat()
            val avail = colWidth - cellPadH * 2
            val ox = when (align) {
                MarkdownElement.Table.Alignment.CENTER -> cellX + cellPadH + (avail - tw).coerceAtLeast(0f) / 2
                MarkdownElement.Table.Alignment.RIGHT -> cellX + cellPadH + (avail - tw).coerceAtLeast(0f)
                else -> cellX + cellPadH
            }
            drawText(textLayoutResult = measured, topLeft = Offset(ox, rowY + cellPadV))
        }
    }
}


@Composable
private fun MathBlockView(expression: String, isTypst: Boolean) {
    val renderedMath = remember(expression) { renderMathToUnicode(expression) }
    val mathColor = MaterialTheme.colorScheme.primary
    val bgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(bgColor)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "\u2211", fontFamily = MapleMonoFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = mathColor)
            Text(
                text = if (isTypst) "TYPST" else "MATH",
                fontFamily = MapleMonoFontFamily, fontSize = 10.sp, fontWeight = FontWeight.Medium,
                color = LocalContentColor.current.copy(alpha = 0.5f)
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Box(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = renderedMath,
                fontFamily = MapleMonoFontFamily, fontSize = 16.sp,
                fontStyle = FontStyle.Italic,
                color = LocalContentColor.current, lineHeight = 24.sp
            )
        }
    }
}

@Composable
private fun InlineMathView(expression: String) {
    val renderedMath = remember(expression) { renderMathToUnicode(expression) }
    Text(
        text = renderedMath,
        fontFamily = MapleMonoFontFamily,
        fontSize = 14.sp,
        fontStyle = FontStyle.Italic,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    )
}


private fun Color.luminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue
