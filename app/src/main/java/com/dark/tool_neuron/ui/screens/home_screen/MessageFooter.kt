package com.dark.tool_neuron.ui.screens.home_screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.model.ChatMessage
import com.dark.tool_neuron.model.MemoryMetrics
import com.dark.tool_neuron.model.MessageKind
import com.dark.tool_neuron.model.TextMetrics
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.ui.theme.Motion

@Composable
fun MessageFooter(
    message: ChatMessage,
    modifier: Modifier = Modifier,
) {
    val text = message.textMetrics
    val memory = message.memoryMetrics
    if (text == null && memory == null && message.kind == MessageKind.Text) return

    val dimens = LocalDimens.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingXs),
    ) {
        text?.let {
            MetricsPill(
                icon = TnIcons.Zap,
                accent = MaterialTheme.colorScheme.primary,
                summary = textSummary(it),
                details = textDetails(it),
            )
        }
        memory?.let {
            MetricsPill(
                icon = TnIcons.Database,
                accent = MaterialTheme.colorScheme.tertiary,
                summary = memorySummary(it),
                details = memoryDetails(it),
            )
        }
    }
}

@Composable
private fun MetricsPill(
    icon: ImageVector,
    accent: Color,
    summary: String,
    details: List<Pair<String, String>>,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    var expanded by remember { mutableStateOf(false) }

    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = Motion.state(),
        label = "chevRotate",
    )

    Surface(
        shape = if (expanded) tnShapes.lg else tnShapes.actionIcon,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier
            .animateContentSize(animationSpec = Motion.content())
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { expanded = !expanded },
            ),
    ) {
        Column(
            modifier = Modifier.padding(
                dimens.spacingSm
            ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = accent,
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Icon(
                    imageVector = TnIcons.ChevronDown,
                    contentDescription = null,
                    modifier = Modifier
                        .size(12.dp)
                        .rotate(rotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(Motion.state()) + expandVertically(Motion.content()),
                exit = fadeOut(Motion.state()) + shrinkVertically(Motion.content()),
            ) {
                Column(
                    modifier = Modifier.padding(top = dimens.spacingXs),
                    verticalArrangement = Arrangement.spacedBy(dimens.spacingXxs),
                ) {
                    HorizontalDivider(
                        color = accent.copy(alpha = 0.15f),
                        thickness = 0.5.dp,
                    )
                    details.forEach { (label, value) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = value,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun textSummary(m: TextMetrics): String {
    val tps = "%.1f t/s".format(m.tokensPerSecond)
    val tokens = "${m.generatedTokens} tok"
    return "$tps  •  $tokens"
}

private fun textDetails(m: TextMetrics): List<Pair<String, String>> = buildList {
    add("Prompt tokens" to m.promptTokens.toString())
    add("Generated tokens" to m.generatedTokens.toString())
    add("Speed" to "%.2f t/s".format(m.tokensPerSecond))
    if (m.timeToFirstTokenMs > 0) add("TTFT" to "${m.timeToFirstTokenMs} ms")
    if (m.totalTimeMs > 0) add("Total" to formatDuration(m.totalTimeMs))
}

private fun memorySummary(m: MemoryMetrics): String {
    val peak = "%.0f MB".format(m.peakMemoryMB)
    return "Memory  •  $peak"
}

private fun memoryDetails(m: MemoryMetrics): List<Pair<String, String>> = buildList {
    if (m.modelSizeMB > 0) add("Model" to "%.0f MB".format(m.modelSizeMB))
    if (m.contextSizeMB > 0) add("Context" to "%.0f MB".format(m.contextSizeMB))
    if (m.peakMemoryMB > 0) add("Peak" to "%.0f MB".format(m.peakMemoryMB))
    if (m.usagePercent > 0) add("Usage" to "%.1f%%".format(m.usagePercent))
}

private fun formatDuration(ms: Long): String =
    if (ms < 1000) "$ms ms" else "%.2f s".format(ms / 1000.0)
