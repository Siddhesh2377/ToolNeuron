package com.dark.tool_neuron.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dark.tool_neuron.R
import com.dark.tool_neuron.ui.theme.ManropeFontFamily
import com.dark.tool_neuron.ui.theme.maple
import com.dark.tool_neuron.ui.theme.rDp

/** Set to false to skip syntax highlighting in code blocks (improves scroll performance). */
val LocalCodeHighlightEnabled = compositionLocalOf { true }

/** Colors extracted once from MaterialTheme — passed to non-composable formatters to avoid
 *  reading theme state inside every Text(), which would trigger recomposition on every frame. */
@Immutable
data class InlineColors(
    val codeBg: Color,
    val highlightBg: Color,
    val mathColor: Color
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MarkdownText(
    text: String, modifier: Modifier = Modifier
) {
    val parsedContent = remember(text) { parseMarkdown(text) }

    // Read theme colors ONCE — these flow to all children as stable params.
    val colors = InlineColors(
        codeBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        highlightBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
        mathColor = MaterialTheme.colorScheme.primary
    )

    Column(
        modifier = modifier.padding(horizontal = rDp(4.dp)),
        verticalArrangement = Arrangement.spacedBy(rDp(4.dp))
    ) {
        parsedContent.forEach { element ->
            MarkdownElementView(element, colors)
        }
    }
}

/**
 * Lazy version of MarkdownText — emits each markdown element as a separate LazyList item.
 * Only visible items are composed, dramatically improving scroll performance for long messages.
 *
 * Use this instead of [MarkdownText] when the content is inside a [LazyColumn].
 */
fun LazyListScope.lazyMarkdownItems(
    text: String,
    keyPrefix: String,
    modifier: Modifier = Modifier
) {
    val elements = parseMarkdown(text)

    items(
        count = elements.size,
        key = { index -> "${keyPrefix}-md-$index" },
        contentType = { index -> elements[index]::class.simpleName }
    ) { index ->
        val scheme = MaterialTheme.colorScheme
        val colors = remember(scheme) {
            InlineColors(
                codeBg = scheme.surfaceVariant.copy(alpha = 0.5f),
                highlightBg = scheme.primary.copy(alpha = 0.3f),
                mathColor = scheme.primary
            )
        }
        Box(modifier = modifier) {
            MarkdownElementView(elements[index], colors)
        }
    }
}

/** Renders a single [MarkdownElement]. Shared by both [MarkdownText] and [lazyMarkdownItems]. */
@Composable
private fun MarkdownElementView(element: MarkdownElement, colors: InlineColors) {
    when (element) {
        is MarkdownElement.Heading1 -> Heading1(element.text, colors)
        is MarkdownElement.Heading2 -> Heading2(element.text, colors)
        is MarkdownElement.Heading3 -> Heading3(element.text, colors)
        is MarkdownElement.Heading4 -> Heading4(element.text, colors)
        is MarkdownElement.Heading5 -> Heading5(element.text, colors)
        is MarkdownElement.Heading6 -> Heading6(element.text, colors)
        is MarkdownElement.Body -> BodyText(element.text, colors)
        is MarkdownElement.BulletPoint -> BulletPoint(element.text, element.level, colors)
        is MarkdownElement.NumberedPoint -> NumberedPoint(element.text, element.number, colors)
        is MarkdownElement.Quote -> BlockQuote(element.text, element.level, colors)
        is MarkdownElement.CodeBlock -> CodeBlockExpanded(element.code, element.language)
        is MarkdownElement.InlineCode -> InlineCodeText(element.text)
        is MarkdownElement.Table -> TableView(element.headers, element.rows, element.alignments, colors)
        is MarkdownElement.MathBlock -> MathBlockView(element.expression, element.isTypst)
        is MarkdownElement.InlineMath -> InlineMathView(element.expression, element.isTypst)
        is MarkdownElement.Divider -> HorizontalDivider(
            modifier = Modifier.padding(vertical = rDp(4.dp)),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

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
    object Divider : MarkdownElement()
}

internal fun parseMarkdown(text: String): List<MarkdownElement> {
    val elements = mutableListOf<MarkdownElement>()
    val lines = text.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]

        when {
            // Block math: \[...\] format (also handles double-escaped \\[...\\])
            line.trimStart().startsWith("\\[") || line.trimStart().startsWith("\\\\[") -> {
                val isDoubleEscaped = line.contains("\\\\[")
                val startPattern = if (isDoubleEscaped) "\\\\[" else "\\["
                val endPatternSingle = "\\]"
                val endPatternDouble = "\\\\]"

                val startCol = line.indexOf(startPattern)
                val afterStart = line.substring(startCol + startPattern.length)

                // Check if closing \] is on the same line
                val sameLineEnd = when {
                    afterStart.contains(endPatternDouble) -> afterStart.indexOf(endPatternDouble)
                    afterStart.contains(endPatternSingle) -> afterStart.indexOf(endPatternSingle)
                    else -> -1
                }
                if (sameLineEnd != -1) {
                    val expression = afterStart.substring(0, sameLineEnd).trim().replace("\\\\", "\\")
                    elements.add(MarkdownElement.MathBlock(expression, false))
                } else {
                    // Multi-line math block
                    val mathLines = mutableListOf<String>()
                    if (afterStart.isNotBlank()) mathLines.add(afterStart.replace("\\\\", "\\"))
                    i++
                    while (i < lines.size && !lines[i].contains(endPatternSingle) && !lines[i].contains(endPatternDouble)) {
                        mathLines.add(lines[i].replace("\\\\", "\\"))
                        i++
                    }
                    // Get content before closing \]
                    if (i < lines.size) {
                        val closingLine = lines[i]
                        val closeIdx = when {
                            closingLine.contains(endPatternDouble) -> closingLine.indexOf(endPatternDouble)
                            else -> closingLine.indexOf(endPatternSingle)
                        }
                        if (closeIdx > 0) {
                            mathLines.add(closingLine.substring(0, closeIdx).replace("\\\\", "\\"))
                        }
                    }
                    val expression = mathLines.joinToString("\n").trim()
                    elements.add(MarkdownElement.MathBlock(expression, false))
                }
            }
            // LaTeX math environments: \begin{equation}, \begin{align}, \begin{gather}, etc.
            // Use regex for more robust detection (handles various escape sequences)
            Regex("""\\{1,2}begin\s*\{(equation|align|gather|multline|eqnarray|displaymath|math)\*?\}""").containsMatchIn(line) -> {
                // Normalize: convert double backslashes to single, remove extra spaces
                val normalizedLine = line.replace("\\\\", "\\").replace(Regex("""\\begin\s+\{"""), "\\begin{")
                val envMatch = Regex("""\\begin\{(equation|align|gather|multline|eqnarray|displaymath|math)(\*?)\}""").find(normalizedLine)
                val envName = envMatch?.groupValues?.get(1) ?: "equation"
                val starred = envMatch?.groupValues?.get(2) ?: ""
                val endPatternRegex = Regex("""\\{1,2}end\s*\{${Regex.escape(envName)}${Regex.escape(starred)}\}""")

                val mathLines = mutableListOf<String>()
                // Get content after \begin{...} on the same line
                val beginTagRegex = Regex("""\\{1,2}begin\s*\{${Regex.escape(envName)}${Regex.escape(starred)}\}""")
                val afterBegin = beginTagRegex.split(normalizedLine, 2).getOrNull(1)?.trim() ?: ""
                if (afterBegin.isNotBlank() && !endPatternRegex.containsMatchIn(afterBegin)) {
                    mathLines.add(afterBegin.replace("\\\\", "\\"))
                }

                i++
                while (i < lines.size && !endPatternRegex.containsMatchIn(lines[i])) {
                    mathLines.add(lines[i].replace("\\\\", "\\"))
                    i++
                }

                // Get content before \end{...} on the closing line
                if (i < lines.size) {
                    val closingLine = lines[i]
                    val beforeEnd = endPatternRegex.split(closingLine, 2).getOrNull(0)?.trim() ?: ""
                    if (beforeEnd.isNotBlank()) {
                        mathLines.add(beforeEnd.replace("\\\\", "\\"))
                    }
                }

                val expression = mathLines.joinToString("\n").trim()
                if (expression.isNotBlank()) {
                    elements.add(MarkdownElement.MathBlock(expression, false))
                }
            }

            // Block math: $$...$$ (can span multiple lines)
            line.trimStart().startsWith("$$") -> {
                val isTypst = line.contains("#") || line.contains("$")
                val startCol = line.indexOf("$$")
                val afterStart = line.substring(startCol + 2)

                // Check if closing $$ is on the same line
                val sameLineEnd = afterStart.indexOf("$$")
                if (sameLineEnd != -1) {
                    val expression = afterStart.substring(0, sameLineEnd).trim().replace("\\\\", "\\")
                    elements.add(
                        MarkdownElement.MathBlock(
                            expression,
                            isTypst && expression.contains("#")
                        )
                    )
                } else {
                    // Multi-line math block
                    val mathLines = mutableListOf<String>()
                    if (afterStart.isNotBlank()) mathLines.add(afterStart.replace("\\\\", "\\"))
                    i++
                    while (i < lines.size && !lines[i].contains("$$")) {
                        mathLines.add(lines[i].replace("\\\\", "\\"))
                        i++
                    }
                    val expression = mathLines.joinToString("\n").trim()
                    elements.add(MarkdownElement.MathBlock(expression, expression.contains("#")))
                }
            }

            line.startsWith("```") -> {
                val language = line.removePrefix("```").trim()
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                elements.add(MarkdownElement.CodeBlock(codeLines.joinToString("\n"), language))
            }

            // LaTeX tabular environment: \begin{tabular}...\end{tabular}
            line.trimStart().contains("\\begin{tabular}") || line.trimStart().contains("\\\\begin{tabular}") -> {
                val tabularLines = mutableListOf<String>()
                tabularLines.add(line.replace("\\\\", "\\"))
                i++
                while (i < lines.size && !lines[i].contains("\\end{tabular}") && !lines[i].contains("\\\\end{tabular}")) {
                    tabularLines.add(lines[i].replace("\\\\", "\\"))
                    i++
                }
                if (i < lines.size) {
                    tabularLines.add(lines[i].replace("\\\\", "\\")) // Add the \end{tabular} line
                }
                val tableElement = parseLatexTabular(tabularLines.joinToString("\n"))
                if (tableElement != null) {
                    elements.add(tableElement)
                }
            }

            // LaTeX table environment: \begin{table}...\end{table} (wrapper around tabular)
            line.trimStart().contains("\\begin{table}") || line.trimStart().contains("\\\\begin{table}") -> {
                val tableLines = mutableListOf<String>()
                tableLines.add(line.replace("\\\\", "\\"))
                i++
                while (i < lines.size && !lines[i].contains("\\end{table}") && !lines[i].contains("\\\\end{table}")) {
                    tableLines.add(lines[i].replace("\\\\", "\\"))
                    i++
                }
                if (i < lines.size) {
                    tableLines.add(lines[i].replace("\\\\", "\\"))
                }
                // Extract tabular from within table environment
                val fullContent = tableLines.joinToString("\n")
                val tableElement = parseLatexTabular(fullContent)
                if (tableElement != null) {
                    elements.add(tableElement)
                }
            }

            // LaTeX array environment: \begin{array}...\end{array}
            line.trimStart().contains("\\begin{array}") || line.trimStart().contains("\\\\begin{array}") -> {
                val arrayLines = mutableListOf<String>()
                arrayLines.add(line.replace("\\\\", "\\"))
                i++
                while (i < lines.size && !lines[i].contains("\\end{array}") && !lines[i].contains("\\\\end{array}")) {
                    arrayLines.add(lines[i].replace("\\\\", "\\"))
                    i++
                }
                if (i < lines.size) {
                    arrayLines.add(lines[i].replace("\\\\", "\\"))
                }
                val tableElement = parseLatexArray(arrayLines.joinToString("\n"))
                if (tableElement != null) {
                    elements.add(tableElement)
                }
            }

            line.trimStart().startsWith("    ") && line.trim().isNotEmpty() -> {
                val codeLines = mutableListOf<String>()
                while (i < lines.size && (lines[i].trimStart()
                        .startsWith("    ") || lines[i].isBlank())
                ) {
                    if (lines[i].trim().isNotEmpty()) {
                        codeLines.add(lines[i].removePrefix("    "))
                    }
                    i++
                }
                i--
                elements.add(MarkdownElement.CodeBlock(codeLines.joinToString("\n"), ""))
            }

            line.startsWith("###### ") -> elements.add(MarkdownElement.Heading6(line.removePrefix("###### ")))
            line.startsWith("##### ") -> elements.add(MarkdownElement.Heading5(line.removePrefix("##### ")))
            line.startsWith("#### ") -> elements.add(MarkdownElement.Heading4(line.removePrefix("#### ")))
            line.startsWith("### ") -> elements.add(MarkdownElement.Heading3(line.removePrefix("### ")))
            line.startsWith("## ") -> elements.add(MarkdownElement.Heading2(line.removePrefix("## ")))
            line.startsWith("# ") -> elements.add(MarkdownElement.Heading1(line.removePrefix("# ")))
            line.matches(Regex("^[+\\-*]\\s+.+")) -> {
                val level = line.takeWhile { it == ' ' }.length / 2
                elements.add(MarkdownElement.BulletPoint(line.trimStart().substring(2), level))
            }

            line.matches(Regex("^\\d+\\.\\s+.+")) -> {
                val number = line.substringBefore(".")
                val text = line.substringAfter(". ")
                elements.add(MarkdownElement.NumberedPoint(text, number))
            }

            line.startsWith(">") -> {
                val level = line.takeWhile { it == '>' }.length
                val text = line.substring(level).trim()
                elements.add(MarkdownElement.Quote(text, level))
            }

            line.startsWith("|") && i + 1 < lines.size && isTableSeparator(lines[i + 1]) -> {
                val headers = parseTableRow(line)
                i++
                val alignmentLine = lines[i]
                val alignments = alignmentLine.split("|")
                    .filter { it.isNotBlank() }
                    .map { cell ->
                        val trimmed = cell.trim()
                        when {
                            trimmed.startsWith(":") && trimmed.endsWith(":") ->
                                MarkdownElement.Table.Alignment.CENTER
                            trimmed.endsWith(":") ->
                                MarkdownElement.Table.Alignment.RIGHT
                            else -> MarkdownElement.Table.Alignment.LEFT
                        }
                    }
                i++
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].trimStart().startsWith("|")) {
                    val row = parseTableRow(lines[i])
                    // Pad/trim row to match header column count
                    val normalized = when {
                        row.size < headers.size -> row + List(headers.size - row.size) { "" }
                        row.size > headers.size -> row.take(headers.size)
                        else -> row
                    }
                    rows.add(normalized)
                    i++
                }
                i--
                // Ensure alignments match column count
                val finalAlignments = when {
                    alignments.size < headers.size ->
                        alignments + List(headers.size - alignments.size) { MarkdownElement.Table.Alignment.LEFT }
                    alignments.size > headers.size -> alignments.take(headers.size)
                    else -> alignments
                }
                elements.add(MarkdownElement.Table(headers, rows, finalAlignments))
            }

            line == "---" || line == "___" || line == "***" -> elements.add(MarkdownElement.Divider)
            line.isNotBlank() -> elements.add(MarkdownElement.Body(line))
        }
        i++
    }

    return elements
}

