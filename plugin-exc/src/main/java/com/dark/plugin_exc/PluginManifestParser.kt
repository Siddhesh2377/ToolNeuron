package com.dark.plugin_exc

import com.dark.plugin_api.PluginManifest
import kotlinx.serialization.json.Json

internal object PluginManifestParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    fun parse(jsonString: String): PluginManifest =
        json.decodeFromString(PluginManifest.serializer(), jsonString)

    fun serialize(manifest: PluginManifest): String =
        json.encodeToString(PluginManifest.serializer(), manifest)
}
