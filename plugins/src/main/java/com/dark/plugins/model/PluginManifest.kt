package com.dark.plugins.model

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class PluginManifest(
    val name: String = "",
    val description: String = "",
    val mainClass: String = "",
    val tools: List<Tools> = emptyList(),
    val version: String = "",
    val rawCode: String = "",
    val rawToolsCode: String = ""
)

data class Tools(
    val toolName: String = "",
    val path: String = "",
    val args: Map<String, Any?> = emptyMap() // allow numbers/bools too

)

object PluginTypeConverters {
    private val gson = Gson()

    @TypeConverter
    @JvmStatic
    fun toolsListToJson(value: List<Tools>?): String =
        gson.toJson(value ?: emptyList<Tools>())

    @TypeConverter
    @JvmStatic
    fun jsonToToolsList(json: String?): List<Tools> {
        if (json.isNullOrBlank()) return emptyList()
        val type = object : TypeToken<List<Tools>>() {}.type
        return gson.fromJson(json, type)
    }

    @TypeConverter
    @JvmStatic
    fun mapToJson(map: Map<String, Any?>?): String =
        gson.toJson(map ?: emptyMap<String, Any?>())

    @TypeConverter
    @JvmStatic
    fun jsonToMap(json: String?): Map<String, Any?> {
        if (json.isNullOrBlank()) return emptyMap()
        val type = object : TypeToken<Map<String, Any?>>() {}.type
        return gson.fromJson(json, type)
    }
}