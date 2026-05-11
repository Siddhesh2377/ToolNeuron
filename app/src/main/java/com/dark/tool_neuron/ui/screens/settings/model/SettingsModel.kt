package com.dark.tool_neuron.ui.screens.settings.model

import androidx.compose.ui.graphics.vector.ImageVector

data class SettingsChoiceOption(
    val key: String,
    val label: String,
    val description: String? = null,
)

sealed interface SettingsItem {
    val id: String
    val title: String
    val subtitle: String?
    val icon: ImageVector?
    val enabled: Boolean

    data class Toggle(
        override val id: String,
        override val title: String,
        override val subtitle: String? = null,
        override val icon: ImageVector? = null,
        override val enabled: Boolean = true,
        val checked: Boolean,
        val onToggle: (Boolean) -> Unit,
    ) : SettingsItem

    data class Action(
        override val id: String,
        override val title: String,
        override val subtitle: String? = null,
        override val icon: ImageVector? = null,
        override val enabled: Boolean = true,
        val trailingText: String? = null,
        val destructive: Boolean = false,
        val onClick: () -> Unit,
    ) : SettingsItem

    data class Choice(
        override val id: String,
        override val title: String,
        override val subtitle: String? = null,
        override val icon: ImageVector? = null,
        override val enabled: Boolean = true,
        val selectedKey: String?,
        val options: List<SettingsChoiceOption>,
        val emptyMessage: String? = null,
        val onSelect: (String?) -> Unit,
    ) : SettingsItem {
        val selectedLabel: String
            get() = options.firstOrNull { it.key == selectedKey }?.label ?: (emptyMessage ?: "Not set")
    }

    data class Info(
        override val id: String,
        override val title: String,
        override val subtitle: String? = null,
        override val icon: ImageVector? = null,
        override val enabled: Boolean = true,
        val value: String,
    ) : SettingsItem
}

data class SettingsSection(
    val id: String,
    val title: String,
    val description: String? = null,
    val icon: ImageVector? = null,
    val items: List<SettingsItem>,
)