/** Check if a line is a valid markdown table separator (e.g., |---|:---:|---:|) */
private fun isTableSeparator(line: String): Boolean {
    val trimmed = line.trim()
    if (!trimmed.contains("|")) return false
    val cells = trimmed.split("|").filter { it.isNotBlank() }
    return cells.isNotEmpty() && cells.all { cell ->
        cell.trim().matches(Regex("^:?-{1,}:?$"))
    }
}

/** Parse a markdown table row into a list of cell strings, handling edge pipes. */
private fun parseTableRow(line: String): List<String> {
    val trimmed = line.trim()
    // Remove leading/trailing pipe if present, then split
    val inner = trimmed
        .removePrefix("|")
        .removeSuffix("|")
    return inner.split("|").map { it.trim() }
}

/**
 * Parse LaTeX tabular environment into a Table element
 * Handles: \begin{tabular}{|c|c|...}, \hline, &, \\, \textbf{}, \textit{}, etc.
 */
private fun parseLatexTabular(content: String): MarkdownElement.Table? {
    try {
        // Extract content between \begin{tabular} and \end{tabular}
        val beginPattern = Regex("""\\begin\{tabular\}\{([^}]*)\}""")
        val beginMatch = beginPattern.find(content) ?: return null

        // Parse column alignment specification (e.g., |c|c|c|c|c|)
        val colSpec = beginMatch.groupValues[1]
        val alignments = colSpec.filter { it in "lcrLCR" }.map { char ->
            when (char.lowercaseChar()) {
                'l' -> MarkdownElement.Table.Alignment.LEFT
                'c' -> MarkdownElement.Table.Alignment.CENTER
                'r' -> MarkdownElement.Table.Alignment.RIGHT
                else -> MarkdownElement.Table.Alignment.LEFT
            }
        }

        // Get content between begin and end
        val startIdx = beginMatch.range.last + 1
        val endIdx = content.indexOf("\\end{tabular}")
        if (endIdx == -1) return null

        val tableContent = content.substring(startIdx, endIdx)

        // Split by \\ (row separator) and filter out empty rows and \hline
        val rowStrings = tableContent.split(Regex("""\\\\"""))
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.matches(Regex("""^\s*\\hline\s*$""")) }
            .map { row ->
                // Remove \hline from within rows
                row.replace("\\hline", "").trim()
            }
            .filter { it.isNotBlank() }

        if (rowStrings.isEmpty()) return null

        // Parse each row into cells (split by &)
        val allRows = rowStrings.map { row ->
            row.split("&").map { cell ->
                processLatexCellContent(cell.trim())
            }
        }

        // First row is headers, rest are data rows
        val headers = allRows.firstOrNull() ?: return null
        val dataRows = if (allRows.size > 1) allRows.drop(1) else emptyList()

        // Ensure alignments match column count
        val finalAlignments = if (alignments.size >= headers.size) {
            alignments.take(headers.size)
        } else {
            alignments + List(headers.size - alignments.size) { MarkdownElement.Table.Alignment.LEFT }
        }

        return MarkdownElement.Table(headers, dataRows, finalAlignments)
    } catch (e: Exception) {
        return null
    }
}

/**
 * Parse LaTeX array environment into a Table element
 * Similar to tabular but used in math mode
 */
private fun parseLatexArray(content: String): MarkdownElement.Table? {
    try {
        val beginPattern = Regex("""\\begin\{array\}\{([^}]*)\}""")
        val beginMatch = beginPattern.find(content) ?: return null

        val colSpec = beginMatch.groupValues[1]
        val alignments = colSpec.filter { it in "lcrLCR" }.map { char ->
            when (char.lowercaseChar()) {
                'l' -> MarkdownElement.Table.Alignment.LEFT
                'c' -> MarkdownElement.Table.Alignment.CENTER
                'r' -> MarkdownElement.Table.Alignment.RIGHT
                else -> MarkdownElement.Table.Alignment.LEFT
            }
        }

        val startIdx = beginMatch.range.last + 1
        val endIdx = content.indexOf("\\end{array}")
        if (endIdx == -1) return null

        val arrayContent = content.substring(startIdx, endIdx)

        val rowStrings = arrayContent.split(Regex("""\\\\"""))
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.matches(Regex("""^\s*\\hline\s*$""")) }
            .map { row -> row.replace("\\hline", "").trim() }
            .filter { it.isNotBlank() }

        if (rowStrings.isEmpty()) return null

        val allRows = rowStrings.map { row ->
            row.split("&").map { cell -> processLatexCellContent(cell.trim()) }
        }

        val headers = allRows.firstOrNull() ?: return null
        val dataRows = if (allRows.size > 1) allRows.drop(1) else emptyList()

        val finalAlignments = if (alignments.size >= headers.size) {
            alignments.take(headers.size)
        } else {
            alignments + List(headers.size - alignments.size) { MarkdownElement.Table.Alignment.LEFT }
        }

        return MarkdownElement.Table(headers, dataRows, finalAlignments)
    } catch (e: Exception) {
        return null
    }
}

/**
 * Process LaTeX formatting commands within table cells
 * Handles: \textbf{}, \textit{}, \emph{}, etc.
 */
private fun processLatexCellContent(cell: String): String {
    var result = cell

    // \textbf{...} -> content (bold indicator removed for plain text)
    result = Regex("""\\textbf\{([^}]*)\}""").replace(result) { it.groupValues[1] }

    // \textit{...} -> content
    result = Regex("""\\textit\{([^}]*)\}""").replace(result) { it.groupValues[1] }

    // \emph{...} -> content
    result = Regex("""\\emph\{([^}]*)\}""").replace(result) { it.groupValues[1] }

    // \underline{...} -> content
    result = Regex("""\\underline\{([^}]*)\}""").replace(result) { it.groupValues[1] }

    // \textrm{...} -> content
    result = Regex("""\\textrm\{([^}]*)\}""").replace(result) { it.groupValues[1] }

    // \textsf{...} -> content
    result = Regex("""\\textsf\{([^}]*)\}""").replace(result) { it.groupValues[1] }

    // \texttt{...} -> content
    result = Regex("""\\texttt\{([^}]*)\}""").replace(result) { it.groupValues[1] }

    // \text{...} -> content
    result = Regex("""\\text\{([^}]*)\}""").replace(result) { it.groupValues[1] }

    // Clean up any remaining backslash commands that might be left
    result = result.replace("\\hline", "").trim()

    return result
}

@Composable
private fun Heading1(text: String, colors: InlineColors) {
    Text(
        text = cachedInlineFormatting(text, colors),
        fontFamily = ManropeFontFamily,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = LocalContentColor.current,
        modifier = Modifier.padding(vertical = rDp(4.dp))
    )
}

@Composable
private fun Heading2(text: String, colors: InlineColors) {
    Text(
        text = cachedInlineFormatting(text, colors),
        fontFamily = ManropeFontFamily,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        color = LocalContentColor.current,
        modifier = Modifier.padding(vertical = rDp(3.dp))
    )
}

@Composable
private fun Heading3(text: String, colors: InlineColors) {
    Text(
        text = cachedInlineFormatting(text, colors),
        fontFamily = ManropeFontFamily,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        color = LocalContentColor.current,
        modifier = Modifier.padding(vertical = rDp(2.dp))
    )
}

@Composable
private fun Heading4(text: String, colors: InlineColors) {
    Text(
        text = cachedInlineFormatting(text, colors),
        fontFamily = ManropeFontFamily,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        color = LocalContentColor.current,
        modifier = Modifier.padding(vertical = rDp(2.dp))
    )
}

@Composable
private fun Heading5(text: String, colors: InlineColors) {
    Text(
        text = cachedInlineFormatting(text, colors),
        fontFamily = ManropeFontFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = LocalContentColor.current,
        modifier = Modifier.padding(vertical = rDp(1.dp))
    )
}

@Composable
private fun Heading6(text: String, colors: InlineColors) {
    Text(
        text = cachedInlineFormatting(text, colors),
        fontFamily = ManropeFontFamily,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = LocalContentColor.current.copy(alpha = 0.87f),
        modifier = Modifier.padding(vertical = rDp(1.dp))
    )
}

@Composable
private fun BodyText(text: String, colors: InlineColors) {
    Text(
        text = cachedInlineFormatting(text, colors),
        fontFamily = ManropeFontFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        color = LocalContentColor.current.copy(alpha = 0.87f),
        lineHeight = 20.sp
    )
}

