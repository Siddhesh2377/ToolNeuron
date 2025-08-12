package com.dark.plugins.engine

import android.content.Context
import androidx.annotation.CallSuper
import androidx.annotation.Keep
import androidx.annotation.MainThread
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.dark.ai_module.ai.Neuron
import com.dark.plugins.repo.PluginRegistry
import com.dark.plugins.worker.PluginManager
import org.json.JSONObject

typealias ComposableBlock = @Composable () -> Unit

@Stable
interface ComposePlugin {
    /** Return top-level UI to render. Host will call this frequently; avoid heavy work. */
    fun content(): ComposableBlock
}

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
        PluginManager.runPlugin(appContext, path, args)
    }

    @Composable
    open fun AppContent() {
        Text("Hello From Default Plugin :)")
    }

    open suspend fun aiCall(input: JSONObject, onToken: (String) -> Unit): JSONObject {
        val temp: String = Neuron.generateStreamAndWait(input.toString()) {
            onToken(it)
        }

        return JSONObject().apply {
            put("response", temp)
        }
    }

    override fun content(): ComposableBlock = { AppContent() }

}
