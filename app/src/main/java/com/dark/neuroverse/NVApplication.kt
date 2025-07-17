package com.dark.neuroverse

import android.app.Application
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.dark.ai_module.ai.Neuron
import com.dark.ai_module.helpers.JNILibHelper
import com.dark.ai_module.workers.ModelManager
import com.dark.neuroverse.data.UserPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class NVApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
        ModelManager.init(applicationContext)
        CoroutineScope(Dispatchers.IO).launch {
            UserPrefs.isJNIInstalled(applicationContext).collect {
                if (it == true) loadJNI {}
            }
        }
    }

    private suspend fun loadJNI(onLoaded: () -> Unit) {
        val model = ModelManager.getFirstModel()?.modelPath ?: ""
        // Wait until JNI lib is loaded (suspend)
        JNILibHelper.loadJNILib(applicationContext) {
            // Then call Neuron.loadModel (also suspend!)
            Neuron.loadModel(
                File(model),
                context = applicationContext,
                systemPrompt = "You are a helpful assistant."
            ) {
                onLoaded()
            }
        }
    }
}