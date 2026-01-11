package com.dark.tool_neuron

import android.app.Application
import com.dark.tool_neuron.di.AppContainer
import com.dark.tool_neuron.worker.LlmModelWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class NVApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppContainer.init(applicationContext)
        LlmModelWorker.bindService(applicationContext)

    }
}