/** Cached wrapper — call from @Composable contexts. Returns a remembered AnnotatedString. */
@Composable
private fun cachedInlineFormatting(text: String, colors: InlineColors): AnnotatedString {
    return remember(text, colors) { buildInlineFormatted(text, colors) }
}

/**
 * Pure function — NO @Composable, NO MaterialTheme reads.
 * All colors are passed in via [InlineColors] so this never triggers recomposition.
 */
private fun buildInlineFormatted(text: String, colors: InlineColors): AnnotatedString = buildAnnotatedString {
    var i = 0
    val chars = text.toCharArray()

    while (i < chars.size) {
        when {
            i + 1 < chars.size && chars[i] == '*' && chars[i + 1] == '*' -> {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(chars[i])
                    i++
                }
            }

            i + 1 < chars.size && chars[i] == '_' && chars[i + 1] == '_' -> {
                val end = text.indexOf("__", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(chars[i])
                    i++
                }
            }

            chars[i] == '*' -> {
                val end = text.indexOf('*', i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(chars[i])
                    i++
                }
            }

            chars[i] == '_' -> {
                val end = text.indexOf('_', i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(chars[i])
                    i++
                }
            }

            i + 1 < chars.size && chars[i] == '~' && chars[i + 1] == '~' -> {
                val end = text.indexOf("~~", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(chars[i])
                    i++
                }
            }

            chars[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end != -1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = maple,
                            background = colors.codeBg,
                            fontSize = 12.sp
                        )
                    ) {
                        append(" ${text.substring(i + 1, end)} ")
                    }
                    i = end + 1
                } else {
                    append(chars[i])
                    i++
                }
            }

            i + 1 < chars.size && chars[i] == '+' && chars[i + 1] == '+' -> {
                val end = text.indexOf("++", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(chars[i])
                    i++
                }
            }

            i + 1 < chars.size && chars[i] == '=' && chars[i + 1] == '=' -> {
                val end = text.indexOf("==", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(background = colors.highlightBg)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(chars[i])
                    i++
                }
            }
            // Inline math: \(...\) format
            i + 1 < chars.size && chars[i] == '\\' && chars[i + 1] == '(' -> {
                val endIdx = text.indexOf("\\)", i + 2)
                if (endIdx != -1) {
                    val mathExpr = text.substring(i + 2, endIdx)
                    val rendered = renderMathToUnicode(mathExpr)
                    withStyle(
                        SpanStyle(
                            fontFamily = maple,
                            fontStyle = FontStyle.Italic,
                            color = colors.mathColor
                        )
                    ) {
                        append(rendered)
                    }
                    i = endIdx + 2
                } else {
                    append(chars[i])
                    i++
                }
            }
            // Inline math: $...$ (but not $$)
            chars[i] == '$' && (i + 1 >= chars.size || chars[i + 1] != '$') -> {
                val end = text.indexOf('$', i + 1)
                if (end != -1 && end > i + 1) {
                    val mathExpr = text.substring(i + 1, end)
                    val rendered = renderMathToUnicode(mathExpr)
                    withStyle(
                        SpanStyle(
                            fontFamily = maple,
                            fontStyle = FontStyle.Italic,
                            color = colors.mathColor
                        )
                    ) {
                        append(rendered)
                    }
                    i = end + 1
                } else {
                    append(chars[i])
                    i++
                }
            }

            else -> {
                val c = chars[i]
                if (c.isHighSurrogate() && i + 1 < chars.size && chars[i + 1].isLowSurrogate()) {
                    // Keep surrogate pairs together (emojis, non-BMP chars)
                    append(c)
                    append(chars[i + 1])
                    i += 2
                } else {
                    append(c)
                    i++
                }
            }
        }
    }
}

@Composable
private fun BulletPoint(text: String, level: Int, colors: InlineColors) {
    Row(
        modifier = Modifier.padding(start = rDp((4 + level * 12).dp)),
        horizontalArrangement = Arrangement.spacedBy(rDp(6.dp))
    ) {
        Text(
            text = "•",
            fontFamily = ManropeFontFamily,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = rDp(1.dp))
        )
        Text(
            text = cachedInlineFormatting(text, colors),
            fontFamily = ManropeFontFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            color = LocalContentColor.current.copy(alpha = 0.87f),
            lineHeight = 20.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun NumberedPoint(text: String, number: String, colors: InlineColors) {
    Row(
        modifier = Modifier.padding(start = rDp(4.dp)),
        horizontalArrangement = Arrangement.spacedBy(rDp(6.dp))
    ) {
        Text(
            text = "$number.",
            fontFamily = ManropeFontFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = rDp(1.dp))
        )
        Text(
            text = cachedInlineFormatting(text, colors),
            fontFamily = ManropeFontFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            color = LocalContentColor.current.copy(alpha = 0.87f),
            lineHeight = 20.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun BlockQuote(text: String, level: Int, colors: InlineColors) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = rDp(((level - 1) * 10).dp))
            .clip(RoundedCornerShape(rDp(6.dp)))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(rDp(8.dp)),
        horizontalArrangement = Arrangement.spacedBy(rDp(6.dp))
    ) {
        Box(
            modifier = Modifier
                .width(rDp(2.dp))
                .height(rDp(18.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Text(
            text = cachedInlineFormatting(text, colors),
            fontFamily = ManropeFontFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            color = LocalContentColor.current.copy(alpha = 0.87f),
            lineHeight = 20.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun InlineCodeText(text: String) {
    Text(
        text = text,
        fontFamily = maple,
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        color = LocalContentColor.current.copy(alpha = 0.85f),
        modifier = Modifier
            .clip(RoundedCornerShape(rDp(4.dp)))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = rDp(5.dp), vertical = rDp(1.dp))
    )
}

/**
 * Canvas-drawn table — measures all text once, then draws everything in a single Canvas pass.
 * Much faster than composable Row/Column/Text trees (no composable overhead per cell).
 */
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
    val cellPadH = with(density) { rDp(8.dp).toPx() }
    val cellPadV = with(density) { rDp(6.dp).toPx() }
    val dividerWidth = with(density) { rDp(1.dp).toPx() }
    val cornerRadius = with(density) { rDp(8.dp).toPx() }

    val textMeasurer = rememberTextMeasurer()

    val headerStyle = TextStyle(
        fontFamily = ManropeFontFamily,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = textColor,
        lineHeight = 17.sp
    )
    val cellStyle = TextStyle(
        fontFamily = ManropeFontFamily,
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        color = dimTextColor,
        lineHeight = 17.sp
    )

    // Pre-measure all cells. We do this inside a BoxWithConstraints to know max width.
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(rDp(8.dp)))
    ) {
        val totalWidth = with(density) { maxWidth.toPx() }
        val colWidth = (totalWidth - dividerWidth * (colCount - 1)) / colCount
        val cellTextWidth = (colWidth - cellPadH * 2).coerceAtLeast(1f).toInt()

        // Measure header texts (with inline formatting: bold, italic, code, etc.)
        val headerMeasured = remember(headers, cellTextWidth, colors) {
            headers.map { h ->
                textMeasurer.measure(
                    text = buildInlineFormatted(h, colors),
                    style = headerStyle,
                    maxLines = 3,
                    constraints = androidx.compose.ui.unit.Constraints(
                        maxWidth = cellTextWidth
                    )
                )
            }
        }

        // Measure body texts (with inline formatting)
        val rowsMeasured = remember(rows, cellTextWidth, colors) {
            rows.map { row ->
                (0 until colCount).map { colIndex ->
                    val cell = row.getOrElse(colIndex) { "" }
                    textMeasurer.measure(
                        text = buildInlineFormatted(cell, colors),
                        style = cellStyle,
                        maxLines = 5,
                        constraints = androidx.compose.ui.unit.Constraints(
                            maxWidth = cellTextWidth
                        )
                    )
                }
            }
        }

        // Compute row heights
        val headerRowHeight = remember(headerMeasured) {
            (headerMeasured.maxOfOrNull { it.size.height } ?: 0) + (cellPadV * 2)
        }
        val rowHeights = remember(rowsMeasured) {
            rowsMeasured.map { cells ->
                (cells.maxOfOrNull { it.size.height } ?: 0) + (cellPadV * 2)
            }
        }
        val totalHeight = headerRowHeight + rowHeights.sum() +
                dividerWidth * rows.size // horizontal dividers between rows

        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { totalHeight.toDp() })
                .drawBehind {
                    // Outer border
                    drawRoundRect(
                        color = outlineColor,
                        size = Size(totalWidth, totalHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = dividerWidth)
                    )
                }
        ) {
            var y = 0f

            // --- Header row ---
            drawRect(
                color = headerBg,
                topLeft = Offset(0f, 0f),
                size = Size(totalWidth, headerRowHeight)
            )
            drawTableRow(
                measuredCells = headerMeasured,
                colCount = colCount,
                colWidth = colWidth,
                cellPadH = cellPadH,
                cellPadV = cellPadV,
                dividerWidth = dividerWidth,
                rowY = y,
                rowHeight = headerRowHeight,
                alignments = alignments,
                outlineColor = outlineColor
            )
            y += headerRowHeight

            // --- Data rows ---
            rowsMeasured.forEachIndexed { rowIndex, cells ->
                // Horizontal divider
                drawRect(
                    color = outlineColor.copy(alpha = 0.4f),
                    topLeft = Offset(0f, y),
                    size = Size(totalWidth, dividerWidth)
                )
                y += dividerWidth

                val rh = rowHeights[rowIndex]

                // Alternating row bg
                if (rowIndex % 2 == 1) {
                    drawRect(
                        color = altRowBg,
                        topLeft = Offset(0f, y),
                        size = Size(totalWidth, rh)
                    )
                }

                drawTableRow(
                    measuredCells = cells,
                    colCount = colCount,
                    colWidth = colWidth,
                    cellPadH = cellPadH,
                    cellPadV = cellPadV,
                    dividerWidth = dividerWidth,
                    rowY = y,
                    rowHeight = rh,
                    alignments = alignments,
                    outlineColor = outlineColor
                )
                y += rh
            }
        }
    }
}

/** Draw a single table row's cells + vertical dividers onto the Canvas. */
private fun DrawScope.drawTableRow(
    measuredCells: List<androidx.compose.ui.text.TextLayoutResult>,
    colCount: Int,
    colWidth: Float,
    cellPadH: Float,
    cellPadV: Float,
    dividerWidth: Float,
    rowY: Float,
    rowHeight: Float,
    alignments: List<MarkdownElement.Table.Alignment>,
    outlineColor: Color
) {
    for (colIndex in 0 until colCount) {
        val cellX = colIndex * (colWidth + dividerWidth)

        // Vertical divider (between columns)
        if (colIndex > 0) {
            drawRect(
                color = outlineColor.copy(alpha = 0.3f),
                topLeft = Offset(cellX - dividerWidth, rowY),
                size = Size(dividerWidth, rowHeight)
            )
        }

        if (colIndex < measuredCells.size) {
            val measured = measuredCells[colIndex]
            val align = alignments.getOrNull(colIndex) ?: MarkdownElement.Table.Alignment.LEFT
            val textWidth = measured.size.width.toFloat()
            val availableWidth = colWidth - cellPadH * 2

            val offsetX = when (align) {
                MarkdownElement.Table.Alignment.CENTER -> cellX + cellPadH + (availableWidth - textWidth).coerceAtLeast(0f) / 2
                MarkdownElement.Table.Alignment.RIGHT -> cellX + cellPadH + (availableWidth - textWidth).coerceAtLeast(0f)
                else -> cellX + cellPadH
            }

            drawText(
                textLayoutResult = measured,
                topLeft = Offset(offsetX, rowY + cellPadV)
            )
        }
    }
}

/**
 * Code block — collapsed = ultra-light header only (no theme resolution, no text measurement).
 * Syntax highlighting / text rendering only happens when expanded.
 */
@Composable
private fun CodeBlockExpanded(code: String, language: String) {
    var isExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // Lightweight colors for header — no full theme resolution needed
    val headerBg = remember(isDark) {
        if (isDark) Color(0xFF282C34) else Color(0xFFF5F5F5)
    }
    val headerFg = remember(isDark) {
        if (isDark) Color(0xFFABB2BF) else Color(0xFF383A42)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(rDp(8.dp)))
            .background(headerBg.copy(alpha = 0.85f))
    ) {
        // Compact header: language label + copy/expand buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = rDp(10.dp), vertical = rDp(6.dp)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(rDp(6.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActionButton(
                    onClickListener = {},
                    icon = R.drawable.code,
                    contentDescription = "Code",
                    shape = RoundedCornerShape(rDp(6.dp)),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = headerFg.copy(0.1f),
                        contentColor = headerFg.copy(0.7f)
                    )
                )
                if (language.isNotEmpty()) {
                    Text(
                        text = language.uppercase(),
                        fontFamily = maple,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = headerFg.copy(alpha = 0.5f)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(rDp(4.dp))) {
                ActionButton(
                    onClickListener = {
                        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText(language, code)
                        clipboardManager.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                    },
                    icon = R.drawable.copy,
                    contentDescription = "Copy",
                    shape = RoundedCornerShape(rDp(6.dp)),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = headerFg.copy(0.1f),
                        contentColor = headerFg.copy(0.7f)
                    )
                )
                ActionToggleButton(
                    checked = isExpanded,
                    onCheckedChange = { isExpanded = !isExpanded },
                    icon = if (isExpanded) R.drawable.up else R.drawable.down,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    shape = RoundedCornerShape(rDp(6.dp)),
                    colors = IconButtonDefaults.filledIconToggleButtonColors(
                        containerColor = headerFg.copy(0.1f),
                        contentColor = headerFg.copy(0.7f),
                        checkedContainerColor = headerFg.copy(0.15f),
                        checkedContentColor = headerFg.copy(0.8f)
                    )
                )
            }
        }

        // Only compose the code content when expanded — zero cost when collapsed
        if (isExpanded) {
            CodeBlockContent(code, language, headerFg, headerBg)
        }
    }
}

