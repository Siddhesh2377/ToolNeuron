package com.dark.tool_neuron

import android.app.Application
import android.util.Log
import com.dark.tool_neuron.di.AppContainer
import com.dark.tool_neuron.worker.LlmModelWorker
import dagger.hilt.android.HiltAndroidApp

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

        // Bind service with application context (survives configuration changes)
        LlmModelWorker.bindService(applicationContext)
    }

    override fun onTerminate() {
        super.onTerminate()
        Log.d(TAG, "Application onTerminate")
        LlmModelWorker.unbindService()
        AppContainer.shutdown()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Don't unbind on configuration changes
        if (level == TRIM_MEMORY_UI_HIDDEN) {
            Log.d(TAG, "UI hidden, keeping service bound")
        }
    }
}