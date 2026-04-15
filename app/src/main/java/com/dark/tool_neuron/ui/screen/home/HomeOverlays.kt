package com.dark.tool_neuron.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.global.Standards
import com.dark.tool_neuron.models.enums.ProviderType
import com.dark.tool_neuron.models.plugins.PluginInfo
import com.dark.tool_neuron.ui.components.ActionSwitch
import com.dark.tool_neuron.ui.components.ActionTextButton
import com.dark.tool_neuron.ui.icons.TnIcons

// ── MoreOptionsOverlay ──────────────────────────────────────────────────────────

@Composable
internal fun MoreOptionsOverlay(
    show: Boolean,
    // RAG
    loadedRagCount: Int,
    isRagEnabled: Boolean,
    onRagToggle: (Boolean) -> Unit,
    onRagManage: () -> Unit,
    // Plugin
    nonWebSearchPlugins: List<PluginInfo>,
    enabledPluginNames: Set<String>,
    isToolCallingModelLoaded: Boolean,
    toolCallingEnabled: Boolean,
    onPluginToggle: (String, Boolean) -> Unit,
    onManagePlugins: () -> Unit
) {
    AnimatedVisibility(visible = show) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Standards.SpacingSm)
                .padding(bottom = Standards.SpacingSm)
                .background(
                    MaterialTheme.colorScheme.primary.copy(0.04f)
                        .compositeOver(MaterialTheme.colorScheme.background),
                    shape = RoundedCornerShape(10.dp)
                )
                .padding(Standards.SpacingMd),
            verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
        ) {
            // RAG section
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(Standards.CardSmallCornerRadius)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Standards.CardPadding, vertical = Standards.SpacingSm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
                ) {
                    Icon(
                        imageVector = TnIcons.Database,
                        contentDescription = null,
                        modifier = Modifier.size(Standards.IconMd),
                        tint = if (loadedRagCount > 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "RAG",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (loadedRagCount > 0) "$loadedRagCount loaded" else "None loaded",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    ActionTextButton(
                        onClickListener = onRagManage,
                        icon = TnIcons.Database,
                        text = "Manage"
                    )
                    ActionSwitch(
                        checked = isRagEnabled,
                        onCheckedChange = onRagToggle,
                        enabled = loadedRagCount > 0
                    )
                }
            }

            // Plugin section (only if tool calling enabled)
            if (toolCallingEnabled && nonWebSearchPlugins.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(Standards.CardSmallCornerRadius)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Standards.CardPadding),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Plugins",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        nonWebSearchPlugins.forEach { plugin ->
                            val isEnabled = enabledPluginNames.contains(plugin.name)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Standards.SpacingXxs),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = plugin.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isToolCallingModelLoaded) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    )
                                    Text(
                                        text = "${plugin.toolDefinitionBuilder.size} tools",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                                ActionSwitch(
                                    checked = isEnabled,
                                    onCheckedChange = { onPluginToggle(plugin.name, !isEnabled) },
                                    enabled = isToolCallingModelLoaded
                                )
                            }
                        }

                        ActionTextButton(
                            onClickListener = onManagePlugins,
                            icon = TnIcons.Wrench,
                            text = "Configure"
                        )
                    }
                }
            }
        }
    }
}

// ── QuickLookChipRow ────────────────────────────────────────────────────────────

@Composable
internal fun QuickLookChipRow(
    loadedRagCount: Int,
    isMemoryEnabled: Boolean,
    isWebSearchEnabled: Boolean = false,
    isRagEnabled: Boolean = true,
    activePluginName: String? = null,
    onRagChipClick: () -> Unit,
    onToolChipClick: () -> Unit,
    onMemoryChipClick: () -> Unit,
    onWebSearchChipClick: () -> Unit = {}
) {
    val hasAnyActive = (loadedRagCount > 0 && isRagEnabled) || isMemoryEnabled || isWebSearchEnabled || activePluginName != null

    AnimatedVisibility(visible = hasAnyActive) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Standards.SpacingXs, vertical = Standards.SpacingXxs),
            horizontalArrangement = Arrangement.spacedBy(Standards.ChipSpacing)
        ) {
            if (loadedRagCount > 0 && isRagEnabled) {
                StatusChip(
                    label = "$loadedRagCount RAG",
                    color = MaterialTheme.colorScheme.primary,
                    onClick = onRagChipClick
                )
            }
            if (isWebSearchEnabled) {
                StatusChip(
                    label = "Web Search",
                    color = MaterialTheme.colorScheme.tertiary,
                    onClick = onWebSearchChipClick
                )
            }
            if (activePluginName != null) {
                StatusChip(
                    label = activePluginName,
                    color = MaterialTheme.colorScheme.tertiary,
                    onClick = onToolChipClick
                )
            }
            if (isMemoryEnabled) {
                StatusChip(
                    label = "Memory",
                    color = MaterialTheme.colorScheme.secondary,
                    onClick = onMemoryChipClick
                )
            }
        }
    }
}

// ── StatusChip ──────────────────────────────────────────────────────────────────

@Composable
internal fun StatusChip(
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(Standards.ChipCornerRadius),
        modifier = Modifier.height(Standards.ChipHeight)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Standards.ChipHorizontalPadding)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(color, RoundedCornerShape(50))
            )
            Spacer(Modifier.width(Standards.SpacingXs))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ── ReloadModelDialog ───────────────────────────────────────────────────────────

@Composable
internal fun ReloadModelDialog(
    modelName: String,
    modelType: ProviderType,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val typeLabel = when (modelType) {
        ProviderType.GGUF -> "Text"
        ProviderType.DIFFUSION -> "Image"
        ProviderType.TTS -> "TTS"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                TnIcons.Cpu, null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text("Load Previous Model?", fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)) {
                Text(
                    "You previously had a model loaded. Would you like to load it again?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    modelName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    typeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Load")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Skip")
            }
        },
        shape = RoundedCornerShape(Standards.RadiusXl)
    )
}