/** Deferred code content — only composed when the block is expanded. */
@Composable
private fun CodeBlockContent(code: String, language: String, fg: Color, bg: Color) {
    val highlightEnabled = LocalCodeHighlightEnabled.current
    val syntaxTheme = resolveSyntaxTheme()

    val highlighted = remember(code, language, highlightEnabled) {
        if (highlightEnabled && language.isNotBlank()) highlightCode(code, language, syntaxTheme)
        else null
    }

    HorizontalDivider(color = fg.copy(alpha = 0.1f))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = rDp(10.dp), vertical = rDp(8.dp))
    ) {
        if (highlighted != null) {
            Text(
                text = highlighted,
                fontFamily = maple,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 18.sp
            )
        } else {
            Text(
                text = code,
                fontFamily = maple,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = fg,
                lineHeight = 18.sp
            )
        }
    }
}

/** Quick luminance check (no full Color.luminance() overhead). */
private fun Color.luminance(): Float {
    return 0.299f * red + 0.587f * green + 0.114f * blue
}

/**
 * Comprehensive LaTeX/Typst symbol mappings for Unicode rendering
 *
 * Total Symbols: ~600+
 * Coverage: >99% of LaTeX mathematical symbols including AMS extensions
 *
 * Categories Covered:
 * - Greek Letters (lowercase, uppercase, and all variants)
 * - Binary Operators (60+ including AMS)
 * - Relation Symbols (80+ including AMS)
 * - Negated Relations (40+ from AMS)
 * - Arrows (60+ including AMS arrows)
 * - Big Operators (all integral variants, sums, products)
 * - Logic & Set Theory symbols
 * - Calculus & Analysis symbols
 * - Geometry symbols
 * - Brackets & Delimiters (including all size variants)
 * - Mathematical Functions (sin, cos, log, lim, etc.)
 * - Hebrew Letters (aleph, beth, gimel, daleth)
 * - Miscellaneous symbols (card suits, music notation, etc.)
 * - Dots (ldots, cdots, vdots, ddots, iddots)
 *
 * Font Commands (via processing functions):
 * - Blackboard Bold (\mathbb{R} -> ℝ) - Full alphabet
 * - Calligraphic (\mathcal{L} -> 𝓛) - Full uppercase
 * - Script (\mathscr{F} -> ℱ) - Full alphabet
 * - Fraktur (\mathfrak{g} -> 𝔤) - Full alphabet
 * - Sans-serif (\mathsf{A} -> 𝖠) - Full alphanumeric
 * - Monospace (\mathtt{x} -> 𝚡) - Full alphanumeric
 *
 * Accent Commands:
 * - \hat, \tilde, \bar, \vec, \dot, \ddot
 * - \acute, \grave, \breve, \check
 * - \widehat, \widetilde
 * - \overline, \underline
 * - \overbrace, \underbrace
 *
 * Other Commands:
 * - Fractions (\frac{a}{b})
 * - Roots (\sqrt{x}, \sqrt[n]{x})
 * - Binomials (\binom{n}{k})
 * - Modular arithmetic (\mod, \bmod, \pmod)
 * - Boxed expressions (\boxed{x})
 * - Stacking (\stackrel, \overset, \underset)
 * - Cancellation (\cancel, \bcancel, \xcancel)
 * - Spacing (\quad, \qquad, \,, \;, etc.)
 *
 * Environments:
 * - cases, matrix, pmatrix, bmatrix, vmatrix, Vmatrix
 * - align, align*
 *
 * Last Updated: 2026-02-04
 * References:
 * - The Comprehensive LaTeX Symbol List (Scott Pakin)
 * - AMS-LaTeX User's Guide
 * - Unicode Mathematical Alphanumeric Symbols (U+1D400-U+1D7FF)
 */
