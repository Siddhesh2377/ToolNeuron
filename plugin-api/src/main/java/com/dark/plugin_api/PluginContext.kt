package com.dark.plugin_api

import android.content.Context
import com.dark.plugin_api.api.DiagApi
import com.dark.plugin_api.api.HxsApi
import com.dark.plugin_api.api.NetworkApi
import com.dark.plugin_api.api.OnnxApi

class PluginContext(
    val pluginId: String,
    val appContext: Context,
    val onnx: OnnxApi,
    val hxs: HxsApi,
    val network: NetworkApi,
    val diag: DiagApi? = null,
)
