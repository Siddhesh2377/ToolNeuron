package com.dark.tool_neuron.ui.components.action_window

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.download_manager.formatBytes
import com.dark.tool_neuron.model.ChatDocument
import com.dark.tool_neuron.model.MemoryMetrics
import com.dark.tool_neuron.model.ModelInfo
import com.dark.tool_neuron.model.TextMetrics
import com.dark.tool_neuron.service.inference.InferenceClient
import com.dark.tool_neuron.ui.components.SectionHeader
import com.dark.tool_neuron.ui.components.TnProgressBar
import com.dark.tool_neuron.ui.components.model_list.InstalledModelList
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.ui.theme.Motion
import com.dark.tool_neuron.viewmodel.home_vm.ModelLoadState
import com.dark.tool_neuron.viewmodel.home_vm.PillState

@Composable
fun ActionWindowPill(
    state: PillState,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current

    val accent = pillAccentColor(state)

    Surface(
        shape = tnShapes.full,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        modifier = Modifier.clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
            onClick = onToggle
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = dimens.spacingMd, vertical = dimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs)
        ) {
            AnimatedContent(
                targetState = state,
                transitionSpec = {
                    (fadeIn(Motion.state()) togetherWith fadeOut(Motion.state()))
                },
                label = "pillLeading",
            ) { target ->
                Box(
                    modifier = Modifier.size(dimens.iconSm),
                    contentAlignment = Alignment.Center,
                ) {
                    if (target == PillState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(dimens.iconSm),
                            color = accent,
                            trackColor = accent.copy(alpha = 0.2f),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = pillIcon(target),
                            contentDescription = null,
                            modifier = Modifier.size(dimens.iconSm),
                            tint = accent,
                        )
                    }
                }
            }

            AnimatedContent(
                targetState = state,
                transitionSpec = {
                    fadeIn(Motion.state()) togetherWith fadeOut(Motion.state())
                },
                label = "pillLabel",
            ) { target ->
                Text(
                    text = target.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = if (expanded) TnIcons.ChevronUp else TnIcons.ChevronDown,
                contentDescription = null,
                modifier = Modifier.size(dimens.iconSm),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ActionWindowOverlay(
    expanded: Boolean,
    onDismiss: () -> Unit,
    installedModels: List<ModelInfo>,
    modelLoadState: ModelLoadState,
    activeModel: ModelInfo?,
    contextUsage: Float,
    lastTextMetrics: TextMetrics?,
    lastMemoryMetrics: MemoryMetrics?,
    onLoadModel: (ModelInfo) -> Unit,
    onUnloadModel: (ModelInfo) -> Unit,
    onStoreClick: () -> Unit = {},
    onAddAttachment: () -> Unit = {},
    documents: List<ChatDocument> = emptyList(),
    onRemoveDocument: (String) -> Unit = {},
    deepIndexing: Set<String> = emptySet(),
    onDeepIndex: (String) -> Unit = {},
    raptorBuilding: Set<String> = emptySet(),
    onBuildRaptor: (String) -> Unit = {},
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current

    if (expanded) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss
                )
        )
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(Motion.state()) + expandVertically(
                animationSpec = Motion.content(),
                expandFrom = Alignment.Top
            ),
            exit = fadeOut(Motion.state()) + shrinkVertically(
                animationSpec = Motion.content(),
                shrinkTowards = Alignment.Top
            )
        ) {
            Surface(
                shape = tnShapes.xl,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = dimens.cardElevation,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.screenPadding)
                    .padding(top = dimens.spacingXs)
            ) {
                ActionWindowContent(
                    installedModels = installedModels,
                    modelLoadState = modelLoadState,
                    activeModel = activeModel,
                    contextUsage = contextUsage,
                    lastTextMetrics = lastTextMetrics,
                    lastMemoryMetrics = lastMemoryMetrics,
                    onLoadModel = onLoadModel,
                    onUnloadModel = onUnloadModel,
                    onStoreClick = onStoreClick,
                    onAddAttachment = onAddAttachment,
                    documents = documents,
                    onRemoveDocument = onRemoveDocument,
                    deepIndexing = deepIndexing,
                    onDeepIndex = onDeepIndex,
                    raptorBuilding = raptorBuilding,
                    onBuildRaptor = onBuildRaptor,
                )
            }
        }
    }
}

@Composable
private fun ActionWindowContent(
    installedModels: List<ModelInfo>,
    modelLoadState: ModelLoadState,
    activeModel: ModelInfo?,
    contextUsage: Float,
    lastTextMetrics: TextMetrics?,
    lastMemoryMetrics: MemoryMetrics?,
    onLoadModel: (ModelInfo) -> Unit,
    onUnloadModel: (ModelInfo) -> Unit,
    onStoreClick: () -> Unit,
    onAddAttachment: () -> Unit,
    documents: List<ChatDocument>,
    onRemoveDocument: (String) -> Unit,
    deepIndexing: Set<String>,
    onDeepIndex: (String) -> Unit,
    raptorBuilding: Set<String>,
    onBuildRaptor: (String) -> Unit,
) {
    val dimens = LocalDimens.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Models", "Stats", "Attach")

    Column(modifier = Modifier.fillMaxWidth()) {
        SecondaryTabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.spacingLg)
        ) {
            when (selectedTab) {
                0 -> ModelsTab(
                    installedModels = installedModels,
                    modelLoadState = modelLoadState,
                    onLoadModel = onLoadModel,
                    onUnloadModel = onUnloadModel,
                    onBrowseStore = onStoreClick,
                )
                1 -> StatsTab(
                    activeModel = activeModel,
                    contextUsage = contextUsage,
                    textMetrics = lastTextMetrics,
                    memoryMetrics = lastMemoryMetrics,
                )
                2 -> AttachmentsTab(
                    documents = documents,
                    onAddAttachment = onAddAttachment,
                    onRemoveAttachment = onRemoveDocument,
                    deepIndexing = deepIndexing,
                    onDeepIndex = onDeepIndex,
                    raptorBuilding = raptorBuilding,
                    onBuildRaptor = onBuildRaptor,
                )
            }
        }
    }
}

