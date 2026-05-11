package com.dark.tool_neuron.service.server

import com.dark.gguf_lib.EmbeddingEngine
import org.json.JSONObject

class ServerEmbeddingEngine {

    private val engine = EmbeddingEngine()
    @Volatile private var loadedModelId: String = ""

    val isLoaded: Boolean get() = engine.isLoaded
    fun loadedId(): String = loadedModelId

    suspend fun ensureLoaded(modelId: String, path: String, configJson: String): Boolean {
        if (modelId == loadedModelId && engine.isLoaded) return true
        if (engine.isLoaded) engine.close()
        val cfg = parseConfig(configJson)
        val ok = engine.load(
            path = path,
            threads = cfg.optInt("threads", 2).coerceAtLeast(1),
            contextSize = cfg.optInt("contextSize", 2048),
        )
        if (ok) loadedModelId = modelId
        return ok
    }

    suspend fun embed(text: String, normalize: Boolean = true): FloatArray? =
        engine.embed(text, normalize)

    suspend fun embedBatch(texts: List<String>, normalize: Boolean = true): List<FloatArray?> =
        engine.embedBatch(texts, normalize)

    suspend fun unload() {
        if (engine.isLoaded) engine.close()
        loadedModelId = ""
    }

    private fun parseConfig(json: String): JSONObject =
        if (json.isBlank()) JSONObject()
        else try { JSONObject(json) } catch (_: Exception) { JSONObject() }
}
