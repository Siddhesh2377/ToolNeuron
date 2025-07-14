package com.dark.neuroverse

import android.app.Application
import com.dark.ai_module.workers.ModelManager

class NVApplication: Application() {

    override fun onCreate() {
        super.onCreate()
        ModelManager.init(applicationContext)
    }

}