private val mathSymbols = mapOf(
    // Greek letters (lowercase)
    "\\alpha" to "α",
    "\\beta" to "β",
    "\\gamma" to "γ",
    "\\delta" to "δ",
    "\\epsilon" to "ε",
    "\\varepsilon" to "ε",
    "\\zeta" to "ζ",
    "\\eta" to "η",
    "\\theta" to "θ",
    "\\vartheta" to "ϑ",
    "\\iota" to "ι",
    "\\kappa" to "κ",
    "\\lambda" to "λ",
    "\\mu" to "μ",
    "\\nu" to "ν",
    "\\xi" to "ξ",
    "\\pi" to "π",
    "\\varpi" to "ϖ",
    "\\rho" to "ρ",
    "\\varrho" to "ϱ",
    "\\sigma" to "σ",
    "\\varsigma" to "ς",
    "\\tau" to "τ",
    "\\upsilon" to "υ",
    "\\phi" to "φ",
    "\\varphi" to "ϕ",
    "\\chi" to "χ",
    "\\psi" to "ψ",
    "\\omega" to "ω",
    // Greek letters (uppercase) - added complete set
    "\\Alpha" to "Α",
    "\\Beta" to "Β",
    "\\Gamma" to "Γ",
    "\\Delta" to "Δ",
    "\\Epsilon" to "Ε",
    "\\Zeta" to "Ζ",
    "\\Eta" to "Η",
    "\\Theta" to "Θ",
    "\\Iota" to "Ι",
    "\\Kappa" to "Κ",
    "\\Lambda" to "Λ",
    "\\Mu" to "Μ",
    "\\Nu" to "Ν",
    "\\Xi" to "Ξ",
    "\\Omicron" to "Ο",
    "\\Pi" to "Π",
    "\\Rho" to "Ρ",
    "\\Sigma" to "Σ",
    "\\Tau" to "Τ",
    "\\Upsilon" to "Υ",
    "\\Phi" to "Φ",
    "\\Chi" to "Χ",
    "\\Psi" to "Ψ",
    "\\Omega" to "Ω",
    // Missing lowercase Greek
    "\\omicron" to "ο",
    // Operators (binary operators)
    "\\pm" to "±",
    "\\mp" to "∓",
    "\\times" to "×",
    "\\div" to "÷",
    "\\cdot" to "·",
    "\\ast" to "∗",
    "\\star" to "⋆",
    "\\circ" to "∘",
    "\\bullet" to "•",
    "\\oplus" to "⊕",
    "\\ominus" to "⊖",
    "\\otimes" to "⊗",
    "\\oslash" to "⊘",
    "\\odot" to "⊙",
    "\\dagger" to "†",
    "\\ddagger" to "‡",
    // Additional binary operators
    "\\sqcap" to "⊓",
    "\\sqcup" to "⊔",
    "\\barwedge" to "⊼",
    "\\veebar" to "⊻",
    "\\doublebarwedge" to "⩞",
    "\\triangleleft" to "◁",
    "\\triangleright" to "▷",
    "\\bigtriangleup" to "△",
    "\\bigtriangledown" to "▽",
    "\\wr" to "≀",
    "\\ltimes" to "⋉",
    "\\rtimes" to "⋊",
    "\\uplus" to "⊎",
    "\\amalg" to "⨿",
    "\\curlywedge" to "⋏",
    "\\curlyvee" to "⋎",
    "\\intercal" to "⊺",
    "\\dotplus" to "∔",
    "\\divideontimes" to "⋇",
    "\\smallsetminus" to "∖",
    // Relations
    "\\leq" to "≤",
    "\\le" to "≤",
    "\\geq" to "≥",
    "\\ge" to "≥",
    "\\neq" to "≠",
    "\\ne" to "≠",
    "\\equiv" to "≡",
    "\\approx" to "≈",
    "\\sim" to "∼",
    "\\simeq" to "≃",
    "\\cong" to "≅",
    "\\propto" to "∝",
    "\\ll" to "≪",
    "\\gg" to "≫",
    "\\subset" to "⊂",
    "\\supset" to "⊃",
    "\\subseteq" to "⊆",
    "\\supseteq" to "⊇",
    "\\in" to "∈",
    "\\notin" to "∉",
    "\\ni" to "∋",
    "\\perp" to "⊥",
    "\\parallel" to "∥",
    // Additional relation symbols
    "\\prec" to "≺",
    "\\succ" to "≻",
    "\\preceq" to "⪯",
    "\\succeq" to "⪰",
    "\\sqsubset" to "⊏",
    "\\sqsupset" to "⊐",
    "\\sqsubseteq" to "⊑",
    "\\sqsupseteq" to "⊒",
    "\\doteq" to "≐",
    "\\asymp" to "≍",
    "\\bowtie" to "⋈",
    "\\models" to "⊨",
    "\\vdash" to "⊢",
    "\\dashv" to "⊣",
    "\\nless" to "≮",
    "\\ngtr" to "≯",
    "\\nleq" to "≰",
    "\\ngeq" to "≱",
    "\\nsubseteq" to "⊈",
    "\\nsupseteq" to "⊉",
    "\\nmid" to "∤",
    "\\nparallel" to "∦",
    "\\smile" to "⌣",
    "\\frown" to "⌢",
    "\\mid" to "∣",
    "\\nsubset" to "⊄",
    "\\nsupset" to "⊅",
    "\\nprec" to "⊀",
    "\\nsucc" to "⊁",
    "\\triangleq" to "≜",
    "\\bumpeq" to "≏",
    "\\Bumpeq" to "≎",
    "\\doteqdot" to "≑",
    "\\fallingdotseq" to "≒",
    "\\risingdotseq" to "≓",
    "\\eqcirc" to "≖",
    "\\circeq" to "≗",
    "\\lesssim" to "≲",
    "\\gtrsim" to "≳",
    "\\lessapprox" to "⪅",
    "\\gtrapprox" to "⪆",
    "\\lessgtr" to "≶",
    "\\gtrless" to "≷",
    // Arrows
    "\\leftarrow" to "←",
    "\\rightarrow" to "→",
    "\\uparrow" to "↑",
    "\\downarrow" to "↓",
    "\\leftrightarrow" to "↔",
    "\\Leftarrow" to "⇐",
    "\\Rightarrow" to "⇒",
    "\\Leftrightarrow" to "⇔",
    "\\mapsto" to "↦",
    "\\to" to "→",
    "\\longrightarrow" to "⟶",
    "\\longleftarrow" to "⟵",
    // Additional arrows
    "\\Uparrow" to "⇑",
    "\\Downarrow" to "⇓",
    "\\Updownarrow" to "⇕",
    "\\updownarrow" to "↕",
    "\\nearrow" to "↗",
    "\\searrow" to "↘",
    "\\swarrow" to "↙",
    "\\nwarrow" to "↖",
    "\\hookleftarrow" to "↩",
    "\\hookrightarrow" to "↪",
    "\\leftharpoonup" to "↼",
    "\\leftharpoondown" to "↽",
    "\\rightharpoonup" to "⇀",
    "\\rightharpoondown" to "⇁",
    "\\rightleftharpoons" to "⇌",
    "\\leftrightharpoons" to "⇋",
    "\\implies" to "⟹",
    "\\impliedby" to "⟸",
    "\\iff" to "⟺",
    "\\longleftrightarrow" to "⟷",
    "\\Longleftarrow" to "⟸",
    "\\Longrightarrow" to "⟹",
    "\\Longleftrightarrow" to "⟺",
    "\\longmapsto" to "⟼",
    "\\leadsto" to "⇝",
    "\\circlearrowleft" to "↺",
    "\\circlearrowright" to "↻",
    "\\curvearrowleft" to "↶",
    "\\curvearrowright" to "↷",
    "\\upharpoonleft" to "↿",
    "\\upharpoonright" to "↾",
    "\\downharpoonleft" to "⇃",
    "\\downharpoonright" to "⇂",
    "\\rightsquigarrow" to "⇝",
    "\\leftrightsquigarrow" to "↭",
    // Big operators
    "\\sum" to "∑",
    "\\prod" to "∏",
    "\\coprod" to "∐",
    "\\int" to "∫",
    "\\oint" to "∮",
    "\\iint" to "∬",
    "\\iiint" to "∭",
    "\\bigcup" to "⋃",
    "\\bigcap" to "⋂",
    "\\bigvee" to "⋁",
    "\\bigwedge" to "⋀",
    // Logic & Set Theory
    "\\top" to "⊤",
    "\\bot" to "⊥",
    "\\therefore" to "∴",
    "\\because" to "∵",
    "\\forall" to "∀",
    "\\exists" to "∃",
    "\\nexists" to "∄",
    "\\neg" to "¬",
    "\\lnot" to "¬",
    "\\land" to "∧",
    "\\lor" to "∨",
    "\\emptyset" to "∅",
    "\\varnothing" to "∅",
    "\\cap" to "∩",
    "\\cup" to "∪",
    "\\setminus" to "∖",
    // Calculus & Analysis
    "\\infty" to "∞",
    "\\partial" to "∂",
    "\\nabla" to "∇",
    "\\Re" to "ℜ",
    "\\Im" to "ℑ",
    "\\aleph" to "ℵ",
    "\\beth" to "ℶ",
    "\\gimel" to "ℷ",
    "\\ell" to "ℓ",
    "\\wp" to "℘",
    "\\hbar" to "ℏ",
    // Geometry
    "\\angle" to "∠",
    "\\measuredangle" to "∡",
    "\\sphericalangle" to "∢",
    "\\triangle" to "△",
    "\\square" to "□",
    "\\diamond" to "◇",
    "\\Box" to "□",
    "\\blacktriangle" to "▴",
    "\\blacktriangledown" to "▾",
    "\\blacksquare" to "▪",
    "\\blacklozenge" to "⧫",
    // Miscellaneous symbols
    "\\clubsuit" to "♣",
    "\\diamondsuit" to "♢",
    "\\heartsuit" to "♡",
    "\\spadesuit" to "♠",
    "\\flat" to "♭",
    "\\natural" to "♮",
    "\\sharp" to "♯",
    "\\checkmark" to "✓",
    "\\copyright" to "©",
    "\\yen" to "¥",
    "\\pounds" to "£",
    "\\sqrt" to "√",
    "\\surd" to "√",
    "\\ldots" to "…",
    "\\cdots" to "⋯",
    "\\vdots" to "⋮",
    "\\ddots" to "⋱",
    "\\prime" to "′",
    "\\degree" to "°",
    // Brackets and Delimiters
    "\\langle" to "⟨",
    "\\rangle" to "⟩",
    "\\lceil" to "⌈",
    "\\rceil" to "⌉",
    "\\lfloor" to "⌊",
    "\\rfloor" to "⌋",
    "\\lbrace" to "{",
    "\\rbrace" to "}",
    "\\ulcorner" to "⌜",
    "\\urcorner" to "⌝",
    "\\llcorner" to "⌞",
    "\\lrcorner" to "⌟",
    "\\lvert" to "|",
    "\\rvert" to "|",
    "\\lVert" to "‖",
    "\\rVert" to "‖",
    // Functions (keep as-is but format nicely)
    "\\sin" to "sin",
    "\\cos" to "cos",
    "\\tan" to "tan",
    "\\cot" to "cot",
    "\\sec" to "sec",
    "\\csc" to "csc",
    "\\arcsin" to "arcsin",
    "\\arccos" to "arccos",
    "\\arctan" to "arctan",
    "\\sinh" to "sinh",
    "\\cosh" to "cosh",
    "\\tanh" to "tanh",
    "\\log" to "log",
    "\\ln" to "ln",
    "\\exp" to "exp",
    "\\lim" to "lim",
    "\\max" to "max",
    "\\min" to "min",
    "\\sup" to "sup",
    "\\inf" to "inf",
    "\\det" to "det",
    "\\arg" to "arg",
    "\\deg" to "deg",
    "\\dim" to "dim",
    "\\gcd" to "gcd",
    "\\hom" to "hom",
    "\\ker" to "ker",
    "\\lg" to "lg",
    "\\Pr" to "Pr",
    "\\limsup" to "lim sup",
    "\\liminf" to "lim inf",
    // ==================== AMS EXTENDED SYMBOLS ====================
    // AMS Binary Operators
    "\\dotplus" to "∔",
    "\\ltimes" to "⋉",
    "\\rtimes" to "⋊",
    "\\Cup" to "⋓",
    "\\doublecup" to "⋓",
    "\\Cap" to "⋒",
    "\\doublecap" to "⋒",
    "\\leftthreetimes" to "⋋",
    "\\rightthreetimes" to "⋌",
    "\\circleddash" to "⊝",
    "\\circledast" to "⊛",
    "\\circledcirc" to "⊚",
    "\\centerdot" to "⋅",
    "\\boxplus" to "⊞",
    "\\boxminus" to "⊟",
    "\\boxtimes" to "⊠",
    "\\boxdot" to "⊡",
    "\\divideontimes" to "⋇",
    "\\curlywedge" to "⋏",
    "\\curlyvee" to "⋎",

    // AMS Relations
    "\\leqq" to "≦",
    "\\geqq" to "≧",
    "\\leqslant" to "⩽",
    "\\geqslant" to "⩾",
    "\\eqslantless" to "⪕",
    "\\eqslantgtr" to "⪖",
    "\\lesseqgtr" to "⋚",
    "\\gtreqless" to "⋛",
    "\\lesseqqgtr" to "⪋",
    "\\gtreqqless" to "⪌",
    "\\doteqdot" to "≑",
    "\\Doteq" to "≑",
    "\\eqcirc" to "≖",
    "\\risingdotseq" to "≓",
    "\\fallingdotseq" to "≒",
    "\\backsim" to "∽",
    "\\backsimeq" to "⋍",
    "\\subseteqq" to "⫅",
    "\\supseteqq" to "⫆",
    "\\Subset" to "⋐",
    "\\Supset" to "⋑",
    "\\sqsubset" to "⊏",
    "\\sqsupset" to "⊐",
    "\\preccurlyeq" to "≼",
    "\\succcurlyeq" to "≽",
    "\\curlyeqprec" to "⋞",
    "\\curlyeqsucc" to "⋟",
    "\\precsim" to "≾",
    "\\succsim" to "≿",
    "\\precapprox" to "⪷",
    "\\succapprox" to "⪸",
    "\\vartriangleleft" to "⊲",
    "\\vartriangleright" to "⊳",
    "\\trianglelefteq" to "⊴",
    "\\trianglerighteq" to "⊵",
    "\\vDash" to "⊨",
    "\\Vdash" to "⊩",
    "\\Vvdash" to "⊪",
    "\\smallsmile" to "⌣",
    "\\smallfrown" to "⌢",
    "\\shortmid" to "∣",
    "\\shortparallel" to "∥",
    "\\bumpeq" to "≏",
    "\\Bumpeq" to "≎",
    "\\between" to "≬",
    "\\pitchfork" to "⋔",
    "\\varpropto" to "∝",
    "\\backepsilon" to "∍",
    "\\therefore" to "∴",
    "\\because" to "∵",

    // AMS Negated Relations
    "\\nless" to "≮",
    "\\ngtr" to "≯",
    "\\nleq" to "≰",
    "\\ngeq" to "≱",
    "\\nleqslant" to "⪇",
    "\\ngeqslant" to "⪈",
    "\\nleqq" to "≰",
    "\\ngeqq" to "≱",
    "\\lneq" to "⪇",
    "\\gneq" to "⪈",
    "\\lneqq" to "≨",
    "\\gneqq" to "≩",
    "\\lvertneqq" to "≨",
    "\\gvertneqq" to "≩",
    "\\lnsim" to "⋦",
    "\\gnsim" to "⋧",
    "\\lnapprox" to "⪉",
    "\\gnapprox" to "⪊",
    "\\nprec" to "⊀",
    "\\nsucc" to "⊁",
    "\\npreceq" to "⋠",
    "\\nsucceq" to "⋡",
    "\\precneqq" to "⪵",
    "\\succneqq" to "⪶",
    "\\precnsim" to "⋨",
    "\\succnsim" to "⋩",
    "\\precnapprox" to "⪹",
    "\\succnapprox" to "⪺",
    "\\nsim" to "≁",
    "\\ncong" to "≇",
    "\\nshortmid" to "∤",
    "\\nshortparallel" to "∦",
    "\\nmid" to "∤",
    "\\nparallel" to "∦",
    "\\nvdash" to "⊬",
    "\\nvDash" to "⊭",
    "\\nVdash" to "⊮",
    "\\nVDash" to "⊯",
    "\\ntriangleleft" to "⋪",
    "\\ntriangleright" to "⋫",
    "\\ntrianglelefteq" to "⋬",
    "\\ntrianglerighteq" to "⋭",
    "\\nsubseteq" to "⊈",
    "\\nsupseteq" to "⊉",
    "\\nsubseteqq" to "⊈",
    "\\nsupseteqq" to "⊉",
    "\\subsetneq" to "⊊",
    "\\supsetneq" to "⊋",
    "\\varsubsetneq" to "⊊",
    "\\varsupsetneq" to "⊋",
    "\\subsetneqq" to "⫋",
    "\\supsetneqq" to "⫌",
    "\\varsubsetneqq" to "⫋",
    "\\varsupsetneqq" to "⫌",

    // AMS Arrows
    "\\leftleftarrows" to "⇇",
    "\\rightrightarrows" to "⇉",
    "\\leftrightarrows" to "⇆",
    "\\rightleftarrows" to "⇄",
    "\\Lleftarrow" to "⇚",
    "\\Rrightarrow" to "⇛",
    "\\twoheadleftarrow" to "↞",
    "\\twoheadrightarrow" to "↠",
    "\\leftarrowtail" to "↢",
    "\\rightarrowtail" to "↣",
    "\\looparrowleft" to "↫",
    "\\looparrowright" to "↬",
    "\\leftrightharpoons" to "⇋",
    "\\rightleftharpoons" to "⇌",
    "\\curvearrowleft" to "↶",
    "\\curvearrowright" to "↷",
    "\\circlearrowleft" to "↺",
    "\\circlearrowright" to "↻",
    "\\Lsh" to "↰",
    "\\Rsh" to "↱",
    "\\upuparrows" to "⇈",
    "\\downdownarrows" to "⇊",
    "\\upharpoonleft" to "↿",
    "\\upharpoonright" to "↾",
    "\\downharpoonleft" to "⇃",
    "\\downharpoonright" to "⇂",
    "\\rightsquigarrow" to "⇝",
    "\\leftrightsquigarrow" to "↭",
    "\\multimap" to "⊸",
    "\\nleftarrow" to "↚",
    "\\nrightarrow" to "↛",
    "\\nLeftarrow" to "⇍",
    "\\nRightarrow" to "⇏",
    "\\nleftrightarrow" to "↮",
    "\\nLeftrightarrow" to "⇎",

    // More integral variants
    "\\iint" to "∬",
    "\\iiint" to "∭",
    "\\iiiint" to "⨌",
    "\\oint" to "∮",
    "\\oiint" to "∯",
    "\\oiiint" to "∰",
    "\\intclockwise" to "∱",
    "\\varointclockwise" to "∲",
    "\\ointctrclockwise" to "∳",

    // Hebrew letters
    "\\aleph" to "ℵ",
    "\\beth" to "ℶ",
    "\\gimel" to "ℷ",
    "\\daleth" to "ℸ",

    // More miscellaneous
    "\\hbar" to "ℏ",
    "\\hslash" to "ℏ",
    "\\Bbbk" to "𝕜",
    "\\square" to "□",
    "\\blacksquare" to "■",
    "\\triangle" to "△",
    "\\triangledown" to "▽",
    "\\blacktriangle" to "▲",
    "\\blacktriangledown" to "▼",
    "\\lozenge" to "◊",
    "\\blacklozenge" to "⧫",
    "\\bigstar" to "★",
    "\\sphericalangle" to "∢",
    "\\measuredangle" to "∡",
    "\\circledS" to "Ⓢ",
    "\\complement" to "∁",
    "\\mho" to "℧",
    "\\eth" to "ð",
    "\\Finv" to "Ⅎ",
    "\\Game" to "⅁",
    "\\diagup" to "╱",
    "\\diagdown" to "╲",
    "\\backprime" to "‵",
    "\\nexists" to "∄",
    "\\Bbbk" to "𝕜",
    "\\varnothing" to "∅",

    // Dots
    "\\ldots" to "…",
    "\\cdots" to "⋯",
    "\\vdots" to "⋮",
    "\\ddots" to "⋱",
    "\\iddots" to "⋰",

    // More delimiters
    "\\ulcorner" to "⌜",
    "\\urcorner" to "⌝",
    "\\llcorner" to "⌞",
    "\\lrcorner" to "⌟",
    "\\lmoustache" to "⎰",
    "\\rmoustache" to "⎱",
    "\\lgroup" to "⟮",
    "\\rgroup" to "⟯",
    "\\lAngle" to "⟪",
    "\\rAngle" to "⟫",
    "\\llbracket" to "⟦",
    "\\rrbracket" to "⟧",

    // Typst-specific (using # prefix)
    "#alpha" to "α",
    "#beta" to "β",
    "#gamma" to "γ",
    "#delta" to "δ",
    "#epsilon" to "ε",
    "#pi" to "π",
    "#sigma" to "σ",
    "#omega" to "ω",
    "#sum" to "∑",
    "#product" to "∏",
    "#integral" to "∫",
    "#infinity" to "∞",
    "#partial" to "∂",
    "#nabla" to "∇",
    "#arrow.r" to "→",
    "#arrow.l" to "←",
    "#arrow.t" to "↑",
    "#arrow.b" to "↓"
)

