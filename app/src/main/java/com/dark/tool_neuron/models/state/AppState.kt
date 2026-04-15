package com.dark.tool_neuron.models.state

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.dark.tool_neuron.plugins.PluginManager
import com.dark.tool_neuron.ui.icons.TnIcons

sealed class AppState {
    // Idle states
    data object Welcome : AppState() // No model loaded, no messages
    data object NoModelLoaded : AppState() // No model loaded, has messages
    data class ModelLoaded(val modelName: String) : AppState() // Model loaded, idle
    
    // Loading states
    data class LoadingModel(val modelName: String, val progress: Float = 0f) : AppState()
    
    // Active states
    data class GeneratingText(val modelName: String) : AppState()
    data class GeneratingImage(val modelName: String) : AppState()
    data class GeneratingAudio(val modelName: String) : AppState()
    data class ExecutingPlugin(val pluginName: String, val toolName: String) : AppState()
    data class PluginExecutionComplete(
        val pluginName: String,
        val toolName: String,
        val success: Boolean,
        val executionTimeMs: Long,
        val errorMessage: String? = null
    ) : AppState()

    // Error states
    data class Error(val message: String, val modelName: String? = null) : AppState()
}

// Helper extension to get display text
fun AppState.getDisplayText(): String = when (this) {
    is AppState.Welcome -> "Hey, Welcome User"
    is AppState.NoModelLoaded -> "No Model Loaded"
    is AppState.ModelLoaded -> "Ready: $modelName"
    is AppState.LoadingModel -> "Loading: $modelName"
    is AppState.GeneratingText -> {
        // Show "Looking for Tool" if any plugins are enabled
        val hasToolsEnabled = PluginManager.enabledPluginNames.value.isNotEmpty()
        if (hasToolsEnabled) "Looking for Tool" else "Generating Text"
    }
    is AppState.GeneratingImage -> "Creating Image"
    is AppState.GeneratingAudio -> "Generating Audio"
    is AppState.ExecutingPlugin -> "Executing Tool"
    is AppState.PluginExecutionComplete -> if (success) "Tool Completed" else "Tool Failed"
    is AppState.Error -> "Error: $message"
}

fun AppState.getIcon(): ImageVector = when (this) {
    is AppState.Welcome -> TnIcons.User
    is AppState.NoModelLoaded -> TnIcons.Photo
    is AppState.ModelLoaded -> TnIcons.Sparkles
    is AppState.LoadingModel -> TnIcons.Settings
    is AppState.GeneratingText,
    is AppState.GeneratingImage,
    is AppState.GeneratingAudio,
    is AppState.ExecutingPlugin,
    is AppState.PluginExecutionComplete -> TnIcons.Wrench
    is AppState.Error -> TnIcons.AlertTriangle
}

@Composable
fun AppState.getColor(): Color = when (this) {
    is AppState.Welcome -> MaterialTheme.colorScheme.primary

    is AppState.NoModelLoaded -> MaterialTheme.colorScheme.onSurfaceVariant

    is AppState.ModelLoaded -> MaterialTheme.colorScheme.tertiary

    is AppState.LoadingModel -> MaterialTheme.colorScheme.secondary

    is AppState.GeneratingText -> MaterialTheme.colorScheme.primary

    is AppState.GeneratingImage -> MaterialTheme.colorScheme.tertiary

    is AppState.GeneratingAudio -> MaterialTheme.colorScheme.secondary

    is AppState.ExecutingPlugin -> MaterialTheme.colorScheme.tertiary

    is AppState.PluginExecutionComplete -> if (success) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.error
    }

    is AppState.Error -> MaterialTheme.colorScheme.error
}

@Composable
fun AppState.getBackgroundColor(): Color = when (this) {
    is AppState.Welcome -> MaterialTheme.colorScheme.primaryContainer

    is AppState.NoModelLoaded -> MaterialTheme.colorScheme.surfaceVariant

    is AppState.ModelLoaded -> MaterialTheme.colorScheme.tertiaryContainer

    is AppState.LoadingModel -> MaterialTheme.colorScheme.secondaryContainer

    is AppState.GeneratingText -> MaterialTheme.colorScheme.primaryContainer

    is AppState.GeneratingImage -> MaterialTheme.colorScheme.tertiaryContainer

    is AppState.GeneratingAudio -> MaterialTheme.colorScheme.secondaryContainer

    is AppState.ExecutingPlugin -> MaterialTheme.colorScheme.tertiaryContainer

    is AppState.PluginExecutionComplete -> if (success) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }

    is AppState.Error -> MaterialTheme.colorScheme.errorContainer
}

@Composable
fun AppState.getContentColor(): Color = when (this) {
    is AppState.Welcome -> MaterialTheme.colorScheme.onPrimaryContainer

    is AppState.NoModelLoaded -> MaterialTheme.colorScheme.onSurfaceVariant

    is AppState.ModelLoaded -> MaterialTheme.colorScheme.onTertiaryContainer

    is AppState.LoadingModel -> MaterialTheme.colorScheme.onSecondaryContainer

    is AppState.GeneratingText -> MaterialTheme.colorScheme.onPrimaryContainer

    is AppState.GeneratingImage -> MaterialTheme.colorScheme.onTertiaryContainer

    is AppState.GeneratingAudio -> MaterialTheme.colorScheme.onSecondaryContainer

    is AppState.ExecutingPlugin -> MaterialTheme.colorScheme.onTertiaryContainer

    is AppState.PluginExecutionComplete -> if (success) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }

    is AppState.Error -> MaterialTheme.colorScheme.onErrorContainer
}