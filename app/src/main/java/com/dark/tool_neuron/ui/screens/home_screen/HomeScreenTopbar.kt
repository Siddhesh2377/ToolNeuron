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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenTopbar(
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val dimens = LocalDimens.current

    CenterAlignedTopAppBar(
        title = {
            ActionWindowPill(
                modelName = "Qwen-1.5B-250M",
                expanded = expanded,
                onToggle = onToggle
            )
        },
        navigationIcon = {
            ActionButton(
                onClickListener = {},
                icon = TnIcons.Menu,
                contentDescription = "Menu",
                modifier = Modifier.padding(start = dimens.screenPadding)
            )
        },
        actions = {
            ActionButton(
                onClickListener = {},
                icon = TnIcons.HatGlasses,
                contentDescription = "Persona",
                modifier = Modifier.padding(end = dimens.spacingSm)
            )
            ActionButton(
                onClickListener = {},
                icon = TnIcons.Download,
                contentDescription = "Store",
                modifier = Modifier.padding(end = dimens.screenPadding)
            )
        }
    )
}
