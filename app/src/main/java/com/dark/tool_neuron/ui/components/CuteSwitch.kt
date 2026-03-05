package com.dark.tool_neuron.ui.components

import android.annotation.SuppressLint
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import com.dark.tool_neuron.ui.theme.Motion

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Cute compact switch component with smooth animations
 */
@SuppressLint("ModifierParameter")
@Composable
fun CuteSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    width: Dp = 44.dp,
    height: Dp = 24.dp,
    thumbSize: Dp = 18.dp,
    checkedTrackColor: Color = MaterialTheme.colorScheme.primary,
    uncheckedTrackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    checkedThumbColor: Color = MaterialTheme.colorScheme.onPrimary,
    uncheckedThumbColor: Color = MaterialTheme.colorScheme.outline,
    thumbIcon: ImageVector? = null,
    thumbIconChecked: ImageVector? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    
    val trackColor by animateColorAsState(
        targetValue = if (checked) checkedTrackColor else uncheckedTrackColor,
        animationSpec = Motion.state(),
        label = "trackColor"
    )

    val thumbColor by animateColorAsState(
        targetValue = if (checked) checkedThumbColor else uncheckedThumbColor,
        animationSpec = Motion.state(),
        label = "thumbColor"
    )

    val thumbOffset by animateDpAsState(
        targetValue = if (checked) width - thumbSize - 3.dp else 3.dp,
        animationSpec = Motion.interactive(),
        label = "thumbOffset"
    )

    val thumbScale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.9f,
        animationSpec = Motion.state(),
        label = "thumbScale"
    )

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(trackColor)
            .clickable(
                interactionSource = interactionSource,

                enabled = enabled,
                role = Role.Switch,
                onClick = { onCheckedChange(!checked) }
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(thumbSize)
                .scale(thumbScale)
                .background(thumbColor, CircleShape)
                .border(1.5.dp, trackColor.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Optional icon in thumb
            val currentIcon = if (checked) thumbIconChecked ?: thumbIcon else thumbIcon
            currentIcon?.let { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = trackColor
                )
            }
        }
    }
}

/**
 * Cute compact switch with drawable resource icon
 */
@SuppressLint("ModifierParameter")
@Composable
fun CuteSwitchResIcon(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    width: Dp = 44.dp,
    height: Dp = 24.dp,
    thumbSize: Dp = 18.dp,
    checkedTrackColor: Color = MaterialTheme.colorScheme.primary,
    uncheckedTrackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    checkedThumbColor: Color = MaterialTheme.colorScheme.onPrimary,
    uncheckedThumbColor: Color = MaterialTheme.colorScheme.outline,
    thumbIcon: Int? = null,
    thumbIconChecked: Int? = null
) {
    val interactionSource = remember { MutableInteractionSource() }

    val trackColor by animateColorAsState(
        targetValue = if (checked) checkedTrackColor else uncheckedTrackColor,
        animationSpec = Motion.state(),
        label = "trackColor"
    )

    val thumbColor by animateColorAsState(
        targetValue = if (checked) checkedThumbColor else uncheckedThumbColor,
        animationSpec = Motion.state(),
        label = "thumbColor"
    )

    val thumbOffset by animateDpAsState(
        targetValue = if (checked) width - thumbSize - 3.dp else 3.dp,
        animationSpec = Motion.interactive(),
        label = "thumbOffset"
    )

    val thumbScale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.9f,
        animationSpec = Motion.state(),
        label = "thumbScale"
    )

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(trackColor)
            .clickable(
                interactionSource = interactionSource,
                
                enabled = enabled,
                role = Role.Switch,
                onClick = { onCheckedChange(!checked) }
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(thumbSize)
                .scale(thumbScale)
                .background(thumbColor, CircleShape)
                .border(1.5.dp, trackColor.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Optional icon in thumb
            val currentIcon = if (checked) thumbIconChecked ?: thumbIcon else thumbIcon
            currentIcon?.let { icon ->
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = trackColor
                )
            }
        }
    }
}

/**
 * Cute compact toggle button (chip-style)
 */
@SuppressLint("ModifierParameter")
@Composable
fun CuteToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    iconChecked: ImageVector? = null,
    checkedBackgroundColor: Color = MaterialTheme.colorScheme.primary,
    uncheckedBackgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    checkedContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    uncheckedContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val interactionSource = remember { MutableInteractionSource() }
    
    val backgroundColor by animateColorAsState(
        targetValue = if (checked) checkedBackgroundColor else uncheckedBackgroundColor,
        animationSpec = Motion.state(),
        label = "backgroundColor"
    )
    
    val contentColor by animateColorAsState(
        targetValue = if (checked) checkedContentColor else uncheckedContentColor,
        animationSpec = Motion.state(),
        label = "contentColor"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.95f,
        animationSpec = Motion.interactive(),
        label = "scale"
    )

    Surface(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interactionSource,
                
                enabled = enabled,
                role = Role.Checkbox,
                onClick = { onCheckedChange(!checked) }
            ),
        color = backgroundColor,
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 12.dp,
                vertical = 8.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val currentIcon = if (checked) iconChecked ?: icon else icon
            currentIcon?.let { iconVector ->
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = text,
                color = contentColor,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

/**
 * Cute compact toggle button with drawable resource icon
 */
@SuppressLint("ModifierParameter")
@Composable
fun CuteToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: Int? = null,
    iconChecked: Int? = null,
    checkedBackgroundColor: Color = MaterialTheme.colorScheme.primary,
    uncheckedBackgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    checkedContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    uncheckedContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val interactionSource = remember { MutableInteractionSource() }
    
    val backgroundColor by animateColorAsState(
        targetValue = if (checked) checkedBackgroundColor else uncheckedBackgroundColor,
        animationSpec = Motion.state(),
        label = "backgroundColor"
    )
    
    val contentColor by animateColorAsState(
        targetValue = if (checked) checkedContentColor else uncheckedContentColor,
        animationSpec = Motion.state(),
        label = "contentColor"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.95f,
        animationSpec = Motion.interactive(),
        label = "scale"
    )

    Surface(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interactionSource,
                
                enabled = enabled,
                role = Role.Checkbox,
                onClick = { onCheckedChange(!checked) }
            ),
        color = backgroundColor,
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 12.dp,
                vertical = 8.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val currentIcon = if (checked) iconChecked ?: icon else icon
            currentIcon?.let { iconRes ->
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = text,
                color = contentColor,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

/**
 * Minimal cute toggle (icon only, bouncy animation)
 */
@SuppressLint("ModifierParameter")
@Composable
fun CuteIconToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconChecked: ImageVector? = null,
    size: Dp = 40.dp,
    checkedBackgroundColor: Color = MaterialTheme.colorScheme.primary,
    uncheckedBackgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    checkedContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    uncheckedContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val interactionSource = remember { MutableInteractionSource() }
    
    val backgroundColor by animateColorAsState(
        targetValue = if (checked) checkedBackgroundColor else uncheckedBackgroundColor,
        animationSpec = Motion.state(),
        label = "backgroundColor"
    )
    
    val contentColor by animateColorAsState(
        targetValue = if (checked) checkedContentColor else uncheckedContentColor,
        animationSpec = Motion.state(),
        label = "contentColor"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.9f,
        animationSpec = Motion.interactive(),
        label = "scale"
    )

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                enabled = enabled,
                role = Role.Checkbox,
                onClick = { onCheckedChange(!checked) }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (checked) iconChecked ?: icon else icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}