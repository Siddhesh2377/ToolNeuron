package com.dark.plugins.worker

import android.content.Context
import android.util.Log
import com.dark.plugins.db.PluginLocalDBDao
import com.dark.plugins.model.InstalledPlugin
import com.dark.plugins.model.LoadedPlugin
import com.dark.plugins.model.PluginManifest
import com.dark.plugins.worker.PluginInstanceBuilder.instantiatePlugin
import dalvik.system.InMemoryDexClassLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.zip.ZipInputStream

internal object PluginOps {

    private const val TAG = "PluginOps"
    private const val PLUGINS_DIR = "plugins"
    private const val MANIFEST_FILE = "manifest.json"
    private const val CLASSES_DEX = "classes.dex"
    private const val PLUGIN_DEX_JAR = "plugin.dex.jar"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val installMutex = Mutex()

    @Volatile
    private var initialized = false
    private lateinit var dao: PluginLocalDBDao

    private val _installedPlugins = MutableStateFlow<List<InstalledPlugin>>(emptyList())
    val installedPlugins: StateFlow<List<InstalledPlugin>> = _installedPlugins.asStateFlow()

    fun init(dbProvider: PluginLocalDBDao) {
        if (initialized) return

        synchronized(this) {
            if (initialized) return

            dao = dbProvider
            scope.launch {
                dao.getInstalledPlugins().collect { plugins ->
                    _installedPlugins.value = plugins
                    Log.d(TAG, "Plugins updated: ${plugins.size} installed")
                }
            }
            initialized = true
            Log.d(TAG, "PluginOps initialized")
        }
    }

    // Installation Methods

    suspend fun installFromAssets(context: Context, assetNames: Array<String>) {
        assetNames.forEach { asset ->
            installMutex.withLock {
                runCatching {
                    val cachedFile = copyAssetToCache(context, asset)
                    try {
                        installPlugin(cachedFile, context)
                        Log.i(TAG, "Successfully installed asset: $asset")
                    } finally {
                        cachedFile.delete()
                    }
                }.onFailure {
                    Log.e(TAG, "Failed to install asset: $asset", it)
                }
            }
        }
    }

    suspend fun installFromPath(context: Context, vararg paths: String) {
        paths.forEach { path ->
            installMutex.withLock {
                runCatching {
                    installPlugin(File(path), context)
                    Log.i(TAG, "Successfully installed from path: $path")
                }.onFailure {
                    Log.e(TAG, "Failed to install from path: $path", it)
                }
            }
        }
    }

    private suspend fun installPlugin(file: File, context: Context) = withContext(Dispatchers.IO) {
        require(file.exists()) { "Plugin file not found: ${file.absolutePath}" }

        // Check if already installed
        val manifest = extractManifestOnly(file)
        val existing = dao.getByName(manifest.name)

        if (existing != null && existing.pluginVersion >= manifest.version) {
            Log.d(TAG, "Plugin ${manifest.name} v${manifest.version} already installed (current: v${existing.pluginVersion})")
            return@withContext
        }

        val pluginDir = getPluginDirectory(context, manifest.name)
        val destFile = File(pluginDir, "${manifest.name}.zip")

        // Copy to persistent storage
        file.copyTo(destFile, overwrite = true)

        // Upsert to database
        val plugin = InstalledPlugin(
            pluginName = manifest.name,
            manifestCode = manifest.rawCode,
            pluginPath = destFile.absolutePath,
            mainClass = manifest.mainClass,
            pluginVersion = manifest.version,
            tools = manifest.tools,
            shaCode = getFileSha256(destFile)
        )

        dao.insertPlugin(plugin)
        Log.i(TAG, "Plugin installed: ${manifest.name} v${manifest.version}, ${manifest.tools.size} tool(s)")
    }

    fun getFileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun loadPluginFromFile(file: File, context: Context): LoadedPlugin {
        return try {
            val (manifest, dexBuffer) = extractPluginData(file)
            val classLoader = InMemoryDexClassLoader(dexBuffer, context.classLoader)
            val (instance, _) = instantiatePlugin(classLoader, manifest.mainClass)

            Log.d(TAG, "Plugin loaded: ${manifest.name}")
            LoadedPlugin(null, manifest, instance, null, null)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load plugin: ${file.name}", e)
            LoadedPlugin(null, null, null, null, e)
        }
    }

