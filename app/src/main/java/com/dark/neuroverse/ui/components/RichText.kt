package com.dark.neuroverse.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dark.neuroverse.ui.theme.Mint
import com.dark.neuroverse.ui.theme.rDP
import com.dark.neuroverse.ui.theme.rSp

// ——————————————————————————————————————————————————————————————
// Public API (drop-in): MarkdownText(text = message.text)
// Renders paragraphs with RichText + code fences as CodeCanvas
// ——————————————————————————————————————————————————————————————
@Composable
fun MarkdownText(
    text: String,
    canCopy: Boolean = false,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    style: TextStyle = MaterialTheme.typography.bodyMedium
) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(rDP(8.dp))) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Text ->
                    RichText(
                        text = block.content,
                        modifier = Modifier.fillMaxWidth(),
                        color = color,
                        style = style
                    )
                is MdBlock.Code ->
                    CodeCanvas(
                        code = block.code,
                        language = block.lang,
                        modifier = Modifier.fillMaxWidth()
                    )
            }
        }
    }
}

// ——————————————————————————————————————————————————————————————
// Canvas for code blocks: header + copy/edit + NO SOFT WRAP
// Also: better syntax highlighting (keywords, types, funcs, numbers, annotations)
// ——————————————————————————————————————————————————————————————
@Composable
fun CodeCanvas(
    modifier: Modifier = Modifier,
    code: String,
    language: String? = null,
    isDarkMode: Boolean = isSystemInDarkTheme()
) {
    var editing by remember { mutableStateOf(false) }
    var text by remember(code) { mutableStateOf(code) }
    val clipboard = LocalClipboardManager.current

    val radius = rDP(12.dp)
    val bg = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    val stroke = MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(radius))
            .background(bg)
            .border(rDP(1.dp), stroke, RoundedCornerShape(radius))
            .padding(rDP(10.dp))
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(rDP(8.dp)),
            modifier = Modifier.fillMaxWidth()
        ) {
            LanguagePill(language ?: "code")
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = { clipboard.setText(AnnotatedString(if (editing) text else code)) },
                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) { Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy") }
            IconButton(
                onClick = { editing = !editing },
                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) { Icon(if (editing) Icons.Rounded.Done else Icons.Rounded.Edit, contentDescription = "Edit") }
        }

        Spacer(Modifier.height(rDP(6.dp)))

        // Body → no soft wrap: horizontal + vertical scroll
        val vScroll = rememberScrollState()
        val hScroll = rememberScrollState()

        if (!editing) {
            val highlighted = remember(text, language) { highlight(text, language, isDarkMode) }
            Box(
                modifier = Modifier
                    .heightIn(max = rDP(260.dp))
                    .verticalScroll(vScroll)
                    .fillMaxWidth()
            ) {
                Row(modifier = Modifier.horizontalScroll(hScroll)) {
                    SelectionContainer {
                        Text(
                            text = highlighted,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = rSp(12.sp),
                                lineHeight = rSp(20.sp)
                            ),
                            softWrap = false,              // 🚫 no wrapping
                            maxLines = Int.MAX_VALUE
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .heightIn(max = rDP(260.dp))
                    .verticalScroll(vScroll)
                    .fillMaxWidth()
            ) {
                Row(modifier = Modifier.horizontalScroll(hScroll)) {
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = rSp(13.sp),
                            lineHeight = rSp(20.sp),
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        singleLine = false,
                        maxLines = Int.MAX_VALUE
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguagePill(label: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(rDP(8.dp)))
            .background(MaterialTheme.colorScheme.surface)
            .border(rDP(1.dp), MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(rDP(8.dp)))
            .padding(horizontal = rDP(8.dp), vertical = rDP(4.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label.lowercase(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = rSp(11.sp)
        )
    }
}

// ——————————————————————————————————————————————————————————————
// Markdown parsing – split into text and fenced code blocks
// Supports ```lang\n...\n```  (lang optional)
// ——————————————————————————————————————————————————————————————
private sealed class MdBlock { data class Text(val content: String): MdBlock(); data class Code(val lang: String?, val code: String): MdBlock() }

private fun parseMarkdownBlocks(input: String): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    val lines = input.split("\n")

    val buf = StringBuilder()
    var i = 0
    var inCode = false
    var lang: String? = null
    val code = StringBuilder()

    fun flushText() {
        if (buf.isNotEmpty()) { out += MdBlock.Text(buf.toString().trimEnd()); buf.clear() }
    }

    while (i < lines.size) {
        val raw = lines[i]
        val t = raw.trimStart()
        if (t.startsWith("```") ) {
            if (!inCode) {
                flushText()
                inCode = true
                lang = t.removePrefix("```").trim().ifBlank { null }
                code.clear()
            } else {
                // closing fence
                out += MdBlock.Code(lang, code.toString().trimEnd())
                inCode = false
                lang = null
            }
            i++
            continue
        }
        if (inCode) {
            code.append(raw)
            if (i != lines.lastIndex) code.append('\n')
        } else {
            buf.append(raw)
            if (i != lines.lastIndex) buf.append('\n')
        }
        i++
    }

    if (inCode) {
        // Unclosed fence → treat rest as code
        out += MdBlock.Code(lang, code.toString().trimEnd())
    } else {
        flushText()
    }
    return out
}

// ——————————————————————————————————————————————————————————————
// Syntax highlighter (improved): Kotlin/Java first-class, generic fallback.
// Highlights: comments, strings, chars, numbers, annotations, keywords,
// types (built-ins + Capitalized identifiers), function names, calls.
// ——————————————————————————————————————————————————————————————
// Drop-in replacement for your highlight() using a One Dark-inspired palette
// Works on both light and dark backgrounds because colors are saturated and mid‑luminance.
// Just replace your existing `highlight(code, language)` with this.

private fun highlight(code: String, language: String?, isDarkMode: Boolean): AnnotatedString {
    val b = AnnotatedString.Builder(code)

    fun styleAll(re: Regex, s: SpanStyle) {
        re.findAll(code).forEach { b.addStyle(s, it.range.first, it.range.last + 1) }
    }

    // One Dark palette — tuned for contrast on light & dark surfaces
    val cmt     = SpanStyle(color = Color(0xFF7F848E))   // comments gray
    val str     = SpanStyle(color = Mint)   // green
    val chr     = SpanStyle(color = Color(0xFF98C379))   // same as strings
    val num     = SpanStyle(color = Color(0xFFD19A66))   // orange
    val ann     = SpanStyle(color = Color(0xFFE06C75))   // red
    val kw      = SpanStyle(color = Color(0xFFC678DD), fontWeight = FontWeight.SemiBold) // purple
    val typ     = if (!isDarkMode) SpanStyle(color = Color(0xFF795920)) else SpanStyle(color = Color(0xFFE5C07B))   // yellow
    val funDecl = SpanStyle(color = Color(0xFF61AFEF))   // blue
    val call    = SpanStyle(color = Color(0xFF56B6C2))   // cyan

    // Protect strings/comments first so later regexes don't recolor inside them
    styleAll(Regex("//.*"), cmt)
    styleAll(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), cmt)
    styleAll(Regex("\"([^\\\\\"]|\\\\.)*\""), str)
    styleAll(Regex("'([^\\\\']|\\\\.)'"), chr)

    // Numbers & annotations (rough; good enough for code blocks)
    styleAll(Regex("\\b(?:0x[0-9a-fA-F_]+|[0-9][0-9_]*(?:\\.[0-9_]+)?(?:[eE][+-]?[0-9_]+)?)\\b"), num)
    styleAll(Regex("@[_A-Za-z][_A-Za-z0-9]*"), ann)

    val lang = language?.lowercase() ?: ""
    if (lang in listOf("kt", "kotlin", "java")) {
        val keywords = listOf(
            // common
            "package","import","as","class","interface","object","fun","val","var","this","super","if","else","when","try","catch","finally","for","while","do","return","break","continue","throw","in","is","null","true","false",
            // modifiers
            "open","abstract","override","private","public","protected","internal","data","sealed","enum","companion","inline","noinline","crossinline","reified","operator","infix","tailrec","const","lateinit","suspend","external","final","actual","expect",
            // java-ish
            "static","void","new","extends","implements","throws","synchronized","volatile","transient","native","strictfp"
        )
        styleAll(Regex("\\b(" + keywords.joinToString("|") + ")\\b"), kw)

        // Built-in types
        styleAll(Regex("\\b(String|Char|Int|Long|Double|Float|Short|Byte|Boolean|Unit|Any|Nothing|Array|List|MutableList|Map|MutableMap|Set|MutableSet)\\b"), typ)

        // Capitalized identifiers → likely types
        styleAll(Regex("(?<![@.])\\b[A-Z][_A-Za-z0-9]*\\b"), typ)

        // Function decl names: after 'fun '
        styleAll(Regex("(?<=\\bfun\\s)\\w+"), funDecl)

        // Function calls: name(  (exclude keywords)
        val exclude = (keywords + listOf("if","for","while","when")).joinToString("|")
        styleAll(Regex("\\b(?!$exclude\\b)[a-zA-Z_]\\w*(?=\\s*\\()"), call)
    } else {
        // Generic fallback
        styleAll(Regex("\\b(class|def|function|var|let|const|return|if|else|for|while|switch|case|break|continue|try|catch|finally|throw|new)\\b"), kw)
        styleAll(Regex("\\b([A-Z][A-Za-z0-9_]*)\\b"), typ)
        styleAll(Regex("\\b[a-zA-Z_]\\w*(?=\\s*\\()"), call)
    }

    return b.toAnnotatedString()
}


// ——————————————————————————————————————————————————————————————
// Rich text: inline markdown-ish + headings / lists / quotes
// ——————————————————————————————————————————————————————————————
@Composable
fun RichText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    fontFamily: FontFamily = FontFamily.Serif,
    fontWeight: FontWeight = FontWeight.Light
) {
    val annotatedText = remember(text) {
        buildAnnotatedString {
            val lines = text.lines()
            var i = 0
            while (i < lines.size) {
                val line = lines[i]
                val t = line.trimStart()
                when {
                    t.startsWith("# ") -> { appendStyledHeader(t.removePrefix("# "), style, 1.5f) }
                    t.startsWith("## ") -> { appendStyledHeader(t.removePrefix("## "), style, 1.3f) }
                    t.startsWith("### ") -> { appendStyledHeader(t.removePrefix("### "), style, 1.2f) }
                    t.startsWith("#### ") -> { appendStyledHeader(t.removePrefix("#### "), style, 1.1f) }
                    t.startsWith("> ") -> { withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = color.copy(alpha = 0.7f))) { append("❝ "); appendStyledSegment(t.removePrefix("> ")) }; append("\n") }
                    t == "---" || t == "***" -> append("\n────────────────\n\n")
                    t.startsWith("•") || t.startsWith("- ") || t.startsWith("* ") -> {
                        append("• ")
                        val content = when {
                            t.startsWith("•") -> t.removePrefix("•").trimStart()
                            t.startsWith("- ") -> t.removePrefix("- ")
                            else -> t.removePrefix("* ")
                        }
                        appendStyledSegment(content); append("\n")
                    }
                    t.matches(Regex("^\\d+\\. .*")) -> {
                        val parts = t.split(". ", limit = 2); append("${parts[0]}. "); if (parts.size > 1) appendStyledSegment(parts[1]); append("\n")
                    }
                    else -> { appendStyledSegment(line); if (i < lines.lastIndex) append("\n") }
                }
                i++
            }
        }
    }

    SelectionContainer { Text(text = annotatedText, modifier = modifier, style = style, color = color, fontFamily = fontFamily, fontWeight = fontWeight) }
}

