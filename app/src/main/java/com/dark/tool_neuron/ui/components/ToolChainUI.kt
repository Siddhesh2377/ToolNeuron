package com.dark.tool_neuron.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.R
import com.dark.tool_neuron.models.messages.ToolChainStepData
import com.dark.tool_neuron.ui.theme.rDp

/**
 * Displays a sequential tool chain with animated connectors.
 * Used during streaming (live steps) and in persisted messages.
 */
@Composable
fun ToolChainDisplay(
    steps: List<ToolChainStepData>,
    currentRound: Int = 0,
    maxRounds: Int = 5,
    isLive: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (steps.isEmpty() && !isLive) return

    var isExpanded by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = rDp(12.dp))
    ) {
        // Header
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            shape = RoundedCornerShape(rDp(8.dp)),
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
            tonalElevation = rDp(1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rDp(10.dp), vertical = rDp(8.dp)),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(rDp(8.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.tool),
                        contentDescription = null,
                        modifier = Modifier.size(rDp(14.dp)),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = "Tool Chain",
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
                        text = "${steps.size} step${if (steps.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isLive && currentRound > 0) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Text(
                            text = "Round $currentRound/$maxRounds",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.size(rDp(18.dp)),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Steps
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeIn(),
            exit = shrinkVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeOut()
        ) {
            Column(
                modifier = Modifier.padding(top = rDp(4.dp))
            ) {
                steps.forEachIndexed { index, step ->
                    ToolChainStep(
                        step = step,
                        isLast = index == steps.size - 1 && !isLive
                    )

                    // Connector line between steps
                    if (index < steps.size - 1 || isLive) {
                        ToolChainConnector(isAnimated = isLive && index == steps.size - 1)
                    }
                }

                // Show loading indicator for next step if live
                if (isLive && currentRound > 0) {
                    ToolChainLoadingStep()
                }
            }
        }
    }
}

@Composable
private fun ToolChainStep(
    step: ToolChainStepData,
    isLast: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(rDp(8.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDp(10.dp)),
            horizontalArrangement = Arrangement.spacedBy(rDp(8.dp)),
            verticalAlignment = Alignment.Top
        ) {
            // Step number with status icon
            Box(
                modifier = Modifier
                    .size(rDp(24.dp))
                    .background(
                        color = if (step.success) {
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                        } else {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (step.success) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(rDp(14.dp)),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        modifier = Modifier.size(rDp(14.dp)),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(rDp(4.dp))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = step.toolName,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${step.executionTimeMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Text(
                    text = step.pluginName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

                // Result preview
                if (step.result.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(rDp(4.dp)),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = step.result.take(150) + if (step.result.length > 150) "..." else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(rDp(6.dp)),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolChainConnector(isAnimated: Boolean = false) {
    Box(
        modifier = Modifier
            .padding(start = rDp(23.dp))
            .width(rDp(2.dp))
            .height(rDp(16.dp))
            .background(
                color = if (isAnimated) {
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                }
            )
    )
}

@Composable
private fun ToolChainLoadingStep() {
    val infiniteTransition = rememberInfiniteTransition(label = "tool_chain_loading")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(rDp(8.dp)),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDp(10.dp)),
            horizontalArrangement = Arrangement.spacedBy(rDp(8.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.tool),
                contentDescription = null,
                modifier = Modifier
                    .size(rDp(20.dp))
                    .rotate(rotation),
                tint = MaterialTheme.colorScheme.tertiary
            )
            Text(
                text = "Generating next step...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
