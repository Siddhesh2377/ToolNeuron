package com.dark.neuroverse.ui.components

import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RemoveRedEye
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.ModeEditOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dark.neuroverse.ui.theme.rDP
import com.dark.neuroverse.ui.theme.rSp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    style: TextStyle = MaterialTheme.typography.bodySmall
) {
    var debouncedText by remember { mutableStateOf(text) }

    LaunchedEffect(text) {
        delay(150) // Wait 150ms after text stops changing
        debouncedText = text
    }

    val blocks = remember(debouncedText) { parseMarkdownBlocks(debouncedText) }

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

    // ✅ ADD THIS: Sync text with incoming code when not editing
    LaunchedEffect(code) {
        if (!editing) {
            text = code
        }
    }

    // flag that tells us whether the *read‑only* dialog is open
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
    LaunchedEffect(text.length, editing, follow) {
        if (!editing && follow && text.isNotEmpty()) {
            delay(100)
            val last = text.split('\n').size
            listState.animateScrollToItem(last)
            if (autoScrollHorizontal) {
                hScroll.animateScrollTo(hScroll.maxValue)
            }
        }
    }

    // ---------- UI ---------------------------------------------------------
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(rDP(8.dp)))
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                shape = RoundedCornerShape(rDP(8.dp))
            )
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
            .padding(rDP(10.dp))
            .widthIn(max = rDP(100.dp))
    ) {
        // ---------- Collapsed card (title + actions) ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(rDP(12.dp))
        ) {
            val iconsSize = rDP(15.dp)
            // Title – first line of the code (or the whole string if it’s a single line)
            Text(
                text = language?.trim() ?: "Text",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )

            Icon(
                Icons.Outlined.RemoveRedEye,
                contentDescription = "Read",
                modifier = Modifier
                    .size(iconsSize)
                    .clickable { showReadDialog = true })

            Icon(
                Icons.Rounded.ContentCopy,
                contentDescription = "Copy",
                modifier = Modifier
                    .size(iconsSize)
                    .clickable {
                        clipboard.setText(AnnotatedString(text))
                    })

            Icon(
                imageVector = (if (!editing) Icons.Rounded.ModeEditOutline else Icons.Rounded.Done),
                contentDescription = "Edit",
                modifier = Modifier
                    .size(iconsSize)
                    .clickable { editing = !editing })
        }

        // ---------- Scrolled preview when collapsed (non‑read mode) ----------
        if (!editing && !showReadDialog) {
            val preview = remember(text, language, isDarkMode) {
                if (text.length > 5000) {
                    AnnotatedString(text)
                } else {
                    highlight(text, language, isDarkMode)
                }
            }
            Text(
                text = preview,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .horizontalScroll(hScroll),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
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
                        .padding(rDP(8.dp))
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.surface, RoundedCornerShape(rDP(8.dp))
                        )
                        .padding(rDP(16.dp))
                ) {
                    Column {
                        // Header of the dialog – same as in the full‑screen editor
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LanguagePill(language ?: "code")
                            Spacer(Modifier.weight(1f))
                            Icon(
                                Icons.Rounded.ContentCopy,
                                contentDescription = "Copy",
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { clipboard.setText(AnnotatedString(text)) })
                            Spacer(Modifier.width(rDP(8.dp)))
                            Icon(
                                Icons.Rounded.Done,
                                contentDescription = "Close",
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { showReadDialog = false })
                        }

                        // The whole code – selectable, non‑editable and syntax‑highlighted
                        val highlighted = remember(text, language, isDarkMode) {
                            highlight(text, language, isDarkMode)
                        }
                        SelectionContainer {
                            Text(
                                text = highlighted,
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
                                softWrap = false,
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                }
            }
        }

        // -----------------------------------------------------------------
        //  EDITING MODE – full‑screen editor (Dialog)
        // -----------------------------------------------------------------
        if (editing) {
            FullScreenCodeEditor(
                initialCode = text, language = language, onDismiss = { newCode ->
                    text = newCode
                    editing = false
                })
        } else {
            if (showReadDialog.not()) {
                // No expanded view – the card already shows the title only.
                // The rest of the UI (FAB, etc.) stays hidden in the collapsed state.
            } else {
                // Expanded view: show the code with lazy‑scroll list & jump‑to‑bottom FAB
                val highlighted = remember(text, language, isDarkMode) {
                    highlight(text, language, isDarkMode)
                }
                SelectionContainer {
                    Text(
                        text = highlighted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = rDP(12.dp))
                            .horizontalScroll(hScroll),
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = rSp(12.sp),
                            lineHeight = rSp(20.sp)
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
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
                                val last = highlightedLinesCount(text)
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

private fun highlightedLinesCount(text: String): Int = text.split('\n').size

@Composable
private fun FullScreenCodeEditor(
    initialCode: String,
    onDismiss: (String) -> Unit,
    language: String? = null,
) {
    var source by remember { mutableStateOf(initialCode) }
    var showWebPreview by remember { mutableStateOf(false) }

    val clipboard = LocalClipboardManager.current

    Dialog(
        onDismissRequest = { onDismiss(source) },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .padding(rDP(8.dp))
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(rDP(8.dp)))
                .padding(rDP(16.dp))
        ) {
            Column {
                // ---------- Header ----------
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = rDP(8.dp)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LanguagePill(language ?: "code")
                    Spacer(Modifier.weight(1f))

                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(source))
                        },
                        shape = RoundedCornerShape(rDP(8.dp)),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondary.copy(0.1f),
                            contentColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Icon(
                            Icons.Rounded.ContentCopy,
                            contentDescription = "Copy",
                            modifier = Modifier.size(rDP(20.dp))
                        )
                    }

                    Spacer(Modifier.width(rDP(8.dp)))

                    IconButton(
                        onClick = {
                            onDismiss(source)
                        },
                        shape = RoundedCornerShape(rDP(8.dp)),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondary.copy(0.1f),
                            contentColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Icon(
                            Icons.Rounded.Done,
                            contentDescription = "Done",
                            modifier = Modifier.size(rDP(20.dp))
                        )
                    }

                    if (language.equals("html", ignoreCase = true)){
                        Spacer(Modifier.width(rDP(8.dp)))

                        IconButton(
                            onClick = {
                                showWebPreview = !showWebPreview
                            },
                            shape = RoundedCornerShape(rDP(8.dp)),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondary.copy(0.1f),
                                contentColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = "Run",
                                modifier = Modifier.size(rDP(20.dp))
                            )
                        }
                    }

                }

                // ---------- Editor / WebView ----------
                Crossfade(targetState = showWebPreview) { isPreview ->
                    if (isPreview) {
                        MicroBrowserView(
                            htmlContent = source, // your HTML string
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                                    RoundedCornerShape(rDP(12.dp))
                                )
                        )
                    } else {
                        BasicTextField(
                            value = source, onValueChange = { source = it }, textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = rSp(11.sp),
                                lineHeight = rSp(14.sp),
                                color = MaterialTheme.colorScheme.onSurface,
                                textMotion = TextMotion.Animated
                            ), modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                                    RoundedCornerShape(rDP(12.dp))
                                )
                                .padding(rDP(12.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkdownTable(
    table: MdBlock.Table,
    modifier: Modifier = Modifier,
) {
    // 1. Determine the number of columns (use a safe fallback of 1)
    val numColumns = table.rows.firstOrNull()?.size ?: 1

    // 2. Decide on a fixed per‑cell width (you can make this dynamic if you like)
    val cellWidth = rDP(110.dp)
    val dividerWidth = rDP(1.dp)

    // 3. Total width of the table (including horizontal padding)
    val totalTableWidth = cellWidth * numColumns + dividerWidth * (numColumns - 1)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(0.5f),
                shape = RoundedCornerShape(rDP(8.dp))
            )
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = .01f),
                shape = RoundedCornerShape(rDP(8.dp))
            ),
    ) {
        // ----------------------------------------------------------
        // The entire table occupies a fixed width so that the weight
        // modifier in the Row below can actually split the space.
        // ----------------------------------------------------------
        Column(
            modifier = Modifier.width(totalTableWidth),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            table.rows.forEachIndexed { rowIndex, row ->
                // Row contents
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            when {
                                rowIndex == 0 -> MaterialTheme.colorScheme.primary.copy(0.1f)
                                else -> Color.Transparent
                            }, shape = RoundedCornerShape(rDP(8.dp), rDP(8.dp))
                        )
                        .padding(horizontal = rDP(8.dp))
                        .height(IntrinsicSize.Min), // Forces each box to match the row’s
                    horizontalArrangement = Arrangement.spacedBy(rDP(2.dp))
                ) {
                    // --------------------------------------------------------------------------
                    // Cell loop – weight takes care of equal widths.
                    // --------------------------------------------------------------------------
                    row.forEachIndexed { colIndex, cellText ->
                        Box(
                            modifier = Modifier
                                .padding(vertical = rDP(12.dp))
                                .weight(1f)
                                .fillMaxHeight(), contentAlignment = Alignment.Center
                        ) {
                            RichText(
                                text = cellText,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = if (rowIndex == 0) FontWeight.SemiBold else FontWeight.Normal,
                                    textAlign = TextAlign.Center
                                ),
                            )
                        }

                        //  Vertical divider between cells (except after the last one)
                        if (colIndex < row.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .background(
                                        when {
                                            rowIndex == 0 -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                        }
                                    )
                            )
                        }
                    }
                }

                // ------------------------------------------------------------------
                // One horizontal line *after the header* – change the logic if you
                // want the bottom line of the table, the top line, or no lines at all.
                // ------------------------------------------------------------------
                if (rowIndex != 0 && table.rows.size > 1 && rowIndex < table.rows.size - 1) {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                }
            }
        }

        VerticalDivider(
            thickness = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    }
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

    fun flushText() {
        if (textBuf.isNotEmpty()) {
            out += MdBlock.Text(textBuf.toString().trimEnd())
            textBuf.clear()
        }
    }

    // ----------- improved table detector --------------------------------------
    fun tryParseTable(startIdx: Int): Pair<Int, MdBlock.Table?> {
        var idx = startIdx
        val rows = mutableListOf<List<String>>()

        // ✅ Limit table scanning to max 100 rows
        var rowCount = 0
        while (idx < lines.size && rowCount < 100) {
            val line = lines[idx].trim()

            if (line.isEmpty() || !line.startsWith("|")) break

            val cells = line.removePrefix("|").removeSuffix("|").split("|").map { it.trim() }

            if (cells.all { it.isEmpty() }) {
                idx++
                break
            }

            rows += cells
            idx++
            rowCount++
        }

        if (rows.size < 2) return startIdx to null

        // ✅ Fast separator check without complex regex
        val separatorRow = rows[1]
        val isSeparator = separatorRow.all { cell ->
            cell.isEmpty() || cell.all { it == '-' || it == ':' || it.isWhitespace() }
        }

        if (!isSeparator) return startIdx to null

        // ✅ Simple alignment detection
        val align = separatorRow.map { cell ->
            when {
                cell.startsWith(":") && cell.endsWith(":") -> TextAlign.Center
                cell.endsWith(":") -> TextAlign.End
                else -> TextAlign.Start
            }
        }

        val dataRows = rows.filterIndexed { rowIndex, _ -> rowIndex != 1 }
        val maxCols = dataRows.maxOfOrNull { it.size } ?: align.size
        val normalizedRows = dataRows.map { row ->
            if (row.size < maxCols) {
                row + List(maxCols - row.size) { "" }
            } else {
                row.take(maxCols)
            }
        }

        return idx to MdBlock.Table(normalizedRows, align)
    }

    // ---------------- main loop -----------------------------------------------
    while (i < lines.size) {
        val raw = lines[i]
        val trimmed = raw.trimStart()

        when {
            // code block start/end
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

            // inside code block
            inCode -> {
                codeBuf.append(raw)
                if (i != lines.lastIndex) codeBuf.append('\n')
                i++
                continue
            }

            // potential table
            trimmed.startsWith("|") -> {
                val (newIdx, tbl) = tryParseTable(i)
                if (tbl != null) {
                    flushText()
                    out += tbl
                    i = newIdx
                    continue
                }

                // not a real table – fall back to plain text
                textBuf.append(raw)
                if (i != lines.lastIndex) textBuf.append('\n')
                i++
            }

            else -> {
                textBuf.append(raw)
                if (i != lines.lastIndex) textBuf.append('\n')
                i++
            }
        }
    }

    // final flush
    if (inCode) {
        out += MdBlock.Code(codeLang, codeBuf.toString().trimEnd())
    } else {
        flushText()
    }
    return out
}

