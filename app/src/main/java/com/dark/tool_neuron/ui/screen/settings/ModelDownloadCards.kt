package com.dark.tool_neuron.ui.screen.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.global.Standards
import com.dark.tool_neuron.service.ModelDownloadService
import com.dark.tool_neuron.ui.components.CaptionText
import com.dark.tool_neuron.ui.components.StandardCard
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.Motion

// ── Reusable Download Card ──

@Composable
internal fun ModelDownloadCard(
    title: String,
    description: String,
    downloadState: ModelDownloadService.DownloadState?,
    onDownload: () -> Unit,
    successText: String = "Downloaded"
) {
    StandardCard(title = title) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(Motion.content()),
            verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
        ) {
            description.split("\n").forEach { line ->
                CaptionText(text = line)
            }

            when (downloadState) {
                is ModelDownloadService.DownloadState.Downloading -> {
                    val progress = downloadState.progress
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                        Spacer(Modifier.width(Standards.SpacingMd))
                        Text(
                            "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is ModelDownloadService.DownloadState.Extracting,
                is ModelDownloadService.DownloadState.Processing -> {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                }

                is ModelDownloadService.DownloadState.Success -> {
                    CaptionText(text = successText)
                }

                is ModelDownloadService.DownloadState.Error -> {
                    Text(
                        text = downloadState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    FilledTonalButton(onClick = onDownload) {
                        Text("Retry")
                    }
                }

                else -> {
                    FilledTonalButton(onClick = onDownload) {
                        Icon(
                            TnIcons.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(Standards.SpacingSm))
                        Text("Download")
                    }
                }
            }
        }
    }
}
