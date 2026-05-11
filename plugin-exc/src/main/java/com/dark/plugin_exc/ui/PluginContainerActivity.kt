package com.dark.plugin_exc.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dark.plugin_exc.PluginExecutor

class PluginContainerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val executor = host?.executor ?: run { finish(); return }

        applyPluginIntent(intent, executor)

        setContent {
            val active by executor.activePlugin.collectAsState()
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = active,
                    transitionSpec = {
                        (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.96f))
                            .togetherWith(
                                fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 1.04f)
                            )
                    },
                    label = "plugin-switch",
                ) { pluginId ->
                    if (pluginId != null) {
                        executor.instance(pluginId)?.plugin?.Content()
                    }
                }

                PluginDock(
                    executor = executor,
                    onClose = ::finish,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val executor = host?.executor ?: return
        applyPluginIntent(intent, executor)
    }

    override fun onPause() {
        super.onPause()
        if (isFinishing) {
            host?.executor?.activePlugin?.value?.let { id ->
                host?.executor?.instance(id)?.pause()
            }
        }
    }

    private fun applyPluginIntent(intent: Intent, executor: PluginExecutor) {
        val requestedId = intent.getStringExtra(EXTRA_PLUGIN_ID) ?: return
        runCatching { executor.switchTo(requestedId) }
    }

    interface Host {
        val executor: PluginExecutor
    }

    companion object {
        const val EXTRA_PLUGIN_ID = "com.dark.plugin_exc.PLUGIN_ID"

        @Volatile internal var host: Host? = null

        fun bind(host: Host) {
            this.host = host
        }

        fun launchIntent(context: Context, pluginId: String): Intent =
            Intent(context, PluginContainerActivity::class.java).apply {
                putExtra(EXTRA_PLUGIN_ID, pluginId)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
    }
}
