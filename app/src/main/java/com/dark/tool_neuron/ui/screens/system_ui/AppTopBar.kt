package com.dark.tool_neuron.ui.screens.system_ui

import androidx.compose.runtime.Composable
import com.dark.tool_neuron.model.NavScreens
import com.dark.tool_neuron.ui.screens.home_screen.HomeScreenTopbar

@Composable
fun AppTopBar(
    currentRoute: String?,
    actionWindowExpanded: Boolean,
    onActionWindowToggle: () -> Unit,
) {
    when (currentRoute) {
        NavScreens.HomeScreen.route -> HomeScreenTopbar(
            expanded = actionWindowExpanded,
            onToggle = onActionWindowToggle
        )
        else -> Unit
    }
}
