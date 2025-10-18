package com.dark.neuroverse.viewModel.chatViewModel

import android.content.Context
import android.util.Log
import com.dark.ai_module.data.ModelsList
import com.dark.ai_module.workers.ModelManager
import com.dark.plugins.manager.PluginManager
import com.dark.plugins.model.Tools
import com.dark.plugins.worker.ToolRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

object ToolCallingManager {
    private const val TAG = "ToolCallingViewModel"

    val toolList: MutableStateFlow<List<Pair<String, List<Tools>>>> = MutableStateFlow(emptyList())
    val selectedTools: MutableStateFlow<Pair<String, Tools>> = MutableStateFlow("" to Tools())

    fun initViewModel() {
        toolList.value = PluginManager.toolsList.value
    }

    fun selectTool(tool: Pair<String, Tools>) {
        selectedTools.value = tool
        ModelManager.setSystemPrompt(ModelsList.toolCallingSystemPrompt)
        ModelManager.setChatTemplate(ModelsList.toolCallingChatTemplate)
    }

    fun unSelectTool() {
        selectedTools.value = "" to Tools()
        ModelManager.setSystemPrompt(ModelsList.defaultSystemPrompt)
        ModelManager.setChatTemplate(ModelsList.defaultChatTemplate)
    }

    fun isToolSelected(): Boolean{
        return selectedTools.value.first.isNotEmpty()
    }

    fun getSelectedTool(): Tools {
        return selectedTools.value.second
    }

    suspend fun executeTool(
        appContext: Context, toolName: String, argsJson: String, onExecute: (JSONObject) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val repairedToolCall = repairToolCall(toolName, argsJson)
            Log.d(TAG, "Executing tool: $repairedToolCall")

            val pluginResult = PluginManager.runPlugin(
                appContext, selectedTools.value.first, repairedToolCall.toString()
            )

            ToolRunner.run(pluginResult, appContext, repairedToolCall) { result ->
                ModelManager.setSystemPrompt(ModelsList.defaultSystemPrompt)
                ModelManager.setChatTemplate(ModelsList.defaultChatTemplate)
                onExecute(result)
            }
        } catch (e: Exception) {
            onExecute(JSONObject().apply {
                put("error", e.message)
            })
        }

    }

    private fun repairToolCall(toolName: String, argsJson: String): JSONObject {
        val selectedToolName = selectedTools.value.second.toolName
        val fallbackTool = toolName.ifBlank { selectedToolName }

        return try {
            val root = JSONObject(argsJson)
            val calls = root.optJSONArray("tool_calls")
            val firstCall = calls?.optJSONObject(0)
            val extractedToolName = firstCall?.optString("name").orEmpty().ifBlank { fallbackTool }
            val argObj = firstCall?.optJSONObject("arguments")

            JSONObject().apply {
                put("tool", extractedToolName)
                put("args", argObj)
            }
        } catch (e: Exception) {
            JSONObject().apply {
                put("err", "error: ${e.message}")
            }
        }
    }
}