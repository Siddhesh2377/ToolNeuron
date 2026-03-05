package com.dark.tool_neuron.plugins.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.models.plugins.ToolState
import com.dark.tool_neuron.ui.theme.ManropeFontFamily
import com.dark.tool_neuron.ui.icons.TnIcons

/**
 * Animated card component for displaying tool execution state
 */
@Composable
fun ToolCard(
    title: String,
    icon: ImageVector,
    state: ToolState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {}
) {
    val containerColor by animateColorAsState(
        targetValue = when (state) {
            is ToolState.Idle -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            is ToolState.InProgress -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            is ToolState.Success -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
            is ToolState.Error -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "containerColor"
    )

    val borderColor by animateColorAsState(
        targetValue = when (state) {
            is ToolState.Idle -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            is ToolState.InProgress -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            is ToolState.Success -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            is ToolState.Error -> MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
        },
        animationSpec = tween(300),
        label = "borderColor"
    )

    val scale by animateFloatAsState(
        targetValue = when (state) {
            is ToolState.InProgress -> 1.02f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // State indicator
                ToolStateIndicator(state = state)
            }

            // State message
            AnimatedVisibility(
                visible = state !is ToolState.Idle,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                ToolStateMessage(state = state)
            }

            // Content
            AnimatedVisibility(
                visible = state is ToolState.Success,
                enter = slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                content()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ToolStateIndicator(state: ToolState) {
    AnimatedContent(
        targetState = state,
        label = "stateIndicator"
    ) { targetState ->
        when (targetState) {
            is ToolState.Idle -> Icon(
                imageVector = TnIcons.Clock,
                contentDescription = "Idle",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )

            is ToolState.InProgress -> LoadingIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.primary
            )

            is ToolState.Success -> Icon(
                imageVector = TnIcons.CircleCheck,
                contentDescription = "Success",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )

            is ToolState.Error -> Icon(
                imageVector = TnIcons.AlertTriangle,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ToolStateMessage(state: ToolState) {
    val (message, color) = when (state) {
        is ToolState.InProgress -> state.message to MaterialTheme.colorScheme.primary
        is ToolState.Success -> state.message to MaterialTheme.colorScheme.primary
        is ToolState.Error -> state.message to MaterialTheme.colorScheme.error
        is ToolState.Idle -> "" to Color.Transparent
    }

    if (message.isNotEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.1f))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = ManropeFontFamily,
                color = color,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
