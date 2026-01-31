package com.dark.tool_neuron.ui.components

import android.annotation.SuppressLint
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.IconToggleButtonColors
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.global.Standards
import com.dark.tool_neuron.models.ui.ActionIcon
import com.dark.tool_neuron.models.ui.ActionItem
import com.dark.tool_neuron.ui.theme.rDp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@SuppressLint("ModifierParameter")
@Composable
fun ActionButton(
    onClickListener: () -> Unit,
    icon: Int,
    contentDescription: String = "Description",
    modifier: Modifier = Modifier,
    shape: Shape = MaterialShapes.Square.toShape(),
    colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(
        containerColor = MaterialTheme.colorScheme.primary.copy(0.06f),
        contentColor = MaterialTheme.colorScheme.primary
    )
) {
    FilledIconButton(
        onClick = { onClickListener() },
        colors = colors,
        shape = shape,
        modifier = modifier.size(rDp(Standards.ActionIconSize))
    ) {
        Icon(
            painterResource(icon),
            contentDescription = contentDescription,
            Modifier.padding(rDp(Standards.ActionIconPadding))
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@SuppressLint("ModifierParameter")
@Composable
fun ActionProgressButton(
    onClickListener: () -> Unit,
    icon: ImageVector = Icons.Default.Stop,
    contentDescription: String = "Description",
    modifier: Modifier = Modifier,
    shape: Shape = MaterialShapes.Circle.toShape(),
    colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(
        containerColor = MaterialTheme.colorScheme.primary.copy(0.06f),
        contentColor = MaterialTheme.colorScheme.primary
    )
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        // Background circular progress indicator
        CircularProgressIndicator(
            modifier = Modifier.size(rDp(Standards.ActionIconSize)),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = rDp(2.dp),
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        )

        // Icon button in center
        FilledIconButton(
            onClick = { onClickListener() },
            colors = colors,
            shape = shape,
            modifier = Modifier.size(rDp(Standards.ActionIconSize - 8.dp))
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                modifier = Modifier.padding(rDp(Standards.ActionIconPadding))
            )
        }
    }
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@SuppressLint("ModifierParameter")
@Composable
fun ActionButton(
    onClickListener: () -> Unit,
    icon: ImageVector,
    contentDescription: String = "Description",
    modifier: Modifier = Modifier,
    shape: Shape = MaterialShapes.Square.toShape(),
    colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(
        containerColor = MaterialTheme.colorScheme.primary.copy(0.06f),
        contentColor = MaterialTheme.colorScheme.primary
    )
) {
    FilledIconButton(
        onClick = { onClickListener() },
        colors = colors,
        shape = shape,
        modifier = modifier.size(rDp(Standards.ActionIconSize))
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            Modifier.padding(rDp(Standards.ActionIconPadding))
        )
    }
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@SuppressLint("ModifierParameter")
@Composable
fun MultiActionButton(
    actions: List<ActionItem>,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(rDp(6.dp)),
    containerColor: Color = MaterialTheme.colorScheme.primary.copy(0.06f),
    contentColor: Color = MaterialTheme.colorScheme.primary,
    dividerColor: Color = MaterialTheme.colorScheme.outline.copy(0.3f)
) {
    Surface(
        shape = shape,
        color = containerColor,
        modifier = modifier.height(rDp(Standards.ActionIconSize))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            actions.forEachIndexed { index, action ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(rDp(Standards.ActionIconSize))
                        .clickable { action.onClick() }
                ) {
                    if (action.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(rDp(Standards.ActionIconSize - 12.dp)),
                            color = contentColor,
                            strokeWidth = rDp(2.dp),
                            trackColor = contentColor.copy(alpha = 0.1f)
                        )
                    } else {
                        when (action.icon) {
                            is ActionIcon.Vector -> Icon(
                                imageVector = action.icon.imageVector,
                                contentDescription = action.contentDescription,
                                tint = contentColor,
                                modifier = Modifier.padding(rDp(Standards.ActionIconPadding))
                            )
                            is ActionIcon.Resource -> Icon(
                                painter = painterResource(action.icon.resId),
                                contentDescription = action.contentDescription,
                                tint = contentColor,
                                modifier = Modifier.padding(rDp(Standards.ActionIconPadding))
                            )
                        }
                    }
                }

                // Add divider between items (not after the last one)
                if (index < actions.lastIndex) {
                    VerticalDivider(
                        modifier = Modifier
                            .height(rDp(Standards.ActionIconSize - 16.dp)),
                        thickness = rDp(1.dp),
                        color = dividerColor
                    )
                }
            }
        }
    }
}



@SuppressLint("ModifierParameter")
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ActionTextButton(
    onClickListener: () -> Unit,
    icon: Int,
    text: String,
    contentDescription: String = "Description",
    modifier: Modifier = Modifier,
    colors: ButtonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary.copy(0.06f),
        contentColor = MaterialTheme.colorScheme.primary
    ),
    shape: Shape = RoundedCornerShape(rDp(6.dp))
) {
    FilledTonalButton(
        onClick = onClickListener,
        shape = shape,
        colors = colors,
        modifier = modifier.height(rDp(Standards.ActionIconSize)),
        contentPadding = PaddingValues(horizontal = rDp(12.dp))
    ) {
        Icon(painterResource(icon), contentDescription)
        Spacer(Modifier.width(rDp(6.dp)))
        Text(text)
    }
}

