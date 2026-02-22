package com.dark.tool_neuron.ui.screen.memory

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dark.tool_neuron.ui.theme.ManropeFontFamily
import com.dark.tool_neuron.ui.theme.rDp
import com.dark.tool_neuron.ui.theme.rSp
import com.memoryvault.core.VaultStats

@Composable
fun VaultStatsOverview(
    stats: VaultStats?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (stats == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(modifier = Modifier.size(rDp(28.dp)))
                Text(
                    "Loading stats...",
                    fontFamily = ManropeFontFamily,
                    fontSize = rSp(13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = rDp(12.dp))
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = rDp(16.dp), vertical = rDp(8.dp)),
        verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
    ) {
        item { StorageCard(stats) }
        item { QuickStats(stats) }
        item { ContentBreakdown(stats) }
        item { Timeline(stats) }
        item { Performance(stats) }
    }
}

@Composable
private fun StorageCard(stats: VaultStats) {
    val usedSpace = stats.totalSizeBytes
    val wastedSpace = stats.wastedSpaceBytes
    val totalSpace = usedSpace + wastedSpace

    val usedPercent = if (totalSpace > 0) usedSpace.toFloat() / totalSpace else 0f
    val animatedPercent by animateFloatAsState(
        targetValue = usedPercent,
        animationSpec = tween(800),
        label = "storage"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        shape = RoundedCornerShape(rDp(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDp(16.dp)),
            horizontalArrangement = Arrangement.spacedBy(rDp(20.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ring chart
            Box(
                modifier = Modifier.size(rDp(90.dp)),
                contentAlignment = Alignment.Center
            ) {
                val primaryColor = MaterialTheme.colorScheme.primary
                val trackColor = MaterialTheme.colorScheme.surfaceVariant

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 12.dp.toPx()
                    val radius = (size.minDimension - strokeWidth) / 2

                    drawCircle(
                        color = trackColor,
                        radius = radius,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

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

                Text(
                    "${(animatedPercent * 100).toInt()}%",
                    fontFamily = ManropeFontFamily,
                    fontSize = rSp(18.sp),
                    fontWeight = FontWeight.Bold
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(rDp(8.dp))) {
                Text(
                    "Storage",
                    fontFamily = ManropeFontFamily,
                    fontSize = rSp(15.sp),
                    fontWeight = FontWeight.SemiBold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(rDp(16.dp))) {
                    LegendItem(MaterialTheme.colorScheme.primary, "Used", formatSize(usedSpace))
                    LegendItem(MaterialTheme.colorScheme.tertiary, "Wasted", formatSize(wastedSpace))
                }
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(rDp(6.dp))
    ) {
        Box(
            modifier = Modifier
                .size(rDp(10.dp))
                .clip(CircleShape)
                .background(color)
        )
        Column {
            Text(
                label,
                fontFamily = ManropeFontFamily,
                fontSize = rSp(11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                fontFamily = ManropeFontFamily,
                fontSize = rSp(13.sp),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun QuickStats(stats: VaultStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(rDp(10.dp))
    ) {
        StatChip(Icons.Outlined.Layers, stats.totalItems.toString(), "Items", Modifier.weight(1f))
        StatChip(Icons.Outlined.Storage, formatSize(stats.indexSizeBytes), "Index", Modifier.weight(1f))
        StatChip(Icons.Outlined.Compress, "${((1 - stats.compressionRatio) * 100).toInt()}%", "Saved", Modifier.weight(1f))
    }
}

@Composable
private fun StatChip(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(rDp(12.dp))
    ) {
        Column(
            modifier = Modifier.padding(rDp(12.dp)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon, null,
                modifier = Modifier.size(rDp(18.dp)),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(rDp(6.dp)))
            Text(
                value,
                fontFamily = ManropeFontFamily,
                fontSize = rSp(14.sp),
                fontWeight = FontWeight.Bold
            )
            Text(
                label,
                fontFamily = ManropeFontFamily,
                fontSize = rSp(11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ContentBreakdown(stats: VaultStats) {
    val items = listOf(
        Triple("Messages", stats.messageCount, MaterialTheme.colorScheme.primary),
        Triple("Files", stats.fileCount, MaterialTheme.colorScheme.tertiary),
        Triple("Embeddings", stats.embeddingCount, MaterialTheme.colorScheme.secondary),
        Triple("Custom", stats.customDataCount, MaterialTheme.colorScheme.error)
    )

    val total = items.sumOf { it.second }.coerceAtLeast(1)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(rDp(12.dp))
    ) {
        Column(
            modifier = Modifier.padding(rDp(14.dp)),
            verticalArrangement = Arrangement.spacedBy(rDp(10.dp))
        ) {
            Text(
                "Content Breakdown",
                fontFamily = ManropeFontFamily,
                fontSize = rSp(14.sp),
                fontWeight = FontWeight.SemiBold
            )

            // Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rDp(8.dp))
                    .clip(RoundedCornerShape(rDp(4.dp)))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                items.forEach { (_, count, color) ->
                    val fraction = count.toFloat() / total
                    if (fraction > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(fraction.coerceAtLeast(0.01f))
                                .background(color)
                        )
                    }
                }
            }

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                items.forEach { (label, count, color) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(rDp(4.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .size(rDp(8.dp))
                                .clip(CircleShape)
                                .background(color)
                        )
                        Text(
                            "$count",
                            fontFamily = ManropeFontFamily,
                            fontSize = rSp(12.sp),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Timeline(stats: VaultStats) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(rDp(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDp(14.dp)),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TimeItem(Icons.Outlined.History, "Oldest",
                if (stats.oldestItem > 0) formatTimestampFull(stats.oldestItem) else "N/A")
            TimeItem(Icons.Outlined.Update, "Newest",
                if (stats.newestItem > 0) formatTimestampFull(stats.newestItem) else "N/A")
        }
    }
}

@Composable
private fun TimeItem(icon: ImageVector, label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
    ) {
        Icon(
            icon, null,
            modifier = Modifier.size(rDp(16.dp)),
            tint = MaterialTheme.colorScheme.primary
        )
        Column {
            Text(
                label,
                fontFamily = ManropeFontFamily,
                fontSize = rSp(11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                fontFamily = ManropeFontFamily,
                fontSize = rSp(12.sp),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun Performance(stats: VaultStats) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(rDp(12.dp))
    ) {
        Column(
            modifier = Modifier.padding(rDp(14.dp)),
            verticalArrangement = Arrangement.spacedBy(rDp(10.dp))
        ) {
            Text(
                "Performance",
                fontFamily = ManropeFontFamily,
                fontSize = rSp(14.sp),
                fontWeight = FontWeight.SemiBold
            )

            ProgressBar(
                "Compression",
                "${((1 - stats.compressionRatio) * 100).toInt()}%",
                1 - stats.compressionRatio
            )

            val wastedPercent = if (stats.totalSizeBytes > 0) {
                stats.wastedSpaceBytes.toFloat() / (stats.totalSizeBytes + stats.wastedSpaceBytes)
            } else 0f

            ProgressBar(
                "Efficiency",
                "${((1 - wastedPercent) * 100).toInt()}%",
                1 - wastedPercent
            )
        }
    }
}

@Composable
private fun ProgressBar(label: String, value: String, progress: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(rDp(4.dp))) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                label,
                fontFamily = ManropeFontFamily,
                fontSize = rSp(12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                fontFamily = ManropeFontFamily,
                fontSize = rSp(12.sp),
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
