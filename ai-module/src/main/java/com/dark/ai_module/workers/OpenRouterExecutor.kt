package com.dark.ai_module.workers

import android.util.Log
import com.dark.ai_module.model.GenerationParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * OpenRouterExecutor - Handles streaming generation via OpenRouter API
 * Implements SSE (Server-Sent Events) parsing for token streaming
 */
class OpenRouterExecutor(
    private val apiKey: String,
    private val baseUrl: String = "https://openrouter.ai/api/v1"
) {
    companion object {
        private const val TAG = "OpenRouterExecutor"
        private const val CONNECT_TIMEOUT = 30_000
        private const val READ_TIMEOUT = 60_000
    }

    private val stopRequested = AtomicBoolean(false)
    private var currentConnection: HttpURLConnection? = null

    /**
     * Generate streaming response from OpenRouter
     */
    suspend fun generateStreaming(
        modelId: String,
        prompt: String,
        systemPrompt: String = "You are a helpful assistant.",
        gen: GenerationParams = GenerationParams(),
        toolsJson: String? = null,
        onToken: (String) -> Unit = {},
        onToolCall: (name: String, args: String) -> Unit = { _, _ -> }
    ): Result<String> = withContext(Dispatchers.IO) {
        stopRequested.set(false)
        val fullResponse = StringBuilder()

        try {
            val url = URL("$baseUrl/chat/completions")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("HTTP-Referer", "https://github.com/Siddhesh2377/ToolNeuron")
                setRequestProperty("X-Title", "ToolNeuron")
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                doOutput = true
            }

            currentConnection = connection

            // Build request body
            val requestBody = buildRequestBody(
                modelId = modelId,
                systemPrompt = systemPrompt,
                userPrompt = prompt,
                maxTokens = gen.maxTokens,
                toolsJson = toolsJson
            )

            // Send request
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(requestBody)
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                val errorStream = connection.errorStream?.bufferedReader()?.readText()
                Log.e(TAG, "OpenRouter error $responseCode: $errorStream")
                return@withContext Result.failure(
                    RuntimeException("OpenRouter API error: $responseCode - $errorStream")
                )
            }

            // Parse SSE stream
            BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                var line: String?
                var currentEvent = StringBuilder()

                while (reader.readLine().also { line = it } != null) {
                    if (stopRequested.get()) {
                        Log.i(TAG, "Generation stopped by user")
                        break
                    }

                    val currentLine = line ?: continue

                    when {
                        currentLine.startsWith("data: ") -> {
                            val data = currentLine.substring(6).trim()

                            if (data == "[DONE]") {
                                break
                            }

                            try {
                                val json = JSONObject(data)
                                val choices = json.optJSONArray("choices")
                                if (choices != null && choices.length() > 0) {
                                    val choice = choices.getJSONObject(0)
                                    val delta = choice.optJSONObject("delta")

                                    if (delta != null) {
                                        // Handle content token
                                        val content = delta.optString("content", "")
                                        if (content.isNotEmpty()) {
                                            onToken(content)
                                            fullResponse.append(content)
                                        }

                                        // Handle tool calls
                                        val toolCalls = delta.optJSONArray("tool_calls")
                                        if (toolCalls != null && toolCalls.length() > 0) {
                                            handleToolCalls(toolCalls, onToolCall)
                                        }
                                    }

                                    // Check for finish reason
                                    val finishReason = choice.optString("finish_reason", "")
                                    if (finishReason == "tool_calls") {
                                        // Tool call complete - extract from message
                                        val message = choice.optJSONObject("message")
                                        message?.optJSONArray("tool_calls")?.let { calls ->
                                            handleToolCalls(calls, onToolCall)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to parse SSE data: $data", e)
                            }
                        }
                        currentLine.isEmpty() -> {
                            // Empty line signals end of event
                            currentEvent.clear()
                        }
                    }
                }
            }

            connection.disconnect()
            currentConnection = null

            Result.success(fullResponse.toString())

        } catch (e: Exception) {
            Log.e(TAG, "Generation failed", e)
            currentConnection?.disconnect()
            currentConnection = null
            Result.failure(e)
        }
    }

    /**
     * Stop ongoing generation
     */
    fun stopGeneration() {
        stopRequested.set(true)
        currentConnection?.disconnect()
        currentConnection = null
    }

    /**
     * Build OpenRouter API request body
     */
    private fun buildRequestBody(
        modelId: String,
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int,
        toolsJson: String?
    ): String {
        val json = JSONObject().apply {
            put("model", modelId)
            put("stream", true)
            put("max_tokens", maxTokens)

            // Messages array
            val messages = JSONArray().apply {
                // System message
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })

                // User message
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userPrompt)
                })
            }
            put("messages", messages)

            // Tools if provided
            if (!toolsJson.isNullOrBlank()) {
                try {
                    val toolsArray = when {
                        toolsJson.trimStart().startsWith('[') -> JSONArray(toolsJson)
                        toolsJson.trimStart().startsWith('{') -> JSONArray().put(JSONObject(toolsJson))
                        else -> null
                    }
                    toolsArray?.let { put("tools", it) }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse tools JSON", e)
                }
            }
        }

        return json.toString()
    }

    /**
     * Handle tool call responses from API
     */
    private fun handleToolCalls(
        toolCalls: JSONArray,
        onToolCall: (name: String, args: String) -> Unit
    ) {
        for (i in 0 until toolCalls.length()) {
            try {
                val toolCall = toolCalls.getJSONObject(i)
                val function = toolCall.optJSONObject("function") ?: continue

                val name = function.optString("name", "")
                val args = function.optString("arguments", "{}")

                if (name.isNotEmpty()) {
                    onToolCall(name, args)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse tool call", e)
            }
        }
    }
}