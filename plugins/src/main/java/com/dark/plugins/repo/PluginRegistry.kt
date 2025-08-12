package com.dark.plugins.repo

import android.content.Context
import android.util.Log
import com.dark.plugins.engine.PluginApi
import com.dark.plugins.sys.plugins.AppIOPlugin
import com.dark.plugins.sys.plugins.UiActionPlugin
import org.json.JSONObject

object PluginRegistry {
    val plugins: MutableList<PluginRepo> = mutableListOf()

    fun init(context: Context) {
        plugins.clear()

    }

    fun getPlugin(name: String): PluginRepo? {
        return plugins.find {
            it.name == name
        }
    }

    fun registerPlugin(vararg paths: String) {
        for (p in paths) {
            plugins.add(PluginRepo(p, "", false))
        }
    }

    fun registerFromAsset(vararg name: String){

    }
}