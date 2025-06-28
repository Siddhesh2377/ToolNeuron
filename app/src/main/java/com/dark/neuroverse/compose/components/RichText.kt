package com.dark.neuroverse.compose.components

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.sp

@Composable
fun RichText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    fontFamily: GenericFontFamily = FontFamily.Serif,
    fontWeight: FontWeight = FontWeight.Light
) {
    val annotatedText = remember(text) {
        buildAnnotatedString {
            val lines = text.lines()
            var inCodeBlock = false
            var codeBlockStartIndex = 0

            var i = 0
            while (i < lines.size) {
                val line = lines[i]
                val trimmedLine = line.trimStart()

                when {
                    // Code blocks (```)
                    trimmedLine.startsWith("```") -> {
                        if (!inCodeBlock) {
                            inCodeBlock = true
                            codeBlockStartIndex = i + 1
                        } else {
                            inCodeBlock = false
                            // Process code block content
                            val codeLines = lines.subList(codeBlockStartIndex, i)
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
                        i++
                        continue
                    }

                    inCodeBlock -> {
                        i++
                        continue
                    }

                    // Headers (# ## ###)
                    trimmedLine.startsWith("# ") -> {
                        val headerText = trimmedLine.removePrefix("# ")
                        withStyle(
                            SpanStyle(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = style.fontSize * 1.5f
                            )
                        ) {
                            append(headerText)
                        }
                        append("\n\n")
                    }

                    trimmedLine.startsWith("## ") -> {
                        val headerText = trimmedLine.removePrefix("## ")
                        withStyle(
                            SpanStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = style.fontSize * 1.3f
                            )
                        ) {
                            append(headerText)
                        }
                        append("\n\n")
                    }

                    trimmedLine.startsWith("### ") -> {
                        val headerText = trimmedLine.removePrefix("### ")
                        withStyle(
                            SpanStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = style.fontSize * 1.2f
                            )
                        ) {
                            append(headerText)
                        }
                        append("\n\n")
                    }

                    // Bullet points (• - *)
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

                    // Numbered lists (1. 2. etc.)
                    trimmedLine.matches(Regex("^\\d+\\. .*")) -> {
                        val parts = trimmedLine.split(". ", limit = 2)
                        append("${parts[0]}. ")
                        if (parts.size > 1) {
                            appendStyledSegment(parts[1])
                        }
                        append("\n")
                    }

                    // Blockquotes (>)
                    trimmedLine.startsWith("> ") -> {
                        val quoteText = trimmedLine.removePrefix("> ")
                        withStyle(
                            SpanStyle(
                                fontStyle = FontStyle.Italic,
                                color = color.copy(alpha = 0.7f)
                            )
                        ) {
                            append("❝ ")
                            appendStyledSegment(quoteText)
                        }
                        append("\n")
                    }

                    // Horizontal rule (---)
                    trimmedLine == "---" || trimmedLine == "***" -> {
                        append("\n────────────────\n\n")
                    }

                    // Regular text
                    else -> {
                        appendStyledSegment(line)
                        append("\n")
                    }
                }
                i++
            }
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

private fun AnnotatedString.Builder.appendStyledSegment(text: String) {
    if (text.isEmpty()) {
        append(text)
        return
    }

    val currentText = text
    var currentIndex = 0

    while (currentIndex < currentText.length) {
        when {
            // Inline code (`code`)
            currentText.substring(currentIndex).startsWith("`") &&
                    currentText.indexOf("`", currentIndex + 1) != -1 -> {
                val endIndex = currentText.indexOf("`", currentIndex + 1)
                val codeText = currentText.substring(currentIndex + 1, endIndex)
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = Color.Gray.copy(alpha = 0.2f)
                    )
                ) {
                    append(codeText)
                }
                currentIndex = endIndex + 1
            }

            // Bold and italic (***text***)
            currentText.substring(currentIndex).startsWith("***") &&
                    currentText.indexOf("***", currentIndex + 3) != -1 -> {
                val endIndex = currentText.indexOf("***", currentIndex + 3)
                val boldItalicText = currentText.substring(currentIndex + 3, endIndex)
                withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.ExtraBold,
                        fontStyle = FontStyle.Italic
                    )
                ) {
                    append(boldItalicText)
                }
                currentIndex = endIndex + 3
            }

            // Bold (**text**)
            currentText.substring(currentIndex).startsWith("**") &&
                    currentText.indexOf("**", currentIndex + 2) != -1 -> {
                val endIndex = currentText.indexOf("**", currentIndex + 2)
                val boldText = currentText.substring(currentIndex + 2, endIndex)
                withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) {
                    append(boldText)
                }
                currentIndex = endIndex + 2
            }

            // Italic (*text*)
            currentText.substring(currentIndex).startsWith("*") &&
                    currentText.indexOf("*", currentIndex + 1) != -1 -> {
                val endIndex = currentText.indexOf("*", currentIndex + 1)
                val italicText = currentText.substring(currentIndex + 1, endIndex)
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(italicText)
                }
                currentIndex = endIndex + 1
            }

            // Strikethrough (~~text~~)
            currentText.substring(currentIndex).startsWith("~~") &&
                    currentText.indexOf("~~", currentIndex + 2) != -1 -> {
                val endIndex = currentText.indexOf("~~", currentIndex + 2)
                val strikeText = currentText.substring(currentIndex + 2, endIndex)
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    append(strikeText)
                }
                currentIndex = endIndex + 2
            }

            // Underline (__text__)
            currentText.substring(currentIndex).startsWith("__") &&
                    currentText.indexOf("__", currentIndex + 2) != -1 -> {
                val endIndex = currentText.indexOf("__", currentIndex + 2)
                val underlineText = currentText.substring(currentIndex + 2, endIndex)
                withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                    append(underlineText)
                }
                currentIndex = endIndex + 2
            }

            // Regular character
            else -> {
                append(currentText[currentIndex])
                currentIndex++
            }
        }
    }
}

// Extension function for easier usage with different markdown features
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    style: TextStyle = MaterialTheme.typography.bodyMedium
) {
    RichText(
        text = markdown,
        modifier = modifier,
        color = color,
        style = style
    )
}