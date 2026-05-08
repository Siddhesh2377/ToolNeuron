package com.dark.tool_neuron.ui.screens.system_ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.dark.tool_neuron.model.NavScreens
import com.dark.tool_neuron.ui.screens.dev_notes.DevNotesBottomBar
import com.dark.tool_neuron.ui.screens.home_screen.HomeScreenBottomBar
import com.dark.tool_neuron.ui.screens.password_screen.PasswordScreenBottomBar
import com.dark.tool_neuron.ui.screens.setup_screen.SetupRagBottomBar
import com.dark.tool_neuron.ui.screens.setup_screen.SetupThemeBottomBar
import com.dark.tool_neuron.ui.screens.terms_conditions.TermsConditionsBottomBar

@Composable
fun AppBottomBar(
    currentRoute: String?,
    navController: NavHostController,
    onOnboardingComplete: () -> Unit = {},
    onThemeSetupComplete: () -> Unit = {},
    onRagSetupComplete: () -> Unit = {},
    onTermsAccepted: () -> Unit = {},
) {
    when (currentRoute) {
        NavScreens.HomeScreen.route -> HomeScreenBottomBar(navController)
        NavScreens.TermsConditions.route -> TermsConditionsBottomBar(
            buttonLabel = "Agree and continue",
            onAccept = onTermsAccepted,
        )
        NavScreens.DevNotes.route -> DevNotesBottomBar(navController, onContinue = onOnboardingComplete)
        NavScreens.PasswordScreen.route -> PasswordScreenBottomBar()
        NavScreens.SetupTheme.route -> SetupThemeBottomBar(onContinue = onThemeSetupComplete)
        NavScreens.SetupRag.route -> SetupRagBottomBar(onContinue = onRagSetupComplete)
        NavScreens.ModelSetup.route -> Unit
        else -> Unit
    }
}
