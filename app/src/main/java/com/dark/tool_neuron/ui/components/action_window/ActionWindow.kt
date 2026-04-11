package com.dark.tool_neuron.ui.components.action_window

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.dark.tool_neuron.service.inference.InferenceClient
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.ActionTextButton
import com.dark.tool_neuron.ui.components.CaptionText
import com.dark.tool_neuron.ui.components.SectionHeader
import com.dark.tool_neuron.ui.components.StatusBadge
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.ui.theme.Motion

@Composable
fun ActionWindowPill(
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    val isModelLoaded by InferenceClient.isModelLoaded.collectAsStateWithLifecycle()

    val modelInfo = if (isModelLoaded) InferenceClient.getModelInfo() else null
    val displayName = modelInfo?.let { parseModelName(it) } ?: "No model"

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
            Icon(
                imageVector = TnIcons.Leaf,
                contentDescription = null,
                modifier = Modifier.size(dimens.iconSm),
                tint = if (isModelLoaded) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                text = displayName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
    onStoreClick: () -> Unit = {},
    onGuideClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onLoadDocument: () -> Unit = {},
    documents: List<com.dark.tool_neuron.model.ChatDocument> = emptyList(),
    onRemoveDocument: (String) -> Unit = {},
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
                    onStoreClick = onStoreClick,
                    onGuideClick = onGuideClick,
                    onSettingsClick = onSettingsClick,
                    onLoadDocument = onLoadDocument,
                    documents = documents,
                    onRemoveDocument = onRemoveDocument,
                )
            }
        }
    }
}

@Composable
private fun ActionWindowContent(
    onStoreClick: () -> Unit,
    onGuideClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onLoadDocument: () -> Unit,
    documents: List<com.dark.tool_neuron.model.ChatDocument>,
    onRemoveDocument: (String) -> Unit,
) {
    val dimens = LocalDimens.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Models", "Stats", "Tools")

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
                0 -> ModelsTab()
                1 -> StatsTab()
                2 -> ToolsTab(
                    onStoreClick = onStoreClick,
                    onGuideClick = onGuideClick,
                    onSettingsClick = onSettingsClick,
                    onLoadDocument = onLoadDocument,
                    documents = documents,
                    onRemoveDocument = onRemoveDocument,
                )
            }
        }
    }
}

@Composable
private fun ModelsTab() {
    val dimens = LocalDimens.current
    val scope = rememberCoroutineScope()
    val isLlmLoaded by InferenceClient.isModelLoaded.collectAsStateWithLifecycle()
    val isTtsLoaded by InferenceClient.isTtsLoaded.collectAsStateWithLifecycle()
    val isSttLoaded by InferenceClient.isSttLoaded.collectAsStateWithLifecycle()

    val llmName = if (isLlmLoaded) (InferenceClient.getModelInfo()?.let { parseModelName(it) } ?: "LLM") else null
    val ttsName = if (isTtsLoaded) "TTS Model" else null
    val sttName = if (isSttLoaded) "STT Model" else null

    Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingSm)) {
        ModelSlotRow(
            icon = TnIcons.MessageCircle,
            label = "Text (LLM)",
            modelName = llmName,
            isLoaded = isLlmLoaded,
            onUnload = { scope.launch { InferenceClient.unloadModel() } }
        )
        ModelSlotRow(
            icon = TnIcons.Volume,
            label = "TTS",
            modelName = ttsName,
            isLoaded = isTtsLoaded,
            onUnload = { scope.launch { InferenceClient.unloadTtsModel() } }
        )
        ModelSlotRow(
            icon = TnIcons.Mic,
            label = "STT",
            modelName = sttName,
            isLoaded = isSttLoaded,
            onUnload = { scope.launch { InferenceClient.unloadSttModel() } }
        )
    }
}

