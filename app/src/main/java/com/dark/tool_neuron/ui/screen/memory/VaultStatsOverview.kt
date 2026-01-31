package com.dark.tool_neuron.ui.screen.memory

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.global.Standards
import com.dark.tool_neuron.ui.components.BodyLabel
import com.dark.tool_neuron.ui.components.CaptionText
import com.dark.tool_neuron.ui.components.StandardCard
import com.dark.tool_neuron.ui.theme.rDp
import com.memoryvault.core.VaultStats

@Composable
fun VaultStatsOverview(
    stats: VaultStats?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (stats == null) {
        LoadingState()
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(rDp(Standards.SpacingLg)),
        verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingLg))
    ) {
        // Storage ring chart
        item {
            StorageOverviewCard(stats)
        }

        // Quick stats row
        item {
            QuickStatsRow(stats)
        }

        // Content breakdown
        item {
            ContentBreakdownCard(stats)
        }

        // Time range card
        item {
            TimeRangeCard(stats)
        }

        // Performance metrics
        item {
            PerformanceCard(stats)
        }
    }
}

@Composable
fun StorageOverviewCard(stats: VaultStats) {
    val usedSpace = stats.totalSizeBytes
    val wastedSpace = stats.wastedSpaceBytes
    val totalSpace = usedSpace + wastedSpace

    val usedPercent = if (totalSpace > 0) usedSpace.toFloat() / totalSpace else 0f
    val animatedPercent by animateFloatAsState(
        targetValue = usedPercent,
        animationSpec = tween(1000),
        label = "storage_animation"
    )

    StandardCard(
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Storage Overview",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(rDp(Standards.SpacingXl)))

            // Ring chart
            Box(
                modifier = Modifier.size(rDp(160.dp)),
                contentAlignment = Alignment.Center
            ) {
                val primaryColor = MaterialTheme.colorScheme.primary
                val trackColor = MaterialTheme.colorScheme.surfaceVariant

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 24.dp.toPx()
                    val radius = (size.minDimension - strokeWidth) / 2

                    // Track
                    drawCircle(
                        color = trackColor,
                        radius = radius,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Used space arc
                    drawArc(
                        color = primaryColor,
                        startAngle = -90f,
                        sweepAngle = animatedPercent * 360f,
                        useCenter = false,
                        topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                        size = Size(size.width - strokeWidth, size.height - strokeWidth),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${(animatedPercent * 100).toInt()}%",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    CaptionText(text = "Used")
                }
            }

            Spacer(Modifier.height(rDp(Standards.SpacingXl)))

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StorageLegendItem(
                    color = MaterialTheme.colorScheme.primary,
                    label = "Used",
                    value = formatSize(usedSpace)
                )
                StorageLegendItem(
                    color = MaterialTheme.colorScheme.tertiary,
                    label = "Wasted",
                    value = formatSize(wastedSpace)
                )
            }
        }
    }
}

@Composable
fun StorageLegendItem(
    color: Color,
    label: String,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingSm))
    ) {
        Box(
            modifier = Modifier
                .size(rDp(12.dp))
                .clip(CircleShape)
                .background(color)
        )
        Column {
            CaptionText(text = label)
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun QuickStatsRow(stats: VaultStats) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingMd))
    ) {
        items(
            listOf(
                QuickStat("Total Items", stats.totalItems.toString(), Icons.Outlined.Layers),
                QuickStat("Index Size", formatSize(stats.indexSizeBytes), Icons.Outlined.Storage),
                QuickStat("Compression", "${((1 - stats.compressionRatio) * 100).toInt()}%", Icons.Outlined.Compress)
            )
        ) { stat ->
            QuickStatCard(stat)
        }
    }
}

data class QuickStat(
    val label: String,
    val value: String,
    val icon: ImageVector
)

