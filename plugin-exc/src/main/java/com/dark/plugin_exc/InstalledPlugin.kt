package com.dark.plugin_exc

import com.dark.plugin_api.PluginManifest
import java.io.File

data class InstalledPlugin(
    val manifest: PluginManifest,
    val rootDir: File,
    val dexFile: File,
    val nativeLibDir: File?,
    val installedAt: Long,
)
