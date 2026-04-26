package com.dark.tool_neuron.service.server

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
import java.util.concurrent.ConcurrentHashMap

class ServerInferenceBridge(
    private val engine: ServerEngine,
    private val onRequestEvent: (ServerRequestEvent) -> Unit,
) : InferenceBridge() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<Long, Job>()

    override fun startGeneration(genId: Long, messagesJson: String, paramsJson: String): Boolean {
        if (!engine.isLoaded) {
            NativeServer.nativeFeedError(genId, "no model loaded")
            return false
        }
        val params = runCatching { JSONObject(paramsJson) }.getOrDefault(JSONObject())
        val maxTokens = params.optInt("max_tokens", DEFAULT_MAX_TOKENS).coerceAtLeast(1)
        val (systemPrompt, forwardedMessagesJson) = splitSystemMessages(messagesJson)

        buildSamplingJson(params)?.let { engine.setSampling(it) }
        if (systemPrompt.isNotBlank()) engine.setSystemPrompt(systemPrompt)

        val job = scope.launch {
            try {
                engine.generateMultiTurnFlow(forwardedMessagesJson, maxTokens).collect { event ->
                    when (event) {
                        is GenerationEvent.Token -> NativeServer.nativeFeedToken(genId, event.text)
                        is GenerationEvent.Done -> NativeServer.nativeFeedDone(genId, "stop")
                        is GenerationEvent.Error -> NativeServer.nativeFeedError(genId, event.message)
                        else -> Unit
                    }
                }
            } catch (e: Exception) {
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
        engine.stopGeneration()
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
