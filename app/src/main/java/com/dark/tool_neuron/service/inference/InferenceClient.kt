package com.dark.tool_neuron.service.inference

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import com.dark.tool_neuron.service.IGenerationCallback
import com.dark.tool_neuron.service.IInferenceService
import com.dark.tool_neuron.service.IModelLoadCallback
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

sealed class InferenceEvent {
    data class Token(val text: String) : InferenceEvent()
    data class ToolCall(val name: String, val argsJson: String) : InferenceEvent()
    data object Done : InferenceEvent()
    data class Error(val message: String) : InferenceEvent()
    data class Metrics(val metricsJson: String) : InferenceEvent()
    data class Progress(val progress: Float) : InferenceEvent()
}

sealed class ServiceState {
    data object Disconnected : ServiceState()
    data object Connecting : ServiceState()
    data object Connected : ServiceState()
    data class Crashed(val message: String) : ServiceState()
}

object InferenceClient {

    private const val TAG = "InferenceClient"
    private const val BIND_TIMEOUT_MS = 15_000L
    private const val REBIND_DELAY_MS = 1000L

    private val _service = MutableStateFlow<IInferenceService?>(null)
    private val _state = MutableStateFlow<ServiceState>(ServiceState.Disconnected)
    val state: StateFlow<ServiceState> = _state.asStateFlow()

    private val _isModelLoaded = MutableStateFlow(false)
    val isModelLoaded: StateFlow<Boolean> = _isModelLoaded.asStateFlow()

    private val _isTtsLoaded = MutableStateFlow(false)
    val isTtsLoaded: StateFlow<Boolean> = _isTtsLoaded.asStateFlow()

    private val _isSttLoaded = MutableStateFlow(false)
    val isSttLoaded: StateFlow<Boolean> = _isSttLoaded.asStateFlow()

    private val _isSdModelLoaded = MutableStateFlow(false)
    val isSdModelLoaded: StateFlow<Boolean> = _isSdModelLoaded.asStateFlow()

    private var appContext: Context? = null
    @Volatile private var isBinding = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = IInferenceService.Stub.asInterface(binder)
            _service.value = svc
            _state.value = ServiceState.Connected
            isBinding = false
            try { _isTtsLoaded.value = svc.isTtsLoaded } catch (_: Exception) {}
            try { _isSttLoaded.value = svc.isSttLoaded } catch (_: Exception) {}
            Log.d(TAG, "Service connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            _service.value = null
            _isModelLoaded.value = false
            _isTtsLoaded.value = false
            _isSttLoaded.value = false
            _isSdModelLoaded.value = false
            _state.value = ServiceState.Crashed("Service process died")
            isBinding = false
            Log.w(TAG, "Service disconnected — will auto-rebind")
            rebind()
        }

        override fun onBindingDied(name: ComponentName?) {
            _service.value = null
            _isModelLoaded.value = false
            _isTtsLoaded.value = false
            _isSttLoaded.value = false
            _isSdModelLoaded.value = false
            _state.value = ServiceState.Crashed("Service binding died")
            isBinding = false
            Log.e(TAG, "Binding died — rebinding")
            rebind()
        }

