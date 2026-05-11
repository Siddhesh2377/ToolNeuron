package com.dark.plugin_exc

import android.content.Context
import com.dark.plugin_api.Plugin
import dalvik.system.DexClassLoader
import java.io.File
import java.io.InputStream

class PluginLoader(private val appContext: Context) {

    private val pluginsRoot: File by lazy {
        File(appContext.filesDir, "plugins").apply { mkdirs() }
    }

    fun rootDirFor(pluginId: String): File = File(pluginsRoot, pluginId)

    fun installed(): List<InstalledPlugin> {
        val children = pluginsRoot.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }
            ?: return emptyList()
        return children.mapNotNull { dir ->
            runCatching { loadInstalled(dir) }.getOrNull()
        }
    }

    fun installFromStream(bundle: InputStream): InstalledPlugin {
        val staging = File(pluginsRoot, ".staging-${System.nanoTime()}")
        try {
            PluginBundle.extract(bundle, staging)
            val manifestFile = File(staging, "manifest.json")
            if (!manifestFile.exists()) {
                throw IllegalStateException("manifest.json missing in plugin bundle")
            }
            val parsed = PluginManifestParser.parse(manifestFile.readText())
            val dest = rootDirFor(parsed.id)
            if (dest.exists()) dest.deleteRecursively()
            if (!staging.renameTo(dest)) {
                staging.copyRecursively(dest, overwrite = true)
                staging.deleteRecursively()
            }
            return loadInstalled(dest)
        } catch (t: Throwable) {
            staging.deleteRecursively()
            throw t
        }
    }

    fun uninstall(pluginId: String) {
        val dir = rootDirFor(pluginId)
        PluginBundle.unlockForDelete(dir)
        dir.deleteRecursively()
    }

    data class Materialised(val plugin: Plugin, val classLoader: ClassLoader)

    fun materialise(installed: InstalledPlugin): Materialised {
        installed.nativeLibDir?.let { dir ->
            dir.listFiles { f -> f.extension == "so" }?.sortedBy { it.name }?.forEach { so ->
                System.load(so.absolutePath)
            }
        }
        val dexFiles = installed.rootDir
            .listFiles { f -> f.isFile && f.name.matches(Regex("^classes\\d*\\.dex$")) }
            ?.sortedBy { f -> dexOrdinal(f.name) }
            ?: emptyList()
        if (dexFiles.isEmpty()) {
            throw IllegalStateException("no classes*.dex files in ${installed.rootDir.name}")
        }
        val dexPath = dexFiles.joinToString(File.pathSeparator) { it.absolutePath }
        val parent = Plugin::class.java.classLoader
        val classLoader = DexClassLoader(
            dexPath,
            null,
            installed.nativeLibDir?.absolutePath,
            parent,
        )
        val cls = classLoader.loadClass(installed.manifest.entryClass)
        val ctor = cls.getDeclaredConstructor()
        ctor.isAccessible = true
        val instance = ctor.newInstance()
        if (instance !is Plugin) {
            throw IllegalStateException(
                "entryClass ${installed.manifest.entryClass} does not implement com.dark.plugin_api.Plugin"
            )
        }
        return Materialised(plugin = instance, classLoader = classLoader)
    }

    fun closeClassLoader(loader: ClassLoader?) {
        if (loader == null) return
        runCatching {
            val pathListField = dalvik.system.BaseDexClassLoader::class.java
                .getDeclaredField("pathList")
            pathListField.isAccessible = true
            val pathList = pathListField.get(loader) ?: return@runCatching
            val dexElementsField = pathList.javaClass.getDeclaredField("dexElements")
            dexElementsField.isAccessible = true
            val elements = dexElementsField.get(pathList) as? Array<*> ?: return@runCatching
            for (element in elements) {
                element ?: continue
                val dexFileField = runCatching {
                    element.javaClass.getDeclaredField("dexFile")
                }.getOrNull() ?: continue
                dexFileField.isAccessible = true
                val dexFile = dexFileField.get(element) as? dalvik.system.DexFile ?: continue
                runCatching { dexFile.close() }
            }
        }
    }

    private fun dexOrdinal(name: String): Int {
        val n = name.removePrefix("classes").removeSuffix(".dex")
        return if (n.isEmpty()) 1 else n.toIntOrNull() ?: Int.MAX_VALUE
    }

    private fun loadInstalled(rootDir: File): InstalledPlugin {
        val manifestFile = File(rootDir, "manifest.json")
        if (!manifestFile.exists()) {
            throw IllegalStateException("manifest.json missing in ${rootDir.name}")
        }
        val parsed = PluginManifestParser.parse(manifestFile.readText())
        val dexFile = File(rootDir, "classes.dex")
        if (!dexFile.exists()) {
            throw IllegalStateException("classes.dex missing in ${rootDir.name}")
        }
        val nativeDir = PluginBundle.detectNativeLibDir(rootDir)
        val resolved = parsed.copy(hasNativeCode = nativeDir != null)
        if (resolved != parsed) {
            manifestFile.writeText(PluginManifestParser.serialize(resolved))
        }
        return InstalledPlugin(
            manifest = resolved,
            rootDir = rootDir,
            dexFile = dexFile,
            nativeLibDir = nativeDir,
            installedAt = manifestFile.lastModified(),
        )
    }
}
