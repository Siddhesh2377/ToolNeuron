package com.dark.plugin_exc

import android.os.Build
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

internal object PluginBundle {

    fun extract(bundle: InputStream, destDir: File) {
        if (destDir.exists()) destDir.deleteRecursively()
        destDir.mkdirs()
        ZipInputStream(bundle).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val safeName = sanitizeEntryName(entry.name)
                if (safeName == null) {
                    entry = zip.nextEntry
                    continue
                }
                val outFile = File(destDir, safeName)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { zip.copyTo(it) }
                    if (safeName.endsWith(".so")) {
                        outFile.setReadable(true, false)
                        outFile.setExecutable(true, false)
                    }
                }
                entry = zip.nextEntry
            }
        }
        lockDown(destDir)
    }

    private fun lockDown(root: File) {
        root.walkBottomUp().forEach { f ->
            val name = f.name
            if (f.isFile && (name.endsWith(".dex") || name.endsWith(".so"))) {
                f.setWritable(false, false)
                f.setReadable(true, false)
            } else if (f.isDirectory) {
                f.setWritable(false, false)
                f.setReadable(true, false)
                f.setExecutable(true, false)
            }
        }
    }

    fun unlockForDelete(root: File) {
        if (!root.exists()) return
        root.walkTopDown().forEach { f ->
            f.setWritable(true, true)
            if (f.isDirectory) {
                f.setExecutable(true, true)
                f.setReadable(true, true)
            }
        }
    }

    fun detectNativeLibDir(rootDir: File): File? {
        for (abi in Build.SUPPORTED_ABIS) {
            val abiDir = File(rootDir, "lib/$abi")
            if (abiDir.exists() && abiDir.isDirectory) {
                val soFiles = abiDir.listFiles { f -> f.extension == "so" }
                if (!soFiles.isNullOrEmpty()) return abiDir
            }
        }
        return null
    }

    private fun sanitizeEntryName(name: String): String? {
        if (name.contains("..")) return null
        if (name.startsWith("/")) return null
        if (name.startsWith("\\")) return null
        return name
    }
}
