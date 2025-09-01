package com.dark.neuroverse.viewModel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import com.dark.plugins.manager.PluginManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn


class DrawerViewModel : ViewModel() {

    /** All loaded plugins (directly mirrored from PluginManager). */
    val installedPlugins =
        PluginManager.installedPlugins.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val loadedPlugins =
        PluginManager.runningPlugins.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Currently focused/active plugin instance (mirrored). */
    val currentPlugin =
        PluginManager.currentPlugin.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Convenience: active plugin name (null if none). */
    val activePluginName: StateFlow<String?> = currentPlugin.map { it?.api?.getPluginInfo()?.name }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Convenience: true if [activePluginName] is still loaded. */
    val isActiveLoaded: StateFlow<Boolean> = activePluginName.map { name ->
            if (name == null) return@map false
            loadedPlugins.value.any {
                it.api?.getPluginInfo()?.name == name
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    /** If nothing is active but we have plugins, select the first. */
    fun selectFirstIfNone() {
        val current = activePluginName.value
        if (current == null && loadedPlugins.value.isNotEmpty()) {
            loadedPlugins.value.first().api?.getPluginInfo()?.name?.let {
                    setCurrentByName(
                        it
                    )
                }
        }
    }

    fun runPlugin(name: String, context: Context){
        // Check if plugin is already running
        val isAlreadyRunning = loadedPlugins.value.any {
            it.api?.getPluginInfo()?.name == name
        }

        if (!isAlreadyRunning) {
            PluginManager.runPlugin(context, name, Any())
        } else {
            Log.d("PluginHostViewModel", "Plugin $name is already running")
        }
    }

    /** Set the active plugin by its display name. */
    fun setCurrentByName(name: String) {
        PluginManager.setCurrentPluginByName(name)
    }

    /** Stop (unload) a plugin by name. No-op if not found. */
    fun stopPlugin(name: String) {
        PluginManager.stopPlugin(name)
    }

    /** Stop currently active plugin, if any. */
    fun stopCurrent() {
        activePluginName.value?.let { PluginManager.stopPlugin(it) }
    }

    fun deletePlugin(pluginName: String): Boolean {
        return PluginManager.uninstallPlugin(pluginName)
    }

    /** Provide a ViewModelStoreOwner for the current plugin's scoped ViewModels. */
    fun currentStoreOwner(): ViewModelStoreOwner? = activePluginName.value?.let {
        PluginManager.getViewModelStoreOwner(it)
    }

}