@Composable
fun QuickStatCard(stat: QuickStat) {
    StandardCard(
        modifier = Modifier.width(rDp(120.dp)),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                stat.icon,
                contentDescription = null,
                modifier = Modifier.size(rDp(Standards.IconLg)),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(rDp(Standards.SpacingSm)))
            Text(
                stat.value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            CaptionText(
                text = stat.label,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun ContentBreakdownCard(stats: VaultStats) {
    val items = listOf(
        ContentTypeData("Messages", stats.messageCount, Icons.Outlined.ChatBubbleOutline, MaterialTheme.colorScheme.primary),
        ContentTypeData("Files", stats.fileCount, Icons.Outlined.InsertDriveFile, MaterialTheme.colorScheme.tertiary),
        ContentTypeData("Embeddings", stats.embeddingCount, Icons.Outlined.Hub, MaterialTheme.colorScheme.secondary),
        ContentTypeData("Custom", stats.customDataCount, Icons.Outlined.DataObject, MaterialTheme.colorScheme.error)
    )

    val total = items.sumOf { it.count }.coerceAtLeast(1)

    StandardCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingLg))
        ) {
            Text(
                "Content Breakdown",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            // Horizontal bar chart
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rDp(12.dp))
                    .clip(RoundedCornerShape(rDp(6.dp)))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                items.forEach { item ->
                    val fraction = item.count.toFloat() / total
                    if (fraction > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(fraction.coerceAtLeast(0.01f))
                                .background(item.color)
                        )
                    }
                }
            }

            // Legend grid
            Column(
                verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingMd))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    items.take(2).forEach { item ->
                        ContentTypeItem(item, Modifier.weight(1f))
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    items.drop(2).forEach { item ->
                        ContentTypeItem(item, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

data class ContentTypeData(
    val label: String,
    val count: Int,
    val icon: ImageVector,
    val color: Color
)

@Composable
fun ContentTypeItem(
    item: ContentTypeData,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingSm))
    ) {
        Box(
            modifier = Modifier
                .size(rDp(36.dp))
                .clip(RoundedCornerShape(rDp(Standards.CardSmallCornerRadius)))
                .background(item.color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                item.icon,
                contentDescription = null,
                modifier = Modifier.size(rDp(Standards.IconMd)),
                tint = item.color
            )
        }
        Column {
            Text(
                item.count.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            CaptionText(text = item.label)
        }
    }
}

@Composable
fun TimeRangeCard(stats: VaultStats) {
    StandardCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingLg))
        ) {
            Text(
                "Data Timeline",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TimelineItem(
                    icon = Icons.Outlined.History,
                    label = "Oldest Item",
                    value = if (stats.oldestItem > 0) formatTimestampFull(stats.oldestItem) else "N/A",
                    modifier = Modifier.weight(1f)
                )
                TimelineItem(
                    icon = Icons.Outlined.Update,
                    label = "Newest Item",
                    value = if (stats.newestItem > 0) formatTimestampFull(stats.newestItem) else "N/A",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun TimelineItem(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingSm))
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(rDp(Standards.IconMd)),
            tint = MaterialTheme.colorScheme.primary
        )
        Column {
            CaptionText(text = label)
            Text(
                value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun PerformanceCard(stats: VaultStats) {
    StandardCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingMd))
        ) {
            Text(
                "Performance Metrics",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            PerformanceRow(
                label = "Compression Efficiency",
                value = "${((1 - stats.compressionRatio) * 100).toInt()}%",
                progress = 1 - stats.compressionRatio
            )

            val wastedPercent = if (stats.totalSizeBytes > 0) {
                stats.wastedSpaceBytes.toFloat() / (stats.totalSizeBytes + stats.wastedSpaceBytes)
            } else 0f

            PerformanceRow(
                label = "Space Efficiency",
                value = "${((1 - wastedPercent) * 100).toInt()}%",
                progress = 1 - wastedPercent
            )
        }
    }
}

@Composable
fun PerformanceRow(
    label: String,
    value: String,
    progress: Float
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingXs))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(rDp(6.dp))
                .clip(RoundedCornerShape(rDp(3.dp))),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingLg))
        ) {
            CircularProgressIndicator()
            BodyLabel(
                text = "Loading vault statistics...",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
