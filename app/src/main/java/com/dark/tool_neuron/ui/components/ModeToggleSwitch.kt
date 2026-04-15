package com.dark.tool_neuron.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import com.dark.tool_neuron.ui.theme.Motion
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.global.Standards
import com.dark.tool_neuron.ui.icons.TnIcons

@Composable
fun ModeToggleSwitch(
    isImageMode: Boolean,
    onModeChange: (Boolean) -> Unit,
    textModelLoaded: Boolean,
    imageModelLoaded: Boolean,
    modifier: Modifier = Modifier
) {
    // Calculate total width: 2 icons + spacing between + padding on sides
    val totalWidth = (Standards.ActionIconSize * 2) + Standards.ActionIconSpace + 4.dp
    val totalHeight = Standards.ActionIconSize + 4.dp

    val backgroundColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        animationSpec = Motion.state(),
        label = "background"
    )

    val thumbOffset by animateDpAsState(
        targetValue = if (isImageMode) {
            Standards.ActionIconSize + Standards.ActionIconSpace
        } else {
            0.dp
        },
        animationSpec = Motion.content(),
        label = "thumbOffset"
    )

    Surface(
        modifier = modifier
            .width(totalWidth)
            .height(totalHeight),
        shape = RoundedCornerShape(Standards.ActionIconRoundedSize),
        color = backgroundColor
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Thumb (sliding indicator)
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset + 2.dp)
                    .align(Alignment.CenterStart)
                    .size(Standards.ActionIconSize)
                    .padding(Standards.SpacingXxs)
                    .clip(RoundedCornerShape(Standards.ActionIconRoundedSize - 2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )

            // Icons row
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Standards.SpacingXxs),
                horizontalArrangement = Arrangement.spacedBy(Standards.ActionIconSpace),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Text icon
                IconButton(
                    isSelected = !isImageMode,
                    isEnabled = textModelLoaded,
                    icon = TnIcons.Code,
                    contentDescription = if (textModelLoaded) "Switch to text chat" else "Load a text model to enable chat",
                    onClick = {
                        if (!isImageMode) return@IconButton
                        onModeChange(false)
                    }
                )

                // Image icon
                IconButton(
                    isSelected = isImageMode,
                    isEnabled = imageModelLoaded,
                    icon = TnIcons.Photo,
                    contentDescription = if (imageModelLoaded) "Switch to image generation" else "Load an image model to enable",
                    onClick = {
                        if (isImageMode) return@IconButton
                        onModeChange(true)
                    }
                )
            }
        }
    }
}

@Composable
private fun IconButton(
    isSelected: Boolean,
    isEnabled: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    val iconTint by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.onPrimary
            isEnabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        },
        animationSpec = Motion.state(),
        label = "iconTint"
    )

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.85f,
        animationSpec = Motion.interactive(),
        label = "iconScale"
    )

    Box(
        modifier = Modifier
            .size(Standards.ActionIconSize)
            .clip(RoundedCornerShape(Standards.ActionIconRoundedSize - 2.dp))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                enabled = isEnabled
            ) {
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier
                .padding(Standards.SpacingXxs)
                .scale(scale)
        )
    }
}