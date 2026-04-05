package com.dark.tool_neuron.ui.screens.home_screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.ui.components.action_window.ActionWindowOverlay
import com.dark.tool_neuron.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    innerPadding: PaddingValues,
    viewModel: HomeViewModel = hiltViewModel(),
    actionWindowExpanded: Boolean,
    onActionWindowDismiss: () -> Unit,
) {
    val expanded by viewModel.actionWindowExpanded.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        // Chat content
        Text(
            text = "No model loaded.\nDownload one to get started.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center)
        )

        // Action window overlay — drops from top, sits above content
        ActionWindowOverlay(
            modelName = "Qwen-1.5B-250M",
            expanded = actionWindowExpanded,
            onDismiss = onActionWindowDismiss
        )
    }
}
