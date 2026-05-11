package com.dark.tool_neuron.service.server

import com.dark.ai_sherpa.OfflineModelConfig
import com.dark.ai_sherpa.OfflineRecognizer
import com.dark.ai_sherpa.OfflineRecognizerConfig
import com.dark.ai_sherpa.OfflineWhisperModelConfig
import org.json.JSONObject

class ServerSttEngine {

    private val lock = Any()
    private var stt: OfflineRecognizer? = null
    @Volatile private var loadedModelId: String = ""

    val isLoaded: Boolean get() = synchronized(lock) { stt != null }
    fun loadedId(): String = loadedModelId

    fun ensureLoaded(modelId: String, configJson: String): Boolean = synchronized(lock) {
        if (modelId == loadedModelId && stt != null) return@synchronized true
        stt?.close()
        stt = null
        loadedModelId = ""
        val cfg = try { JSONObject(configJson) } catch (_: Exception) { return@synchronized false }
        val type = cfg.optString("type", "whisper")
        return@synchronized try {
            val recognizerConfig = when (type) {
                "whisper" -> OfflineRecognizerConfig(
                    modelConfig = OfflineModelConfig(
                        whisper = OfflineWhisperModelConfig(
                            encoder = cfg.getString("encoder"),
                            decoder = cfg.getString("decoder"),
                        ),
                        tokens = cfg.getString("tokens"),
                        numThreads = cfg.optInt("numThreads", 2),
                    ),
                )
                else -> throw IllegalArgumentException("Unsupported STT type: $type")
            }
            stt = OfflineRecognizer.fromFile(recognizerConfig)
            loadedModelId = modelId
            true
        } catch (e: Exception) {
            stt = null
            loadedModelId = ""
            throw e
        }
    }

    fun recognize(samples: FloatArray, sampleRate: Int): String? = synchronized(lock) {
        val recognizer = stt ?: return@synchronized null
        try {
            val stream = recognizer.createStream()
            stream.acceptWaveform(sampleRate = sampleRate, samples = samples)
            recognizer.decode(stream)
            val result = recognizer.getResult(stream).text
            stream.close()
            result
        } catch (_: Exception) {
            null
        }
    }

    fun unload() = synchronized(lock) {
        stt?.close()
        stt = null
        loadedModelId = ""
    }
}
