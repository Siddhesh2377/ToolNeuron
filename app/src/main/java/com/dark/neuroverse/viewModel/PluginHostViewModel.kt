package com.dark.neuroverse.viewModel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import com.dark.ai_module.workers.ModelManager
import com.dark.plugins.worker.PluginManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File


class PluginHostViewModel : ViewModel() {

    /** All loaded plugins (directly mirrored from PluginManager). */
    val loadedPlugins =
        PluginManager.plugins.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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
                it.loadedPlugin?.api?.getPluginInfo()?.name == name
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /* ----------------------------------------------------------------- *//*  Public API                                                        *//* ----------------------------------------------------------------- */

    /** Load default model & bootstrap plugins once. Safe to call repeatedly. */
    fun ensureInitialLoad(appContext: Context) {
        viewModelScope.launch {
            ModelManager.getModel("Qwen3-Zero-Coder-Reasoning-0.8B")?.let { mdl ->
                ModelManager.loadModel(appContext, mdl) {
                    if (loadedPlugins.value.isEmpty()) {
                        listOf(
                            "app-io-plugin.zip", "demo-macro-plugin.zip", "ai-chat-plugin.zip"
                        ).forEach { runPluginZip(appContext, it) }
                    }
                }
            }
        }
    }

    fun loadPlugin(appContext: Context, file: File) {
        viewModelScope.launch {
            runPluginZip(appContext = appContext, path = file)
        }
    }

    /** If nothing is active but we have plugins, select the first. */
    fun selectFirstIfNone() {
        val current = activePluginName.value
        if (current == null && loadedPlugins.value.isNotEmpty()) {
            loadedPlugins.value.first().loadedPlugin?.api?.getPluginInfo()?.name?.let {
                    setCurrentByName(
                        it
                    )
                }
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

    /** Run a packaged plugin (.zip) from assets with optional args. */
    fun runPluginZip(appContext: Context, zipFileName: String, args: Any = Unit) {
        PluginManager.runPlugin(appContext, zipFileName, args)
    }

    /** Run a packaged plugin (.zip) from a file with optional args. */
    fun runPluginZip(appContext: Context, path: File, args: Any = Unit) {
        PluginManager.runPlugin(appContext, path, args)
    }

    /** Provide a ViewModelStoreOwner for the current plugin's scoped ViewModels. */
    fun currentStoreOwner(): ViewModelStoreOwner? = activePluginName.value?.let {
        PluginManager.getViewModelStoreOwner(it)
    }

}