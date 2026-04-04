package com.dark.tool_neuron.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.dark.tool_neuron.model.NavScreens
import com.dark.tool_neuron.ui.screens.home_screen.HomeScreen
import com.dark.tool_neuron.ui.screens.intro_screen.IntroScreen
import com.dark.tool_neuron.ui.theme.rememberNavTransitions
import kotlinx.coroutines.delay

@Composable
fun TNavigation(
    navController: NavHostController,
    innerPadding: PaddingValues,
    startDestination: String,
) {
    val transitions = rememberNavTransitions()

    LaunchedEffect(Unit) {
        delay(3000)
        navController.navigate(NavScreens.HomeScreen.route) {
            popUpTo(NavScreens.IntroScreen.route) { inclusive = true }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
    ) {
        composable(NavScreens.IntroScreen.route) {
            IntroScreen(innerPadding)
        }
        composable(NavScreens.HomeScreen.route) {
            HomeScreen(innerPadding)
        }
    }
}
