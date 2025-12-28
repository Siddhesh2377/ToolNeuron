package com.mp.ai_gguf

import com.mp.ai_gguf.models.StreamCallback

class GGUFNativeLib {

    external fun nativeGetStateSize(): Long
    external fun nativeLoadStateData(state: ByteArray): Boolean
    external fun nativeGetStateData(): ByteArray?
    external fun nativeLoadStateFile(path: String): Boolean
    external fun nativeSaveStateFile(path: String): Boolean
    external fun nativeRelease(): Boolean
    external fun nativeSetChatTemplate(template: String)
    external fun nativeSetToolsJson(toolsJson: String)
    external fun nativeSetSystemPrompt(prompt: String)
    external fun nativeGetModelInfo(): String
    external fun nativeStopGeneration()
    external fun nativeGenerateStream(
        prompt: String, maxTokens: Int, callback: StreamCallback
    ): Boolean

    /** Native init‑fn that reflects the *simpler* C++ signature. */
    external fun nativeInit(
        path: String,
        threads: Int,
        ctxSize: Int,
        temp: Float,
        topK: Int,
        topP: Float,
        minP: Float,
        mirostat: Int,
        mirostatTau: Float,
        mirostatEta: Float,
        seed: Int
    ): Boolean

    companion object {
        init {
            System.loadLibrary("ai_gguf")
        }
    }
}
