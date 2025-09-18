package com.dark.plugins.worker

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.dark.plugins.model.LoadedPlugin
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

object ToolRunner {
    private const val TAG = "ToolRunner"
    private val ALIASES = mapOf(
        "read" to "searchWeb",   // safety net for older prompts
        "search" to "searchWeb"
    )

    fun run(
        loadedPlugin: LoadedPlugin,
        context: Context,
        data: JSONObject,
        timeoutMs: Long = 30_000L,
        onResult: (result: JSONObject) -> Unit
    ) {
        val api = loadedPlugin.api ?: return onResult(errorJson("plugin_api_null"))
        val originalTool = data.optString("tool", "").trim()
        val args = data.optJSONObject("args") ?: return onResult(errorJson("invalid_payload"))

        fun invoke(tool: String, alreadyRetried: Boolean) {
            val handler = Handler(Looper.getMainLooper())
            val done = AtomicBoolean(false)
            fun complete(any: JSONObject) { if (done.compareAndSet(false, true)) onResult(any) }
            val timeout = Runnable { complete(errorJson("timeout", mapOf("tool" to tool, "ms" to timeoutMs))) }
            handler.postDelayed(timeout, timeoutMs)

            try {
                api.runTool(context, tool, args) { result ->
                    handler.removeCallbacks(timeout)
                    val s = result.toString()
                    Log.i("TOOL RES", "here it is $result")
                    // Detect "Unknown tool" style message and retry via alias once
                    if (!alreadyRetried && s.contains("Unknown tool", ignoreCase = true)) {
                        val alias = ALIASES[tool]
                        if (!alias.isNullOrBlank()) {
                            Log.w(TAG, "Unknown tool '$tool', retrying as '$alias'")
                            invoke(alias, true); return@runTool
                        }
                    }
                    complete(JSONObject(result.toString()))
                }
            } catch (t: Throwable) {
                handler.removeCallbacks(timeout)
                complete(errorJson("exception", mapOf("tool" to tool, "message" to (t.message ?: t::class.java.simpleName))))
            }
        }

        val tool = originalTool.ifBlank { return onResult(errorJson("missing_tool_name")) }
        Log.d(TAG, "Running tool='$tool' in plugin='${loadedPlugin.manifest?.name}' args=$args")
        invoke(tool, alreadyRetried = false)
    }

    private fun errorJson(code: String, meta: Map<String, Any?> = emptyMap(), details: String? = null) =
        org.json.JSONObject().apply {
            put("ok", false); put("error", code)
            details?.let { put("details", it) }
            if (meta.isNotEmpty()) put("meta", org.json.JSONObject().apply { meta.forEach { (k, v) -> put(k, v) } })
        }
}
