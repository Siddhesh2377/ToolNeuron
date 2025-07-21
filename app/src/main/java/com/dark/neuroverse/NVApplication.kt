package com.dark.neuroverse

import android.app.Application
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.dark.ai_module.workers.ModelManager
import com.dark.ai_module.workers.ModelParams.Emotional
import com.dark.ai_module.workers.ModelParams.Professional
import com.dark.neuroverse.data.UserPrefs
import com.mp.updatemanager.UpdateScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class NVApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
        ModelManager.init(applicationContext)
        UpdateScheduler.scheduleTestUpdateCheck(applicationContext, 10)
        CoroutineScope(Dispatchers.IO).launch {
            ModelManager.updateModelParams(
                Professional(
                    UserPrefs.getModelPParams(applicationContext).firstOrNull() ?: 2.5f
                ),
                Emotional(
                    UserPrefs.getModelEParams(applicationContext).firstOrNull() ?: 7.3f
                )
            )
        }
    }
}