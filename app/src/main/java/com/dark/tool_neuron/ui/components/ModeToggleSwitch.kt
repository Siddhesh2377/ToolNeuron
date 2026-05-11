package com.dark.tool_neuron.ui.components

import android.annotation.SuppressLint
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.DefaultTnShapes
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.ui.theme.Motion

@SuppressLint("UseOfNonLambdaOffsetOverload")
@Composable
fun ModeToggleSwitch(
    isImageMode: Boolean,
    onModeChange: (Boolean) -> Unit,
    textModelLoaded: Boolean,
    imageModelLoaded: Boolean,
    modifier: Modifier = Modifier
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current

    val totalWidth = (dimens.actionIconSize * 2) + dimens.actionIconSpace + 4.dp
    val totalHeight = dimens.actionIconSize + 4.dp

    val backgroundColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        animationSpec = Motion.state(),
        label = "bg"
    )

    val thumbOffset by animateDpAsState(
        targetValue = if (isImageMode) dimens.actionIconSize + dimens.actionIconSpace else 0.dp,
        animationSpec = Motion.content(),
        label = "thumb"
    )

    Surface(
        modifier = modifier.width(totalWidth).height(totalHeight),
        shape = tnShapes.card,
        color = backgroundColor
    ) {
        Box(Modifier.fillMaxSize()) {
            // Sliding thumb
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset + 2.dp)
                    .align(Alignment.CenterStart)
                    .size(dimens.actionIconSize)
                    .padding(dimens.spacingXxs)
                    .clip(tnShapes.actionIcon)
                    .background(MaterialTheme.colorScheme.primary)
            )

            // Icons row
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(dimens.spacingXxs),
                horizontalArrangement = Arrangement.spacedBy(dimens.actionIconSpace),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModeIcon(
                    isSelected = !isImageMode,
                    isEnabled = textModelLoaded,
                    icon = TnIcons.TextSize,
                    contentDescription = if (textModelLoaded) "Text chat" else "Load a text model",
                    onClick = { if (isImageMode) onModeChange(false) }
                )
                ModeIcon(
                    isSelected = isImageMode,
                    isEnabled = imageModelLoaded,
                    icon = TnIcons.Photo,
                    contentDescription = if (imageModelLoaded) "Image generation" else "Load an image model",
                    onClick = { if (!isImageMode) onModeChange(true) }
                )
            }
        }
    }
}

@Composable
private fun ModeIcon(
    isSelected: Boolean,
    isEnabled: Boolean,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current

    val tint by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.onPrimary
            isEnabled  -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            else       -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        },
        animationSpec = Motion.state(),
        label = "tint"
    )

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.85f,
        animationSpec = Motion.interactive(),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .size(dimens.actionIconSize)
            .clip(tnShapes.actionIcon)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                enabled = isEnabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier
                .padding(dimens.spacingXs)
                .scale(scale)
        )
    }
}
