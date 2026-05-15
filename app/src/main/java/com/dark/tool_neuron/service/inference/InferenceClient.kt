package com.dark.tool_neuron.service.inference

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import com.dark.ai_sd.DiffusionBackendState
import com.dark.ai_sd.DiffusionGenerationParams
import com.dark.ai_sd.DiffusionGenerationState
import com.dark.ai_sd.RuntimeSetupState
import com.dark.ai_sd.UpscaleState
import com.dark.gguf_lib.ImageQuality
import com.dark.tool_neuron.service.IDiffusionCallback
import com.dark.tn_security.TnCode
import com.dark.tn_security.TnEvent
import com.dark.tn_security.TnLevel
import com.dark.tn_security.TnModule
import com.dark.tn_security.TnSecurity
import com.dark.tn_security.TnStage
import com.dark.tool_neuron.service.IGenerationCallback
import com.dark.tool_neuron.service.IInferenceService
import com.dark.tool_neuron.service.IModelLoadCallback
import com.dark.tool_neuron.service.ITnEventCallback
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.resume

sealed class InferenceEvent {
    data class Token(val text: String) : InferenceEvent()
    data class ToolCall(val name: String, val argsJson: String) : InferenceEvent()
    data object Done : InferenceEvent()
    data class Error(val message: String) : InferenceEvent()
    data class Metrics(val metricsJson: String) : InferenceEvent()
    data class Progress(val progress: Float) : InferenceEvent()
    data class VlmStageMetrics(val vlmEncodeMs: Float, val vlmDecodeMs: Float, val imageTokens: Int) : InferenceEvent()
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

    private val _contextUsage = MutableStateFlow(0f)
    val contextUsage: StateFlow<Float> = _contextUsage.asStateFlow()

    private val _isTtsLoaded = MutableStateFlow(false)
    val isTtsLoaded: StateFlow<Boolean> = _isTtsLoaded.asStateFlow()

    private val _isSttLoaded = MutableStateFlow(false)
    val isSttLoaded: StateFlow<Boolean> = _isSttLoaded.asStateFlow()

    private val _isVlmLoaded = MutableStateFlow(false)
    val isVlmLoaded: StateFlow<Boolean> = _isVlmLoaded.asStateFlow()

    private val _lastCrashInfo = MutableStateFlow<CrashInfo?>(null)
    val lastCrashInfo: StateFlow<CrashInfo?> = _lastCrashInfo.asStateFlow()

    fun dismissCrashInfo() { _lastCrashInfo.value = null }

    /**
     * Service-side TnEvent stream subscriber. Events arriving here are
     * forwarded into the app-process TnSecurity (so HxsSink can persist them
     * and the UI can observe them via TnSecurity.events) and, for backward
     * compatibility with the existing crash dialog, populate _lastCrashInfo
     * for error-level + crash events.
     */
    private val tnCallback = object : ITnEventCallback.Stub() {
        override fun onEvent(
            kind: Int, level: Int, module: Int, code: Int, stage: Int,
            tag: String?, opId: String?, file: String?, line: Int, func: String?,
            message: String?, suggestion: String?, timestampMs: Long, tid: Int,
        ) {
            val tnMod = TnModule.fromInt(module)
            val ev: TnEvent = when (kind) {
                1 -> TnEvent.Error(
                    timestampMs = timestampMs, module = tnMod, opId = opId, tid = tid,
                    code = TnCode.fromInt(code), stage = TnStage.fromInt(stage),
                    file = file, line = line, func = func,
                    message = message ?: "", suggestion = suggestion,
                )
                2 -> TnEvent.Cancellation(
                    timestampMs = timestampMs, module = tnMod, opId = opId, tid = tid,
                    reason = message,
                )
                else -> TnEvent.Log(
                    timestampMs = timestampMs, module = tnMod, opId = opId, tid = tid,
                    level = TnLevel.fromInt(level), tag = tag,
                    file = file, line = line, func = func,
                    message = message ?: "",
                )
            }

            // Forward into app-side TnSecurity so all sinks (HxsSink, etc.) see it.
            forwardToAppSinks(ev)

            // Back-compat: surface errors + crashes in the existing crash dialog.
            if (ev is TnEvent.Error) {
                _lastCrashInfo.value = CrashInfo.fromTnEvent(ev)
            }
        }

        override fun onCrashReplay(crashJson: String?, crashFilePath: String?) {
            val info = CrashInfo.fromCrashReplay(crashJson ?: "{}", crashFilePath ?: "")
            if (info != null) _lastCrashInfo.value = info
        }
    }