@Composable
private fun ModelsTab(
    installedModels: List<ModelInfo>,
    modelLoadState: ModelLoadState,
    onLoadModel: (ModelInfo) -> Unit,
    onUnloadModel: (ModelInfo) -> Unit,
    onBrowseStore: () -> Unit,
) {
    InstalledModelList(
        models = installedModels,
        loadState = modelLoadState,
        onLoad = onLoadModel,
        onUnload = onUnloadModel,
        onBrowseStore = onBrowseStore,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp, max = 320.dp),
    )
}

@Composable
private fun StatsTab(
    activeModel: ModelInfo?,
    contextUsage: Float,
    textMetrics: TextMetrics?,
    memoryMetrics: MemoryMetrics?,
) {
    val dimens = LocalDimens.current
    val isModelLoaded by InferenceClient.isModelLoaded.collectAsStateWithLifecycle()

    val contextPercent = "${(contextUsage * 100).toInt()}%"

    Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingMd)) {
        SectionHeader(title = "Model")
        StatRow("Name", activeModel?.name ?: "None loaded")
        if (activeModel != null && activeModel.fileSize > 0) {
            StatRow("File size", formatBytes(activeModel.fileSize))
        }

        SectionHeader(title = "Context")
        StatRow("Usage", if (isModelLoaded) contextPercent else "--")
        if (isModelLoaded) {
            TnProgressBar(
                progress = contextUsage.coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth(),
                fillColor = if (contextUsage > 0.85f) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurface,
            )
        }

        SectionHeader(title = "Performance")
        if (textMetrics != null) {
            StatRow("Generation speed", "%.2f tok/s".format(textMetrics.tokensPerSecond))
            StatRow("Prompt tokens", textMetrics.promptTokens.toString())
            StatRow("Generated tokens", textMetrics.generatedTokens.toString())
            if (textMetrics.timeToFirstTokenMs > 0) {
                StatRow("Time to first token", "${textMetrics.timeToFirstTokenMs} ms")
            }
            if (textMetrics.totalTimeMs > 0) {
                StatRow("Total time", formatDuration(textMetrics.totalTimeMs))
            }
        } else {
            StatRow("Generation speed", if (isModelLoaded) "No data yet" else "--")
        }
        StatRow("Backend", if (isModelLoaded) "CPU" else "--")

        SectionHeader(title = "Memory (resident)")
        if (memoryMetrics != null) {
            if (memoryMetrics.modelSizeMB > 0) {
                StatRow("Model on disk", "%.0f MB".format(memoryMetrics.modelSizeMB))
            }
            if (memoryMetrics.contextSizeMB > 0) {
                StatRow("KV cache", "%.0f MB".format(memoryMetrics.contextSizeMB))
            }
            if (memoryMetrics.currentMemoryMB > 0) {
                StatRow("Process now (RSS)", "%.0f MB".format(memoryMetrics.currentMemoryMB))
            }
            if (memoryMetrics.peakMemoryMB > 0) {
                StatRow("Process peak (HWM)", "%.0f MB".format(memoryMetrics.peakMemoryMB))
            }
            if (memoryMetrics.usagePercent > 0) {
                StatRow("Peak vs device RAM", "%.1f%%".format(memoryMetrics.usagePercent))
            }
        } else {
            StatRow("Model on disk", if (isModelLoaded) "No data yet" else "--")
            StatRow("KV cache", if (isModelLoaded) "No data yet" else "--")
        }
    }
}

private fun formatDuration(ms: Long): String =
    if (ms < 1000) "$ms ms" else "%.2f s".format(ms / 1000.0)

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun pillAccentColor(state: PillState): Color = when (state) {
    PillState.Loaded, PillState.Generating, PillState.Thinking, PillState.ToolCalling,
    PillState.Loading, PillState.Image, PillState.Rag -> MaterialTheme.colorScheme.primary
    PillState.Error -> MaterialTheme.colorScheme.error
    PillState.Idle -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
}

private fun pillIcon(state: PillState): ImageVector = when (state) {
    PillState.Idle -> TnIcons.Leaf
    PillState.Loaded -> TnIcons.Check
    PillState.Generating -> TnIcons.Sparkles
    PillState.Thinking -> TnIcons.Sparkles
    PillState.ToolCalling -> TnIcons.Wrench
    PillState.Image -> TnIcons.Photo
    PillState.Rag -> TnIcons.BookOpen
    PillState.Error -> TnIcons.AlertTriangle
    PillState.Loading -> TnIcons.Leaf
}

