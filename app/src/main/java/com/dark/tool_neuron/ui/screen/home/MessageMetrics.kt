package com.dark.tool_neuron.ui.screen.home

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.models.engine_schema.DecodingMetrics
import com.dark.tool_neuron.models.messages.ImageGenerationMetrics
import com.dark.tool_neuron.models.messages.MemoryMetrics
import com.dark.tool_neuron.ui.components.ExpandCollapseIcon
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.Motion
import com.dark.tool_neuron.ui.theme.maple
import com.dark.tool_neuron.global.Standards

// ── MetricsDisplay ──

@Composable
internal fun MetricsDisplay(metrics: DecodingMetrics, memoryMetrics: MemoryMetrics? = null) {
    var isExpanded by remember { mutableStateOf(false) }

    val formattedSpeed = remember(metrics.tokensPerSecond) {
        "%.1f".format(metrics.tokensPerSecond)
    }
    val formattedTime = remember(metrics.totalTimeMs) {
        if (metrics.totalTimeMs > 0f) "%.1f".format(metrics.totalTimeMs / 1000f) else null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Standards.SpacingMd)
    ) {
        // Summary row
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            shape = RoundedCornerShape(Standards.RadiusMd),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
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
                        imageVector = TnIcons.Gauge,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "$formattedSpeed t/s",
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
                        text = "${metrics.tokensEvaluated + metrics.tokensPredicted} tokens",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                ExpandCollapseIcon(isExpanded = isExpanded, size = 18.dp)
            }
        }

        // Detailed metrics
        AnimatedVisibility(
            visible = isExpanded,
            enter = Motion.Enter,
            exit = Motion.Exit
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                shape = RoundedCornerShape(Standards.RadiusMd),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
                ) {
                    MetricRow(
                        icon = TnIcons.Coins,
                        label = "Total Tokens",
                        value = (metrics.tokensEvaluated + metrics.tokensPredicted).toString()
                    )

                    if (metrics.tokensEvaluated > 0) {
                        MetricRow(
                            icon = TnIcons.Prompt,
                            label = "Prompt Tokens",
                            value = metrics.tokensEvaluated.toString()
                        )
                    }

                    if (metrics.tokensPredicted > 0) {
                        MetricRow(
                            icon = TnIcons.Wand,
                            label = "Generated Tokens",
                            value = metrics.tokensPredicted.toString()
                        )
                    }

                    MetricRow(
                        icon = TnIcons.Gauge,
                        label = "Speed",
                        value = "$formattedSpeed t/s"
                    )

                    if (metrics.timeToFirstTokenMs > 0f) {
                        MetricRow(
                            icon = TnIcons.Clock,
                            label = "Time to First Token",
                            value = "${"%.0f".format(metrics.timeToFirstTokenMs)} ms"
                        )
                    }

                    formattedTime?.let { time ->
                        MetricRow(
                            icon = TnIcons.Clock,
                            label = "Total Duration",
                            value = "$time s"
                        )
                    }

                    // Memory metrics section
                    memoryMetrics?.let { mem ->
                        if (mem.modelSizeMB > 0 || mem.peakMemoryMB > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = Standards.SpacingXs),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )

                            Text(
                                text = "Memory",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(bottom = Standards.SpacingXs)
                            )

                            if (mem.modelSizeMB > 0) {
                                MetricRow(
                                    icon = TnIcons.Coins,
                                    label = "Model Size",
                                    value = "${mem.modelSizeMB} MB"
                                )
                            }

                            if (mem.contextSizeMB > 0) {
                                MetricRow(
                                    icon = TnIcons.Coins,
                                    label = "Context Size",
                                    value = "${mem.contextSizeMB} MB"
                                )
                            }

                            if (mem.peakMemoryMB > 0) {
                                MetricRow(
                                    icon = TnIcons.Coins,
                                    label = "Peak Memory",
                                    value = "${mem.peakMemoryMB} MB"
                                )
                            }

                            if (mem.memoryUsagePercent > 0) {
                                MetricRow(
                                    icon = TnIcons.Coins,
                                    label = "Memory Usage",
                                    value = "${"%.1f".format(mem.memoryUsagePercent)}%"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── MemoryMetricsDisplay ──

@Composable
internal fun MemoryMetricsDisplay(metrics: MemoryMetrics) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Standards.SpacingMd)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            shape = RoundedCornerShape(Standards.RadiusMd),
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
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
                        imageVector = TnIcons.Coins,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = "Memory",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (metrics.peakMemoryMB > 0) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )

                        Text(
                            text = "${metrics.peakMemoryMB} MB peak",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                ExpandCollapseIcon(isExpanded = isExpanded, size = 18.dp)
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = Motion.Enter,
            exit = Motion.Exit
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                shape = RoundedCornerShape(Standards.RadiusMd),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
                ) {
                    if (metrics.modelSizeMB > 0) {
                        MetricRow(
                            icon = TnIcons.Coins,
                            label = "Model Size",
                            value = "${metrics.modelSizeMB} MB"
                        )
                    }

                    if (metrics.contextSizeMB > 0) {
                        MetricRow(
                            icon = TnIcons.Coins,
                            label = "Context Size",
                            value = "${metrics.contextSizeMB} MB"
                        )
                    }

                    if (metrics.peakMemoryMB > 0) {
                        MetricRow(
                            icon = TnIcons.Coins,
                            label = "Peak Memory",
                            value = "${metrics.peakMemoryMB} MB"
                        )
                    }

                    if (metrics.memoryUsagePercent > 0) {
                        MetricRow(
                            icon = TnIcons.Coins,
                            label = "Memory Usage",
                            value = "${"%.1f".format(metrics.memoryUsagePercent)}%"
                        )
                    }
                }
            }
        }
    }
}

