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

    external fun nativeSetToolsJson(toolsJson: String) // NEW


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
        ctxSize: Int = 4096,
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
        onDone: () -> Unit,
        toolsJson: String? = null,                                        // NEW (optional)
        onToolCall: (name: String, argsJson: String) -> Unit = { _, _ -> } // NEW (optional)
    ): Job {
        // Guard: model present?
        val modelInfo = runCatching { nativeGetModelInfo() }.getOrNull()
        if (modelInfo.isNullOrEmpty()) {
            val err = "No model loaded. Please call initModel() first."
            Log.e(TAG, err)
            onError(err)
            return SupervisorJob().apply { complete() }
        }

        // Enable/disable tools for this turn
        if (toolsJson != null) nativeSetToolsJson(toolsJson) else nativeSetToolsJson("")

        val tokenCh = Channel<String>(capacity = 256)

        val batchPeriodMs = 35L
        val batcherJob = uiScope.launch(Dispatchers.Default) {
            val sb = StringBuilder()
            var lastFlush = System.nanoTime()
            fun flush(force: Boolean = false) {
                if (sb.isNotEmpty() && (force || (System.nanoTime() - lastFlush) / 1_000_000 >= batchPeriodMs)) {
                    val chunk = sb.toString()
                    sb.setLength(0)
                    lastFlush = System.nanoTime()
                    uiScope.launch(Dispatchers.Main.immediate) { onGenerate(chunk) }
                }
            }
            try {
                for (tok in tokenCh) {
                    sb.append(tok)
                    flush(false)
                }
            } finally {
                flush(true)
            }
        }

        val cb = object : StreamCallback {
            override fun onToken(token: String) {
                if (!tokenCh.trySend(token).isSuccess) {
                    Log.w(TAG, "Token dropped due to backpressure")
                }
            }
            // 🔥 NEW: tool-call surfaced to app; native will also end the turn with onDone()
            override fun onToolCall(name: String, argsJson: String) {
                uiScope.launch(Dispatchers.Main.immediate) {
                    onToolCall(name, argsJson)
                }
            }
            override fun onDone() { tokenCh.close() }
            override fun onError(message: String) {
                uiScope.launch(Dispatchers.Main.immediate) { onError(message) }
                tokenCh.close()
            }
        }

        onStart()

        val parentJob = uiScope.launch(Dispatchers.IO) {
            try {
                nativeGenerateStream(prompt, maxTokens, cb)
            } catch (t: Throwable) {
                Log.e(TAG, "nativeGenerateStream error", t)
                withContext(Dispatchers.Main.immediate) { onError(t.message ?: "Native error") }
            } finally {
                tokenCh.close()
            }
        }

        parentJob.invokeOnCompletion {
            batcherJob.cancel()
            uiScope.launch {
                batcherJob.join()
                withContext(Dispatchers.Main.immediate) { onDone() }
            }
        }
        return parentJob
    }


}

@Keep
interface StreamCallback {
    fun onToken(token: String)
    fun onToolCall(name: String, argsJson: String) // NEW
    fun onDone()
    fun onError(message: String)
}