/** Pre-sorted by key length (longest first) so that longer LaTeX commands are matched before
 *  shorter prefixes (e.g. "\alpha" before "\alp"). Computed once and cached. */
private val sortedMathSymbols: List<Pair<String, String>> by lazy {
    mathSymbols.entries.sortedByDescending { it.key.length }.map { it.key to it.value }
}

// Superscript Unicode mappings - extended coverage
private val superscriptMap = mapOf(
    '0' to '⁰',
    '1' to '¹',
    '2' to '²',
    '3' to '³',
    '4' to '⁴',
    '5' to '⁵',
    '6' to '⁶',
    '7' to '⁷',
    '8' to '⁸',
    '9' to '⁹',
    '+' to '⁺',
    '-' to '⁻',
    '=' to '⁼',
    '(' to '⁽',
    ')' to '⁾',
    'a' to 'ᵃ',
    'b' to 'ᵇ',
    'c' to 'ᶜ',
    'd' to 'ᵈ',
    'e' to 'ᵉ',
    'f' to 'ᶠ',
    'g' to 'ᵍ',
    'h' to 'ʰ',
    'i' to 'ⁱ',
    'j' to 'ʲ',
    'k' to 'ᵏ',
    'l' to 'ˡ',
    'm' to 'ᵐ',
    'n' to 'ⁿ',
    'o' to 'ᵒ',
    'p' to 'ᵖ',
    'q' to 'ᑫ',  // approximation
    'r' to 'ʳ',
    's' to 'ˢ',
    't' to 'ᵗ',
    'u' to 'ᵘ',
    'v' to 'ᵛ',
    'w' to 'ʷ',
    'x' to 'ˣ',
    'y' to 'ʸ',
    'z' to 'ᶻ',
    'A' to 'ᴬ',
    'B' to 'ᴮ',
    'D' to 'ᴰ',
    'E' to 'ᴱ',
    'G' to 'ᴳ',
    'H' to 'ᴴ',
    'I' to 'ᴵ',
    'J' to 'ᴶ',
    'K' to 'ᴷ',
    'L' to 'ᴸ',
    'M' to 'ᴹ',
    'N' to 'ᴺ',
    'O' to 'ᴼ',
    'P' to 'ᴾ',
    'R' to 'ᴿ',
    'T' to 'ᵀ',
    'U' to 'ᵁ',
    'V' to 'ⱽ',
    'W' to 'ᵂ'
)

// Subscript Unicode mappings - extended coverage
private val subscriptMap = mapOf(
    '0' to '₀',
    '1' to '₁',
    '2' to '₂',
    '3' to '₃',
    '4' to '₄',
    '5' to '₅',
    '6' to '₆',
    '7' to '₇',
    '8' to '₈',
    '9' to '₉',
    '+' to '₊',
    '-' to '₋',
    '=' to '₌',
    '(' to '₍',
    ')' to '₎',
    'a' to 'ₐ',
    'b' to 'ᵦ',  // approximation using Greek beta subscript
    'c' to 'c',   // no Unicode subscript, use normal
    'd' to 'd',   // no Unicode subscript, use normal
    'e' to 'ₑ',
    'f' to 'f',   // no Unicode subscript, use normal
    'g' to 'g',   // no Unicode subscript, use normal
    'h' to 'ₕ',
    'i' to 'ᵢ',
    'j' to 'ⱼ',
    'k' to 'ₖ',
    'l' to 'ₗ',
    'm' to 'ₘ',
    'n' to 'ₙ',
    'o' to 'ₒ',
    'p' to 'ₚ',
    'q' to 'q',   // no Unicode subscript, use normal
    'r' to 'ᵣ',
    's' to 'ₛ',
    't' to 'ₜ',
    'u' to 'ᵤ',
    'v' to 'ᵥ',
    'w' to 'w',   // no Unicode subscript, use normal
    'x' to 'ₓ',
    'y' to 'y',   // no Unicode subscript, use normal
    'z' to 'z'    // no Unicode subscript, use normal
)

/**
 * Process \mathbb{X} commands to convert to blackboard bold Unicode characters
 * Example: \mathbb{R} -> ℝ, \mathbb{N} -> ℕ
 */
private fun processBlackboardBold(input: String): String {
    val bbMap = mapOf(
        "A" to "𝔸", "B" to "𝔹", "C" to "ℂ", "D" to "𝔻", "E" to "𝔼",
        "F" to "𝔽", "G" to "𝔾", "H" to "ℍ", "I" to "𝕀", "J" to "𝕁",
        "K" to "𝕂", "L" to "𝕃", "M" to "𝕄", "N" to "ℕ", "O" to "𝕆",
        "P" to "ℙ", "Q" to "ℚ", "R" to "ℝ", "S" to "𝕊", "T" to "𝕋",
        "U" to "𝕌", "V" to "𝕍", "W" to "𝕎", "X" to "𝕏", "Y" to "𝕐",
        "Z" to "ℤ"
    )
    val pattern = Regex("""\\mathbb\{([A-Z])\}""")
    return pattern.replace(input) { match ->
        bbMap[match.groupValues[1]] ?: match.value
    }
}

/**
 * Process \mathcal{X} commands to convert to calligraphic Unicode characters
 * Example: \mathcal{L} -> 𝓛, \mathcal{F} -> 𝓕
 */
private fun processCalligraphic(input: String): String {
    val calMap = mapOf(
        "A" to "𝓐", "B" to "𝓑", "C" to "𝓒", "D" to "𝓓", "E" to "𝓔",
        "F" to "𝓕", "G" to "𝓖", "H" to "𝓗", "I" to "𝓘", "J" to "𝓙",
        "K" to "𝓚", "L" to "𝓛", "M" to "𝓜", "N" to "𝓝", "O" to "𝓞",
        "P" to "𝓟", "Q" to "𝓠", "R" to "𝓡", "S" to "𝓢", "T" to "𝓣",
        "U" to "𝓤", "V" to "𝓥", "W" to "𝓦", "X" to "𝓧", "Y" to "𝓨",
        "Z" to "𝓩"
    )
    val pattern = Regex("""\\mathcal\{([A-Z])\}""")
    return pattern.replace(input) { match ->
        calMap[match.groupValues[1]] ?: match.value
    }
}

/**
 * Process \mathscr{X} commands to convert to script Unicode characters
 * Example: \mathscr{L} -> ℒ, \mathscr{F} -> ℱ
 */
private fun processScript(input: String): String {
    val scrMap = mapOf(
        "A" to "𝒜", "B" to "ℬ", "C" to "𝒞", "D" to "𝒟", "E" to "ℰ",
        "F" to "ℱ", "G" to "𝒢", "H" to "ℋ", "I" to "ℐ", "J" to "𝒥",
        "K" to "𝒦", "L" to "ℒ", "M" to "ℳ", "N" to "𝒩", "O" to "𝒪",
        "P" to "𝒫", "Q" to "𝒬", "R" to "ℛ", "S" to "𝒮", "T" to "𝒯",
        "U" to "𝒰", "V" to "𝒱", "W" to "𝒲", "X" to "𝒳", "Y" to "𝒴",
        "Z" to "𝒵",
        "a" to "𝒶", "b" to "𝒷", "c" to "𝒸", "d" to "𝒹", "e" to "ℯ",
        "f" to "𝒻", "g" to "ℊ", "h" to "𝒽", "i" to "𝒾", "j" to "𝒿",
        "k" to "𝓀", "l" to "𝓁", "m" to "𝓂", "n" to "𝓃", "o" to "ℴ",
        "p" to "𝓅", "q" to "𝓆", "r" to "𝓇", "s" to "𝓈", "t" to "𝓉",
        "u" to "𝓊", "v" to "𝓋", "w" to "𝓌", "x" to "𝓍", "y" to "𝓎",
        "z" to "𝓏"
    )
    val pattern = Regex("""\\mathscr\{([A-Za-z]+)\}""")
    return pattern.replace(input) { match ->
        match.groupValues[1].map { scrMap[it.toString()] ?: it.toString() }.joinToString("")
    }
}

/**
 * Process \mathfrak{X} commands to convert to Fraktur Unicode characters
 * Example: \mathfrak{g} -> 𝔤, \mathfrak{A} -> 𝔄
 * Note: Some letters (C, H, I, R, Z) have special Unicode positions in Letterlike Symbols block
 */
