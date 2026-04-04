package com.dark.tool_neuron.ui.screens.system_ui

import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dark.tool_neuron.model.NavScreens
import com.dark.tool_neuron.ui.navigation.TNavigation

@Composable
fun AppScaffold() {
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route

    val isFullscreen = currentRoute == NavScreens.IntroScreen.route

    Scaffold(
        topBar = { if (!isFullscreen) AppTopBar(currentRoute) },
        bottomBar = { if (!isFullscreen) AppBottomBar(currentRoute, navController) },
    ) { innerPadding ->
        TNavigation(
            navController = navController,
            innerPadding = innerPadding,
            startDestination = NavScreens.IntroScreen.route,
        )
    }
}
