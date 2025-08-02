package com.dark.plugins.worker

import android.content.Context
import androidx.compose.runtime.Composable
import com.dark.plugins.engine.PluginApi
import com.dark.plugins.engine.PluginManifest
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.zip.ZipInputStream

data class LoadedZip(
    val manifest: PluginManifest, val dexBuffer: ByteBuffer
)

data class LoadedPlugin(
    val manifest: PluginManifest? = null, val api: PluginApi? = null, val content: (@Composable () -> Unit)? = null, val throwable: Throwable? = null
)

/** Reads plugin zip from assets, parses Manifest.json and extracts classes.dex. */
fun loadPluginZipFromAssets(ctx: Context, assetName: String): LoadedZip {
    val pluginFile = File(ctx.cacheDir, assetName)

    // Copy asset to cacheDir
    ctx.assets.open(assetName).use { input ->
        pluginFile.outputStream().use { output ->
            input.copyTo(output)
        }
    }

    // Assuming LoadedZip takes a File as a constructor argument
    return loadPluginZipFromPath(pluginFile)
}


fun loadPluginZipFromPath(path: File): LoadedZip {
    var manifest: PluginManifest? = null
    var dexBuf: ByteBuffer? = null

    path.inputStream().use { input ->
        ZipInputStream(input).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                if (!e.isDirectory) {
                    when (e.name) {
                        "Manifest.json", "manifest.json" -> {
                            val text = zis.readBytes().decodeToString()
                            val j = JSONObject(text)
                            manifest = PluginManifest(
                                name = j.getString("name"), mainClass = j.getString("mainClass")
                            )
                        }

                        "classes.dex" -> {
                            dexBuf = ByteBuffer.wrap(zis.readBytes())
                        }

                        "plugin.dex.jar" -> {
                            // Nested jar: open and pull classes.dex from inside
                            val jarBytes = zis.readBytes()
                            dexBuf = extractClassesDexFromJar(jarBytes)
                        }
                    }
                }
                e = zis.nextEntry
            }
        }
    }

    checkNotNull(manifest) { "Manifest.json not found in ${path.name}." }
    checkNotNull(dexBuf) { "classes.dex not found in ${path.name} (directly or inside plugin.dex.jar)." }
    return LoadedZip(manifest, dexBuf)
}

fun extractClassesDexFromJar(jarBytes: ByteArray): ByteBuffer {
    ZipInputStream(ByteArrayInputStream(jarBytes)).use { jz ->
        var je = jz.nextEntry
        while (je != null) {
            if (!je.isDirectory && je.name == "classes.dex") {
                return ByteBuffer.wrap(jz.readBytes())
            }
            je = jz.nextEntry
        }
    }
    error("classes.dex not found inside plugin.dex.jar")
}


@Suppress("UNCHECKED_CAST")
fun instantiatePlugin(
    cl: ClassLoader, className: String, appCtx: Context
): Pair<PluginApi, (@Composable () -> Unit)> {
    val clazz = cl.loadClass(className)

    // Prefer cast to your shared interface for type-safety
    val composePluginIface =
        Class.forName("com.dark.plugins.engine.ComposePlugin", false, appCtx.classLoader)

    val instance = createInstanceSmart(clazz, appCtx) as PluginApi

    // Optional onCreate(data)
    runCatching {
        val m = clazz.getMethod("onCreate", Any::class.java)
        m.invoke(instance, emptyMap<String, Any>())
    }

    val contentBlock: @Composable () -> Unit = if (composePluginIface.isAssignableFrom(clazz)) {
        val typed = composePluginIface.cast(instance)
        val m = composePluginIface.getMethod("content")
        m.invoke(typed) as (@Composable () -> Unit)
    } else {
        // Fallback: reflection on method named "content"
        val m = clazz.getMethod("content")
        m.invoke(instance) as (@Composable () -> Unit)
    }

    return instance to contentBlock
}

@Suppress("UNCHECKED_CAST")
fun createInstanceSmart(clazz: Class<*>, appCtx: Context): Any {
    // 1) zero-arg
    runCatching {
        val c = clazz.getDeclaredConstructor()
        c.isAccessible = true
        return c.newInstance()
    }
    // 2) Kotlin object
    runCatching {
        val f = clazz.getField("INSTANCE")
        return f.get(null) ?: Any()
    }
    // 3) Companion.create()/getInstance()
    runCatching {
        val companion = clazz.declaredClasses.firstOrNull { it.simpleName == "Companion" }
        if (companion != null) {
            val compField = clazz.getField("Companion")
            val comp = compField.get(null)
            val m =
                companion.methods.firstOrNull { it.name == "create" || it.name == "getInstance" }
            if (comp != null && m != null) return m.invoke(comp) ?: Any()
        }
    }
    // 4) (Context) constructor
    runCatching {
        val c = clazz.getDeclaredConstructor(Context::class.java)
        c.isAccessible = true
        return c.newInstance(appCtx)
    }

    error("No suitable constructor/factory for ${clazz.name}. Provide a zero-arg or (Context) ctor.")
}