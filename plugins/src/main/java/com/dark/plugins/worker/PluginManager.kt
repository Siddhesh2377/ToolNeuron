package com.dark.plugins.worker

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import dalvik.system.InMemoryDexClassLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class Plugin(
    val loadedPlugin: LoadedPlugin?, val job: Job
)

object PluginManager {

    private val pluginScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val pluginViewModelStores = mutableMapOf<String, ViewModelStore>()

    private val _plugins = MutableStateFlow<List<Plugin>>(emptyList())
    val plugins = _plugins.asStateFlow()

    private val _currentPlugin = MutableStateFlow<LoadedPlugin?>(null)
    val currentPlugin = _currentPlugin.asStateFlow()

    fun runPlugin(ctx: Context, pluginName: String, data: Any): LoadedPlugin {
        val loadedPlugin = loadPlugin(pluginName, ctx)

        val api = loadedPlugin.api
        if (api != null) {
            val job = pluginScope.launch {
                try {
                    api.onCreate(data)
                    api.onDestroy()
                } catch (e: Exception) {
                    Log.e("PluginManager", "Plugin execution failed", e)
                }
            }

            val updatedList = _plugins.value.toMutableList().apply {
                add(Plugin(loadedPlugin, job))
            }
            _plugins.value = updatedList
        }

        _currentPlugin.value = loadedPlugin
        return loadedPlugin
    }

    fun stopPlugin(pluginName: String) {
        _plugins.value.filterNot {
            it.loadedPlugin!!.api!!.getPluginInfo().name == pluginName
        }.also { remainingPlugins ->
            _plugins.value.find { it.loadedPlugin!!.api!!.getPluginInfo().name == pluginName }?.job?.cancel()
            _plugins.value = remainingPlugins
        }
    }

    fun getViewModelStoreOwner(pluginName: String): ViewModelStoreOwner {
        return object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore =
                pluginViewModelStores.getOrPut(pluginName) { ViewModelStore() }
        }
    }

    fun clearPluginViewModels() {
        pluginViewModelStores.values.forEach { it.clear() }
        pluginViewModelStores.clear()
    }

    fun cancelAll() {
        pluginScope.coroutineContext.cancelChildren()
        _plugins.value = emptyList()
    }

    fun setCurrentPluginByName(pluginName: String) {
        val plugin = _plugins.value.find {
            it.loadedPlugin?.manifest?.name == pluginName
        }?.loadedPlugin

        _currentPlugin.value = plugin
    }


    private fun loadPlugin(assetZipName: String, ctx: Context): LoadedPlugin {
        return try {
            val (manifest, dexBuf) = loadPluginZipFromAssets(ctx, assetZipName)
            val classLoader = InMemoryDexClassLoader(dexBuf, ctx.classLoader)

            val (pluginInstance, block) = instantiatePlugin(classLoader, manifest.mainClass, ctx)
            LoadedPlugin(manifest, pluginInstance, block, null)
        } catch (t: Throwable) {
            Log.e("PluginManager", "Failed to load plugin", t)
            LoadedPlugin(null, null, null, t)
        }
    }
}
