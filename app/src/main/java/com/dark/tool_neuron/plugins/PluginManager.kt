package com.dark.tool_neuron.plugins

import android.util.Log
import com.dark.tool_neuron.models.plugins.PluginExecutionMetrics
import com.dark.tool_neuron.models.plugins.PluginInfo
import com.dark.tool_neuron.models.plugins.PluginResultData
import com.dark.tool_neuron.plugins.api.SuperPlugin
import com.dark.tool_neuron.worker.LlmModelWorker
import com.mp.ai_gguf.toolcalling.GrammarMode
import com.mp.ai_gguf.toolcalling.ToolCall
import com.mp.ai_gguf.toolcalling.ToolCallingConfig
import com.mp.ai_gguf.toolcalling.ToolDefinitionBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.dark.tool_neuron.models.data.HuggingFaceModel
import com.dark.tool_neuron.models.data.ModelType
import org.json.JSONArray
import org.json.JSONObject

object PluginManager {

    private const val TAG = "PluginManager"

    // Web Search is treated as a system tool with its own toggle
    const val WEB_SEARCH_PLUGIN_NAME = "Web Search"

    const val TOOL_CALLING_MODEL_ID = "ruvltra-claude-code-0.5b"
    val TOOL_CALLING_MODEL = HuggingFaceModel(
        id = "ruvltra-claude-code-0.5b",
        name = "Ruvltra Claude Code 0.5B",
        description = "Compact text generation model optimized for tool calling",
        fileUri = "ruv/ruvltra-claude-code/resolve/main/ruvltra-claude-code-0.5b-q4_k_m.gguf",
        approximateSize = "400 MB",
        modelType = ModelType.GGUF,
        isZip = false,
        tags = listOf("GGUF", "Q4_K_M", "Tool Calling"),
        requiresNPU = false,
        repositoryUrl = "ruv/ruvltra-claude-code"
    )

    // Registry of all plugins
    private val _plugins = mutableMapOf<String, SuperPlugin>()

    // O(1) tool name -> plugin key lookup cache
    private val _toolNameToPluginKey = mutableMapOf<String, String>()

    // Cached enabled tool definitions, invalidated on enable/disable
    private var _cachedEnabledToolDefs: List<ToolDefinitionBuilder>? = null

    // Set of enabled plugin names
    private val _enabledPluginNames = MutableStateFlow<Set<String>>(emptySet())
    val enabledPluginNames: StateFlow<Set<String>> = _enabledPluginNames.asStateFlow()

    // Web Search enabled state (independent toggle)
    private val _isWebSearchEnabled = MutableStateFlow(false)
    val isWebSearchEnabled: StateFlow<Boolean> = _isWebSearchEnabled.asStateFlow()

    // List of registered plugins
    private val _registeredPlugins = MutableStateFlow<List<PluginInfo>>(emptyList())
    val registeredPlugins: StateFlow<List<PluginInfo>> = _registeredPlugins.asStateFlow()

    // Tool calling configuration
    private val _toolCallingConfig = MutableStateFlow(ToolCallingConfig())
    val toolCallingConfig: StateFlow<ToolCallingConfig> = _toolCallingConfig.asStateFlow()

    // Grammar mode — always STRICT
    private val _grammarMode = MutableStateFlow(GrammarMode.STRICT)
    val grammarMode: StateFlow<GrammarMode> = _grammarMode.asStateFlow()

    // Premium plugins that require Pro
    private val PREMIUM_PLUGINS = setOf("Web Search", "File Manager", "Dev Utils", "Device Info")

    // Feature gate provider — set by NVApplication at startup
    var featureGateProvider: (() -> Boolean)? = null

    // Whether multi-turn is active
    private val _multiTurnEnabled = MutableStateFlow(true)
    val multiTurnEnabled: StateFlow<Boolean> = _multiTurnEnabled.asStateFlow()

    // Whether the currently loaded model supports tool calling (Qwen/ChatML)
    private val _isToolCallingModelLoaded = MutableStateFlow(false)
    val isToolCallingModelLoaded: StateFlow<Boolean> = _isToolCallingModelLoaded.asStateFlow()

    // Bypass model check for tool calling
    private val _toolCallingBypassEnabled = MutableStateFlow(false)
    val toolCallingBypassEnabled: StateFlow<Boolean> = _toolCallingBypassEnabled.asStateFlow()

