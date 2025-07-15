package com.dark.neuroverse

import android.app.Application
import com.dark.ai_module.ai.Neuron
import com.dark.ai_module.workers.ModelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class NVApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        ModelManager.init(applicationContext)
    }
}