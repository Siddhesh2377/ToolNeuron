package com.dark.neuroverse.ui.components

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
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
import com.dark.neuroverse.R
import com.dark.neuroverse.ui.theme.rDP
import com.dark.neuroverse.ui.theme.rSp
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch

// ——————————————————————————————————————————————————————————————
// Public API (drop-in): MarkdownText(text = message.text)
// Renders paragraphs with RichText + code fences as CodeCanvas
// ——————————————————————————————————————————————————————————————

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    style: TextStyle = MaterialTheme.typography.bodyMedium
) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(rDP(8.dp))) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Text -> RichText(
                    text = block.content,
                    modifier = Modifier.fillMaxWidth(),
                    color = color,
                    style = style
                )

                is MdBlock.Code -> CodeCanvas(
                    code = block.code, language = block.lang, modifier = Modifier.fillMaxWidth()
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
    isDarkMode: Boolean = isSystemInDarkTheme(),
    autoFollowInit: Boolean = true,
    autoScrollHorizontal: Boolean = false,
) {
    var editing by remember { mutableStateOf(false) }
    var text by remember(code) { mutableStateOf(code) }
    val clipboard = LocalClipboardManager.current

    val radius = rDP(12.dp)
    val bg = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)

    // Scroll states
    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    // Auto-follow state: turns off if user scrolls up; can be re-enabled via FAB
    var follow by remember { mutableStateOf(autoFollowInit) }

    // Detect user moving away from bottom → disable follow
    LaunchedEffect(Unit) {
        snapshotFlow { vScroll.value to vScroll.maxValue }.collect { (value, max) ->
                val nearBottom = max - value < 24
                if (!nearBottom && vScroll.isScrollInProgress) follow = false
            }
    }

    // When content grows and follow is on, scroll to bottom (and optionally right)
    LaunchedEffect(text, editing, follow) {
        if (!editing && follow) {
            // Wait a frame for layout to update maxValue
            snapshotFlow { vScroll.maxValue }.take(1)
                .collect { end -> scope.launch { vScroll.animateScrollTo(end) } }
            if (autoScrollHorizontal) {
                snapshotFlow { hScroll.maxValue }.take(1)
                    .collect { end -> scope.launch { hScroll.animateScrollTo(end) } }
            }
        }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(radius))
            .background(bg)
            .padding(horizontal = rDP(10.dp))
            .padding(bottom = rDP(10.dp))
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(rDP(12.dp))
        ) {
            LanguagePill(language ?: "code")
            Spacer(Modifier.weight(1f))
            Icon(
                painterResource(R.drawable.copy),
                modifier = Modifier
                    .size(rDP(15.dp))
                    .clickable { clipboard.setText(AnnotatedString(if (editing) text else code)) },
                contentDescription = "Copy"
            )
            Icon(
                painterResource(if (!editing) R.drawable.edit else R.drawable.done),
                modifier = Modifier
                    .size(rDP(15.dp))
                    .clickable { editing = !editing },
                contentDescription = "Edit"
            )
        }

        Spacer(Modifier.height(rDP(6.dp)))

        // Body → no soft wrap: horizontal + vertical scroll
        Box(
            modifier = Modifier
                .heightIn(max = rDP(260.dp))
                .fillMaxWidth()
        ) {
            if (!editing) {
                val highlighted =
                    remember(text, language, isDarkMode) { highlight(text, language, isDarkMode) }
                Box(
                    modifier = Modifier
                        .verticalScroll(vScroll)
                        .padding(horizontal = rDP(12.dp))
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
                                softWrap = false,
                                maxLines = Int.MAX_VALUE
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .verticalScroll(vScroll)
                        .fillMaxWidth()
                ) {
                    Row(modifier = Modifier.horizontalScroll(hScroll)) {
                        BasicTextField(
                            value = text, onValueChange = { text = it }, textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = rSp(13.sp),
                                lineHeight = rSp(20.sp),
                                color = MaterialTheme.colorScheme.onSurface
                            ), singleLine = false, maxLines = Int.MAX_VALUE
                        )
                    }
                }
            }

            // Floating "Jump to bottom" when not following
            androidx.compose.animation.AnimatedVisibility(
                visible = !follow,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = rDP(6.dp), bottom = rDP(6.dp))
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        follow = true
                        scope.launch { vScroll.animateScrollTo(vScroll.maxValue) }
                        if (autoScrollHorizontal) scope.launch { hScroll.animateScrollTo(hScroll.maxValue) }
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.ArrowDownward, contentDescription = "Jump to bottom")
                }
            }

        }
    }
}


