package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import com.dark.tool_neuron.plugin.PluginRegistry
import com.dark.tool_neuron.plugin.api.Plugin
import com.dark.tool_neuron.repo.PluginPrefsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

@HiltViewModel
class PluginHubViewModel @Inject constructor(
    registry: PluginRegistry,
    private val prefs: PluginPrefsRepository,
) : ViewModel() {

    val plugins: List<Plugin> = registry.all

    val enabledIds: StateFlow<Set<String>> = prefs.all
        .map { map -> map.values.filter { it.enabled }.map { it.pluginId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, prefs.enabledIds())

    fun setEnabled(pluginId: String, enabled: Boolean) {
        prefs.setEnabled(pluginId, enabled)
    }
}
