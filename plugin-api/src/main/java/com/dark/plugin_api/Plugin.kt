package com.dark.plugin_api

import androidx.compose.runtime.Composable

interface Plugin {
    fun onLoad(context: PluginContext)
    fun onStart()
    fun onPause()
    fun onUnload()

    @Composable
    fun Content()
}

const val PLUGIN_API_VERSION: Int = 1
