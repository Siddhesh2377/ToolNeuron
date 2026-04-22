package com.dark.tool_neuron.ui.screens.home_screen

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.action_window.ActionWindowPill
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.viewmodel.home_vm.PillState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenTopbar(
    pillState: PillState,
    expanded: Boolean,
    onToggle: () -> Unit,
    onMenuClick: () -> Unit = {},
    onStoreClick: () -> Unit = {},
    onGuideClick: () -> Unit = {},
    onTerminalClick: () -> Unit = {},
) {
    val dimens = LocalDimens.current

    CenterAlignedTopAppBar(
        title = {
            ActionWindowPill(
                state = pillState,
                expanded = expanded,
                onToggle = onToggle,
            )
        },
        navigationIcon = {
            ActionButton(
                onClickListener = onMenuClick,
                icon = TnIcons.Menu,
                contentDescription = "Menu",
                modifier = Modifier.padding(start = dimens.screenPadding)
            )
        },
        actions = {
            ActionButton(
                onClickListener = onTerminalClick,
                icon = TnIcons.Terminal,
                contentDescription = "Terminal",
                modifier = Modifier.padding(end = dimens.spacingSm)
            )
            ActionButton(
                onClickListener = onGuideClick,
                icon = TnIcons.BookOpen,
                contentDescription = "Guide",
                modifier = Modifier.padding(end = dimens.spacingSm)
            )
            ActionButton(
                onClickListener = onStoreClick,
                icon = TnIcons.Download,
                contentDescription = "Store",
                modifier = Modifier.padding(end = dimens.screenPadding)
            )
        }
    )
}
