package com.dark.tool_neuron.ui.screen.home

import android.content.Intent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.activity.ModelPickerActivity
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.AnimatedTitle
import com.dark.tool_neuron.ui.icons.TnIcons

// ── TopBar ──────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TopBar(
    onMenuClick: () -> Unit,
    onSettingsClick: () -> Unit,
    showDynamicWindow: () -> Unit,
    onStoreButtonClicked: () -> Unit
) {
    val context = LocalContext.current

    CenterAlignedTopAppBar(title = {
        AnimatedTitle(
            modifier = Modifier, onShowDynamicWindow = {
                showDynamicWindow()
            })
    }, navigationIcon = {
        ActionButton(
            onClickListener = onMenuClick,
            icon = TnIcons.Menu,
            modifier = Modifier.padding(start = 6.dp)
        )
    }, actions = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActionButton(
                onClickListener = onSettingsClick,
                icon = TnIcons.Settings,
                modifier = Modifier.padding(end = 6.dp)
            )
            ActionButton(
                onClickListener = {
                    onStoreButtonClicked()
                }, icon = TnIcons.Download, modifier = Modifier.padding(end = 6.dp)
            )
            ActionButton(
                onClickListener = {
                    // Open ModelPickerScreen with GGUF / VLM tabs
                    context.startActivity(Intent(context, ModelPickerActivity::class.java))
                }, icon = TnIcons.Upload, modifier = Modifier.padding(end = 6.dp)
            )
        }
    })
}
