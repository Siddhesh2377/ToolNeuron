package com.dark.tool_neuron.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.ModeEditOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.dark.tool_neuron.ui.theme.rDP
import com.dark.tool_neuron.ui.theme.rSp

// OPTIMIZATION 1: Remove debouncing - it adds 150ms delay to every update
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    style: TextStyle = MaterialTheme.typography.bodySmall
) {
    // OPTIMIZATION: Parse blocks only when text changes, use remember with stable key
    val blocks = remember(text) { parseMarkdownBlocks(text) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(rDP(8.dp))
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
                    code = block.code,
                    language = block.lang,
                    modifier = Modifier.fillMaxWidth()
                )

                is MdBlock.Table -> MarkdownTable(
                    table = block,
                    modifier = Modifier.fillMaxWidth()
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
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf(code) }
    var showWebPreview by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(code) {
        if (!editing) {
            text = code
        }
    }

    val clipboard = LocalClipboardManager.current
    val isHtml = language.equals("html", ignoreCase = true)

    // Enhanced color scheme
    val cardColor = if (isDarkMode) Color(0xFF1E1E2E) else Color(0xFFF8F9FA)
    val borderColor = if (isDarkMode) Color(0xFF313244) else Color(0xFFDEE2E6)
    val accentColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(rDP(12.dp)))
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(rDP(12.dp))
            )
            .background(cardColor)
    ) {
        // Header - Fixed alignment and spacing
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            accentColor.copy(alpha = 0.08f),
                            accentColor.copy(alpha = 0.03f)
                        )
                    )
                )
                .padding(horizontal = rDP(16.dp), vertical = rDP(8.dp)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Language pill
            Text(
                text = language?.uppercase() ?: "TEXT",
                color = accentColor,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                ),
                modifier = Modifier
                    .background(
                        accentColor.copy(alpha = 0.15f),
                        RoundedCornerShape(rDP(6.dp))
                    )
                    .padding(horizontal = rDP(10.dp), vertical = rDP(6.dp))
            )

            // Action buttons - Properly aligned
            Row(
                horizontalArrangement = Arrangement.spacedBy(rDP(4.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Copy button
                IconButton(
                    onClick = { clipboard.setText(AnnotatedString(text)) },
                    modifier = Modifier.size(rDP(36.dp)),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = accentColor.copy(alpha = 0.7f)
                    )
                ) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = "Copy",
                        modifier = Modifier.size(rDP(20.dp))
                    )
                }

                // Edit/Done button
                IconButton(
                    onClick = { editing = !editing },
                    modifier = Modifier.size(rDP(36.dp)),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (editing) accentColor.copy(alpha = 0.2f)
                        else Color.Transparent,
                        contentColor = accentColor.copy(alpha = 0.7f)
                    )
                ) {
                    Icon(
                        if (editing) Icons.Rounded.Done else Icons.Rounded.ModeEditOutline,
                        contentDescription = if (editing) "Done" else "Edit",
                        modifier = Modifier.size(rDP(20.dp))
                    )
                }

                // HTML preview button
                if (isHtml) {
                    IconButton(
                        onClick = { showWebPreview = !showWebPreview },
                        modifier = Modifier.size(rDP(36.dp)),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (showWebPreview) accentColor.copy(alpha = 0.2f)
                            else Color.Transparent,
                            contentColor = accentColor.copy(alpha = 0.7f)
                        )
                    ) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = "Preview",
                            modifier = Modifier.size(rDP(20.dp))
                        )
                    }
                }
            }
        }

        // Content - Smooth transitions
        AnimatedContent(
            targetState = if (editing) "edit" else if (showWebPreview) "preview" else "view",
            transitionSpec = {
                fadeIn(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) + scaleIn(
                    initialScale = 0.95f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) togetherWith fadeOut(animationSpec = tween(150)) + scaleOut(targetScale = 0.95f)
            },
            label = "code_content"
        ) { targetState ->
            when (targetState) {
                "edit" -> CodeEditor(
                    text = text,
                    onTextChange = { text = it },
                    isDarkMode = isDarkMode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = rDP(400.dp))
                )

                "preview" -> if (isHtml) {
                    MicroBrowserView(
                        htmlContent = text,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = rDP(400.dp))
                    )
                }

                else -> CodeViewer(
                    text = text,
                    language = language,
                    isDarkMode = isDarkMode,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// Improved CodeEditor with better styling
@Composable
private fun CodeEditor(
    text: String,
    onTextChange: (String) -> Unit,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val textColor = if (isDarkMode) Color(0xFFCDD6F4) else Color(0xFF1E1E2E)
    val bgColor = if (isDarkMode) Color(0xFF181825) else Color(0xFFFFFFFF)

    BasicTextField(
        value = text,
        onValueChange = onTextChange,
        textStyle = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = rSp(13.sp),
            lineHeight = rSp(20.sp),
            color = textColor,
            textMotion = TextMotion.Animated
        ),
        modifier = modifier
            .background(
                bgColor,
                RoundedCornerShape(bottomStart = rDP(12.dp), bottomEnd = rDP(12.dp))
            )
            .padding(rDP(16.dp))
            .verticalScroll(scrollState),
        decorationBox = { innerTextField ->
            Box(modifier = Modifier.fillMaxSize()) {
                if (text.isEmpty()) {
                    Text(
                        text = "Start typing...",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = rSp(13.sp),
                            color = textColor.copy(alpha = 0.4f)
                        )
                    )
                }
                innerTextField()
            }
        }
    )
}

