package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.models.plugins.PluginInfo
import com.dark.tool_neuron.plugins.PluginManager
import com.dark.gguf_lib.toolcalling.GrammarMode
import com.dark.gguf_lib.toolcalling.ToolCallingConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class PluginViewModel @Inject constructor() : ViewModel() {

    // UI State
    private val _showPluginOverlay = MutableStateFlow(false)
    val showPluginOverlay: StateFlow<Boolean> = _showPluginOverlay.asStateFlow()

    // Plugin lists (from PluginManager)
    val registeredPlugins: StateFlow<List<PluginInfo>> = PluginManager.registeredPlugins
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val enabledPluginNames: StateFlow<Set<String>> = PluginManager.enabledPluginNames
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // Expanded plugins for UI
    private val _expandedPluginIds = MutableStateFlow<Set<String>>(emptySet())
    val expandedPluginIds: StateFlow<Set<String>> = _expandedPluginIds.asStateFlow()

    // Tool calling config state
    val grammarMode: StateFlow<GrammarMode> = PluginManager.grammarMode
    val multiTurnEnabled: StateFlow<Boolean> = PluginManager.multiTurnEnabled
    val toolCallingConfig: StateFlow<ToolCallingConfig> = PluginManager.toolCallingConfig

    // Whether the loaded model supports tool calling
    val isToolCallingModelLoaded: StateFlow<Boolean> = PluginManager.isToolCallingModelLoaded

    // Web Search independent toggle
    val isWebSearchEnabled: StateFlow<Boolean> = PluginManager.isWebSearchEnabled

    // Plugins excluding Web Search (for More Options overlay and Plugin sheet)
    val nonWebSearchPlugins: StateFlow<List<PluginInfo>> = PluginManager.registeredPlugins
        .map { plugins -> plugins.filter { it.name != PluginManager.WEB_SEARCH_PLUGIN_NAME } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ==================== UI Controls ====================

    fun showPluginOverlay() {
        _showPluginOverlay.value = true
    }

    fun hidePluginOverlay() {
        _showPluginOverlay.value = false
    }

    // ==================== Plugin Operations ====================

    fun togglePluginEnabled(pluginName: String, enabled: Boolean) {
        PluginManager.togglePlugin(pluginName, enabled)
    }

    fun toggleWebSearch(enabled: Boolean) {
        PluginManager.enableWebSearch(enabled)
    }

    fun togglePluginExpanded(pluginName: String) {
        _expandedPluginIds.value = if (_expandedPluginIds.value.contains(pluginName)) {
            _expandedPluginIds.value - pluginName
        } else {
            _expandedPluginIds.value + pluginName
        }
    }

    fun getEnabledPlugins(): List<PluginInfo> {
        val enabledNames = enabledPluginNames.value
        return registeredPlugins.value.filter { enabledNames.contains(it.name) }
    }

    // ==================== Tool Calling Config ====================

    fun setGrammarMode(mode: GrammarMode) {
        PluginManager.setGrammarMode(mode)
    }

    fun setMultiTurnEnabled(enabled: Boolean) {
        PluginManager.setMultiTurnEnabled(enabled)
    }

    fun setMaxRounds(maxRounds: Int) {
        val current = PluginManager.getToolCallingConfig()
        PluginManager.updateToolCallingConfig(
            current.copy(maxRounds = maxRounds.coerceIn(1, 10))
        )
    }

    fun setMaxTokensPerTurn(maxTokens: Int) {
        val current = PluginManager.getToolCallingConfig()
        PluginManager.updateToolCallingConfig(
            current.copy(maxTokensPerTurn = maxTokens.coerceIn(64, 2048))
        )
    }
}