private sealed class MdBlock {
    data class Text(val content: String) : MdBlock()
    data class Code(val lang: String?, val code: String) : MdBlock()
    data class Table(val rows: List<List<String>>, val align: List<TextAlign>) : MdBlock()
}

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
    val funDecl = SpanStyle(
        color = if (isDarkMode) Color(0xFF61AFEF) else Color(0xFF0070C2)
    )
    val call = SpanStyle(
        color = if (isDarkMode) Color(0xFF56B6C2) else Color(0xFF0097A7)
    )

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
        styleAll(
            Regex("\\b([A-Z][A-Za-z0-9_]*)\\b"), typ
        )
        styleAll(
            Regex("\\b[a-zA-Z_]\\w*(?=\\s*\\()"), call
        )
    }

    return b.toAnnotatedString()
}

@Composable
fun RichText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    fontFamily: FontFamily = FontFamily.Default,
    fontWeight: FontWeight = FontWeight.Light
) {
    val annotatedText = remember(text) {
        buildAnnotatedString {
            val lines = text.lines()
            for (i in lines.indices) {
                val line = lines[i]
                val t = line.trimStart()
                when {
                    t.startsWith("# ") -> {
                        appendStyledSegment(t.removePrefix("# "), style, 1.5f, isHeader = true)
                    }

                    t.startsWith("## ") -> {
                        appendStyledSegment(t.removePrefix("## "), style, 1.3f, isHeader = true)
                    }

                    t.startsWith("### ") -> {
                        appendStyledSegment(t.removePrefix("### "), style, 1.2f, isHeader = true)
                    }

                    t.startsWith("#### ") -> {
                        appendStyledSegment(t.removePrefix("#### "), style, 1.1f, isHeader = true)
                    }

                    t.startsWith("##### ") -> {
                        appendStyledSegment(t.removePrefix("##### "), style, 1.0f, isHeader = true)
                    }

                    t.startsWith("> ") -> {
                        withStyle(
                            SpanStyle(
                                fontStyle = FontStyle.Italic, color = color.copy(alpha = 0.7f)
                            )
                        ) {
                            append("❝ ")
                            appendStyledSegment(t.removePrefix("> "), style)
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
                        appendStyledSegment(content, style)
                        append("\n")
                    }

                    t.matches(Regex("^\\d+\\. .*")) -> {
                        val parts = t.split(". ", limit = 2)
                        append("${parts[0]}. ")
                        if (parts.size > 1) appendStyledSegment(parts[1], style)
                        append("\n")
                    }

                    else -> {
                        appendStyledSegment(line, style)
                        if (i < lines.lastIndex) append("\n")
                    }
                }
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

private fun AnnotatedString.Builder.appendStyledSegment(
    text: String,
    baseStyle: TextStyle,
    scale: Float = 1f,
    isHeader: Boolean = false,
    color: Color = Color.Unspecified
) {
    if (text.isEmpty()) return

    var idx = 0
    while (idx < text.length) {
        when {
            text.startsWith("***", idx) && text.indexOf("***", idx + 3) != -1 -> {
                val end = text.indexOf("***", idx + 3)
                withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.ExtraBold,
                        fontStyle = FontStyle.Italic,
                        fontSize = baseStyle.fontSize * scale,
                        color = color
                    )
                ) { append(text.substring(idx + 3, end)) }
                idx = end + 3
            }

            text.startsWith("**", idx) && text.indexOf("**", idx + 2) != -1 -> {
                val end = text.indexOf("**", idx + 2)
                withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = baseStyle.fontSize * scale,
                        color = color
                    )
                ) { append(text.substring(idx + 2, end)) }
                idx = end + 2
            }

            text.startsWith("*", idx) && text.indexOf("*", idx + 1) != -1 -> {
                val end = text.indexOf("*", idx + 1)
                withStyle(
                    SpanStyle(
                        fontStyle = FontStyle.Italic,
                        fontSize = baseStyle.fontSize * scale,
                        color = color
                    )
                ) { append(text.substring(idx + 1, end)) }
                idx = end + 1
            }

            text.startsWith("`", idx) && text.indexOf("`", idx + 1) != -1 -> {
                val end = text.indexOf("`", idx + 1)
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = Color.Gray.copy(alpha = 0.2f),
                        fontSize = baseStyle.fontSize * scale,
                        color = color
                    )
                ) { append(text.substring(idx + 1, end)) }
                idx = end + 1
            }

            text.startsWith("~~", idx) && text.indexOf("~~", idx + 2) != -1 -> {
                val end = text.indexOf("~~", idx + 2)
                withStyle(
                    SpanStyle(
                        textDecoration = TextDecoration.LineThrough,
                        fontSize = baseStyle.fontSize * scale,
                        color = color
                    )
                ) { append(text.substring(idx + 2, end)) }
                idx = end + 2
            }

            text.startsWith("__", idx) && text.indexOf("__", idx + 2) != -1 -> {
                val end = text.indexOf("__", idx + 2)
                withStyle(
                    SpanStyle(
                        textDecoration = TextDecoration.Underline,
                        fontSize = baseStyle.fontSize * scale,
                        color = color
                    )
                ) { append(text.substring(idx + 2, end)) }
                idx = end + 2
            }

            else -> {
                val char = text[idx]

                // Check if this is a high surrogate (first part of emoji)
                val isHighSurrogate = Character.isHighSurrogate(char)

                // Get the codepoint (handles surrogate pairs correctly)
                val codePoint = if (isHighSurrogate && idx + 1 < text.length) {
                    Character.toCodePoint(char, text[idx + 1])
                } else {
                    char.code
                }

                // Check if codepoint is an emoji
                val isEmoji = codePoint in 0x1F300..0x1FAF8 ||  // Emoticons & symbols
                        codePoint in 0x2600..0x27BF ||     // Misc symbols & dingbats
                        codePoint in 0x1F900..0x1F9FF ||   // Supplemental symbols
                        codePoint in 0x1F600..0x1F64F ||   // Emoticons
                        codePoint in 0x1F680..0x1F6FF ||   // Transport & map
                        codePoint == 0xFE0F ||             // Variation selector
                        codePoint == 0x200D                // Zero-width joiner

                if (isEmoji) {
                    // Append emoji without color styling
                    if (isHighSurrogate && idx + 1 < text.length) {
                        append(char)
                        append(text[idx + 1])
                        idx += 2
                    } else {
                        append(char)
                        idx++
                    }
                } else {
                    // Apply styling to regular characters
                    withStyle(
                        SpanStyle(
                            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                            fontSize = baseStyle.fontSize * scale,
                            color = color
                        )
                    ) { append(char) }
                    idx++
                }
            }
        }
    }

    if (isHeader) append("\n\n")
}


@Composable
private fun LanguagePill(label: String) {
    Text(
        text = label, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium
    )
}