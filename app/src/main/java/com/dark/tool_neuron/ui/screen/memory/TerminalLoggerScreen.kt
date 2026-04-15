package com.dark.tool_neuron.ui.screen.memory

import androidx.compose.animation.AnimatedVisibility
import com.dark.tool_neuron.ui.theme.Motion
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import com.dark.tool_neuron.global.formatTimeOnly
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.global.Standards

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalLoggerScreen() {
    var filterLevel by remember { mutableStateOf<LogLevel?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var autoScroll by remember { mutableStateOf(true) }
    var showFilters by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }

    val allLogs by VaultLogger.logs.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    val filteredLogs = remember(searchQuery, filterLevel, allLogs) {
        var logs = allLogs

        if (filterLevel != null) {
            logs = logs.filter { it.level == filterLevel }
        }

        if (searchQuery.isNotBlank()) {
            logs = logs.filter {
                it.message.contains(searchQuery, ignoreCase = true) ||
                        it.tag.contains(searchQuery, ignoreCase = true)
            }
        }

        logs
    }

    LaunchedEffect(allLogs.size, autoScroll) {
        if (autoScroll && allLogs.isNotEmpty() && !isPaused) {
            delay(100)
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        topBar = {
            MinimalTopBar(
                isPaused = isPaused,
                autoScroll = autoScroll,
                showFilters = showFilters,
                onPauseToggle = { isPaused = !isPaused },
                onAutoScrollToggle = { autoScroll = !autoScroll },
                onClear = { VaultLogger.clear() },
                onFilterToggle = { showFilters = !showFilters }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Filters
            AnimatedVisibility(
                visible = showFilters,
                enter = Motion.Enter,
                exit = Motion.Exit
            ) {
                MinimalFilters(
                    currentFilter = filterLevel,
                    searchQuery = searchQuery,
                    onFilterChange = { filterLevel = it },
                    onSearchChange = { searchQuery = it }
                )
            }

            // Status line
            StatusLine(
                logCount = allLogs.size,
                filteredCount = filteredLogs.size,
                isActive = !isPaused
            )

            // Main log area - pure terminal output
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (filteredLogs.isEmpty()) {
                    EmptyTerminal(isPaused)
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface),
                        contentPadding = PaddingValues(
                            horizontal = 20.dp,
                            vertical = 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                        reverseLayout = true
                    ) {
                        itemsIndexed(filteredLogs.asReversed()) { index, log ->
                            RawLogLine(log)
                        }
                    }
                }

                // Minimal scroll FAB
                if (!autoScroll && filteredLogs.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(20.dp)
                    ) {
                        FloatingActionButton(
                            onClick = { autoScroll = true },
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                TnIcons.ChevronDown,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinimalTopBar(
    isPaused: Boolean,
    autoScroll: Boolean,
    showFilters: Boolean,
    onPauseToggle: () -> Unit,
    onAutoScrollToggle: () -> Unit,
    onClear: () -> Unit,
    onFilterToggle: () -> Unit
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "logger",
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 0.sp
                )

                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            if (!isPaused) Color(0xFF00C853)
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                )
            }
        },
        actions = {
            IconButton(
                onClick = onFilterToggle,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    TnIcons.Filter,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (showFilters) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onAutoScrollToggle,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    TnIcons.ChevronDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (autoScroll) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onPauseToggle,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    if (isPaused) TnIcons.PlayerPlay else TnIcons.PlayerPause,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onClear,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    TnIcons.Trash,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
fun StatusLine(
    logCount: Int,
    filteredCount: Int,
    isActive: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 20.dp, vertical = Standards.SpacingSm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )) {
                    append("showing ")
                }
                withStyle(SpanStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )) {
                    append("$filteredCount")
                }
                withStyle(SpanStyle(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )) {
                    append(" / $logCount")
                }
            },
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )

        Text(
            "max: ${VaultLogger.maxLogSize}",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun MinimalFilters(
    currentFilter: LogLevel?,
    searchQuery: String,
    onFilterChange: (LogLevel?) -> Unit,
    onSearchChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            .padding(Standards.SpacingLg),
        verticalArrangement = Arrangement.spacedBy(Standards.SpacingMd)
    ) {
        // Minimal search
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    "search",
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchChange("") },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            TnIcons.XCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(Standards.SpacingXs),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.outline,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent
            ),
            textStyle = LocalTextStyle.current.copy(
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
        )

        // Level filters - minimal chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
        ) {
            item {
                MinimalChip(
                    label = "all",
                    selected = currentFilter == null,
                    onClick = { onFilterChange(null) }
                )
            }

            items(LogLevel.entries.size) { index ->
                val level = LogLevel.entries[index]
                MinimalChip(
                    label = level.prefix.lowercase(),
                    selected = currentFilter == level,
                    onClick = { onFilterChange(if (currentFilter == level) null else level) },
                    color = getCleanLevelColor(level)
                )
            }
        }
    }
}

@Composable
fun MinimalChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    color: Color? = null
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Standards.SpacingXs),
        color = if (selected) {
            color?.copy(alpha = 0.15f) ?: MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        },
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (selected) {
                color ?: MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            }
        )
    ) {
        Text(
            label,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) {
                color ?: MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun RawLogLine(entry: LogEntry) {
    val levelColor = getCleanLevelColor(entry.level)
    val levelPrefix = getCleanLevelPrefix(entry.level)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        // Single line log format: [timestamp] level tag: message
        Text(
            buildAnnotatedString {
                // Timestamp
                withStyle(SpanStyle(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    fontFamily = FontFamily.Monospace
                )) {
                    append("[${formatTimeOnly(entry.timestamp)}] ")
                }

                // Level
                withStyle(SpanStyle(
                    color = levelColor,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace
                )) {
                    append("$levelPrefix ")
                }

                // Tag
                withStyle(SpanStyle(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontFamily = FontFamily.Monospace
                )) {
                    append("${entry.tag}: ")
                }

                // Message
                withStyle(SpanStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace
                )) {
                    append(entry.message)
                }
            },
            fontSize = 12.sp,
            lineHeight = 18.sp
        )

        // Stack trace if exists - indented
        entry.stackTrace?.let { trace ->
            Text(
                "  │ $trace",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                lineHeight = 16.sp,
                modifier = Modifier.padding(start = Standards.SpacingXs, top = Standards.SpacingXxs),
                maxLines = 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun EmptyTerminal(isPaused: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
        ) {
            Text(
                if (isPaused) "│ paused" else "│ waiting",
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    }
}

// Clean color palette for terminal
@Composable
fun getCleanLevelColor(level: LogLevel): Color {
    return when (level) {
        LogLevel.DEBUG -> MaterialTheme.colorScheme.tertiary
        LogLevel.INFO -> MaterialTheme.colorScheme.primary
        LogLevel.WARNING -> Color(0xFFFFA726)
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
        LogLevel.CRITICAL -> Color(0xFFD32F2F)
    }
}

fun getCleanLevelPrefix(level: LogLevel): String {
    return when (level) {
        LogLevel.DEBUG -> "dbg"
        LogLevel.INFO -> "inf"
        LogLevel.WARNING -> "wrn"
        LogLevel.ERROR -> "err"
        LogLevel.CRITICAL -> "crt"
    }
}

@Composable
fun getLevelColor(level: LogLevel): Color {
    return getCleanLevelColor(level)
}

