package com.dark.plugins.db

import android.content.Context
import android.util.Log
import com.dark.plugins.worker.LoadedPlugin
import com.dark.plugins.worker.instantiatePlugin
import com.dark.plugins.worker.loadPluginZipFromPath
import dalvik.system.InMemoryDexClassLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

object PluginManager {

    var installedPlugins = MutableStateFlow<List<PluginModel>>(emptyList())
    var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun init() {

    }

    fun registerPluginFromAsset(vararg name: String, context: Context) {
        scope.launch {
            installedPlugins.value = name.map {
                getPluginInfo(
                    loadPluginZipFromAssets(context, it).absolutePath,
                    context
                )
            }
        }
    }

    fun registerPlugin(vararg path: String, context: Context) {
        scope.launch {
            installedPlugins.value = path.map { getPluginInfo(it, context) }
        }
    }

    fun getPluginInfo(zipPath: String, context: Context): PluginModel {
        val loadedPlugin = loadPluginFromFile(File(zipPath), context)
        return if (loadedPlugin.api == null) {
            PluginModel(null, null, null)
        } else {
            PluginModel(
                loadedPlugin.api.getPluginInfo().name, loadedPlugin.api, loadedPlugin.manifest
            )
        }
    }

    private fun loadPluginZipFromAssets(ctx: Context, assetName: String): File {
        val pluginFile = File(ctx.cacheDir, assetName)

        // Copy asset to cacheDir
        ctx.assets.open(assetName).use { input ->
            pluginFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        // Assuming LoadedZip takes a File as a constructor argument
        return pluginFile
    }

    private fun loadPluginFromFile(path: File, ctx: Context): LoadedPlugin {
        return try {
            val (manifest, dexBuf) = loadPluginZipFromPath(path)
            val classLoader = InMemoryDexClassLoader(dexBuf, ctx.classLoader)
            val (pluginInstance, block) = instantiatePlugin(classLoader, manifest.mainClass, ctx)
            LoadedPlugin(manifest, pluginInstance, block, null)
        } catch (t: Throwable) {
            Log.e("PluginManager", "Failed to load plugin", t)
            LoadedPlugin(null, null, null, t)
        }
    }

}