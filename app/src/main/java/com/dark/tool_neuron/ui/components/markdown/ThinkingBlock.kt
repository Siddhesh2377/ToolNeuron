package com.dark.tool_neuron.ui.components.markdown

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import com.dark.tool_neuron.ui.components.ExpandCollapseIcon
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.ui.theme.Motion

@Composable
fun ThinkingBlock(
    text: String,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current

    var manuallyToggled by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(isStreaming) }

    LaunchedEffect(isStreaming) {
        if (!manuallyToggled) expanded = isStreaming
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = tnShapes.md,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
    ) {
        Column(modifier = Modifier.padding(dimens.spacingSm)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        manuallyToggled = true
                        expanded = !expanded
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
            ) {
                ThinkingPulseIcon(active = isStreaming)
                Text(
                    text = if (isStreaming) "Thinking…" else "Reasoning",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                ExpandCollapseIcon(
                    isExpanded = expanded,
                    size = dimens.iconSm,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(Motion.state()) + expandVertically(Motion.content()),
                exit = fadeOut(Motion.state()) + shrinkVertically(Motion.content()),
            ) {
                MarkdownText(
                    text = text,
                    modifier = Modifier.padding(top = dimens.spacingXs),
                )
            }
        }
    }
}

@Composable
private fun ThinkingPulseIcon(active: Boolean) {
    val dimens = LocalDimens.current
    val transition = rememberInfiniteTransition(label = "thinkPulse")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "thinkPulseAlpha",
    )
    val staticAlpha = 0.7f
    Icon(
        imageVector = TnIcons.Sparkles,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .size(dimens.iconSm)
            .alpha(if (active) pulseAlpha else staticAlpha),
    )
}
