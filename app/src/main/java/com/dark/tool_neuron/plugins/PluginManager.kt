package com.dark.tool_neuron.plugins

import android.util.Log
import com.dark.tool_neuron.models.plugins.PluginInfo
import com.dark.tool_neuron.plugins.api.SuperPlugin
import com.dark.tool_neuron.worker.LlmModelWorker
import com.dark.gguf_lib.toolcalling.GrammarMode
import com.dark.gguf_lib.toolcalling.ToolCall
import com.dark.gguf_lib.toolcalling.ToolCallingConfig
import com.dark.gguf_lib.toolcalling.ToolDefinitionBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.dark.tool_neuron.models.data.HuggingFaceModel
import com.dark.tool_neuron.models.data.ModelType
import java.util.concurrent.ConcurrentHashMap
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

    // Registry of all plugins (thread-safe)
    private val _plugins = ConcurrentHashMap<String, SuperPlugin>()

    // O(1) tool name -> plugin key lookup cache (thread-safe)
    private val _toolNameToPluginKey = ConcurrentHashMap<String, String>()

    // Cached enabled tool definitions and JSON, invalidated on enable/disable
    @Volatile private var _cachedEnabledToolDefs: List<ToolDefinitionBuilder>? = null

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

    // Whether multi-turn is active
    private val _multiTurnEnabled = MutableStateFlow(true)
    val multiTurnEnabled: StateFlow<Boolean> = _multiTurnEnabled.asStateFlow()

    // Whether the currently loaded model supports tool calling (Qwen/ChatML)
    private val _isToolCallingModelLoaded = MutableStateFlow(false)
    val isToolCallingModelLoaded: StateFlow<Boolean> = _isToolCallingModelLoaded.asStateFlow()

    // Bypass model check for tool calling
    private val _toolCallingBypassEnabled = MutableStateFlow(false)

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
     * Enable a plugin. Multiple plugins can be active simultaneously.
     */
    fun enablePlugin(pluginName: String) {
        if (!_plugins.containsKey(pluginName)) return
        _enabledPluginNames.update { it + pluginName }
        _cachedEnabledToolDefs = null

        syncToolsWithLLM()
    }

    /**
     * Disable a plugin
     */
    fun disablePlugin(pluginName: String) {
        if (!_enabledPluginNames.value.contains(pluginName)) return
        _enabledPluginNames.update { it - pluginName }
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
        val changed = _enabledPluginNames.value.contains(WEB_SEARCH_PLUGIN_NAME) != enabled
        if (changed) {
            _enabledPluginNames.update { current ->
                if (enabled) current + WEB_SEARCH_PLUGIN_NAME
                else current - WEB_SEARCH_PLUGIN_NAME
            }
            _cachedEnabledToolDefs = null

            syncToolsWithLLM()
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

        if (toolDefinitions.isEmpty()) {
            LlmModelWorker.clearToolsGguf()
            Log.d(TAG, "Cleared all tools from LLM")
        } else {
            val mode = _grammarMode.value
            val config = ToolCallingConfig(
                grammarMode = mode,
                useTypedGrammar = _toolCallingConfig.value.useTypedGrammar
            )

            // Use direct same-process path — properly enables grammar constraints
            val success = LlmModelWorker.enableToolCallingDirect(toolDefinitions, config)

            if (success) {
                // With STRICT grammar, any model can do tool calling — mark as loaded
                if (mode == GrammarMode.STRICT && !_isToolCallingModelLoaded.value) {
                    _isToolCallingModelLoaded.value = true
                    Log.d(TAG, "Tool calling force-enabled via STRICT grammar")
                }
                Log.d(TAG, "Synced ${toolDefinitions.size} tools with LLM " +
                        "(grammar=${mode.name}, typed=${config.useTypedGrammar})")
            } else {
                Log.e(TAG, "Failed to sync tools with LLM")
            }
        }
    }

    /**
     * Get a plugin by name
     */
    fun getPlugin(pluginName: String): SuperPlugin? {
        return _plugins[pluginName]
    }

    /**
     * Get tool definitions for all enabled plugins (cached)
     */
    fun getEnabledToolDefinitions(): List<ToolDefinitionBuilder> {
        _cachedEnabledToolDefs?.let { return it }
        synchronized(this) {
            _cachedEnabledToolDefs?.let { return it }
            val defs = _enabledPluginNames.value.flatMap { pluginName ->
                _plugins[pluginName]?.getPluginInfo()?.toolDefinitionBuilder ?: emptyList()
            }
            _cachedEnabledToolDefs = defs
            return defs
        }
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

        if (!_enabledPluginNames.value.contains(pluginInfo.name)) {
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
                val resultJson = convertDataToJson(data, pluginKey)
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
     * Update the list of registered plugins
     */
    private fun updateRegisteredPlugins() {
        _registeredPlugins.value = _plugins.values.map { it.getPluginInfo() }
    }

    /**
     * Convert plugin result data to JSON string.
     * Delegates to each plugin's serializeResult() method.
     */
    private fun convertDataToJson(data: Any?, pluginKey: String? = null): String {
        if (data == null) return "{}"
        return try {
            val plugin = pluginKey?.let { _plugins[it] }
            plugin?.serializeResult(data) ?: data.toString()
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

