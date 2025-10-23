package com.dark.plugins.worker

import android.content.Context
import com.dark.plugins.model.LoadedPlugin
import org.json.JSONObject

object ToolRunner {
    fun run(
        loadedPlugin: LoadedPlugin,
        context: Context,
        data: JSONObject,
        onResult: (result: JSONObject) -> Unit
    ) {
        val api = loadedPlugin.api ?: return onResult(errorJson("plugin_api_null"))
        val tool = data.optString("tool", "").trim()
        val args = data.optJSONObject("args") ?: return onResult(errorJson("invalid_payload"))

        try {
            api.onToolCalled(context, tool, args) { result ->
                onResult(result as? JSONObject ?: errorJson("invalid_result"))
            }
        } catch (t: Throwable) {
            onResult(
                errorJson(
                    "exception",
                    mapOf("tool" to tool, "message" to (t.message ?: t::class.java.simpleName))
                )
            )
        }
    }

    private fun errorJson(
        code: String, meta: Map<String, Any?> = emptyMap(), details: String? = null
    ) = JSONObject().apply {
        put("ok", false); put("error", code)
        details?.let { put("details", it) }
        if (meta.isNotEmpty()) put(
            "meta", JSONObject().apply { meta.forEach { (k, v) -> put(k, v) } })
    }
}
