package com.dark.plugin_exc

import ai.onnxruntime.OrtEnvironment
import android.content.Context
import com.dark.hxs.HexStorage
import com.dark.plugin_api.PluginContext
import com.dark.plugin_exc.api.HxsApiImpl
import com.dark.plugin_exc.api.NetworkApiImpl
import com.dark.plugin_exc.api.OnnxApiImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.InputStream

class PluginExecutor(
    private val appContext: Context,
    private val hxs: HexStorage,
) {

    private val loader = PluginLoader(appContext)
    val registry: PluginRegistry = PluginRegistry(loader)

    private val instances = HashMap<String, PluginInstance>()

    private val _openPlugins = MutableStateFlow<List<String>>(emptyList())
    val openPlugins: StateFlow<List<String>> = _openPlugins

    private val _activePlugin = MutableStateFlow<String?>(null)
    val activePlugin: StateFlow<String?> = _activePlugin

    private val ortEnv: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    @Volatile
    var onnxExecutionProvider: String = "cpu"

    fun install(stream: InputStream): InstalledPlugin {
        val installed = loader.installFromStream(stream)
        registry.refresh()
        return installed
    }

    fun uninstall(pluginId: String) {
        close(pluginId)
        runCatching { hxs.dropCollection("plugin_$pluginId") }
        loader.uninstall(pluginId)
        registry.remove(pluginId)
    }

    @Synchronized
    fun open(pluginId: String): PluginInstance {
        instances[pluginId]?.let { existing ->
            promoteActive(pluginId)
            existing.start()
            return existing
        }
        val installed = registry.find(pluginId)
            ?: throw IllegalArgumentException("plugin not installed: $pluginId")
        val gate = CapabilityGate(installed.manifest)
        val pluginCtx = PluginContext(
            pluginId = installed.manifest.id,
            appContext = appContext,
            onnx = OnnxApiImpl(ortEnv, gate, epProvider = { onnxExecutionProvider }),
            hxs = HxsApiImpl(hxs, installed.manifest.id, gate),
            network = NetworkApiImpl(gate),
        )
        val materialised = loader.materialise(installed)
        val instance = PluginInstance(
            installed = installed,
            plugin = materialised.plugin,
            pluginContext = pluginCtx,
            gate = gate,
            classLoader = materialised.classLoader,
            loader = loader,
        )
        instance.load()
        instance.start()
        instances[pluginId] = instance
        promoteActive(pluginId)
        return instance
    }

    @Synchronized
    fun switchTo(pluginId: String): PluginInstance {
        val current = _activePlugin.value
        if (current != null && current != pluginId) {
            instances[current]?.pause()
        }
        return open(pluginId)
    }

    @Synchronized
    fun close(pluginId: String) {
        val instance = instances.remove(pluginId) ?: return
        instance.unload()
        _openPlugins.update { list -> list.filterNot { it == pluginId } }
        if (_activePlugin.value == pluginId) {
            _activePlugin.value = _openPlugins.value.lastOrNull()
        }
    }

    @Synchronized
    fun closeAll() {
        instances.values.forEach { it.unload() }
        instances.clear()
        _openPlugins.value = emptyList()
        _activePlugin.value = null
    }

    @Synchronized
    fun instance(pluginId: String): PluginInstance? = instances[pluginId]

    private fun promoteActive(pluginId: String) {
        _openPlugins.update { list ->
            if (pluginId in list) list else list + pluginId
        }
        _activePlugin.value = pluginId
    }
}
