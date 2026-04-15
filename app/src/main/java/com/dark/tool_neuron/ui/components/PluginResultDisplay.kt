package com.dark.tool_neuron.ui.components

import androidx.compose.animation.AnimatedVisibility
import com.dark.tool_neuron.ui.theme.Motion
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.models.messages.Messages
import com.dark.tool_neuron.models.plugins.PluginExecutionMetrics
import com.dark.tool_neuron.models.plugins.PluginResultData
import com.dark.tool_neuron.plugins.PluginManager
import org.json.JSONObject
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.global.Standards

@Composable
fun PluginResultCard(
    message: Messages,
    modifier: Modifier = Modifier
) {
    val pluginData = message.content.pluginResultData ?: return
    val metrics = message.pluginMetrics

    var isExpanded by remember { mutableStateOf(false) }
    var showDetailDialog by remember { mutableStateOf(false) }

    if (showDetailDialog) {
        PluginResultDetailDialog(
            pluginData = pluginData,
            metrics = metrics,
            onDismiss = { showDetailDialog = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Standards.SpacingMd)
    ) {
        // Summary Row
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            shape = RoundedCornerShape(Standards.RadiusMd),
            color = if (pluginData.success) {
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            },
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = Standards.SpacingSm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Standards.SpacingMd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = TnIcons.Tool,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (pluginData.success) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )

                    Text(
                        text = pluginData.pluginName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )

                    Text(
                        text = pluginData.toolName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Status Badge
                    Icon(
                        imageVector = if (pluginData.success) {
                            TnIcons.CircleCheck
                        } else {
                            TnIcons.AlertTriangle
                        },
                        contentDescription = if (pluginData.success) "Success" else "Failed",
                        modifier = Modifier.size(14.dp),
                        tint = if (pluginData.success) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }

                ExpandCollapseIcon(isExpanded = isExpanded, size = 18.dp)
            }
        }

        // Detailed Results
        AnimatedVisibility(
            visible = isExpanded,
            enter = Motion.Enter,
            exit = Motion.Exit
        ) {
            Surface(
                onClick = { showDetailDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                shape = RoundedCornerShape(Standards.RadiusMd),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Metrics
                    metrics?.let { m ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Execution Time:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${m.executionTimeMs}ms",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Input Parameters (if expanded)
                    if (pluginData.inputParams.isNotEmpty()) {
                        Text(
                            text = "Input:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(Standards.RadiusSm),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = formatJsonForDisplay(pluginData.inputParams),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(Standards.SpacingSm)
                            )
                        }
                    }

                    // Output using CacheToolUI
                    if (pluginData.success) {
                        Text(
                            text = "Result:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        val plugin = PluginManager.getPlugin(pluginData.pluginName)
                        // Parse JSON outside composable scope
                        val resultJson = remember(pluginData.resultData) {
                            try {
                                JSONObject(pluginData.resultData)
                            } catch (e: Exception) {
                                null
                            }
                        }

                        if (plugin != null && resultJson != null) {
                            plugin.CacheToolUI(data = resultJson)
                        } else {
                            // Fallback to text display if plugin not available or JSON parsing failed
                            Surface(
                                shape = RoundedCornerShape(Standards.RadiusSm),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    text = pluginData.resultData,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(Standards.SpacingSm)
                                )
                            }
                        }
                    } else {
                        // Show error
                        Text(
                            text = "Error:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Surface(
                            shape = RoundedCornerShape(Standards.RadiusSm),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        ) {
                            Text(
                                text = pluginData.resultData,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(Standards.SpacingSm)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatJsonForDisplay(json: String): String {
    return try {
        val jsonObject = JSONObject(json)
        jsonObject.keys().asSequence().joinToString("\n") { key ->
            "$key: ${jsonObject.get(key)}"
        }
    } catch (_: Exception) {
        json
    }
}

@Composable
private fun PluginResultDetailDialog(
    pluginData: PluginResultData,
    metrics: PluginExecutionMetrics?,
    onDismiss: () -> Unit
) {
    ToolDetailDialog(
        title = "${pluginData.pluginName} · ${pluginData.toolName}",
        onDismiss = onDismiss
    ) {
        DetailKeyValue("Status", if (pluginData.success) "Success" else "Failed")
        metrics?.let {
            DetailKeyValue("Execution Time", "${it.executionTimeMs}ms")
        }

        if (pluginData.inputParams.isNotBlank()) {
            DetailSection(label = "Input Parameters", content = formatJsonForDisplay(pluginData.inputParams))
        }

        DetailSection(
            label = if (pluginData.success) "Result" else "Error",
            content = pluginData.resultData
        )
    }
}
