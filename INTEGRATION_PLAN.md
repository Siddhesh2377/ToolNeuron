# Tool-Neuron Integration Plan

## Goal
Replace `ai_gguf-release.aar` with custom llama.cpp native build in ToolNeuron app.

## Strategy
Create drop-in replacement Kotlin classes under `com.mp.ai_gguf` package + new JNI bridge
that directly uses llama.cpp APIs. This means GGUFEngine.kt and all upstream code stays unchanged.

## Files to Create

### 1. Native Build
- `app/src/main/cpp/CMakeLists.txt` — Build llama.cpp + JNI bridge
- `app/src/main/cpp/gguf_jni.cpp` — Comprehensive JNI bridge using llama.cpp directly

### 2. Kotlin Replacement Classes (drop-in for AAR)
- `app/src/main/java/com/mp/ai_gguf/GGUFNativeLib.kt` — JNI wrapper with all native methods
- `app/src/main/java/com/mp/ai_gguf/models/StreamCallback.kt` — Callback interface
- `app/src/main/java/com/mp/ai_gguf/models/DecodingMetrics.kt` — Metrics data class
- `app/src/main/java/com/mp/ai_gguf/toolcalling/GrammarMode.kt` — Grammar mode enum
- `app/src/main/java/com/mp/ai_gguf/toolcalling/ToolCallingConfig.kt` — Config data class

### 3. Build Config Modifications
- `app/build.gradle.kts` — Add externalNativeBuild, remove AAR dependency

## JNI Bridge Methods Required
From GGUFEngine.kt usage analysis:
- nativeLoadModel(path, nCtx, nThreads, flashAttn, cacheTypeK, cacheTypeV): Boolean
- nativeLoadModelFromFd(fd, nCtx, nThreads, flashAttn, cacheTypeK, cacheTypeV): Boolean
- nativeSetSampling(temp, topK, topP, minP, mirostat, mirostatTau, mirostatEta, seed)
- nativeSetSystemPrompt(prompt)
- nativeSetChatTemplate(template)
- nativeGenerateStream(prompt, maxTokens, callback): Boolean
- nativeGenerateStreamMultiTurn(messagesJson, maxTokens, callback): Boolean
- nativeStopGeneration()
- nativeRelease()
- nativeGetModelInfo(): String?
- nativeIsToolCallingSupported(): Boolean
- nativeSetToolsJson(toolsJson)
- nativeSetGrammarMode(mode)
- nativeSetTypedGrammar(enabled)
- nativeUpdateSamplerParams(paramsJson): Boolean
- nativeSetLogitBias(biasJson)
- nativeLoadControlVectors(vectorsJson): Boolean
- nativeClearControlVector()
- nativeGetStateSize(): Long
- nativeStateSaveToFile(path): Boolean
- nativeStateLoadFromFile(path): Boolean

## Execution Order
1. Create cpp/CMakeLists.txt
2. Create cpp/gguf_jni.cpp
3. Create all Kotlin replacement classes
4. Modify app/build.gradle.kts
5. Build and test