@SuppressLint("ModifierParameter")
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ActionTextButton(
    onClickListener: () -> Unit,
    icon: ImageVector,
    text: String,
    contentDescription: String = "Description",
    modifier: Modifier = Modifier,
    colors: ButtonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary.copy(0.06f),
        contentColor = MaterialTheme.colorScheme.primary
    ),
    shape: Shape = RoundedCornerShape(rDp(6.dp))
) {
    FilledTonalButton(
        onClick = onClickListener,
        shape = shape,
        colors = colors,
        modifier = modifier.height(rDp(Standards.ActionIconSize)),
        contentPadding = PaddingValues(end = rDp(12.dp))
    ) {
        Icon(icon, contentDescription)
        Spacer(Modifier.width(rDp(6.dp)))
        Text(text)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@SuppressLint("ModifierParameter")
@Composable
fun ActionToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: Int,
    contentDescription: String = "Description",
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = MaterialShapes.Square.toShape(),
    colors: IconToggleButtonColors = IconButtonDefaults.filledIconToggleButtonColors(
        containerColor = MaterialTheme.colorScheme.primary.copy(0.06f),
        contentColor = MaterialTheme.colorScheme.primary,
        checkedContentColor = MaterialTheme.colorScheme.onPrimary
    )
) {
    FilledIconToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = colors,
        shape = shape,
        modifier = modifier.size(rDp(Standards.ActionIconSize))
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            modifier = Modifier.padding(rDp(Standards.ActionIconPadding))
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ActionToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector,
    contentDescription: String = "Description",
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = MaterialShapes.Square.toShape(),
    colors: IconToggleButtonColors = IconButtonDefaults.filledIconToggleButtonColors(
        containerColor = MaterialTheme.colorScheme.primary.copy(0.06f),
        contentColor = MaterialTheme.colorScheme.primary,
        checkedContentColor = MaterialTheme.colorScheme.onPrimary
    )
) {
    FilledIconToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = colors,
        shape = shape,
        modifier = modifier.size(rDp(Standards.ActionIconSize))
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.padding(rDp(Standards.ActionIconPadding))
        )
    }
}

// ==================== ActionSwitch ====================

/**
 * Custom toggle switch matching ActionButton dimensions and styling.
 * Same height (30dp) and corner radius (6dp) as MultiActionButton.
 * Uses spring animations for bouncy, satisfying toggling.
 */
@SuppressLint("ModifierParameter")
@Composable
fun ActionSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    width: Dp = rDp(52.dp),
    height: Dp = rDp(Standards.ActionIconSize),
    thumbSize: Dp = rDp(22.dp),
    shape: Shape = RoundedCornerShape(rDp(8.dp))
) {
    val interactionSource = remember { MutableInteractionSource() }

    val trackColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            checked -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "actionSwitchTrack"
    )

    val thumbColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            checked -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.outline
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "actionSwitchThumb"
    )

    val thumbOffset by animateDpAsState(
        targetValue = if (checked) width - thumbSize - rDp(4.dp) else rDp(4.dp),
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = 350f
        ),
        label = "actionSwitchOffset"
    )

    val thumbScale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.9f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f),
        label = "actionSwitchScale"
    )

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(shape)
            .background(trackColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
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
                .background(thumbColor, RoundedCornerShape(rDp(6.dp)))
                .border(rDp(1.dp), trackColor.copy(alpha = 0.15f), RoundedCornerShape(rDp(6.dp)))
        )
    }
}

// ==================== ActionToggleGroup ====================

/**
 * Single-select segmented toggle matching ActionButton styling.
 * Same height (30dp) and corner radius (6dp) as MultiActionButton.
 * Spring-animated sliding indicator that moves to the selected item.
 */
@SuppressLint("ModifierParameter")
@Composable
fun <T> ActionToggleGroup(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    itemLabel: (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    if (items.isEmpty()) return

    val selectedIndex = items.indexOf(selectedItem).coerceAtLeast(0)
    val density = LocalDensity.current
    val containerWidth = remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val itemWidth = if (containerWidth.intValue > 0 && items.isNotEmpty()) {
        with(density) { (containerWidth.intValue / items.size).toDp() }
    } else {
        rDp(0.dp)
    }

    val indicatorOffset by animateDpAsState(
        targetValue = itemWidth * selectedIndex,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = 350f
        ),
        label = "toggleIndicatorOffset"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(rDp(Standards.ActionIconSize))
            .onSizeChanged { containerWidth.intValue = it.width },
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
        shape = RoundedCornerShape(rDp(6.dp))
    ) {
        Box {
            // Sliding indicator
            if (itemWidth > rDp(0.dp)) {
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset + rDp(2.dp))
                        .padding(vertical = rDp(2.dp))
                        .width(itemWidth - rDp(4.dp))
                        .height(rDp(Standards.ActionIconSize - 4.dp))
                        .background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(rDp(4.dp))
                        )
                )
            }

            // Items row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rDp(Standards.ActionIconSize)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    val isSelected = index == selectedIndex

                    val contentColor by animateColorAsState(
                        targetValue = when {
                            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            isSelected -> MaterialTheme.colorScheme.onPrimary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "toggleItemColor$index"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(rDp(Standards.ActionIconSize))
                            .clip(RoundedCornerShape(rDp(4.dp)))
                            .clickable(
                                enabled = enabled,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onItemSelected(item) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = itemLabel(item),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
