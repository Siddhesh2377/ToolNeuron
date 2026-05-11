package com.dark.plugin_exc

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class PluginRegistry internal constructor(private val loader: PluginLoader) {

    private val _installed = MutableStateFlow<List<InstalledPlugin>>(emptyList())
    val installed: StateFlow<List<InstalledPlugin>> = _installed

    init {
        refresh()
    }

    fun refresh() {
        _installed.value = loader.installed().sortedBy { it.manifest.name.lowercase() }
    }

    fun find(pluginId: String): InstalledPlugin? =
        _installed.value.firstOrNull { it.manifest.id == pluginId }

    internal fun remove(pluginId: String) {
        _installed.update { list -> list.filterNot { it.manifest.id == pluginId } }
    }
}
