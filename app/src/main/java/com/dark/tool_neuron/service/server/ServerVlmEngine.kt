package com.dark.tool_neuron.service.server

import com.dark.gguf_lib.GGMLEngine
import com.dark.gguf_lib.ImageQuality
import com.dark.gguf_lib.models.GenerationEvent
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import java.io.File

class ServerVlmEngine {

    private val engine = GGMLEngine()
    @Volatile private var loadedModelId: String = ""
    @Volatile private var loadedMmproj: String = ""

    val isLoaded: Boolean get() = engine.isLoaded && engine.isVlmLoaded
    fun loadedId(): String = loadedModelId

    suspend fun ensureLoaded(modelId: String, basePath: String, mmprojPath: String, configJson: String): Boolean {
        if (modelId == loadedModelId && engine.isLoaded && engine.isVlmLoaded) return true
        if (engine.isLoaded) {
            try { engine.releaseVlmProjector() } catch (_: Exception) {}
            engine.unload()
        }
        val cfg = parseConfig(configJson)
        val ok = engine.load(
            path = basePath,
            contextSize = cfg.optInt("contextSize", 4096),
            flashAttn = cfg.optBoolean("flashAttn", true),
            cacheTypeK = cfg.optString("cacheTypeK", "q8_0"),
            cacheTypeV = cfg.optString("cacheTypeV", "q8_0"),
        )
        if (!ok) return false
        engine.setThreadMode(cfg.optInt("threadMode", 1))

        val mm = if (mmprojPath.isNotBlank() && File(mmprojPath).exists()) mmprojPath else colocatedMmproj(basePath)
        if (mm.isBlank() || !File(mm).exists()) {
            engine.unload()
            return false
        }
        val vlmOk = engine.loadVlmProjector(
            path = mm,
            threads = cfg.optInt("threadMode", 2).coerceAtLeast(1),
            imageMinTokens = cfg.optInt("imageMinTokens", 256),
            imageMaxTokens = cfg.optInt("imageMaxTokens", 1024),
        )
        if (!vlmOk) {
            engine.unload()
            return false
        }
        loadedModelId = modelId
        loadedMmproj = mm
        return true
    }

    fun defaultMarker(): String? = engine.getVlmDefaultMarker()

    fun setSampling(samplingJson: String) {
        engine.updateSamplerParams(samplingJson)
    }

    fun setSystemPrompt(prompt: String) = engine.setSystemPrompt(prompt)

    fun generateFlow(messagesJson: String, imageBytes: List<ByteArray>, maxTokens: Int): Flow<GenerationEvent> =
        engine.generateVlmFlow(
            messagesJson = messagesJson,
            imageData = imageBytes,
            maxTokens = maxTokens,
            vtKeys = emptyList<ByteArray>(),
            vlmKvKey = null,
            imageQuality = ImageQuality.HIGH,
        )

    fun stopGeneration() = engine.stopGeneration()

    suspend fun unload() {
        try { engine.releaseVlmProjector() } catch (_: Exception) {}
        if (engine.isLoaded) engine.unload()
        loadedModelId = ""
        loadedMmproj = ""
    }

    private fun colocatedMmproj(basePath: String): String {
        val base = File(basePath)
        val parent = base.parentFile ?: return ""
        val candidate = parent.listFiles()
            ?.firstOrNull { it.isFile && it.name.contains("mmproj", ignoreCase = true) }
        return candidate?.absolutePath.orEmpty()
    }

    private fun parseConfig(json: String): JSONObject =
        if (json.isBlank()) JSONObject()
        else try { JSONObject(json) } catch (_: Exception) { JSONObject() }
}
