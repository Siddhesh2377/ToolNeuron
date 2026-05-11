package com.dark.tool_neuron.plugin

import com.dark.tool_neuron.plugin.api.Plugin
import com.dark.tool_neuron.plugin.api.ToolDef

class PluginRegistry(
    plugins: List<Plugin>,
) {
    val all: List<Plugin> = plugins.distinctBy { it.id }

    private val byId: Map<String, Plugin> = all.associateBy { it.id }

    private val byToolName: Map<String, Plugin> =
        all.flatMap { p -> p.tools.map { t -> t.name to p } }.toMap()

    fun get(id: String): Plugin? = byId[id]

    fun pluginForTool(toolName: String): Plugin? = byToolName[toolName]

    fun enabledToolDefs(enabledIds: Set<String>): List<ToolDef> =
        all.filter { it.id in enabledIds }.flatMap { it.tools }
}
