package com.dark.tool_neuron.ui.screens.server

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerTopBar() {
    TopAppBar(
        title = { Text("Remote Server") },
        modifier = Modifier.fillMaxWidth(),
    )
}
