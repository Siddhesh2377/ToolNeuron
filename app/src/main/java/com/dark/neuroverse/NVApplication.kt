package com.dark.neuroverse

import android.app.Application
import com.dark.ai_module.workers.AudioManager
import com.dark.ai_module.workers.ModelManager
import com.dark.neuroverse.util.initOpenRouterFromPrefs
import com.dark.neuroverse.worker.ChatManager
import com.dark.neuroverse.worker.UserDataManager
import com.dark.plugins.manager.PluginManager
import com.dark.neuroverse.worker.DataHubManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NVApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            UserDataManager.init(applicationContext)
            ChatManager.refreshChats()
            ModelManager.init(applicationContext)
            AudioManager.init(applicationContext)
            PluginManager.init(applicationContext)
            DataHubManager.init(applicationContext)
            initOpenRouterFromPrefs(applicationContext)
        }
    }
}
