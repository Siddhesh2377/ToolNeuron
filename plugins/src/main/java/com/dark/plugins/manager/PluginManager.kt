package com.dark.plugins.manager

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.dark.plugins.db.LocalPluginDBManager
import com.dark.plugins.db.PluginLocalDBDao
import com.dark.plugins.model.LoadedPlugin
import com.dark.plugins.model.PluginLocalDB
import com.dark.plugins.worker.instantiatePlugin
import com.dark.plugins.worker.loadPluginZipFromPath
import dalvik.system.InMemoryDexClassLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

object PluginManager {

    private const val TAG = "PluginManager"

    // A standard Scope For Plugins to Execute
    private val supervisorJob = SupervisorJob()
    val pluginScope: CoroutineScope = CoroutineScope(Dispatchers.IO + supervisorJob)

    // Installed plugins (from DB)
    private val _installedPlugins = MutableStateFlow<List<PluginLocalDB>>(emptyList())
    val installedPlugins: StateFlow<List<PluginLocalDB>> = _installedPlugins.asStateFlow()

    // Running plugins
    private val _runningPlugins = MutableStateFlow<List<LoadedPlugin>>(emptyList())
    val runningPlugins: StateFlow<List<LoadedPlugin>> = _runningPlugins.asStateFlow()

    // Mapping Plugin ViewModels
    private val pluginViewModelStores = mutableMapOf<String, ViewModelStore>()

    // Current Plugin in use
    private val _currentPlugin = MutableStateFlow<LoadedPlugin?>(null)
    val currentPlugin: StateFlow<LoadedPlugin?> = _currentPlugin.asStateFlow()

    // Backing DAO (init via init(context))
    private val daoRef = AtomicReference<PluginLocalDBDao?>()

