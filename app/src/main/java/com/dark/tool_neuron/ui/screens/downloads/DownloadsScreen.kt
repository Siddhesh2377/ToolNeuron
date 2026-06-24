package com.dark.tool_neuron.ui.screens.downloads

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.download_manager.HxdState
import com.dark.download_manager.HxdStatus
import com.dark.download_manager.formatBytes
import com.dark.tool_neuron.model.DownloadHistoryEntry
import com.dark.tool_neuron.model.DownloadHistoryStatus
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.TnIndeterminateProgressBar
import com.dark.tool_neuron.ui.components.TnProgressBar
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.viewmodel.ActiveDownloadItem
import com.dark.tool_neuron.viewmodel.DownloadsViewModel

@Composable
fun DownloadsScreen(
    innerPadding: PaddingValues,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val active by viewModel.activeDownloads.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val dimens = LocalDimens.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .navigationBarsPadding()
            .padding(horizontal = dimens.screenPadding),
        contentAlignment = Alignment.TopCenter,
    ) {
        if (active.isEmpty() && history.isEmpty()) {
            EmptyState()
        } else {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
            ) {
                item { Spacer(Modifier.height(dimens.spacingSm)) }

                if (active.isNotEmpty()) {
                    item { SectionHeader(label = "Active", count = active.size) }
                    items(active, key = { it.hxdId }) { item ->
                        ActiveRow(
                            item = item,
                            onCancel = { viewModel.cancel(item.hxdId) },
                            onRetry = { viewModel.retry(item.hxdId) },
                            onClear = { viewModel.clear(item.hxdId) },
                        )
                    }
                    item { Spacer(Modifier.height(dimens.spacingSm)) }
                }

                if (history.isNotEmpty()) {
                    item {
                        SectionHeader(
                            label = "History",
                            count = history.size,
                            onClear = { viewModel.clearHistory() },
                        )
                    }
                    items(history, key = { it.id }) { entry ->
                        HistoryRow(entry = entry)
                    }
                }

                item { Spacer(Modifier.height(dimens.spacingMd)) }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    label: String,
    count: Int,
    onClear: (() -> Unit)? = null,
) {
    val dimens = LocalDimens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.spacingSm, vertical = dimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${label.uppercase()}  ·  $count",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (onClear != null) {
            ActionButton(
                onClickListener = onClear,
                icon = TnIcons.Trash,
                contentDescription = "Clear history",
            )
        }
    }
}

@Composable
private fun ActiveRow(
    item: ActiveDownloadItem,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onClear: () -> Unit,
) {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current
    val state = item.state
    Surface(
        shape = shapes.card,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingMd),
            ) {
                TypeBadge(type = item.type)
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (state.status == HxdStatus.FAILED) {
                    ActionButton(
                        onClickListener = onRetry,
                        icon = TnIcons.Refresh,
                        contentDescription = "Retry download",
                    )
                    ActionButton(
                        onClickListener = onClear,
                        icon = TnIcons.Trash,
                        contentDescription = "Clear failed download",
                    )
                } else {
                    ActionButton(
                        onClickListener = onCancel,
                        icon = TnIcons.X,
                        contentDescription = "Cancel download",
                    )
                }
            }
            ActiveProgress(state = state)
        }
    }
}

@Composable
private fun ActiveProgress(state: HxdState) {
    val dimens = LocalDimens.current
    when (state.status) {
        HxdStatus.QUEUED -> Text(
            text = "Queued",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HxdStatus.CONNECTING -> {
            TnIndeterminateProgressBar(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(dimens.spacingXxs))
            Text(
                text = "Connecting…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HxdStatus.PAUSED -> {
            if (state.totalBytes > 0L) {
                TnProgressBar(
                    progress = state.progress.coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(dimens.spacingXxs))
            Text(
                text = "Paused",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HxdStatus.FAILED -> {
            Text(
                text = state.error ?: "Download failed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        else -> {
            if (state.totalBytes <= 0L) {
                TnIndeterminateProgressBar(modifier = Modifier.fillMaxWidth())
            } else {
                TnProgressBar(
                    progress = state.progress.coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(dimens.spacingXxs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = progressLabel(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = state.speedFormatted,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun progressLabel(state: HxdState): String {
    val downloaded = formatBytes(state.downloadedBytes.coerceAtLeast(0L))
    if (state.totalBytes <= 0L) return downloaded
    val total = formatBytes(state.totalBytes)
    val pct = (state.progress.coerceIn(0f, 1f) * 100f).toInt()
    return "$pct%  ·  $downloaded / $total"
}

@Composable
private fun HistoryRow(entry: DownloadHistoryEntry) {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current
    val statusColor = when (entry.status) {
        DownloadHistoryStatus.COMPLETED -> MaterialTheme.colorScheme.primary
        DownloadHistoryStatus.FAILED -> MaterialTheme.colorScheme.error
        DownloadHistoryStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusIcon = when (entry.status) {
        DownloadHistoryStatus.COMPLETED -> TnIcons.CircleCheck
        DownloadHistoryStatus.FAILED -> TnIcons.AlertTriangle
        DownloadHistoryStatus.CANCELLED -> TnIcons.X
    }
    val statusLabel = when (entry.status) {
        DownloadHistoryStatus.COMPLETED -> "Completed"
        DownloadHistoryStatus.FAILED -> "Failed"
        DownloadHistoryStatus.CANCELLED -> "Cancelled"
    }
    Surface(
        shape = shapes.cardSmall,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(dimens.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingMd),
        ) {
            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                modifier = Modifier.size(dimens.iconMd),
                tint = statusColor,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = historySubtitle(entry, statusLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun historySubtitle(entry: DownloadHistoryEntry, statusLabel: String): String {
    val parts = mutableListOf(statusLabel)
    parts += relativeTime(entry.completedAt)
    if (entry.totalBytes > 0L) parts += formatBytes(entry.totalBytes)
    if (entry.error != null && entry.error.isNotBlank() &&
        entry.status != DownloadHistoryStatus.COMPLETED
    ) {
        parts += entry.error.take(48)
    }
    return parts.joinToString("  ·  ")
}

private fun relativeTime(ms: Long): String =
    DateUtils.getRelativeTimeSpanString(
        ms,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()

@Composable
private fun TypeBadge(type: String) {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current
    Surface(
        shape = shapes.full,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        modifier = Modifier.size(dimens.actionIconSize),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = iconForType(type),
                contentDescription = null,
                modifier = Modifier.size(dimens.iconSm),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun iconForType(type: String): ImageVector = when (type.lowercase()) {
    "gguf", "embedding" -> TnIcons.Cpu
    "vlm", "mmproj" -> TnIcons.Eye
    "tts" -> TnIcons.Volume
    "stt" -> TnIcons.Mic
    "image_gen", "image_upscaler" -> TnIcons.Photo
    "runtime" -> TnIcons.Package
    else -> TnIcons.Download
}

@Composable
private fun EmptyState() {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current
    Surface(
        shape = shapes.card,
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.spacingXxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(dimens.spacingMd),
        ) {
            Surface(
                shape = shapes.full,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                modifier = Modifier.size(72.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = TnIcons.Download,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = "No downloads yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Downloads you start from the Store or Image Task will appear here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
