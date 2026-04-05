package com.dark.tool_neuron.ui.components.action_window

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.ui.theme.Motion

// ── Pill (lives in TopBar) ──────────────────────────────────────────────────

@Composable
fun ActionWindowPill(
    modelName: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current

    Surface(
        shape = tnShapes.full,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        modifier = Modifier.clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
            onClick = onToggle
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = dimens.spacingMd, vertical = dimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs)
        ) {
            Icon(
                imageVector = TnIcons.Leaf,
                contentDescription = null,
                modifier = Modifier.size(dimens.iconSm),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = modelName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = if (expanded) TnIcons.ChevronUp else TnIcons.ChevronDown,
                contentDescription = null,
                modifier = Modifier.size(dimens.iconSm),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Overlay card (lives in body Box) ───────────────────────────────────────

@Composable
fun ActionWindowOverlay(
    modelName: String,
    expanded: Boolean,
    onDismiss: () -> Unit,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current

    if (expanded) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss
                )
        )
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(Motion.state()) + expandVertically(
                animationSpec = Motion.content(),
                expandFrom = Alignment.Top
            ),
            exit = fadeOut(Motion.state()) + shrinkVertically(
                animationSpec = Motion.content(),
                shrinkTowards = Alignment.Top
            )
        ) {
            Surface(
                shape = tnShapes.xl,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = dimens.cardElevation,
                shadowElevation = dimens.cardElevation,
                modifier = Modifier
                    .width(dimens.spacingXxxl * 5)
                    .padding(top = dimens.spacingXs)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(dimens.spacingLg),
                    verticalArrangement = Arrangement.spacedBy(dimens.spacingMd)
                ) {
                    Text(
                        text = modelName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    PlaceholderRow("Status", "Ready")
                    PlaceholderRow("Backend", "CPU")
                    PlaceholderRow("Context", "4096 tokens")
                    PlaceholderRow("Speed", "-- tok/s")
                }
            }
        }
    }
}

@Composable
private fun PlaceholderRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
