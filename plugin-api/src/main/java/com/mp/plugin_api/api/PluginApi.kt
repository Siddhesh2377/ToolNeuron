package com.mp.plugin_api.api

import android.content.Context
import androidx.annotation.CallSuper
import androidx.annotation.Keep
import androidx.annotation.MainThread
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import org.json.JSONObject

typealias ComposableBlock = @Composable () -> Unit

@Keep
@Stable
interface ComposePlugin {
    @Keep
    fun content(): ComposableBlock

    @Keep
    fun toolPreviewContent(data: String): ComposableBlock
}


@Keep
open class PluginApi : ComposePlugin {

    @Keep
    @MainThread
    @CallSuper
    open fun onCreate() {
    }

    @Keep
    @MainThread
    @CallSuper
    open fun onDestroy() {
    }

    @Keep
    @Composable
    open fun AppContent() {
        Text("Hello From Default Plugin :)")
    }

    @Keep
    @Composable
    open fun ToolPreviewContent(data: String) {
        Text("Hello From Default Plugin :)")
    }

    @Keep
    open fun onToolCalled(
        context: Context,
        toolName: String,
        args: JSONObject,
        callback: (result: Any) -> Unit,
    ) {

    }

    @Keep
    override fun content(): ComposableBlock = { AppContent() }

    @Keep
    override fun toolPreviewContent(data: String): ComposableBlock = { ToolPreviewContent(data) }
}
