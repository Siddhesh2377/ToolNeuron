package com.dark.plugin_exc

import com.dark.plugin_api.Plugin
import com.dark.plugin_api.PluginContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class PluginInstance internal constructor(
    val installed: InstalledPlugin,
    val plugin: Plugin,
    val pluginContext: PluginContext,
    val gate: CapabilityGate,
    internal val classLoader: ClassLoader,
    internal val loader: PluginLoader,
) {

    enum class State { LOADED, STARTED, PAUSED, UNLOADED }

    private val supervisor = SupervisorJob()
    val scope: CoroutineScope = CoroutineScope(supervisor + Dispatchers.Main.immediate)

    @Volatile var state: State = State.LOADED
        private set

    internal fun load() {
        plugin.onLoad(pluginContext)
    }

    fun start() {
        if (state == State.STARTED || state == State.UNLOADED) return
        plugin.onStart()
        state = State.STARTED
    }

    fun pause() {
        if (state != State.STARTED) return
        plugin.onPause()
        state = State.PAUSED
    }

    fun unload() {
        if (state == State.UNLOADED) return
        runCatching { plugin.onUnload() }
        scope.cancel()
        loader.closeClassLoader(classLoader)
        state = State.UNLOADED
    }
}