@Composable
private fun ModelSlotRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modelName: String?,
    isLoaded: Boolean,
    onUnload: () -> Unit,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current

    Surface(
        shape = tnShapes.cardSmall,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.cardPadding, vertical = dimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(dimens.iconMd),
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = modelName ?: "None loaded",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = if (isLoaded) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            StatusBadge(
                text = if (isLoaded) "Active" else "Inactive",
                isActive = isLoaded
            )
            if (isLoaded) {
                ActionButton(
                    onClickListener = onUnload,
                    icon = TnIcons.X,
                    contentDescription = "Unload"
                )
            }
        }
    }
}

@Composable
private fun StatsTab() {
    val dimens = LocalDimens.current
    val isModelLoaded by InferenceClient.isModelLoaded.collectAsStateWithLifecycle()

    val contextUsage = if (isModelLoaded) InferenceClient.getContextUsage() else 0f
    val contextPercent = "${(contextUsage * 100).toInt()}%"

    Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingMd)) {
        SectionHeader(title = "Context")
        StatRow("Usage", if (isModelLoaded) contextPercent else "--")
        if (isModelLoaded) {
            LinearProgressIndicator(
                progress = { contextUsage.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            )
        }

        SectionHeader(title = "Performance")
        StatRow("Generation speed", if (isModelLoaded) "-- tok/s" else "--")
        StatRow("Backend", if (isModelLoaded) "CPU" else "--")

        SectionHeader(title = "Memory")
        StatRow("Model size", if (isModelLoaded) "--" else "No model")
        StatRow("Context size", if (isModelLoaded) "--" else "--")
    }
}

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
private fun ToolsTab(
    onStoreClick: () -> Unit,
    onGuideClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onLoadDocument: () -> Unit,
    documents: List<com.dark.tool_neuron.model.ChatDocument>,
    onRemoveDocument: (String) -> Unit,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current

    Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingSm)) {
        SectionHeader(title = "Actions")
        ActionTextButton(
            onClickListener = onLoadDocument,
            icon = TnIcons.BookOpen,
            text = if (documents.isNotEmpty()) "Load Document (${documents.size})" else "Load Document",
            modifier = Modifier.fillMaxWidth()
        )

        AnimatedVisibility(
            visible = documents.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingXxs)) {
                documents.forEach { doc ->
                    Surface(
                        shape = tnShapes.cardSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = dimens.spacingSm, vertical = dimens.spacingXxs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = TnIcons.BookOpen,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(dimens.spacingXs))
                            Text(
                                text = doc.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            CaptionText(text = formatFileSize(doc.sizeBytes))
                            Spacer(Modifier.width(dimens.spacingXs))
                            Icon(
                                imageVector = TnIcons.X,
                                contentDescription = "Remove",
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                        onClick = { onRemoveDocument(doc.id) }
                                    ),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        ActionTextButton(
            onClickListener = {},
            icon = TnIcons.Edit,
            text = "System Prompt",
            modifier = Modifier.fillMaxWidth()
        )
        ActionTextButton(
            onClickListener = {},
            icon = TnIcons.Code,
            text = "Chat Template",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(dimens.spacingXs))
        SectionHeader(title = "Quick Links")
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm)) {
            ActionTextButton(
                onClickListener = onStoreClick,
                icon = TnIcons.Download,
                text = "Store"
            )
            ActionTextButton(
                onClickListener = onGuideClick,
                icon = TnIcons.Compass,
                text = "Guide"
            )
            ActionTextButton(
                onClickListener = onSettingsClick,
                icon = TnIcons.Settings,
                text = "Settings"
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.0f KB".format(kb)
    val mb = kb / 1024.0
    return "%.1f MB".format(mb)
}

private fun parseModelName(modelInfoJson: String): String {
    return try {
        val nameRegex = """"(?:model_?name|name)"\s*:\s*"([^"]+)"""".toRegex()
        nameRegex.find(modelInfoJson)?.groupValues?.get(1) ?: "Model"
    } catch (_: Exception) {
        "Model"
    }
}
