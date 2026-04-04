package com.dark.tool_neuron.ui.screens.system_ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.dark.tool_neuron.model.NavScreens
import com.dark.tool_neuron.ui.screens.home_screen.HomeScreenBottomBar

@Composable
fun AppBottomBar(currentRoute: String?, navController: NavHostController) {
    when (currentRoute) {
        NavScreens.HomeScreen.route -> HomeScreenBottomBar(navController)
        else -> Unit
    }
}