private fun AnnotatedString.Builder.appendStyledHeader(text: String, style: TextStyle, scale: Float) {
    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = style.fontSize * scale)) { append(text) }
    append("\n\n")
}

private fun AnnotatedString.Builder.appendStyledSegment(text: String) {
    if (text.isEmpty()) { append(text); return }
    var idx = 0
    while (idx < text.length) {
        when {
            text.startsWith("`", idx) && text.indexOf("`", idx + 1) != -1 -> {
                val end = text.indexOf("`", idx + 1)
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color.Gray.copy(alpha = 0.2f))) { append(text.substring(idx + 1, end)) }
                idx = end + 1
            }
            text.startsWith("***", idx) && text.indexOf("***", idx + 3) != -1 -> {
                val end = text.indexOf("***", idx + 3)
                withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic)) { append(text.substring(idx + 3, end)) }
                idx = end + 3
            }
            text.startsWith("**", idx) && text.indexOf("**", idx + 2) != -1 -> {
                val end = text.indexOf("**", idx + 2)
                withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(text.substring(idx + 2, end)) }
                idx = end + 2
            }
            text.startsWith("*", idx) && text.indexOf("*", idx + 1) != -1 -> {
                val end = text.indexOf("*", idx + 1)
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(idx + 1, end)) }
                idx = end + 1
            }
            text.startsWith("~~", idx) && text.indexOf("~~", idx + 2) != -1 -> {
                val end = text.indexOf("~~", idx + 2)
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(text.substring(idx + 2, end)) }
                idx = end + 2
            }
            text.startsWith("__", idx) && text.indexOf("__", idx + 2) != -1 -> {
                val end = text.indexOf("__", idx + 2)
                withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append(text.substring(idx + 2, end)) }
                idx = end + 2
            }
            else -> { append(text[idx]); idx++ }
        }
    }
}