@Composable
private fun LanguagePill(label: String) {
    Text(
        text = label, color = MaterialTheme.colorScheme.primary, fontSize = rSp(11.sp)
    )
}

// ——————————————————————————————————————————————————————————————
// Markdown parsing – split into text and fenced code blocks
// Supports ```lang\n...\n```  (lang optional)
// ——————————————————————————————————————————————————————————————
private sealed class MdBlock {
    data class Text(val content: String) : MdBlock()
    data class Code(val lang: String?, val code: String) : MdBlock()
}

private fun parseMarkdownBlocks(input: String): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    val lines = input.split("\n")

    val buf = StringBuilder()
    var i = 0
    var inCode = false
    var lang: String? = null
    val code = StringBuilder()

    fun flushText() {
        if (buf.isNotEmpty()) {
            out += MdBlock.Text(buf.toString().trimEnd()); buf.clear()
        }
    }

    while (i < lines.size) {
        val raw = lines[i]
        val t = raw.trimStart()
        if (t.startsWith("```")) {
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
    val cmt = SpanStyle(color = Color(0xFF7F848E))
    val str = SpanStyle(color = Color(0xFF10B981))
    val chr = SpanStyle(color = Color(0xFF10B981))
    val num = SpanStyle(color = Color(0xFFD19A66))
    val ann = SpanStyle(color = Color(0xFFE06C75))
    val kw = SpanStyle(color = Color(0xFFC678DD), fontWeight = FontWeight.SemiBold)
    val typ = if (!isDarkMode) SpanStyle(color = Color(0xFF795920)) else SpanStyle(
        color = Color(0xFFE5C07B)
    )
    val funDecl = SpanStyle(color = if (isDarkMode) Color(0xFF61AFEF) else Color(0xFF0070C2))
    val call = SpanStyle(color = if (isDarkMode) Color(0xFF56B6C2) else Color(0xFF0097A7))

    // Protect strings/comments first so later regexes don't recolor inside them
    styleAll(Regex("//.*"), cmt)
    styleAll(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), cmt)
    styleAll(Regex("\"([^\\\\\"]|\\\\.)*\""), str)
    styleAll(Regex("'([^\\\\']|\\\\.)'"), chr)

    // Numbers & annotations (rough; good enough for code blocks)
    styleAll(
        Regex("\\b(?:0x[0-9a-fA-F_]+|[0-9][0-9_]*(?:\\.[0-9_]+)?(?:[eE][+-]?[0-9_]+)?)\\b"), num
    )
    styleAll(Regex("@[_A-Za-z][_A-Za-z0-9]*"), ann)

    val lang = language?.lowercase() ?: ""
    if (lang in listOf("kt", "kotlin", "java")) {
        val keywords = listOf(
            // common
            "package",
            "import",
            "as",
            "class",
            "interface",
            "object",
            "fun",
            "val",
            "var",
            "this",
            "super",
            "if",
            "else",
            "when",
            "try",
            "catch",
            "finally",
            "for",
            "while",
            "do",
            "return",
            "break",
            "continue",
            "throw",
            "in",
            "is",
            "null",
            "true",
            "false",
            // modifiers
            "open",
            "abstract",
            "override",
            "private",
            "public",
            "protected",
            "internal",
            "data",
            "sealed",
            "enum",
            "companion",
            "inline",
            "noinline",
            "crossinline",
            "reified",
            "operator",
            "infix",
            "tailrec",
            "const",
            "lateinit",
            "suspend",
            "external",
            "final",
            "actual",
            "expect",
            // java-ish
            "static",
            "void",
            "new",
            "extends",
            "implements",
            "throws",
            "synchronized",
            "volatile",
            "transient",
            "native",
            "strictfp"
        )
        styleAll(Regex("\\b(" + keywords.joinToString("|") + ")\\b"), kw)

        // Built-in types
        styleAll(
            Regex("\\b(String|Char|Int|Long|Double|Float|Short|Byte|Boolean|Unit|Any|Nothing|Array|List|MutableList|Map|MutableMap|Set|MutableSet)\\b"),
            typ
        )

        // Capitalized identifiers → likely types
        styleAll(Regex("(?<![@.])\\b[A-Z][_A-Za-z0-9]*\\b"), typ)

        // Function decl names: after 'fun '
        styleAll(Regex("(?<=\\bfun\\s)\\w+"), funDecl)

        // Function calls: name(  (exclude keywords)
        val exclude = (keywords + listOf("if", "for", "while", "when")).joinToString("|")
        styleAll(Regex("\\b(?!$exclude\\b)[a-zA-Z_]\\w*(?=\\s*\\()"), call)
    } else {
        // Generic fallback
        styleAll(
            Regex("\\b(class|def|function|var|let|const|return|if|else|for|while|switch|case|break|continue|try|catch|finally|throw|new)\\b"),
            kw
        )
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
                    t.startsWith("# ") -> {
                        appendStyledHeader(t.removePrefix("# "), style, 1.5f)
                    }

                    t.startsWith("## ") -> {
                        appendStyledHeader(t.removePrefix("## "), style, 1.3f)
                    }

                    t.startsWith("### ") -> {
                        appendStyledHeader(t.removePrefix("### "), style, 1.2f)
                    }

                    t.startsWith("#### ") -> {
                        appendStyledHeader(t.removePrefix("#### "), style, 1.1f)
                    }

                    t.startsWith("> ") -> {
                        withStyle(
                            SpanStyle(
                                fontStyle = FontStyle.Italic, color = color.copy(alpha = 0.7f)
                            )
                        ) { append("❝ "); appendStyledSegment(t.removePrefix("> ")) }; append("\n")
                    }

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
                        val parts = t.split(
                            ". ", limit = 2
                        ); append("${parts[0]}. "); if (parts.size > 1) appendStyledSegment(parts[1]); append(
                            "\n"
                        )
                    }

                    else -> {
                        appendStyledSegment(line); if (i < lines.lastIndex) append("\n")
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
            fontFamily = fontFamily,
            fontWeight = fontWeight
        )
    }
}

