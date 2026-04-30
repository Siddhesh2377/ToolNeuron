package com.dark.tool_neuron.ui.screens.research

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.model.IterationStep
import com.dark.tool_neuron.model.ResearchUiState
import com.dark.tool_neuron.model.ResearchUrlEntry
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.ui.theme.Motion

@Composable
fun ResearchCard(
    question: String,
    state: ResearchUiState,
    onOpenDocument: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    val cardState = ResearchCardState.fromUiState(state)
    var expanded by remember { mutableStateOf(false) }
    val historyAvailable = state.history.isNotEmpty()

    Surface(
        shape = tnShapes.lg,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            .compositeOver(MaterialTheme.colorScheme.surface),
        tonalElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = dimens.spacingXs),
    ) {
        Column(modifier = Modifier.padding(dimens.spacingMd)) {
            ResearchHeader(question = question)
            Spacer(Modifier.size(dimens.spacingSm))
            AnimatedContent(
                targetState = cardState,
                transitionSpec = { fadeIn(Motion.state()) togetherWith fadeOut(Motion.state()) },
                label = "research_state",
            ) { current ->
                when (current) {
                    is ResearchCardState.Plan -> StatusRow(TnIcons.Compass, "Planning research…")
                    is ResearchCardState.Search -> SearchRow(current)
                    is ResearchCardState.Fetch -> FetchRow(current)
                    is ResearchCardState.Compress -> CompressRow(current)
                    is ResearchCardState.QuestionGen -> QuestionGenRow(current)
                    is ResearchCardState.Final -> StatusRow(TnIcons.Sparkles, "Writing document…")
                    is ResearchCardState.Stopping -> StatusRow(TnIcons.PlayerStop, "Stopping research…")
                    is ResearchCardState.Done -> DoneRow(current, onOpenDocument)
                    is ResearchCardState.Cancelled -> ErrorRow(TnIcons.X, current.reason, isError = false)
                    is ResearchCardState.Failed -> ErrorRow(TnIcons.AlertTriangle, current.message, isError = true)
                }
            }

            AnimatedVisibility(visible = state.isInFlight()) {
                Column {
                    Spacer(Modifier.size(dimens.spacingSm))
                    InFlightFooter(
                        iteration = state.iteration,
                        maxIterations = state.maxIterations,
                        onCancel = onCancel,
                    )
                }
            }

            AnimatedVisibility(visible = historyAvailable) {
                Column {
                    Spacer(Modifier.size(dimens.spacingSm))
                    HistoryToggle(
                        expanded = expanded,
                        iterationCount = state.history.size,
                        onClick = { expanded = !expanded },
                    )
                    AnimatedVisibility(visible = expanded) {
                        Column {
                            Spacer(Modifier.size(dimens.spacingXs))
                            state.history.sortedBy { it.iteration }.forEach { step ->
                                IterationStepCard(step)
                                Spacer(Modifier.size(dimens.spacingXs))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryToggle(
    expanded: Boolean,
    iterationCount: Int,
    onClick: () -> Unit,
) {
    val dimens = LocalDimens.current
    val rotation by animateDpAsState(
        targetValue = if (expanded) 180.dp else 0.dp,
        label = "chevron_rotation",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = dimens.spacingXs),
    ) {
        Text(
            text = if (expanded) "Hide iterations" else "Show iterations ($iterationCount)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = TnIcons.ChevronDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(dimens.iconSm)
                .rotate(rotation.value),
        )
    }
}

@Composable
private fun IterationStepCard(step: IterationStep) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    Surface(
        shape = tnShapes.md,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(dimens.spacingMd)) {
            Text(
                "Iteration ${step.iteration}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            if (step.query.isNotBlank()) {
                Spacer(Modifier.size(dimens.spacingXs))
                StepLine(TnIcons.Search, "Search")
                Spacer(Modifier.size(dimens.spacingXxs))
                Text(
                    "“${step.query}”",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (step.resultCount > 0) {
                    Text(
                        "${step.resultCount} results",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (step.urls.isNotEmpty()) {
                Spacer(Modifier.size(dimens.spacingSm))
                StepLine(TnIcons.Globe, "Fetched ${step.urls.count { it.ok == true }}/${step.urls.size}")
                Spacer(Modifier.size(dimens.spacingXxs))
                step.urls.take(MAX_URLS_PER_ITER).forEach { entry ->
                    UrlLine(entry)
                }
                if (step.urls.size > MAX_URLS_PER_ITER) {
                    Text(
                        "+${step.urls.size - MAX_URLS_PER_ITER} more",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (step.compressedBytes > 0L || step.rawBytes > 0L) {
                Spacer(Modifier.size(dimens.spacingSm))
                StepLine(TnIcons.Zap, "Compressed")
                Spacer(Modifier.size(dimens.spacingXxs))
                Text(
                    "${formatStepBytes(step.rawBytes)} → ${formatStepBytes(step.compressedBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (step.questions.isNotEmpty()) {
                Spacer(Modifier.size(dimens.spacingSm))
                StepLine(TnIcons.MessageCircle, "Follow-up questions")
                Spacer(Modifier.size(dimens.spacingXxs))
                step.questions.forEach { q ->
                    Text(
                        "• $q",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StepLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
) {
    val dimens = LocalDimens.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(dimens.iconSm),
        )
        Spacer(Modifier.width(dimens.spacingXs))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun UrlLine(entry: ResearchUrlEntry) {
    val dimens = LocalDimens.current
    val tint = when (entry.ok) {
        true -> MaterialTheme.colorScheme.primary
        false -> MaterialTheme.colorScheme.error
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 1.dp),
    ) {
        Icon(
            imageVector = when (entry.ok) {
                true -> TnIcons.Check
                false -> TnIcons.X
                null -> TnIcons.Circle
            },
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(dimens.spacingXs))
        Text(
            text = entry.url,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatStepBytes(value: Long): String {
    if (value < 1024) return "${value}B"
    if (value < 1024 * 1024) return "${value / 1024}KB"
    return "${value / (1024 * 1024)}MB"
}

private const val MAX_URLS_PER_ITER = 8

@Composable
private fun InFlightFooter(
    iteration: Int,
    maxIterations: Int,
    onCancel: () -> Unit,
) {
    val dimens = LocalDimens.current
    val pct by animateFloatAsState(
        targetValue = if (maxIterations > 0) {
            (iteration.toFloat() / maxIterations.toFloat()).coerceIn(0f, 1f)
        } else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "iter_pct",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (maxIterations > 0) {
            Text(
                "Iteration $iteration/$maxIterations",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(dimens.spacingSm))
            LinearProgressIndicator(
                progress = { pct },
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp),
            )
            Spacer(Modifier.width(dimens.spacingSm))
        } else {
            LinearProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp),
            )
            Spacer(Modifier.width(dimens.spacingSm))
        }
        TextButton(onClick = onCancel) {
            Icon(
                imageVector = TnIcons.PlayerStop,
                contentDescription = null,
                modifier = Modifier.size(dimens.iconSm),
            )
            Spacer(Modifier.size(4.dp))
            Text("Stop", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ResearchHeader(question: String) {
    val dimens = LocalDimens.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
            modifier = Modifier.size(28.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = TnIcons.Sparkles,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.width(dimens.spacingSm))
        Column {
            Text(
                text = "Research",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = question,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun StatusRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    val dimens = LocalDimens.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(dimens.iconSm),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(dimens.spacingSm))
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(dimens.iconSm),
        )
        Spacer(Modifier.width(dimens.spacingXs))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}
