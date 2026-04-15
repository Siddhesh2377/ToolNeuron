package com.dark.tool_neuron.plugins.api

import androidx.compose.runtime.Composable
import com.dark.tool_neuron.models.plugins.PluginInfo
import com.dark.gguf_lib.toolcalling.ToolCall
import org.json.JSONObject

interface SuperPlugin {

    // This Function is for Plugin Definition
    fun getPluginInfo(): PluginInfo

    //This Function is to execute the called Tool
    suspend fun executeTool(toolCall: ToolCall): Result<Any>

    // Each plugin serializes its own result types to JSON
    fun serializeResult(data: Any): String

    //This is a function that will use internal ViewModel For the Tool, and update the UI
    @Composable
    fun ToolCallUI()

    @Composable
    fun CacheToolUI(data: JSONObject)
}