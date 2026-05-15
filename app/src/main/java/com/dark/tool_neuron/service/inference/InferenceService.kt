package com.dark.tool_neuron.service.inference

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.DeadObjectException
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import com.dark.ai_sherpa.OfflineModelConfig
import com.dark.ai_sherpa.OfflineRecognizer
import com.dark.ai_sherpa.OfflineRecognizerConfig
import com.dark.ai_sherpa.OfflineTts
import com.dark.ai_sherpa.OfflineTtsConfig
import com.dark.ai_sherpa.OfflineTtsModelConfig
import com.dark.ai_sherpa.OfflineTtsVitsModelConfig
import com.dark.ai_sherpa.OfflineWhisperModelConfig
import android.graphics.Bitmap
import com.dark.ai_sd.DiffusionBackendState
import com.dark.ai_sd.DiffusionGenerationParams
import com.dark.ai_sd.DiffusionGenerationState
import com.dark.ai_sd.DiffusionModelConfig
import com.dark.ai_sd.DiffusionRuntimeConfig
import com.dark.ai_sd.RuntimeSetupState
import com.dark.ai_sd.StableDiffusionManager
import com.dark.ai_sd.UpscaleState
import com.dark.gguf_lib.GGMLEngine
import com.dark.gguf_lib.ImageQuality
import com.dark.gguf_lib.RAGEngine
import com.dark.gguf_lib.models.GenerationEvent
import com.dark.tool_neuron.service.IDiffusionCallback
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import org.json.JSONArray
import com.dark.tn_security.LogcatSink
import com.dark.tn_security.TnCode
import com.dark.tn_security.TnEvent
import com.dark.tn_security.TnModule
import com.dark.tn_security.TnSecurity
import com.dark.tn_security.TnSink
import com.dark.tn_security.TnStage
import java.io.File
import android.os.RemoteCallbackList
import com.dark.tool_neuron.R
import com.dark.tool_neuron.service.IGenerationCallback
import com.dark.tool_neuron.service.IInferenceService
import com.dark.tool_neuron.service.IModelLoadCallback
import com.dark.tool_neuron.service.ITnEventCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

