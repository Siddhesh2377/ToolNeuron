package com.dark.tool_neuron.ui.components

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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dark.tool_neuron.R
import com.dark.tool_neuron.ui.theme.ManropeFontFamily
import com.dark.tool_neuron.ui.theme.maple
import com.dark.tool_neuron.ui.theme.rDp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MarkdownText(
    text: String, modifier: Modifier = Modifier
) {
    val parsedContent = remember(text) { parseMarkdown(text) }

    Column(
        modifier = modifier.padding(horizontal = rDp(16.dp)),
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
            modifier = Modifier
                .width(rDp(3.dp))
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
    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(rDp(10.dp)))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = rDp(12.dp), vertical = rDp(10.dp)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(rDp(8.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActionButton(
                    onClickListener = {},
                    icon = R.drawable.code,
                    contentDescription = "Code",
                    shape = RoundedCornerShape(rDp(8.dp)),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(0.08f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                )
                if (language.isNotEmpty()) {
                    Text(
                        text = language.uppercase(),
                        fontFamily = maple,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(rDp(6.dp))) {
                ActionButton(
                    onClickListener = { /* Copy code */ },
                    icon = R.drawable.copy,
                    contentDescription = "Copy",
                    shape = RoundedCornerShape(rDp(8.dp)),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(0.08f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                )
                ActionToggleButton(
                    checked = isExpanded,
                    onCheckedChange = { isExpanded = !isExpanded },
                    icon = if (isExpanded) R.drawable.up else R.drawable.down,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    shape = RoundedCornerShape(rDp(8.dp)),
                    colors = IconButtonDefaults.filledIconToggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(0.08f),
                        contentColor = MaterialTheme.colorScheme.primary,
                        checkedContainerColor = MaterialTheme.colorScheme.primary.copy(0.12f),
                        checkedContentColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

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

// LaTeX/Typst symbol mappings for Unicode rendering
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
    // Greek letters (uppercase)
    "\\Gamma" to "Γ",
    "\\Delta" to "Δ",
    "\\Theta" to "Θ",
    "\\Lambda" to "Λ",
    "\\Xi" to "Ξ",
    "\\Pi" to "Π",
    "\\Sigma" to "Σ",
    "\\Upsilon" to "Υ",
    "\\Phi" to "Φ",
    "\\Psi" to "Ψ",
    "\\Omega" to "Ω",
    // Operators
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
    // Miscellaneous
    "\\infty" to "∞",
    "\\partial" to "∂",
    "\\nabla" to "∇",
    "\\forall" to "∀",
    "\\exists" to "∃",
    "\\nexists" to "∄",
    "\\emptyset" to "∅",
    "\\varnothing" to "∅",
    "\\neg" to "¬",
    "\\lnot" to "¬",
    "\\land" to "∧",
    "\\lor" to "∨",
    "\\cap" to "∩",
    "\\cup" to "∪",
    "\\setminus" to "∖",
    "\\sqrt" to "√",
    "\\surd" to "√",
    "\\angle" to "∠",
    "\\triangle" to "△",
    "\\square" to "□",
    "\\diamond" to "◇",
    "\\Box" to "□",
    "\\ldots" to "…",
    "\\cdots" to "⋯",
    "\\vdots" to "⋮",
    "\\ddots" to "⋱",
    "\\prime" to "′",
    "\\degree" to "°",
    "\\circ" to "°",
    // Brackets
    "\\langle" to "⟨",
    "\\rangle" to "⟩",
    "\\lceil" to "⌈",
    "\\rceil" to "⌉",
    "\\lfloor" to "⌊",
    "\\rfloor" to "⌋",
    "\\lbrace" to "{",
    "\\rbrace" to "}",
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

// Superscript Unicode mappings
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
    'r' to 'ʳ',
    's' to 'ˢ',
    't' to 'ᵗ',
    'u' to 'ᵘ',
    'v' to 'ᵛ',
    'w' to 'ʷ',
    'x' to 'ˣ',
    'y' to 'ʸ',
    'z' to 'ᶻ'
)

// Subscript Unicode mappings
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
    'e' to 'ₑ',
    'h' to 'ₕ',
    'i' to 'ᵢ',
    'j' to 'ⱼ',
    'k' to 'ₖ',
    'l' to 'ₗ',
    'm' to 'ₘ',
    'n' to 'ₙ',
    'o' to 'ₒ',
    'p' to 'ₚ',
    'r' to 'ᵣ',
    's' to 'ₛ',
    't' to 'ₜ',
    'u' to 'ᵤ',
    'v' to 'ᵥ',
    'x' to 'ₓ'
)

private fun renderMathToUnicode(expression: String): String {
    var result = expression

    // Handle \text{...} - extract text content
    result = processTextCommand(result)

    // Handle \textbf{...}, \textit{...}, \mathrm{...}, \mathbf{...}
    result = processTextFormattingCommands(result)

    // Replace LaTeX/Typst symbols with Unicode
    mathSymbols.entries.sortedByDescending { it.key.length }.forEach { (latex, unicode) ->
        result = result.replace(latex, unicode)
    }

    // Handle superscripts: ^{...} or ^x
    result = processSuperscripts(result)

    // Handle subscripts: _{...} or _x
    result = processSubscripts(result)

    // Handle fractions: \frac{a}{b} -> a/b or a⁄b
    result = processFractions(result)

    // Handle sqrt: \sqrt{x} -> √x
    result = processSqrt(result)

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