        override fun onNullBinding(name: ComponentName?) {
            _state.value = ServiceState.Crashed("Service returned null binding")
            isBinding = false
        }
    }

    fun bind(context: Context) {
        appContext = context.applicationContext
        performBind()
    }

    fun unbind() {
        val ctx = appContext ?: return
        try { ctx.unbindService(connection) } catch (_: Exception) {}
        _service.value = null
        _state.value = ServiceState.Disconnected
        isBinding = false
    }

    private fun performBind() {
        val ctx = appContext ?: return
        if (isBinding) return
        synchronized(this) {
            if (isBinding) return
            isBinding = true
            _state.value = ServiceState.Connecting
            val intent = Intent(ctx, InferenceService::class.java)
            ctx.bindService(intent, connection, Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT)
        }
    }

    private fun rebind() {
        val ctx = appContext ?: return
        try { ctx.unbindService(connection) } catch (_: Exception) {}
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            performBind()
        }, REBIND_DELAY_MS)
    }

    private suspend fun ensureBound(): IInferenceService =
        withTimeout(BIND_TIMEOUT_MS) { _service.first { it != null }!! }

    // ── Model lifecycle ──

    suspend fun loadModel(modelPath: String, configJson: String = "{}"): Result<String> {
        val svc = ensureBound()
        return suspendCancellableCoroutine { cont ->
            svc.loadModel(modelPath, configJson, loadCallback(cont))
        }
    }

    suspend fun loadModelFromUri(context: Context, uri: android.net.Uri, configJson: String = "{}"): Result<String> {
        val svc = ensureBound()
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: return Result.failure(IllegalArgumentException("Cannot open URI: $uri"))
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { pfd.close() }
            svc.loadModelFromFd(pfd, configJson, object : IModelLoadCallback.Stub() {
                override fun onSuccess(modelInfoJson: String) {
                    pfd.close()
                    _isModelLoaded.value = true
                    cont.resume(Result.success(modelInfoJson))
                }
                override fun onError(message: String) {
                    pfd.close()
                    _isModelLoaded.value = false
                    cont.resume(Result.failure(RuntimeException(message)))
                }
            })
        }
    }

    suspend fun unloadModel() {
        _isModelLoaded.value = false
        try { ensureBound().unloadModel() } catch (_: Exception) {}
    }

    fun getModelInfo(): String? =
        try { _service.value?.getModelInfo() } catch (_: Exception) { null }

    // ── Generation ──

    fun generateMultiTurn(messagesJson: String, maxTokens: Int): Flow<InferenceEvent> =
        callbackFlow {
            val svc = _service.first { it != null }!!
            val cb = generationCallback { event -> trySend(event) }
            try {
                svc.generateMultiTurn(messagesJson, maxTokens, cb)
            } catch (e: Exception) {
                trySend(InferenceEvent.Error(e.message ?: "Service error"))
                close()
            }
            awaitClose {}
        }.buffer(Channel.UNLIMITED).flowOn(Dispatchers.IO)

    fun generate(prompt: String, maxTokens: Int): Flow<InferenceEvent> =
        callbackFlow {
            val svc = _service.first { it != null }!!
            val cb = generationCallback { event -> trySend(event) }
            try {
                svc.generate(prompt, maxTokens, cb)
            } catch (e: Exception) {
                trySend(InferenceEvent.Error(e.message ?: "Service error"))
                close()
            }
            awaitClose {}
        }.buffer(Channel.UNLIMITED).flowOn(Dispatchers.IO)

    fun stopGeneration() {
        try { _service.value?.stopGeneration() } catch (_: Exception) {}
    }

    // ── Sampling ──

    fun setSampling(samplingJson: String) {
        try { _service.value?.setSampling(samplingJson) } catch (_: Exception) {}
    }

    fun setSystemPrompt(prompt: String) {
        try { _service.value?.setSystemPrompt(prompt) } catch (_: Exception) {}
    }

    fun setChatTemplate(template: String) {
        try { _service.value?.setChatTemplate(template) } catch (_: Exception) {}
    }

    // ── Tool calling ──

    fun isToolCallingSupported(): Boolean =
        try { _service.value?.isToolCallingSupported() ?: false } catch (_: Exception) { false }

    fun setToolsJson(toolsJson: String) {
        try { _service.value?.setToolsJson(toolsJson) } catch (_: Exception) {}
    }

    fun enableToolCalling(toolsJson: String, grammarMode: Int = 1, useTypedGrammar: Boolean = true) {
        try { _service.value?.enableToolCalling(toolsJson, grammarMode, useTypedGrammar) } catch (_: Exception) {}
    }

    fun clearTools() {
        try { _service.value?.clearTools() } catch (_: Exception) {}
    }

    // ── Context ──

    fun getContextUsage(): Float =
        try { _service.value?.contextUsage ?: 0f } catch (_: Exception) { 0f }

    // ── Thinking ──

    fun supportsThinking(): Boolean =
        try { _service.value?.supportsThinking() ?: false } catch (_: Exception) { false }

    fun setThinkingEnabled(enabled: Boolean) {
        try { _service.value?.setThinkingEnabled(enabled) } catch (_: Exception) {}
    }

    // ── Optimizations ──

    fun setPromptCacheDir(path: String) {
        try { _service.value?.setPromptCacheDir(path) } catch (_: Exception) {}
    }

    fun warmUp(): Boolean =
        try { _service.value?.warmUp() ?: false } catch (_: Exception) { false }

    // ── TTS ──

    suspend fun loadTtsModel(configJson: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = ensureBound().loadTtsModel(configJson)
            _isTtsLoaded.value = result
            result
        } catch (e: Exception) {
            Log.e(TAG, "loadTtsModel failed", e)
            false
        }
    }

    suspend fun unloadTtsModel() {
        _isTtsLoaded.value = false
        try { ensureBound().unloadTtsModel() } catch (_: Exception) {}
    }

    suspend fun synthesize(text: String, speakerId: Int = 0, speed: Float = 1.0f): FloatArray? =
        withContext(Dispatchers.IO) {
            try { ensureBound().synthesize(text, speakerId, speed) } catch (e: Exception) {
                Log.e(TAG, "synthesize failed", e)
                null
            }
        }

    fun getTtsSampleRate(): Int =
        try { _service.value?.ttsSampleRate ?: 0 } catch (_: Exception) { 0 }

    // ── STT ──

    suspend fun loadSttModel(configJson: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = ensureBound().loadSttModel(configJson)
            _isSttLoaded.value = result
            result
        } catch (e: Exception) {
            Log.e(TAG, "loadSttModel failed", e)
            false
        }
    }

    suspend fun unloadSttModel() {
        _isSttLoaded.value = false
        try { ensureBound().unloadSttModel() } catch (_: Exception) {}
    }

    suspend fun recognize(samples: FloatArray, sampleRate: Int = 16000): String? =
        withContext(Dispatchers.IO) {
            try { ensureBound().recognize(samples, sampleRate) } catch (e: Exception) {
                Log.e(TAG, "recognize failed", e)
                null
            }
        }

    // ── SD ──

    suspend fun initSdRuntime(configJson: String = "{}"): Boolean = withContext(Dispatchers.IO) {
        try { ensureBound().initSdRuntime(configJson) } catch (e: Exception) {
            Log.e(TAG, "initSdRuntime failed", e)
            false
        }
    }

    suspend fun loadSdModel(configJson: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = ensureBound().loadSdModel(configJson)
            _isSdModelLoaded.value = result
            result
        } catch (e: Exception) {
            Log.e(TAG, "loadSdModel failed", e)
            false
        }
    }

    fun generateSdImage(paramsJson: String): Flow<InferenceEvent> =
        callbackFlow {
            val svc = _service.first { it != null }!!
            val cb = generationCallback { event -> trySend(event) }
            try {
                svc.generateSdImage(paramsJson, cb)
            } catch (e: Exception) {
                trySend(InferenceEvent.Error(e.message ?: "SD error"))
                close()
            }
            awaitClose {}
        }.buffer(Channel.UNLIMITED).flowOn(Dispatchers.IO)

    fun stopSdGeneration() {
        try { _service.value?.stopSdGeneration() } catch (_: Exception) {}
    }

    suspend fun unloadSdModel() {
        _isSdModelLoaded.value = false
        try { ensureBound().unloadSdModel() } catch (_: Exception) {}
    }

    fun isSdModelLoaded(): Boolean =
        try { _service.value?.isSdModelLoaded ?: false } catch (_: Exception) { false }

    fun getSocInfo(): String =
        try { _service.value?.socInfo ?: "{}" } catch (_: Exception) { "{}" }

    // ── Internal helpers ──

    private fun loadCallback(cont: CancellableContinuation<Result<String>>) =
        object : IModelLoadCallback.Stub() {
            override fun onSuccess(modelInfoJson: String) {
                _isModelLoaded.value = true
                cont.resume(Result.success(modelInfoJson))
            }
            override fun onError(message: String) {
                _isModelLoaded.value = false
                cont.resume(Result.failure(RuntimeException(message)))
            }
        }

    private fun generationCallback(emit: (InferenceEvent) -> Unit) =
        object : IGenerationCallback.Stub() {
            override fun onToken(token: String) { emit(InferenceEvent.Token(token)) }
            override fun onToolCall(name: String, argsJson: String) { emit(InferenceEvent.ToolCall(name, argsJson)) }
            override fun onDone() { emit(InferenceEvent.Done) }
            override fun onError(message: String) { emit(InferenceEvent.Error(message)) }
            override fun onMetrics(metricsJson: String) { emit(InferenceEvent.Metrics(metricsJson)) }
            override fun onProgress(progress: Float) { emit(InferenceEvent.Progress(progress)) }
        }
}
