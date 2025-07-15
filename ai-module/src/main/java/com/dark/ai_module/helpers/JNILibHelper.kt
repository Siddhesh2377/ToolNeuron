package com.dark.ai_module.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Log
import io.shubham0204.smollm.SmolLM
import io.shubham0204.smollm.workers.JNIWorker
import java.io.File

object JNILibHelper {

    fun checkIfJNILibExists(context: Context): Boolean {
        val nativeJniPath = File(context.filesDir, "jniLibs")
        if (!nativeJniPath.exists() || !nativeJniPath.isDirectory) {
            Log.d("JNILibCheck", "Directory does not exist.")
            return false
        }

        val soFiles = nativeJniPath.listFiles { file -> file.name.endsWith(".so") }
        val exists = !soFiles.isNullOrEmpty()

        Log.d("JNILibCheck", "Found JNI .so files: ${soFiles?.map { it.name }}")
        return exists
    }

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    suspend fun loadJNILib(context: Context, onComplete: () -> Unit) {
        val nativeJniPath = File(context.filesDir, "jniLibs").apply { mkdirs() }
        val fileName = JNIWorker.getCompatibleJniLibName()
        val soFile = File(nativeJniPath, "lib$fileName.so")
        if (soFile.exists()) {
            Log.d("JNILibLoad", "Loading JNI lib from: ${soFile.absolutePath}")
            System.load(soFile.absolutePath)
            onComplete()
        } else {
            JNIWorker.downloadLib(context){
                Log.d("JNILibLoad", "Downloaded JNI lib: $fileName")
                onComplete()
            }
        }
    }


}