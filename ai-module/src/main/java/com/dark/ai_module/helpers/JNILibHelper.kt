package com.dark.ai_module.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Log
import com.dark.ai_module.ai.Neuron
import com.dark.ai_module.workers.ModelManager
import io.shubham0204.smollm.SmolLM
import io.shubham0204.smollm.workers.JNIWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

object JNILibHelper {
    fun checkIfJNILibExists(context: Context): Boolean {
        val nativeJniPath = File(context.filesDir, "jniLibs")
        val soFiles = nativeJniPath.listFiles { it.name.endsWith(".so") }
        return !soFiles.isNullOrEmpty().also {
            Log.d("JNILibCheck", "Found JNI libs: ${soFiles?.map { it.name }}")
        }
    }

    suspend fun loadJNILib(context: Context, onComplete: () -> Unit) {
        val path = File(context.filesDir, "jniLibs").apply { mkdirs() }
        val libName = JNIWorker.getCompatibleJniLibName()
        val soFile = File(path, "lib$libName.so")

        if (soFile.exists()) {
            Log.d("JNILibLoad", "Lib already exists: ${soFile.absolutePath}")
            onComplete()
        } else {
            JNIWorker.downloadLib(context, libName) {
                Log.d("JNILibLoad", "Downloaded lib: $libName")
                onComplete()
            }
        }
    }
}
