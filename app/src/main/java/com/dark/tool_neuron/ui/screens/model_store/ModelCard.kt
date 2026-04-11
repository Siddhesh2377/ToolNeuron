package com.dark.tool_neuron.ui.screens.model_store

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dark.download_manager.HxdState
import com.dark.download_manager.HxdStatus
import com.dark.tool_neuron.model.HuggingFaceModel
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.ActionProgressButton
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.ui.theme.Motion
import com.dark.tool_neuron.util.formatBytes

@Composable
fun CatalogModelCard(
    model: HuggingFaceModel,
    isInstalled: Boolean,
    downloadState: HxdState?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
) {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current
    val isActive = downloadState != null &&
        downloadState.status in listOf(HxdStatus.QUEUED, HxdStatus.CONNECTING, HxdStatus.DOWNLOADING)
    val isFailed = downloadState != null && downloadState.status == HxdStatus.FAILED

    Surface(
        shape = shapes.cardSmall,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(dimens.spacingMd)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val typeLabel = when (model.modelType) {
                        "tts" -> "TTS"
                        "stt" -> "STT"
                        else -> "LLM"
                    }
                    val typeColor = when (model.modelType) {
                        "tts" -> MaterialTheme.colorScheme.secondary
                        "stt" -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    }
                    Text(
                        text = typeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = typeColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(
                                typeColor.copy(alpha = 0.12f),
                                RoundedCornerShape(dimens.spacingXs)
                            )
                            .padding(horizontal = 6.dp, vertical = dimens.spacingXxs)
                    )
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                when {
                    isInstalled -> {
                        Icon(
                            TnIcons.CircleCheck, "Installed",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    isActive -> {
                        ActionProgressButton(
                            onClickListener = onCancel,
                            contentDescription = "Cancel"
                        )
                    }
                    isFailed -> {
                        ActionButton(
                            onClickListener = onDownload,
                            icon = TnIcons.Refresh,
                            contentDescription = "Retry"
                        )
                    }
                    else -> {
                        ActionButton(
                            onClickListener = onDownload,
                            icon = TnIcons.Download,
                            contentDescription = "Download"
                        )
                    }
                }
            }

            Spacer(Modifier.height(dimens.spacingXs))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = model.approximateSize,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            RoundedCornerShape(dimens.spacingXs)
                        )
                        .padding(horizontal = 6.dp, vertical = dimens.spacingXxs)
                )

                if (model.quantization.isNotBlank()) {
                    Text(
                        text = model.quantization,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(dimens.spacingXs)
                            )
                            .padding(horizontal = 6.dp, vertical = dimens.spacingXxs)
                    )
                }

                model.tags.take(2).forEach { tag ->
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(dimens.spacingXs)
                            )
                            .padding(horizontal = 5.dp, vertical = dimens.spacingXxs)
                    )
                }
            }

            AnimatedVisibility(
                visible = isActive,
                enter = Motion.Enter,
                exit = Motion.Exit
            ) {
                if (downloadState != null) {
                    DownloadProgress(downloadState)
                }
            }

            AnimatedVisibility(visible = isFailed) {
                Text(
                    text = "Download failed: ${downloadState?.error ?: "Unknown error"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = dimens.spacingXs)
                )
            }
        }
    }
}

@Composable
private fun DownloadProgress(state: HxdState) {
    val dimens = LocalDimens.current
    Column(modifier = Modifier.padding(top = dimens.spacingSm)) {
        val progress = state.progress
        if (progress >= 0f) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val statusText = when (state.status) {
                HxdStatus.QUEUED -> "Queued"
                HxdStatus.CONNECTING -> "Connecting..."
                HxdStatus.DOWNLOADING -> {
                    val dl = formatBytes(state.downloadedBytes)
                    val total = if (state.totalBytes > 0) " / ${formatBytes(state.totalBytes)}" else ""
                    "$dl$total"
                }
                else -> ""
            }
            Text(statusText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            if (state.speedFormatted.isNotBlank()) {
                Text(state.speedFormatted, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
