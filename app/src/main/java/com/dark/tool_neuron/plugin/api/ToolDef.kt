package com.dark.tool_neuron.plugin.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ToolDef(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)