// Improved CodeViewer with better layout
@Composable
private fun CodeViewer(
    text: String,
    language: String?,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val hScrollState = rememberScrollState()

    val highlighted = remember(text, language, isDarkMode) {
        if (text.length > 10000) {
            AnnotatedString(text)
        } else {
            highlight(text, language, isDarkMode)
        }
    }

    val bgColor = if (isDarkMode) Color(0xFF181825) else Color(0xFFFFFFFF)

    SelectionContainer {
        Text(
            text = highlighted,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = rSp(13.sp),
                lineHeight = rSp(20.sp),
                color = if (isDarkMode) Color(0xFFCDD6F4) else Color(0xFF1E1E2E)
            ),
            modifier = modifier
                .background(
                    bgColor,
                    RoundedCornerShape(bottomStart = rDP(12.dp), bottomEnd = rDP(12.dp))
                )
                .padding(rDP(16.dp))
                .heightIn(max = rDP(400.dp))
                .verticalScroll(scrollState)
                .horizontalScroll(hScrollState),
            softWrap = false
        )
    }
}

// OPTIMIZATION 6: Improved table rendering with better colors
@Composable
private fun MarkdownTable(
    table: MdBlock.Table,
    modifier: Modifier = Modifier,
) {
    val isDarkMode = isSystemInDarkTheme()
    val numColumns = table.rows.firstOrNull()?.size ?: 1
    val cellWidth = rDP(120.dp)
    val dividerWidth = rDP(1.dp)
    val totalTableWidth = cellWidth * numColumns + dividerWidth * (numColumns - 1)

    // Enhanced colors
    val borderColor = if (isDarkMode) Color(0xFF313244) else Color(0xFFDEE2E6)
    val headerBg = if (isDarkMode) Color(0xFF1E1E2E) else MaterialTheme.colorScheme.primary.copy(0.08f)
    val cellBg = if (isDarkMode) Color(0xFF181825) else Color.White
    val dividerColor = if (isDarkMode) Color(0xFF45475A) else Color(0xFFDEE2E6)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .clip(RoundedCornerShape(rDP(12.dp)))
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(rDP(12.dp))
            )
            .background(cellBg)
    ) {
        Column(
            modifier = Modifier.width(totalTableWidth),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            table.rows.forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (rowIndex == 0) headerBg else Color.Transparent)
                        .padding(horizontal = rDP(12.dp))
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(rDP(2.dp))
                ) {
                    row.forEachIndexed { colIndex, cellText ->
                        Box(
                            modifier = Modifier
                                .padding(vertical = rDP(14.dp))
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            RichText(
                                text = cellText,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (rowIndex == 0) FontWeight.Bold else FontWeight.Normal,
                                    textAlign = TextAlign.Center
                                ),
                            )
                        }

                        if (colIndex < row.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .background(dividerColor)
                            )
                        }
                    }
                }

                if (rowIndex == 0 || (rowIndex > 0 && rowIndex < table.rows.size - 1)) {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = dividerColor
                    )
                }
            }
        }
    }
}