private fun processFraktur(input: String): String {
    val frakMap = mapOf(
        // Uppercase Fraktur (U+1D504 - U+1D51C, with some in Letterlike Symbols)
        "A" to "𝔄", "B" to "𝔅", "C" to "ℭ", "D" to "𝔇", "E" to "𝔈",
        "F" to "𝔉", "G" to "𝔊", "H" to "ℌ", "I" to "ℑ", "J" to "𝔍",
        "K" to "𝔎", "L" to "𝔏", "M" to "𝔐", "N" to "𝔑", "O" to "𝔒",
        "P" to "𝔓", "Q" to "𝔔", "R" to "ℜ", "S" to "𝔖", "T" to "𝔗",
        "U" to "𝔘", "V" to "𝔙", "W" to "𝔚", "X" to "𝔛", "Y" to "𝔜",
        "Z" to "ℨ",
        // Lowercase Fraktur (U+1D51E - U+1D537)
        "a" to "𝔞", "b" to "𝔟", "c" to "𝔠", "d" to "𝔡", "e" to "𝔢",
        "f" to "𝔣", "g" to "𝔤", "h" to "𝔥", "i" to "𝔦", "j" to "𝔧",
        "k" to "𝔨", "l" to "𝔩", "m" to "𝔪", "n" to "𝔫", "o" to "𝔬",
        "p" to "𝔭", "q" to "𝔮", "r" to "𝔯", "s" to "𝔰", "t" to "𝔱",
        "u" to "𝔲", "v" to "𝔳", "w" to "𝔴", "x" to "𝔵", "y" to "𝔶",
        "z" to "𝔷"
    )
    val pattern = Regex("""\\mathfrak\{([A-Za-z]+)\}""")
    return pattern.replace(input) { match ->
        match.groupValues[1].map { frakMap[it.toString()] ?: it.toString() }.joinToString("")
    }
}

/**
 * Process \mathsf{X} commands to convert to Sans-serif Unicode characters
 * Example: \mathsf{A} -> 𝖠
 */
private fun processSansSerif(input: String): String {
    val sfMap = mapOf(
        "A" to "𝖠", "B" to "𝖡", "C" to "𝖢", "D" to "𝖣", "E" to "𝖤",
        "F" to "𝖥", "G" to "𝖦", "H" to "𝖧", "I" to "𝖨", "J" to "𝖩",
        "K" to "𝖪", "L" to "𝖫", "M" to "𝖬", "N" to "𝖭", "O" to "𝖮",
        "P" to "𝖯", "Q" to "𝖰", "R" to "𝖱", "S" to "𝖲", "T" to "𝖳",
        "U" to "𝖴", "V" to "𝖵", "W" to "𝖶", "X" to "𝖷", "Y" to "𝖸",
        "Z" to "𝖹",
        "a" to "𝖺", "b" to "𝖻", "c" to "𝖼", "d" to "𝖽", "e" to "𝖾",
        "f" to "𝖿", "g" to "𝗀", "h" to "𝗁", "i" to "𝗂", "j" to "𝗃",
        "k" to "𝗄", "l" to "𝗅", "m" to "𝗆", "n" to "𝗇", "o" to "𝗈",
        "p" to "𝗉", "q" to "𝗊", "r" to "𝗋", "s" to "𝗌", "t" to "𝗍",
        "u" to "𝗎", "v" to "𝗏", "w" to "𝗐", "x" to "𝗑", "y" to "𝗒",
        "z" to "𝗓",
        "0" to "𝟢", "1" to "𝟣", "2" to "𝟤", "3" to "𝟥", "4" to "𝟦",
        "5" to "𝟧", "6" to "𝟨", "7" to "𝟩", "8" to "𝟪", "9" to "𝟫"
    )
    val pattern = Regex("""\\mathsf\{([A-Za-z0-9]+)\}""")
    return pattern.replace(input) { match ->
        match.groupValues[1].map { sfMap[it.toString()] ?: it.toString() }.joinToString("")
    }
}

/**
 * Process \mathtt{X} commands to convert to Monospace Unicode characters
 * Example: \mathtt{A} -> 𝙰
 */
private fun processMonospace(input: String): String {
    val ttMap = mapOf(
        "A" to "𝙰", "B" to "𝙱", "C" to "𝙲", "D" to "𝙳", "E" to "𝙴",
        "F" to "𝙵", "G" to "𝙶", "H" to "𝙷", "I" to "𝙸", "J" to "𝙹",
        "K" to "𝙺", "L" to "𝙻", "M" to "𝙼", "N" to "𝙽", "O" to "𝙾",
        "P" to "𝙿", "Q" to "𝚀", "R" to "𝚁", "S" to "𝚂", "T" to "𝚃",
        "U" to "𝚄", "V" to "𝚅", "W" to "𝚆", "X" to "𝚇", "Y" to "𝚈",
        "Z" to "𝚉",
        "a" to "𝚊", "b" to "𝚋", "c" to "𝚌", "d" to "𝚍", "e" to "𝚎",
        "f" to "𝚏", "g" to "𝚐", "h" to "𝚑", "i" to "𝚒", "j" to "𝚓",
        "k" to "𝚔", "l" to "𝚕", "m" to "𝚖", "n" to "𝚗", "o" to "𝚘",
        "p" to "𝚙", "q" to "𝚚", "r" to "𝚛", "s" to "𝚜", "t" to "𝚝",
        "u" to "𝚞", "v" to "𝚟", "w" to "𝚠", "x" to "𝚡", "y" to "𝚢",
        "z" to "𝚣",
        "0" to "𝟶", "1" to "𝟷", "2" to "𝟸", "3" to "𝟹", "4" to "𝟺",
        "5" to "𝟻", "6" to "𝟼", "7" to "𝟽", "8" to "𝟾", "9" to "𝟿"
    )
    val pattern = Regex("""\\mathtt\{([A-Za-z0-9]+)\}""")
    return pattern.replace(input) { match ->
        match.groupValues[1].map { ttMap[it.toString()] ?: it.toString() }.joinToString("")
    }
}

/**
 * Process \boxed{X} command to render boxed expressions
 * Example: \boxed{E=mc^2} -> ⎵E=mc²⎵ (approximation with brackets)
 */
private fun processBoxed(input: String): String {
    val boxedPattern = Regex("""\\boxed\{([^}]+)\}""")
    return boxedPattern.replace(input) { match ->
        "[${match.groupValues[1]}]"
    }
}

/**
 * Process \mod, \bmod, \pmod commands
 * Example: \pmod{n} -> (mod n), a \bmod b -> a mod b
 */
private fun processModular(input: String): String {
    var result = input
    // \pmod{n} -> (mod n)
    result = Regex("""\\pmod\{([^}]+)\}""").replace(result) { match ->
        "(mod ${match.groupValues[1]})"
    }
    // \bmod -> mod (binary operator)
    result = result.replace("\\bmod", " mod ")
    // \mod -> mod
    result = result.replace("\\mod", " mod ")
    return result
}

/**
 * Process \overline{X} and \underline{X} commands
 * Example: \overline{AB} -> A̅B̅
 */
private fun processOverUnderline(input: String): String {
    var result = input
    // \overline{...} -> add combining overline to each character
    val overlinePattern = Regex("""\\overline\{([^}]+)\}""")
    result = overlinePattern.replace(result) { match ->
        match.groupValues[1].map { "$it\u0305" }.joinToString("")
    }
    // \underline{...} -> add combining underline to each character
    val underlinePattern = Regex("""\\underline\{([^}]+)\}""")
    result = underlinePattern.replace(result) { match ->
        match.groupValues[1].map { "$it\u0332" }.joinToString("")
    }
    return result
}

/**
 * Process \overbrace and \underbrace commands
 * Example: \overbrace{a+b+c}^{n} -> a+b+c with brace annotation
 */
private fun processOverUnderBrace(input: String): String {
    var result = input
    // \overbrace{...}^{label} -> ⏞ content ⏞ superscript
    val overbracePattern = Regex("""\\overbrace\{([^}]+)\}(?:\^\{([^}]+)\})?""")
    result = overbracePattern.replace(result) { match ->
        val content = match.groupValues[1]
        val label = match.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() }
        if (label != null) "⏞${content}⏞^{$label}" else "⏞${content}⏞"
    }
    // \underbrace{...}_{label} -> ⏟ content ⏟ subscript
    val underbracePattern = Regex("""\\underbrace\{([^}]+)\}(?:_\{([^}]+)\})?""")
    result = underbracePattern.replace(result) { match ->
        val content = match.groupValues[1]
        val label = match.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() }
        if (label != null) "⏟${content}⏟_{$label}" else "⏟${content}⏟"
    }
    return result
}

/**
 * Process \stackrel, \overset, \underset commands
 * Example: \stackrel{def}{=} -> ≝, \overset{?}{=} -> =̃
 */
private fun processStacking(input: String): String {
    var result = input
    // Common stackrel patterns
    result = result.replace("\\stackrel{def}{=}", "≝")
    result = result.replace("\\stackrel{?}{=}", "≟")
    // \overset{X}{Y} -> Y with X above (simplified: just show both)
    val oversetPattern = Regex("""\\overset\{([^}]+)\}\{([^}]+)\}""")
    result = oversetPattern.replace(result) { match ->
        "${match.groupValues[2]}^{${match.groupValues[1]}}"
    }
    // \underset{X}{Y} -> Y with X below
    val undersetPattern = Regex("""\\underset\{([^}]+)\}\{([^}]+)\}""")
    result = undersetPattern.replace(result) { match ->
        "${match.groupValues[2]}_{${match.groupValues[1]}}"
    }
    return result
}

/**
 * Process \widehat and \widetilde commands
 * Example: \widehat{ABC} -> ABC with wide hat
 */
private fun processWideAccents(input: String): String {
    var result = input
    // \widehat{...}
    val widehatPattern = Regex("""\\widehat\{([^}]+)\}""")
    result = widehatPattern.replace(result) { match ->
        match.groupValues[1].map { "$it\u0302" }.joinToString("")
    }
    // \widetilde{...}
    val widetildePattern = Regex("""\\widetilde\{([^}]+)\}""")
    result = widetildePattern.replace(result) { match ->
        match.groupValues[1].map { "$it\u0303" }.joinToString("")
    }
    return result
}

/**
 * Process \cancel, \bcancel, \xcancel commands (strikethrough)
 */
private fun processCancel(input: String): String {
    var result = input
    // \cancel{X} -> X with strikethrough
    val cancelPattern = Regex("""\\cancel\{([^}]+)\}""")
    result = cancelPattern.replace(result) { match ->
        match.groupValues[1].map { "$it\u0336" }.joinToString("")
    }
    // \bcancel{X} and \xcancel{X} -> same treatment
    val bcancelPattern = Regex("""\\[bx]cancel\{([^}]+)\}""")
    result = bcancelPattern.replace(result) { match ->
        match.groupValues[1].map { "$it\u0336" }.joinToString("")
    }
    return result
}

/**
 * Process accent commands to add diacritical marks
 * Example: \hat{x} -> x̂, \vec{v} -> v⃗
 */
private fun processAccents(input: String): String {
    val accentMap = mapOf(
        "hat" to "\u0302",      // combining circumflex ̂
        "tilde" to "\u0303",    // combining tilde ̃
        "bar" to "\u0304",      // combining overline ̄
        "vec" to "\u20D7",      // combining right arrow above ⃗
        "dot" to "\u0307",      // combining dot above ̇
        "ddot" to "\u0308",     // combining diaeresis ̈
        "acute" to "\u0301",    // combining acute ́
        "grave" to "\u0300",    // combining grave ̀
        "breve" to "\u0306",    // combining breve ̆
        "check" to "\u030C"     // combining caron ̌
    )
    var result = input
    accentMap.forEach { (cmd, combining) ->
        val pattern = Regex("""\\$cmd\{(.)\}""")
        result = pattern.replace(result) { match ->
            match.groupValues[1] + combining
        }
    }
    return result
}

