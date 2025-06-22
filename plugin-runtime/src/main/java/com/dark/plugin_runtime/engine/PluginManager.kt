package com.dark.plugin_runtime.engine

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import com.dark.plugin_api.info.plugin.Plugin
import com.dark.plugin_api.info.services.types.ScreenReading
import com.dark.plugin_api.info.services.types.ServiceType
import com.dark.plugin_runtime.database.installed_plugin_db.PluginInstalledDatabase
import com.dark.plugin_runtime.model.PluginModel
import com.dark.plugin_runtime.model.ScreenReadingServicePlugins
import com.dark.plugin_runtime.utils.queryFileName
import dalvik.system.DexClassLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Singleton object responsible for managing plugins:
 * - Installation
 * - Uninstallation
 * - Service discovery
 * - Dynamic loading & execution
 */
@SuppressLint("StaticFieldLeak")
object PluginManager {

    private val pluginScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _getInstalledPlugins = MutableStateFlow<List<PluginModel>>(emptyList())

    /**
     * Public accessor for service-based plugins as StateFlow.
     */
    val InstalledPlugins: StateFlow<List<PluginModel>> = _getInstalledPlugins.asStateFlow()

    private val _serviceBasedPluginsScreenReading = MutableStateFlow<List<ScreenReadingServicePlugins>>(emptyList())

    /**
     * Public accessor for service-based plugins as StateFlow.
     */
    val serviceBasedPluginsScreenReading: StateFlow<List<ScreenReadingServicePlugins>> = _serviceBasedPluginsScreenReading.asStateFlow()

    private lateinit var context: Context

    private lateinit var db: PluginInstalledDatabase

    /**
     * Initialize PluginManager with application context.
     */
    fun init(context: Context) {
        this.context = context.applicationContext
        db = PluginInstalledDatabase.getInstance(context)
    }

    private const val TAG = "PluginManager"

    /**
     * Returns the folder where a given plugin is stored.
     */
    private fun getPluginFolder(pluginName: String): File =
        File(context.getDir("plugins", Context.MODE_PRIVATE), pluginName)

