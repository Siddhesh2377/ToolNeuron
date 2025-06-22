package com.dark.neuroverse.compose.components

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
    val annotatedText = remember(text) {
        buildAnnotatedString {
            val lines = text.lines()

            lines.forEach { line ->
                when {
                    line.trimStart().startsWith("•") -> {
                        append("• ")
                        val content = line.removePrefix("•").trimStart()
                        appendStyledSegment(content)
                        append("\n")
                    }

                    else -> {
                        appendStyledSegment(line)
                        append("\n")
                    }
                }
            }
        }
    }

    Text(
        text = annotatedText,
        style = style,
        color = color,
        textAlign = TextAlign.Justify,
        fontFamily = fontFamily,
        fontWeight = fontWeight,
    )
}

private fun AnnotatedString.Builder.appendStyledSegment(line: String) {
    // Bold (**text**)
    if (line.contains("**")) {
        val parts = line.split("**")
        parts.forEachIndexed { index, part ->
            if (index % 2 == 0) {
                append(part)
            } else {
                withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) {
                    append(part)
                }
            }
        }
    }
    // Italic (*text*)
    else if (line.contains("*")) {
        val parts = line.split("*")
        parts.forEachIndexed { index, part ->
            if (index % 2 == 0) {
                append(part)
            } else {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(part)
                }
            }
        }
    } else {
        append(line)
    }
}
