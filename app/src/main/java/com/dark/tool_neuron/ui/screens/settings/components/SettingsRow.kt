package com.dark.tool_neuron.ui.screens.settings.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.dark.tool_neuron.ui.components.ActionSwitch
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.screens.settings.model.SettingsItem
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.ui.theme.Motion

@Composable
fun SettingsItemRow(
    item: SettingsItem,
    onOpenChoice: (SettingsItem.Choice) -> Unit,
) {
    when (item) {
        is SettingsItem.Toggle -> ToggleRow(item)
        is SettingsItem.Action -> ActionRow(item)
        is SettingsItem.Choice -> ChoiceRow(item, onOpenChoice)
        is SettingsItem.Info -> InfoRow(item)
    }
}

@Composable
private fun ToggleRow(item: SettingsItem.Toggle) {
    RowShell(
        title = item.title,
        subtitle = item.subtitle,
        icon = item.icon,
        enabled = item.enabled,
        onClick = { if (item.enabled) item.onToggle(!item.checked) },
        trailing = {
            ActionSwitch(
                checked = item.checked,
                onCheckedChange = { if (item.enabled) item.onToggle(it) },
                enabled = item.enabled,
            )
        },
    )
}

@Composable
private fun ActionRow(item: SettingsItem.Action) {
    val titleColor by animateColorAsState(
        targetValue = when {
            !item.enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            item.destructive -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurface
        },
        animationSpec = Motion.state(),
        label = "actionTitle",
    )
    RowShell(
        title = item.title,
        subtitle = item.subtitle,
        icon = item.icon,
        enabled = item.enabled,
        onClick = item.onClick,
        titleColorOverride = titleColor,
        trailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LocalDimens.current.spacingXs),
            ) {
                if (!item.trailingText.isNullOrBlank()) {
                    Text(
                        text = item.trailingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = TnIcons.ArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(LocalDimens.current.iconMd),
                    tint = titleColor.copy(alpha = 0.6f),
                )
            }
        },
    )
}

@Composable
private fun ChoiceRow(
    item: SettingsItem.Choice,
    onOpenChoice: (SettingsItem.Choice) -> Unit,
) {
    RowShell(
        title = item.title,
        subtitle = item.subtitle,
        icon = item.icon,
        enabled = item.enabled,
        onClick = { if (item.enabled) onOpenChoice(item) },
        valueLine = item.selectedLabel,
        trailing = {
            Icon(
                imageVector = TnIcons.ChevronDown,
                contentDescription = null,
                modifier = Modifier.size(LocalDimens.current.iconMd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@Composable
private fun InfoRow(item: SettingsItem.Info) {
    RowShell(
        title = item.title,
        subtitle = item.subtitle,
        icon = item.icon,
        enabled = item.enabled,
        onClick = null,
        trailing = {
            Text(
                text = item.value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
        },
    )
}

@Composable
private fun RowShell(
    title: String,
    subtitle: String?,
    icon: ImageVector?,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    trailing: @Composable () -> Unit,
    titleColorOverride: Color? = null,
    valueLine: String? = null,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    val titleColor = titleColorOverride ?: if (enabled) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    val subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant

    val base = Modifier
        .fillMaxWidth()
        .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)

    Surface(
        modifier = base,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = tnShapes.cardSmall,
    ) {
        Row(
            modifier = Modifier.padding(dimens.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier.size(dimens.actionIconSize),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(dimens.iconMd),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = subtitleColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!valueLine.isNullOrBlank()) {
                    Text(
                        text = valueLine,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            trailing()
        }
    }
}
