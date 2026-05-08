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
import com.dark.ai_sherpa.SherpaLib
import com.dark.gguf_lib.GGMLEngine
import com.dark.gguf_lib.GGUFNativeLib
import com.dark.gguf_lib.models.GenerationEvent
import java.io.File
import com.dark.tool_neuron.R
import com.dark.tool_neuron.service.IGenerationCallback
import com.dark.tool_neuron.service.IInferenceService
import com.dark.tool_neuron.service.IModelLoadCallback
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
    private val ttsLock = Any()
    private val sttLock = Any()
    private var tts: OfflineTts? = null
    private var stt: OfflineRecognizer? = null

    private val crashLogFile: File by lazy {
        File(filesDir, "inference_crash.json").also { it.parentFile?.mkdirs() }
    }
    private val sherpaCrashLogFile: File by lazy {
        File(filesDir, "sherpa_crash.json").also { it.parentFile?.mkdirs() }
    }
    private val ktErrorLock = Any()
    @Volatile private var ktLastErrorJson: String? = null

    override fun onCreate() {
        super.onCreate()
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        setupNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Inference ready"))
        engine.setTokenBatchSize(STREAMING_TOKEN_BATCH_BYTES)
        try {
            GGUFNativeLib.nativeErrorInit()
            GGUFNativeLib.nativeErrorSetCrashLogPath(crashLogFile.absolutePath)
        } catch (t: Throwable) {
            Log.e(TAG, "gguf error tracker init failed", t)
        }
        try {
            SherpaLib.nativeErrorInit()
            SherpaLib.nativeErrorSetCrashLogPath(sherpaCrashLogFile.absolutePath)
        } catch (t: Throwable) {
            Log.e(TAG, "sherpa error tracker init failed", t)
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            try { engine.stopGeneration() } catch (_: Throwable) {}
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        try {
            runBlocking(Dispatchers.IO) {
                if (engine.isLoaded) engine.unload()
            }
        } catch (_: Throwable) {}
        synchronized(ttsLock) { tts?.close(); tts = null }
        synchronized(sttLock) { stt?.close(); stt = null }
        scope.cancel()
        super.onDestroy()
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
                        threadMode = config.optInt("threadMode", 1),
                        flashAttn = config.optBoolean("flashAttn", true),
                        cacheTypeK = config.optString("cacheTypeK", "q8_0"),
                        cacheTypeV = config.optString("cacheTypeV", "q8_0"),
                    )
                    if (success) {
                        applySamplingFromConfig(config)
                        updateNotification("Model loaded")
                        safeCallback(callback) { it.onSuccess(engine.getModelInfoJson() ?: "{}") }
                    } else {
                        val nativeMsg = nativeErrorOrFallback("Model load returned false")
                        captureKt("loadModel", "path=$modelPath", "ModelLoad", nativeMsg)
                        safeCallback(callback) { it.onError(nativeMsg) }
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
                        threadMode = config.optInt("threadMode", 1),
                        flashAttn = config.optBoolean("flashAttn", true),
                        cacheTypeK = config.optString("cacheTypeK", "q8_0"),
                        cacheTypeV = config.optString("cacheTypeV", "q8_0"),
                    )
                    if (success) {
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

        override fun isToolCallingSupported(): Boolean =
            engine.isLoaded && engine.isToolCallingSupported()

        override fun setToolsJson(toolsJson: String) {
            engine.setToolsJson(toolsJson)
        }

        override fun enableToolCalling(toolsJson: String, grammarMode: Int, useTypedGrammar: Boolean) {
            engine.enableToolCalling(toolsJson, grammarMode, useTypedGrammar)
        }

        override fun clearTools() {
            engine.clearTools()
        }

        override fun getContextUsage(): Float = engine.getContextUsage()

        override fun getContextInfo(prompt: String?): String? = null

        override fun supportsThinking(): Boolean =
            engine.isLoaded && engine.supportsThinking()

        override fun setThinkingEnabled(enabled: Boolean) {
            engine.setThinkingEnabled(enabled)
        }

        override fun setPromptCacheDir(path: String) {
            engine.setPromptCacheDir(path)
        }

        override fun warmUp(): Boolean = engine.warmUp()

        override fun setThreadMode(mode: Int) {}

        override fun getStateSize(): Long = engine.getStateSize()

        override fun stateSaveToFile(path: String): Boolean = engine.stateSaveToFile(path)

        override fun stateLoadFromFile(path: String): Boolean = engine.stateLoadFromFile(path)

        override fun loadVlmProjector(path: String, threads: Int, imageMinTokens: Int, imageMaxTokens: Int): Boolean =
            engine.loadVlmProjector(path, threads, imageMinTokens, imageMaxTokens)

        override fun loadVlmProjectorFromFd(pfd: ParcelFileDescriptor, threads: Int, imageMinTokens: Int, imageMaxTokens: Int): Boolean {
            val fd = pfd.detachFd()
            return engine.loadVlmProjectorFromFd(fd, threads, imageMinTokens, imageMaxTokens)
        }

        override fun releaseVlmProjector() {
            engine.releaseVlmProjector()
        }

        override fun isVlmLoaded(): Boolean = engine.isVlmLoaded

        override fun getVlmInfo(): String? = engine.getVlmInfoJson()

        override fun getVlmDefaultMarker(): String = engine.getVlmDefaultMarker()

        override fun generateVlm(
            messagesJson: String,
            imageFds: Array<ParcelFileDescriptor>?,
            maxTokens: Int,
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
            collectFlow(engine.generateVlmFlow(messagesJson, images, maxTokens), callback)
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

        override fun getLastErrorJson(): String = readNativeOrKtError()

        override fun clearLastError() {
            synchronized(ktErrorLock) { ktLastErrorJson = null }
            try { GGUFNativeLib.nativeErrorClear() } catch (_: Throwable) {}
        }

        override fun drainCrashLogJson(): String {
            return try {
                if (!crashLogFile.exists()) return ""
                val content = crashLogFile.readText()
                crashLogFile.delete()
                content
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to drain crash log", e)
                ""
            }
        }

        override fun getSherpaLastErrorJson(): String {
            return try {
                val raw = SherpaLib.nativeErrorGetLastJson()
                if (raw == "{}" || raw.isBlank()) "" else raw
            } catch (_: Throwable) { "" }
        }

        override fun clearSherpaLastError() {
            try { SherpaLib.nativeErrorClear() } catch (_: Throwable) {}
        }

        override fun drainSherpaCrashLogJson(): String {
            return try {
                if (!sherpaCrashLogFile.exists()) return ""
                val content = sherpaCrashLogFile.readText()
                sherpaCrashLogFile.delete()
                content
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to drain sherpa crash log", e)
                ""
            }
        }

    }

    private fun collectFlow(flow: Flow<GenerationEvent>, callback: IGenerationCallback) {
        scope.launch(Dispatchers.IO) {
            try {
                flow.collect { event ->
                    try {
                        when (event) {
                            is GenerationEvent.Token -> callback.onToken(event.text)
                            is GenerationEvent.ToolCall -> callback.onToolCall(event.name, event.argsJson)
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

    private fun nativeErrorOrFallback(fallback: String): String {
        return try {
            val j = JSONObject(GGUFNativeLib.nativeErrorGetLastJson())
            j.optString("message").takeIf { it.isNotBlank() } ?: fallback
        } catch (_: Throwable) { fallback }
    }

    private fun captureKt(op: String, detail: String?, category: String, message: String) {
        fun esc(s: String?): String = s.orEmpty()
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        val ts = System.currentTimeMillis()
        val json = """{"code":1,"category":"${esc(category)}","message":"${esc(message)}","op_at_time":{"op":"${esc(op)}","detail":"${esc(detail)}","started_ms":$ts},"timestamp":$ts}"""
        synchronized(ktErrorLock) { ktLastErrorJson = json }
    }

    private fun readNativeOrKtError(): String {
        val kt = synchronized(ktErrorLock) { ktLastErrorJson }
        if (!kt.isNullOrBlank()) return kt
        return try {
            val native = GGUFNativeLib.nativeErrorGetLastJson()
            if (native == "{}" || native.isBlank()) "" else native
        } catch (_: Throwable) { "" }
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

        private val ADVANCED_SAMPLING_KEYS = arrayOf(
            "repeatPenalty", "frequencyPenalty", "presencePenalty", "penaltyLastN",
            "dryMultiplier", "dryBase", "dryAllowedLength", "dryPenaltyLastN",
            "xtcProbability", "xtcThreshold",
        )
    }
}
