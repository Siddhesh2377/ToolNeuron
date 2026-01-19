package com.dark.tool_neuron.plugins.api

import com.dark.tool_neuron.models.plugins.PluginInfo
import com.mp.ai_gguf.toolcalling.ToolCall

class SuperPlugin {

    fun getPluginInfo(): PluginInfo {
        return PluginInfo()
    }

    fun executeTool(toolCall: ToolCall): Result<Any>{
        return Result.success(Any())
    }

}