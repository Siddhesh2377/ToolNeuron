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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages tool selection, execution, and result processing.
 * Handles tool calling system prompt/template switching.
 */
object ToolCallingManager {
    private const val TAG = "ToolCallingManager"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Directly observe PluginManager's tools list (reactive)
    val toolList: StateFlow<List<Pair<String, List<Tools>>>> =
        PluginManager.toolsList

    // Use toolList.value for synchronous access
    private val _selectedTool = kotlinx.coroutines.flow.MutableStateFlow("" to Tools())
    val selectedTool: StateFlow<Pair<String, Tools>> = _selectedTool

    /**
     * Initializes the tool list from PluginManager.
     * Should be called during ViewModel initialization.
     */
    fun initViewModel() {
        // Start observing the flow
        scope.launch {
            toolList.collect { tools ->
                Log.d(TAG, "Tools updated: ${tools.size} plugin(s), ${tools.sumOf { it.second.size }} tool(s)")
                tools.forEach { (pluginName, pluginTools) ->
                    pluginTools.forEach { tool ->
                        Log.d(TAG, "Tool: ${tool.toolName}, description: ${tool.description}")
                    }
                }
            }
        }

        // Log initial state
        val current = toolList.value
        Log.d(TAG, "Initialized with ${current.size} tool categories")
    }

    /**
     * Selects a tool and switches to tool calling mode.
     * Updates model configuration for tool calling.
     */
    fun selectTool(tool: Pair<String, Tools>) {
        _selectedTool.value = tool
        ModelManager.setSystemPrompt(ModelsList.toolCallingSystemPrompt)
        ModelManager.setChatTemplate(ModelsList.toolCallingChatTemplate)
        Log.d(TAG, "Selected tool: ${tool.second.toolName}")
    }

    /**
     * Unselects current tool and reverts to normal conversation mode.
     */
    fun unSelectTool() {
        val wasSelected = _selectedTool.value.first.isNotEmpty()
        _selectedTool.value = "" to Tools()

        if (wasSelected) {
            ModelManager.setSystemPrompt(ModelsList.defaultSystemPrompt)
            ModelManager.setChatTemplate(ModelsList.defaultChatTemplate)
            Log.d(TAG, "Tool unselected, reverted to normal mode")
        }
    }

    /**
     * Checks if a tool is currently selected.
     */
    fun isToolSelected(): Boolean = _selectedTool.value.first.isNotEmpty()

    /**
     * Gets the currently selected tool.
     */
    fun getSelectedTool(): Tools = _selectedTool.value.second

    /**
     * Builds JSON tool definition for the model.
     * Converts tool schema to OpenAI function calling format.
     */
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

    /**
     * Executes a tool with the given arguments.
     * Handles tool execution, error handling, and result processing.
     */
    suspend fun executeTool(
        appContext: Context,
        toolName: String,
        argsJson: String,
        onExecute: (JSONObject) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            // Repair potentially malformed tool call
            val repairedToolCall = repairToolCall(toolName, argsJson)
            Log.d(TAG, "Executing tool: ${repairedToolCall.optString("tool")}")

            // Check for repair errors
            if (repairedToolCall.has("err")) {
                val error = repairedToolCall.getString("err")
                Log.e(TAG, "Tool call repair failed: $error")
                onExecute(JSONObject().put("error", error))
                return@withContext
            }

            val messageId = TextGenerationWorker.currentMsgId.value

            // Run plugin
            val pluginResult = PluginManager.runPlugin(appContext, _selectedTool.value.first)

            // Execute tool and handle result
            ToolRunner.run(pluginResult, appContext, repairedToolCall) { result ->
                try {
                    // Revert to normal mode after tool execution
                    ModelManager.setSystemPrompt(ModelsList.defaultSystemPrompt)
                    ModelManager.setChatTemplate(ModelsList.defaultChatTemplate)

                    // Check for execution errors
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

                    // Success path
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

    /**
     * Repairs potentially malformed tool call JSON.
     * Extracts tool name and arguments from various formats.
     */
    private fun repairToolCall(toolName: String, argsJson: String): JSONObject {
        val selectedToolName = _selectedTool.value.second.toolName
        val fallbackTool = toolName.ifBlank { selectedToolName }

        return try {
            val root = JSONObject(argsJson)

            // Try to extract from tool_calls array format
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

            // Try direct format
            if (root.has("tool") && root.has("args")) {
                return root
            }

            // Fallback: assume entire JSON is arguments
            JSONObject().apply {
                put("tool", fallbackTool)
                put("args", root)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error repairing tool call", e)
            JSONObject().put("err", "Tool call parsing failed: ${e.message}")
        }
    }

    /**
     * Refreshes tool list from PluginManager.
     * Useful after dynamic plugin loading/unloading.
     */
    fun refreshToolList() {
        // toolList is already reactive, just log current state
        val current = toolList.value
        Log.d(TAG, "Tool list: ${current.size} categories")
    }

    /**
     * Gets tool by name for validation or lookup.
     */
    fun getToolByName(toolName: String): Tools? {
        return toolList.value
            .flatMap { it.second }
            .firstOrNull { it.toolName == toolName }
    }
}