package com.dark.tool_neuron.service.server

import com.dark.ai_sherpa.OfflineTts
import com.dark.ai_sherpa.OfflineTtsConfig
import com.dark.ai_sherpa.OfflineTtsModelConfig
import com.dark.ai_sherpa.OfflineTtsVitsModelConfig
import org.json.JSONObject

class ServerTtsEngine {

    private val lock = Any()
    private var tts: OfflineTts? = null
    @Volatile private var loadedModelId: String = ""

    val isLoaded: Boolean get() = synchronized(lock) { tts != null }
    fun loadedId(): String = loadedModelId

    fun ensureLoaded(modelId: String, configJson: String): Boolean = synchronized(lock) {
        if (modelId == loadedModelId && tts != null) return@synchronized true
        tts?.close()
        tts = null
        loadedModelId = ""
        val cfg = try { JSONObject(configJson) } catch (_: Exception) { return@synchronized false }
        try {
            val ttsConfig = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = cfg.getString("model"),
                        tokens = cfg.getString("tokens"),
                        dataDir = cfg.optString("dataDir", ""),
                    ),
                    numThreads = cfg.optInt("numThreads", 2),
                ),
            )
            tts = OfflineTts.fromFile(ttsConfig)
            loadedModelId = modelId
            true
        } catch (e: Exception) {
            tts = null
            loadedModelId = ""
            throw e
        }
    }

    fun synthesize(text: String, speakerId: Int, speed: Float): FloatArray? = synchronized(lock) {
        val current = tts ?: return@synchronized null
        try {
            current.generate(text, sid = speakerId, speed = speed).samples
        } catch (e: Exception) {
            null
        }
    }

    fun sampleRate(): Int = synchronized(lock) { tts?.sampleRate ?: 0 }

    fun unload() = synchronized(lock) {
        tts?.close()
        tts = null
        loadedModelId = ""
    }
}
