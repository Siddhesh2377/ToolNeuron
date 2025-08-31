package com.dark.plugins.manager

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.dark.plugins.db.LocalPluginDBManager
import com.dark.plugins.db.PluginLocalDBDao
import com.dark.plugins.model.LoadedPlugin
import com.dark.plugins.model.PluginLocalDB
import com.dark.plugins.model.Tools
import com.dark.plugins.worker.instantiatePlugin
import com.dark.plugins.worker.loadPluginZipFromPath
import dalvik.system.InMemoryDexClassLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException

object PluginManager {

    //Private Variables
    private const val TAG = "PluginManager"
    private val supervisorJob = SupervisorJob()
    private val _installedPlugins = MutableStateFlow<List<PluginLocalDB>>(emptyList())
    private val _runningPlugins = MutableStateFlow<List<LoadedPlugin>>(emptyList())
    private val pluginViewModelStores = mutableMapOf<String, ViewModelStore>()
    private val _currentPlugin = MutableStateFlow<LoadedPlugin?>(null)
    private val daoRef = AtomicReference<PluginLocalDBDao?>()

    //Public Variables
    val pluginScope: CoroutineScope = CoroutineScope(Dispatchers.IO + supervisorJob)
    val installedPlugins: StateFlow<List<PluginLocalDB>> = _installedPlugins.asStateFlow()
    val runningPlugins: StateFlow<List<LoadedPlugin>> = _runningPlugins.asStateFlow()
    val currentPlugin: StateFlow<LoadedPlugin?> = _currentPlugin.asStateFlow()

    val toolsList: StateFlow<List<Pair<String, List<Tools>>>> =
        _installedPlugins
            .map { rows -> rows.map { it.pluginName to it.tools } }
            .stateIn(pluginScope, SharingStarted.Eagerly, emptyList())

    //Public Fun's

    //Init Function
    fun init(context: Context) {
        Log.d(TAG, "PluginManager.init() Started")
        val pluginDatabase = daoRef.get() == null

        //If pluginDatabase is not null Then Return
        if (!pluginDatabase) {
            Log.d(TAG, "Database is already initialized — continuing with plugin sync and seeding")
            return
        }

        //If pluginDatabase is null Then Init Database
        Log.d(TAG, "Database is not initialized — proceeding with setup")
        val db = LocalPluginDBManager.getInstance(context.applicationContext)
        daoRef.set(db.getPluginLocalDBDao())
        Log.d(TAG, "Database is initialized: ${daoRef.get()}")
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

        //Register Plugin From Assets :: FOR DEBUG PURPOSE ONLY
        registerPluginFromAssets(context, arrayOf("web-searching-plugin.zip"))
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
        if (path.isEmpty()) {
            Log.w(TAG, "Plugin not found: $name")
            return LoadedPlugin(null, null, null, null, IllegalArgumentException("Not installed"))
        }

        val loadedPlugin = loadPluginFromFile(File(path), ctx)
        val api = loadedPlugin.api ?: return loadedPlugin.also {
            Log.e(TAG, "No API found for plugin: $name", loadedPlugin.throwable)
        }

        val idName =
            loadedPlugin.manifest?.name ?: api.getPluginInfo().name.takeIf { it.isNotBlank() }
            ?: name

        val job = pluginScope.launch {
            try {
                api.onCreate(data)
                // Keep this coroutine alive until stopPlugin() cancels it.
                awaitCancellation()
            } catch (ce: CancellationException) {
                // normal stop
                throw ce
            } catch (e: Exception) {
                Log.e(TAG, "Plugin execution failed for $idName", e)
            } finally {
                try {
                    api.onDestroy()
                } catch (e: Exception) {
                    Log.e(TAG, "onDestroy failed for $idName", e)
                }
            }
        }

        val running = loadedPlugin.copy(job = job)

        // Replace any existing instance with the same idName
        _runningPlugins.value =
            _runningPlugins.value.filterNot { it.displayName() == idName } + running

        _currentPlugin.value = running

        // Auto-remove from running list when the job completes
        job.invokeOnCompletion {
            val list = _runningPlugins.value.filterNot { it.displayName() == idName }
            _runningPlugins.value = list
            if (_currentPlugin.value?.displayName() == idName) {
                _currentPlugin.value = list.firstOrNull()
            }
            pluginViewModelStores.remove(idName)?.clear()
        }

        return running
    }


    fun stopPlugin(pluginName: String) {
        Log.d(TAG, "Stopping plugin: $pluginName")

        val currentList = _runningPlugins.value.toMutableList()
        val idx = currentList.indexOfFirst { it.displayName() == pluginName }
        if (idx == -1) {
            Log.w(
                TAG,
                "Plugin $pluginName not found in loaded plugins. Running=${currentList.map { it.displayName() }}"
            )
            return
        }

        val plugin = currentList.removeAt(idx)

        // Cancel the job – onDestroy() is called in runPlugin’s finally.
        plugin.job?.cancel()

        _runningPlugins.value = currentList
        if (_currentPlugin.value?.displayName() == pluginName) {
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

    private fun LoadedPlugin.displayName(): String = this.manifest?.name?.takeIf { it.isNotBlank() }
        ?: this.api?.getPluginInfo()?.name?.takeIf { it.isNotBlank() } ?: ""


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
            pluginVersion = manifest.version,
            tools = manifest.tools
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
        pluginVersion: String,
        tools: List<Tools>
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
                        pluginVersion = pluginVersion,
                        tools = tools
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


    fun addTools() {

    }
}