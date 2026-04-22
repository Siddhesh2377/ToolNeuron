package com.dark.tool_neuron.ui.screens.home_screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.ui.theme.Motion

@Composable
internal fun PlusMenuCard(
    thinkingEnabled: Boolean,
    showThinking: Boolean,
    documentCount: Int,
    onThinkingToggle: () -> Unit,
    onDocumentsClick: () -> Unit,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current

    Surface(
        shape = tnShapes.xl,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.padding(bottom = dimens.spacingSm),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.spacingSm),
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            PlusMenuItem(
                modifier = Modifier.weight(0.5f),
                icon = TnIcons.BookOpen,
                label = if (documentCount > 0) "Documents ($documentCount)" else "Documents",
                isToggled = documentCount > 0,
                onClick = onDocumentsClick,
            )
            if (showThinking) {
                PlusMenuItem(
                    modifier = Modifier.weight(0.5f),
                    icon = TnIcons.Sparkles,
                    label = "Thinking",
                    isToggled = thinkingEnabled,
                    onClick = onThinkingToggle,
                )
            } else {
                Spacer(modifier = Modifier.weight(0.5f))
            }
        }
    }
}

@Composable
private fun PlusMenuItem(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    isToggled: Boolean,
    onClick: () -> Unit,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current

    val bgColor by animateColorAsState(
        targetValue = if (isToggled)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        animationSpec = Motion.state(),
        label = "plusItemBg",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isToggled)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.onSurface,
        animationSpec = Motion.state(),
        label = "plusItemContent",
    )

    Surface(
        shape = tnShapes.md,
        color = bgColor,
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = dimens.spacingMd,
                vertical = dimens.spacingSm,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(dimens.iconMd),
                tint = contentColor,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = contentColor,
                modifier = Modifier.weight(1f),
            )
            if (isToggled) {
                Icon(
                    imageVector = TnIcons.Check,
                    contentDescription = null,
                    modifier = Modifier.size(dimens.iconSm),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
