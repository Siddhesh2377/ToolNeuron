package com.dark.plugins.worker

import android.content.Context
import android.util.Log
import com.dark.plugins.model.LoadedPlugin
import org.json.JSONObject

object ToolRunner {

    fun run(loadedPlugin: LoadedPlugin, context: Context, data: JSONObject) {
        Log.d("ToolRunner", "Running tool for plugin ${loadedPlugin.manifest?.name}")

        if (loadedPlugin.api == null) Log.e("ToolRunner", "API is null")
        if (loadedPlugin.api != null) Log.e("ToolRunner", "API is Not Null ${loadedPlugin.api.getPluginInfo()}")

        loadedPlugin.api?.runTool(context, data.getString("tool"), data.getJSONObject("arguments")) { result ->
            Log.d("ToolRunner", "Tool result: $result")
        }
    }


}