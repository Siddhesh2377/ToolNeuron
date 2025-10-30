package com.dark.neuroverse.worker

import android.content.Context
import android.util.Log
import com.dark.ai_module.data.ModelsList
import com.dark.ai_module.workers.ModelManager
import com.dark.neuroverse.model.ToolOutput
import com.dark.neuroverse.util.writeToolOutputJson
import com.dark.plugins.manager.PluginManager
import com.dark.plugins.model.Tools
import com.dark.plugins.worker.ToolRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object ToolCallingManager {
    private const val TAG = "ToolCallingManager"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val toolList: StateFlow<List<Pair<String, List<Tools>>>> = PluginManager.toolsList
    private val _selectedTool = kotlinx.coroutines.flow.MutableStateFlow("" to Tools())
    val selectedTool: StateFlow<Pair<String, Tools>> = _selectedTool

    fun initViewModel() {
        scope.launch {
            toolList.collect { tools ->
                Log.d(TAG, "Tools updated: ${tools.size} plugin(s)")
            }
        }
    }

    fun selectTool(tool: Pair<String, Tools>) {
        _selectedTool.value = tool
        ModelManager.setSystemPrompt(ModelsList.toolCallingSystemPrompt)
        ModelManager.setChatTemplate(ModelsList.toolCallingChatTemplate)
        Log.d(TAG, "Selected tool: ${tool.second.toolName}")
    }

    fun unSelectTool() {
        val wasSelected = _selectedTool.value.first.isNotEmpty()
        _selectedTool.value = "" to Tools()

        if (wasSelected) {
            ModelManager.setSystemPrompt(ModelsList.defaultSystemPrompt)
            ModelManager.setChatTemplate(ModelsList.defaultChatTemplate)
            Log.d(TAG, "Tool unselected")
        }
    }

    fun isToolSelected(): Boolean = _selectedTool.value.first.isNotEmpty()
    fun getSelectedTool(): Tools = _selectedTool.value.second

    fun toolDefinitionBuilder(tool: Tools): JSONArray {
        val properties = JSONObject()
        val required = mutableListOf<String>()

        tool.args.forEach { (key, value) ->
            val type = when (value) {
                is Int, is Double, is Float -> "number"
                is Boolean -> "boolean"
                else -> "string"
            }
            properties.put(key, JSONObject().put("type", type))
            if (value != null) required.add(key)
        }

        val parameters = JSONObject()
            .put("type", "object")
            .put("properties", properties)
            .put("required", JSONArray(required))

        val function = JSONObject()
            .put("name", tool.toolName)
            .put("description", tool.description)
            .put("parameters", parameters)

        return JSONArray().put(
            JSONObject()
                .put("type", "function")
                .put("function", function)
        )
    }

    suspend fun executeTool(
        appContext: Context,
        toolName: String,
        argsJson: String,
        onExecute: (JSONObject) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val repairedToolCall = repairToolCall(toolName, argsJson)
            Log.d(TAG, "Executing tool: ${repairedToolCall.optString("tool")}")

            if (repairedToolCall.has("err")) {
                val error = repairedToolCall.getString("err")
                Log.e(TAG, "Tool repair failed: $error")
                onExecute(JSONObject().put("error", error))
                return@withContext
            }

            val messageId = TextGenerationWorker.currentMsgId.value
            val pluginResult = PluginManager.runPlugin(appContext, _selectedTool.value.first)

            ToolRunner.run(pluginResult, appContext, repairedToolCall) { result ->
                try {
                    ModelManager.setSystemPrompt(ModelsList.defaultSystemPrompt)
                    ModelManager.setChatTemplate(ModelsList.defaultChatTemplate)

                    if (result.has("error")) {
                        val errorMsg = result.getString("error")
                        Log.e(TAG, "Tool execution error: $errorMsg")

                        ChatManager.updateStreamingMessage(
                            messageId = messageId,
                            text = "",
                            toolError = errorMsg,
                            isFinal = true
                        )

                        UIStateManager.setStateIdle()
                        onExecute(JSONObject().put("error", errorMsg))
                        return@run
                    }

                    Log.d(TAG, "Tool executed successfully")
                    val toolOutput = writeToolOutputJson(result.toString()) ?: ToolOutput()
                    ChatManager.updateToolPreview(messageId, toolOutput)
                    UIStateManager.setStateIdle()

                    onExecute(JSONObject().put("success", "Tool execution completed"))

                } catch (e: Exception) {
                    Log.e(TAG, "Error processing tool result", e)
                    UIStateManager.setStateError("Tool result processing failed", cause = e)

                    ChatManager.updateStreamingMessage(
                        messageId = messageId,
                        text = "",
                        toolError = e.message ?: "Unknown error",
                        isFinal = true
                    )

                    onExecute(JSONObject().put("error", e.message))
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Tool execution exception", e)
            onExecute(JSONObject().put("error", e.message))
        }
    }

    private fun repairToolCall(toolName: String, argsJson: String): JSONObject {
        val selectedToolName = _selectedTool.value.second.toolName
        val fallbackTool = toolName.ifBlank { selectedToolName }

        return try {
            val root = JSONObject(argsJson)

            val calls = root.optJSONArray("tool_calls")
            if (calls != null && calls.length() > 0) {
                val firstCall = calls.getJSONObject(0)
                val extractedToolName = firstCall.optString("name").ifBlank { fallbackTool }
                val argObj = firstCall.optJSONObject("arguments")

                return JSONObject().apply {
                    put("tool", extractedToolName)
                    put("args", argObj ?: JSONObject())
                }
            }

            if (root.has("tool") && root.has("args")) {
                return root
            }

            JSONObject().apply {
                put("tool", fallbackTool)
                put("args", root)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error repairing tool call", e)
            JSONObject().put("err", "Tool call parsing failed: ${e.message}")
        }
    }

    fun refreshToolList() {
        val current = toolList.value
        Log.d(TAG, "Tool list: ${current.size} categories")
    }

    fun getToolByName(toolName: String): Tools? {
        return toolList.value
            .flatMap { it.second }
            .firstOrNull { it.toolName == toolName }
    }
}