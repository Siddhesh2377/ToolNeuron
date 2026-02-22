package com.dark.tool_neuron.ui.components

import android.annotation.SuppressLint
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.global.Standards
import com.dark.tool_neuron.ui.theme.rDp

// ==================== Text Components ====================

/**
 * Section title text - used for section headers in forms and settings
 */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = modifier.padding(vertical = rDp(Standards.SpacingXs))
    )
}

/**
 * Body label text - used for descriptions, labels alongside controls
 */
@Composable
fun BodyLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    maxLines: Int = Int.MAX_VALUE
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

/**
 * Caption text - small secondary info text
 */
@Composable
fun CaptionText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier
    )
}

// ==================== Switch Components ====================

/**
 * Standard row with label and CuteSwitch on the right.
 * Optional icon on the left, optional description below the title.
 */
@SuppressLint("ModifierParameter")
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    iconRes: Int? = null,
    enabled: Boolean = true,
    titleColor: androidx.compose.ui.graphics.Color? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(rDp(Standards.CardSmallCornerRadius))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = rDp(Standards.CardPadding), vertical = rDp(Standards.SpacingSm)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingSm))
        ) {
            // Optional icon
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(rDp(Standards.IconMd)),
                    tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            } else if (iconRes != null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(rDp(Standards.IconMd)),
                    tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }

            // Title + description
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = titleColor ?: if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 0.7f else 0.38f)
                    )
                }
            }

            // Switch
            ActionSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    }
}

// ==================== Multi-Toggle Components ====================

/**
 * A group of segmented toggle items in a row.
 * Each item is a labeled toggle chip. Only one can be selected at a time.
 */
@SuppressLint("ModifierParameter")
@Composable
fun <T> SegmentedToggleGroup(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    itemLabel: (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(rDp(Standards.CardSmallCornerRadius))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDp(Standards.SpacingXs)),
            horizontalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingXs))
        ) {
            items.forEach { item ->
                val isSelected = item == selectedItem
                SegmentedToggleItem(
                    label = itemLabel(item),
                    isSelected = isSelected,
                    enabled = enabled,
                    onClick = { onItemSelected(item) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SegmentedToggleItem(
    label: String,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
        else Color.Transparent,
        animationSpec = tween(200),
        label = "segBg"
    )

    val contentColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            isSelected -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(200),
        label = "segContent"
    )

    Surface(
        modifier = modifier
            .height(rDp(Standards.ToggleGroupHeight))
            .clip(RoundedCornerShape(rDp(Standards.CardSmallCornerRadius - 2.dp)))
            .clickable(
                enabled = enabled,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        color = backgroundColor,
        shape = RoundedCornerShape(rDp(Standards.CardSmallCornerRadius - 2.dp))
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = rDp(Standards.SpacingSm))
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Multi-select toggle row - allows multiple items to be selected.
 */
@SuppressLint("ModifierParameter")
@Composable
fun <T> MultiToggleRow(
    items: List<T>,
    selectedItems: Set<T>,
    onItemToggle: (T) -> Unit,
    itemLabel: (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingSm))
    ) {
        items.forEach { item ->
            val isSelected = selectedItems.contains(item)

            val backgroundColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                animationSpec = tween(200),
                label = "multiToggleBg"
            )

            val contentColor by animateColorAsState(
                targetValue = when {
                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = tween(200),
                label = "multiToggleContent"
            )

            Surface(
                modifier = Modifier
                    .height(rDp(Standards.ToggleGroupHeight))
                    .clip(RoundedCornerShape(rDp(Standards.CardSmallCornerRadius)))
                    .clickable(
                        enabled = enabled,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onItemToggle(item) }
                    ),
                color = backgroundColor,
                shape = RoundedCornerShape(rDp(Standards.CardSmallCornerRadius))
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = rDp(Standards.SpacingMd))
                ) {
                    Text(
                        text = itemLabel(item),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = contentColor,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// ==================== Card Components ====================

/**
 * Standard card with optional icon, title, description, and custom content slot.
 */
@SuppressLint("ModifierParameter")
@Composable
fun StandardCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    description: String? = null,
    icon: ImageVector? = null,
    iconRes: Int? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
    onClick: (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
    content: @Composable (() -> Unit)? = null
) {
    val clickModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier),
        color = containerColor,
        shape = RoundedCornerShape(rDp(Standards.CardCornerRadius)),
        tonalElevation = rDp(Standards.CardElevation)
    ) {
        Column(
            modifier = Modifier.padding(rDp(Standards.CardPadding)),
            verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingSm))
        ) {
            if (title != null || icon != null || iconRes != null || trailing != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingSm))
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(rDp(Standards.IconMd)),
                            tint = iconTint
                        )
                    } else if (iconRes != null) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(rDp(Standards.IconMd)),
                            tint = iconTint
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        if (title != null) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (description != null) {
                            Text(
                                text = description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (trailing != null) {
                        trailing()
                    }
                }
            }

            if (content != null) {
                content()
            }
        }
    }
}

/**
 * Compact info card - used for status indicators, small info panels
 */
@SuppressLint("ModifierParameter")
@Composable
fun InfoCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconRes: Int? = null,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
    contentColor: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        shape = RoundedCornerShape(rDp(Standards.CardSmallCornerRadius))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = rDp(Standards.SpacingSm), vertical = rDp(Standards.SpacingXs)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingXs))
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(rDp(Standards.IconSm)),
                    tint = contentColor
                )
            } else if (iconRes != null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(rDp(Standards.IconSm)),
                    tint = contentColor
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}

// ==================== Badge Components ====================

/**
 * Small colored badge/chip for tags, labels, and status indicators.
 */
@SuppressLint("ModifierParameter")
@Composable
fun InfoBadge(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
    contentColor: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        modifier = modifier.height(rDp(Standards.BadgeHeight)),
        color = containerColor,
        shape = RoundedCornerShape(rDp(Standards.SpacingXs))
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = rDp(Standards.SpacingSm))
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}

/**
 * Status badge with dot indicator
 */
@SuppressLint("ModifierParameter")
@Composable
fun StatusBadge(
    text: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.tertiary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
) {
    val dotColor by animateColorAsState(
        targetValue = if (isActive) activeColor else inactiveColor,
        animationSpec = tween(300),
        label = "statusDot"
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingXs))
    ) {
        Box(
            modifier = Modifier
                .size(rDp(6.dp))
                .background(dotColor, RoundedCornerShape(50))
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = if (isActive) activeColor else inactiveColor
        )
    }
}

// ==================== Section Components ====================

/**
 * Section header with title and optional action on the right
 */
@SuppressLint("ModifierParameter")
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(rDp(Standards.SectionHeaderHeight)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        if (action != null) {
            action()
        }
    }
}

/**
 * Divider-like section separator with optional label
 */
@SuppressLint("ModifierParameter")
@Composable
fun SectionDivider(
    modifier: Modifier = Modifier,
    label: String? = null
) {
    if (label != null) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = rDp(Standards.SpacingSm)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingSm))
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(rDp(1.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(rDp(1.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            )
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = rDp(Standards.SpacingSm))
                .height(rDp(1.dp))
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        )
    }
}
