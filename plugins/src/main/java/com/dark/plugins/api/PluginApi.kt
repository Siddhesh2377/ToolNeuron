package com.dark.plugins.api

import android.content.Context
import androidx.annotation.CallSuper
import androidx.annotation.Keep
import androidx.annotation.MainThread
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.dark.ai_module.workers.ModelManager
import com.dark.plugins.manager.PluginManager
import org.json.JSONObject

typealias ComposableBlock = @Composable () -> Unit

@Keep
@Stable
interface ComposePlugin {
    @Keep
            /** Return top-level UI to render. Host will call this frequently; avoid heavy work. */
    fun content(): ComposableBlock
}

@Keep
@Immutable
data class PluginInfo(
    val name: String = "", val description: String = ""
)

@Keep
open class PluginApi(ctx: Context) : ComposePlugin {

    protected val appContext: Context = ctx.applicationContext

    open fun getPluginInfo(): PluginInfo = PluginInfo()

    @MainThread
    @CallSuper
    open fun onCreate(data: Any) {
    }

    @MainThread
    @CallSuper
    open fun onDestroy() {
    }

    open fun callPlugin(name: String, data: Any) {
        PluginManager.runPlugin(appContext, name, data)
    }

    @Composable
    open fun AppContent() {
        Text("Hello From Default Plugin :)")
    }

    open suspend fun aiCall(
        input: JSONObject, onToken: (String) -> Unit
    ): JSONObject {
        val temp: String = ModelManager.generateStreaming(
            prompt = input.toString(),
            gen = ModelManager.GenerationParams(),
            onToken = onToken,
            toolJson = null)
        return JSONObject().apply { put("response", temp) }
    }

    open suspend fun stopGeneration() {
        ModelManager.stopGeneration()
    }

    @Keep
    open fun runTool(
        context: Context,
        toolName: String,
        args: JSONObject,
        callback: (result: Any) -> Unit,
    ) {

    }

    @Keep
    override fun content(): ComposableBlock = { AppContent() }

}
