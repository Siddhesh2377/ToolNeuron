package com.dark.tool_neuron.models.plugins

import com.mp.ai_gguf.toolcalling.ToolDefinitionBuilder

data class PluginInfo(
    val name: String = "",
    val description: String = "",
    val author: String = "",
    val version: String = "",
    val toolDefinitionBuilder: List<ToolDefinitionBuilder> = emptyList()
)

