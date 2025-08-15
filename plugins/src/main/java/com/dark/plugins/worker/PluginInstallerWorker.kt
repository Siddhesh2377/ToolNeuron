package com.dark.plugins.worker

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import com.dark.plugins.api.PluginApi
import com.dark.plugins.model.PluginManifest
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.ZipInputStream

private const val TAG = "PluginLoader"

fun loadPluginZipFromPath(path: File): Pair<PluginManifest, ByteBuffer> {
    var manifest: PluginManifest? = null
    var dexBuf: ByteBuffer? = null

    path.inputStream().use { input ->
        ZipInputStream(input).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    when (entry.name.lowercase()) {
                        "manifest.json" -> {
                            val text = zis.readBytes().decodeToString()
                            manifest = PluginManifestWorker(text).getPluginManifest()
                            Log.d(TAG, "Manifest loaded: $manifest")
                        }
                        "classes.dex" -> {
                            dexBuf = ByteBuffer.wrap(zis.readBytes())
                            Log.d(TAG, "classes.dex loaded directly.")
                        }
                        "plugin.dex.jar" -> {
                            val jarBytes = zis.readBytes()
                            dexBuf = extractClassesDexFromJar(jarBytes)
                            Log.d(TAG, "classes.dex extracted from plugin.dex.jar.")
                        }
                    }
                }
                entry = zis.nextEntry
            }
        }
    }

    checkNotNull(manifest) { "Manifest.json not found in ${path.name}" }
    checkNotNull(dexBuf) { "classes.dex not found in ${path.name}" }
    return manifest to dexBuf
}

fun extractClassesDexFromJar(jarBytes: ByteArray): ByteBuffer {
    ZipInputStream(ByteArrayInputStream(jarBytes)).use { jar ->
        var entry = jar.nextEntry
        while (entry != null) {
            if (!entry.isDirectory && entry.name == "classes.dex") {
                return ByteBuffer.wrap(jar.readBytes())
            }
            entry = jar.nextEntry
        }
    }
    error("classes.dex not found inside plugin.dex.jar")
}

@Suppress("UNCHECKED_CAST")
fun instantiatePlugin(
    cl: ClassLoader, className: String, appCtx: Context
): Pair<PluginApi, (@Composable () -> Unit)> {
    val clazz = cl.loadClass(className)
    Log.d(TAG, "Loaded class: $className")

    val instance = createInstanceSmart(clazz, appCtx) as PluginApi
    Log.d(TAG, "Plugin instance created: $instance")

    runCatching {
        val onCreateMethod = clazz.getMethod("onCreate", Any::class.java)
        onCreateMethod.isAccessible = true
        onCreateMethod.invoke(instance, emptyMap<String, Any>())
        Log.d(TAG, "onCreate method invoked")
    }.onFailure {
        Log.w(TAG, "Failed to invoke onCreate: ${it.message}")
    }

    // Try getting Compose function via ComposePlugin interface first
    val contentBlock = runCatching {
        val iface = Class.forName(
            "com.dark.plugins.api.ComposePlugin",
            false,
            cl // plugin's DexClassLoader
        )

        if (iface.isAssignableFrom(clazz)) {
            val typed = iface.cast(instance)
            val method = iface.getDeclaredMethod("content")
            method.isAccessible = true
            method.invoke(typed) as? @Composable () -> Unit
        } else null
    }.getOrNull() ?: run {
        // Fallback: brute force find any zero-arg method returning Function0
        val fallback = clazz.methods.firstOrNull {
            it.parameterCount == 0 &&
                    it.returnType.name.contains("kotlin.jvm.functions.Function0")
        } ?: error("No composable content() method found on ${clazz.name}")
        fallback.isAccessible = true
        fallback.invoke(instance) as? @Composable () -> Unit
    }

    checkNotNull(contentBlock) { "content() did not return a valid ComposableBlock" }
    Log.d(TAG, "content() block retrieved successfully")

    return instance to contentBlock
}

fun createInstanceSmart(clazz: Class<*>, appCtx: Context): Any {
    Log.d(TAG, "Trying to instantiate ${clazz.name}")

    runCatching {
        val ctor = clazz.getDeclaredConstructor()
        ctor.isAccessible = true
        Log.d(TAG, "Using zero-arg constructor")
        return ctor.newInstance()
    }.onFailure { Log.w(TAG, "Zero-arg constructor failed: ${it.message}") }

    runCatching {
        val instanceField = clazz.getField("INSTANCE")
        Log.d(TAG, "Using Kotlin object INSTANCE")
        return instanceField.get(null) ?: error("INSTANCE was null")
    }.onFailure { Log.w(TAG, "INSTANCE field failed: ${it.message}") }

    runCatching {
        val companion = clazz.declaredClasses.firstOrNull { it.simpleName == "Companion" }
        if (companion != null) {
            val companionField = clazz.getField("Companion")
            val comp = companionField.get(null)
            val method = companion.methods.firstOrNull {
                it.name == "create" || it.name == "getInstance"
            }
            if (comp != null && method != null) {
                Log.d(TAG, "Using companion method: ${method.name}")
                return method.invoke(comp) ?: error("Companion method returned null")
            }
        }
    }.onFailure { Log.w(TAG, "Companion method failed: ${it.message}") }

    runCatching {
        val ctor = clazz.getDeclaredConstructor(Context::class.java)
        ctor.isAccessible = true
        Log.d(TAG, "Using (Context) constructor")
        return ctor.newInstance(appCtx)
    }.onFailure {
        Log.e(TAG, "Context constructor threw exception", it)
    }

    throw IllegalStateException("No suitable constructor/factory found for ${clazz.name}")
}
