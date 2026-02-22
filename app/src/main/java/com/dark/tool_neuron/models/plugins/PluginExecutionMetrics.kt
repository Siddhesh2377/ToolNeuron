package com.dark.tool_neuron.models.plugins

import kotlinx.serialization.Serializable

@Serializable
data class PluginExecutionMetrics(
    val pluginName: String,
    val toolName: String,
    val executionTimeMs: Long,
    val success: Boolean,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class PluginResultData(
    val pluginName: String,
    val toolName: String,
    val inputParams: String,  // JSON string
    val resultData: String,   // JSON string
    val success: Boolean
)
