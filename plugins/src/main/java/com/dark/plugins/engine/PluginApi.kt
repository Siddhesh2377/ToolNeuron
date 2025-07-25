package com.dark.plugins.engine

import android.content.Context
import com.dark.plugins.repo.PluginRegistry

open class PluginApi(context: Context): PLG{

    var pluginInterface: PLG = this

    open fun getPluginInfo(): PluginInfo {
        return PluginInfo()
    }

    open fun onCreate(data: Any) {}

    open fun onDestroy() {}

    override fun callPlugin(name: String, data: Any) {
        PluginRegistry.runPlugin(name, data)
    }
}

interface PLG {
    fun callPlugin(name: String, data: Any)
}