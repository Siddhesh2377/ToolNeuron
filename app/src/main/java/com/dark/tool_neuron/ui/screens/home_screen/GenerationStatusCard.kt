package com.dark.tool_neuron.ui.screens.home_screen

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.viewmodel.home_vm.GenerationStatus

@Composable
fun GenerationStatusBubble(
    status: GenerationStatus,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalDimens.current
    val view = statusView(status) ?: return

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        verticalAlignment = Alignment.Top,
    ) {
        StatusLeading(leading = view.leading, tint = view.tint)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 2.dp),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingXxs),
        ) {
            Text(
                text = view.label,
                style = MaterialTheme.typography.bodyMedium,
                color = view.tint,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            view.subtitle?.takeIf { it.isNotBlank() }?.let { sub ->
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private sealed interface Leading {
    data object Dot : Leading
    data object Spinner : Leading
    data class AnimatedVector(val icon: ImageVector, val anim: Anim) : Leading
    data class StaticVector(val icon: ImageVector) : Leading
}

private enum class Anim { Rotate, Pulse }

private data class StatusView(
    val leading: Leading,
    val label: String,
    val subtitle: String?,
    val tint: Color,
)

@Composable
private fun statusView(status: GenerationStatus): StatusView? = when (status) {
    GenerationStatus.Hidden,
    is GenerationStatus.GeneratingText,
    is GenerationStatus.Thinking -> null

    GenerationStatus.Welcome -> StatusView(
        leading = Leading.Dot,
        label = "Load a model to start chatting",
        subtitle = null,
        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
    )

    GenerationStatus.NoModelLoaded -> StatusView(
        leading = Leading.Dot,
        label = "No model loaded",
        subtitle = "Pick one from Models to continue",
        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
    )

    is GenerationStatus.ModelLoading -> StatusView(
        leading = Leading.Spinner,
        label = "Loading ${status.modelName}",
        subtitle = null,
        tint = MaterialTheme.colorScheme.primary,
    )

    is GenerationStatus.ExecutingTool -> StatusView(
        leading = Leading.AnimatedVector(TnIcons.Wrench, Anim.Rotate),
        label = "Calling ${status.toolName}",
        subtitle = status.pluginName.takeIf { it.isNotBlank() },
        tint = MaterialTheme.colorScheme.tertiary,
    )

    is GenerationStatus.ToolComplete -> StatusView(
        leading = Leading.StaticVector(
            if (status.success) TnIcons.CircleCheck else TnIcons.AlertTriangle
        ),
        label = if (status.success) "${status.toolName} done in ${status.elapsedMs}ms"
        else "${status.toolName} failed",
        subtitle = if (!status.success) status.errorMessage else null,
        tint = if (status.success) MaterialTheme.colorScheme.tertiary
        else MaterialTheme.colorScheme.error,
    )

    is GenerationStatus.Error -> StatusView(
        leading = Leading.StaticVector(TnIcons.AlertTriangle),
        label = "Error",
        subtitle = status.message,
        tint = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun StatusLeading(leading: Leading, tint: Color) {
    when (leading) {
        Leading.Dot -> Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .background(tint, CircleShape)
        )

        Leading.Spinner -> Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                color = tint,
                strokeWidth = 1.5.dp,
                trackColor = tint.copy(alpha = 0.25f),
            )
        }

        is Leading.AnimatedVector -> AnimatedLeadingIcon(leading.icon, leading.anim, tint)

        is Leading.StaticVector -> Icon(
            imageVector = leading.icon,
            contentDescription = null,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(14.dp),
            tint = tint,
        )
    }
}

@Composable
private fun AnimatedLeadingIcon(icon: ImageVector, anim: Anim, tint: Color) {
    val transition = rememberInfiniteTransition(label = "statusLeading")
    when (anim) {
        Anim.Rotate -> {
            val rotation by transition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "statusRotate",
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(14.dp)
                    .rotate(rotation),
                tint = tint,
            )
        }
        Anim.Pulse -> {
            val scale by transition.animateFloat(
                initialValue = 1f,
                targetValue = 1.15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "statusPulse",
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(14.dp)
                    .scale(scale),
                tint = tint,
            )
        }
    }
}
