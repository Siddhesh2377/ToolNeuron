package com.dark.tool_neuron.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.ui.theme.Motion

@Composable
fun PinDotRow(length: Int, dots: Int = 6) {
    val fixedDots = dots.coerceAtLeast(1)
    val clamped = length.coerceIn(0, fixedDots)
    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        repeat(fixedDots) { i ->
            val filled = i < clamped
            val scale by animateFloatAsState(
                targetValue = if (filled) 1f else 0.6f,
                animationSpec = Motion.interactive(),
                label = "dotScale$i"
            )
            val color by animateColorAsState(
                targetValue = if (filled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                animationSpec = Motion.state(),
                label = "dotColor$i"
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .scale(scale)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
fun PinNumberPad(
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    enabled: Boolean = true
) {
    val dimens = LocalDimens.current
    val rows = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(dimens.padButtonGap),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.padButtonGap)) {
                row.forEach { digit ->
                    PadButton(onClick = { onDigit(digit) }, enabled = enabled) {
                        Text(
                            text = digit.toString(),
                            fontSize = dimens.padFontSize.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.padButtonGap)) {
            Spacer(Modifier.size(dimens.padButtonSize))
            PadButton(onClick = { onDigit('0') }, enabled = enabled) {
                Text(
                    text = "0",
                    fontSize = dimens.padFontSize.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            PadButton(onClick = onDelete, enabled = enabled) {
                Icon(
                    imageVector = TnIcons.Backspace,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PinActionRow(
    actions: List<PinAction>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        actions.forEach { action ->
            val color = if (action.primary) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant

            Text(
                text = action.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (action.primary) FontWeight.SemiBold else FontWeight.Normal,
                color = if (action.enabled) color else color.copy(alpha = 0.38f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(enabled = action.enabled, onClick = action.onClick)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            )
        }
    }
}

data class PinAction(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val primary: Boolean = false,
)

@Composable
private fun PadButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = Motion.interactive(),
        label = "padPress"
    )

    Surface(
        modifier = Modifier
            .size(dimens.padButtonSize)
            .scale(scale)
            .clip(tnShapes.lg)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = tnShapes.lg
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}
