package com.dark.tool_neuron.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dark.tool_neuron.logger.AppLogger
import com.dark.tool_neuron.logger.LogEntry
import com.dark.tool_neuron.logger.LogSession
import com.dark.tool_neuron.worker.UserDataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LogViewMode {
    TERMINAL, UI
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoggingScreen(
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var sessions by remember { mutableStateOf<List<LogSession>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var viewMode by remember { mutableStateOf(LogViewMode.UI) }
    var expandedSessionId by remember { mutableStateOf<String?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }

    // Load sessions
    LaunchedEffect(Unit) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val root = UserDataManager.getRootNode()
            sessions = AppLogger.getSessions(root).reversed() // Newest first
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("System Logs") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    // View mode toggle
                    IconButton(onClick = { viewMode = LogViewMode.TERMINAL }) {
                        Icon(
                            Icons.Default.Terminal,
                            contentDescription = "Terminal View",
                            tint = if (viewMode == LogViewMode.TERMINAL)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = { viewMode = LogViewMode.UI }) {
                        Icon(
                            Icons.Default.ViewList,
                            contentDescription = "UI View",
                            tint = if (viewMode == LogViewMode.UI)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Default.Delete, "Clear All")
                    }

                    IconButton(onClick = {
                        scope.launch {
                            isLoading = true
                            withContext(Dispatchers.IO) {
                                val root = UserDataManager.getRootNode()
                                sessions = AppLogger.getSessions(root).reversed()
                            }
                            isLoading = false
                        }
                    }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (sessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No logs yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            when (viewMode) {
                LogViewMode.TERMINAL -> TerminalView(
                    sessions = sessions,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )

                LogViewMode.UI -> UIView(
                    sessions = sessions,
                    expandedSessionId = expandedSessionId,
                    onToggleSession = { sessionId ->
                        expandedSessionId = if (expandedSessionId == sessionId) {
                            null
                        } else {
                            sessionId
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }
        }

        if (showClearDialog) {
            val applicationContext = LocalContext.current

            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                title = { Text("Clear All Logs") },
                text = { Text("Are you sure you want to delete all log sessions? This cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val root = UserDataManager.getRootNode()
                                AppLogger.clearAllLogs(root)
                                UserDataManager.performTreeSave(applicationContext)
                            }
                            sessions = emptyList()
                            showClearDialog = false
                        }
                    }) {
                        Text("Clear All", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun TerminalView(
    sessions: List<LogSession>,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberLazyListState()
    val isDarkTheme = !MaterialTheme.colorScheme.surface.luminance().let { it > 0.5f }

    // Terminal colors based on theme
    val terminalBg = if (isDarkTheme) Color(0xFF0D1117) else Color(0xFFF6F8FA)
    val terminalText = if (isDarkTheme) Color(0xFFE6EDF3) else Color(0xFF24292F)

    Surface(
        modifier = modifier,
        color = terminalBg
    ) {
        LazyColumn(
            state = scrollState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            sessions.forEach { session ->
                // Session header
                item(key = "header_${session.id}") {
                    TerminalSessionHeader(
                        session = session,
                        textColor = terminalText
                    )
                }

                // All logs in this session
                items(session.logs, key = { "${session.id}_${it.timestamp}" }) { log ->
                    TerminalLogLine(
                        log = log,
                        isDarkTheme = isDarkTheme,
                        textColor = terminalText
                    )
                }

                // Session footer
                item(key = "footer_${session.id}") {
                    TerminalSessionFooter(
                        session = session,
                        textColor = terminalText
                    )
                }
            }
        }
    }
}

@Composable
fun TerminalSessionHeader(
    session: LogSession,
    textColor: Color
) {
    val separatorColor = textColor.copy(alpha = 0.3f)

    Column(modifier = Modifier.fillMaxWidth()) {
        // Top separator
        Text(
            text = "═".repeat(80),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = separatorColor,
            modifier = Modifier.horizontalScroll(rememberScrollState())
        )

        // Session info
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = Color(0xFF58A6FF))) {
                    append("╔═══ SESSION START ═══╗")
                }
                append("\n")
                withStyle(SpanStyle(color = Color(0xFF79C0FF))) {
                    append("║ ")
                }
                append("Name: ")
                withStyle(SpanStyle(color = Color(0xFFFFA657), fontWeight = FontWeight.Bold)) {
                    append(session.name)
                }
                append("\n")
                withStyle(SpanStyle(color = Color(0xFF79C0FF))) {
                    append("║ ")
                }
                append("Started: ")
                withStyle(SpanStyle(color = Color(0xFF79C0FF))) {
                    append(session.getFormattedStartTime())
                }
                append("\n")
                withStyle(SpanStyle(color = Color(0xFF79C0FF))) {
                    append("║ ")
                }
                append("Duration: ")
                withStyle(SpanStyle(color = Color(0xFFBB80FF))) {
                    append(session.getFormattedDuration())
                }
                append("\n")
                withStyle(SpanStyle(color = Color(0xFF58A6FF))) {
                    append("╚═══════════════════╝")
                }
            },
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun TerminalSessionFooter(
    session: LogSession,
    textColor: Color
) {
    val separatorColor = textColor.copy(alpha = 0.3f)

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = Color(0xFF58A6FF))) {
                    append("╔═══ SESSION END ═══╗")
                }
                append("\n")
                withStyle(SpanStyle(color = Color(0xFF79C0FF))) {
                    append("║ ")
                }
                append("Total logs: ")
                withStyle(SpanStyle(color = Color(0xFF56D364), fontWeight = FontWeight.Bold)) {
                    append("${session.logs.size}")
                }
                append("\n")
                withStyle(SpanStyle(color = Color(0xFF58A6FF))) {
                    append("╚═══════════════════╝")
                }
            },
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )

        Text(
            text = "═".repeat(80),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = separatorColor,
            modifier = Modifier.horizontalScroll(rememberScrollState())
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun TerminalLogLine(
    log: LogEntry,
    isDarkTheme: Boolean,
    textColor: Color
) {
    val (levelPrefix, levelColor) = when (log.level) {
        AppLogger.LogLevel.INFO -> "[INFO]" to (if (isDarkTheme) Color(0xFF56D364) else Color(0xFF1A7F37))
        AppLogger.LogLevel.WARN -> "[WARN]" to (if (isDarkTheme) Color(0xFFE3B341) else Color(0xFF9A6700))
        AppLogger.LogLevel.ERROR -> "[ERROR]" to (if (isDarkTheme) Color(0xFFFF7B72) else Color(0xFFCF222E))
    }

    val timestampColor = if (isDarkTheme) Color(0xFF8B949E) else Color(0xFF57606A)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        // Main log line
        Text(
            text = buildAnnotatedString {
                // Timestamp
                withStyle(SpanStyle(color = timestampColor)) {
                    append("[${log.getFormattedTime()}]")
                }
                append(" ")

                // Level
                withStyle(SpanStyle(color = levelColor, fontWeight = FontWeight.Bold)) {
                    append(levelPrefix)
                }
                append(" ")

                // Message
                withStyle(SpanStyle(color = textColor)) {
                    append(log.message)
                }
            },
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )

        // Details (if any)
        log.details?.let { details ->
            details.forEach { (key, value) ->
                Text(
                    text = buildAnnotatedString {
                        append("  ")
                        withStyle(SpanStyle(color = Color(0xFF79C0FF))) {
                            append("├─")
                        }
                        append(" ")
                        withStyle(SpanStyle(color = Color(0xFFD2A8FF))) {
                            append("$key:")
                        }
                        append(" ")
                        withStyle(SpanStyle(color = if (isDarkTheme) Color(0xFFA5D6FF) else Color(0xFF0969DA))) {
                            append(value.toString())
                        }
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
fun UIView(
    sessions: List<LogSession>,
    expandedSessionId: String?,
    onToggleSession: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(sessions, key = { it.id }) { session ->
            SessionCard(
                session = session,
                isExpanded = expandedSessionId == session.id,
                onToggle = { onToggleSession(session.id) }
            )
        }
    }
}

@Composable
fun SessionCard(
    session: LogSession,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(16.dp)
        ) {
            // Session Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = "Started: ${session.getFormattedStartTime()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = "Duration: ${session.getFormattedDuration()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = "${session.logs.size} logs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand"
                )
            }

            // Expanded logs
            if (isExpanded && session.logs.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    session.logs.forEach { log ->
                        LogEntryItem(log)
                    }
                }
            }
        }
    }
}

@Composable
fun LogEntryItem(log: LogEntry) {
    val levelColor = when (log.level) {
        AppLogger.LogLevel.INFO -> Color(0xFF4CAF50)
        AppLogger.LogLevel.WARN -> Color(0xFFFF9800)
        AppLogger.LogLevel.ERROR -> Color(0xFFF44336)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .padding(12.dp)
    ) {
        // Level indicator bar
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(levelColor)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Time and level
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "[${log.getFormattedTime()}]",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = log.level.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = levelColor
                )
            }

            // Message
            Text(
                text = log.message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )

            // Details
            log.details?.let { details ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    details.forEach { (key, value) ->
                        Row {
                            Text(
                                text = "$key: ",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = value.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

// Extension function for color luminance
private fun Color.luminance(): Float {
    return 0.299f * red + 0.587f * green + 0.114f * blue
}