    private fun forwardToAppSinks(ev: TnEvent) {
        // Re-emit through app-side TnSecurity. Since the app process runs its
        // own TnSecurity (with its own sink list — HxsSink, LogcatSink, etc.),
        // we just call the emission helpers which fan out to those sinks.
        when (ev) {
            is TnEvent.Log -> TnSecurity.log(
                level = ev.level, module = ev.module, message = ev.message,
                tag = ev.tag, opId = ev.opId, file = ev.file, line = ev.line, func = ev.func,
            )
            is TnEvent.Error -> TnSecurity.error(
                code = ev.code, stage = ev.stage, module = ev.module, message = ev.message,
                suggestion = ev.suggestion, opId = ev.opId, file = ev.file, line = ev.line, func = ev.func,
            )
            is TnEvent.Cancellation -> TnSecurity.cancel(
                module = ev.module, opId = ev.opId, reason = ev.reason,
            )
            is TnEvent.Crash -> { /* delivered separately via onCrashReplay */ }
        }
    }

    private val _sdBackendState = MutableStateFlow<DiffusionBackendState>(DiffusionBackendState.Idle)
    val sdBackendState: StateFlow<DiffusionBackendState> = _sdBackendState.asStateFlow()

    private val _sdGenerationState = MutableStateFlow<DiffusionGenerationState>(DiffusionGenerationState.Idle)
    val sdGenerationState: StateFlow<DiffusionGenerationState> = _sdGenerationState.asStateFlow()

    private val _sdIsGenerating = MutableStateFlow(false)
    val sdIsGenerating: StateFlow<Boolean> = _sdIsGenerating.asStateFlow()

    private val _sdUpscaleState = MutableStateFlow<UpscaleState>(UpscaleState.Idle)
    val sdUpscaleState: StateFlow<UpscaleState> = _sdUpscaleState.asStateFlow()

    private val _sdRuntimeSetupState = MutableStateFlow<RuntimeSetupState>(RuntimeSetupState.Idle)
    val sdRuntimeSetupState: StateFlow<RuntimeSetupState> = _sdRuntimeSetupState.asStateFlow()

    private val sdCallback = object : IDiffusionCallback.Stub() {
        override fun onBackendState(kind: Int, errorMessage: String?) {
            _sdBackendState.value = when (kind) {
                0 -> DiffusionBackendState.Idle
                1 -> DiffusionBackendState.Starting
                2 -> DiffusionBackendState.Running
                3 -> DiffusionBackendState.Error(errorMessage ?: "Unknown SD backend error")
                else -> DiffusionBackendState.Idle
            }
        }

        override fun onGenerationState(
            kind: Int, progress: Float, currentStep: Int, totalSteps: Int,
            intermediate: Bitmap?, complete: Bitmap?,
            seed: Long, width: Int, height: Int, errorMessage: String?,
        ) {
            _sdGenerationState.value = when (kind) {
                0 -> DiffusionGenerationState.Idle
                1 -> DiffusionGenerationState.Progress(progress, currentStep, totalSteps, intermediate)
                2 -> if (complete != null) DiffusionGenerationState.Complete(
                    bitmap = complete,
                    seed = if (seed == 0L) null else seed,
                    width = width, height = height,
                ) else DiffusionGenerationState.Idle
                3 -> DiffusionGenerationState.Error(errorMessage ?: "Generation failed")
                else -> DiffusionGenerationState.Idle
            }
        }

        override fun onIsGenerating(generating: Boolean) {
            _sdIsGenerating.value = generating
        }

        override fun onUpscaleState(
            kind: Int, complete: Bitmap?, width: Int, height: Int, timeMs: Int, errorMessage: String?,
        ) {
            _sdUpscaleState.value = when (kind) {
                0 -> UpscaleState.Idle
                1 -> UpscaleState.Processing
                2 -> if (complete != null) UpscaleState.Complete(complete, width, height, timeMs)
                     else UpscaleState.Idle
                3 -> UpscaleState.Error(errorMessage ?: "Upscale failed")
                else -> UpscaleState.Idle
            }
        }

        override fun onRuntimeSetupState(
            kind: Int, progressBytes: Long, totalBytes: Long, detail: String?, errorMessage: String?,
        ) {
            _sdRuntimeSetupState.value = when (kind) {
                0 -> RuntimeSetupState.Idle
                1 -> RuntimeSetupState.Downloading(progressBytes, totalBytes, detail ?: "")
                2 -> RuntimeSetupState.CopyingAsset(progressBytes, totalBytes)
                3 -> RuntimeSetupState.Extracting(progressBytes.toInt(), detail ?: "")
                4 -> RuntimeSetupState.CopyingSafetyChecker
                5 -> RuntimeSetupState.InitializingRuntime
                6 -> RuntimeSetupState.Complete
                7 -> RuntimeSetupState.Error(errorMessage ?: "Runtime setup failed")
                else -> RuntimeSetupState.Idle
            }
        }
    }

