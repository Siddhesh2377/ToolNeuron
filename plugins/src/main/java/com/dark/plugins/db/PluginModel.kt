package com.dark.plugins.db

import com.dark.plugins.engine.PluginApi
import com.dark.plugins.engine.PluginInfo
import com.dark.plugins.engine.PluginManifest

data class PluginModel (
    val pluginName: String?,
    val api: PluginApi?,
    val manifest: PluginManifest?
)