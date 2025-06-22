package com.dark.neuroverse

import android.app.Application
import android.util.Log
import com.dark.plugin_runtime.engine.PluginManager

class NeuroVerseApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d("NeuroVerseApplication", "✅ Application started")
        PluginManager.init(applicationContext)
    }
}
