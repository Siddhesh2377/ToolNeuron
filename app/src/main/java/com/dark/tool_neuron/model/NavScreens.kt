package com.dark.tool_neuron.model

sealed class NavScreens(val route: String) {
    object IntroScreen : NavScreens("intro_screen")
    object HomeScreen : NavScreens("home_screen")
    object DevNotes : NavScreens("dev_notes")
    object PasswordScreen : NavScreens("password_screen")
    object SetupScreen : NavScreens("setup_screen")
    object ModelStore : NavScreens("model_store")
    object ModelSetup : NavScreens("model_setup")
    object AppGuide : NavScreens("app_guide")
}