class InferenceService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val engine = GGMLEngine()
    private val ragEngine = RAGEngine()
    private val ragLock = Any()
    private val ttsLock = Any()
    private val sttLock = Any()
    private var tts: OfflineTts? = null
    private var stt: OfflineRecognizer? = null

    private val crashDir: File by lazy {
        File(filesDir, "tn_security/crashes").also { it.mkdirs() }
    }

    private val tnClients = RemoteCallbackList<ITnEventCallback>()
    private val sdClients = RemoteCallbackList<IDiffusionCallback>()
    private var sdForwardingJobs: List<Job> = emptyList()
    private val sdLock = Any()
    private val sdBroadcastLock = Any()
    @Volatile private var sdInitialized = false
    private val sdManager: StableDiffusionManager by lazy {
        StableDiffusionManager.getInstance(applicationContext)
    }

    private val tnSink = TnSink { ev ->
        val n = tnClients.beginBroadcast()
        try {
            for (i in 0 until n) {
                val cb = tnClients.getBroadcastItem(i)
                try {
                    when (ev) {
                        is TnEvent.Log -> cb.onEvent(
                            0, ev.level.value, ev.module.value, 0, 0,
                            ev.tag, ev.opId, ev.file, ev.line, ev.func,
                            ev.message, null, ev.timestampMs, ev.tid,
                        )
                        is TnEvent.Error -> cb.onEvent(
                            1, 4 /*ERROR*/, ev.module.value, ev.code.value, ev.stage.value,
                            null, ev.opId, ev.file, ev.line, ev.func,
                            ev.message, ev.suggestion, ev.timestampMs, ev.tid,
                        )
                        is TnEvent.Cancellation -> cb.onEvent(
                            2, 2 /*INFO*/, ev.module.value, TnCode.CANCELLED.value, 0,
                            null, ev.opId, null, 0, null,
                            ev.reason ?: "", null, ev.timestampMs, ev.tid,
                        )
                        is TnEvent.Crash -> cb.onCrashReplay(
                            // Re-emit as a synthetic crash JSON for the app side.
                            // Real crash files are shipped via replayPendingCrashes().
                            "{}", ev.crashFilePath,
                        )
                    }
                } catch (_: DeadObjectException) {
                    // Will be reaped by RemoteCallbackList automatically.
                } catch (_: Throwable) {
                    // Per-callback failure must not kill the broadcast loop.
                }
            }
        } finally {
            tnClients.finishBroadcast()
        }
    }

    @Volatile private var loadedProjectorPath: String? = null
    @Volatile private var loadedImageMaxTokens: Int = -1

    private val vtCacheDir: File by lazy {
        File(filesDir, "vlm_vt_cache").also { it.mkdirs() }
    }

    override fun onCreate() {
        super.onCreate()
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        setupNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Inference ready"))
        engine.setTokenBatchSize(STREAMING_TOKEN_BATCH_BYTES)
        try {
            TnSecurity.init(
                context = this,
                module = TnModule.TN_SERVICE,
                crashDir = crashDir,
                installSignalHandlers = true,
            )
            TnSecurity.addSink(LogcatSink())  // dev visibility
            TnSecurity.addSink(tnSink)        // broadcast to bound clients
        } catch (t: Throwable) {
            Log.e(TAG, "tn_security init failed", t)
        }
        try {
            engine.vtCacheInit(vtCacheDir.absolutePath, VT_CACHE_BUDGET_BYTES)
        } catch (t: Throwable) {
            Log.e(TAG, "vt cache init failed", t)
        }
    }

    private fun startSdForwarding() {
        synchronized(sdLock) {
            if (sdForwardingJobs.isNotEmpty()) return
            // Tail every SD StateFlow so registered callbacks see live updates.
            sdForwardingJobs = listOf(
                scope.launch {
                    sdManager.diffusionBackendState.collect { state ->
                        broadcastBackend(state)
                    }
                },
                scope.launch {
                    sdManager.diffusionGenerationState.collect { state ->
                        broadcastGeneration(state)
                    }
                },
                scope.launch {
                    sdManager.isGenerating.collect { gen ->
                        broadcastIsGenerating(gen)
                    }
                },
                scope.launch {
                    sdManager.upscaleState.collect { state ->
                        broadcastUpscale(state)
                    }
                },
                scope.launch {
                    sdManager.runtimeSetupState.collect { state ->
                        broadcastRuntimeSetup(state)
                    }
                },
            )
        }
    }

    private fun fanoutSd(block: (IDiffusionCallback) -> Unit) {
        synchronized(sdBroadcastLock) {
            val n = sdClients.beginBroadcast()
            try {
                for (i in 0 until n) {
                    try { block(sdClients.getBroadcastItem(i)) } catch (_: Throwable) {}
                }
            } finally {
                sdClients.finishBroadcast()
            }
        }
    }

    private fun broadcastBackend(state: DiffusionBackendState) = fanoutSd { cb ->
        when (state) {
            is DiffusionBackendState.Idle      -> cb.onBackendState(0, null)
            is DiffusionBackendState.Starting  -> cb.onBackendState(1, null)
            is DiffusionBackendState.Running   -> cb.onBackendState(2, null)
            is DiffusionBackendState.Error     -> cb.onBackendState(3, state.message)
        }
    }

    private fun broadcastGeneration(state: DiffusionGenerationState) = fanoutSd { cb ->
        when (state) {
            is DiffusionGenerationState.Idle ->
                cb.onGenerationState(0, 0f, 0, 0, null, null, 0L, 0, 0, null)
            is DiffusionGenerationState.Progress ->
                cb.onGenerationState(1, state.progress, state.currentStep, state.totalSteps,
                    state.intermediateImage, null, 0L, 0, 0, null)
            is DiffusionGenerationState.Complete ->
                cb.onGenerationState(2, 1f, 0, 0, null, state.bitmap,
                    state.seed ?: 0L, state.width, state.height, null)
            is DiffusionGenerationState.Error ->
                cb.onGenerationState(3, 0f, 0, 0, null, null, 0L, 0, 0, state.message)
        }
    }

    private fun broadcastIsGenerating(generating: Boolean) = fanoutSd { cb ->
        cb.onIsGenerating(generating)
    }

    private fun broadcastUpscale(state: UpscaleState) = fanoutSd { cb ->
        when (state) {
            is UpscaleState.Idle       -> cb.onUpscaleState(0, null, 0, 0, 0, null)
            is UpscaleState.Processing -> cb.onUpscaleState(1, null, 0, 0, 0, null)
            is UpscaleState.Complete   -> cb.onUpscaleState(2, state.bitmap, state.width, state.height, state.timeMs, null)
            is UpscaleState.Error      -> cb.onUpscaleState(3, null, 0, 0, 0, state.message)
        }
    }

    private fun broadcastRuntimeSetup(state: RuntimeSetupState) = fanoutSd { cb ->
        when (state) {
            is RuntimeSetupState.Idle ->
                cb.onRuntimeSetupState(0, 0L, 0L, null, null)
            is RuntimeSetupState.Downloading ->
                cb.onRuntimeSetupState(1, state.bytesDownloaded, state.totalBytes, state.fileName, null)
            is RuntimeSetupState.CopyingAsset ->
                cb.onRuntimeSetupState(2, state.bytesWritten, state.totalBytes, null, null)
            is RuntimeSetupState.Extracting ->
                cb.onRuntimeSetupState(3, state.filesExtracted.toLong(), 0L, state.currentFile, null)
            is RuntimeSetupState.CopyingSafetyChecker ->
                cb.onRuntimeSetupState(4, 0L, 0L, null, null)
            is RuntimeSetupState.InitializingRuntime ->
                cb.onRuntimeSetupState(5, 0L, 0L, null, null)
            is RuntimeSetupState.Complete ->
                cb.onRuntimeSetupState(6, 0L, 0L, null, null)
            is RuntimeSetupState.Error ->
                cb.onRuntimeSetupState(7, 0L, 0L, null, state.message)
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // Hard-stop from the foreground notification: cancel any in-flight
            // work AND tear down every loaded backend before we let the
            // process go down, so memory is reclaimed immediately instead of
            // waiting on onDestroy ordering / lingering binder refs.
            unloadEverything()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        unloadEverything()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        unloadEverything()
        scope.cancel()
        super.onDestroy()
    }

    @Volatile private var teardownInProgress = false

    /**
     * Synchronously release every backend held by this service: cancel any
     * generation, free the LLM + VLM projector, close TTS/STT/RAG, and tear
     * down the SD pipeline. Idempotent — safe to call from onStartCommand,
     * onTaskRemoved, and onDestroy (which often fire in sequence).
     */
    private fun unloadEverything() {
        if (teardownInProgress) return
        teardownInProgress = true
        try { engine.stopGeneration() } catch (_: Throwable) {}
        try {
            runBlocking(Dispatchers.IO) {
                if (engine.isLoaded) engine.unload()
            }
        } catch (_: Throwable) {}
        try { engine.releaseVlmProjector() } catch (_: Throwable) {}
        synchronized(ttsLock) {
            try { tts?.close() } catch (_: Throwable) {}
            tts = null
        }
        synchronized(sttLock) {
            try { stt?.close() } catch (_: Throwable) {}
            stt = null
        }
        synchronized(ragLock) {
            try { ragEngine.close() } catch (_: Throwable) {}
        }
        synchronized(sdLock) {
            sdForwardingJobs.forEach { it.cancel() }
            sdForwardingJobs = emptyList()
            if (sdInitialized) {
                try { sdManager.cleanup() } catch (_: Throwable) {}
                sdInitialized = false
            }
        }
    }

    private val binder = object : IInferenceService.Stub() {

        override fun loadModel(modelPath: String, configJson: String, callback: IModelLoadCallback) {
            scope.launch {
                try {
                    logDeviceProfile()
                    val modelSize = File(modelPath).length()
                    val memAvail = readMemAvailableBytes()
                    Log.i(TAG, "loadModel path=$modelPath sizeMb=${modelSize / (1024 * 1024)} memAvailMb=${memAvail / (1024 * 1024)}")
                    if (memAvail in 1 until (modelSize * 6 / 5)) {
                        val msg = "Not enough free memory: model needs ~${modelSize / (1024 * 1024)} MB, " +
                            "device has ~${memAvail / (1024 * 1024)} MB free. Close other apps or pick a smaller quant."
                        captureKt("loadModel", "path=$modelPath", "InsufficientRam", msg)
                        safeCallback(callback) { it.onError(msg) }
                        return@launch
                    }

                    if (engine.isLoaded) engine.unload()
                    val config = parseConfig(configJson)
                    val success = engine.load(
                        path = modelPath,
                        contextSize = config.optInt("contextSize", 4096),
                        flashAttn = config.optBoolean("flashAttn", true),
                        cacheTypeK = config.optString("cacheTypeK", "q8_0"),
                        cacheTypeV = config.optString("cacheTypeV", "q8_0"),
                    )
                    if (success) {
                        engine.setThreadMode(config.optInt("threadMode", 1))
                        applySamplingFromConfig(config)
                        updateNotification("Model loaded")
                        safeCallback(callback) { it.onSuccess(engine.getModelInfoJson() ?: "{}") }
                    } else {
                        val msg = "Model load returned false"
                        captureKt("loadModel", "path=$modelPath", "ModelLoad", msg)
                        safeCallback(callback) { it.onError(msg) }
                    }
                } catch (e: OutOfMemoryError) {
                    try { engine.unload() } catch (_: Throwable) {}
                    captureKt("loadModel", "path=$modelPath", "OOM",
                        "JVM out of memory while loading the model. Try a smaller quant or lower Context Size.")
                    safeCallback(callback) { it.onError("Out of memory") }
                } catch (e: Exception) {
                    captureKt("loadModel", "path=$modelPath", "ModelLoad", e.message ?: e.javaClass.simpleName)
                    safeCallback(callback) { it.onError(e.message ?: "Unknown error") }
                }
            }
        }

        override fun loadModelFromFd(pfd: ParcelFileDescriptor, configJson: String, callback: IModelLoadCallback) {
            val fd = pfd.detachFd()
            scope.launch {
                try {
                    if (engine.isLoaded) engine.unload()
                    val config = parseConfig(configJson)
                    val success = engine.loadFromFd(
                        fd = fd,
                        contextSize = config.optInt("contextSize", 4096),
                        flashAttn = config.optBoolean("flashAttn", true),
                        cacheTypeK = config.optString("cacheTypeK", "q8_0"),
                        cacheTypeV = config.optString("cacheTypeV", "q8_0"),
                    )
                    if (success) {
                        engine.setThreadMode(config.optInt("threadMode", 1))
                        applySamplingFromConfig(config)
                        updateNotification("Model loaded")
                        safeCallback(callback) { it.onSuccess(engine.getModelInfoJson() ?: "{}") }
                    } else {
                        safeCallback(callback) { it.onError("Failed to load model from FD") }
                    }
                } catch (e: OutOfMemoryError) {
                    try { engine.unload() } catch (_: Throwable) {}
                    safeCallback(callback) { it.onError("Out of memory") }
                } catch (e: Exception) {
                    safeCallback(callback) { it.onError(e.message ?: "Unknown error") }
                }
            }
        }

        override fun unloadModel() {
            scope.launch {
                if (engine.isLoaded) engine.unload()
                updateNotification("Inference ready")
            }
        }

        override fun isModelLoaded(): Boolean = engine.isLoaded

        override fun getModelInfo(): String? = engine.getModelInfoJson()

        override fun generate(prompt: String, maxTokens: Int, callback: IGenerationCallback) {
            collectFlow(engine.generateFlow(prompt, maxTokens), callback)
        }

        override fun generateMultiTurn(messagesJson: String, maxTokens: Int, callback: IGenerationCallback) {
            collectFlow(engine.generateMultiTurnFlow(messagesJson, maxTokens), callback)
        }

        override fun compactConversation(messagesJson: String, maxTokens: Int, callback: IGenerationCallback) {
            // Append the summarize instruction as a synthetic user turn, run
            // the normal multi-turn generate, then wipe the KV cache so the
            // next real turn prefills from scratch against whatever new
            // history the caller installs (typically [system, assistant:
            // summary, user: …]).
            val augmented = try {
                val arr = org.json.JSONArray(messagesJson)
                arr.put(org.json.JSONObject().apply {
                    put("role", "user")
                    put("content", GGMLEngine.DEFAULT_SUMMARIZE_PROMPT)
                })
                arr.toString()
            } catch (e: Exception) {
                try { callback.onError("invalid messages json: ${e.message}") } catch (_: Exception) {}
                return
            }
            collectFlow(
                flow = engine.generateMultiTurnFlow(augmented, maxTokens),
                callback = callback,
                onComplete = { try { engine.resetKvCache() } catch (_: Throwable) {} },
            )
        }

        override fun stopGeneration() {
            engine.stopGeneration()
        }

        override fun setSampling(samplingJson: String) {
            engine.updateSamplerParams(samplingJson)
        }

        override fun setSystemPrompt(prompt: String) {
            engine.setSystemPrompt(prompt)
        }

        override fun setChatTemplate(template: String) {
            engine.setChatTemplate(template)
        }

        override fun isToolCallingSupported(): Boolean = false

        override fun setToolsJson(toolsJson: String) {}

        override fun enableToolCalling(toolsJson: String, grammarMode: Int, useTypedGrammar: Boolean) {}

        override fun clearTools() {}

        override fun getContextUsage(): Float = engine.getContextUsage()

        override fun getContextInfo(prompt: String?): String? = null

        override fun getMemoryStatsJson(): String? = engine.getMemoryStatsJson()

        override fun getVtCacheStatsJson(): String = engine.vtCacheStatsJson()

        override fun getVlmKvCacheStatsJson(): String = engine.vlmKvCacheStatsJson()

        override fun supportsThinking(): Boolean =
            engine.isLoaded && engine.supportsThinking()

        override fun setThinkingEnabled(enabled: Boolean) {
            engine.setThinkingEnabled(enabled)
        }

        override fun setPromptCacheDir(path: String) {
            engine.setPromptCacheDir(path)
        }

        override fun warmUp(): Boolean = engine.warmUp()

        override fun setThreadMode(mode: Int) {
            engine.setThreadMode(mode)
        }

        override fun getStateSize(): Long = engine.getStateSize()

        override fun stateSaveToFile(path: String): Boolean = engine.stateSaveToFile(path)

        override fun stateLoadFromFile(path: String): Boolean = engine.stateLoadFromFile(path)

        override fun loadVlmProjector(path: String, threads: Int, imageMinTokens: Int, imageMaxTokens: Int): Boolean {
            val ok = runBlocking(Dispatchers.IO) {
                engine.loadVlmProjector(path, threads, imageMinTokens, imageMaxTokens)
            }
            if (ok) {
                loadedProjectorPath = path
                loadedImageMaxTokens = imageMaxTokens
            }
            return ok
        }

        override fun loadVlmProjectorFromFd(pfd: ParcelFileDescriptor, threads: Int, imageMinTokens: Int, imageMaxTokens: Int): Boolean {
            val fd = pfd.detachFd()
            val ok = runBlocking(Dispatchers.IO) {
                engine.loadVlmProjectorFromFd(fd, threads, imageMinTokens, imageMaxTokens)
            }
            if (ok) {
                loadedProjectorPath = "fd:$fd"
                loadedImageMaxTokens = imageMaxTokens
            }
            return ok
        }

        override fun releaseVlmProjector() {
            engine.releaseVlmProjector()
            loadedProjectorPath = null
            loadedImageMaxTokens = -1
        }

        override fun isVlmLoaded(): Boolean = engine.isVlmLoaded

        override fun getVlmInfo(): String? = engine.getVlmInfoJson()

        override fun getVlmDefaultMarker(): String = engine.getVlmDefaultMarker()

        override fun generateVlm(
            messagesJson: String,
            imageFds: Array<ParcelFileDescriptor>?,
            maxTokens: Int,
            imageQuality: Int,
            callback: IGenerationCallback,
        ) {
            val images: List<ByteArray> = try {
                imageFds
                    ?.map { pfd ->
                        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
                    }
                    ?: emptyList()
            } catch (e: Exception) {
                captureKt("generateVlm", "pfdRead", "VLM", e.message ?: e.javaClass.simpleName)
                safeCallback(callback) { it.onError("Failed to read image bytes: ${e.message}") }
                return
            }
            val quality = ImageQuality.entries.getOrNull(imageQuality) ?: ImageQuality.MEDIUM
            val projector = loadedProjectorPath
            val maxImageTokens = loadedImageMaxTokens
            val vtKeys: List<ByteArray?>? = if (projector != null) {
                images.map { engine.computeVtKey(it, projector, maxImageTokens) }
            } else null
            collectFlow(
                engine.generateVlmFlow(
                    messagesJson = messagesJson,
                    imageData = images,
                    maxTokens = maxTokens,
                    vtKeys = vtKeys,
                    imageQuality = quality,
                ),
                callback,
            )
        }

        override fun precomputeVlmVision(pfd: ParcelFileDescriptor, imageQuality: Int): Boolean {
            val projector = loadedProjectorPath ?: return false
            val maxImageTokens = loadedImageMaxTokens
            val bytes = try {
                ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
            } catch (e: Exception) {
                Log.e(TAG, "precomputeVlmVision: read failed", e)
                return false
            }
            val quality = ImageQuality.entries.getOrNull(imageQuality) ?: ImageQuality.MEDIUM
            return runBlocking(Dispatchers.IO) {
                try {
                    engine.precomputeVisionEmbeddings(bytes, projector, maxImageTokens, quality)
                } catch (e: Exception) {
                    Log.e(TAG, "precomputeVlmVision: encode failed", e)
                    false
                }
            }
        }

        override fun loadTtsModel(configJson: String): Boolean {
            return synchronized(ttsLock) {
                try {
                    tts?.close()
                    val cfg = parseConfig(configJson)
                    val ttsConfig = OfflineTtsConfig(
                        model = OfflineTtsModelConfig(
                            vits = OfflineTtsVitsModelConfig(
                                model = cfg.getString("model"),
                                tokens = cfg.getString("tokens"),
                                dataDir = cfg.optString("dataDir", "")
                            ),
                            numThreads = cfg.optInt("numThreads", 2)
                        )
                    )
                    tts = OfflineTts.fromFile(ttsConfig)
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load TTS model", e)
                    captureKt("loadTtsModel", configJson.take(200), "TTS",
                        e.message ?: e.javaClass.simpleName)
                    tts = null
                    false
                }
            }
        }

        override fun unloadTtsModel() {
            synchronized(ttsLock) {
                tts?.close()
                tts = null
            }
        }

        override fun isTtsLoaded(): Boolean = synchronized(ttsLock) { tts != null }

        override fun synthesize(text: String, speakerId: Int, speed: Float): FloatArray? {
            return synchronized(ttsLock) {
                try {
                    val audio = tts?.generate(text, sid = speakerId, speed = speed)
                    audio?.samples
                } catch (e: Exception) {
                    Log.e(TAG, "TTS synthesis failed", e)
                    captureKt("synthesize", "speakerId=$speakerId speed=$speed", "TTS",
                        e.message ?: e.javaClass.simpleName)
                    null
                }
            }
        }

        override fun getTtsSampleRate(): Int = synchronized(ttsLock) { tts?.sampleRate ?: 0 }

        override fun loadSttModel(configJson: String): Boolean {
            return synchronized(sttLock) {
                try {
                    stt?.close()
                    val cfg = parseConfig(configJson)
                    val type = cfg.optString("type", "whisper")
                    val recognizerConfig = when (type) {
                        "whisper" -> OfflineRecognizerConfig(
                            modelConfig = OfflineModelConfig(
                                whisper = OfflineWhisperModelConfig(
                                    encoder = cfg.getString("encoder"),
                                    decoder = cfg.getString("decoder")
                                ),
                                tokens = cfg.getString("tokens"),
                                numThreads = cfg.optInt("numThreads", 2)
                            )
                        )
                        else -> throw IllegalArgumentException("Unsupported STT type: $type")
                    }
                    stt = OfflineRecognizer.fromFile(recognizerConfig)
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load STT model", e)
                    captureKt("loadSttModel", configJson.take(200), "STT",
                        e.message ?: e.javaClass.simpleName)
                    stt = null
                    false
                }
            }
        }

        override fun unloadSttModel() {
            synchronized(sttLock) {
                stt?.close()
                stt = null
            }
        }

        override fun isSttLoaded(): Boolean = synchronized(sttLock) { stt != null }

        override fun recognize(samples: FloatArray, sampleRate: Int): String? {
            return runRecognize(samples, sampleRate)
        }

        override fun recognizeFromFd(pfd: ParcelFileDescriptor?, sampleCount: Int, sampleRate: Int): String? {
            if (pfd == null || sampleCount <= 0) return null
            val samples = FloatArray(sampleCount)
            try {
                ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
                    val byteBuf = ByteArray(8192)
                    val totalBytes = sampleCount * 4
                    var read = 0
                    val tmp = ByteArray(totalBytes)
                    while (read < totalBytes) {
                        val n = input.read(byteBuf)
                        if (n <= 0) break
                        val copy = minOf(n, totalBytes - read)
                        System.arraycopy(byteBuf, 0, tmp, read, copy)
                        read += copy
                    }
                    val bb = ByteBuffer.wrap(tmp).order(ByteOrder.nativeOrder())
                    val fb = bb.asFloatBuffer()
                    fb.get(samples, 0, minOf(sampleCount, fb.remaining()))
                }
            } catch (e: Exception) {
                Log.e(TAG, "recognizeFromFd FD read failed", e)
                captureKt("recognizeFromFd", "sampleCount=$sampleCount", "STT", e.message ?: e.javaClass.simpleName)
                return null
            }
            return runRecognize(samples, sampleRate)
        }

        private fun runRecognize(samples: FloatArray, sampleRate: Int): String? {
            return synchronized(sttLock) {
                val recognizer = stt ?: return@synchronized null
                try {
                    val stream = recognizer.createStream()
                    stream.acceptWaveform(sampleRate = sampleRate, samples = samples)
                    recognizer.decode(stream)
                    val result = recognizer.getResult(stream).text
                    stream.close()
                    result
                } catch (e: Exception) {
                    Log.e(TAG, "STT recognition failed", e)
                    captureKt("recognize", "sampleRate=$sampleRate", "STT", e.message ?: e.javaClass.simpleName)
                    null
                }
            }
        }

        override fun registerTnEvents(cb: ITnEventCallback?) {
            if (cb != null) tnClients.register(cb)
        }

        override fun unregisterTnEvents(cb: ITnEventCallback?) {
            if (cb != null) tnClients.unregister(cb)
        }

        override fun ragEnsureReady(
            threads: Int, chunkSize: Int, chunkOverlap: Int,
            dims: Int, topK: Int, topN: Int, lateChunking: Boolean,
        ): Boolean = synchronized(ragLock) {
            try {
                if (ragEngine.isCreated) return@synchronized true
                ragEngine.create(
                    threads = threads,
                    chunkSize = chunkSize,
                    chunkOverlap = chunkOverlap,
                    dims = dims,
                    topK = topK,
                    topN = topN,
                    lateChunking = lateChunking,
                )
            } catch (e: Throwable) {
                captureKt("ragEnsureReady", null, "RAG", e.message ?: e.javaClass.simpleName)
                false
            }
        }

        override fun ragLoadEmbeddingModel(path: String): Boolean = synchronized(ragLock) {
            try {
                runBlocking(Dispatchers.IO) { ragEngine.loadModel(path) }
            } catch (e: Throwable) {
                captureKt("ragLoadEmbeddingModel", "path=$path", "RAG", e.message ?: e.javaClass.simpleName)
                false
            }
        }

        override fun ragLoadEmbeddingModelFromFd(pfd: ParcelFileDescriptor?): Boolean {
            if (pfd == null) return false
            val fd = pfd.detachFd()
            return synchronized(ragLock) {
                try {
                    runBlocking(Dispatchers.IO) { ragEngine.loadModelFromFd(fd) }
                } catch (e: Throwable) {
                    captureKt("ragLoadEmbeddingModelFromFd", "fd=$fd", "RAG", e.message ?: e.javaClass.simpleName)
                    false
                }
            }
        }

        override fun ragUnloadEmbeddingModel() {
            synchronized(ragLock) {
                try { ragEngine.close() } catch (_: Throwable) {}
            }
        }

        override fun ragIsLoaded(): Boolean = synchronized(ragLock) {
            try { ragEngine.isModelLoaded } catch (_: Throwable) { false }
        }

        override fun ragIngestBytes(
            pfd: ParcelFileDescriptor?, size: Long,
            mimeHint: String?, nameHint: String?, docId: String,
        ): Int {
            if (pfd == null) return -2
            val bytes = try {
                readAllFromPfd(pfd, size)
            } catch (e: Throwable) {
                captureKt("ragIngestBytes", "docId=$docId size=$size", "RAG", e.message ?: e.javaClass.simpleName)
                return -2
            }
            return synchronized(ragLock) {
                try {
                    runBlocking(Dispatchers.IO) {
                        ragEngine.ingestBytes(bytes, mimeHint, nameHint, docId)
                    }
                } catch (e: Throwable) {
                    captureKt("ragIngestBytes", "docId=$docId", "RAG", e.message ?: e.javaClass.simpleName)
                    -5
                }
            }
        }

        override fun ragExtractText(
            pfd: ParcelFileDescriptor?, size: Long,
            mimeHint: String?, nameHint: String?,
        ): String? {
            if (pfd == null) return null
            val bytes = try {
                readAllFromPfd(pfd, size)
            } catch (e: Throwable) {
                captureKt("ragExtractText", "size=$size", "RAG", e.message ?: e.javaClass.simpleName)
                return null
            }
            return try {
                runBlocking(Dispatchers.IO) {
                    ragEngine.extractText(bytes, mimeHint, nameHint)
                }
            } catch (e: Throwable) {
                captureKt("ragExtractText", null, "RAG", e.message ?: e.javaClass.simpleName)
                null
            }
        }

        override fun ragQuery(query: String): String = synchronized(ragLock) {
            try {
                val hits = runBlocking(Dispatchers.IO) { ragEngine.query(query) }
                serializeHits(hits)
            } catch (e: Throwable) {
                captureKt("ragQuery", "q=${query.take(60)}", "RAG", e.message ?: e.javaClass.simpleName)
                "[]"
            }
        }

        override fun ragQueryFiltered(query: String, docIdPrefix: String): String = synchronized(ragLock) {
            try {
                val hits = runBlocking(Dispatchers.IO) { ragEngine.queryFiltered(query, docIdPrefix) }
                serializeHits(hits)
            } catch (e: Throwable) {
                captureKt("ragQueryFiltered", "prefix=$docIdPrefix", "RAG", e.message ?: e.javaClass.simpleName)
                "[]"
            }
        }

        override fun ragRemoveDocument(docId: String): Int = synchronized(ragLock) {
            try { ragEngine.removeDocument(docId) } catch (_: Throwable) { -1 }
        }

        override fun ragClear() {
            synchronized(ragLock) { try { ragEngine.clear() } catch (_: Throwable) {} }
        }

        override fun ragInfo(): String? = synchronized(ragLock) {
            try { ragEngine.info() } catch (_: Throwable) { null }
        }

        override fun ragDocumentCount(): Int = synchronized(ragLock) {
            try { ragEngine.documentCount } catch (_: Throwable) { 0 }
        }

        override fun ragChunkCount(): Int = synchronized(ragLock) {
            try { ragEngine.chunkCount } catch (_: Throwable) { 0 }
        }

        override fun ragExportIndex(pfd: ParcelFileDescriptor?): Int {
            if (pfd == null) return -1
            val bytes = synchronized(ragLock) {
                try { ragEngine.exportIndex() } catch (_: Throwable) { null }
            } ?: run { try { pfd.close() } catch (_: Throwable) {}; return -1 }
            return try {
                ParcelFileDescriptor.AutoCloseOutputStream(pfd).use { out ->
                    out.write(bytes)
                }
                bytes.size
            } catch (e: Throwable) {
                captureKt("ragExportIndex", "bytes=${bytes.size}", "RAG", e.message ?: e.javaClass.simpleName)
                -1
            }
        }

        override fun ragImportIndex(pfd: ParcelFileDescriptor?, size: Long): Int {
            if (pfd == null) return -5
            val bytes = try {
                readAllFromPfd(pfd, size)
            } catch (e: Throwable) {
                captureKt("ragImportIndex", "size=$size", "RAG", e.message ?: e.javaClass.simpleName)
                return -5
            }
            return synchronized(ragLock) {
                try { ragEngine.importIndex(bytes) } catch (e: Throwable) {
                    captureKt("ragImportIndex", "bytes=${bytes.size}", "RAG", e.message ?: e.javaClass.simpleName)
                    -5
                }
            }
        }

        override fun ragRelease() {
            synchronized(ragLock) {
                try { ragEngine.close() } catch (_: Throwable) {}
            }
        }

        override fun sdEnsureRuntime(runtimeDir: String, tarXzSourcePath: String): Boolean {
            return try {
                startSdForwarding()
                runBlocking(Dispatchers.IO) {
                    sdManager.initialize(
                        DiffusionRuntimeConfig(
                            runtimeDir = runtimeDir,
                            tarXzSourcePath = tarXzSourcePath,
                        )
                    )
                }
                sdInitialized = true
                true
            } catch (e: Throwable) {
                captureKt("sdEnsureRuntime", "tar=$tarXzSourcePath", "SD", e.message ?: e.javaClass.simpleName)
                false
            }
        }

        override fun sdLoadDiffusionModel(
            name: String, modelDir: String,
            textEmbeddingSize: Int, runOnCpu: Boolean, useCpuClip: Boolean,
            isPony: Boolean, safetyMode: Boolean,
            width: Int, height: Int,
        ): Boolean {
            return try {
                sdManager.loadModel(
                    DiffusionModelConfig(
                        name = name,
                        modelDir = modelDir,
                        textEmbeddingSize = textEmbeddingSize,
                        runOnCpu = runOnCpu,
                        useCpuClip = useCpuClip,
                        isPony = isPony,
                        safetyMode = safetyMode,
                    ),
                    width = width, height = height,
                )
            } catch (e: Throwable) {
                captureKt("sdLoadDiffusionModel", "model=$name dir=$modelDir", "SD", e.message ?: e.javaClass.simpleName)
                false
            }
        }

        override fun sdLoadUpscaler(modelPath: String, useMnn: Boolean, useOpenCL: Boolean): Boolean {
            return try {
                sdManager.loadUpscaler(modelPath, useMnn = useMnn, useOpenCL = useOpenCL)
            } catch (e: Throwable) {
                captureKt("sdLoadUpscaler", "path=$modelPath", "SD", e.message ?: e.javaClass.simpleName)
                false
            }
        }

        override fun sdGenerate(
            prompt: String, negativePrompt: String, steps: Int, cfgScale: Float,
            seed: Long, hasSeed: Boolean,
            width: Int, height: Int, scheduler: String, useOpenCL: Boolean,
            inputImage: String?, mask: String?, denoiseStrength: Float,
            showDiffusionProcess: Boolean, showDiffusionStride: Int,
        ) {
            try {
                sdManager.generateImage(
                    DiffusionGenerationParams(
                        prompt = prompt,
                        negativePrompt = negativePrompt,
                        steps = steps,
                        cfgScale = cfgScale,
                        seed = if (hasSeed) seed else null,
                        width = width,
                        height = height,
                        scheduler = scheduler,
                        useOpenCL = useOpenCL,
                        inputImage = inputImage,
                        mask = mask,
                        denoiseStrength = denoiseStrength,
                        showDiffusionProcess = showDiffusionProcess,
                        showDiffusionStride = showDiffusionStride,
                    )
                )
            } catch (e: Throwable) {
                captureKt("sdGenerate", "prompt=${prompt.take(60)}", "SD", e.message ?: e.javaClass.simpleName)
            }
        }

        override fun sdCancelGeneration() {
            try { sdManager.cancelGeneration() } catch (_: Throwable) {}
        }

        override fun sdResetGenerationState() {
            try { sdManager.resetGenerationState() } catch (_: Throwable) {}
        }

        override fun sdUpscale(bitmap: Bitmap?) {
            if (bitmap == null) return
            try { sdManager.upscaleImage(bitmap) } catch (e: Throwable) {
                captureKt("sdUpscale", "size=${bitmap.width}x${bitmap.height}", "SD", e.message ?: e.javaClass.simpleName)
            }
        }

        override fun sdStop() {
            try { sdManager.stopBackend() } catch (_: Throwable) {}
        }

        override fun sdCleanup() {
            try { sdManager.cleanup() } catch (_: Throwable) {}
        }

        override fun sdIsBackendRunning(): Boolean =
            try { sdManager.isBackendRunning() } catch (_: Throwable) { false }

        override fun sdGetSupportedResolutions(modelDir: String, baseW: Int, baseH: Int): String {
            return try {
                val list = sdManager.getSupportedResolutions(modelDir, baseW, baseH)
                val arr = JSONArray()
                for ((w, h) in list) arr.put(JSONArray().put(w).put(h))
                arr.toString()
            } catch (_: Throwable) { "[]" }
        }

        override fun sdRegisterEvents(cb: IDiffusionCallback?) {
            if (cb == null) return
            sdClients.register(cb)
            startSdForwarding()
            // Re-emit current values so a late subscriber sees state immediately.
            try {
                cb.onBackendState(
                    when (val s = sdManager.diffusionBackendState.value) {
                        is DiffusionBackendState.Idle -> 0
                        is DiffusionBackendState.Starting -> 1
                        is DiffusionBackendState.Running -> 2
                        is DiffusionBackendState.Error -> 3
                    },
                    (sdManager.diffusionBackendState.value as? DiffusionBackendState.Error)?.message,
                )
                cb.onIsGenerating(sdManager.isGenerating.value)
            } catch (_: Throwable) {}
        }

        override fun sdUnregisterEvents(cb: IDiffusionCallback?) {
            if (cb != null) sdClients.unregister(cb)
        }

        override fun replayPendingCrashes() {
            try {
                val replayed = TnSecurity.drainCrashFiles(crashDir)
                for (crash in replayed) {
                    val json = try {
                        // Re-read the file would be cleaner but drain already
                        // deleted it. Build a minimal envelope from the
                        // already-materialised TnEvent.Crash so the client
                        // can render it consistently.
                        JSONObject().apply {
                            put("signal", crash.signal)
                            put("signal_name", crash.signalName)
                            put("timestamp_ms", crash.timestampMs)
                            put("pid", crash.pid)
                            put("tid", crash.tid)
                            put("module", crash.module.value)
                            put("module_slug", crash.module.slug)
                            put("fault_addr", crash.faultAddr ?: "")
                        }.toString()
                    } catch (_: Throwable) { "{}" }
                    val n = tnClients.beginBroadcast()
                    try {
                        for (i in 0 until n) {
                            try {
                                tnClients.getBroadcastItem(i)
                                    .onCrashReplay(json, crash.crashFilePath)
                            } catch (_: Throwable) {}
                        }
                    } finally { tnClients.finishBroadcast() }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "replayPendingCrashes failed", e)
            }
        }

    }

    private fun collectFlow(
        flow: Flow<GenerationEvent>,
        callback: IGenerationCallback,
        onComplete: () -> Unit = {},
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                flow.collect { event ->
                    try {
                        when (event) {
                            is GenerationEvent.Token -> callback.onToken(event.text)
                            is GenerationEvent.Done -> callback.onDone()
                            is GenerationEvent.Error -> callback.onError(event.message)
                            is GenerationEvent.Metrics -> {
                                val m = event.metrics
                                val json = JSONObject().apply {
                                    put("tokensPerSecond", m.tokensPerSecond)
                                    put("timeToFirstTokenMs", m.timeToFirstTokenMs)
                                    put("totalTimeMs", m.totalTimeMs)
                                    put("tokensEvaluated", m.tokensEvaluated)
                                    put("tokensPredicted", m.tokensPredicted)
                                    put("modelSizeMB", m.modelSizeMB)
                                    put("contextSizeMB", m.contextSizeMB)
                                    put("peakMemoryMB", m.peakMemoryMB)
                                    put("memoryUsagePercent", m.memoryUsagePercent)
                                }
                                callback.onMetrics(json.toString())
                            }
                            is GenerationEvent.Progress -> callback.onProgress(event.progress)
                            is GenerationEvent.VlmStageMetrics ->
                                callback.onVlmStageMetrics(event.vlmEncodeMs, event.vlmDecodeMs, event.imageTokens)
                            is GenerationEvent.VlmKvCacheStatus -> Unit
                            is GenerationEvent.VtCacheStatus -> Unit
                        }
                    } catch (_: DeadObjectException) {
                        engine.stopGeneration()
                        return@collect
                    }
                }
            } catch (_: DeadObjectException) {
                engine.stopGeneration()
            } catch (e: Exception) {
                try { callback.onError(e.message ?: "Unknown error") } catch (_: Exception) {}
            } finally {
                try { onComplete() } catch (_: Throwable) {}
            }
        }
    }

    private fun parseConfig(json: String): JSONObject =
        if (json.isBlank()) JSONObject() else try { JSONObject(json) } catch (_: Exception) { JSONObject() }

    private fun applySamplingFromConfig(config: JSONObject) {
        if (config.has("temperature") || config.has("topK") || config.has("topP")) {
            engine.setSampling(
                temperature = config.optDouble("temperature", 0.7).toFloat(),
                topK = config.optInt("topK", 40),
                topP = config.optDouble("topP", 0.9).toFloat(),
                minP = config.optDouble("minP", 0.05).toFloat(),
                mirostat = config.optInt("mirostat", 0),
                mirostatTau = config.optDouble("mirostatTau", 5.0).toFloat(),
                mirostatEta = config.optDouble("mirostatEta", 0.1).toFloat(),
                seed = config.optInt("seed", -1),
            )
        }

        val advanced = JSONObject()
        for (key in ADVANCED_SAMPLING_KEYS) {
            if (config.has(key)) advanced.put(key, config.get(key))
        }
        if (advanced.length() > 0) engine.updateSamplerParams(advanced.toString())

        val nWindow = config.optInt("kvWindow", 0)
        if (nWindow > 0) {
            engine.setKvPolicy(
                nSink = config.optInt("kvSink", 4),
                nWindow = nWindow,
                evictAtFull = config.optBoolean("kvEvictAtFull", true),
            )
        }

        val system = config.optString("systemPrompt", "")
        if (system.isNotEmpty()) engine.setSystemPrompt(system)
        val template = config.optString("chatTemplate", "")
        if (template.isNotEmpty()) engine.setChatTemplate(template)
    }

    private inline fun <T> safeCallback(target: T, block: (T) -> Unit) {
        try { block(target) } catch (_: DeadObjectException) {}
    }

    /**
     * Compat shim — translates the old captureKt() signature into a structured
     * TnSecurity.error call. Keeps existing call sites compiling while every
     * error flows through the unified event pipeline.
     */
    private fun captureKt(op: String, detail: String?, category: String, message: String) {
        val (code, stage) = mapCategoryToTn(category)
        TnSecurity.withOp(op) {
            TnSecurity.error(
                code = code,
                stage = stage,
                module = TnModule.TN_SERVICE,
                message = if (detail.isNullOrBlank()) message else "$message (detail: $detail)",
            )
        }
    }

    private fun mapCategoryToTn(category: String): Pair<TnCode, TnStage> = when (category) {
        "InsufficientRam" -> TnCode.OOM             to TnStage.INIT
        "OOM"             -> TnCode.OOM             to TnStage.LOAD
        "ModelLoad"       -> TnCode.MODEL_LOAD_FAIL to TnStage.LOAD
        "TTS"             -> TnCode.DECODE_FAIL     to TnStage.TTS_GENERATE
        "STT"             -> TnCode.DECODE_FAIL     to TnStage.STT_DECODE
        "VLM"             -> TnCode.DECODE_FAIL     to TnStage.VLM_DECODE_IMG
        "RAG"             -> TnCode.DECODE_FAIL     to TnStage.RAG_INGEST
        "SD"              -> TnCode.DECODE_FAIL     to TnStage.SD_UNET
        else              -> TnCode.UNKNOWN         to TnStage.UNSPECIFIED
    }

    private fun readAllFromPfd(pfd: ParcelFileDescriptor, hintedSize: Long): ByteArray {
        val capacity = if (hintedSize in 1..(64L * 1024L * 1024L)) hintedSize.toInt() else 0
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
            if (capacity > 0) {
                val buf = ByteArray(capacity)
                var read = 0
                while (read < capacity) {
                    val n = input.read(buf, read, capacity - read)
                    if (n <= 0) break
                    read += n
                }
                if (read == capacity) buf else buf.copyOf(read)
            } else {
                input.readBytes()
            }
        }
    }

    private fun serializeHits(hits: List<com.dark.gguf_lib.models.RAGResult>): String {
        val arr = JSONArray()
        for (h in hits) {
            arr.put(
                JSONObject()
                    .put("text", h.text)
                    .put("doc_id", h.docId)
                    .put("chunk_index", h.chunkIndex)
                    .put("score", h.score.toDouble()),
            )
        }
        return arr.toString()
    }

    private fun setupNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Inference Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val stopPi = PendingIntent.getService(
            this,
            1,
            Intent(this, InferenceService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.inference_leaf)
            .setContentTitle("Tool Neuron")
            .setContentText(text)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop", stopPi).build())
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    @Volatile private var deviceProfileLogged = false

    private fun logDeviceProfile() {
        if (deviceProfileLogged) return
        deviceProfileLogged = true
        try {
            val abis = Build.SUPPORTED_ABIS.joinToString(",")
            val features = readCpuFeatures()
            val totalMb = readMemTotalBytes() / (1024 * 1024)
            Log.i(TAG, "device profile: model=${Build.MODEL} soc=${Build.SOC_MODEL} abis=$abis ramMb=$totalMb features=$features")
        } catch (t: Throwable) {
            Log.w(TAG, "logDeviceProfile failed: ${t.message}")
        }
    }

    private fun readCpuFeatures(): String {
        return try {
            File("/proc/cpuinfo").bufferedReader().useLines { lines ->
                lines.firstOrNull { it.startsWith("Features") }
                    ?.substringAfter(':')
                    ?.trim()
                    ?: ""
            }
        } catch (_: Throwable) {
            ""
        }
    }

    private fun readMemAvailableBytes(): Long = readMemKey("MemAvailable:")

    private fun readMemTotalBytes(): Long = readMemKey("MemTotal:")

    private fun readMemKey(prefix: String): Long {
        return try {
            File("/proc/meminfo").bufferedReader().useLines { lines ->
                lines.firstOrNull { it.startsWith(prefix) }
                    ?.let { line ->
                        val kb = line.substringAfter(':').trim().substringBefore(' ').toLongOrNull()
                        if (kb != null) kb * 1024L else -1L
                    } ?: -1L
            }
        } catch (_: Throwable) {
            -1L
        }
    }

    companion object {
        const val ACTION_STOP = "com.dark.tool_neuron.inference.ACTION_STOP"

        private const val TAG = "InferenceService"
        private const val CHANNEL_ID = "tn_inference"
        private const val NOTIFICATION_ID = 0x544E4953 // TNIS
        private const val STREAMING_TOKEN_BATCH_BYTES = 8
        private const val VT_CACHE_BUDGET_BYTES = 500L * 1024L * 1024L

        private val ADVANCED_SAMPLING_KEYS = arrayOf(
            "repeatPenalty", "frequencyPenalty", "presencePenalty", "penaltyLastN",
            "dryMultiplier", "dryBase", "dryAllowedLength", "dryPenaltyLastN",
            "xtcProbability", "xtcThreshold",
        )
    }
}
