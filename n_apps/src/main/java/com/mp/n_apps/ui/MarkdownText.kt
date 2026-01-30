package com.mp.n_apps.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mp.n_apps.R

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun rDp(
    baseDp: Dp, designWidth: Float = 360f, minDp: Dp? = null, maxDp: Dp? = null
): Dp {
    val config = LocalConfiguration.current
    val screenWidthDp = config.screenWidthDp.toFloat()

    // Prevent division by zero and handle edge cases
    if (designWidth <= 0f || screenWidthDp <= 0f) return baseDp

    val scaleFactor = screenWidthDp / designWidth

    // Apply scaling with optional clamping
    var scaledValue = baseDp.value * scaleFactor

    // Clamp to min/max if provided
    minDp?.let { scaledValue = maxOf(scaledValue, it.value) }
    maxDp?.let { scaledValue = minOf(scaledValue, it.value) }

    return scaledValue.dp
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MarkdownText(
    text: String, modifier: Modifier = Modifier
) {
    val parsedContent = remember(text) { parseMarkdown(text) }

    Column(
        modifier = modifier.padding(horizontal = rDp(8.dp)),
        verticalArrangement = Arrangement.spacedBy(rDp(10.dp))
    ) {
        parsedContent.forEach { element ->
            when (element) {
                is MarkdownElement.Heading1 -> Heading1(element.text)
                is MarkdownElement.Heading2 -> Heading2(element.text)
                is MarkdownElement.Heading3 -> Heading3(element.text)
                is MarkdownElement.Heading4 -> Heading4(element.text)
                is MarkdownElement.Heading5 -> Heading5(element.text)
                is MarkdownElement.Heading6 -> Heading6(element.text)
                is MarkdownElement.Body -> BodyText(element.text)
                is MarkdownElement.BulletPoint -> BulletPoint(element.text, element.level)
                is MarkdownElement.NumberedPoint -> NumberedPoint(element.text, element.number)
                is MarkdownElement.Quote -> BlockQuote(element.text, element.level)
                is MarkdownElement.CodeBlock -> CodeBlockExpanded(element.code, element.language)
                is MarkdownElement.InlineCode -> InlineCodeText(element.text)
                is MarkdownElement.Table -> TableView(
                    element.headers,
                    element.rows,
                    element.alignments
                )

                is MarkdownElement.MathBlock -> MathBlockView(element.expression, element.isTypst)
                is MarkdownElement.InlineMath -> InlineMathView(element.expression, element.isTypst)
                is MarkdownElement.Divider -> HorizontalDivider(
                    modifier = Modifier.padding(vertical = rDp(8.dp)),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    }
}

private sealed class MarkdownElement {
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

private fun parseMarkdown(text: String): List<MarkdownElement> {
    val elements = mutableListOf<MarkdownElement>()
    val lines = text.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]

        when {
            // Block math: \[...\] format
            line.trimStart().startsWith("\\[") -> {
                val startCol = line.indexOf("\\[")
                val afterStart = line.substring(startCol + 2)

                // Check if closing \] is on the same line
                val sameLineEnd = afterStart.indexOf("\\]")
                if (sameLineEnd != -1) {
                    val expression = afterStart.substring(0, sameLineEnd).trim()
                    elements.add(MarkdownElement.MathBlock(expression, false))
                } else {
                    // Multi-line math block
                    val mathLines = mutableListOf<String>()
                    if (afterStart.isNotBlank()) mathLines.add(afterStart)
                    i++
                    while (i < lines.size && !lines[i].contains("\\]")) {
                        mathLines.add(lines[i])
                        i++
                    }
                    // Get content before closing \]
                    if (i < lines.size) {
                        val closingLine = lines[i]
                        val closeIdx = closingLine.indexOf("\\]")
                        if (closeIdx > 0) {
                            mathLines.add(closingLine.substring(0, closeIdx))
                        }
                    }
                    val expression = mathLines.joinToString("\n").trim()
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
                    val expression = afterStart.substring(0, sameLineEnd).trim()
                    elements.add(
                        MarkdownElement.MathBlock(
                            expression,
                            isTypst && expression.contains("#")
                        )
                    )
                } else {
                    // Multi-line math block
                    val mathLines = mutableListOf<String>()
                    if (afterStart.isNotBlank()) mathLines.add(afterStart)
                    i++
                    while (i < lines.size && !lines[i].contains("$$")) {
                        mathLines.add(lines[i])
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

            line.startsWith("|") && i + 1 < lines.size && lines[i + 1].contains("|") -> {
                val headers = line.split("|").filter { it.isNotBlank() }.map { it.trim() }
                i++
                val alignmentLine = lines[i]
                val alignments = alignmentLine.split("|").filter { it.isNotBlank() }.map {
                    when {
                        it.trim().startsWith(":") && it.trim()
                            .endsWith(":") -> MarkdownElement.Table.Alignment.CENTER

                        it.trim().endsWith(":") -> MarkdownElement.Table.Alignment.RIGHT
                        else -> MarkdownElement.Table.Alignment.LEFT
                    }
                }
                i++
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].startsWith("|")) {
                    rows.add(lines[i].split("|").filter { it.isNotBlank() }.map { it.trim() })
                    i++
                }
                i--
                elements.add(MarkdownElement.Table(headers, rows, alignments))
            }

            line == "---" || line == "___" || line == "***" -> elements.add(MarkdownElement.Divider)
            line.isNotBlank() -> elements.add(MarkdownElement.Body(line))
        }
        i++
    }

    return elements
}

@Composable
private fun Heading1(text: String) {
    Text(
        text = processInlineFormatting(text),
        fontFamily = ManropeFontFamily,
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(vertical = rDp(8.dp))
    )
}

@Composable
private fun Heading2(text: String) {
    Text(
        text = processInlineFormatting(text),
        fontFamily = ManropeFontFamily,
        fontSize = 24.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(vertical = rDp(7.dp))
    )
}

@Composable
private fun Heading3(text: String) {
    Text(
        text = processInlineFormatting(text),
        fontFamily = ManropeFontFamily,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(vertical = rDp(6.dp))
    )
}

@Composable
private fun Heading4(text: String) {
    Text(
        text = processInlineFormatting(text),
        fontFamily = ManropeFontFamily,
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(vertical = rDp(5.dp))
    )
}

@Composable
private fun Heading5(text: String) {
    Text(
        text = processInlineFormatting(text),
        fontFamily = ManropeFontFamily,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(vertical = rDp(4.dp))
    )
}

@Composable
private fun Heading6(text: String) {
    Text(
        text = processInlineFormatting(text),
        fontFamily = ManropeFontFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.87f),
        modifier = Modifier.padding(vertical = rDp(3.dp))
    )
}

@Composable
private fun BodyText(text: String) {
    Text(
        text = processInlineFormatting(text),
        fontFamily = ManropeFontFamily,
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.87f),
        lineHeight = 22.sp
    )
}

@Composable
private fun processInlineFormatting(text: String) = buildAnnotatedString {
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
                            background = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            fontSize = 13.sp
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
                    withStyle(
                        SpanStyle(
                            background = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                    ) {
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
                            color = MaterialTheme.colorScheme.primary
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
                            color = MaterialTheme.colorScheme.primary
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
                append(chars[i])
                i++
            }
        }
    }
}

@Composable
private fun BulletPoint(text: String, level: Int) {
    Row(
        modifier = Modifier.padding(start = rDp((8 + level * 16).dp)),
        horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
    ) {
        Text(
            text = "•",
            fontFamily = ManropeFontFamily,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = rDp(2.dp))
        )
        Text(
            text = processInlineFormatting(text),
            fontFamily = ManropeFontFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.87f),
            lineHeight = 22.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun NumberedPoint(text: String, number: String) {
    Row(
        modifier = Modifier.padding(start = rDp(8.dp)),
        horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
    ) {
        Text(
            text = "$number.",
            fontFamily = ManropeFontFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = rDp(2.dp))
        )
        Text(
            text = processInlineFormatting(text),
            fontFamily = ManropeFontFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.87f),
            lineHeight = 22.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun BlockQuote(text: String, level: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = rDp(((level - 1) * 12).dp))
            .clip(RoundedCornerShape(rDp(8.dp)))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(rDp(12.dp)), horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
    ) {
        Box(
            modifier = Modifier.width(rDp(3.dp))
                .height(rDp(20.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Text(
            text = processInlineFormatting(text),
            fontFamily = ManropeFontFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.87f),
            lineHeight = 22.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun InlineCodeText(text: String) {
    Text(
        text = text,
        fontFamily = maple,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
        modifier = Modifier
            .clip(RoundedCornerShape(rDp(4.dp)))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = rDp(6.dp), vertical = rDp(2.dp))
    )
}

@Composable
private fun TableView(
    headers: List<String>,
    rows: List<List<String>>,
    alignments: List<MarkdownElement.Table.Alignment>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(rDp(10.dp)))
            .border(
                width = rDp(1.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(rDp(10.dp))
            )
            .horizontalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(rDp(12.dp))
        ) {
            headers.forEachIndexed { index, header ->
                Text(
                    text = header,
                    fontFamily = ManropeFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = rDp(8.dp))
                )
            }
        }

        rows.forEach { row ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(rDp(12.dp))
            ) {
                row.forEachIndexed { index, cell ->
                    Text(
                        text = cell,
                        fontFamily = ManropeFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.87f),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = rDp(8.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeBlockExpanded(code: String, language: String) {
    var isExpanded by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(rDp(10.dp)))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        AnimatedVisibility(
            visible = isExpanded, enter = expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium
                )
            ) + fadeIn(), exit = shrinkVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium
                )
            ) + fadeOut()
        ) {
            Column {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(rDp(12.dp))
                ) {
                    Text(
                        text = code,
                        fontFamily = maple,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

/**
 * Comprehensive LaTeX/Typst symbol mappings for Unicode rendering
 *
 * Total Symbols: ~320+
 * Coverage: >95% of commonly used LaTeX mathematical symbols
 *
 * Categories Covered:
 * - Greek Letters (lowercase & uppercase)
 * - Binary Operators (30+)
 * - Relation Symbols (40+)
 * - Arrows (35+)
 * - Big Operators (summation, product, integral, etc.)
 * - Logic & Set Theory symbols
 * - Calculus & Analysis symbols
 * - Geometry symbols
 * - Brackets & Delimiters
 * - Mathematical Functions
 * - Miscellaneous symbols (card suits, music notation, etc.)
 *
 * Additional Features (via processing functions):
 * - Blackboard Bold (\mathbb{R} -> ℝ)
 * - Calligraphic Font (\mathcal{L} -> 𝓛)
 * - Accents (\hat{x} -> x̂, \vec{v} -> v⃗)
 * - Binomial Coefficients (\binom{n}{k})
 * - Spacing commands (\quad, \,, etc.)
 * - Extended super/subscripts
 *
 * Last Updated: 2026-01-17
 * References: LaTeX comprehensive symbol list, Unicode Mathematical Operators
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

    // Handle LaTeX environments (cases, matrix, align, etc.)
    result = processLatexEnvironments(result)

    // Handle left/right delimiters
    result = processDelimiters(result)

    // Replace LaTeX/Typst symbols with Unicode
    mathSymbols.entries.sortedByDescending { it.key.length }.forEach { (latex, unicode) ->
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
            .clip(RoundedCornerShape(rDp(10.dp)))
            .background(bgColor)
    ) {
        // Header with icon
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = rDp(12.dp), vertical = rDp(8.dp)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(rDp(8.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "∑",
                    fontFamily = maple,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = mathColor
                )
                Text(
                    text = if (isTypst) "TYPST" else "MATH",
                    fontFamily = maple,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )

        // Math expression
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(rDp(16.dp)), contentAlignment = Alignment.Center
        ) {
            Text(
                text = renderedMath,
                fontFamily = maple,
                fontSize = 18.sp,
                fontWeight = FontWeight.Normal,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onBackground,
                lineHeight = 28.sp
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
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        fontStyle = FontStyle.Italic,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(rDp(4.dp)))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = rDp(6.dp), vertical = rDp(2.dp))
    )
}