    /** Kept as a no-op for now — events now arrive automatically via [tnCallback]. */
    @Suppress("unused")
    fun reportLiveError() {
        // Intentionally empty. Pre-tn_security callers polled the service for
        // an opaque last-error JSON; with tn_security every error is pushed
        // through tnCallback the moment it's emitted.
    }

    private var appContext: Context? = null
    @Volatile private var isBinding = false

    private val pendingLoads = mutableSetOf<CancellableContinuation<Result<String>>>()
    private val pendingLoadsLock = Any()

    private fun trackLoad(
        cont: CancellableContinuation<Result<String>>,
        onCancel: (() -> Unit)? = null,
    ) {
        synchronized(pendingLoadsLock) { pendingLoads.add(cont) }
        // CancellableContinuation accepts exactly one cancellation handler — additional
        // resources (e.g. the pfd in loadModelFromUri) must be closed here, not via
        // a second invokeOnCancellation call (which throws IllegalStateException).
        cont.invokeOnCancellation {
            try { onCancel?.invoke() } catch (_: Throwable) {}
            synchronized(pendingLoadsLock) { pendingLoads.remove(cont) }
        }
    }

    private fun untrackLoad(cont: CancellableContinuation<Result<String>>) {
        synchronized(pendingLoadsLock) { pendingLoads.remove(cont) }
    }