/**
 * Process \binom{n}{k} commands to render binomial coefficients
 * Example: \binom{n}{k} -> (n¦k)
 */
private fun processBinomials(input: String): String {
    val binomPattern = Regex("""\\binom\{([^}]+)\}\{([^}]+)\}""")
    return binomPattern.replace(input) { match ->
        val n = match.groupValues[1]
        val k = match.groupValues[2]
        "($n¦$k)"
    }
}

/**
 * Process spacing commands to add appropriate Unicode spaces
 * Example: \quad -> em space, \, -> thin space
 */
private fun processSpacing(input: String): String {
    return input
        .replace("""\\,""", "\u2009")      // thin space
        .replace("""\\:""", "\u2005")      // medium space
        .replace("""\\>""", "\u2005")      // medium space
        .replace("""\\;""", "\u2004")      // thick space
        .replace("""\\!""", "")             // negative thin space (remove)
        .replace("""\\quad""", "\u2003")   // em space
        .replace("""\\qquad""", "\u2003\u2003") // 2 em spaces
        .replace("""\\ """, " ")            // normal space
}

private fun renderMathToUnicode(expression: String): String {
    var result = expression

    // Handle \text{...} - extract text content
    result = processTextCommand(result)

    // Handle \textbf{...}, \textit{...}, \mathrm{...}, \mathbf{...}
    result = processTextFormattingCommands(result)

    // Handle \mathbb{X} - blackboard bold
    result = processBlackboardBold(result)

    // Handle \mathcal{X} - calligraphic font
    result = processCalligraphic(result)

    // Handle \mathscr{X} - script font
    result = processScript(result)

    // Handle \mathfrak{X} - Fraktur font
    result = processFraktur(result)

    // Handle \mathsf{X} - sans-serif font
    result = processSansSerif(result)

    // Handle \mathtt{X} - monospace font
    result = processMonospace(result)

    // Handle \boxed{X}
    result = processBoxed(result)

    // Handle \mod, \bmod, \pmod
    result = processModular(result)

    // Handle \overline, \underline
    result = processOverUnderline(result)

    // Handle \overbrace, \underbrace
    result = processOverUnderBrace(result)

    // Handle \stackrel, \overset, \underset
    result = processStacking(result)

    // Handle \widehat, \widetilde
    result = processWideAccents(result)

    // Handle \cancel, \bcancel, \xcancel
    result = processCancel(result)

    // Handle LaTeX environments (cases, matrix, align, etc.)
    result = processLatexEnvironments(result)

    // Handle left/right delimiters
    result = processDelimiters(result)

    // Replace LaTeX/Typst symbols with Unicode (pre-sorted by key length, longest first)
    sortedMathSymbols.forEach { (latex, unicode) ->
        result = result.replace(latex, unicode)
    }

    // Handle accents (must come after symbol replacement but before super/subscripts)
    result = processAccents(result)

    // Handle superscripts: ^{...} or ^x
    result = processSuperscripts(result)

    // Handle subscripts: _{...} or _x
    result = processSubscripts(result)

    // Handle fractions: \frac{a}{b} -> a/b or a⁄b
    result = processFractions(result)

    // Handle binomial coefficients: \binom{n}{k}
    result = processBinomials(result)

    // Handle sqrt: \sqrt{x} -> √x
    result = processSqrt(result)

    // Handle line breaks in math mode: \\ becomes actual newline
    result = processLineBreaks(result)

    // Handle spacing commands
    result = processSpacing(result)

    // Clean up remaining braces
    result = result.replace("{", "").replace("}", "")

    return result.trim()
}

private fun processTextCommand(input: String): String {
    var result = input
    // Handle \text{...}
    val textPattern = Regex("\\\\text\\{([^}]+)\\}")
    result = textPattern.replace(result) { match ->
        match.groupValues[1]
    }
    return result
}

private fun processTextFormattingCommands(input: String): String {
    var result = input
    // Handle \textbf{...}, \mathbf{...}
    val boldPattern = Regex("\\\\(textbf|mathbf)\\{([^}]+)\\}")
    result = boldPattern.replace(result) { match ->
        match.groupValues[2]
    }
    // Handle \textit{...}, \mathit{...}
    val italicPattern = Regex("\\\\(textit|mathit)\\{([^}]+)\\}")
    result = italicPattern.replace(result) { match ->
        match.groupValues[2]
    }
    // Handle \mathrm{...}
    val rmPattern = Regex("\\\\mathrm\\{([^}]+)\\}")
    result = rmPattern.replace(result) { match ->
        match.groupValues[1]
    }
    return result
}

private fun processSuperscripts(input: String): String {
    var result = input
    // Handle ^{...}
    val bracedPattern = Regex("\\^\\{([^}]+)\\}")
    result = bracedPattern.replace(result) { match ->
        match.groupValues[1].map { superscriptMap[it] ?: it }.joinToString("")
    }
    // Handle ^x (single character)
    val singlePattern = Regex("\\^([a-zA-Z0-9])")
    result = singlePattern.replace(result) { match ->
        val char = match.groupValues[1].firstOrNull()
        if (char != null) superscriptMap[char]?.toString() ?: "^$char" else match.value
    }
    return result
}

private fun processSubscripts(input: String): String {
    var result = input
    // Handle _{...}
    val bracedPattern = Regex("_\\{([^}]+)\\}")
    result = bracedPattern.replace(result) { match ->
        match.groupValues[1].map { subscriptMap[it] ?: it }.joinToString("")
    }
    // Handle _x (single character)
    val singlePattern = Regex("_([a-zA-Z0-9])")
    result = singlePattern.replace(result) { match ->
        val char = match.groupValues[1].firstOrNull()
        if (char != null) subscriptMap[char]?.toString() ?: "_$char" else match.value
    }
    return result
}

private fun processFractions(input: String): String {
    var result = input
    // Handle \frac{a}{b}
    val fracPattern = Regex("\\\\frac\\{([^}]+)\\}\\{([^}]+)\\}")
    result = fracPattern.replace(result) { match ->
        val num = match.groupValues[1]
        val den = match.groupValues[2]
        // Use fraction slash for simple fractions
        if (num.length == 1 && den.length == 1) {
            "$num⁄$den"
        } else {
            "($num)/($den)"
        }
    }
    return result
}

private fun processSqrt(input: String): String {
    var result = input
    // Handle \sqrt{x}
    val sqrtPattern = Regex("\\\\sqrt\\{([^}]+)\\}")
    result = sqrtPattern.replace(result) { match ->
        "√(${match.groupValues[1]})"
    }
    // Handle \sqrt[n]{x}
    val nthRootPattern = Regex("\\\\sqrt\\[([^]]+)\\]\\{([^}]+)\\}")
    result = nthRootPattern.replace(result) { match ->
        val n = match.groupValues[1].map { superscriptMap[it] ?: it }.joinToString("")
        "${n}√(${match.groupValues[2]})"
    }
    return result
}

/**
 * Process LaTeX environments like \begin{cases}, \begin{matrix}, etc.
 */
private fun processLatexEnvironments(input: String): String {
    var result = input

    // Handle \begin{cases}...\end{cases}
    // Format: { equation1, if condition1
    //         { equation2, if condition2
    val casesPattern = Regex("\\\\begin\\{cases\\}(.*?)\\\\end\\{cases\\}", RegexOption.DOT_MATCHES_ALL)
    result = casesPattern.replace(result) { match ->
        val content = match.groupValues[1].trim()
        val lines = content.split("\\\\\\\\").map { it.trim() }.filter { it.isNotEmpty() }
        "{\n" + lines.joinToString("\n") { "  $it" } + "\n"
    }

    // Handle \begin{matrix}...\end{matrix} (and variants: pmatrix, bmatrix, vmatrix)
    val matrixTypes = listOf("matrix", "pmatrix", "bmatrix", "vmatrix", "Vmatrix")
    for (matrixType in matrixTypes) {
        val matrixPattern = Regex("\\\\begin\\{$matrixType\\}(.*?)\\\\end\\{$matrixType\\}", RegexOption.DOT_MATCHES_ALL)
        result = matrixPattern.replace(result) { match ->
            val content = match.groupValues[1].trim()
            val rows = content.split("\\\\\\\\").map { it.trim() }.filter { it.isNotEmpty() }

            // Add appropriate brackets
            val (leftBracket, rightBracket) = when (matrixType) {
                "pmatrix" -> "(" to ")"
                "bmatrix" -> "[" to "]"
                "vmatrix" -> "|" to "|"
                "Vmatrix" -> "‖" to "‖"
                else -> "" to ""
            }

            val formattedRows = rows.joinToString("\n") { row ->
                val cells = row.split("&").map { it.trim() }
                "  " + cells.joinToString("  ")
            }

            if (leftBracket.isNotEmpty()) {
                "$leftBracket\n$formattedRows\n$rightBracket"
            } else {
                formattedRows
            }
        }
    }

    // Handle \begin{align}...\end{align} and \begin{align*}...\end{align*}
    val alignPattern = Regex("\\\\begin\\{align\\*?\\}(.*?)\\\\end\\{align\\*?\\}", RegexOption.DOT_MATCHES_ALL)
    result = alignPattern.replace(result) { match ->
        val content = match.groupValues[1].trim()
        val lines = content.split("\\\\\\\\").map { it.trim().replace("&", "") }.filter { it.isNotEmpty() }
        lines.joinToString("\n")
    }

    return result
}

/**
 * Process \left and \right delimiters
 */
private fun processDelimiters(input: String): String {
    var result = input

    // Map of delimiter replacements
    val delimiterMap = mapOf(
        "\\\\left\\{" to "{",
        "\\\\right\\}" to "}",
        "\\\\left\\(" to "(",
        "\\\\right\\)" to ")",
        "\\\\left\\[" to "[",
        "\\\\right\\]" to "]",
        "\\\\left\\|" to "|",
        "\\\\right\\|" to "|",
        "\\\\left\\." to "",  // . means no delimiter
        "\\\\right\\." to "", // . means no delimiter
        "\\\\left<" to "⟨",
        "\\\\right>" to "⟩"
    )

    delimiterMap.forEach { (latex, unicode) ->
        result = result.replace(latex, unicode)
    }

    return result
}

/**
 * Process line breaks (\\) in math mode
 */
private fun processLineBreaks(input: String): String {
    // Replace \\ with newline
    // This handles cases where \\ is used for line breaks in math mode
    var result = input

    // Replace \\ followed by optional spacing like \\[2em]
    result = result.replace(Regex("\\\\\\\\\\[.*?\\]"), "\n")

    // Replace standalone \\ (not part of a command like \alpha, \beta, etc.)
    // We need to be careful not to replace \\ that's part of another command
    result = result.replace(Regex("\\\\\\\\(?![a-zA-Z])"), "\n")

    return result
}

@Composable
private fun MathBlockView(expression: String, isTypst: Boolean) {
    val renderedMath = remember(expression) { renderMathToUnicode(expression) }
    val mathColor = MaterialTheme.colorScheme.primary
    val bgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(rDp(8.dp)))
            .background(bgColor)
    ) {
        // Compact header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = rDp(10.dp), vertical = rDp(5.dp)),
            horizontalArrangement = Arrangement.spacedBy(rDp(6.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "∑",
                fontFamily = maple,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = mathColor
            )
            Text(
                text = if (isTypst) "TYPST" else "MATH",
                fontFamily = maple,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = LocalContentColor.current.copy(alpha = 0.5f)
            )
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )

        // Math expression
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(rDp(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = renderedMath,
                fontFamily = maple,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                fontStyle = FontStyle.Italic,
                color = LocalContentColor.current,
                lineHeight = 24.sp
            )
        }
    }
}

@Composable
private fun InlineMathView(expression: String, isTypst: Boolean) {
    val renderedMath = remember(expression) { renderMathToUnicode(expression) }

    Text(
        text = renderedMath,
        fontFamily = maple,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        fontStyle = FontStyle.Italic,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(rDp(4.dp)))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = rDp(5.dp), vertical = rDp(1.dp))
    )
}