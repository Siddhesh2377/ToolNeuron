package com.dark.plugins.repo

import android.content.Context
import android.util.Log
import com.dark.plugins.engine.PluginApi
import com.dark.plugins.sys.plugins.AppIOPlugin
import com.dark.plugins.sys.plugins.UiActionPlugin
import org.json.JSONObject

object PluginRegistry {
    val plugins: MutableList<PluginApi> = mutableListOf()

    fun init(context: Context) {
        plugins.clear()
        registerPlugin(AppIOPlugin(context), UiActionPlugin(context))
    }

    fun getPlugin(name: String): PluginApi? {
        return plugins.find {
            it.getPluginInfo().name == name
        }
    }

    fun registerPlugin(vararg plugin: PluginApi) {
        for (p in plugin) {
            plugins.add(p)
        }
    }

    fun runComplexPlugins(json: JSONObject) {
        Log.d("PluginRegistry", "Running complex plugins")

//        Log.d(
//            "PluginRegistry",
//            "WorkFlow: ${json.getString("title")}" + "\n Description: ${json.getString("description")}" + "\n Tools: ${
//                json.getJSONArray("tools_called").length()
//            }"
//        )
        val i = 0

        val steps = json.getJSONArray("steps")

        for (i in 0 until steps.length()) {
            val step = steps.getJSONObject(i)
            val pluginName = step.getString("tool")
            val args = step.getJSONObject("args")
            runPlugin(pluginName, args)
        }
    }

    fun runPlugin(string: String, args: Any) {
        plugins.find {
            it.getPluginInfo().name == string
        }.let {
            if (it == null) {
                Log.d("PluginRegistry", "Sry Bro, it is Null")
                return
            }
            Log.d("PluginRegistry", "Running plugin: ${it.getPluginInfo().name}")
            it.onCreate(args)
        }
    }
}