private fun AnnotatedString.Builder.appendStyledHeader(
    text: String, style: TextStyle, scale: Float
) {
    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = style.fontSize * scale)) {
        append(
            text
        )
    }
    append("\n\n")
}

private fun AnnotatedString.Builder.appendStyledSegment(text: String) {
    if (text.isEmpty()) {
        append(text); return
    }
    var idx = 0
    while (idx < text.length) {
        when {
            text.startsWith("`", idx) && text.indexOf("`", idx + 1) != -1 -> {
                val end = text.indexOf("`", idx + 1)
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = Color.Gray.copy(alpha = 0.2f)
                    )
                ) { append(text.substring(idx + 1, end)) }
                idx = end + 1
            }

            text.startsWith("***", idx) && text.indexOf("***", idx + 3) != -1 -> {
                val end = text.indexOf("***", idx + 3)
                withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic
                    )
                ) { append(text.substring(idx + 3, end)) }
                idx = end + 3
            }

            text.startsWith("**", idx) && text.indexOf("**", idx + 2) != -1 -> {
                val end = text.indexOf("**", idx + 2)
                withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) {
                    append(
                        text.substring(
                            idx + 2, end
                        )
                    )
                }
                idx = end + 2
            }

            text.startsWith("*", idx) && text.indexOf("*", idx + 1) != -1 -> {
                val end = text.indexOf("*", idx + 1)
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(
                        text.substring(
                            idx + 1, end
                        )
                    )
                }
                idx = end + 1
            }

            text.startsWith("~~", idx) && text.indexOf("~~", idx + 2) != -1 -> {
                val end = text.indexOf("~~", idx + 2)
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    append(
                        text.substring(
                            idx + 2, end
                        )
                    )
                }
                idx = end + 2
            }

            text.startsWith("__", idx) && text.indexOf("__", idx + 2) != -1 -> {
                val end = text.indexOf("__", idx + 2)
                withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                    append(
                        text.substring(
                            idx + 2, end
                        )
                    )
                }
                idx = end + 2
            }

            else -> {
                append(text[idx]); idx++
            }
        }
    }
}