package com.dark.plugins.worker

import android.content.Context
import android.util.Log
import com.dark.plugins.model.LoadedPlugin
import org.json.JSONObject

object ToolRunner {

    fun run(loadedPlugin: LoadedPlugin, context: Context, data: JSONObject, onResult: (result: Any) -> Unit) {
        Log.d("ToolRunner", "Running tool for plugin ${loadedPlugin.manifest?.name}")
        data.has("tool").let {
            if (!it) {
                Log.e("ToolRunner", "No tool specified in data")
                return
            }
            loadedPlugin.api?.runTool(
                context,
                data.getString("tool"),
                data.getJSONObject("args"),

            ) { result ->
                Log.d("ToolRunner", "Tool result: $result")
                onResult(result)
            }
        }
    }


}