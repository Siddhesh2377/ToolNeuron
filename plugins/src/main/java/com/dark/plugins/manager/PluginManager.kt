package com.dark.plugins.manager

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.dark.plugins.db.PluginDataBaseProvider
import com.dark.plugins.model.InstalledPlugin
import com.dark.plugins.model.LoadedPlugin
import com.dark.plugins.model.Tools
import com.dark.plugins.worker.PluginOps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

object PluginManager {

    private const val TAG = "PluginManager"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val pluginMutex = Mutex()

    @Volatile
    private var isInitialized = false

    @Volatile
    private var pluginViewModelStore: ViewModelStore? = null

    // Direct access to PluginOps flow for better reactivity
    val installedPlugins: StateFlow<List<InstalledPlugin>>
        get() = PluginOps.installedPlugins

    val activePlugin = kotlinx.coroutines.flow.MutableStateFlow<LoadedPlugin?>(null)

    // Cached tools list that updates when plugins change
    val toolsList: StateFlow<List<Pair<String, List<Tools>>>> =
        PluginOps.installedPlugins.map { plugins ->
                plugins.map { it.pluginName to it.tools }
            }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    // Initialization

    fun init(context: Context) {
        if (isInitialized) return

        synchronized(this) {
            if (isInitialized) return

            val dao = PluginDataBaseProvider.getDatabase(context.applicationContext)
                .getInstalledPluginDao()

            PluginOps.init(dao)

            isInitialized = true
            Log.d(TAG, "PluginManager initialized")
        }
    }

    /**
     * Gets current tools list synchronously.
     * Returns cached value from StateFlow.
     */
    fun getTools(): List<Pair<String, List<Tools>>> {
        val tools = toolsList.value
        Log.d(TAG, "getTools: ${tools.size} plugin(s), ${tools.sumOf { it.second.size }} tool(s)")
        return tools
    }

    // Plugin Lifecycle

    suspend fun runPlugin(context: Context, name: String): LoadedPlugin = pluginMutex.withLock {
        stopCurrentPluginUnsafe()

        val pluginPath = installedPlugins.value.find { it.pluginName == name }?.pluginPath
            ?: return LoadedPlugin(
                null, null, null, null, IllegalArgumentException("Plugin not installed: $name")
            )

        val loaded = PluginOps.loadPluginFromFile(File(pluginPath), context)
        val api = loaded.api ?: return loaded.also {
            Log.e(TAG, "Plugin API unavailable: $name", loaded.throwable)
        }

        val job = scope.launch {
            try {
                api.onCreate()
                awaitCancellation()
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e(TAG, "Plugin execution error: $name", e)
            } finally {
                runCatching { api.onDestroy() }.onFailure {
                        Log.e(
                            TAG,
                            "onDestroy failed: $name",
                            it
                        )
                    }
            }
        }

        val running = loaded.copy(job = job)
        activePlugin.value = running

        job.invokeOnCompletion {
            if (activePlugin.value?.pluginName == name) {
                activePlugin.value = null
                clearViewModelStore()
            }
        }

        Log.d(TAG, "Plugin started: $name")
        return running
    }

    fun stopCurrentPlugin() {
        scope.launch {
            pluginMutex.withLock {
                stopCurrentPluginUnsafe()
            }
        }
    }

    private fun stopCurrentPluginUnsafe() {
        activePlugin.value?.let { plugin ->
            plugin.job?.cancel()
            activePlugin.value = null
            clearViewModelStore()
            Log.d(TAG, "Plugin stopped: ${plugin.pluginName}")
        }
    }

    fun getViewModelStoreOwner(): ViewModelStoreOwner = object : ViewModelStoreOwner {
        override val viewModelStore: ViewModelStore
            get() = pluginViewModelStore ?: ViewModelStore().also {
                pluginViewModelStore = it
            }
    }

    private fun clearViewModelStore() {
        pluginViewModelStore?.clear()
        pluginViewModelStore = null
    }

    // Installation & Uninstallation

    fun installFromAssets(context: Context, assets: Array<String>) {
        if (assets.isEmpty()) return
        scope.launch {
            PluginOps.installFromAssets(context, assets)
        }
    }

    fun installFromPath(context: Context, vararg paths: String) {
        if (paths.isEmpty()) return
        scope.launch {
            PluginOps.installFromPath(context, *paths)
        }
    }

    fun uninstall(pluginName: String) {
        scope.launch {
            pluginMutex.withLock {
                if (activePlugin.value?.pluginName == pluginName) {
                    stopCurrentPluginUnsafe()
                }
            }
            PluginOps.uninstallPlugin(pluginName)
        }
    }

    /**
     * Returns the plugin name that provides the given tool name.
     * If no matching tool is found, returns null.
     */
    fun getPluginNameByToolName(toolName: String): String? {
        val allTools = toolsList.value
        for ((pluginName, tools) in allTools) {
            if (tools.any { it.toolName.equals(toolName, ignoreCase = true) }) {
                Log.d(TAG, "Tool '$toolName' belongs to plugin '$pluginName'")
                return pluginName
            }
        }
        Log.w(TAG, "Tool not found: $toolName")
        return null
    }


    private val LoadedPlugin.pluginName: String
        get() = manifest?.name.orEmpty()
}