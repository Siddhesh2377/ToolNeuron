package com.dark.neuroverse.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dark.neuroverse.R
import com.dark.neuroverse.ui.theme.Coral
import com.dark.neuroverse.ui.theme.rDP
import com.dark.neuroverse.ui.theme.rSp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/* -------------------------------------------------------------------------- *//*  PUBLIC API                                                               *//* -------------------------------------------------------------------------- */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    style: TextStyle = MaterialTheme.typography.bodyMedium
) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }

    Column(
        modifier = modifier, verticalArrangement = Arrangement.spacedBy(rDP(8.dp))
    ) {
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

                is MdBlock.Table -> MarkdownTable(
                    table = block, modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/* -------------------------------------------------------------------------- *//*  CODE CANVAS – full‑screen edit mode, auto‑follow, lazy rendering           *//* -------------------------------------------------------------------------- */
@Composable
fun CodeCanvas(
    modifier: Modifier = Modifier,
    code: String,
    language: String? = null,
    isDarkMode: Boolean = isSystemInDarkTheme(),
    autoScrollHorizontal: Boolean = false,
) {
    // ---------- UI state – three independent saveables ----------
    var editing by rememberSaveable { mutableStateOf(false) }
    var follow by rememberSaveable { mutableStateOf(true) }
    var text by rememberSaveable { mutableStateOf(code) }

    // we also need a flag that tells us whether the *read‑only* dialog is open
    var showReadDialog by remember { mutableStateOf(false) }

    // ---------- scroll & coroutine ----------
    val listState = rememberLazyListState()          // lazy‑list scroll state
    val hScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    // ---------- follow‑bottom logic ----------
    // true when the very last item is visible
    val nearBottom by remember {
        derivedStateOf {
            val lastIndex = listState.layoutInfo.totalItemsCount - 1
            lastIndex >= 0 && listState.firstVisibleItemIndex == lastIndex
        }
    }
    LaunchedEffect(nearBottom) {
        if (nearBottom && !editing) follow = true
    }

    // ---------- auto‑scroll when new text arrives ----------
    LaunchedEffect(text, editing, follow) {
        if (!editing && follow) {
            val last = highlightedLinesCount(text)
            scope.launch { listState.animateScrollToItem(last) }
            if (autoScrollHorizontal) scope.launch {
                hScroll.animateScrollTo(hScroll.maxValue)
            }
        }
    }

    // ---------- UI ---------------------------------------------------------
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(rDP(12.dp)))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
            .padding(horizontal = rDP(10.dp))
            .padding(bottom = rDP(10.dp))
    ) {
        // ---------- Collapsed card (title + actions) ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(rDP(12.dp))
        ) {
            // Title – first line of the code (or the whole string if it’s a single line)
            Text(
                text = text.lines().firstOrNull() ?: "",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )

            // ----- READ -----
            Icon(
                painterResource(R.drawable.copy),   // you can replace with any “read” icon
                contentDescription = "Read",
                modifier = Modifier
                    .size(rDP(15.dp))
                    .clickable { showReadDialog = true })

            Icon(
                painterResource(R.drawable.copy),
                contentDescription = "Copy",
                modifier = Modifier
                    .size(rDP(15.dp))
                    .clickable {
                        clipboard.setText(AnnotatedString(text))
                    })

            // ----- EDIT / DONE -----
            Icon(
                painterResource(if (!editing) R.drawable.edit else R.drawable.done),
                contentDescription = "Edit",
                modifier = Modifier
                    .size(rDP(15.dp))
                    .clickable { editing = !editing })
        }

        // -----------------------------------------------------------------
        //  If the user pressed **Read**, show a simple read‑only dialog.
        // -----------------------------------------------------------------
        if (showReadDialog) {
            Dialog(
                onDismissRequest = { showReadDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp)
                ) {
                    Column {
                        // Header of the dialog – same as in the full‑screen editor
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LanguagePill(language ?: "code")
                            Spacer(Modifier.weight(1f))
                            Icon(
                                painterResource(R.drawable.copy),
                                contentDescription = "Copy",
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { clipboard.setText(AnnotatedString(text)) })
                            Icon(
                                painterResource(R.drawable.done),
                                contentDescription = "Close",
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { showReadDialog = false })
                        }

                        // The whole code – selectable, non‑editable
                        SelectionContainer {
                            Text(
                                text = text,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = rSp(13.sp),
                                    lineHeight = rSp(20.sp)
                                ),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .horizontalScroll(hScroll),
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        // -----------------------------------------------------------------
        //  EDITING MODE – full‑screen editor (unchanged from your original)
        // -----------------------------------------------------------------
        if (editing) {
            FullScreenCodeEditor(
                initialCode = text, language = language, onDismiss = { newCode ->
                    text = newCode
                    editing = false
                })
        } else {
            // -----------------------------------------------------------------
            //  READ‑ONLY (expanded) view – shown only after the **Read** button
            //  was tapped.  We keep the lazy‑list implementation so huge blocks
            //  still render efficiently.
            // -----------------------------------------------------------------
            if (showReadDialog.not()) {
                // No expanded view – the card already shows the title only.
                // The rest of the UI (FAB, etc.) stays hidden in the collapsed state.
            } else {
                // (This block will never be hit because the dialog already shows
                // the whole code.  It is kept only for completeness if you ever
                // want to switch to an in‑place expanded view instead of a dialog.)
                val highlighted = remember(text, language, isDarkMode) {
                    highlight(text, language, isDarkMode)
                }
                val lines = highlighted.text.split('\n')

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .heightIn(max = rDP(260.dp))
                        .fillMaxWidth()
                        .padding(horizontal = rDP(12.dp))
                ) {
                    items(lines) { line ->
                        SelectionContainer {
                            Text(
                                text = line,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = rSp(12.sp),
                                    lineHeight = rSp(20.sp)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(hScroll),
                                softWrap = false
                            )
                        }
                    }
                }

                // Jump‑to‑bottom FAB (visible only when not following)
                AnimatedVisibility(
                    visible = !follow,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(end = rDP(6.dp), bottom = rDP(6.dp))
                ) {
                    SmallFloatingActionButton(
                        onClick = {
                            follow = true
                            scope.launch {
                                val last = lines.size - 1
                                listState.animateScrollToItem(last)
                                if (autoScrollHorizontal) hScroll.animateScrollTo(hScroll.maxValue)
                            }
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
}

/* -------------------------------------------------------------------------- *//*  Helper – count how many lines we have (used for the auto‑scroll effect)   *//* -------------------------------------------------------------------------- */
private fun highlightedLinesCount(text: String): Int = text.split('\n').size

/* -------------------------------------------------------------------------- *//*  FULL‑SCREEN EDITOR (Dialog)                                             *//* -------------------------------------------------------------------------- */
@Composable
private fun FullScreenCodeEditor(
    initialCode: String,
    onDismiss: (String) -> Unit,
    language: String? = null,
    isDarkMode: Boolean = isSystemInDarkTheme()
) {
    var code by rememberSaveable(initialCode) { mutableStateOf(initialCode) }
    val clipboard = LocalClipboardManager.current

    Dialog(
        onDismissRequest = { onDismiss(code) },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            Column {
                // Header inside dialog
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LanguagePill(language ?: "code")
                    Spacer(Modifier.weight(1f))
                    Icon(
                        painterResource(R.drawable.copy),
                        contentDescription = "Copy",
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { clipboard.setText(AnnotatedString(code)) })
                    Icon(
                        painterResource(R.drawable.done),
                        contentDescription = "Done",
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { onDismiss(code) })
                }

                // Editor
                BasicTextField(
                    value = code, onValueChange = { code = it }, textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = rSp(13.sp),
                        lineHeight = rSp(20.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    ), modifier = Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                            RoundedCornerShape(rDP(12.dp))
                        )
                        .padding(horizontal = rDP(12.dp))
                )
            }
        }
    }
}

/* -------------------------------------------------------------------------- *//*  SUPPORT FOR TABLES                                                       *//* -------------------------------------------------------------------------- */
@Composable
private fun MarkdownTable(
    table: MdBlock.Table, modifier: Modifier = Modifier
) {
    Column(modifier) {
        table.rows.forEachIndexed { rowIdx, cells ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(
                        if (rowIdx % 2 == 0) MaterialTheme.colorScheme.surfaceVariant
                        else Color.Transparent
                    )
            ) {
                cells.forEachIndexed { colIdx, cell ->
                    Text(
                        text = cell,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        textAlign = table.align.getOrNull(colIdx) ?: TextAlign.Start,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

/* -------------------------------------------------------------------------- *//*  MARKDOWN PARSER – now also detects pipe tables                           *//* -------------------------------------------------------------------------- */
private sealed class MdBlock {
    data class Text(val content: String) : MdBlock()
    data class Code(val lang: String?, val code: String) : MdBlock()

    /** `align` stores a `TextAlign` for each column */
    data class Table(val rows: List<List<String>>, val align: List<TextAlign>) : MdBlock()
}

private fun parseMarkdownBlocks(input: String): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    val lines = input.lines()
    var i = 0

    // buffers
    val textBuf = StringBuilder()
    var inCode = false
    var codeLang: String? = null
    val codeBuf = StringBuilder()

    // flush normal text
    fun flushText() {
        if (textBuf.isNotEmpty()) {
            out += MdBlock.Text(textBuf.toString().trimEnd())
            textBuf.clear()
        }
    }

    // ---- table detection ----------------------------------------------------
    fun tryParseTable(startIdx: Int): Pair<Int, MdBlock.Table?> {
        var idx = startIdx
        val rows = mutableListOf<List<String>>()
        while (idx < lines.size && lines[idx].trimStart().startsWith("|")) {
            // remove leading/trailing pipe, split on '|'
            val cells = lines[idx].trim().trim('|').split("|").map { it.trim() }
            rows += cells
            idx++
        }

        // need at least header + separator
        if (rows.size < 2) return startIdx to null

        // separator line (e.g. |---|:---:|---:|)
        val sep = rows[1]
        val isSeparator = sep.all {
            it.matches(Regex("-{3,}|:{1,2}-+|-{3,}:"))
        }
        if (!isSeparator) return startIdx to null

        // column alignment → TextAlign
        val align = sep.map {
            when {
                it.startsWith(":") && it.endsWith(":") -> TextAlign.Center
                it.startsWith(":") -> TextAlign.Start
                it.endsWith(":") -> TextAlign.End
                else -> TextAlign.Start
            }
        }

        // drop separator row
        val dataRows = rows.filterIndexed { idx2, _ -> idx2 != 1 }
        return idx to MdBlock.Table(dataRows, align)
    }

    // ---- main parsing loop --------------------------------------------------
    while (i < lines.size) {
        val raw = lines[i]
        val trimmed = raw.trimStart()
        when {
            // code fence ---------------------------------------------------------
            trimmed.startsWith("```") -> {
                if (!inCode) {
                    flushText()
                    inCode = true
                    codeLang = trimmed.removePrefix("```").trim().ifBlank { null }
                    codeBuf.clear()
                } else {
                    out += MdBlock.Code(codeLang, codeBuf.toString().trimEnd())
                    inCode = false
                    codeLang = null
                }
                i++
                continue
            }
            // inside a code block ------------------------------------------------
            inCode -> {
                codeBuf.append(raw)
                if (i != lines.lastIndex) codeBuf.append('\n')
                i++
                continue
            }
            // possible table ----------------------------------------------------
            trimmed.startsWith("|") -> {
                val (newIdx, table) = tryParseTable(i)
                if (table != null) {
                    flushText()
                    out += table
                    i = newIdx
                    continue
                }
                // not a true table → plain text
                textBuf.append(raw)
                if (i != lines.lastIndex) textBuf.append('\n')
            }
            // ordinary text -----------------------------------------------------
            else -> {
                textBuf.append(raw)
                if (i != lines.lastIndex) textBuf.append('\n')
            }
        }
        i++
    }

    // final flush
    if (inCode) {
        out += MdBlock.Code(codeLang, codeBuf.toString().trimEnd())
    } else {
        flushText()
    }
    return out
}

/* -------------------------------------------------------------------------- *//*  SYNTAX HIGHLIGHTER (unchanged apart from minor memoisation)             *//* -------------------------------------------------------------------------- */
private fun highlight(
    code: String, language: String?, isDarkMode: Boolean
): AnnotatedString {
    val b = AnnotatedString.Builder(code)

    fun styleAll(re: Regex, s: SpanStyle) {
        re.findAll(code).forEach { b.addStyle(s, it.range.first, it.range.last + 1) }
    }

    // One Dark palette
    val cmt = SpanStyle(color = Color(0xFF7F848E))
    val str = SpanStyle(color = Color(0xFF10B981))
    val chr = SpanStyle(color = Color(0xFF10B981))
    val num = SpanStyle(color = Color(0xFFD19A66))
    val ann = SpanStyle(color = Color(0xFFE06C75))
    val kw = SpanStyle(color = Color(0xFFC678DD), fontWeight = FontWeight.SemiBold)
    val typ = if (!isDarkMode) SpanStyle(color = Color(0xFF795920))
    else SpanStyle(color = Color(0xFFE5C07B))
    val funDecl = SpanStyle(color = if (isDarkMode) Color(0xFF61AFEF) else Color(0xFF0070C2))
    val call = SpanStyle(color = if (isDarkMode) Color(0xFF56B6C2) else Color(0xFF0097A7))

    // comments / strings first
    styleAll(Regex("//.*"), cmt)
    styleAll(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), cmt)
    styleAll(Regex("\"([^\\\\\"]|\\\\.)*\""), str)
    styleAll(Regex("'([^\\\\']|\\\\.)'"), chr)

    // numbers & annotations
    styleAll(
        Regex("\\b(?:0x[0-9a-fA-F_]+|[0-9][0-9_]*(?:\\.[0-9_]+)?(?:[eE][+-]?[0-9_]+)?)\\b"), num
    )
    styleAll(Regex("@[_A-Za-z][_A-Za-z0-9]*"), ann)

    val lang = language?.lowercase() ?: ""
    if (lang in listOf("kt", "kotlin", "java")) {
        val keywords = listOf(
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
        styleAll(Regex("\\b(${keywords.joinToString("|")})\\b"), kw)

        // built‑in types
        styleAll(
            Regex("\\b(String|Char|Int|Long|Double|Float|Short|Byte|Boolean|Unit|Any|Nothing|Array|List|MutableList|Map|MutableMap|Set|MutableSet)\\b"),
            typ
        )
        // capitalized identifiers → likely types
        styleAll(Regex("(?<![@.])\\b[A-Z][_A-Za-z0-9]*\\b"), typ)

        // function declarations
        styleAll(Regex("(?<=\\bfun\\s)\\w+"), funDecl)

        // function calls (exclude keywords)
        val exclude = (keywords + listOf("if", "for", "while", "when")).joinToString("|")
        styleAll(Regex("\\b(?!$exclude\\b)[a-zA-Z_]\\w*(?=\\s*\\()"), call)
    } else {
        // generic fallback
        styleAll(
            Regex("\\b(class|def|function|var|let|const|return|if|else|for|while|switch|case|break|continue|try|catch|finally|throw|new)\\b"),
            kw
        )
        styleAll(Regex("\\b([A-Z][A-Za-z0-9_]*)\\b"), typ)
        styleAll(Regex("\\b[a-zA-Z_]\\w*(?=\\s*\\()"), call)
    }

    return b.toAnnotatedString()
}

/* -------------------------------------------------------------------------- *//*  RICH TEXT (headings, lists, quotes, inline styles)                       *//* -------------------------------------------------------------------------- */
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
                        ) {
                            append("❝ ")
                            appendStyledSegment(t.removePrefix("> "))
                        }
                        append("\n")
                    }

                    t == "---" || t == "***" -> append("\n────────────────\n\n")
                    t.startsWith("•") || t.startsWith("- ") || t.startsWith("* ") -> {
                        append("• ")
                        val content = when {
                            t.startsWith("•") -> t.removePrefix("•").trimStart()
                            t.startsWith("- ") -> t.removePrefix("- ")
                            else -> t.removePrefix("* ")
                        }
                        appendStyledSegment(content)
                        append("\n")
                    }

                    t.matches(Regex("^\\d+\\. .*")) -> {
                        val parts = t.split(". ", limit = 2)
                        append("${parts[0]}. ")
                        if (parts.size > 1) appendStyledSegment(parts[1])
                        append("\n")
                    }

                    else -> {
                        appendStyledSegment(line)
                        if (i < lines.lastIndex) append("\n")
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
        append(text)
    }
    append("\n\n")
}

private fun AnnotatedString.Builder.appendStyledSegment(text: String) {
    if (text.isEmpty()) {
        append(text)
        return
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
                    append(text.substring(idx + 2, end))
                }
                idx = end + 2
            }

            text.startsWith("*", idx) && text.indexOf("*", idx + 1) != -1 -> {
                val end = text.indexOf("*", idx + 1)
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(text.substring(idx + 1, end))
                }
                idx = end + 1
            }

            text.startsWith("~~", idx) && text.indexOf("~~", idx + 2) != -1 -> {
                val end = text.indexOf("~~", idx + 2)
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    append(text.substring(idx + 2, end))
                }
                idx = end + 2
            }

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

/* -------------------------------------------------------------------------- *//*  UI HELPERS                                                               *//* -------------------------------------------------------------------------- */
@Composable
private fun LanguagePill(label: String) {
    Text(
        text = label, color = MaterialTheme.colorScheme.primary, fontSize = rSp(11.sp)
    )
}

/* -------------------------------------------------------------------------- *//*  PLACEHOLDER – robot like “decoding…” animation                           *//* -------------------------------------------------------------------------- */
@Composable
fun RobotDecodePlaceholder(
    active: Boolean, base: String = "Decoding response", modifier: Modifier = Modifier
) {
    val charset = "!@#${'$'}%&*+/:;?=<>[]{}ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789░▒▓█"
    var shown by remember { mutableStateOf(base) }
    var step by remember { mutableStateOf(0) }

    // caret blink
    val blink by rememberInfiniteTransition(label = "caret").animateFloat(
        initialValue = 1f, targetValue = 0.25f, animationSpec = infiniteRepeatable(
            animation = tween(500), repeatMode = RepeatMode.Reverse
        ), label = "caretFloat"
    )

    // shimmer sweep
    val shimmerX by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Restart),
        label = "shimmerFloat"
    )

    // loop while active
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        val phrases = listOf(
            "$base …",
            "Tokenizing …",
            "Loading KV cache …",
            "Neurons waking up …",
            "Planning …",
            "Reasoning …"
        )
        while (active && coroutineContext.isActive) {
            val seed = phrases[step % phrases.size]
            val noisy = seed.map { c ->
                when {
                    c.isWhitespace() || c == '…' -> c
                    Random.nextFloat() < 0.20f -> charset.random()
                    else -> c
                }
            }.joinToString("")
            shown = noisy
            step++
            delay(66L) // ~15 fps
        }
    }

    // render
    val caret = "▌"
    val gradient = Brush.linearGradient(
        colors = listOf(
            Coral.copy(alpha = 0.25f), Coral, Coral.copy(alpha = 0.25f)
        ), start = Offset.Zero, end = Offset(1000f * shimmerX + 1f, 0f)
    )

    Box(
        modifier
            .clip(RoundedCornerShape(rDP(8.dp)))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(rDP(8.dp)))
            .padding(rDP(9.dp))
    ) {
        Text(
            text = shown,
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.drawWithContent {
                drawContent()
                drawRect(brush = gradient, alpha = 0.25f, blendMode = BlendMode.SrcOver)
            })
        Text(
            text = caret,
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = blink),
            modifier = Modifier.align(Alignment.BottomEnd)
        )
    }
}

/* -------------------------------------------------------------------------- *//*  INTERNAL STATE HOLDER                                                    *//* -------------------------------------------------------------------------- */
private data class CodeUiState(
    val editing: Boolean = false, val follow: Boolean = true, val text: String
)