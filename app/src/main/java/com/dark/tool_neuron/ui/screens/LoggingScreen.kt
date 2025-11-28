package com.dark.tool_neuron.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
    var selectedSessionForTerminal by remember { mutableStateOf<String?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }

    // Load sessions
    LaunchedEffect(Unit) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val root = UserDataManager.getRootNode()
            sessions = AppLogger.getSessions(root).reversed() // Newest first
        }
        isLoading = false

        // Auto-select first session for terminal view
        if (sessions.isNotEmpty()) {
            selectedSessionForTerminal = sessions.first().id
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("System Logs")
                    if (sessions.isNotEmpty()) {
                        Text(
                            text = "${sessions.size} session${if (sessions.size > 1) "s" else ""} • ${sessions.sumOf { it.logs.size }} logs",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }, navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, "Back")
                }
            }, actions = {
                // View mode toggle
                IconButton(onClick = { viewMode = LogViewMode.TERMINAL }) {
                    Icon(
                        Icons.Default.Terminal,
                        contentDescription = "Terminal View",
                        tint = if (viewMode == LogViewMode.TERMINAL) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = { viewMode = LogViewMode.UI }) {
                    Icon(
                        Icons.Default.ViewList,
                        contentDescription = "UI View",
                        tint = if (viewMode == LogViewMode.UI) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
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
            })
        }) { padding ->
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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        "No logs yet",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Logs will appear here as you use the app",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            when (viewMode) {
                LogViewMode.TERMINAL -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    // Session selector for terminal view
                    SessionSelector(
                        sessions = sessions,
                        selectedSessionId = selectedSessionForTerminal,
                        onSessionSelected = { selectedSessionForTerminal = it })

                    // Show only selected session
                    val selectedSession = sessions.find { it.id == selectedSessionForTerminal }
                    if (selectedSession != null) {
                        TerminalView(
                            session = selectedSession, modifier = Modifier.fillMaxSize()
                        )
                    }
                }

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

            AlertDialog(onDismissRequest = { showClearDialog = false }, icon = {
                Icon(
                    Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            }, title = { Text("Clear All Logs") }, text = {
                Text("Are you sure you want to delete all ${sessions.size} log session${if (sessions.size > 1) "s" else ""}? This cannot be undone.")
            }, confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val root = UserDataManager.getRootNode()
                                AppLogger.clearAllLogs(root)
                                UserDataManager.performTreeSave(applicationContext)
                            }
                            sessions = emptyList()
                            showClearDialog = false
                        }
                    }, colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Clear All")
                }
            }, dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            })
        }
    }
}

