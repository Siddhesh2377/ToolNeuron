package com.mp.ai_gguf

import com.mp.ai_gguf.models.EmbeddingCallback
import com.mp.ai_gguf.models.StreamCallback

/**
 * Drop-in replacement for ai_gguf AAR's GGUFNativeLib.
 * JNI bridge to llama.cpp via gguf_jni.cpp.
 */
class GGUFNativeLib {

    // ---- Model Loading ----

    external fun nativeLoadModel(
        path: String, nCtx: Int, nThreads: Int,
        flashAttn: Boolean, cacheTypeK: String, cacheTypeV: String
    ): Boolean

    external fun nativeLoadModelFromFd(
        fd: Int, nCtx: Int, nThreads: Int,
        flashAttn: Boolean, cacheTypeK: String, cacheTypeV: String
    ): Boolean

    // ---- Sampling Configuration ----

    external fun nativeSetSampling(
        temperature: Float, topK: Int, topP: Float, minP: Float,
        mirostat: Int, mirostatTau: Float, mirostatEta: Float, seed: Int
    )

    // ---- Prompt Configuration ----

    external fun nativeSetSystemPrompt(prompt: String)
    external fun nativeSetChatTemplate(template: String)

    // ---- Generation ----

    external fun nativeGenerateStream(
        prompt: String, maxTokens: Int, callback: StreamCallback
    ): Boolean

    external fun nativeGenerateStreamMultiTurn(
        messagesJson: String, maxTokens: Int, callback: StreamCallback
    ): Boolean

    external fun nativeStopGeneration()

    // ---- Cleanup ----

    external fun nativeRelease()

    // ---- Model Info ----

    external fun nativeGetModelInfo(): String?

    // ---- Tool Calling ----

    external fun nativeIsToolCallingSupported(): Boolean
    external fun nativeSetToolsJson(toolsJson: String)
    external fun nativeSetGrammarMode(mode: Int)
    external fun nativeSetTypedGrammar(enabled: Boolean)

    // ---- Persona Engine ----

    external fun nativeUpdateSamplerParams(paramsJson: String): Boolean
    external fun nativeSetLogitBias(biasJson: String)
    external fun nativeLoadControlVectors(vectorsJson: String): Boolean
    external fun nativeClearControlVector()

    // ---- Character Engine (llama.cpp engine API) ----

    external fun nativeSetPersonality(paramsJson: String)
    external fun nativeSetMood(mood: Int)
    external fun nativeGetCharacterContext(): String
    external fun nativeSetUncensored(enabled: Boolean)
    external fun nativeGetUncensored(): Boolean
    external fun nativeSupportsThinking(): Boolean

    // ---- Advanced Persona Stubs (not yet backed by native code) ----
    // These are required by ControlVectorManager. They return safe defaults
    // so the app compiles and runs without the advanced personality C++ infra.

    fun nativeComputePersonalityVectors(promptsJson: String, strengthsJson: String, cacheDir: String): Boolean = false
    fun nativeApplyEmotionGatedVectors(personaJson: String, emotionJson: String, cacheDir: String, scale: Float): Boolean = false
    fun nativeProbeAndSetHeadScales(strengthsJson: String, cacheDir: String): String = """{"success":false,"error":"not implemented"}"""
    fun nativeSetHeadScales(scales: FloatArray) {}
    fun nativeResetHeadScales() {}
    fun nativeSetAttentionTemperatureProfile(profileJson: String) {}
    fun nativeResetAttentionTemperatures() {}
    fun nativeSetResidualGates(gatesJson: String) {}
    fun nativeSetResidualGates(attnGates: FloatArray, ffnGates: FloatArray) {}
    fun nativeResetResidualGates() {}
    fun nativeClearAttentionBias() {}
    fun nativeSetNormOffsets(strengthsJson: String, cacheDir: String, scale: Float): String = """{"success":false,"error":"not implemented"}"""
    fun nativeResetNormOffsets() {}
    fun nativeFastWeightInit(dim: Int, gamma: Float, eta: Float, inject: Float): Boolean = false
    fun nativeFastWeightUpdate() {}
    fun nativeFastWeightReset() {}
    fun nativeFastWeightGetState(): String = "{}"
    fun nativeInitSparseMasks(initialValue: Float): Boolean = false
    fun nativeUpdateSparseMasks(text: String, keepRatio: Float, momentum: Float): String = """{"success":false,"error":"not implemented"}"""
    fun nativeResetSparseMasks() {}
    fun nativeInitHypernetworkFromDirections(strengthsJson: String, cacheDir: String, rank: Int, strength: Float): String = """{"success":false,"error":"not implemented"}"""
    fun nativeResetHypernetwork() {}
    fun nativeInitKan(alpha: Float): Boolean = false
    fun nativeResetKan() {}
    fun nativeForwardLearnStep(text: String, learningRate: Float, noiseScale: Float, maxTokens: Int): String = """{"success":false,"error":"not implemented"}"""
    fun nativeSetCaptureEnabled(enabled: Boolean) {}
    fun nativeProbeEmotionAxes(cacheDir: String): String = """{"success":false,"error":"not implemented"}"""
    fun nativeSaveInterventionState(path: String): Boolean = false
    fun nativeLoadInterventionState(path: String): Boolean = false
    fun nativeLayerCount(): Int = 0

    // ---- KV Cache State ----

    external fun nativeGetStateSize(): Long
    external fun nativeStateSaveToFile(path: String): Boolean
    external fun nativeStateLoadFromFile(path: String): Boolean

    // ---- Embedding ----

    external fun nativeLoadEmbeddingModel(path: String, nThreads: Int, nCtx: Int): Boolean
    external fun nativeEncodeText(text: String, normalize: Boolean, callback: EmbeddingCallback): Boolean
    external fun nativeReleaseEmbeddingModel()

    companion object {
        init {
            System.loadLibrary("gguf_jni")
        }
    }
}
