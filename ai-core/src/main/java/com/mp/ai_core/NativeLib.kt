package com.mp.ai_core

import android.util.Log
import androidx.annotation.Keep
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow

private const val TAG = "MicroAI"

class NativeLib {

    external fun nativeInit(
        path: String,
        threads: Int,
        gpuLayers: Int,
        useMMAP: Boolean,
        useMLOCK: Boolean,
        ctxSize: Int,
        temp: Float,
        topK: Int,
        topP: Float,
        minP: Float
    ): Boolean

    external fun nativeRelease(): Boolean

    external fun nativeSetChatTemplate(template: String)

    external fun nativeGenerateStream(
        prompt: String,
        maxTokens: Int,
        callback: StreamCallback
    ): Boolean

    external fun nativeSetSystemPrompt(prompt: String)

    external fun nativeGetModelInfo(): String
    external fun nativeStopGeneration()

    companion object {
        init {
            System.loadLibrary("ai_core")
        }
    }

    /** Initialize model safely */
    fun initModel(
        path: String,
        threads: Int = Runtime.getRuntime().availableProcessors() / 2,
        gpuLayers: Int = 0,
        useMMAP: Boolean = true,
        useMLOCK: Boolean = false,
        ctxSize: Int = 2048,
        temp: Float = 0.7f,
        topK: Int = 20,
        topP: Float = 0.9f,
        minP: Float = 0.0f
    ): Boolean {
        return try {
            val ok = nativeInit(
                path, threads, gpuLayers, useMMAP, useMLOCK,
                ctxSize, temp, topK, topP, minP
            )
            if (!ok) {
                Log.e(TAG, "Model initialization failed at path: $path")
            }
            ok
        } catch (t: Throwable) {
            Log.e(TAG, "Model init error", t)
            false
        }
    }

    /** System prompt setter */
    fun setSystemPrompt(prompt: String) = nativeSetSystemPrompt(prompt)

    /** Streamed generation with model check */
    fun generateStreaming(
        prompt: String,
        maxTokens: Int = 512,
        uiScope: CoroutineScope,
        onStart: () -> Unit,
        onGenerate: (String) -> Unit,
        onError: (String) -> Unit,
        onDone: () -> Unit
    ): Job {
        // Model check before generating
        val modelInfo = runCatching { nativeGetModelInfo() }.getOrNull()
        if (modelInfo.isNullOrEmpty()) {
            val err = "No model loaded. Please call initModel() first."
            Log.e(TAG, err)
            onError(err)
            return Job().apply { complete() }
        }

        val channel = Channel<String>(Channel.UNLIMITED)

        val cb = object : StreamCallback {
            override fun onToken(token: String) {
                uiScope.launch { channel.send(token) }
            }

            override fun onDone() {
                uiScope.launch { channel.close() }
            }

            override fun onError(message: String) {
                uiScope.launch {
                    onError(message)
                    channel.close()
                }
            }
        }

        onStart()

        // Native call in IO scope
        return uiScope.launch(Dispatchers.IO) {
            try {
                nativeGenerateStream(prompt, maxTokens, cb)
            } catch (t: Throwable) {
                Log.e(TAG, "nativeGenerateStream error", t)
                onError(t.message ?: "Native error")
                channel.close()
            }
        }.also { job ->
            // Token collector on UI
            uiScope.launch {
                channel.receiveAsFlow().collectLatest { token ->
                    onGenerate(token)
                }
                onDone()
            }
        }
    }
}

@Keep
interface StreamCallback {
    fun onToken(token: String)
    fun onDone()
    fun onError(message: String)
}
