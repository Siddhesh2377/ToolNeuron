package com.dark.tool_neuron.model

sealed class Screen(val route: String) {
    object Intro : Screen("intro")
    object Model : Screen("models")
    object Home : Screen("home")
    object Settings : Screen("settings")
}