    /**
     * Installs a plugin from the provided Uri.
     */
    fun installPlugin(
        uri: Uri,
        onInstallationStarted: () -> Unit = {},
        onError: (Exception) -> Unit = {},
        onInstallationComplete: (pluginInfo: PluginModel) -> Unit
    ) {
        pluginScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val rawFileName = queryFileName(uri, context)
                        ?: "plugin_${System.currentTimeMillis()}.zip"
                    val pluginName = rawFileName.substringBeforeLast('.')
                    val pluginFolder = getPluginFolder(pluginName).apply { mkdirs() }

                    val inputStream = context.contentResolver.openInputStream(uri)
                    ZipInputStream(inputStream).use { zip ->
                        var entry: ZipEntry?
                        var extractedAny = false

                        withContext(Dispatchers.Main) { onInstallationStarted() }

                        while (zip.nextEntry.also { entry = it } != null) {
                            val outFile = File(pluginFolder, entry!!.name)
                            if (entry!!.isDirectory) {
                                outFile.mkdirs()
                            } else {
                                outFile.parentFile?.mkdirs()
                                zip.copyTo(outFile.outputStream())
                                zip.closeEntry()
                                extractedAny = true
                            }
                        }

                        if (!extractedAny) {
                            Log.d(TAG, "❌ ZIP archive is empty.")
                        } else {
                            Log.d(TAG, "📦 Successfully extracted plugin")

                            val pluginInfo = readPluginInfo(pluginName)
                            db.pluginDao().insertPlugin(pluginInfo)

                            withContext(Dispatchers.Main) {
                                onInstallationComplete(pluginInfo)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to install plugin", e)
                withContext(Dispatchers.Main) {
                    onError(e)
                }
            }
        }
    }

    fun updateInstalledPlugins(){
        pluginScope.launch {
            withContext(Dispatchers.IO) {
                val sPlugins = db.pluginDao().getAllPlugins()
                val newScreenReadingServicePlugins = mutableListOf<PluginModel>()

                for (plugin in sPlugins) {
                    newScreenReadingServicePlugins.add(plugin)
                }
                _getInstalledPlugins.value = newScreenReadingServicePlugins
            }
        }
    }

    /**
     * Reads manifest.json and constructs PluginModel.
     */
    private fun readPluginInfo(pluginName: String): PluginModel {
        val pluginFolder = getPluginFolder(pluginName)
        val manifestFilePath = File(pluginFolder, "manifest.json")

        if (!manifestFilePath.exists())
            throw FileNotFoundException("❌ manifest.json not found at $manifestFilePath")

        Log.d(TAG, "✅ Found manifest.json at: ${manifestFilePath.absolutePath}")

        val manifest = JSONObject(manifestFilePath.readText())

        val permissions = (0 until manifest.getJSONArray("permissions").length())
            .map { manifest.getJSONArray("permissions").getString(it) }

        return PluginModel(
            pluginName = manifest.getString("name"),
            pluginDescription = manifest.getString("description"),
            pluginPermissions = permissions,
            autoStart = manifest.has("services"),
            mainClass = manifest.getString("main"),
            manifestFile = manifestFilePath.path,
            pluginPath = pluginFolder.path,
            pluginApi = manifest.getString("plugin-api-version")
        )
    }

    /**
     * Uninstalls the given plugin.
     */
    fun unInstallPlugin(path: String, onResult: (Boolean) -> Unit) {
        pluginScope.launch {
            withContext(Dispatchers.IO) {
                Log.d(TAG, "📦 Uninstalling plugin $path at: $path")

                val file = File(path)

                Log.d(TAG, "❌ Deleting plugin at: $path")

                val result = if (file.exists()) {
                    val deleted = file.deleteRecursively()
                    if (deleted) {
                        Log.d(TAG, "✅ Plugin deleted: $path")
                        db.pluginDao().deletePlugin(path)
                        true
                    } else {
                        Log.e(TAG, "❌ Failed to delete plugin at: $path")
                        false
                    }
                } else {
                    Log.w(TAG, "⚠️ Plugin path does not exist: $path")
                    false
                }

                withContext(Dispatchers.Main) {
                    onResult(result)
                }
            }
        }
    }

    fun loadPluginScreenReadingServices() {
        pluginScope.launch {
            withContext(Dispatchers.IO) {
                val sPlugins = db.pluginDao().getAutoRunningPlugins()
                val newScreenReadingServicePlugins = mutableListOf<ScreenReadingServicePlugins>()

                for (plugin in sPlugins) {
                    val manifest = File(plugin.manifestFile)
                    val manifestJson = JSONObject(manifest.readText())
                    val services = manifestJson.getJSONObject("services")

                    services.keys().forEach { serviceKey ->
                        val serviceObj = services.getJSONObject(serviceKey)
                        val serviceClassPath = serviceObj.getString("serviceClass")

                        val pluginFolder = db.pluginDao().getPluginFolderByName(plugin.pluginName)
                        val pluginJar = File(pluginFolder, "plugin.jar")

                        if (!pluginJar.exists())
                            throw FileNotFoundException("❌ plugin.jar not found at $pluginJar")

                        val safeJar = File(
                            context.noBackupFilesDir,
                            "${plugin.pluginName.trim()}-readonly.jar"
                        )
                        pluginJar.copyTo(safeJar, overwrite = true)
                        safeJar.setReadOnly()

                        val classLoader = DexClassLoader(
                            safeJar.absolutePath,
                            null,
                            null,
                            context.classLoader
                        )

                        val clazz = classLoader.loadClass(serviceClassPath)
                        val constructor = clazz.getDeclaredConstructor(Context::class.java)
                        val instance = constructor.newInstance(context)

                        if (instance !is ScreenReading) throw IllegalStateException("❌ $serviceClassPath does not implement Plugin interface")

                        newScreenReadingServicePlugins.add(
                            ScreenReadingServicePlugins(
                                plugin.pluginName,
                                ServiceType.SCREEN_READING,
                                serviceClassPath,
                                instance
                            )
                        )
                    }
                }

                _serviceBasedPluginsScreenReading.value = newScreenReadingServicePlugins
            }
        }
    }

    /**
     * Dynamically loads and runs a plugin.
     */
    fun runPlugin(pluginName: String, onResult: (Plugin) -> Unit) {
        val db = PluginInstalledDatabase.getInstance(context)
        val scope = CoroutineScope(Dispatchers.IO)

        scope.launch {
            val pluginFolder = db.pluginDao().getPluginFolderByName(pluginName)
            val pluginJar = File(pluginFolder, "plugin.jar")
            val mainClass = db.pluginDao().getMainClassByName(pluginName)

            if (!pluginJar.exists())
                throw FileNotFoundException("❌ plugin.jar not found at $pluginJar")

            val safeJar = File(context.noBackupFilesDir, "$pluginName-readonly.jar")
            pluginJar.copyTo(safeJar, overwrite = true)
            safeJar.setReadOnly()

            val classLoader = DexClassLoader(
                safeJar.absolutePath,
                null,
                null,
                context.classLoader
            )

            val clazz = classLoader.loadClass(mainClass)
            val constructor = clazz.getDeclaredConstructor(Context::class.java)
            val instance = constructor.newInstance(context)

            if (instance !is Plugin)
                throw IllegalStateException("❌ $mainClass does not implement Plugin interface")

            Log.i(TAG, "✅ Loaded plugin class: ${instance.getName()}")
            onResult(instance)

            PluginExecutionManager.launchPlugin(instance)
        }
    }

    /**
     * Stops the given plugin.
     */
    fun stopPlugin(name: String, onResult: (Boolean) -> Unit) {
        pluginScope.launch {
            withContext(Dispatchers.IO) {
                PluginExecutionManager.stopPlugin(name)
                onResult(true)
            }
        }
    }
}