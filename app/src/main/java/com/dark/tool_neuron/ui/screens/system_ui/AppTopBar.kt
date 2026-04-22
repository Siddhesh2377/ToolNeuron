package com.dark.tool_neuron.ui.screens.system_ui

import androidx.compose.runtime.Composable
import com.dark.tool_neuron.model.NavScreens
import com.dark.tool_neuron.ui.screens.dev_notes.DevNotesTopBar
import com.dark.tool_neuron.ui.screens.home_screen.HomeScreenTopbar
import com.dark.tool_neuron.ui.screens.password_screen.PasswordScreenTopBar
import com.dark.tool_neuron.ui.screens.setup_screen.SetupScreenTopBar
import com.dark.tool_neuron.viewmodel.home_vm.PillState

@Composable
fun AppTopBar(
    currentRoute: String?,
    pillState: PillState,
    actionWindowExpanded: Boolean,
    onActionWindowToggle: () -> Unit,
    onMenuClick: () -> Unit = {},
    onBack: () -> Unit = {},
    onNavigateToStore: () -> Unit = {},
    onNavigateToGuide: () -> Unit = {},
    onNavigateToTerminal: () -> Unit = {},
) {
    when (currentRoute) {
        NavScreens.HomeScreen.route -> HomeScreenTopbar(
            pillState = pillState,
            expanded = actionWindowExpanded,
            onToggle = onActionWindowToggle,
            onMenuClick = onMenuClick,
            onStoreClick = onNavigateToStore,
            onGuideClick = onNavigateToGuide,
            onTerminalClick = onNavigateToTerminal,
        )
        NavScreens.DevNotes.route -> DevNotesTopBar()
        NavScreens.PasswordScreen.route -> PasswordScreenTopBar()
        NavScreens.SetupScreen.route -> SetupScreenTopBar()
        NavScreens.ModelSetup.route -> SetupScreenTopBar()
        NavScreens.ModelStore.route -> Unit
        NavScreens.AppGuide.route -> Unit
        else -> Unit
    }
}