    fun init(context: Context) {
        Log.d(TAG, "PluginManager.init() called")

        val firstInit = daoRef.get() == null
        if (firstInit) {
            Log.d(TAG, "DAO is not initialized — proceeding with setup")
            val db = LocalPluginDBManager.getInstance(context.applicationContext)
            daoRef.set(db.getPluginLocalDBDao())
            Log.d(TAG, "DAO initialized: ${daoRef.get()}")
        } else {
            Log.d(TAG, "DAO already initialized — continuing with plugin sync and seeding")
        }

        // Always start collection if not already collecting
        if (firstInit) {
            pluginScope.launch {
                Log.d(TAG, "Starting collection of plugins from DB...")
                try {
                    daoRef.get()?.getAll()?.collect { rows ->
                        Log.d(TAG, "DB emitted ${rows.size} plugin(s)")
                        _installedPlugins.value = rows
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed collecting plugins from DB", t)
                }
            }
        }

        registerPluginFromAssets(context, arrayOf("ai-chat-plugin.zip"))
    }


    /** Install from APK assets (multiple). */
    fun registerPluginFromAssets(context: Context, names: Array<String>) {
        pluginScope.launch {
            names.forEach { assetName ->
                try {
                    val file = loadPluginZipFromAssets(context, assetName)
                    installPlugin(file, context)
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to register asset plugin: $assetName", t)
                }
            }
        }
    }

    /** Install from arbitrary filesystem paths. */
    fun registerPlugin(vararg path: String, context: Context) {
        pluginScope.launch {
            path.forEach { p ->
                try {
                    installPlugin(file = File(p), context = context)
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to register plugin from path: $p", t)
                }
            }
        }
    }

    /**
     * Load + run a plugin by DB/display name. Returns the LoadedPlugin (even if run fails),
     * and updates running/current state if start succeeded.
     */
    fun runPlugin(ctx: Context, name: String, data: Any): LoadedPlugin {
        val path = getPlugin(name)
        Log.d("PluginManager", "Running plugin: $path")
        if (path.isEmpty()) {
            Log.w(TAG, "Plugin not found: $name")
            return LoadedPlugin(null, null, null, null, IllegalArgumentException("Not installed"))
        }

        val loadedPlugin = loadPluginFromFile(File(path), ctx)
        val api = loadedPlugin.api

        if (api == null) {
            Log.e(TAG, "No API found for plugin: $name", loadedPlugin.throwable)
            return loadedPlugin
        }

        val pluginName = runCatching { api.getPluginInfo().name }.getOrElse { name }

        // Launch the plugin job and keep a reference on LoadedPlugin
        val job = pluginScope.launch {
            try {
                api.onCreate(data)
                // IMPORTANT: Do NOT call onDestroy() immediately — let stop/cancel trigger it.
                // onDestroy will be invoked in stopPlugin() or if the job is cancelled with a finally.
            } catch (e: Exception) {
                Log.e(TAG, "Plugin execution failed for $pluginName", e)
            } finally {
                try {
                    api.onDestroy()
                } catch (e: Exception) {
                    Log.e(TAG, "onDestroy failed for $pluginName", e)
                }
            }
        }

        val running = loadedPlugin.copy(job = job)
        _runningPlugins.value =
            _runningPlugins.value.filterNot { it.api?.getPluginInfo()?.name == pluginName } + running

        _currentPlugin.value = running
        return running
    }

    fun stopPlugin(pluginName: String) {
        Log.d(TAG, "Stopping plugin: $pluginName")

        val currentList = _runningPlugins.value.toMutableList()
        val idx = currentList.indexOfFirst { it.api?.getPluginInfo()?.name == pluginName }
        if (idx == -1) {
            Log.w(TAG, "Plugin $pluginName not found in loaded plugins.")
            return
        }

        val plugin = currentList.removeAt(idx)
        // Cancel its job -> triggers onDestroy in runPlugin's finally
        plugin.job?.cancel()

        _runningPlugins.value = currentList
        if (_currentPlugin.value?.api?.getPluginInfo()?.name == pluginName) {
            _currentPlugin.value = currentList.firstOrNull()
        }

        pluginViewModelStores.remove(pluginName)?.clear()
        Log.d(TAG, "Plugin $pluginName successfully stopped.")
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
        _runningPlugins.value = emptyList()
        _currentPlugin.value = null
        clearPluginViewModels()
    }

    fun setCurrentPluginByName(pluginName: String) {
        _currentPlugin.value = _runningPlugins.value.find {
            it.api?.getPluginInfo()?.name == pluginName
        }
    }

    /** Returns absolute plugin path for a given display name. Empty string if not found. */
    fun getPlugin(name: String): String {
        return installedPlugins.value.find { it.pluginName == name }?.pluginPath.orEmpty()
    }

    // --- Internals ---

    private suspend fun installPlugin(file: File, context: Context) = withContext(Dispatchers.IO) {
        if (!file.exists()) throw IOException("Plugin file not found: ${file.absolutePath}")

        val loaded = loadPluginFromFile(file, context)
        val manifest = loaded.manifest ?: run {
            Log.w(TAG, "No manifest in plugin: ${file.name}")
            return@withContext
        }

        // Directory for this plugin (folder named after plugin *id*, not the zip filename)
        // If you prefer by zip name, keep file.nameWithoutExtension; using mainClass ensures uniqueness.
        val pluginDir = File(context.filesDir, "plugins/${file.nameWithoutExtension}")
        if (pluginDir.exists()) {
            return@withContext
        }

        if (!pluginDir.mkdirs()) {
            Log.w(TAG, "Failed to create plugin dir: ${pluginDir.absolutePath}")
        }

        Log.d("PluginLoader", "Plugin dir created: ${file.name}")

        // Copy the zip/payload inside the dir
        val destFile = File(pluginDir, file.name)
        file.copyTo(destFile, overwrite = true)

        Log.d("PluginLoader", "Plugin copied: ${destFile.absolutePath}")

        upsertDb(
            pluginName = manifest.name,           // consider using human-friendly name if available
            manifestCode = manifest.rawCode,
            pluginPath = destFile.absolutePath,
            mainClass = manifest.mainClass,
            pluginVersion = manifest.version
        )
    }

    fun uninstallPlugin(pluginName: String): Boolean {
        // stop if running
        stopPlugin(pluginName)

        val row = installedPlugins.value.find { it.pluginName == pluginName } ?: run {
            Log.w(TAG, "uninstallPlugin: not found -> $pluginName")
            return false
        }

        // delete files (we stored absolute path to the artifact file)
        val artifact = File(row.pluginPath)
        val dir = artifact.parentFile
        val ok = try {
            if (dir?.exists() == true) dir.deleteRecursively() else artifact.delete()
        } catch (t: Throwable) {
            Log.e(TAG, "uninstallPlugin: file delete failed for $pluginName", t)
            false
        }

        pluginScope.launch {
            try {
                daoRef.get()?.deleteByName(pluginName)
            } catch (t: Throwable) {
                Log.e(TAG, "uninstallPlugin: DB delete failed for $pluginName", t)
            }
        }

        // clear any ViewModelStore leftover
        pluginViewModelStores.remove(pluginName)?.clear()
        return ok
    }


    private fun upsertDb(
        pluginName: String,
        manifestCode: String,
        pluginPath: String,
        mainClass: String,
        pluginVersion: String
    ) {
        val dao = daoRef.get()
        if (dao == null) {
            Log.e(TAG, "DAO not initialized; cannot upsert plugin $pluginName")
            return
        }
        pluginScope.launch {
            try {
                dao.upsertPlugin(
                    PluginLocalDB(
                        pluginName = pluginName,
                        manifestCode = manifestCode,
                        pluginPath = pluginPath,
                        mainClass = mainClass,
                        pluginVersion = pluginVersion
                    )
                )
            } catch (t: Throwable) {
                Log.e(TAG, "DB upsert failed for $pluginName", t)
            }
        }
    }

    private fun loadPluginZipFromAssets(ctx: Context, assetName: String): File {
        val out = File(ctx.cacheDir, assetName)
        return try {
            ctx.assets.open(assetName).use { input ->
                out.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            out
        } catch (t: Throwable) {
            // Clean partial files
            runCatching { out.delete() }
            throw IOException("Failed to copy asset $assetName", t)
        }
    }

    private fun loadPluginFromFile(path: File, ctx: Context): LoadedPlugin {
        return try {
            val (manifest, dexBuf) = loadPluginZipFromPath(path)
            val classLoader = InMemoryDexClassLoader(dexBuf, ctx.classLoader)
            val (pluginInstance, content) = instantiatePlugin(classLoader, manifest.mainClass, ctx)
            LoadedPlugin(
                job = null,
                manifest = manifest,
                api = pluginInstance,
                content = content,
                throwable = null
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to load plugin from ${path.absolutePath}", t)
            LoadedPlugin(job = null, manifest = null, api = null, content = null, throwable = t)
        }
    }
}