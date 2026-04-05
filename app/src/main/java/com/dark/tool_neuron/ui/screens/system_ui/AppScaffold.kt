package com.dark.tool_neuron.ui.screens.system_ui

import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dark.tool_neuron.model.NavScreens
import com.dark.tool_neuron.ui.navigation.TNavigation
import com.dark.tool_neuron.viewmodel.HomeViewModel

@Composable
fun AppScaffold() {
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route

    val homeViewModel: HomeViewModel = hiltViewModel()
    val actionWindowExpanded by homeViewModel.actionWindowExpanded.collectAsStateWithLifecycle()

    val isFullscreen = currentRoute == NavScreens.IntroScreen.route

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            if (!isFullscreen) AppTopBar(
                currentRoute = currentRoute,
                actionWindowExpanded = actionWindowExpanded,
                onActionWindowToggle = homeViewModel::toggleActionWindow
            )
        },
        bottomBar = { if (!isFullscreen) AppBottomBar(currentRoute, navController) },
    ) { innerPadding ->
        TNavigation(
            navController = navController,
            innerPadding = innerPadding,
            startDestination = NavScreens.HomeScreen.route,
            actionWindowExpanded = actionWindowExpanded,
            onActionWindowDismiss = homeViewModel::collapseActionWindow
        )
    }
}
