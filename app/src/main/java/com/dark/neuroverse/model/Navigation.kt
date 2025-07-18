package com.dark.neuroverse.model

sealed class Screen(val route: String) {
    object Intro : Screen("intro")
    object Model : Screen("models")
    object Home : Screen("home")
    object Settings : Screen("settings")
}