    // Uninstall

    suspend fun uninstallPlugin(pluginName: String) = withContext(Dispatchers.IO) {
        installMutex.withLock {
            runCatching {
                val plugin = dao.getByName(pluginName) ?: run {
                    Log.w(TAG, "Plugin not found: $pluginName")
                    return@withContext
                }

                val file = File(plugin.pluginPath)
                val deleted = file.parentFile?.deleteRecursively() ?: file.delete()

                if (deleted) {
                    dao.deleteByName(pluginName)
                    Log.i(TAG, "Plugin uninstalled: $pluginName")
                } else {
                    Log.w(TAG, "Failed to delete files for: $pluginName")
                }
            }.onFailure {
                Log.e(TAG, "Uninstall failed: $pluginName", it)
            }
        }
    }

    // Helper Methods

    private fun copyAssetToCache(context: Context, assetName: String): File {
        val cacheFile = File(context.cacheDir, "plugin_${System.currentTimeMillis()}_$assetName")
        return try {
            context.assets.open(assetName).use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            cacheFile
        } catch (e: Throwable) {
            cacheFile.delete()
            throw IOException("Failed to copy asset: $assetName", e)
        }
    }

    private fun getPluginDirectory(context: Context, pluginName: String): File {
        return File(context.filesDir, "$PLUGINS_DIR/$pluginName").apply {
            if (!exists()) mkdirs()
        }
    }

    suspend fun getPluginByName(name: String): InstalledPlugin? =
        withContext(Dispatchers.IO) { dao.getByName(name) }

    private fun extractManifestOnly(file: File): PluginManifest {
        file.inputStream().use { fileInput ->
            ZipInputStream(fileInput).use { zip ->
                for (entry in generateSequence { zip.nextEntry }) {
                    if (entry.isDirectory) continue
                    if (entry.name.equals(MANIFEST_FILE, ignoreCase = true)) {
                        return PluginManifestWorker(
                            zip.readBytes().decodeToString()
                        ).getPluginManifest()
                    }
                }
            }
        }
        error("manifest.json not found in ${file.name}")
    }

    fun extractPluginData(file: File): Pair<PluginManifest, ByteBuffer> {
        var manifest: PluginManifest? = null
        var dexBuffer: ByteBuffer? = null

        file.inputStream().use { fileInput ->
            ZipInputStream(fileInput).use { zip ->
                for (entry in generateSequence { zip.nextEntry }) {
                    if (entry.isDirectory) continue

                    when (entry.name.lowercase()) {
                        MANIFEST_FILE -> {
                            if (manifest == null) {
                                manifest = PluginManifestWorker(
                                    zip.readBytes().decodeToString()
                                ).getPluginManifest()
                            }
                        }
                        CLASSES_DEX -> {
                            if (dexBuffer == null) {
                                dexBuffer = ByteBuffer.wrap(zip.readBytes())
                            }
                        }
                        PLUGIN_DEX_JAR -> {
                            if (dexBuffer == null) {
                                dexBuffer = extractDexFromJar(zip.readBytes())
                            }
                        }
                    }

                    // Early exit if both found
                    if (manifest != null && dexBuffer != null) break
                }
            }
        }

        return requireNotNull(manifest) { "manifest.json not found in ${file.name}" } to
                requireNotNull(dexBuffer) { "classes.dex not found in ${file.name}" }
    }

    private fun extractDexFromJar(jarBytes: ByteArray): ByteBuffer {
        ZipInputStream(ByteArrayInputStream(jarBytes)).use { jar ->
            for (entry in generateSequence { jar.nextEntry }) {
                if (!entry.isDirectory && entry.name == CLASSES_DEX) {
                    return ByteBuffer.wrap(jar.readBytes())
                }
            }
        }
        error("classes.dex not found in plugin.dex.jar")
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        installMutex.withLock {
            dao.deleteInstalledPlugins()
            Log.i(TAG, "All plugins cleared")
        }
    }
}