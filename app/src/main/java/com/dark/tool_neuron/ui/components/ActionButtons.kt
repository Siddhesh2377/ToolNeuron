package com.dark.tool_neuron.ui.components

import android.annotation.SuppressLint
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.material3.IconToggleButtonColors
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.model.ui.ActionIcon
import com.dark.tool_neuron.model.ui.ActionItem
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.DefaultTnShapes
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.ui.theme.Motion

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@SuppressLint("ModifierParameter")
@Composable
fun ActionButton(
    onClickListener: () -> Unit,
    icon: Int,
    contentDescription: String = "Description",
    modifier: Modifier = Modifier,
    shape: Shape = DefaultTnShapes.actionIcon,
    colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(
        containerColor = MaterialTheme.colorScheme.primary.copy(0.08f),
        contentColor = MaterialTheme.colorScheme.primary
    )
) {
    val dimens = LocalDimens.current
    FilledIconButton(
        onClick = onClickListener,
        colors = colors,
        shape = shape,
        modifier = modifier.size(dimens.actionIconSize)
    ) {
        Icon(
            painterResource(icon),
            contentDescription = contentDescription,
            modifier = Modifier.padding(dimens.actionIconPadding)
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@SuppressLint("ModifierParameter")
@Composable
fun ActionButtonNormalSize(
    onClickListener: () -> Unit,
    icon: ImageVector,
    contentDescription: String = "Description",
    modifier: Modifier = Modifier,
    shape: Shape = DefaultTnShapes.actionIcon,
    colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(
        containerColor = MaterialTheme.colorScheme.primary.copy(0.08f),
        contentColor = MaterialTheme.colorScheme.primary
    )
) {
    val dimens = LocalDimens.current
    FilledIconButton(
        onClick = onClickListener,
        colors = colors,
        shape = shape,
        modifier = modifier
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            modifier = Modifier.padding(dimens.actionIconPadding)
        )
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
    shape: Shape = DefaultTnShapes.actionIcon,
    colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(
        containerColor = MaterialTheme.colorScheme.primary.copy(0.08f),
        contentColor = MaterialTheme.colorScheme.primary
    )
) {
    val dimens = LocalDimens.current
    FilledIconButton(
        onClick = onClickListener,
        colors = colors,
        shape = shape,
        modifier = modifier.size(dimens.actionIconSize)
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            modifier = Modifier.padding(dimens.actionIconPadding)
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@SuppressLint("ModifierParameter")
@Composable
fun ActionProgressButton(
    onClickListener: () -> Unit,
    icon: ImageVector = TnIcons.PlayerStop,
    contentDescription: String = "Description",
    modifier: Modifier = Modifier,
    colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(
        containerColor = MaterialTheme.colorScheme.primary.copy(0.08f),
        contentColor = MaterialTheme.colorScheme.primary
    )
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(dimens.actionIconSize),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 2.dp,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        )
        FilledIconButton(
            onClick = onClickListener,
            colors = colors,
            shape = tnShapes.full,
            modifier = Modifier.size(dimens.actionIconSize - 8.dp)
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                modifier = Modifier.padding(dimens.actionIconPadding)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@SuppressLint("ModifierParameter")
@Composable
fun MultiActionButton(
    actions: List<ActionItem>,
    modifier: Modifier = Modifier,
    shape: Shape = DefaultTnShapes.actionIcon,
    containerColor: Color = MaterialTheme.colorScheme.primary.copy(0.08f),
    contentColor: Color = MaterialTheme.colorScheme.primary,
    dividerColor: Color = MaterialTheme.colorScheme.outline.copy(0.25f)
) {
    val dimens = LocalDimens.current
    Surface(
        shape = shape,
        color = containerColor,
        modifier = modifier.height(dimens.actionIconSize)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            actions.forEachIndexed { index, action ->
                val tint = if (action.enabled) contentColor else contentColor.copy(alpha = 0.3f)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(dimens.actionIconSize)
                        .then(
                            if (action.enabled) Modifier.clickable(
                                interactionSource = remember(index) { MutableInteractionSource() },
                                indication = null,
                                onClick = action.onClick
                            ) else Modifier
                        )
                ) {
                    if (action.isLoading) {
                        LoadingIndicator(
                            modifier = Modifier.size(dimens.actionIconSize - 12.dp),
                            color = tint
                        )
                    } else {
                        when (action.icon) {
                            is ActionIcon.Vector -> Icon(
                                imageVector = action.icon.imageVector,
                                contentDescription = action.contentDescription,
                                tint = tint,
                                modifier = Modifier.padding(dimens.actionIconPadding)
                            )
                            is ActionIcon.Resource -> Icon(
                                painter = painterResource(action.icon.resId),
                                contentDescription = action.contentDescription,
                                tint = tint,
                                modifier = Modifier.padding(dimens.actionIconPadding)
                            )
                        }
                    }
                }
                if (index < actions.lastIndex) {
                    VerticalDivider(
                        modifier = Modifier.height(dimens.actionIconSize - 14.dp),
                        thickness = 1.dp,
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
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(
        containerColor = MaterialTheme.colorScheme.primary.copy(0.08f),
        contentColor = MaterialTheme.colorScheme.primary
    ),
    shape: Shape = DefaultTnShapes.actionIcon
) {
    val dimens = LocalDimens.current
    FilledTonalButton(
        onClick = onClickListener,
        shape = shape,
        colors = colors,
        modifier = modifier.height(dimens.actionIconSize),
        contentPadding = PaddingValues(horizontal = 14.dp),
        enabled = enabled
    ) {
        Icon(painterResource(icon), contentDescription, modifier = Modifier.size(dimens.iconSm))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium)
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
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(
        containerColor = MaterialTheme.colorScheme.primary.copy(0.08f),
        contentColor = MaterialTheme.colorScheme.primary
    ),
    shape: Shape = DefaultTnShapes.actionIcon
) {
    val dimens = LocalDimens.current
    FilledTonalButton(
        onClick = onClickListener,
        shape = shape,
        colors = colors,
        modifier = modifier.height(dimens.actionIconSize),
        contentPadding = PaddingValues(horizontal = 14.dp),
        enabled = enabled
    ) {
        Icon(icon, contentDescription, modifier = Modifier.size(dimens.iconSm))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium)
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
    shape: Shape = DefaultTnShapes.actionIcon,
    colors: IconToggleButtonColors = IconButtonDefaults.filledIconToggleButtonColors(
        containerColor = MaterialTheme.colorScheme.primary.copy(0.08f),
        contentColor = MaterialTheme.colorScheme.primary,
        checkedContainerColor = MaterialTheme.colorScheme.primary,
        checkedContentColor = MaterialTheme.colorScheme.onPrimary
    )
) {
    val dimens = LocalDimens.current
    FilledIconToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = colors,
        shape = shape,
        modifier = modifier.size(dimens.actionIconSize)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            modifier = Modifier.padding(dimens.actionIconPadding)
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@SuppressLint("ModifierParameter")
@Composable
fun ActionToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector,
    contentDescription: String = "Description",
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = DefaultTnShapes.actionIcon,
    colors: IconToggleButtonColors = IconButtonDefaults.filledIconToggleButtonColors(
        containerColor = MaterialTheme.colorScheme.primary.copy(0.08f),
        contentColor = MaterialTheme.colorScheme.primary,
        checkedContainerColor = MaterialTheme.colorScheme.primary,
        checkedContentColor = MaterialTheme.colorScheme.onPrimary
    )
) {
    val dimens = LocalDimens.current
    FilledIconToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = colors,
        shape = shape,
        modifier = modifier.size(dimens.actionIconSize)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.padding(dimens.actionIconPadding)
        )
    }
}

@SuppressLint("ModifierParameter")
@Composable
fun ActionSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    CuteSwitch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        width = 52.dp,
        height = 30.dp,
        thumbSize = 22.dp,
    )
}

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

    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    val selectedIndex = items.indexOf(selectedItem).coerceAtLeast(0)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(dimens.actionIconSize),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        shape = tnShapes.actionIcon
    ) {
        BoxWithConstraints {
            val itemWidth = maxWidth / items.size

            val indicatorOffset by animateDpAsState(
                targetValue = itemWidth * selectedIndex,
                animationSpec = Motion.interactive(),
                label = "toggleIndicatorOffset"
            )

            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset + 2.dp)
                    .padding(vertical = 2.dp)
                    .width(itemWidth - 4.dp)
                    .height(dimens.actionIconSize - 4.dp)
                    .background(MaterialTheme.colorScheme.primary, tnShapes.actionIcon)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimens.actionIconSize),
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
                        animationSpec = Motion.state(),
                        label = "toggleItemColor$index"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(dimens.actionIconSize)
                            .clip(tnShapes.actionIcon)
                            .clickable(
                                enabled = enabled,
                                interactionSource = remember(index) { MutableInteractionSource() },
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
