package com.dark.tool_neuron.service.inference

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
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
import com.dark.gguf_lib.GGMLEngine
import com.dark.gguf_lib.models.GenerationEvent
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

class InferenceService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val engine = GGMLEngine()
    private val ttsLock = Any()
    private val sttLock = Any()
    private var tts: OfflineTts? = null
    private var stt: OfflineRecognizer? = null

    override fun onCreate() {
        super.onCreate()
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        setupNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Inference ready"))
        Log.d(TAG, "InferenceService created (pid=${Process.myPid()})")
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        runBlocking(Dispatchers.IO) {
            if (engine.isLoaded) engine.unload()
        }
        synchronized(ttsLock) { tts?.close(); tts = null }
        synchronized(sttLock) { stt?.close(); stt = null }
        scope.cancel()
        Log.d(TAG, "InferenceService destroyed")
        super.onDestroy()
    }

    private val binder = object : IInferenceService.Stub() {

        // ── Model lifecycle ──

        override fun loadModel(modelPath: String, configJson: String, callback: IModelLoadCallback) {
            scope.launch {
                try {
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
                        safeCallback(callback) { it.onError("Failed to load model") }
                    }
                } catch (e: OutOfMemoryError) {
                    try { engine.unload() } catch (_: Throwable) {}
                    safeCallback(callback) { it.onError("Out of memory") }
                } catch (e: Exception) {
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

        // ── Generation ──

        override fun generate(prompt: String, maxTokens: Int, callback: IGenerationCallback) {
            collectFlow(engine.generateFlow(prompt, maxTokens), callback)
        }

        override fun generateMultiTurn(messagesJson: String, maxTokens: Int, callback: IGenerationCallback) {
            collectFlow(engine.generateMultiTurnFlow(messagesJson, maxTokens), callback)
        }

        override fun stopGeneration() {
            engine.stopGeneration()
        }

        // ── Sampling ──

        override fun setSampling(samplingJson: String) {
            engine.updateSamplerParams(samplingJson)
        }

        override fun setSystemPrompt(prompt: String) {
            engine.setSystemPrompt(prompt)
        }

        override fun setChatTemplate(template: String) {
            engine.setChatTemplate(template)
        }

        // ── Tool calling ──

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

        // ── Context ──

        override fun getContextUsage(): Float = engine.getContextUsage()

        override fun getContextInfo(prompt: String?): String? = null

        // ── Thinking ──

        override fun supportsThinking(): Boolean =
            engine.isLoaded && engine.supportsThinking()

        override fun setThinkingEnabled(enabled: Boolean) {
            engine.setThinkingEnabled(enabled)
        }

        // ── Optimizations ──

        override fun setPromptCacheDir(path: String) {
            engine.setPromptCacheDir(path)
        }

        override fun warmUp(): Boolean = engine.warmUp()

        override fun setThreadMode(mode: Int) {
            // Thread mode set at load time via config
        }

        // ── KV state ──

        override fun getStateSize(): Long = engine.getStateSize()

        override fun stateSaveToFile(path: String): Boolean = engine.stateSaveToFile(path)

        override fun stateLoadFromFile(path: String): Boolean = engine.stateLoadFromFile(path)

        // ── VLM ──

        override fun loadVlmProjector(path: String, threads: Int): Boolean =
            engine.loadVlmProjector(path, threads)

        override fun loadVlmProjectorFromFd(pfd: ParcelFileDescriptor, threads: Int): Boolean {
            val fd = pfd.detachFd()
            return engine.loadVlmProjectorFromFd(fd, threads)
        }

        override fun releaseVlmProjector() {
            engine.releaseVlmProjector()
        }

        override fun isVlmLoaded(): Boolean = engine.isVlmLoaded

        override fun getVlmInfo(): String? = engine.getVlmInfoJson()

        override fun generateVlm(messagesJson: String, maxTokens: Int, callback: IGenerationCallback) {
            collectFlow(engine.generateVlmFlow(messagesJson, emptyList(), maxTokens), callback)
        }

        // TTS

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
                    null
                }
            }
        }

        override fun getTtsSampleRate(): Int = synchronized(ttsLock) { tts?.sampleRate ?: 0 }

        // STT

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
                    null
                }
            }
        }

    }

    // ── Internal helpers ──

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
        val system = config.optString("systemPrompt", "")
        if (system.isNotEmpty()) engine.setSystemPrompt(system)
        val template = config.optString("chatTemplate", "")
        if (template.isNotEmpty()) engine.setChatTemplate(template)
    }

    private inline fun <T> safeCallback(target: T, block: (T) -> Unit) {
        try { block(target) } catch (_: DeadObjectException) {}
    }

    private fun setupNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Inference Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.inference_leaf)
            .setContentTitle("Tool Neuron")
            .setContentText(text)
            .setOngoing(true)
            .build()

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        private const val TAG = "InferenceService"
        private const val CHANNEL_ID = "tn_inference"
        private const val NOTIFICATION_ID = 0x544E4953 // TNIS
    }
}
