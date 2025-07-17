package com.dark.neuroverse.ui.components

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.GenericFontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

@Composable
fun RichText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    fontFamily: GenericFontFamily = FontFamily.Serif,
    fontWeight: FontWeight = FontWeight.Light
) {
    // Rebuild annotatedString on every text change (remove remember)
    val annotatedText = buildAnnotatedString {
        val lines = text.lines()
        var inCodeBlock = false
        var codeBlockStartIndex = 0

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmedLine = line.trimStart()

            when {
                // Code block delimiter
                trimmedLine.startsWith("```") -> {
                    if (!inCodeBlock) {
                        inCodeBlock = true
                        codeBlockStartIndex = i + 1
                    } else {
                        // closing ```
                        inCodeBlock = false
                        val codeLines = lines.subList(codeBlockStartIndex, i)
                        appendCodeBlock(codeLines, style)
                    }
                    i++
                    continue
                }

                // inside open code block, skip until closed
                inCodeBlock -> {
                    i++
                    continue
                }

                // Headers
                trimmedLine.startsWith("# ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, fontSize = style.fontSize * 1.5f)) {
                        append(trimmedLine.removePrefix("# "))
                    }
                    append("\n\n")
                }

                trimmedLine.startsWith("## ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = style.fontSize * 1.3f)) {
                        append(trimmedLine.removePrefix("## "))
                    }
                    append("\n\n")
                }

                trimmedLine.startsWith("### ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = style.fontSize * 1.2f)) {
                        append(trimmedLine.removePrefix("### "))
                    }
                    append("\n\n")
                }

                // List items
                trimmedLine.startsWith("•") || trimmedLine.startsWith("- ") || trimmedLine.startsWith("* ") -> {
                    append("• ")
                    val content = when {
                        trimmedLine.startsWith("•") -> trimmedLine.removePrefix("•").trimStart()
                        trimmedLine.startsWith("- ") -> trimmedLine.removePrefix("- ")
                        else -> trimmedLine.removePrefix("* ")
                    }
                    appendStyledSegment(content)
                    append("\n")
                }

                // Numbered list
                trimmedLine.matches(Regex("^\\d+\\. .*")) -> {
                    val parts = trimmedLine.split(". ", limit = 2)
                    append("${parts[0]}. ")
                    if (parts.size > 1) appendStyledSegment(parts[1])
                    append("\n")
                }

                // Blockquote
                trimmedLine.startsWith("> ") -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = color.copy(alpha = 0.7f))) {
                        append("❝ ")
                        appendStyledSegment(trimmedLine.removePrefix("> "))
                    }
                    append("\n")
                }

                // Horizontal rule
                trimmedLine == "---" || trimmedLine == "***" -> {
                    append("\n────────────────\n\n")
                }

                else -> {
                    appendStyledSegment(line)
                    if (i < lines.lastIndex) {
                        append("\n")
                    }
                }
            }
            i++
        }

        // If code block never closed, render remaining
        if (inCodeBlock && codeBlockStartIndex < lines.size) {
            val codeLines = lines.subList(codeBlockStartIndex, lines.size)
            appendCodeBlock(codeLines, style)
        }
    }

    SelectionContainer {
        Text(
            text = annotatedText,
            modifier = modifier,
            style = style,
            color = color,
            textAlign = TextAlign.Justify,
            fontFamily = fontFamily,
            fontWeight = fontWeight
        )
    }
}

// Helper to append code lines
private fun AnnotatedString.Builder.appendCodeBlock(
    codeLines: List<String>,
    style: TextStyle
) {
    withStyle(
        SpanStyle(
            fontFamily = FontFamily.Monospace,
            background = Color.Gray.copy(alpha = 0.1f),
            fontSize = style.fontSize * 0.9f
        )
    ) {
        codeLines.forEach { codeLine ->
            append(codeLine)
            append("\n")
        }
    }
}

private fun AnnotatedString.Builder.appendStyledSegment(text: String) {
    if (text.isEmpty()) {
        append(text)
        return
    }

    var idx = 0
    while (idx < text.length) {
        when {
            // inline code
            text.startsWith("`", idx) && text.indexOf("`", idx + 1) != -1 -> {
                val end = text.indexOf("`", idx + 1)
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color.Gray.copy(alpha = 0.2f))) {
                    append(text.substring(idx + 1, end))
                }
                idx = end + 1
            }
            // bold italic ***
            text.startsWith("***", idx) && text.indexOf("***", idx + 3) != -1 -> {
                val end = text.indexOf("***", idx + 3)
                withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic)) {
                    append(text.substring(idx + 3, end))
                }
                idx = end + 3
            }
            // bold **
            text.startsWith("**", idx) && text.indexOf("**", idx + 2) != -1 -> {
                val end = text.indexOf("**", idx + 2)
                withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) {
                    append(text.substring(idx + 2, end))
                }
                idx = end + 2
            }
            // italic *
            text.startsWith("*", idx) && text.indexOf("*", idx + 1) != -1 -> {
                val end = text.indexOf("*", idx + 1)
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(text.substring(idx + 1, end))
                }
                idx = end + 1
            }
            // strikethrough ~~
            text.startsWith("~~", idx) && text.indexOf("~~", idx + 2) != -1 -> {
                val end = text.indexOf("~~", idx + 2)
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    append(text.substring(idx + 2, end))
                }
                idx = end + 2
            }
            // underline __
            text.startsWith("__", idx) && text.indexOf("__", idx + 2) != -1 -> {
                val end = text.indexOf("__", idx + 2)
                withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                    append(text.substring(idx + 2, end))
                }
                idx = end + 2
            }
            else -> {
                append(text[idx])
                idx++
            }
        }
    }
}

@Composable
fun MarkdownText(
    text: String,
    canCopy: Boolean = false,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    style: TextStyle = MaterialTheme.typography.bodyMedium
) {
    when(canCopy){
        true -> {
            SelectionContainer {
                RichText(
                    text = text,
                    modifier = modifier,
                    color = color,
                    style = style
                )
            }
        }
        false -> {
            RichText(
                text = text,
                modifier = modifier,
                color = color,
                style = style
            )
        }
    }

}
