package com.dark.tool_neuron.ui.screens.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.ui.components.PinDotRow
import com.dark.tool_neuron.ui.components.PinNumberPad
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.screens.settings.model.SettingsChoiceOption
import com.dark.tool_neuron.ui.screens.settings.model.SettingsDialog
import com.dark.tool_neuron.ui.theme.LocalDimens

@Composable
fun SettingsDialogHost(
    dialog: SettingsDialog?,
    onDismiss: () -> Unit,
) {
    when (dialog) {
        is SettingsDialog.Choice -> ChoiceDialog(dialog, onDismiss)
        is SettingsDialog.Confirm -> ConfirmDialog(dialog, onDismiss)
        is SettingsDialog.PinEntry -> PinEntryDialog(dialog, onDismiss)
        null -> Unit
    }
}

@Composable
private fun ChoiceDialog(dialog: SettingsDialog.Choice, onDismiss: () -> Unit) {
    val dimens = LocalDimens.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialog.title) },
        text = {
            if (dialog.options.isEmpty()) {
                Text(
                    "No options available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingXs)) {
                    if (dialog.allowClear) {
                        ChoiceRowItem(
                            option = SettingsChoiceOption(key = "", label = "None (auto-pick)"),
                            selected = dialog.selectedKey.isNullOrBlank(),
                            onClick = { dialog.onSelect(null) },
                        )
                    }
                    dialog.options.forEach { option ->
                        ChoiceRowItem(
                            option = option,
                            selected = option.key == dialog.selectedKey,
                            onClick = { dialog.onSelect(option.key) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun ChoiceRowItem(
    option: SettingsChoiceOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary,
                ),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!option.description.isNullOrBlank()) {
                    Text(
                        text = option.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfirmDialog(dialog: SettingsDialog.Confirm, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (dialog.destructive) TnIcons.AlertTriangle else TnIcons.Info,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = if (dialog.destructive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text(dialog.title) },
        text = { Text(dialog.message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(
                onClick = dialog.onConfirm,
                colors = if (dialog.destructive)
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                else ButtonDefaults.buttonColors(),
            ) { Text(dialog.confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun PinEntryDialog(dialog: SettingsDialog.PinEntry, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    val dimens = LocalDimens.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialog.title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
            ) {
                Text(
                    text = dialog.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val maxDigits = dialog.minLength.coerceAtLeast(6)
                PinDotRow(length = pin.length, dots = maxDigits)
                PinNumberPad(
                    onDigit = { c ->
                        if (pin.length < maxDigits) pin += c
                    },
                    onDelete = {
                        if (pin.isNotEmpty()) pin = pin.dropLast(1)
                    },
                )
            }
        },
        confirmButton = {
            Button(
                enabled = pin.length >= dialog.minLength,
                onClick = { dialog.onSubmit(pin) },
            ) { Text(dialog.confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