    private fun failPendingLoads(reason: String) {
        val toFail = synchronized(pendingLoadsLock) {
            val snapshot = pendingLoads.toList()
            pendingLoads.clear()
            snapshot
        }
        toFail.forEach { cont ->
            if (cont.isActive) cont.resume(Result.failure(RuntimeException(reason)))
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = IInferenceService.Stub.asInterface(binder)
            _service.value = svc
            _state.value = ServiceState.Connected
            isBinding = false
            // Always sync the four "is loaded" flags from the freshly-bound
            // service. Without the isModelLoaded probe here, a low-memory
            // service kill that didn't fire onServiceDisconnected in time
            // would leave the pill stuck on "Model Loaded" while the new
            // :inference process actually has nothing loaded.
            val modelLoadedOnSvc = try { svc.isModelLoaded } catch (_: Exception) { false }
            if (_isModelLoaded.value != modelLoadedOnSvc) {
                Log.i(TAG, "rebind: model state drift, client=${_isModelLoaded.value} svc=$modelLoadedOnSvc — taking svc as truth")
            }
            _isModelLoaded.value = modelLoadedOnSvc
            try { _isTtsLoaded.value = svc.isTtsLoaded } catch (_: Exception) {}
            try { _isSttLoaded.value = svc.isSttLoaded } catch (_: Exception) {}
            try { _isVlmLoaded.value = svc.isVlmLoaded } catch (_: Exception) {}
            try {
                svc.registerTnEvents(tnCallback)
                svc.replayPendingCrashes()
            } catch (e: Throwable) {
                Log.e(TAG, "tn_security stream registration failed", e)
            }
            try {
                svc.sdRegisterEvents(sdCallback)
            } catch (e: Throwable) {
                Log.e(TAG, "SD event registration failed", e)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            _service.value = null
            _isModelLoaded.value = false
            _isTtsLoaded.value = false
            _isSttLoaded.value = false
            _isVlmLoaded.value = false
            _state.value = ServiceState.Crashed("Service process died")
            isBinding = false
            failPendingLoads("Inference service crashed during model load")
            Log.w(TAG, "Service disconnected — will auto-rebind")
            rebind()
        }

        override fun onBindingDied(name: ComponentName?) {
            _service.value = null
            _isModelLoaded.value = false
            _isTtsLoaded.value = false
            _isSttLoaded.value = false
            _isVlmLoaded.value = false
            _state.value = ServiceState.Crashed("Service binding died")
            isBinding = false
            failPendingLoads("Inference service binding died")
            Log.e(TAG, "Binding died — rebinding")
            rebind()
        }

        override fun onNullBinding(name: ComponentName?) {
            _service.value = null
            _isModelLoaded.value = false
            _isTtsLoaded.value = false
            _isSttLoaded.value = false
            _isVlmLoaded.value = false
            _state.value = ServiceState.Crashed("Service returned null binding")
            isBinding = false
            failPendingLoads("Inference service returned null binding")
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
        // Match the disconnect callbacks: unbinding the service means nothing
        // is loaded from the client's perspective anymore.
        _isModelLoaded.value = false
        _isTtsLoaded.value = false
        _isSttLoaded.value = false
        _isVlmLoaded.value = false
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
            ctx.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun rebind() {
        val ctx = appContext ?: return
        try { ctx.unbindService(connection) } catch (_: Exception) {}
        Handler(Looper.getMainLooper()).postDelayed({
            performBind()
        }, REBIND_DELAY_MS)
    }

    private suspend fun ensureBound(): IInferenceService =
        withTimeout(BIND_TIMEOUT_MS) { _service.first { it != null }!! }

    suspend fun loadModel(modelPath: String, configJson: String = "{}"): Result<String> {
        val svc = ensureBound()
        return suspendCancellableCoroutine { cont ->
            trackLoad(cont)
            svc.loadModel(modelPath, configJson, loadCallback(cont))
        }
    }

    suspend fun loadModelFromUri(context: Context, uri: Uri, configJson: String = "{}"): Result<String> {
        val svc = ensureBound()
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: return Result.failure(IllegalArgumentException("Cannot open URI: $uri"))
        return suspendCancellableCoroutine { cont ->
            trackLoad(cont) { pfd.close() }
            svc.loadModelFromFd(pfd, configJson, object : IModelLoadCallback.Stub() {
                override fun onSuccess(modelInfoJson: String) {
                    pfd.close()
                    untrackLoad(cont)
                    _isModelLoaded.value = true
                    cont.resume(Result.success(modelInfoJson))
                }
                override fun onError(message: String) {
                    pfd.close()
                    untrackLoad(cont)
                    _isModelLoaded.value = false
                    reportLiveError()
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

    fun generateMultiTurn(messagesJson: String, maxTokens: Int): Flow<InferenceEvent> =
        callbackFlow {
            val svc = withTimeoutOrNull(BIND_TIMEOUT_MS) { _service.first { it != null } }
            if (svc == null) {
                trySend(InferenceEvent.Error("Inference service unavailable"))
                close()
                return@callbackFlow
            }
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
            val svc = withTimeoutOrNull(BIND_TIMEOUT_MS) { _service.first { it != null } }
            if (svc == null) {
                trySend(InferenceEvent.Error("Inference service unavailable"))
                close()
                return@callbackFlow
            }
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

    /**
     * Drives GGMLEngine.compact on the :inference side and forwards summary
     * tokens / metrics / done events through the same channel as generate.
     * Caller is expected to replace the persisted chat history with the
     * collected summary after [InferenceEvent.Done].
     */
    fun compactConversation(messagesJson: String, maxTokens: Int): Flow<InferenceEvent> =
        callbackFlow {
            val svc = withTimeoutOrNull(BIND_TIMEOUT_MS) { _service.first { it != null } }
            if (svc == null) {
                trySend(InferenceEvent.Error("Inference service unavailable"))
                close()
                return@callbackFlow
            }
            val cb = generationCallback { event -> trySend(event) }
            try {
                svc.compactConversation(messagesJson, maxTokens, cb)
            } catch (e: Exception) {
                trySend(InferenceEvent.Error(e.message ?: "Service error"))
                close()
            }
            awaitClose {}
        }.buffer(Channel.UNLIMITED).flowOn(Dispatchers.IO)

    suspend fun loadVlmProjector(
        path: String,
        threads: Int = 2,
        imageMinTokens: Int = -1,
        imageMaxTokens: Int = -1,
    ): Boolean = withContext(Dispatchers.IO) {
        val svc = ensureBound()
        try {
            val ok = svc.loadVlmProjector(path, threads, imageMinTokens, imageMaxTokens)
            _isVlmLoaded.value = ok
            ok
        } catch (e: Exception) {
            Log.e(TAG, "loadVlmProjector failed", e)
            false
        }
    }

    suspend fun releaseVlmProjector() {
        _isVlmLoaded.value = false
        try { ensureBound().releaseVlmProjector() } catch (_: Exception) {}
    }

    fun getVlmDefaultMarker(): String =
        try { _service.value?.vlmDefaultMarker.orEmpty() } catch (_: Exception) { "" }

    fun getVlmInfo(): String? =
        try { _service.value?.getVlmInfo() } catch (_: Exception) { null }

    fun generateVlm(
        context: Context,
        messagesJson: String,
        imageUris: List<Uri>,
        maxTokens: Int,
        imageQuality: ImageQuality = ImageQuality.MEDIUM,
    ): Flow<InferenceEvent> = callbackFlow {
        val svc = withTimeoutOrNull(BIND_TIMEOUT_MS) { _service.first { it != null } }
        if (svc == null) {
            trySend(InferenceEvent.Error("Inference service unavailable"))
            close()
            return@callbackFlow
        }
        val pfds: Array<ParcelFileDescriptor> = try {
            imageUris.map { uri ->
                context.contentResolver.openFileDescriptor(uri, "r")
                    ?: throw IllegalArgumentException("Cannot open image URI: $uri")
            }.toTypedArray()
        } catch (e: Exception) {
            trySend(InferenceEvent.Error(e.message ?: "Cannot open image URI"))
            close()
            return@callbackFlow
        }
        val cb = generationCallback { event -> trySend(event) }
        try {
            svc.generateVlm(messagesJson, pfds, maxTokens, imageQuality.nativeValue, cb)
        } catch (e: Exception) {
            trySend(InferenceEvent.Error(e.message ?: "Service error"))
            close()
        } finally {
            pfds.forEach { p -> try { p.close() } catch (_: Exception) {} }
        }
        awaitClose {}
    }.buffer(Channel.UNLIMITED).flowOn(Dispatchers.IO)

    suspend fun precomputeVision(
        context: Context,
        imageUri: Uri,
        imageQuality: ImageQuality,
    ): Boolean = withContext(Dispatchers.IO) {
        val svc = withTimeoutOrNull(BIND_TIMEOUT_MS) { _service.first { it != null } } ?: return@withContext false
        val pfd = try {
            context.contentResolver.openFileDescriptor(imageUri, "r")
        } catch (e: Exception) {
            Log.e(TAG, "precomputeVision: open uri failed", e)
            null
        } ?: return@withContext false
        try {
            svc.precomputeVlmVision(pfd, imageQuality.nativeValue)
        } catch (e: Exception) {
            Log.e(TAG, "precomputeVision failed", e)
            false
        } finally {
            try { pfd.close() } catch (_: Exception) {}
        }
    }

    fun setSampling(samplingJson: String) {
        try { _service.value?.setSampling(samplingJson) } catch (_: Exception) {}
    }

    fun setSystemPrompt(prompt: String) {
        try { _service.value?.setSystemPrompt(prompt) } catch (_: Exception) {}
    }

    fun setChatTemplate(template: String) {
        try { _service.value?.setChatTemplate(template) } catch (_: Exception) {}
    }

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

    fun getContextUsage(): Float =
        try { _service.value?.contextUsage ?: 0f } catch (_: Exception) { 0f }

    fun setThreadMode(mode: Int) {
        try { _service.value?.setThreadMode(mode) } catch (_: Exception) {}
    }

    fun getMemoryStatsJson(): String? =
        try { _service.value?.memoryStatsJson } catch (_: Exception) { null }

    fun getVtCacheStatsJson(): String? =
        try { _service.value?.vtCacheStatsJson } catch (_: Exception) { null }

    fun getVlmKvCacheStatsJson(): String? =
        try { _service.value?.vlmKvCacheStatsJson } catch (_: Exception) { null }

    fun supportsThinking(): Boolean =
        try { _service.value?.supportsThinking() ?: false } catch (_: Exception) { false }

    fun setThinkingEnabled(enabled: Boolean) {
        try { _service.value?.setThinkingEnabled(enabled) } catch (_: Exception) {}
    }

    fun setPromptCacheDir(path: String) {
        try { _service.value?.setPromptCacheDir(path) } catch (_: Exception) {}
    }

    fun warmUp(): Boolean =
        try { _service.value?.warmUp() ?: false } catch (_: Exception) { false }

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
            val byteSize = samples.size * 4
            if (byteSize < 800_000) {
                try { return@withContext ensureBound().recognize(samples, sampleRate) }
                catch (e: Exception) {
                    Log.e(TAG, "recognize failed", e)
                    return@withContext null
                }
            }
            val pipe = ParcelFileDescriptor.createPipe()
            val readEnd = pipe[0]
            val writeEnd = pipe[1]
            val writer = launch(Dispatchers.IO) {
                try {
                    FileOutputStream(writeEnd.fileDescriptor).use { out ->
                        val bb = ByteBuffer.allocate(samples.size * 4)
                            .order(ByteOrder.nativeOrder())
                        bb.asFloatBuffer().put(samples)
                        out.write(bb.array())
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "recognize FD write failed", e)
                } finally {
                    runCatching { writeEnd.close() }
                }
            }
            try {
                ensureBound().recognizeFromFd(readEnd, samples.size, sampleRate)
            } catch (e: Exception) {
                Log.e(TAG, "recognize failed", e)
                null
            } finally {
                runCatching { readEnd.close() }
                writer.join()
            }
        }

    data class RagHit(
        val text: String,
        val docId: String,
        val chunkIndex: Int,
        val score: Float,
    )

    suspend fun ragEnsureReady(
        threads: Int = 0,
        chunkSize: Int = 256,
        chunkOverlap: Int = 32,
        dims: Int = 256,
        topK: Int = 64,
        topN: Int = 5,
        lateChunking: Boolean = true,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            ensureBound().ragEnsureReady(threads, chunkSize, chunkOverlap, dims, topK, topN, lateChunking)
        } catch (e: Throwable) {
            Log.e(TAG, "ragEnsureReady failed", e)
            false
        }
    }

    suspend fun ragLoadEmbeddingModel(path: String): Boolean = withContext(Dispatchers.IO) {
        try { ensureBound().ragLoadEmbeddingModel(path) }
        catch (e: Throwable) { Log.e(TAG, "ragLoadEmbeddingModel failed", e); false }
    }

    suspend fun ragIsLoaded(): Boolean = withContext(Dispatchers.IO) {
        try { ensureBound().ragIsLoaded() } catch (_: Throwable) { false }
    }

    suspend fun ragIngestBytes(
        bytes: ByteArray, mimeHint: String?, nameHint: String?, docId: String,
    ): Int = withContext(Dispatchers.IO) {
        val svc = try { ensureBound() } catch (e: Throwable) {
            Log.e(TAG, "ragIngestBytes: bind failed", e); return@withContext -5
        }
        streamBytesToService(bytes) { pfd, size ->
            svc.ragIngestBytes(pfd, size, mimeHint, nameHint, docId)
        } ?: -5
    }

    suspend fun ragExtractText(
        bytes: ByteArray, mimeHint: String?, nameHint: String?,
    ): String? = withContext(Dispatchers.IO) {
        val svc = try { ensureBound() } catch (e: Throwable) {
            Log.e(TAG, "ragExtractText: bind failed", e); return@withContext null
        }
        streamBytesToService(bytes) { pfd, size ->
            svc.ragExtractText(pfd, size, mimeHint, nameHint)
        }
    }

    suspend fun ragQuery(query: String): List<RagHit> = withContext(Dispatchers.IO) {
        try { parseHits(ensureBound().ragQuery(query)) }
        catch (e: Throwable) { Log.e(TAG, "ragQuery failed", e); emptyList() }
    }

    suspend fun ragQueryFiltered(query: String, docIdPrefix: String): List<RagHit> = withContext(Dispatchers.IO) {
        try { parseHits(ensureBound().ragQueryFiltered(query, docIdPrefix)) }
        catch (e: Throwable) { Log.e(TAG, "ragQueryFiltered failed", e); emptyList() }
    }

    suspend fun ragRemoveDocument(docId: String): Int = withContext(Dispatchers.IO) {
        try { ensureBound().ragRemoveDocument(docId) } catch (_: Throwable) { -1 }
    }

    suspend fun ragInfo(): String? = withContext(Dispatchers.IO) {
        try { ensureBound().ragInfo() } catch (_: Throwable) { null }
    }

    suspend fun ragDocumentCount(): Int = withContext(Dispatchers.IO) {
        try { ensureBound().ragDocumentCount() } catch (_: Throwable) { 0 }
    }

    suspend fun ragChunkCount(): Int = withContext(Dispatchers.IO) {
        try { ensureBound().ragChunkCount() } catch (_: Throwable) { 0 }
    }

    suspend fun ragExportIndex(): ByteArray? = withContext(Dispatchers.IO) {
        val svc = try { ensureBound() } catch (e: Throwable) {
            Log.e(TAG, "ragExportIndex: bind failed", e); return@withContext null
        }
        val pipe = try { ParcelFileDescriptor.createPipe() }
        catch (e: Throwable) { Log.e(TAG, "createPipe failed", e); return@withContext null }
        val readEnd = pipe[0]
        val writeEnd = pipe[1]
        val resultBuf = java.io.ByteArrayOutputStream()
        val reader = launch(Dispatchers.IO) {
            try {
                ParcelFileDescriptor.AutoCloseInputStream(readEnd).use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        resultBuf.write(buf, 0, n)
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "ragExportIndex: read pipe failed", e)
            }
        }
        val written = try {
            svc.ragExportIndex(writeEnd)
        } catch (e: Throwable) {
            Log.e(TAG, "ragExportIndex: call failed", e); -1
        } finally {
            try { writeEnd.close() } catch (_: Throwable) {}
        }
        reader.join()
        if (written <= 0) null else resultBuf.toByteArray().also {
            if (it.size != written) Log.w(TAG, "ragExportIndex: declared=$written read=${it.size}")
        }
    }

    suspend fun ragImportIndex(bytes: ByteArray): Int = withContext(Dispatchers.IO) {
        val svc = try { ensureBound() } catch (e: Throwable) {
            Log.e(TAG, "ragImportIndex: bind failed", e); return@withContext -5
        }
        streamBytesToService(bytes) { pfd, size -> svc.ragImportIndex(pfd, size) } ?: -5
    }

    suspend fun ragRelease() = withContext(Dispatchers.IO) {
        try { ensureBound().ragRelease() } catch (_: Throwable) {}
    }

    suspend fun sdEnsureRuntime(runtimeDir: String, tarXzSourcePath: String): Boolean = withContext(Dispatchers.IO) {
        try { ensureBound().sdEnsureRuntime(runtimeDir, tarXzSourcePath) }
        catch (e: Throwable) { Log.e(TAG, "sdEnsureRuntime failed", e); false }
    }

    suspend fun sdLoadDiffusionModel(
        name: String, modelDir: String,
        textEmbeddingSize: Int, runOnCpu: Boolean, useCpuClip: Boolean,
        isPony: Boolean, safetyMode: Boolean,
        width: Int, height: Int,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            ensureBound().sdLoadDiffusionModel(
                name, modelDir, textEmbeddingSize, runOnCpu, useCpuClip,
                isPony, safetyMode, width, height,
            )
        } catch (e: Throwable) { Log.e(TAG, "sdLoadDiffusionModel failed", e); false }
    }

    suspend fun sdLoadUpscaler(modelPath: String, useMnn: Boolean, useOpenCL: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            try { ensureBound().sdLoadUpscaler(modelPath, useMnn, useOpenCL) }
            catch (e: Throwable) { Log.e(TAG, "sdLoadUpscaler failed", e); false }
        }

    fun sdGenerate(params: DiffusionGenerationParams) {
        // Fire-and-forget: progress streams over IDiffusionCallback into sdGenerationState.
        try {
            _service.value?.sdGenerate(
                params.prompt,
                params.negativePrompt,
                params.steps,
                params.cfgScale,
                params.seed ?: 0L,
                params.seed != null,
                params.width,
                params.height,
                params.scheduler,
                params.useOpenCL,
                params.inputImage,
                params.mask,
                params.denoiseStrength,
                params.showDiffusionProcess,
                params.showDiffusionStride,
            )
        } catch (e: Throwable) {
            Log.e(TAG, "sdGenerate failed", e)
            _sdGenerationState.value = DiffusionGenerationState.Error(e.message ?: "Service error")
        }
    }

    fun sdCancelGeneration() {
        try { _service.value?.sdCancelGeneration() } catch (_: Throwable) {}
    }

    fun sdResetGenerationState() {
        try { _service.value?.sdResetGenerationState() } catch (_: Throwable) {}
    }

    fun sdUpscale(bitmap: Bitmap) {
        try { _service.value?.sdUpscale(bitmap) } catch (e: Throwable) {
            Log.e(TAG, "sdUpscale failed", e)
            _sdUpscaleState.value = UpscaleState.Error(e.message ?: "Service error")
        }
    }

    fun sdStop() {
        try { _service.value?.sdStop() } catch (_: Throwable) {}
    }

    fun sdCleanup() {
        try { _service.value?.sdCleanup() } catch (_: Throwable) {}
    }

    fun sdIsBackendRunning(): Boolean =
        try { _service.value?.sdIsBackendRunning() ?: false } catch (_: Throwable) { false }

    suspend fun sdGetSupportedResolutions(modelDir: String, baseW: Int, baseH: Int): List<Pair<Int, Int>> =
        withContext(Dispatchers.IO) {
            val json = try { ensureBound().sdGetSupportedResolutions(modelDir, baseW, baseH) }
            catch (_: Throwable) { "[]" } ?: "[]"
            try {
                val arr = org.json.JSONArray(json)
                (0 until arr.length()).map { i ->
                    val pair = arr.getJSONArray(i)
                    pair.getInt(0) to pair.getInt(1)
                }
            } catch (_: Throwable) { emptyList() }
        }

    /**
     * Streams [bytes] into the service over a one-shot pipe. The write side runs
     * on its own IO coroutine so the synchronous binder call can read it without
     * deadlock when the payload exceeds the pipe buffer.
     *
     * Must be called from inside a coroutine (uses [kotlinx.coroutines.coroutineScope]
     * to launch the writer); returns null on failure.
     */
    private suspend inline fun <T> streamBytesToService(
        bytes: ByteArray,
        crossinline call: (ParcelFileDescriptor, Long) -> T,
    ): T? {
        if (bytes.isEmpty()) return null
        val pipe = try { ParcelFileDescriptor.createPipe() }
        catch (e: Throwable) { Log.e(TAG, "createPipe failed", e); return null }
        val readEnd = pipe[0]
        val writeEnd = pipe[1]
        return kotlinx.coroutines.coroutineScope {
            val writer = launch(Dispatchers.IO) {
                try {
                    FileOutputStream(writeEnd.fileDescriptor).use { it.write(bytes) }
                } catch (e: Throwable) {
                    Log.e(TAG, "pipe write failed", e)
                } finally {
                    try { writeEnd.close() } catch (_: Throwable) {}
                }
            }
            try {
                call(readEnd, bytes.size.toLong())
            } catch (e: Throwable) {
                Log.e(TAG, "service call over pipe failed", e); null
            } finally {
                try { readEnd.close() } catch (_: Throwable) {}
                writer.join()
            }
        }
    }

    private fun parseHits(jsonStr: String?): List<RagHit> {
        if (jsonStr.isNullOrEmpty()) return emptyList()
        return try {
            val arr = org.json.JSONArray(jsonStr)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                RagHit(
                    text = obj.getString("text"),
                    docId = obj.getString("doc_id"),
                    chunkIndex = obj.getInt("chunk_index"),
                    score = obj.getDouble("score").toFloat(),
                )
            }
        } catch (_: Throwable) { emptyList() }
    }

    private fun loadCallback(cont: CancellableContinuation<Result<String>>) =
        object : IModelLoadCallback.Stub() {
            override fun onSuccess(modelInfoJson: String) {
                untrackLoad(cont)
                _isModelLoaded.value = true
                if (cont.isActive) cont.resume(Result.success(modelInfoJson))
            }
            override fun onError(message: String) {
                untrackLoad(cont)
                _isModelLoaded.value = false
                reportLiveError()
                if (cont.isActive) cont.resume(Result.failure(RuntimeException(message)))
            }
        }

    private fun generationCallback(emit: (InferenceEvent) -> Unit) =
        object : IGenerationCallback.Stub() {
            override fun onToken(token: String) {
                emit(InferenceEvent.Token(token))
                _contextUsage.value = getContextUsage()
            }
            override fun onToolCall(name: String, argsJson: String) { emit(InferenceEvent.ToolCall(name, argsJson)) }
            override fun onDone() {
                emit(InferenceEvent.Done)
                _contextUsage.value = getContextUsage()
            }
            override fun onError(message: String) {
                emit(InferenceEvent.Error(message))
                reportLiveError()
            }
            override fun onMetrics(metricsJson: String) { emit(InferenceEvent.Metrics(metricsJson)) }
            override fun onProgress(progress: Float) {
                emit(InferenceEvent.Progress(progress))
                _contextUsage.value = getContextUsage()
            }
            override fun onVlmStageMetrics(vlmEncodeMs: Float, vlmDecodeMs: Float, imageTokens: Int) {
                emit(InferenceEvent.VlmStageMetrics(vlmEncodeMs, vlmDecodeMs, imageTokens))
            }
        }
}
