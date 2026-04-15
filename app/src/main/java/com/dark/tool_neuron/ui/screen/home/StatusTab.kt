package com.dark.tool_neuron.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.models.state.AppState
import com.dark.tool_neuron.viewmodel.ChatViewModel
import com.dark.tool_neuron.global.Standards

// ── Status Tab ──

@Composable
internal fun StatusTabContent(
    appState: AppState,
    chatViewModel: ChatViewModel,
    loadedRagCount: Int = 0,
    enabledToolCount: Int = 0,
    isMemoryEnabled: Boolean = false,
    ttsModelLoaded: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
    ) {
        when (appState) {
            is AppState.Welcome -> WelcomeContent()
            is AppState.NoModelLoaded -> NoModelLoadedContent()
            is AppState.ModelLoaded -> ModelLoadedContent(appState)
            is AppState.LoadingModel -> LoadingModelContent(appState)
            is AppState.GeneratingText -> GeneratingTextContent(appState, chatViewModel)
            is AppState.GeneratingImage -> GeneratingImageContent(appState, chatViewModel)
            is AppState.GeneratingAudio -> GeneratingAudioContent()
            is AppState.ExecutingPlugin -> ExecutingPluginContent(appState)
            is AppState.PluginExecutionComplete -> PluginExecutionCompleteContent(appState)
            is AppState.Error -> ErrorContent(appState)
        }

        val hasActiveSubsystems = loadedRagCount > 0 || enabledToolCount > 0 || isMemoryEnabled || ttsModelLoaded
        if (hasActiveSubsystems) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                thickness = 0.5.dp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (loadedRagCount > 0) {
                    CompactBadge("RAG ($loadedRagCount)", MaterialTheme.colorScheme.primary)
                }
                if (enabledToolCount > 0) {
                    CompactBadge("Tools ($enabledToolCount)", MaterialTheme.colorScheme.tertiary)
                }
                if (isMemoryEnabled) {
                    CompactBadge("Memory", MaterialTheme.colorScheme.secondary)
                }
                if (ttsModelLoaded) {
                    CompactBadge("TTS", MaterialTheme.colorScheme.tertiary)
                }
            }
        }
    }
}

@Composable
internal fun CompactBadge(text: String, color: androidx.compose.ui.graphics.Color) {
    Surface(
        shape = RoundedCornerShape(Standards.SpacingXs),
        color = color.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = Standards.SpacingXxs),
            horizontalArrangement = Arrangement.spacedBy(Standards.SpacingXs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .background(color, CircleShape)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
