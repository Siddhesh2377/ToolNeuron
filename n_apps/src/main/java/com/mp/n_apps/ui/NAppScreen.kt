package com.mp.n_apps.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mp.n_apps.renderer.NAppRenderer

private enum class IDEMode { CANVAS, CODE, SPLIT }
private enum class CodeTab { STATE, UI, ACTIONS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NAppScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAgents: () -> Unit = {},
    viewModel: NAppViewModel
) {
    val context = LocalContext.current
    val napp by viewModel.napp.collectAsState()
    val errors by viewModel.errors.collectAsState()
    val stateJson by viewModel.stateJson.collectAsState()
    val uiJson by viewModel.uiJson.collectAsState()
    val actionsJson by viewModel.actionsJson.collectAsState()
    val isAgentWorking by viewModel.isAgentWorking.collectAsState()
    val agentMessages by viewModel.agentMessages.collectAsState()
    val activeAgent by viewModel.activeAgent.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()

    // Workspace state
    val projects by viewModel.projects.collectAsState()
    val currentProjectId by viewModel.currentProjectId.collectAsState()
    val hasUnsavedChanges by viewModel.hasUnsavedChanges.collectAsState()
    val versionHistory by viewModel.versionHistory.collectAsState()
    val toolLog by viewModel.toolLog.collectAsState()
    val agentProgress by viewModel.agentProgress.collectAsState()

    var ideMode by remember { mutableStateOf(IDEMode.CANVAS) }
    val hasApiKey = activeAgent?.apiKey?.isNotBlank() == true
    var showAgentChat by remember { mutableStateOf(false) }
    var showToolActivity by remember { mutableStateOf(false) }
    var showProjectPicker by remember { mutableStateOf(false) }
    var showVersionHistory by remember { mutableStateOf(false) }
    var agentInput by remember { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }

    // Init workspace and API key
    LaunchedEffect(Unit) {
        viewModel.initWorkspace(context)
        viewModel.initConfig(context)
    }

    // Toast -> Snackbar
    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeToast()
        }
    }

    // Auto-show tool activity when agent is working
    LaunchedEffect(isAgentWorking) {
        if (isAgentWorking) showToolActivity = true
    }

    // Derive current project name
    val currentProjectName = projects.find { it.id == currentProjectId }?.name

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            IDETopBar(
                projectName = currentProjectName,
                appName = napp?.manifest?.app?.name ?: "NApp Builder",
                ideMode = ideMode,
                onModeChange = { ideMode = it },
                onSettings = onNavigateToAgents,
                onBack = onNavigateBack,
                showAgentChat = showAgentChat,
                onToggleChat = { showAgentChat = !showAgentChat },
                hasMessages = agentMessages.isNotEmpty(),
                onProjectPicker = { showProjectPicker = true },
                onSave = { viewModel.saveProject() },
                onHistory = { showVersionHistory = true },
                hasUnsavedChanges = hasUnsavedChanges,
                hasProject = currentProjectId != null,
                onToolActivity = { showToolActivity = !showToolActivity },
                hasToolLog = toolLog.isNotEmpty()
            )
        },
        bottomBar = {
            AgentActionBar(
                agentInput = agentInput,
                onInputChange = { agentInput = it },
                onSend = {
                    viewModel.sendAgentCommand(agentInput)
                    agentInput = ""
                },
                isAgentWorking = isAgentWorking,
                hasApiKey = hasApiKey,
                onSetApiKey = onNavigateToAgents
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                when (ideMode) {
                    IDEMode.CANVAS -> CanvasPreview(viewModel)
                    IDEMode.CODE -> ThreeFileEditor(
                        stateJson = stateJson,
                        uiJson = uiJson,
                        actionsJson = actionsJson,
                        onStateJsonChange = viewModel::updateStateJson,
                        onUiJsonChange = viewModel::updateUiJson,
                        onActionsJsonChange = viewModel::updateActionsJson,
                        modifier = Modifier.fillMaxSize()
                    )
                    IDEMode.SPLIT -> SplitView(
                        viewModel = viewModel,
                        stateJson = stateJson,
                        uiJson = uiJson,
                        actionsJson = actionsJson
                    )
                }

                // Agent chat overlay
                if (showAgentChat && agentMessages.isNotEmpty()) {
                    AgentChatPanel(
                        messages = agentMessages,
                        onDismiss = { showAgentChat = false },
                        onClear = { viewModel.clearAgentHistory() },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }

                // Tool activity overlay
                if (showToolActivity && (toolLog.isNotEmpty() || isAgentWorking)) {
                    ToolActivityPanel(
                        toolLog = toolLog,
                        isAgentWorking = isAgentWorking,
                        agentProgress = agentProgress,
                        onDismiss = { showToolActivity = false },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }

            if (errors.isNotEmpty()) {
                ErrorStrip(errors = errors)
            }
        }
    }

    // Bottom sheets
    if (showProjectPicker) {
        ProjectPickerSheet(
            projects = projects,
            currentProjectId = currentProjectId,
            onProjectSelect = { viewModel.openProject(it) },
            onProjectCreate = { viewModel.createProject(it) },
            onProjectDelete = { viewModel.deleteProject(it) },
            onDismiss = { showProjectPicker = false }
        )
    }

    if (showVersionHistory) {
        VersionHistorySheet(
            versions = versionHistory,
            onCommit = { viewModel.commitVersion(it) },
            onRevert = { viewModel.revertToVersion(it) },
            onDismiss = { showVersionHistory = false }
        )
    }
}

// ════════════════════════════════════════
//  IDETopBar — updated with project, save, commit, history
// ════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IDETopBar(
    projectName: String?,
    appName: String,
    ideMode: IDEMode,
    onModeChange: (IDEMode) -> Unit,
    onSettings: () -> Unit,
    onBack: () -> Unit,
    showAgentChat: Boolean,
    onToggleChat: () -> Unit,
    hasMessages: Boolean,
    onProjectPicker: () -> Unit,
    onSave: () -> Unit,
    onHistory: () -> Unit,
    hasUnsavedChanges: Boolean,
    hasProject: Boolean,
    onToolActivity: () -> Unit,
    hasToolLog: Boolean
) {
    Column {
        CenterAlignedTopAppBar(
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable(onClick = onProjectPicker)
                ) {
                    Text(
                        text = projectName ?: appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (hasUnsavedChanges) {
                        Surface(
                            modifier = Modifier.size(6.dp),
                            color = MaterialTheme.colorScheme.error,
                            shape = RoundedCornerShape(3.dp)
                        ) {}
                    }
                }
            },
            navigationIcon = {
                NAppIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    onClick = onBack
                )
            },
            actions = {
                if (hasProject) {
                    NAppIconButton(
                        icon = Icons.Default.Save,
                        onClick = onSave
                    )
                    NAppIconButton(
                        icon = Icons.Default.History,
                        onClick = onHistory
                    )
                }
                NAppIconButton(
                    icon = Icons.Default.FolderOpen,
                    onClick = onProjectPicker
                )
                NAppIconButton(
                    icon = if (showAgentChat) Icons.Default.Close else Icons.AutoMirrored.Filled.Chat,
                    onClick = onToggleChat
                )
                NAppIconButton(
                    icon = Icons.Default.Settings,
                    onClick = onSettings
                )
            }
        )

        // Mode toggle bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                NAppToggleGroup(
                    items = IDEMode.entries,
                    selectedItem = ideMode,
                    onItemSelected = onModeChange,
                    itemLabel = { mode ->
                        when (mode) {
                            IDEMode.CANVAS -> "Canvas"
                            IDEMode.CODE -> "Code"
                            IDEMode.SPLIT -> "Split"
                        }
                    }
                )
            }

            if (hasToolLog) {
                Surface(
                    modifier = Modifier
                        .height(20.dp)
                        .clickable(onClick = onToolActivity),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = null,
                                modifier = Modifier.size(10.dp),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                text = "Tools",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }

            if (hasMessages) {
                Surface(
                    modifier = Modifier.height(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = "AI",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    }
}

// ════════════════════════════════════════
//  NAppIconButton — 30dp, primary.copy(0.06f), 6dp corners
// ════════════════════════════════════════

@Composable
private fun NAppIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .size(30.dp)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(30.dp)
                    .padding(6.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ════════════════════════════════════════
//  NAppToggleGroup — spring-animated slider, 30dp, 6dp corners
// ════════════════════════════════════════

@Composable
private fun <T> NAppToggleGroup(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    itemLabel: (T) -> String,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    val selectedIndex = items.indexOf(selectedItem).coerceAtLeast(0)
    val density = LocalDensity.current
    val containerWidth = remember { mutableIntStateOf(0) }
    val itemWidth = if (containerWidth.intValue > 0 && items.isNotEmpty()) {
        with(density) { (containerWidth.intValue / items.size).toDp() }
    } else {
        0.dp
    }

    val indicatorOffset by animateDpAsState(
        targetValue = itemWidth * selectedIndex,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 350f),
        label = "toggleOffset"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(30.dp)
            .onSizeChanged { containerWidth.intValue = it.width },
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Box {
            if (itemWidth > 0.dp) {
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset + 2.dp)
                        .padding(vertical = 2.dp)
                        .width(itemWidth - 4.dp)
                        .height(26.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(4.dp)
                        )
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    val isSelected = index == selectedIndex
                    val contentColor by animateColorAsState(
                        targetValue = when {
                            isSelected -> MaterialTheme.colorScheme.onPrimary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "toggleColor$index"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(30.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onItemSelected(item) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = itemLabel(item),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════
//  AgentActionBar (Bottom)
// ════════════════════════════════════════

@Composable
private fun AgentActionBar(
    agentInput: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    isAgentWorking: Boolean,
    hasApiKey: Boolean,
    onSetApiKey: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary.copy(0.04f)
            .compositeOver(MaterialTheme.colorScheme.background),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            if (!hasApiKey) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSetApiKey() },
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Configure AI Settings",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Set API key, model, and endpoint",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextField(
                        value = agentInput,
                        onValueChange = onInputChange,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(max = 120.dp),
                        placeholder = {
                            Text(
                                text = "Describe your app...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        maxLines = 4,
                        enabled = !isAgentWorking
                    )

                    NAppIconButton(
                        icon = Icons.AutoMirrored.Filled.Send,
                        onClick = {
                            if (agentInput.isNotBlank()) onSend()
                        }
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════
//  CanvasPreview
// ════════════════════════════════════════

@Composable
private fun CanvasPreview(viewModel: NAppViewModel) {
    val napp by viewModel.napp.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        val currentNApp = napp
        if (currentNApp != null) {
            NAppRenderer(
                ui = currentNApp.ui,
                state = viewModel.runtime.stateStore.getSnapshot(),
                resolver = viewModel.runtime.resolver,
                onStateChange = viewModel::onStateChange,
                onAction = viewModel::onAction
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Describe an app to get started",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Text(
                    text = "Use the input below to tell the AI what to build",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        }
    }
}

// ════════════════════════════════════════
//  ThreeFileEditor (3 tabs: state / ui / actions)
// ════════════════════════════════════════

@Composable
private fun ThreeFileEditor(
    stateJson: String,
    uiJson: String,
    actionsJson: String,
    onStateJsonChange: (String) -> Unit,
    onUiJsonChange: (String) -> Unit,
    onActionsJsonChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf(CodeTab.UI) }

    Column(modifier = modifier) {
        // Tab bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CodeTab.entries.forEach { tab ->
                val isSelected = tab == activeTab
                val bg by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                    else Color.Transparent,
                    animationSpec = tween(200),
                    label = "tabBg"
                )
                val fg by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(200),
                    label = "tabFg"
                )

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(26.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { activeTab = tab },
                    color = bg,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = when (tab) {
                                CodeTab.STATE -> "state.json"
                                CodeTab.UI -> "ui.json"
                                CodeTab.ACTIONS -> "actions.json"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            fontFamily = FontFamily.Monospace,
                            color = fg
                        )
                    }
                }
            }
        }

        // Editor pane
        when (activeTab) {
            CodeTab.STATE -> JsonEditorPane(
                json = stateJson,
                onJsonChange = onStateJsonChange,
                placeholder = "{ \"schema\": { } }",
                modifier = Modifier.fillMaxSize()
            )
            CodeTab.UI -> JsonEditorPane(
                json = uiJson,
                onJsonChange = onUiJsonChange,
                placeholder = "{ \"components\": [ ] }",
                modifier = Modifier.fillMaxSize()
            )
            CodeTab.ACTIONS -> JsonEditorPane(
                json = actionsJson,
                onJsonChange = onActionsJsonChange,
                placeholder = "{ \"actions\": { } }",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// ════════════════════════════════════════
//  JsonEditorPane (line-numbered, monospace)
// ════════════════════════════════════════

@Composable
private fun JsonEditorPane(
    json: String,
    onJsonChange: (String) -> Unit,
    placeholder: String = "Paste or type JSON here...",
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    val lines = json.lines()

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Line numbers gutter
            Column(
                modifier = Modifier
                    .verticalScroll(scrollState)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.End
            ) {
                lines.forEachIndexed { index, _ ->
                    Text(
                        text = "${index + 1}",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }

            // Editor area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .horizontalScroll(horizontalScrollState)
                    .padding(12.dp)
            ) {
                BasicTextField(
                    value = json,
                    onValueChange = onJsonChange,
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        if (json.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                        innerTextField()
                    }
                )
            }
        }
    }
}

// ════════════════════════════════════════
//  SplitView (top: 3-tab code, bottom: preview)
// ════════════════════════════════════════

@Composable
private fun SplitView(
    viewModel: NAppViewModel,
    stateJson: String,
    uiJson: String,
    actionsJson: String
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ThreeFileEditor(
            stateJson = stateJson,
            uiJson = uiJson,
            actionsJson = actionsJson,
            onStateJsonChange = viewModel::updateStateJson,
            onUiJsonChange = viewModel::updateUiJson,
            onActionsJsonChange = viewModel::updateActionsJson,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        // Divider with Run button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "PREVIEW",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                letterSpacing = 1.sp
            )
            TextButton(onClick = { viewModel.reparse() }) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Run",
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Run", style = MaterialTheme.typography.labelSmall)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            CanvasPreview(viewModel = viewModel)
        }
    }
}

// ════════════════════════════════════════
//  AgentChatPanel (overlay)
// ════════════════════════════════════════

@Composable
private fun AgentChatPanel(
    messages: List<AgentChatMessage>,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 400.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        tonalElevation = 4.dp,
        shadowElevation = 8.dp
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Agent Chat",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    NAppIconButton(icon = Icons.Default.DeleteSweep, onClick = onClear)
                    NAppIconButton(icon = Icons.Default.Close, onClick = onDismiss)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // Messages
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { message ->
                    ChatMessageBubble(message)
                }
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(message: AgentChatMessage) {
    val isUser = message.role == "user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 280.dp),
            color = if (isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            },
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                if (message.isJsonUpdate) {
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        modifier = Modifier.height(20.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = "Applied to canvas",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════
//  ErrorStrip (compact, expandable)
// ════════════════════════════════════════

@Composable
private fun ErrorStrip(errors: List<String>) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .animateContentSize(),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "${errors.size} error${if (errors.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (expanded) "Collapse" else "Tap to expand",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.5f)
                )
            }

            if (expanded) {
                Spacer(Modifier.height(4.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 120.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    errors.forEach { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

