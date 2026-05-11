package com.dark.tool_neuron.service.server

import android.graphics.Bitmap
import android.util.Log
import com.dark.ai_sd.DiffusionGenerationParams
import com.dark.ai_sd.DiffusionGenerationResult
import com.dark.gguf_lib.models.GenerationEvent
import com.dark.native_server.InferenceBridge
import com.dark.native_server.NativeServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "ServerBridge"

class ServerInferenceBridge(
    private val registry: ServerEngineRegistry,
    private val onRequestEvent: (ServerRequestEvent) -> Unit,
) : InferenceBridge() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<Long, Job>()
    private val replyJobs = ConcurrentHashMap<Long, Job>()

    override fun startGeneration(
        genId: Long,
        modelId: String,
        messagesJson: String,
        paramsJson: String,
        imagePaths: Array<String>,
    ): Boolean {
        val params = runCatching { JSONObject(paramsJson) }.getOrDefault(JSONObject())
        val maxTokens = params.optInt("max_tokens", DEFAULT_MAX_TOKENS).coerceAtLeast(1)
        val (systemPrompt, forwardedMessages) = splitSystemMessages(messagesJson)

        val isVlm = imagePaths.isNotEmpty()
        val job = scope.launch {
            try {
                if (isVlm) {
                    val engine = registry.vlmFor(modelId) ?: run {
                        NativeServer.nativeFeedError(genId, "VLM engine unavailable")
                        return@launch
                    }
                    buildSamplingJson(params)?.let { engine.setSampling(it) }
                    if (systemPrompt.isNotBlank()) engine.setSystemPrompt(systemPrompt)
                    val annotated = prefixVlmMarker(forwardedMessages, engine.defaultMarker())
                    val images = imagePaths.mapNotNull { p ->
                        runCatching { File(p).readBytes() }.getOrNull()
                    }
                    if (images.isEmpty()) {
                        NativeServer.nativeFeedError(genId, "no readable image payloads")
                        return@launch
                    }
                    engine.generateFlow(annotated, images, maxTokens).collect { event ->
                        when (event) {
                            is GenerationEvent.Token  -> NativeServer.nativeFeedToken(genId, event.text)
                            is GenerationEvent.Done   -> NativeServer.nativeFeedDone(genId, "stop")
                            is GenerationEvent.Error  -> NativeServer.nativeFeedError(genId, event.message)
                            else -> Unit
                        }
                    }
                } else {
                    val engine = registry.chatFor(modelId) ?: run {
                        NativeServer.nativeFeedError(genId, "chat engine unavailable")
                        return@launch
                    }
                    buildSamplingJson(params)?.let { engine.setSampling(it) }
                    if (systemPrompt.isNotBlank()) engine.setSystemPrompt(systemPrompt)
                    engine.generateMultiTurnFlow(forwardedMessages, maxTokens).collect { event ->
                        when (event) {
                            is GenerationEvent.Token  -> NativeServer.nativeFeedToken(genId, event.text)
                            is GenerationEvent.Done   -> NativeServer.nativeFeedDone(genId, "stop")
                            is GenerationEvent.Error  -> NativeServer.nativeFeedError(genId, event.message)
                            else -> Unit
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "generation failed", e)
                NativeServer.nativeFeedError(genId, e.message ?: "generation failed")
            } finally {
                jobs.remove(genId)
            }
        }
        jobs[genId] = job
        return true
    }

    override fun cancelGeneration(genId: Long) {
        val job = jobs.remove(genId) ?: return
        job.cancel()
        registry.catalog.firstOf(ServerEngineKind.CHAT_GGUF)?.let {}
    }

    override fun startEmbedding(replyId: Long, modelId: String, inputsJson: String): Boolean {
        val arr = runCatching { JSONArray(inputsJson) }.getOrNull() ?: return false
        val inputs = (0 until arr.length()).mapNotNull { arr.opt(it)?.toString() }
        if (inputs.isEmpty()) return false
        val job = scope.launch {
            try {
                val engine = registry.embedFor(modelId) ?: run {
                    NativeServer.nativeFeedReplyError(replyId, "embedding engine unavailable")
                    return@launch
                }
                val vectors = engine.embedBatch(inputs, normalize = true)
                val payload = JSONObject().apply {
                    val varr = JSONArray()
                    vectors.forEach { row ->
                        val r = JSONArray()
                        if (row != null) {
                            for (v in row) r.put(v.toDouble())
                        }
                        varr.put(r)
                    }
                    put("vectors", varr)
                }
                NativeServer.nativeFeedReplyText(replyId, payload.toString(), "application/json")
            } catch (e: Exception) {
                Log.w(TAG, "embedding failed", e)
                NativeServer.nativeFeedReplyError(replyId, e.message ?: "embedding failed")
            } finally {
                replyJobs.remove(replyId)
            }
        }
        replyJobs[replyId] = job
        return true
    }

    override fun startTts(
        replyId: Long,
        modelId: String,
        text: String,
        speakerId: Int,
        speed: Float,
        outPath: String,
    ): Boolean {
        val job = scope.launch {
            try {
                val engine = registry.ttsFor(modelId) ?: run {
                    NativeServer.nativeFeedReplyError(replyId, "TTS engine unavailable")
                    return@launch
                }
                val samples = engine.synthesize(text, speakerId, speed) ?: run {
                    NativeServer.nativeFeedReplyError(replyId, "TTS synthesis returned no audio")
                    return@launch
                }
                val sampleRate = engine.sampleRate().coerceAtLeast(8000)
                if (!ServerWavCodec.writeWav(outPath, samples, sampleRate)) {
                    NativeServer.nativeFeedReplyError(replyId, "could not write WAV output")
                    return@launch
                }
                NativeServer.nativeFeedReplyBinary(replyId, outPath, "audio/wav")
            } catch (e: Exception) {
                Log.w(TAG, "TTS failed", e)
                NativeServer.nativeFeedReplyError(replyId, e.message ?: "TTS failed")
            } finally {
                replyJobs.remove(replyId)
            }
        }
        replyJobs[replyId] = job
        return true
    }

    override fun startStt(replyId: Long, modelId: String, wavPath: String): Boolean {
        val job = scope.launch {
            try {
                val engine = registry.sttFor(modelId) ?: run {
                    NativeServer.nativeFeedReplyError(replyId, "STT engine unavailable")
                    return@launch
                }
                val pcm = ServerWavCodec.readWav(wavPath) ?: run {
                    NativeServer.nativeFeedReplyError(replyId, "unsupported audio format (WAV PCM only)")
                    return@launch
                }
                val text = engine.recognize(pcm.samples, pcm.sampleRate)
                if (text == null) {
                    NativeServer.nativeFeedReplyError(replyId, "recognition failed")
                } else {
                    NativeServer.nativeFeedReplyText(replyId, text, "text/plain")
                }
            } catch (e: Exception) {
                Log.w(TAG, "STT failed", e)
                NativeServer.nativeFeedReplyError(replyId, e.message ?: "STT failed")
            } finally {
                replyJobs.remove(replyId)
            }
        }
        replyJobs[replyId] = job
        return true
    }

    override fun startImageGen(
        replyId: Long,
        modelId: String,
        paramsJson: String,
        inputImagePath: String,
        maskPath: String,
        outPath: String,
    ): Boolean {
        val params = runCatching { JSONObject(paramsJson) }.getOrDefault(JSONObject())
        val width  = params.optInt("width", 512)
        val height = params.optInt("height", 512)
        val job = scope.launch {
            try {
                val pair = registry.imageGenFor(modelId, width, height) ?: run {
                    NativeServer.nativeFeedReplyError(replyId, "image engine unavailable or runtime not installed")
                    return@launch
                }
                val engine = pair.first
                val sdParams = DiffusionGenerationParams(
                    prompt          = params.optString("prompt", ""),
                    negativePrompt  = params.optString("negative_prompt", ""),
                    steps           = params.optInt("steps", 20),
                    cfgScale        = params.optDouble("cfg", params.optDouble("cfg_scale", 7.0)).toFloat(),
                    seed            = if (params.has("seed") && !params.isNull("seed")) params.optLong("seed") else null,
                    width           = width,
                    height          = height,
                    scheduler       = params.optString("scheduler", "Euler a"),
                    useOpenCL       = params.optBoolean("use_opencl", false),
                    inputImage      = inputImagePath.ifBlank { "" },
                    mask            = maskPath.ifBlank { "" },
                    denoiseStrength = params.optDouble("denoise", 0.7).toFloat(),
                    showDiffusionProcess = false,
                    showDiffusionStride  = 4,
                )
                val result = engine.generate(sdParams)
                when (result) {
                    is DiffusionGenerationResult.Success -> {
                        if (!engine.writePng(outPath, result.bitmap)) {
                            NativeServer.nativeFeedReplyError(replyId, "failed to encode PNG")
                            return@launch
                        }
                        NativeServer.nativeFeedReplyBinary(replyId, outPath, "image/png")
                    }
                    is DiffusionGenerationResult.Failure -> {
                        NativeServer.nativeFeedReplyError(replyId, result.error)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "image gen failed", e)
                NativeServer.nativeFeedReplyError(replyId, e.message ?: "image generation failed")
            } finally {
                replyJobs.remove(replyId)
            }
        }
        replyJobs[replyId] = job
        return true
    }

    override fun startImageUpscale(
        replyId: Long,
        modelId: String,
        imagePath: String,
        outPath: String,
    ): Boolean {
        val job = scope.launch {
            try {
                val pair = registry.upscalerFor(modelId) ?: run {
                    NativeServer.nativeFeedReplyError(replyId, "upscaler engine unavailable")
                    return@launch
                }
                val engine = pair.first
                val bitmap = engine.loadBitmap(imagePath) ?: run {
                    NativeServer.nativeFeedReplyError(replyId, "could not decode input image")
                    return@launch
                }
                val out: Bitmap? = engine.upscale(bitmap)
                if (out == null) {
                    NativeServer.nativeFeedReplyError(replyId, "upscale failed")
                    return@launch
                }
                if (!engine.writePng(outPath, out)) {
                    NativeServer.nativeFeedReplyError(replyId, "failed to encode PNG")
                    return@launch
                }
                NativeServer.nativeFeedReplyBinary(replyId, outPath, "image/png")
            } catch (e: Exception) {
                Log.w(TAG, "upscale failed", e)
                NativeServer.nativeFeedReplyError(replyId, e.message ?: "upscale failed")
            } finally {
                replyJobs.remove(replyId)
            }
        }
        replyJobs[replyId] = job
        return true
    }

    override fun onRequestEvent(eventJson: String) {
        val o = runCatching { JSONObject(eventJson) }.getOrNull() ?: return
        val evt = ServerRequestEvent(
            timestampMs = o.optLong("ts_ms"),
            method      = o.optString("method"),
            path        = o.optString("path"),
            status      = o.optInt("status"),
            durationMs  = o.optLong("duration_ms"),
            client      = o.optString("client"),
        )
        onRequestEvent.invoke(evt)
    }

    fun shutdown() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        replyJobs.values.forEach { it.cancel() }
        replyJobs.clear()
        scope.cancel()
    }

    private fun splitSystemMessages(messagesJson: String): Pair<String, String> {
        val source = runCatching { JSONArray(messagesJson) }.getOrNull() ?: return "" to messagesJson
        val out = JSONArray()
        val sysBuilder = StringBuilder()
        for (i in 0 until source.length()) {
            val msg = source.optJSONObject(i) ?: continue
            val role = msg.optString("role")
            val content = msg.optString("content")
            if (role == "system") {
                if (content.isNotBlank()) {
                    if (sysBuilder.isNotEmpty()) sysBuilder.append("\n")
                    sysBuilder.append(content)
                }
                continue
            }
            out.put(msg)
        }
        return sysBuilder.toString() to out.toString()
    }

    private fun prefixVlmMarker(messagesJson: String, marker: String?): String {
        val mk = marker?.takeIf { it.isNotBlank() } ?: return messagesJson
        val arr = runCatching { JSONArray(messagesJson) }.getOrNull() ?: return messagesJson
        var lastUser = -1
        for (i in arr.length() - 1 downTo 0) {
            val obj = arr.optJSONObject(i) ?: continue
            if (obj.optString("role") == "user") { lastUser = i; break }
        }
        if (lastUser < 0) return messagesJson
        val obj = arr.getJSONObject(lastUser)
        val existing = obj.optString("content")
        obj.put("content", mk + existing)
        arr.put(lastUser, obj)
        return arr.toString()
    }

    private fun buildSamplingJson(params: JSONObject): String? {
        val sampling = JSONObject()
        var changed = false
        if (params.has("temperature")) {
            sampling.put("temperature", params.optDouble("temperature"))
            changed = true
        }
        if (params.has("top_p")) {
            sampling.put("topP", params.optDouble("top_p"))
            changed = true
        }
        if (params.has("presence_penalty")) {
            sampling.put("presencePenalty", params.optDouble("presence_penalty"))
            changed = true
        }
        if (params.has("frequency_penalty")) {
            sampling.put("frequencyPenalty", params.optDouble("frequency_penalty"))
            changed = true
        }
        return if (changed) sampling.toString() else null
    }

    companion object {
        private const val DEFAULT_MAX_TOKENS = 512
    }
}
