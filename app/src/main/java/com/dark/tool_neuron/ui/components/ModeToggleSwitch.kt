package com.dark.tool_neuron.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.ui.theme.rDp

@Composable
fun ModeToggleSwitch(
    isImageMode: Boolean,
    onModeChange: (Boolean) -> Unit,
    textModelLoaded: Boolean,
    imageModelLoaded: Boolean,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        animationSpec = tween(300),
        label = "background"
    )

    val thumbOffset by animateDpAsState(
        targetValue = if (isImageMode) rDp(32.dp) else rDp(0.dp),
        animationSpec = tween(300),
        label = "thumbOffset"
    )

    Surface(
        modifier = modifier
            .width(rDp(68.dp))
            .height(rDp(36.dp)),
        shape = RoundedCornerShape(rDp(6.dp)),
        color = backgroundColor
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Thumb (sliding indicator)
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset + rDp(2.dp))
                    .align(Alignment.CenterStart)
                    .size(rDp(32.dp), rDp(32.dp))
                    .padding(rDp(2.dp))
                    .clip(RoundedCornerShape(rDp(4.dp)))
                    .background(MaterialTheme.colorScheme.primary)
            )

            // Icons row
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = rDp(4.dp)),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Text icon
                Box(
                    modifier = Modifier
                        .size(rDp(32.dp))
                        .clip(RoundedCornerShape(rDp(4.dp)))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            enabled = textModelLoaded
                        ) {
                            if (!isImageMode) return@clickable
                            onModeChange(false)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.TextFields,
                        contentDescription = "Text mode",
                        tint = if (!isImageMode) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(
                                alpha = if (textModelLoaded) 0.6f else 0.3f
                            )
                        },
                        modifier = Modifier.size(rDp(18.dp))
                    )
                }

                // Image icon
                Box(
                    modifier = Modifier
                        .size(rDp(32.dp))
                        .clip(RoundedCornerShape(rDp(4.dp)))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            enabled = imageModelLoaded
                        ) {
                            if (isImageMode) return@clickable
                            onModeChange(true)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = "Image mode",
                        tint = if (isImageMode) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(
                                alpha = if (imageModelLoaded) 0.6f else 0.3f
                            )
                        },
                        modifier = Modifier.size(rDp(18.dp))
                    )
                }
            }
        }
    }
}