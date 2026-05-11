package com.dark.tool_neuron.service.server

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ServerEngineRegistry(private val app: Context) {

    @Volatile var catalog: ServerCatalog = ServerCatalog(emptyMap())
        private set

    private val chatLock  = Mutex()
    private val vlmLock   = Mutex()
    private val embedLock = Mutex()
    private val ttsLock   = Mutex()
    private val sttLock   = Mutex()
    private val imgLock   = Mutex()

    private val chat  = ServerEngine()
    private val vlm   = ServerVlmEngine()
    private val embed = ServerEmbeddingEngine()
    private val tts   = ServerTtsEngine()
    private val stt   = ServerSttEngine()
    private val img   by lazy { ServerImageEngine(app) }

    fun setCatalog(c: ServerCatalog) {
        catalog = c
    }

    suspend fun chatFor(modelId: String): ServerEngine? = chatLock.withLock {
        val entry = catalog.byId(modelId) ?: catalog.firstOf(ServerEngineKind.CHAT_GGUF)
        entry ?: return@withLock null
        if (entry.kind != ServerEngineKind.CHAT_GGUF) return@withLock null
        if (chat.isLoaded && chat.loadedId() == entry.id) return@withLock chat
        if (chat.isLoaded) chat.unload()
        val ok = chat.load(entry.id, entry.path, entry.configJson)
        if (ok) chat else null
    }

    suspend fun vlmFor(modelId: String): ServerVlmEngine? = vlmLock.withLock {
        val entry = catalog.byId(modelId) ?: catalog.firstOf(ServerEngineKind.VLM)
        entry ?: return@withLock null
        if (entry.kind != ServerEngineKind.VLM) return@withLock null
        val ok = vlm.ensureLoaded(entry.id, entry.path, entry.mmprojPath, entry.configJson)
        if (ok) vlm else null
    }

    suspend fun embedFor(modelId: String): ServerEmbeddingEngine? = embedLock.withLock {
        val entry = catalog.byId(modelId) ?: catalog.firstOf(ServerEngineKind.EMBEDDING)
        entry ?: return@withLock null
        if (entry.kind != ServerEngineKind.EMBEDDING) return@withLock null
        val ok = embed.ensureLoaded(entry.id, entry.path, entry.configJson)
        if (ok) embed else null
    }

    fun ttsFor(modelId: String): ServerTtsEngine? {
        val entry = catalog.byId(modelId) ?: catalog.firstOf(ServerEngineKind.TTS) ?: return null
        if (entry.kind != ServerEngineKind.TTS) return null
        return synchronized(ttsLockObj) {
            try {
                val ok = tts.ensureLoaded(entry.id, entry.configJson)
                if (ok) tts else null
            } catch (_: Exception) {
                null
            }
        }
    }

    fun sttFor(modelId: String): ServerSttEngine? {
        val entry = catalog.byId(modelId) ?: catalog.firstOf(ServerEngineKind.STT) ?: return null
        if (entry.kind != ServerEngineKind.STT) return null
        return synchronized(sttLockObj) {
            try {
                val ok = stt.ensureLoaded(entry.id, entry.configJson)
                if (ok) stt else null
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun imageGenFor(modelId: String, width: Int, height: Int): Pair<ServerImageEngine, ServerCatalogEntry>? =
        imgLock.withLock {
            val entry = catalog.byId(modelId) ?: catalog.firstOf(ServerEngineKind.IMAGE_GEN) ?: return@withLock null
            if (entry.kind != ServerEngineKind.IMAGE_GEN) return@withLock null
            val ok = img.loadDiffusion(entry.id, entry.name, entry.path, width, height)
            if (ok) img to entry else null
        }

    suspend fun upscalerFor(modelId: String): Pair<ServerImageEngine, ServerCatalogEntry>? = imgLock.withLock {
        val entry = catalog.byId(modelId) ?: catalog.firstOf(ServerEngineKind.IMAGE_UPSCALER) ?: return@withLock null
        if (entry.kind != ServerEngineKind.IMAGE_UPSCALER) return@withLock null
        val ok = img.loadUpscaler(entry.id, entry.path)
        if (ok) img to entry else null
    }

    suspend fun shutdownAll() {
        try { chat.unload() } catch (_: Exception) {}
        try { vlm.unload() } catch (_: Exception) {}
        try { embed.unload() } catch (_: Exception) {}
        tts.unload()
        stt.unload()
        try { img.shutdown() } catch (_: Exception) {}
    }

    private val ttsLockObj = Any()
    private val sttLockObj = Any()
}
