package com.dark.tool_neuron.models.ui

import androidx.compose.ui.graphics.vector.ImageVector

sealed class ActionIcon {
    data class Vector(val imageVector: ImageVector) : ActionIcon()
    data class Resource(val resId: Int) : ActionIcon()
}

data class ActionItem(
    val icon: ActionIcon,
    val onClick: () -> Unit,
    val contentDescription: String = "Action",
    val isLoading: Boolean = false,
    val enabled: Boolean = true
)