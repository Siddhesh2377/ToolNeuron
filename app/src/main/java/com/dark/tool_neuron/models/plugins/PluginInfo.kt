package com.dark.tool_neuron.models.plugins

import com.dark.gguf_lib.toolcalling.ToolDefinitionBuilder

data class PluginInfo(
    val name: String = "",
    val description: String = "",
    val author: String = "",
    val version: String = "",
    val toolDefinitionBuilder: List<ToolDefinitionBuilder> = emptyList()
)