// OPTIMIZATION 7: Optimized markdown block parsing
private fun parseMarkdownBlocks(input: String): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    val lines = input.lines()
    var i = 0

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

    // OPTIMIZATION: Improved table detection with limits
    fun tryParseTable(startIdx: Int): Pair<Int, MdBlock.Table?> {
        var idx = startIdx
        val rows = mutableListOf<List<String>>()
        var rowCount = 0

        // Limit to 100 rows for performance
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

        val separatorRow = rows[1]
        val isSeparator = separatorRow.all { cell ->
            cell.isEmpty() || cell.all { it == '-' || it == ':' || it.isWhitespace() }
        }

        if (!isSeparator) return startIdx to null

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

    while (i < lines.size) {
        val raw = lines[i]
        val trimmed = raw.trimStart()

        when {
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

            inCode -> {
                codeBuf.append(raw)
                if (i != lines.lastIndex) codeBuf.append('\n')
                i++
                continue
            }

            trimmed.startsWith("|") -> {
                val (newIdx, tbl) = tryParseTable(i)
                if (tbl != null) {
                    flushText()
                    out += tbl
                    i = newIdx
                    continue
                }
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

// OPTIMIZATION 8: Improved syntax highlighting with Catppuccin colors
private fun highlight(
    code: String,
    language: String?,
    isDarkMode: Boolean
): AnnotatedString {
    val b = AnnotatedString.Builder(code)

    fun styleAll(re: Regex, s: SpanStyle) {
        re.findAll(code).forEach { b.addStyle(s, it.range.first, it.range.last + 1) }
    }

    // Catppuccin Mocha palette (dark) / Latte (light)
    val cmt = if (isDarkMode) SpanStyle(color = Color(0xFF6C7086)) else SpanStyle(color = Color(0xFF9CA0B0))
    val str = if (isDarkMode) SpanStyle(color = Color(0xFFA6E3A1)) else SpanStyle(color = Color(0xFF40A02B))
    val chr = if (isDarkMode) SpanStyle(color = Color(0xFFA6E3A1)) else SpanStyle(color = Color(0xFF40A02B))
    val num = if (isDarkMode) SpanStyle(color = Color(0xFFFAB387)) else SpanStyle(color = Color(0xFFD20F39))
    val ann = if (isDarkMode) SpanStyle(color = Color(0xFFF38BA8)) else SpanStyle(color = Color(0xFFD20F39))
    val kw = if (isDarkMode) {
        SpanStyle(color = Color(0xFFCBA6F7), fontWeight = FontWeight.Bold)
    } else {
        SpanStyle(color = Color(0xFF8839EF), fontWeight = FontWeight.Bold)
    }
    val typ = if (isDarkMode) {
        SpanStyle(color = Color(0xFFF9E2AF))
    } else {
        SpanStyle(color = Color(0xFFDF8E1D))
    }
    val funDecl = if (isDarkMode) {
        SpanStyle(color = Color(0xFF89B4FA))
    } else {
        SpanStyle(color = Color(0xFF1E66F5))
    }
    val call = if (isDarkMode) {
        SpanStyle(color = Color(0xFF94E2D5))
    } else {
        SpanStyle(color = Color(0xFF179299))
    }

    // Comments & strings first
    styleAll(Regex("//.*"), cmt)
    styleAll(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), cmt)
    styleAll(Regex("\"([^\\\\\"]|\\\\.)*\""), str)
    styleAll(Regex("'([^\\\\']|\\\\.)'"), chr)
    styleAll(
        Regex("\\b(?:0x[0-9a-fA-F_]+|[0-9][0-9_]*(?:\\.[0-9_]+)?(?:[eE][+-]?[0-9_]+)?)\\b"),
        num
    )
    styleAll(Regex("@[_A-Za-z][_A-Za-z0-9]*"), ann)

    val lang = language?.lowercase() ?: ""
    if (lang in listOf("kt", "kotlin", "java")) {
        val keywords = listOf(
            "package", "import", "as", "class", "interface", "object", "fun", "val", "var",
            "this", "super", "if", "else", "when", "try", "catch", "finally", "for", "while",
            "do", "return", "break", "continue", "throw", "in", "is", "null", "true", "false",
            "open", "abstract", "override", "private", "public", "protected", "internal",
            "data", "sealed", "enum", "companion", "inline", "noinline", "crossinline",
            "reified", "operator", "infix", "tailrec", "const", "lateinit", "suspend",
            "external", "final", "actual", "expect", "static", "void", "new", "extends",
            "implements", "throws", "synchronized", "volatile", "transient", "native", "strictfp"
        )
        styleAll(Regex("\\b(${keywords.joinToString("|")})\\b"), kw)
        styleAll(
            Regex("\\b(String|Char|Int|Long|Double|Float|Short|Byte|Boolean|Unit|Any|Nothing|Array|List|MutableList|Map|MutableMap|Set|MutableSet)\\b"),
            typ
        )
        styleAll(Regex("(?<![@.])\\b[A-Z][_A-Za-z0-9]*\\b"), typ)
        styleAll(Regex("(?<=\\bfun\\s)\\w+"), funDecl)
        val exclude = (keywords + listOf("if", "for", "while", "when")).joinToString("|")
        styleAll(Regex("\\b(?!$exclude\\b)[a-zA-Z_]\\w*(?=\\s*\\()"), call)
    } else {
        styleAll(
            Regex("\\b(class|def|function|var|let|const|return|if|else|for|while|switch|case|break|continue|try|catch|finally|throw|new)\\b"),
            kw
        )
        styleAll(Regex("\\b([A-Z][A-Za-z0-9_]*)\\b"), typ)
        styleAll(Regex("\\b[a-zA-Z_]\\w*(?=\\s*\\()"), call)
    }

    return b.toAnnotatedString()
}

// OPTIMIZATION 9: Optimized RichText with stable remember
@Composable
fun RichText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    fontFamily: FontFamily = FontFamily.Default,
    fontWeight: FontWeight = FontWeight.Light
) {
    // OPTIMIZATION: Build annotated string only when text changes
    val annotatedText = remember(text) {
        buildAnnotatedString {
            val lines = text.lines()
            for (i in lines.indices) {
                val line = lines[i]
                val t = line.trimStart()
                when {
                    t.startsWith("# ") -> {
                        appendStyledSegment(t.removePrefix("# "), style, 1.6f, isHeader = true)
                    }
                    t.startsWith("## ") -> {
                        appendStyledSegment(t.removePrefix("## "), style, 1.4f, isHeader = true)
                    }
                    t.startsWith("### ") -> {
                        appendStyledSegment(t.removePrefix("### "), style, 1.25f, isHeader = true)
                    }
                    t.startsWith("#### ") -> {
                        appendStyledSegment(t.removePrefix("#### "), style, 1.15f, isHeader = true)
                    }
                    t.startsWith("##### ") -> {
                        appendStyledSegment(t.removePrefix("##### "), style, 1.05f, isHeader = true)
                    }
                    t.startsWith("> ") -> {
                        withStyle(
                            SpanStyle(
                                fontStyle = FontStyle.Italic,
                                color = color.copy(alpha = 0.7f)
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

// OPTIMIZATION 10: Optimized styled segment building
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
            // Bold italic: ***text***
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

            // Bold: **text**
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

            // Italic: *text*
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

            // Inline code: `text`
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

            // Strikethrough: ~~text~~
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

            // Underline: __text__
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
                val isHighSurrogate = Character.isHighSurrogate(char)
                val codePoint = if (isHighSurrogate && idx + 1 < text.length) {
                    Character.toCodePoint(char, text[idx + 1])
                } else {
                    char.code
                }

                // Check if codepoint is an emoji
                val isEmoji = codePoint in 0x1F300..0x1FAF8 ||
                        codePoint in 0x2600..0x27BF ||
                        codePoint in 0x1F900..0x1F9FF ||
                        codePoint in 0x1F600..0x1F64F ||
                        codePoint in 0x1F680..0x1F6FF ||
                        codePoint == 0xFE0F ||
                        codePoint == 0x200D

                if (isEmoji) {
                    if (isHighSurrogate && idx + 1 < text.length) {
                        append(char)
                        append(text[idx + 1])
                        idx += 2
                    } else {
                        append(char)
                        idx++
                    }
                } else {
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