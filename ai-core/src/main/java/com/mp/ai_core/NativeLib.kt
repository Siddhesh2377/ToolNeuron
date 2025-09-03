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
        onGenerate: (String) -> Unit, // will be called with coalesced chunks
        onError: (String) -> Unit,
        onDone: () -> Unit
    ): Job {
        // Guard: model present?
        val modelInfo = runCatching { nativeGetModelInfo() }.getOrNull()
        if (modelInfo.isNullOrEmpty()) {
            val err = "No model loaded. Please call initModel() first."
            Log.e(TAG, err)
            onError(err)
            // Return an already-completed job (no work running)
            return SupervisorJob().apply { complete() }
        }

        // Channel for tokens coming from JNI callback (no per-token coroutines)
        // Small buffer keeps memory bounded; adjust if needed.
        val tokenCh = Channel<String>(capacity = 256)

        // A tiny batcher to reduce UI updates; adjust period as you like
        val batchPeriodMs = 35L
        val batcherJob = uiScope.launch(Dispatchers.Default) {
            val sb = StringBuilder()
            var lastFlush = System.nanoTime()

            fun flushIfNeeded(force: Boolean = false) {
                if (sb.isNotEmpty() && (force ||
                            ((System.nanoTime() - lastFlush) / 1_000_000) >= batchPeriodMs)) {
                    val chunk = sb.toString()
                    sb.setLength(0)
                    lastFlush = System.nanoTime()
                    // Switch to Main only for the actual UI callback
                    uiScope.launch(Dispatchers.Main.immediate) {
                        onGenerate(chunk)
                    }
                }
            }

            try {
                for (tok in tokenCh) {
                    sb.append(tok)
                    flushIfNeeded(force = false)
                }
            } finally {
                // Final flush on normal close or cancellation
                flushIfNeeded(force = true)
            }
        }

        // JNI callback -> push tokens without launching a coroutine each time
        val cb = object : StreamCallback {
            override fun onToken(token: String) {
                // Non-suspending, thread-safe. If buffer is full, drop the token to protect UI.
                // If you prefer to block instead, use tokenCh.trySend(token).isSuccess check.
                if (!tokenCh.trySend(token).isSuccess) {
                    // Optional: count drops for diagnostics
                    Log.w(TAG, "Token dropped due to backpressure")
                }
            }
            override fun onDone() {
                // Close from callback side as well (safe even if already closed)
                tokenCh.close()
            }
            override fun onError(message: String) {
                // Surface error and close stream
                uiScope.launch(Dispatchers.Main.immediate) { onError(message) }
                tokenCh.close()
            }
        }

        onStart()

        // Parent job that runs native call and ties all children together
        val parentJob = uiScope.launch(Dispatchers.IO) {
            try {
                // This blocks until native finishes or is stopped
                nativeGenerateStream(prompt, maxTokens, cb)
            } catch (t: Throwable) {
                Log.e(TAG, "nativeGenerateStream error", t)
                withContext(Dispatchers.Main.immediate) {
                    onError(t.message ?: "Native error")
                }
            } finally {
                // Ensure the channel is closed even if native didn't call onDone()
                tokenCh.close()
            }
        }

        // When the parent completes (normal/stop/cancel), finish batcher then call onDone once.
        parentJob.invokeOnCompletion {
            // Ensure batcher is finished and last chunk delivered
            batcherJob.cancel() // this triggers final flush in finally{}
            uiScope.launch {
                // Give the batcher a turn to flush
                batcherJob.join()
                withContext(Dispatchers.Main.immediate) {
                    onDone()
                }
            }
        }

        return parentJob
    }

}

@Keep
interface StreamCallback {
    fun onToken(token: String)
    fun onDone()
    fun onError(message: String)
}