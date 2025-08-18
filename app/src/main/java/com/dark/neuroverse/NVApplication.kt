package com.dark.neuroverse

import android.app.Application
import android.util.Log
import com.dark.ai_module.workers.ModelManager
import com.dark.ai_module.workers.ModelParams
import com.dark.neuroverse.data.UserPrefs
import com.dark.plugins.manager.PluginManager
import com.mp.updatemanager.UpdateCenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * NVApplication — trims startup work, avoids blocking main thread,
 * and preloads a preferred or first-available model if present.
 */
class NVApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // Synchronous, cheap initializations
        ModelManager.init(applicationContext)
        UpdateCenter.init(this)
        PluginManager.init(applicationContext)

        // Lightweight background bootstrap
        appScope.launch {
            // 1) Apply user-tuned decoding params (with safe fallbacks)
            val professional = UserPrefs.getModelPParams(applicationContext).firstOrNull() ?: 2.5f
            val emotional = UserPrefs.getModelEParams(applicationContext).firstOrNull() ?: 7.3f
            ModelManager.updateModelParams(
                ModelParams.Professional(professional),
                ModelParams.Emotional(emotional)
            )

            // 2) Try to auto-load a preferred model; otherwise load the first available
            runCatching {
                val preferred = ModelManager.getModel(PREFERRED_MODEL_NAME)
                val candidate = preferred ?: ModelManager.getFirstModel()
                if (candidate != null) {
                    // Suspends until the model is fully ready
                    ModelManager.loadModelAwait(modelData = candidate)
                }
            }.onFailure { err ->
                Log.e(TAG, "Auto-load model failed", err)
            }
        }
    }

    companion object {
        private const val TAG = "NVApplication"
        private const val PREFERRED_MODEL_NAME = "Lucy-128k-gguf"
    }
}
