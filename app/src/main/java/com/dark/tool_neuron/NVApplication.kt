package com.dark.tool_neuron

import android.app.Application
import com.dark.tool_neuron.di.AppContainer
import com.dark.tool_neuron.worker.LlmModelWorker

class NVApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppContainer.init(applicationContext)
        LlmModelWorker.bindService(applicationContext)
    }
}