// ── ImageMetricsDisplay ──

@Composable
internal fun ImageMetricsDisplay(metrics: ImageGenerationMetrics) {
    var isExpanded by remember { mutableStateOf(false) }

    val formattedTime = remember(metrics.generationTimeMs) {
        "%.1f".format(metrics.generationTimeMs / 1000f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Standards.SpacingMd)
    ) {
        // Summary row
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            shape = RoundedCornerShape(Standards.RadiusMd),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
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
                        imageVector = TnIcons.Photo,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = "${metrics.width}×${metrics.height}",
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
                        text = "${metrics.steps} steps",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                ExpandCollapseIcon(isExpanded = isExpanded, size = 18.dp)
            }
        }

        // Detailed metrics
        AnimatedVisibility(
            visible = isExpanded,
            enter = Motion.Enter,
            exit = Motion.Exit
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                shape = RoundedCornerShape(Standards.RadiusMd),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
                ) {
                    MetricRow(
                        icon = TnIcons.Photo,
                        label = "Dimensions",
                        value = "${metrics.width} × ${metrics.height}"
                    )

                    MetricRow(
                        icon = TnIcons.SortAscending,
                        label = "Steps",
                        value = metrics.steps.toString()
                    )

                    MetricRow(
                        icon = TnIcons.Adjustments,
                        label = "CFG Scale",
                        value = "%.1f".format(metrics.cfgScale)
                    )

                    MetricRow(
                        icon = TnIcons.Coins,
                        label = "Seed",
                        value = metrics.seed.toString()
                    )

                    MetricRow(
                        icon = TnIcons.CalendarTime,
                        label = "Scheduler",
                        value = metrics.scheduler.uppercase()
                    )

                    MetricRow(
                        icon = TnIcons.Clock,
                        label = "Generation Time",
                        value = "$formattedTime s"
                    )
                }
            }
        }
    }
}

// ── MetricRow ──

@Composable
internal fun MetricRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = maple
        )
    }
}
