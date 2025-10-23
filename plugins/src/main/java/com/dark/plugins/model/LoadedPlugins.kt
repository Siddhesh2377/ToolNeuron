package com.dark.plugins.model

import androidx.compose.runtime.Composable
import com.mp.plugin_api.api.PluginApi
import kotlinx.coroutines.Job

data class LoadedPlugin(
    val job: Job? = null,
    val manifest: PluginManifest? = null,
    val api: PluginApi? = null,
    val content: (@Composable () -> Unit)? = null,
    val throwable: Throwable? = null
)