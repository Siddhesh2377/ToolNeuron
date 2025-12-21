package com.mp.ai_engine.workers.model.internal_model_worker

import android.content.Context
import com.mp.ai_engine.env.SystemEnv
import com.mp.ai_engine.models.llm_models.OpenRouterDatabaseModel
import com.mp.ai_engine.models.llm_tasks.OpenRouterTask
import com.mp.ai_engine.workers.model.SuperModelWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class OpenRouterWorker :
    SuperModelWorker<Pair<OpenRouterDatabaseModel, Context>, OpenRouterTask>() {

    private lateinit var currentModel: OpenRouterDatabaseModel
    private var apiKey: String = ""

    private val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS).build()

    override suspend fun loadModel(modelData: Pair<OpenRouterDatabaseModel, Context>): Result<String> {
        currentModel = modelData.first
        apiKey = SystemEnv.getOpenRouterApiKey(modelData.second).first()
        return Result.success("Model loaded successfully")
    }

    override suspend fun runTask(task: OpenRouterTask) = withContext(Dispatchers.IO) {
        val buffer = StringBuilder()

        if (apiKey == "") {
            task.result.completeExceptionally(RuntimeException("API key not set"))
            return@withContext
        }

        try {
            val requestBody = buildRequestBody(task)
            val request = Request.Builder().url(currentModel.endpoint)
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("HTTP-Referer", "https://github.com/Siddhesh2377/ToolNeuron")
                .addHeader("X-Title", "ToolNeuron").build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                val error = response.body.string()
                task.result.completeExceptionally(RuntimeException("API error: ${response.code} - $error"))
                return@withContext
            }

            response.body.source().use { source ->
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: continue

                    if (line.startsWith("data: ")) {
                        val data = line.substring(6).trim()

                        if (data == "[DONE]") break

                        try {
                            val json = JSONObject(data)
                            val choices = json.optJSONArray("choices") ?: continue

                            if (choices.length() > 0) {
                                val choice = choices.getJSONObject(0)
                                val delta = choice.optJSONObject("delta")

                                delta?.let {
                                    val content = it.optString("content", "")
                                    if (content.isNotEmpty()) {
                                        buffer.append(content)
                                        task.events.onToken(content)
                                    }

                                    val toolCalls = it.optJSONArray("tool_calls")
                                    if (toolCalls != null && toolCalls.length() > 0) {
                                        handleToolCalls(toolCalls, task)
                                    }
                                }

                                val finishReason = choice.optString("finish_reason", "")
                                if (finishReason == "tool_calls") {
                                    choice.optJSONObject("message")?.optJSONArray("tool_calls")
                                        ?.let { calls ->
                                            handleToolCalls(calls, task)
                                        }
                                }
                            }
                        } catch (e: Exception) {
                            task.result.completeExceptionally(e)
                        }
                    }
                }
            }

            task.result.complete(buffer.toString())

        } catch (e: Throwable) {
            task.result.completeExceptionally(e)
        }
    }

    private fun buildRequestBody(task: OpenRouterTask): String {
        return JSONObject().apply {
            put("model", currentModel.modelId)
            put("stream", true)
            put("max_tokens", task.maxTokens)
            put("temperature", currentModel.temperature)
            put("top_p", currentModel.topP)
            put("frequency_penalty", currentModel.frequencyPenalty)
            put("presence_penalty", currentModel.presencePenalty)

            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", task.input)
                })
            })
        }.toString()
    }

    private fun handleToolCalls(toolCalls: JSONArray, task: OpenRouterTask) {
        for (i in 0 until toolCalls.length()) {
            try {
                val toolCall = toolCalls.getJSONObject(i)
                val function = toolCall.optJSONObject("function") ?: continue

                val name = function.optString("name", "")
                val args = function.optString("arguments", "{}")

                if (name.isNotEmpty()) {
                    task.events.onTool(name, args)
                }
            } catch (e: Exception) {
                // Skip malformed tool calls
            }
        }
    }

    override fun unloadModel() {
        client.dispatcher.cancelAll()
    }
}