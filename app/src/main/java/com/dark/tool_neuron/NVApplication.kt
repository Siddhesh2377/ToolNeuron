package com.dark.tool_neuron

import android.app.Application
import android.util.Log
import com.dark.tool_neuron.data.AppSettingsDataStore
import com.dark.tool_neuron.di.AppContainer
import com.dark.tool_neuron.plugins.DeviceInfoPlugin
import com.dark.tool_neuron.plugins.FileManagerPlugin
import com.dark.tool_neuron.plugins.PluginManager
import com.dark.tool_neuron.plugins.WebSearchPlugin
import com.dark.tool_neuron.tts.TTSManager
import com.dark.tool_neuron.worker.LlmModelWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltAndroidApp
class NVApplication : Application() {

    companion object {
        private const val TAG = "NVApplication"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Application onCreate")

        // Initialize app container first
        AppContainer.init(applicationContext, this)

        // Register plugins
        PluginManager.registerPlugin(WebSearchPlugin())
        PluginManager.registerPlugin(DeviceInfoPlugin(applicationContext))
        PluginManager.registerPlugin(FileManagerPlugin(applicationContext))
        Log.d(TAG, "Plugins registered: ${PluginManager.registeredPlugins.value.size} plugins")

        // Initialize TTS Manager without auto-loading (loading controlled by settings)
        TTSManager.init(applicationContext, autoLoad = false)
        Log.d(TAG, "TTSManager initialized")

        // Conditionally load TTS model based on user setting
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        appScope.launch {
            try {
                val settings = AppSettingsDataStore(applicationContext)
                val loadOnStart = settings.loadTTSOnStart.first()
                if (loadOnStart) {
                    val modelDir = TTSManager.getModelDirectory()
                    if (modelDir != null) {
                        val success = TTSManager.loadModel(modelDir)
                        Log.d(TAG, "TTS model auto-loaded on start: $success")
                    }
                } else {
                    Log.d(TAG, "TTS auto-load disabled by user setting")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking TTS auto-load setting", e)
            }
        }

        // Note: Service binding moved to MainActivity to comply with Android 14+ foreground service restrictions
    }

    override fun onTerminate() {
        super.onTerminate()
        Log.d(TAG, "Application onTerminate")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Don't unbind on configuration changes
        if (level == TRIM_MEMORY_UI_HIDDEN) {
            Log.d(TAG, "UI hidden, keeping service bound")
        }
    }
}