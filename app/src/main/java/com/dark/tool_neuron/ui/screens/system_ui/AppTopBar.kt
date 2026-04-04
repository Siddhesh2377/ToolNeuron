package com.dark.tool_neuron.ui.screens.system_ui

import androidx.compose.runtime.Composable
import com.dark.tool_neuron.model.NavScreens
import com.dark.tool_neuron.ui.screens.home_screen.HomeScreenTopbar

@Composable
fun AppTopBar(currentRoute: String?) {
    when (currentRoute) {
        NavScreens.HomeScreen.route -> HomeScreenTopbar()
        else -> Unit
    }
}
