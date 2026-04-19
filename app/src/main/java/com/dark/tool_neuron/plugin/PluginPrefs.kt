package com.dark.tool_neuron.plugin

data class PluginPrefs(
    val pluginId: String,
    val enabled: Boolean,
    val configJson: String = "{}",
)
