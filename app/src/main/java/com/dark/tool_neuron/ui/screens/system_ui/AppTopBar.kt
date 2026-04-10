package com.dark.tool_neuron.ui.screens.system_ui

import androidx.compose.runtime.Composable
import com.dark.tool_neuron.model.NavScreens
import com.dark.tool_neuron.ui.screens.dev_notes.DevNotesTopBar
import com.dark.tool_neuron.ui.screens.home_screen.HomeScreenTopbar
import com.dark.tool_neuron.ui.screens.password_screen.PasswordScreenTopBar
import com.dark.tool_neuron.ui.screens.setup_screen.SetupScreenTopBar

@Composable
fun AppTopBar(
    currentRoute: String?,
    actionWindowExpanded: Boolean,
    onActionWindowToggle: () -> Unit,
    onBack: () -> Unit = {},
    onNavigateToStore: () -> Unit = {},
) {
    when (currentRoute) {
        NavScreens.HomeScreen.route -> HomeScreenTopbar(
            expanded = actionWindowExpanded,
            onToggle = onActionWindowToggle,
            onStoreClick = onNavigateToStore,
        )
        NavScreens.DevNotes.route -> DevNotesTopBar()
        NavScreens.PasswordScreen.route -> PasswordScreenTopBar()
        NavScreens.SetupScreen.route -> SetupScreenTopBar()
        NavScreens.ModelStore.route -> Unit
        else -> Unit
    }
}
