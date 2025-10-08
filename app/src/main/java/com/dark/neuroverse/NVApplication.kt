package com.dark.neuroverse

import android.app.Application
import com.dark.ai_module.workers.ModelManager
import com.dark.neuroverse.util.initOpenRouterFromPrefs
import com.dark.plugins.manager.PluginManager
import com.mp.data_hub_lib.manager.DataHubManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * NVApplication — trims startup work, avoids blocking main thread,
 * and preloads a preferred or first-available model if present.
 */
class NVApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ModelManager.init(applicationContext)
        PluginManager.init(applicationContext)
        DataHubManager.init(applicationContext)

        appScope.launch {
            initOpenRouterFromPrefs(applicationContext)
        }
    }

    companion object {
        private const val TAG = "NVApplication"
    }
}