@Composable
fun SessionSelector(
    sessions: List<LogSession>,
    selectedSessionId: String?,
    onSessionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    Icons.Default.FilterList,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Select Session",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            // Session chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sessions.forEach { session ->
                    FilterChip(
                        selected = session.id == selectedSessionId,
                        onClick = { onSessionSelected(session.id) },
                        label = {
                            Column {
                                Text(
                                    session.name, style = MaterialTheme.typography.labelLarge
                                )
                                Text(
                                    "${session.logs.size} logs • ${session.getFormattedDuration()}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        },
                        leadingIcon = if (session.id == selectedSessionId) {
                            {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else null)
                }
            }
        }
    }
}

@Composable
fun TerminalView(
    session: LogSession, modifier: Modifier = Modifier
) {
    val scrollState = rememberLazyListState()
    val isDarkTheme = !MaterialTheme.colorScheme.surface.luminance().let { it > 0.5f }

    // Terminal colors
    val terminalBg = if (isDarkTheme) Color(0xFF0D1117) else Color(0xFFF6F8FA)
    val terminalText = if (isDarkTheme) Color(0xFFE6EDF3) else Color(0xFF24292F)

    Surface(
        modifier = modifier, color = terminalBg
    ) {
        LazyColumn(
            state = scrollState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Session header
            item(key = "header_${session.id}") {
                TerminalSessionHeader(
                    session = session, textColor = terminalText
                )
            }

            // All logs
            items(
                items = session.logs, key = { "${session.id}_${it.timestamp}" }) { log ->
                TerminalLogLine(
                    log = log, isDarkTheme = isDarkTheme, textColor = terminalText
                )
            }

            // Session footer
            item(key = "footer_${session.id}") {
                TerminalSessionFooter(
                    session = session, textColor = terminalText
                )
            }
        }
    }
}

@Composable
fun TerminalSessionHeader(
    session: LogSession, textColor: Color
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
                withStyle(SpanStyle(color = Color(0xFF79C0FF))) {
                    append("║ ")
                }
                append("Total Logs: ")
                withStyle(SpanStyle(color = Color(0xFF56D364), fontWeight = FontWeight.Bold)) {
                    append("${session.logs.size}")
                }
                append("\n")
                withStyle(SpanStyle(color = Color(0xFF58A6FF))) {
                    append("╚═══════════════════════╝")
                }
            }, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
fun TerminalSessionFooter(
    session: LogSession, textColor: Color
) {
    val separatorColor = textColor.copy(alpha = 0.3f)

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = Color(0xFF58A6FF))) {
                    append("╔═══ SESSION END ═══╗")
                }
                append("\n")
                withStyle(SpanStyle(color = Color(0xFF79C0FF))) {
                    append("║ ")
                }
                append("Ended: ")
                withStyle(SpanStyle(color = Color(0xFFBB80FF))) {
                    append(session.endTime?.let {
                        java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                            .format(java.util.Date(it))
                    } ?: "In Progress")
                }
                append("\n")
                withStyle(SpanStyle(color = Color(0xFF58A6FF))) {
                    append("╚═══════════════════╝")
                }
            }, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp
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
    log: LogEntry, isDarkTheme: Boolean, textColor: Color
) {
    val (levelPrefix, levelColor) = when (log.level) {
        AppLogger.LogLevel.INFO -> "INFO " to (if (isDarkTheme) Color(0xFF56D364) else Color(
            0xFF1A7F37
        ))

        AppLogger.LogLevel.WARN -> "WARN " to (if (isDarkTheme) Color(0xFFE3B341) else Color(
            0xFF9A6700
        ))

        AppLogger.LogLevel.ERROR -> "ERROR" to (if (isDarkTheme) Color(0xFFFF7B72) else Color(
            0xFFCF222E
        ))
    }

    val timestampColor = if (isDarkTheme) Color(0xFF8B949E) else Color(0xFF57606A)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
    ) {
        // Main log line
        Text(
            text = buildAnnotatedString {
                // Timestamp
                withStyle(SpanStyle(color = timestampColor)) {
                    append(log.getFormattedTime())
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
            }, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp
        )

        // Details (if any)
        log.details?.let { details ->
            details.forEach { (key, value) ->
                Text(
                    text = buildAnnotatedString {
                        append("         ")
                        withStyle(SpanStyle(color = Color(0xFF79C0FF))) {
                            append("│ ")
                        }
                        withStyle(SpanStyle(color = Color(0xFFD2A8FF))) {
                            append("$key:")
                        }
                        append(" ")
                        withStyle(
                            SpanStyle(
                                color = if (isDarkTheme) Color(0xFFA5D6FF) else Color(
                                    0xFF0969DA
                                )
                            )
                        ) {
                            append(value.toString())
                        }
                    }, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp
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
                onToggle = { onToggleSession(session.id) })
        }
    }
}

@Composable
fun SessionCard(
    session: LogSession, isExpanded: Boolean, onToggle: () -> Unit
) {
    // Animation for expansion
    val expandProgress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f, animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow
        ), label = "expand"
    )

    // Rotation animation for icon
    val iconRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f, animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium
        ), label = "iconRotation"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow
                )
            ), elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp, pressedElevation = 2.dp
        ), colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() }) { onToggle() }
                .padding(16.dp)) {
            // Session Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Animated icon
                        Icon(
                            if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier
                                .size(20.dp)
                                .graphicsLayer {
                                    scaleX = 0.9f + (expandProgress * 0.1f)
                                    scaleY = 0.9f + (expandProgress * 0.1f)
                                },
                            tint = androidx.compose.ui.graphics.lerp(
                                MaterialTheme.colorScheme.onSurfaceVariant,
                                MaterialTheme.colorScheme.primary,
                                expandProgress
                            )
                        )

                        Text(
                            text = session.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Animated info chips
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .padding(start = 28.dp)
                            .graphicsLayer {
                                alpha = 0.7f + (expandProgress * 0.3f)
                            }) {
                        InfoChip(
                            icon = Icons.Default.AccessTime, text = session.getFormattedStartTime()
                        )

                        InfoChip(
                            icon = Icons.Default.Timer, text = session.getFormattedDuration()
                        )

                        InfoChip(
                            icon = Icons.Default.Description,
                            text = "${session.logs.size} logs",
                            highlighted = true
                        )
                    }
                }

                // Animated expand/collapse icon
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer {
                            rotationZ = iconRotation
                        },
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Expanded logs with animation
            AnimatedVisibility(
                visible = isExpanded && session.logs.isNotEmpty(), enter = fadeIn(
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) + expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ), exit = fadeOut(
                    animationSpec = tween(200, easing = LinearOutSlowInEasing)
                ) + shrinkVertically(
                    animationSpec = tween(200, easing = LinearOutSlowInEasing)
                )
            ) {
                Column {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 16.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        session.logs.forEachIndexed { index, log ->
                            // Staggered entrance animation for logs
                            Box(
                                modifier = Modifier.graphicsLayer {
                                    val delay = (index * 0.05f).coerceAtMost(0.3f)
                                    val progress =
                                        (expandProgress - delay).coerceIn(0f, 1f) / (1f - delay)

                                    alpha = progress
                                    translationY = (1f - progress) * 20f
                                }) {
                                LogEntryItem(log)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoChip(
    icon: ImageVector, text: String, highlighted: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (highlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = if (highlighted) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (highlighted) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
fun LogEntryItem(log: LogEntry) {
    val levelColor = when (log.level) {
        AppLogger.LogLevel.INFO -> Color(0xFF4CAF50)
        AppLogger.LogLevel.WARN -> Color(0xFFFF9800)
        AppLogger.LogLevel.ERROR -> Color(0xFFF44336)
    }

    val levelIcon = when (log.level) {
        AppLogger.LogLevel.INFO -> Icons.Default.CheckCircle
        AppLogger.LogLevel.WARN -> Icons.Default.Warning
        AppLogger.LogLevel.ERROR -> Icons.Default.Error
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .padding(12.dp)
    ) {
        // Level indicator
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                levelIcon,
                contentDescription = log.level.name,
                modifier = Modifier.size(20.dp),
                tint = levelColor
            )
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(if (log.details != null) 40.dp else 20.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(levelColor.copy(alpha = 0.3f))
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Time and level
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = log.getFormattedTime(),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(4.dp), color = levelColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = log.level.name,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = levelColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Message
            Text(
                text = log.message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp),
                color = MaterialTheme.colorScheme.onSurface
            )

            // Details
            log.details?.let { details ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    details.forEach { (key, value) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "$key:",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
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