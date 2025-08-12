package com.dark.plugins.repo

import androidx.compose.ui.graphics.Path
import com.dark.plugins.engine.PluginApi

data class PluginRepo(
    val name: String,
    val path: String,
    val isFromAsset: Boolean
)