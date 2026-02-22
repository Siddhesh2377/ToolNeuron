package com.dark.tool_neuron.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import com.dark.tool_neuron.ui.theme.rDp
import com.mp.ai_gguf.toolcalling.GrammarMode
import com.mp.ai_gguf.toolcalling.ToolCallingConfig
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginOverlayBottomSheet(
    show: Boolean,
    plugins: List<PluginInfo>,
    enabledPluginNames: Set<String>,
    expandedPluginIds: Set<String>,
    grammarMode: GrammarMode = GrammarMode.LAZY,
    multiTurnEnabled: Boolean = true,
    toolCallingConfig: ToolCallingConfig = ToolCallingConfig(),
    onDismiss: () -> Unit,
    onPluginToggle: (String, Boolean) -> Unit,
    onPluginExpand: (String) -> Unit,
    onGrammarModeChange: (GrammarMode) -> Unit = {},
    onMultiTurnToggle: (Boolean) -> Unit = {},
    onMaxRoundsChange: (Int) -> Unit = {},
    onMaxTokensPerTurnChange: (Int) -> Unit = {}
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
                        .padding(vertical = rDp(12.dp))
                        .width(rDp(40.dp))
                        .height(rDp(4.dp))
                        .clip(RoundedCornerShape(rDp(2.dp)))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = rDp(600.dp))
                    .padding(bottom = rDp(16.dp))
            ) {
                // Header
                PluginOverlayHeader(
                    enabledCount = enabledPluginNames.size,
                    totalCount = plugins.size
                )

                Spacer(modifier = Modifier.height(rDp(12.dp)))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(rDp(8.dp))
                ) {
                    // Tool Calling Config Section
                    item {
                        ToolCallingConfigSection(
                            grammarMode = grammarMode,
                            multiTurnEnabled = multiTurnEnabled,
                            toolCallingConfig = toolCallingConfig,
                            onGrammarModeChange = onGrammarModeChange,
                            onMultiTurnToggle = onMultiTurnToggle,
                            onMaxRoundsChange = onMaxRoundsChange,
                            onMaxTokensPerTurnChange = onMaxTokensPerTurnChange
                        )
                    }

                    item {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = rDp(16.dp), vertical = rDp(4.dp)),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }

                    // Plugin List
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

@Composable
private fun ToolCallingConfigSection(
    grammarMode: GrammarMode,
    multiTurnEnabled: Boolean,
    toolCallingConfig: ToolCallingConfig,
    onGrammarModeChange: (GrammarMode) -> Unit,
    onMultiTurnToggle: (Boolean) -> Unit,
    onMaxRoundsChange: (Int) -> Unit,
    onMaxTokensPerTurnChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = rDp(16.dp)),
        verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
    ) {
        SectionHeader(title = "Tool Calling Config")

        // Grammar Mode Toggle
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(rDp(10.dp)),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ) {
            Column(
                modifier = Modifier.padding(rDp(12.dp)),
                verticalArrangement = Arrangement.spacedBy(rDp(8.dp))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(rDp(4.dp))) {
                    BodyLabel(
                        text = "Grammar Mode",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    CaptionText(
                        text = if (grammarMode == GrammarMode.STRICT) "Strict: Forces JSON tool output"
                        else "Lazy: Model chooses text or tool"
                    )
                    ActionToggleGroup(
                        items = listOf(GrammarMode.STRICT, GrammarMode.LAZY),
                        selectedItem = grammarMode,
                        onItemSelected = onGrammarModeChange,
                        itemLabel = { it.name }
                    )
                }

                // Multi-turn toggle
                SwitchRow(
                    title = "Multi-turn",
                    description = "Allow model to chain multiple tool calls",
                    checked = multiTurnEnabled,
                    onCheckedChange = onMultiTurnToggle
                )

                // Max Rounds Slider (only visible when multi-turn is enabled)
                AnimatedVisibility(visible = multiTurnEnabled) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(rDp(4.dp))
                    ) {
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
                                shape = RoundedCornerShape(rDp(4.dp))
                            ) {
                                Text(
                                    text = "${toolCallingConfig.maxRounds}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(
                                        horizontal = rDp(8.dp),
                                        vertical = rDp(2.dp)
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

@Composable
private fun GrammarModeChip(
    mode: GrammarMode,
    onModeChange: (GrammarMode) -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(rDp(8.dp)))
            .clickable {
                onModeChange(
                    if (mode == GrammarMode.STRICT) GrammarMode.LAZY else GrammarMode.STRICT
                )
            },
        shape = RoundedCornerShape(rDp(8.dp)),
        color = if (mode == GrammarMode.STRICT) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        } else {
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
        }
    ) {
        Text(
            text = if (mode == GrammarMode.STRICT) "STRICT" else "LAZY",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (mode == GrammarMode.STRICT) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.tertiary
            },
            modifier = Modifier.padding(horizontal = rDp(12.dp), vertical = rDp(6.dp))
        )
    }
}

@Composable
private fun PluginOverlayHeader(
    enabledCount: Int,
    totalCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = rDp(16.dp)),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            SectionHeader(title = "Plugins (Beta)") {
                InfoBadge(
                    text = "$enabledCount / $totalCount enabled",
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

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
            .padding(horizontal = rDp(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) {
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        shape = RoundedCornerShape(rDp(12.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = rDp(16.dp), vertical = rDp(6.dp))
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = null,
                        tint = if (isEnabled) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(rDp(24.dp))
                    )

                    Spacer(modifier = Modifier.width(rDp(12.dp)))

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
                            text = "${plugin.toolDefinitionBuilder.size} tools",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = onToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.tertiary,
                            checkedTrackColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    )

                    IconButton(onClick = onExpand) {
                        Icon(
                            imageVector = if (isExpanded) {
                                Icons.Default.ExpandLess
                            } else {
                                Icons.Default.ExpandMore
                            },
                            contentDescription = if (isExpanded) "Collapse" else "Expand"
                        )
                    }
                }
            }

            // Expanded Content
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = rDp(12.dp))
                ) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = rDp(8.dp)),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    // Description
                    Text(
                        text = plugin.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = rDp(8.dp))
                    )

                    // Author and Version
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = rDp(8.dp)),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Author: ${plugin.author}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "v${plugin.version}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Tools List
                    if (plugin.toolDefinitionBuilder.isNotEmpty()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = rDp(8.dp)),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        Text(
                            text = "Available Tools:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = rDp(8.dp))
                        )

                        plugin.toolDefinitionBuilder.forEach { tool ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = rDp(4.dp))
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

@Composable
private fun EmptyPluginState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(rDp(32.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Build,
            contentDescription = null,
            modifier = Modifier
                .size(rDp(64.dp))
                .padding(bottom = rDp(16.dp)),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Text(
            text = "No Plugins Available",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = rDp(8.dp))
        )
        Text(
            text = "Plugins will appear here once they are registered",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}
