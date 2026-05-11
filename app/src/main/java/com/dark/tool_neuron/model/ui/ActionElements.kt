package com.dark.tool_neuron.model.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

@Immutable
sealed class ActionIcon {
    @Immutable
    data class Vector(val imageVector: ImageVector) : ActionIcon()
    @Immutable
    data class Resource(val resId: Int) : ActionIcon()
}

@Immutable
data class ActionItem(
    val icon: ActionIcon,
    val onClick: () -> Unit,
    val contentDescription: String = "Action",
    val isLoading: Boolean = false,
    val enabled: Boolean = true
)
