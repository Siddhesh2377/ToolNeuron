package com.mp.n_apps.agent

import android.util.Log
import com.mp.n_apps.network.LLMClient
import com.mp.n_apps.vcs.NAppVersionControl
import com.mp.n_apps.workspace.ProjectFiles
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.json.JSONArray
import org.json.JSONObject

class NAppToolAgent(
    private val getCurrentFiles: () -> ProjectFiles,
    private val updateFiles: (ProjectFiles) -> Unit,
    private val vcs: NAppVersionControl?,
    private val onToolExecuted: ((ToolLogEntry) -> Unit)? = null,
    private val onProgressUpdate: ((AgentProgress) -> Unit)? = null
) {
    companion object {
        private const val TAG = "NAppToolAgent"
        private const val DEFAULT_MAX_ITERATIONS = 10
        private const val MAX_HISTORY_MESSAGES = 60
    }

    private val executor = ToolExecutor(getCurrentFiles, updateFiles, vcs)
    private val tools = NativeToolDefinitions.buildToolsArray()
    private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }
    private val conversationHistory = mutableListOf<JSONObject>()

    suspend fun runAgentLoop(
        apiKey: String,
        baseUrl: String,
        model: String,
        userMessage: String,
        maxIterations: Int = DEFAULT_MAX_ITERATIONS
    ): AgentLoopResult {
        val toolLog = mutableListOf<ToolLogEntry>()
        var iteration = 0

        conversationHistory.add(JSONObject().apply {
            put("role", "user")
            put("content", userMessage)
        })
        trimHistory()

        onProgressUpdate?.invoke(AgentProgress(0, maxIterations, "Starting..."))

        while (iteration < maxIterations) {
            iteration++

            onProgressUpdate?.invoke(
                AgentProgress(iteration, maxIterations, "Calling AI (step $iteration)...")
            )

            // Build messages: system + history
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", ToolSystemPrompt.SYSTEM_PROMPT)
                })
                conversationHistory.forEach { put(it) }
            }

            val apiResult = LLMClient.chatCompletion(
                apiKey = apiKey,
                baseUrl = baseUrl,
                model = model,
                messages = messages,
                tools = tools,
                maxTokens = 4096,
                temperature = 0.7f
            )

            val response = apiResult.getOrElse { error ->
                Log.e(TAG, "API call failed at iteration $iteration", error)
                val isRateLimit = error.message?.contains("429") == true || error.message?.contains("Rate limit") == true
                onProgressUpdate?.invoke(AgentProgress(iteration, maxIterations, if (isRateLimit) "Rate limited" else "Error"))
                return AgentLoopResult(
                    finalText = "Error: ${error.message}",
                    toolLog = toolLog,
                    totalIterations = iteration,
                    success = false,
                    error = error.message
                )
            }

            Log.d(TAG, "Iteration $iteration: ${response.toolCalls.size} tool calls, content=${response.content?.take(100)} (${response.usage?.totalTokens} tokens)")

            if (response.toolCalls.isEmpty()) {
                // No tool calls — final text response
                val finalText = response.content?.ifBlank { "Done." } ?: "Done."

                conversationHistory.add(JSONObject().apply {
                    put("role", "assistant")
                    put("content", finalText)
                })
                trimHistory()

                onProgressUpdate?.invoke(AgentProgress(iteration, maxIterations, "Done"))

                return AgentLoopResult(
                    finalText = finalText,
                    toolLog = toolLog,
                    totalIterations = iteration,
                    success = true
                )
            }

            // Model wants to call tools — add assistant message with tool_calls to history
            onProgressUpdate?.invoke(
                AgentProgress(iteration, maxIterations, "Executing ${response.toolCalls.size} tool(s)...")
            )

            conversationHistory.add(JSONObject().apply {
                put("role", "assistant")
                put("content", JSONObject.NULL)
                put("tool_calls", JSONArray().apply {
                    for (tc in response.toolCalls) {
                        put(JSONObject().apply {
                            put("id", tc.id)
                            put("type", "function")
                            put("function", JSONObject().apply {
                                put("name", tc.name)
                                put("arguments", tc.arguments)
                            })
                        })
                    }
                })
            })

            // Execute each tool and add results
            for (tc in response.toolCalls) {
                Log.d(TAG, "Tool: ${tc.name} | args: ${tc.arguments.take(200)}")

                val toolCall = try {
                    val params = jsonParser.parseToJsonElement(tc.arguments).jsonObject
                    ToolCall(name = tc.name, params = params)
                } catch (e: Exception) {
                    Log.e(TAG, "Bad arguments for ${tc.name}", e)
                    conversationHistory.add(JSONObject().apply {
                        put("role", "tool")
                        put("tool_call_id", tc.id)
                        put("content", "Error: Invalid arguments — ${e.message}")
                    })
                    toolLog.add(ToolLogEntry(iteration, tc.name, tc.arguments.take(300), false, "Parse error: ${e.message}"))
                    continue
                }

                val result = executor.execute(toolCall)

                val resultOutput = if (result.success) {
                    when (val data = result.data) {
                        is JsonPrimitive -> data.content
                        null -> "OK"
                        else -> data.toString()
                    }
                } else {
                    "Error: ${result.error ?: "Unknown error"}"
                }

                conversationHistory.add(JSONObject().apply {
                    put("role", "tool")
                    put("tool_call_id", tc.id)
                    put("content", resultOutput)
                })

                val logEntry = ToolLogEntry(
                    iteration = iteration,
                    toolName = tc.name,
                    params = tc.arguments.take(300),
                    success = result.success,
                    resultSummary = resultOutput.take(200)
                )
                toolLog.add(logEntry)
                onToolExecuted?.invoke(logEntry)
            }

            trimHistory()
        }

        // Max iterations reached
        onProgressUpdate?.invoke(AgentProgress(maxIterations, maxIterations, "Max steps reached"))

        return AgentLoopResult(
            finalText = "Agent stopped after $maxIterations iterations.",
            toolLog = toolLog,
            totalIterations = iteration,
            success = true
        )
    }

    fun clearHistory() {
        conversationHistory.clear()
    }

    /**
     * Keep history manageable. Remove oldest items, ensuring we
     * always start with a user message (not orphaned tool results).
     */
    private fun trimHistory() {
        while (conversationHistory.size > MAX_HISTORY_MESSAGES) {
            conversationHistory.removeAt(0)
        }
        while (conversationHistory.isNotEmpty()) {
            val first = conversationHistory[0]
            if (first.optString("role") == "user") break
            conversationHistory.removeAt(0)
        }
    }
}
