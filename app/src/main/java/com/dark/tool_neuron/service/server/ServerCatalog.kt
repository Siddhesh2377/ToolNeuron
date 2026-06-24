package com.dark.tool_neuron.service.server

import org.json.JSONArray
import org.json.JSONObject

enum class ServerEngineKind(val token: String) {
    CHAT_GGUF("gguf"),
    VLM("vlm"),
    EMBEDDING("embedding"),
    TTS("tts"),
    STT("stt"),
    IMAGE_GEN("image_gen"),
    IMAGE_UPSCALER("image_upscaler"),
    ;

    companion object {
        fun fromToken(s: String?): ServerEngineKind? =
            entries.firstOrNull { it.token == s }
    }
}

data class ServerCatalogEntry(
    val id: String,
    val name: String,
    val path: String,
    val mmprojPath: String,
    val configJson: String,
    val kind: ServerEngineKind,
    val createdUnix: Long,
    val primary: Boolean = false,
    val defaultModel: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("path", path)
        if (mmprojPath.isNotBlank()) put("mmproj_path", mmprojPath)
        put("config_json", configJson)
        put("type", kind.token)
        put("created", createdUnix)
        if (primary) put("primary", true)
        if (defaultModel) put("default", true)
    }
}

class ServerCatalog(
    private val entriesByKind: Map<ServerEngineKind, List<ServerCatalogEntry>>,
) {
    val all: List<ServerCatalogEntry> = entriesByKind.values.flatten()

    fun byId(id: String): ServerCatalogEntry? = all.firstOrNull { it.id == id }

    fun firstOf(kind: ServerEngineKind): ServerCatalogEntry? =
        entriesByKind[kind]?.firstOrNull()

    fun primary(): ServerCatalogEntry? =
        all.firstOrNull { it.primary }

    fun listOf(kind: ServerEngineKind): List<ServerCatalogEntry> =
        entriesByKind[kind].orEmpty()

    fun hasAny(kind: ServerEngineKind): Boolean =
        entriesByKind[kind]?.isNotEmpty() == true

    fun toJsonArray(): JSONArray = JSONArray().apply {
        all.forEach { put(it.toJson()) }
    }

    companion object {
        fun fromJsonArray(arr: JSONArray): ServerCatalog {
            val grouped = HashMap<ServerEngineKind, MutableList<ServerCatalogEntry>>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val kind = ServerEngineKind.fromToken(o.optString("type")) ?: continue
                val entry = ServerCatalogEntry(
                    id = o.optString("id"),
                    name = o.optString("name", o.optString("id")),
                    path = o.optString("path"),
                    mmprojPath = o.optString("mmproj_path", ""),
                    configJson = o.optString("config_json", "{}"),
                    kind = kind,
                    createdUnix = o.optLong("created"),
                    primary = o.optBoolean("primary", false),
                    defaultModel = o.optBoolean("default", false),
                )
                if (entry.id.isBlank() || entry.path.isBlank()) continue
                grouped.getOrPut(kind) { mutableListOf() }.add(entry)
            }
            return ServerCatalog(grouped)
        }
    }
}
