package com.dark.tool_neuron.ui.screens.settings.model

sealed interface SettingsDialog {
    data class Choice(
        val itemId: String,
        val title: String,
        val options: List<SettingsChoiceOption>,
        val selectedKey: String?,
        val allowClear: Boolean,
        val onSelect: (String?) -> Unit,
    ) : SettingsDialog

    data class Confirm(
        val title: String,
        val message: String,
        val confirmLabel: String,
        val destructive: Boolean,
        val onConfirm: () -> Unit,
    ) : SettingsDialog

    data class PinEntry(
        val title: String,
        val message: String,
        val minLength: Int,
        val confirmLabel: String = "Save",
        val onSubmit: (String) -> Unit,
    ) : SettingsDialog
}

data class SettingsState(
    val sections: List<SettingsSection> = emptyList(),
    val dialog: SettingsDialog? = null,
    val snackbarMessage: String? = null,
    val appVersion: String = "",
)
