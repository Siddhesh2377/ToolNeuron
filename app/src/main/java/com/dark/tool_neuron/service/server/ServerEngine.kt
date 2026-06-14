package com.dark.tool_neuron.service.server

import com.dark.gguf_lib.GGMLEngine
import com.dark.gguf_lib.models.GenerationEvent
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

class ServerEngine {

    private val engine = GGMLEngine()
    @Volatile private var loadedModelId: String = ""

    val isLoaded: Boolean get() = engine.isLoaded
    fun loadedId(): String = loadedModelId

    suspend fun load(modelId: String, path: String, configJson: String): Boolean {
        if (engine.isLoaded) engine.unload()
        val cfg = parseConfig(configJson)
        val ok = engine.load(
            path = path,
            contextSize = cfg.optInt("contextSize", 4096),
            flashAttn = cfg.optBoolean("flashAttn", true),
            cacheTypeK = cfg.optString("cacheTypeK", "q8_0"),
            cacheTypeV = cfg.optString("cacheTypeV", "q8_0"),
            opOffload = cfg.optBoolean("opOffload", false),
        )
        if (ok) {
            engine.setThreadMode(cfg.optInt("threadMode", 1))
            applySamplingFromConfig(cfg)
            loadedModelId = modelId
        }
        return ok
    }

    suspend fun unload() {
        if (engine.isLoaded) engine.unload()
        loadedModelId = ""
    }

    fun stopGeneration() = engine.stopGeneration()

    fun setSampling(samplingJson: String) {
        engine.updateSamplerParams(samplingJson)
    }

    fun setSystemPrompt(prompt: String) = engine.setSystemPrompt(prompt)

    fun setChatTemplate(template: String) = engine.setChatTemplate(template)

    fun getModelInfoJson(): String? = engine.getModelInfoJson()

    fun generateMultiTurnFlow(messagesJson: String, maxTokens: Int): Flow<GenerationEvent> =
        engine.generateMultiTurnFlow(messagesJson, maxTokens)

    private fun parseConfig(json: String): JSONObject =
        if (json.isBlank()) JSONObject()
        else try { JSONObject(json) } catch (_: Exception) { JSONObject() }

    private fun applySamplingFromConfig(cfg: JSONObject) {
        if (cfg.has("temperature") || cfg.has("topK") || cfg.has("topP")) {
            engine.setSampling(
                temperature = cfg.optDouble("temperature", 0.7).toFloat(),
                topK = cfg.optInt("topK", 40),
                topP = cfg.optDouble("topP", 0.9).toFloat(),
                minP = cfg.optDouble("minP", 0.05).toFloat(),
                mirostat = cfg.optInt("mirostat", 0),
                mirostatTau = cfg.optDouble("mirostatTau", 5.0).toFloat(),
                mirostatEta = cfg.optDouble("mirostatEta", 0.1).toFloat(),
                seed = cfg.optInt("seed", -1),
            )
        }

        val advanced = JSONObject()
        for (key in ADVANCED_SAMPLING_KEYS) {
            if (cfg.has(key)) advanced.put(key, cfg.get(key))
        }
        if (advanced.length() > 0) engine.updateSamplerParams(advanced.toString())

        val nWindow = cfg.optInt("kvWindow", 0)
        if (nWindow > 0) {
            engine.setKvPolicy(
                nSink = cfg.optInt("kvSink", 4),
                nWindow = nWindow,
                evictAtFull = cfg.optBoolean("kvEvictAtFull", true),
            )
        }

        val system = cfg.optString("systemPrompt", "")
        if (system.isNotEmpty()) engine.setSystemPrompt(system)
    }

    companion object {
        private val ADVANCED_SAMPLING_KEYS = arrayOf(
            "repeatPenalty", "frequencyPenalty", "presencePenalty", "penaltyLastN",
            "dryMultiplier", "dryBase", "dryAllowedLength", "dryPenaltyLastN",
            "xtcProbability", "xtcThreshold",
        )
    }
}
