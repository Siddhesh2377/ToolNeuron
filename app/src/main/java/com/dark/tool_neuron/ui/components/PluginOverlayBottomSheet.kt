package com.dark.tool_neuron.ui.components

import androidx.compose.animation.AnimatedVisibility
import com.dark.tool_neuron.ui.theme.Motion
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.models.plugins.PluginInfo
import com.dark.gguf_lib.toolcalling.ToolCallingConfig
import kotlin.math.roundToInt
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.global.Standards

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginOverlayBottomSheet(
    show: Boolean,
    plugins: List<PluginInfo>,
    enabledPluginNames: Set<String>,
    expandedPluginIds: Set<String>,
    multiTurnEnabled: Boolean = true,
    toolCallingConfig: ToolCallingConfig = ToolCallingConfig(),
    onDismiss: () -> Unit,
    onPluginToggle: (String, Boolean) -> Unit,
    onPluginExpand: (String) -> Unit,
    onMultiTurnToggle: (Boolean) -> Unit = {},
    onMaxRoundsChange: (Int) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (show) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = {
                Box(
                    Modifier
                        .padding(vertical = Standards.SpacingMd)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 600.dp)
                    .padding(bottom = Standards.SpacingLg)
            ) {
                // ── Header ──
                PluginOverlayHeader(
                    enabledCount = enabledPluginNames.size,
                    totalCount = plugins.size
                )

                Spacer(modifier = Modifier.height(Standards.SpacingMd))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
                ) {
                    // ── Config Section ──
                    item {
                        ToolCallingConfigSection(
                            multiTurnEnabled = multiTurnEnabled,
                            toolCallingConfig = toolCallingConfig,
                            onMultiTurnToggle = onMultiTurnToggle,
                            onMaxRoundsChange = onMaxRoundsChange
                        )
                    }

                    item {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = Standards.SpacingLg, vertical = Standards.SpacingXs),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }

                    // ── Plugin List ──
                    if (plugins.isEmpty()) {
                        item { EmptyPluginState() }
                    } else {
                        items(plugins) { plugin ->
                            PluginListItem(
                                plugin = plugin,
                                isEnabled = enabledPluginNames.contains(plugin.name),
                                isExpanded = expandedPluginIds.contains(plugin.name),
                                onToggle = { enabled -> onPluginToggle(plugin.name, enabled) },
                                onExpand = { onPluginExpand(plugin.name) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Config Section ──

@Composable
private fun ToolCallingConfigSection(
    multiTurnEnabled: Boolean,
    toolCallingConfig: ToolCallingConfig,
    onMultiTurnToggle: (Boolean) -> Unit,
    onMaxRoundsChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Standards.SpacingLg),
        verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
    ) {
        SectionHeader(title = "Tool Calling Config")

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ) {
            Column(
                modifier = Modifier.padding(Standards.SpacingMd),
                verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
            ) {
                // Multi-turn toggle
                SwitchRow(
                    title = "Multi-turn",
                    description = "Allow model to chain multiple tool calls",
                    checked = multiTurnEnabled,
                    onCheckedChange = onMultiTurnToggle
                )

                // Max Rounds Slider (only when multi-turn enabled)
                AnimatedVisibility(
                    visible = multiTurnEnabled,
                    enter = Motion.Enter,
                    exit = Motion.Exit
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Standards.SpacingXs)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Max Rounds",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(Standards.SpacingXs)
                            ) {
                                Text(
                                    text = "${toolCallingConfig.maxRounds}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(
                                        horizontal = 8.dp,
                                        vertical = 2.dp
                                    )
                                )
                            }
                        }

                        Slider(
                            value = toolCallingConfig.maxRounds.toFloat(),
                            onValueChange = { onMaxRoundsChange(it.roundToInt()) },
                            valueRange = 1f..10f,
                            steps = 8,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }
        }
    }
}

// ── Header ──

@Composable
private fun PluginOverlayHeader(
    enabledCount: Int,
    totalCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Standards.SpacingLg),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            SectionHeader(title = "Plugins") {
                InfoBadge(
                    text = "$enabledCount / $totalCount active",
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

// ── Plugin Card ──

@Composable
private fun PluginListItem(
    plugin: PluginInfo,
    isEnabled: Boolean,
    isExpanded: Boolean,
    onToggle: (Boolean) -> Unit,
    onExpand: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Standards.SpacingLg),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) {
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        shape = RoundedCornerShape(Standards.RadiusLg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Standards.SpacingLg, vertical = 6.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plugin.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${plugin.toolDefinitionBuilder.size} tool${if (plugin.toolDefinitionBuilder.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = onToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.tertiary,
                            checkedTrackColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    )

                    IconButton(onClick = onExpand) {
                        ExpandCollapseIcon(isExpanded = isExpanded)
                    }
                }
            }

            // Expanded Details
            AnimatedVisibility(
                visible = isExpanded,
                enter = Motion.Enter,
                exit = Motion.Exit
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Standards.SpacingSm)
                ) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Text(
                        text = plugin.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Standards.SpacingSm)
                    )

                    // Tools
                    if (plugin.toolDefinitionBuilder.isNotEmpty()) {
                        Text(
                            text = "Tools:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = Standards.SpacingXs)
                        )

                        plugin.toolDefinitionBuilder.forEach { tool ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Standards.SpacingXxs)
                            ) {
                                Text(
                                    text = "• ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                Column {
                                    Text(
                                        text = tool.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = tool.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Empty State ──

@Composable
private fun EmptyPluginState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Standards.SpacingXxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "No Plugins Available",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Standards.SpacingSm)
        )
        Text(
            text = "Plugins will appear here once they are registered",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}
