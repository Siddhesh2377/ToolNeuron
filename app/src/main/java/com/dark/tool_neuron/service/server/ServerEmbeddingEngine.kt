package com.dark.tool_neuron.service.server

import com.dark.gguf_lib.RAGEngine
import org.json.JSONObject

// Routes /v1/embeddings through RAGEngine.encode(): one shared C engine in the
// :server process covers both standalone embedding and (if a chunk index is
// later attached) retrieval. The previous EmbeddingEngine path (g_embed) is
// gone; both now share g_rag's llama_model + ctx in this process.
class ServerEmbeddingEngine {

    private val engine = RAGEngine()
    @Volatile private var loadedModelId: String = ""
    @Volatile private var loadedPath: String = ""

    val isLoaded: Boolean get() = engine.isModelLoaded
    fun loadedId(): String = loadedModelId

    suspend fun ensureLoaded(modelId: String, path: String, configJson: String): Boolean {
        if (modelId == loadedModelId && engine.isModelLoaded && loadedPath == path) return true
        if (engine.isCreated) engine.close()
        val cfg = parseConfig(configJson)
        // Embedding-only path: chunk_size large enough to cover typical
        // inputs in one window (we never call addDocument here), dims
        // unused because encode() returns raw n_embd, late_chunking off
        // because there's nothing to chunk.
        val created = engine.create(
            threads      = cfg.optInt("threads", 2).coerceAtLeast(1),
            chunkSize    = cfg.optInt("contextSize", 2048).coerceAtLeast(256),
            chunkOverlap = 0,
            dims         = 256,
            topK         = 1,
            topN         = 1,
            lateChunking = false,
        )
        if (!created) return false
        val loaded = engine.loadModel(path)
        if (loaded) {
            loadedModelId = modelId
            loadedPath = path
        }
        return loaded
    }

    suspend fun embed(text: String, normalize: Boolean = true): FloatArray? =
        engine.encode(text, normalize)

    suspend fun embedBatch(texts: List<String>, normalize: Boolean = true): List<FloatArray?> =
        texts.map { engine.encode(it, normalize) }

    suspend fun unload() {
        if (engine.isCreated) engine.close()
        loadedModelId = ""
        loadedPath = ""
    }

    private fun parseConfig(json: String): JSONObject =
        if (json.isBlank()) JSONObject()
        else try { JSONObject(json) } catch (_: Exception) { JSONObject() }
}