    /**
     * Set whether to bypass the tool calling model check.
     * When enabled, tool calling is available for any loaded model.
     */
    fun setToolCallingBypassEnabled(enabled: Boolean) {
        _toolCallingBypassEnabled.value = enabled
        Log.d(TAG, "Tool calling bypass: ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Update whether the loaded model supports tool calling.
     * Should be called when a model is loaded or unloaded.
     * @param nativeSupports true if the model natively supports tool calling (has a chat template)
     */
    fun setToolCallingModelLoaded(nativeSupports: Boolean) {
        _isToolCallingModelLoaded.value = nativeSupports || _toolCallingBypassEnabled.value
    }

    /**
     * Register a plugin
     */
    fun registerPlugin(plugin: SuperPlugin) {
        val pluginInfo = plugin.getPluginInfo()
        _plugins[pluginInfo.name] = plugin

        // Populate tool name -> plugin key cache for O(1) lookup
        pluginInfo.toolDefinitionBuilder.forEach { toolDef ->
            _toolNameToPluginKey[toolDef.name.lowercase()] = pluginInfo.name
        }

        _cachedEnabledToolDefs = null
        updateRegisteredPlugins()
    }

    /**
     * Check if a plugin is premium and requires Pro.
     */
    fun isPremiumPlugin(pluginName: String): Boolean {
        return pluginName in PREMIUM_PLUGINS
    }

    /**
     * Enable a plugin. Non-WebSearch plugins are single-select:
     * enabling one disables all other non-WebSearch plugins.
     * Returns false if the plugin is premium and the user is not Pro.
     */
    fun enablePlugin(pluginName: String): Boolean {
        if (!_plugins.containsKey(pluginName)) return false

        // Check feature gate for premium plugins
        if (isPremiumPlugin(pluginName) && featureGateProvider?.invoke() != true) {
            Log.d(TAG, "Plugin $pluginName requires Pro — blocked")
            return false
        }

        // Single-select for non-WebSearch plugins
        if (pluginName != WEB_SEARCH_PLUGIN_NAME) {
            val toDisable = _enabledPluginNames.value.filter {
                it != WEB_SEARCH_PLUGIN_NAME && it != pluginName
            }
            if (toDisable.isNotEmpty()) {
                _enabledPluginNames.value = _enabledPluginNames.value - toDisable
            }
        }

        _enabledPluginNames.value += pluginName
        _cachedEnabledToolDefs = null
        syncToolsWithLLM()
        return true
    }

    /**
     * Disable a plugin
     */
    fun disablePlugin(pluginName: String) {
        if (!_enabledPluginNames.value.contains(pluginName)) return
        _enabledPluginNames.value -= pluginName
        if (pluginName == WEB_SEARCH_PLUGIN_NAME) {
            _isWebSearchEnabled.value = false
        }
        _cachedEnabledToolDefs = null
        syncToolsWithLLM()
    }

    /**
     * Toggle plugin enabled state
     */
    fun togglePlugin(pluginName: String, enabled: Boolean) {
        if (enabled) {
            enablePlugin(pluginName)
        } else {
            disablePlugin(pluginName)
        }
    }

    /**
     * Toggle Web Search independently (system tool)
     */
    fun enableWebSearch(enabled: Boolean) {
        _isWebSearchEnabled.value = enabled
        if (enabled) {
            if (!_enabledPluginNames.value.contains(WEB_SEARCH_PLUGIN_NAME)) {
                _enabledPluginNames.value += WEB_SEARCH_PLUGIN_NAME
                _cachedEnabledToolDefs = null
                syncToolsWithLLM()
            }
        } else {
            if (_enabledPluginNames.value.contains(WEB_SEARCH_PLUGIN_NAME)) {
                _enabledPluginNames.value -= WEB_SEARCH_PLUGIN_NAME
                _cachedEnabledToolDefs = null
                syncToolsWithLLM()
            }
        }
    }

    /**
     * Set grammar mode for tool calling
     */
    fun setGrammarMode(mode: GrammarMode) {
        _grammarMode.value = mode
        LlmModelWorker.setGrammarModeGguf(mode.value)
        Log.d(TAG, "Grammar mode set to ${mode.name}")
    }

    /**
     * Set multi-turn tool calling enabled state
     */
    fun setMultiTurnEnabled(enabled: Boolean) {
        _multiTurnEnabled.value = enabled
        Log.d(TAG, "Multi-turn tool calling: ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Update tool calling configuration
     */
    fun updateToolCallingConfig(config: ToolCallingConfig) {
        _toolCallingConfig.value = config
        // Re-sync with new config
        syncToolsWithLLM()
    }

    /**
     * Get current tool calling config
     */
    fun getToolCallingConfig(): ToolCallingConfig = _toolCallingConfig.value

    /**
     * Clear grammar constraints (for plan/summary generation phases)
     */
    fun clearGrammar() {
        LlmModelWorker.clearToolsGguf()
        Log.d(TAG, "Grammar cleared for plain text generation")
    }

    /**
     * Restore grammar constraints (for tool call generation phase)
     */
    fun restoreGrammar() {
        syncToolsWithLLM()
        Log.d(TAG, "Grammar restored for tool calling")
    }

    /**
     * Get human-readable tool descriptions with parameter info for LLM prompts
     */
    fun getToolDescriptionsText(): String {
        return getEnabledToolDefinitions().joinToString("\n\n") { toolDef ->
            val openAI = toolDef.build().toOpenAIFormat()
            val name = openAI.optString("name", toolDef.name)
            val desc = openAI.optString("description", "")
            val params = openAI.optJSONObject("parameters")
            val props = params?.optJSONObject("properties")
            val required = params?.optJSONArray("required")
            val requiredSet = mutableSetOf<String>()
            if (required != null) {
                for (i in 0 until required.length()) requiredSet.add(required.getString(i))
            }

            if (props != null && props.length() > 0) {
                val paramLines = props.keys().asSequence().joinToString("\n") { key ->
                    val p = props.getJSONObject(key)
                    val type = p.optString("type", "string")
                    val pDesc = p.optString("description", "")
                    val req = if (requiredSet.contains(key)) "REQUIRED" else "optional"
                    "    $key ($type, $req): $pDesc"
                }
                "- $name: $desc\n  Params:\n$paramLines"
            } else {
                "- $name: $desc"
            }
        }
    }

    /**
     * Get compact tool signatures for Phase 2 (tool call generation)
     */
    fun getToolSignaturesText(): String {
        return getEnabledToolDefinitions().joinToString("\n") { toolDef ->
            val openAI = toolDef.build().toOpenAIFormat()
            val name = openAI.optString("name", toolDef.name)
            val params = openAI.optJSONObject("parameters")
            val props = params?.optJSONObject("properties")
            val required = params?.optJSONArray("required")
            val requiredSet = mutableSetOf<String>()
            if (required != null) {
                for (i in 0 until required.length()) requiredSet.add(required.getString(i))
            }

            if (props != null && props.length() > 0) {
                val paramStr = props.keys().asSequence().joinToString(", ") { key ->
                    val type = props.getJSONObject(key).optString("type", "string")
                    val opt = if (requiredSet.contains(key)) "" else "?"
                    "$key$opt: $type"
                }
                "$name($paramStr)"
            } else {
                "$name()"
            }
        }
    }

    fun getEnabledToolNames(): List<String> {
        return getEnabledToolDefinitions().map { it.name }
    }

    /**
     * Manually sync enabled plugin tools with the LLM.
     * Now uses enableToolCallingGguf() with grammar configuration.
     * Works with any model that has a chat template (model-agnostic).
     */
    fun syncToolsWithLLM() {
        val toolDefinitions = getEnabledToolDefinitions()

        val modelInfo = LlmModelWorker.getGgufModelInfo()
        Log.d(TAG, "Current model info: $modelInfo")

        if (toolDefinitions.isEmpty()) {
            LlmModelWorker.clearToolsGguf()
            Log.d(TAG, "Cleared all tools from LLM")
        } else {
            val toolsJson = convertToolsToJson(toolDefinitions)
            Log.d(TAG, "Tools JSON (${toolsJson.length} chars): $toolsJson")

            val config = _toolCallingConfig.value
            val mode = _grammarMode.value

            val success = LlmModelWorker.enableToolCallingGguf(
                toolsJson = toolsJson,
                grammarMode = mode.value,
                useTypedGrammar = config.useTypedGrammar
            )

            if (success) {
                Log.d(TAG, "Synced ${toolDefinitions.size} tools with LLM " +
                        "(grammar=${mode.name}, typed=${config.useTypedGrammar})")
            } else {
                Log.e(TAG, "Failed to sync tools with LLM")
            }
        }
    }

    /**
     * Convert tool definitions to OpenAI-format JSON
     */
    private fun convertToolsToJson(toolDefinitions: List<ToolDefinitionBuilder>): String {
        val toolsArray = JSONArray()

        for (toolDef in toolDefinitions) {
            val toolJson = JSONObject().apply {
                put("type", "function")
                put("function", toolDef.build().toOpenAIFormat())
            }
            toolsArray.put(toolJson)
        }

        return toolsArray.toString()
    }

    /**
     * Get a plugin by name
     */
    fun getPlugin(pluginName: String): SuperPlugin? {
        return _plugins[pluginName]
    }

    /**
     * Check if a plugin is enabled
     */
    fun isPluginEnabled(pluginName: String): Boolean {
        return _enabledPluginNames.value.contains(pluginName)
    }

    /**
     * Get tool definitions for all enabled plugins (cached)
     */
    fun getEnabledToolDefinitions(): List<ToolDefinitionBuilder> {
        _cachedEnabledToolDefs?.let { return it }
        val defs = _enabledPluginNames.value.flatMap { pluginName ->
            _plugins[pluginName]?.getPluginInfo()?.toolDefinitionBuilder ?: emptyList()
        }
        _cachedEnabledToolDefs = defs
        return defs
    }

    /**
     * Check if any tools are currently enabled
     */
    fun hasEnabledTools(): Boolean = getEnabledToolDefinitions().isNotEmpty()

    /**
     * Execute a tool call and return result in SDK ToolResult format for multi-turn.
     * Returns a JSON string that can be appended as a "tool" message in the conversation.
     */
    suspend fun executeToolForMultiTurn(toolCall: ToolCall): MultiTurnToolResult {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Multi-turn tool call: ${toolCall.name} with args: ${toolCall.arguments}")

        // O(1) lookup via cached tool name -> plugin key map
        val pluginKey = _toolNameToPluginKey[toolCall.name.lowercase()]
        val plugin = pluginKey?.let { _plugins[it] }

        if (plugin == null) {
            Log.e(TAG, "Tool not found: ${toolCall.name}")
            return MultiTurnToolResult(
                toolName = toolCall.name,
                resultJson = """{"error": "Tool not found: ${toolCall.name}"}""",
                isError = true,
                pluginName = "Unknown",
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }

        val pluginInfo = plugin.getPluginInfo()

        if (!isPluginEnabled(pluginInfo.name)) {
            return MultiTurnToolResult(
                toolName = toolCall.name,
                resultJson = """{"error": "Plugin not enabled: ${pluginInfo.name}"}""",
                isError = true,
                pluginName = pluginInfo.name,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }

        return try {
            val result = plugin.executeTool(toolCall)
            val executionTime = System.currentTimeMillis() - startTime

            if (result.isSuccess) {
                val data = result.getOrNull()
                val resultJson = convertDataToJson(data)
                MultiTurnToolResult(
                    toolName = toolCall.name,
                    resultJson = resultJson,
                    isError = false,
                    pluginName = pluginInfo.name,
                    executionTimeMs = executionTime,
                    rawData = data
                )
            } else {
                val error = result.exceptionOrNull()
                MultiTurnToolResult(
                    toolName = toolCall.name,
                    resultJson = """{"error": "${error?.message ?: "Unknown error"}"}""",
                    isError = true,
                    pluginName = pluginInfo.name,
                    executionTimeMs = executionTime
                )
            }
        } catch (e: Exception) {
            val executionTime = System.currentTimeMillis() - startTime
            MultiTurnToolResult(
                toolName = toolCall.name,
                resultJson = """{"error": "${e.message ?: "Unknown error"}"}""",
                isError = true,
                pluginName = pluginInfo.name,
                executionTimeMs = executionTime
            )
        }
    }

    /**
     * Execute a tool call (legacy single-turn API)
     */
    suspend fun executeTool(toolCall: ToolCall): PluginExecutionResult {
        val startTime = System.currentTimeMillis()

        Log.d(TAG, "Tool call received: ${toolCall.name} with args: ${toolCall.arguments}")

        // O(1) lookup via cached tool name -> plugin key map
        val pluginKey = _toolNameToPluginKey[toolCall.name.lowercase()]
        val plugin = pluginKey?.let { _plugins[it] }

        if (plugin == null) {
            Log.e(TAG, "Tool not found: ${toolCall.name}")
            Log.d(TAG, "Available tools: ${_toolNameToPluginKey.keys}")
            val metrics = PluginExecutionMetrics(
                pluginName = "Unknown",
                toolName = toolCall.name,
                executionTimeMs = System.currentTimeMillis() - startTime,
                success = false,
                errorMessage = "Tool not found: ${toolCall.name}"
            )
            return PluginExecutionResult.Failure(metrics, null)
        }

        val pluginInfo = plugin.getPluginInfo()

        if (!isPluginEnabled(pluginInfo.name)) {
            val metrics = PluginExecutionMetrics(
                pluginName = pluginInfo.name,
                toolName = toolCall.name,
                executionTimeMs = System.currentTimeMillis() - startTime,
                success = false,
                errorMessage = "Plugin is not enabled: ${pluginInfo.name}"
            )
            return PluginExecutionResult.Failure(metrics, null)
        }

        return try {
            val result = plugin.executeTool(toolCall)
            val executionTime = System.currentTimeMillis() - startTime

            if (result.isSuccess) {
                val data = result.getOrNull()
                val metrics = PluginExecutionMetrics(
                    pluginName = pluginInfo.name,
                    toolName = toolCall.name,
                    executionTimeMs = executionTime,
                    success = true
                )

                val resultDataJson = convertDataToJson(data)

                val resultData = PluginResultData(
                    pluginName = pluginInfo.name,
                    toolName = toolCall.name,
                    inputParams = toolCall.arguments.toString(),
                    resultData = resultDataJson,
                    success = true
                )

                PluginExecutionResult.Success(metrics, resultData, data!!)
            } else {
                val error = result.exceptionOrNull()
                val metrics = PluginExecutionMetrics(
                    pluginName = pluginInfo.name,
                    toolName = toolCall.name,
                    executionTimeMs = executionTime,
                    success = false,
                    errorMessage = error?.message ?: "Unknown error"
                )

                val resultData = PluginResultData(
                    pluginName = pluginInfo.name,
                    toolName = toolCall.name,
                    inputParams = toolCall.arguments.toString(),
                    resultData = error?.message ?: "Unknown error",
                    success = false
                )

                PluginExecutionResult.Failure(metrics, resultData)
            }
        } catch (e: Exception) {
            val executionTime = System.currentTimeMillis() - startTime
            val metrics = PluginExecutionMetrics(
                pluginName = pluginInfo.name,
                toolName = toolCall.name,
                executionTimeMs = executionTime,
                success = false,
                errorMessage = e.message ?: "Unknown error"
            )

            val resultData = PluginResultData(
                pluginName = pluginInfo.name,
                toolName = toolCall.name,
                inputParams = toolCall.arguments.toString(),
                resultData = e.message ?: "Unknown error",
                success = false
            )

            PluginExecutionResult.Failure(metrics, resultData)
        }
    }

    /**
     * Update the list of registered plugins
     */
    private fun updateRegisteredPlugins() {
        _registeredPlugins.value = _plugins.values.map { it.getPluginInfo() }
    }

    /**
     * Convert plugin result data to JSON string
     */
    private fun convertDataToJson(data: Any?): String {
        if (data == null) return "{}"

        return try {
            when (data) {
                is com.dark.tool_neuron.models.plugins.DuckDuckGoSearchResponse -> {
                    JSONObject().apply {
                        put("query", data.query)
                        put("totalResults", data.totalResults)
                        put("searchTime", data.searchTime)

                        val resultsArray = JSONArray()
                        data.results.forEach { result ->
                            resultsArray.put(JSONObject().apply {
                                put("title", result.title)
                                put("snippet", result.snippet)
                                put("url", result.url)
                                put("position", result.position)
                            })
                        }
                        put("results", resultsArray)
                    }.toString()
                }
                is com.dark.tool_neuron.models.plugins.ScrapedContent -> {
                    JSONObject().apply {
                        put("url", data.url)
                        put("title", data.title)
                        put("content", data.content)
                        put("contentLength", data.contentLength)
                        put("fetchTime", data.fetchTime)

                        val metadataObj = JSONObject()
                        data.metadata.forEach { (key, value) ->
                            metadataObj.put(key, value)
                        }
                        put("metadata", metadataObj)
                    }.toString()
                }
                is DeviceInfoResponse -> {
                    JSONObject().apply {
                        put("infoType", data.infoType)
                        val infoObj = JSONObject()
                        data.info.forEach { (key, value) -> infoObj.put(key, value) }
                        put("info", infoObj)
                    }.toString()
                }
                is FileManagerResponse -> {
                    JSONObject().apply {
                        put("tool", data.tool)
                        put("path", data.path)
                        put("content", data.content)
                        put("fileCount", data.fileCount)
                        put("success", data.success)
                    }.toString()
                }
                else -> {
                    Log.w(TAG, "Unknown data type: ${data::class.simpleName}, using toString()")
                    data.toString()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert data to JSON: ${e.message}")
            data.toString()
        }
    }
}

/**
 * Result from multi-turn tool execution
 */
data class MultiTurnToolResult(
    val toolName: String,
    val resultJson: String,
    val isError: Boolean,
    val pluginName: String,
    val executionTimeMs: Long,
    val rawData: Any? = null
)

/**
 * Result of plugin execution (legacy)
 */
sealed class PluginExecutionResult {
    data class Success(
        val metrics: PluginExecutionMetrics,
        val resultData: PluginResultData,
        val data: Any
    ) : PluginExecutionResult()

    data class Failure(
        val metrics: PluginExecutionMetrics,
        val resultData: PluginResultData?
    ) : PluginExecutionResult()
}
