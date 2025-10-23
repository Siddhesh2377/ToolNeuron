package com.dark.plugins.worker

import android.util.Log
import com.dark.plugins.model.MetaData
import com.dark.plugins.model.PluginManifest
import com.dark.plugins.model.Tools
import org.json.JSONArray
import org.json.JSONObject

class PluginManifestWorker {

    private val jsonCode: String
    private val root: JSONObject

    constructor(manifestCode: String) {
        require(manifestCode.isNotBlank()) { "Manifest JSON string cannot be null or empty" }
        this.jsonCode = manifestCode
        this.root = JSONObject(manifestCode)
    }

    fun getMainClass(): String = root.getString("mainClass")

    fun getPluginName(): String = root.getString("name")

    fun getPluginDescription(): String = root.getString("description")

    /**
     * Optional. Returns "" if missing.
     */
    fun getPluginVersion(): String = root.optString("version", "")

    /**
     * Optional. Returns default MetaData() if missing.
     */
    fun getMetaData(): MetaData {
        val m = root.optJSONObject("metaData") ?: return MetaData()
        return MetaData(
            author = m.optString("author", ""),
            role = m.optString("role", ""),
            pluginApi = m.optString("pluginApi", "")
        )
    }

    /**
     * Parse the tools array safely. Each tool: { toolName, path, args:{} }
     */
    fun getTools(): List<Tools> {
        val out = mutableListOf<Tools>()
        val toolsArr: JSONArray = root.optJSONArray("tools") ?: return emptyList()

        for (i in 0 until toolsArr.length()) {
            val tObj = toolsArr.optJSONObject(i) ?: continue
            val toolName = tObj.optString("toolName", "")
            val description = tObj.optString("description", "")
            val argsObj = tObj.optJSONObject("args") ?: JSONObject()

            Log.d("TOOL MANAGER", "Tool: $toolName, description: $description")

            out += Tools(
                toolName = toolName, description = description, args = argsObj.toMap()
            )
        }
        return out
    }

    fun getPluginManifest(): PluginManifest {
        val toolsArr: JSONArray = root.optJSONArray("tools") ?: JSONArray()

        return PluginManifest(
            name = getPluginName(),
            description = getPluginDescription(),
            mainClass = getMainClass(),
            tools = getTools(),
            version = getPluginVersion(),
            rawCode = this.jsonCode,
            metaData = getMetaData(),            // <- include parsed MetaData
            rawToolsCode = toolsArr.toString()
        )
    }

    fun getManifestCode(): String = this.jsonCode


}

/** ---------- JSON helpers ---------- */
private fun JSONObject.toMap(): Map<String, Any?> {
    val map = mutableMapOf<String, Any?>()
    val it = keys()
    while (it.hasNext()) {
        val k = it.next()
        val v = this.opt(k)
        map[k] = when (v) {
            is JSONObject -> v.toMap()
            is JSONArray -> v.toList()
            JSONObject.NULL -> null
            else -> v
        }
    }
    return map
}

private fun JSONArray.toList(): List<Any?> {
    val list = mutableListOf<Any?>()
    for (i in 0 until length()) {
        val v = opt(i)
        list += when (v) {
            is JSONObject -> v.toMap()
            is JSONArray -> v.toList()
            JSONObject.NULL -> null
            else -> v
        }
    }
    return list
}
