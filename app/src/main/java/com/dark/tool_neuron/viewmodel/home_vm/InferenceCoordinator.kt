package com.dark.tool_neuron.viewmodel.home_vm

import android.util.Log
import com.dark.tool_neuron.model.ChatMessage
import com.dark.tool_neuron.model.MemoryMetrics
import com.dark.tool_neuron.model.TextMetrics
import com.dark.tool_neuron.repo.ChatRepository
import com.dark.tool_neuron.service.inference.InferenceClient
import com.dark.tool_neuron.service.inference.InferenceEvent
import kotlinx.coroutines.flow.transformWhile
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "InferenceCoordinator"
private const val MAX_TOKENS = 2048
private const val MAX_TOOL_ITERATIONS = 3
private const val ROLE_USER = "user"
private const val ROLE_ASSISTANT = "assistant"
private const val ROLE_TOOL = "tool"
private val THINK_TAGS = listOf(
    "<think>" to "</think>",
    "[THINK]" to "[/THINK]",
    "<reasoning>" to "</reasoning>",
)

data class GenerationOutcome(
    val content: String,
    val thinking: String,
    val error: String?,
    val textMetrics: TextMetrics?,
    val memoryMetrics: MemoryMetrics?,
)

@Singleton
class InferenceCoordinator @Inject constructor(
    private val chatRepo: ChatRepository,
    private val toolCallCoordinator: ToolCallCoordinator,
) {
    suspend fun run(
        chatId: String,
        onStreamingUpdate: (content: String, thinking: String) -> Unit,
        onToolExecuted: (List<ChatMessage>) -> Unit,
        onMetrics: (TextMetrics?, MemoryMetrics?) -> Unit,
    ): GenerationOutcome {
        var cleanContent = ""
        var thinkingContent = ""
        var errorMessage: String? = null
        var textMetrics: TextMetrics? = null
        var memoryMetrics: MemoryMetrics? = null
        val toolResults = mutableListOf<Pair<String, Boolean>>()

        toolLoop@ for (iteration in 0 until MAX_TOOL_ITERATIONS) {
            var rawAssistant = ""
            var pendingToolCall: InferenceEvent.ToolCall? = null

            val historyJson = buildMessagesJson(chatRepo.getMessages(chatId))

            InferenceClient.generateMultiTurn(historyJson, MAX_TOKENS)
                .transformWhile { event ->
                    emit(event)
                    event !is InferenceEvent.Done && event !is InferenceEvent.Error
                }
                .collect { event ->
                    when (event) {
                        is InferenceEvent.Token -> {
                            rawAssistant += event.text
                            val (clean, think) = parseThinking(rawAssistant)
                            cleanContent = clean
                            thinkingContent = think
                            onStreamingUpdate(clean, think)
                        }
                        is InferenceEvent.ToolCall -> pendingToolCall = event
                        is InferenceEvent.Metrics -> {
                            val parsed = parseMetrics(event.metricsJson)
                            textMetrics = parsed.first
                            memoryMetrics = parsed.second
                            onMetrics(parsed.first, parsed.second)
                        }
                        is InferenceEvent.Error -> errorMessage = event.message
                        else -> {}
                    }
                }

            val tc = pendingToolCall ?: toolCallCoordinator.parseFromText(cleanContent)
            if (tc == null || errorMessage != null) break@toolLoop

            Log.i(TAG, "tool call fired: source=${if (pendingToolCall != null) "native" else "text-parse"} name=${tc.name} args=${tc.argsJson.take(200)}")

            val toolMsg = toolCallCoordinator.execute(chatId, tc)
            val isSuccess = !toolMsg.content.contains("\"error\"")
            toolResults += toolMsg.id to isSuccess
            chatRepo.addMessage(toolMsg)
            onToolExecuted(chatRepo.getMessages(chatId))
            onStreamingUpdate("", "")
            cleanContent = ""
            thinkingContent = ""

            if (isSuccess) {
                InferenceClient.clearTools()
            }
        }

        val firstSuccessId = toolResults.firstOrNull { it.second }?.first
        if (firstSuccessId != null) {
            toolResults
                .filter { it.first != firstSuccessId }
                .forEach { chatRepo.deleteMessage(it.first) }
            onToolExecuted(chatRepo.getMessages(chatId))
        }

        return GenerationOutcome(
            content = cleanContent,
            thinking = thinkingContent,
            error = errorMessage,
            textMetrics = textMetrics,
            memoryMetrics = memoryMetrics,
        )
    }

    private fun buildMessagesJson(messages: List<ChatMessage>): String {
        val arr = JSONArray()
        messages.forEach { msg ->
            when (msg.role) {
                ROLE_USER, ROLE_ASSISTANT, ROLE_TOOL -> {
                    arr.put(JSONObject().apply {
                        put("role", msg.role)
                        put("content", msg.content)
                        if (msg.role == ROLE_TOOL && msg.modelName.isNotEmpty()) {
                            put("name", msg.modelName)
                        }
                    })
                }
            }
        }
        return arr.toString()
    }

    private fun parseMetrics(json: String): Pair<TextMetrics?, MemoryMetrics?> {
        if (json.isBlank()) return null to null
        return try {
            val o = JSONObject(json)
            val text = TextMetrics(
                tokensPerSecond = o.optDouble("tokensPerSecond", 0.0),
                timeToFirstTokenMs = o.optLong("timeToFirstTokenMs", 0L),
                totalTimeMs = o.optLong("totalTimeMs", 0L),
                promptTokens = o.optInt("tokensEvaluated", 0),
                generatedTokens = o.optInt("tokensPredicted", 0),
            )
            val mem = MemoryMetrics(
                modelSizeMB = o.optDouble("modelSizeMB", 0.0),
                contextSizeMB = o.optDouble("contextSizeMB", 0.0),
                peakMemoryMB = o.optDouble("peakMemoryMB", 0.0),
                usagePercent = o.optDouble("memoryUsagePercent", 0.0),
            )
            val textValid = text.tokensPerSecond > 0.0 || text.generatedTokens > 0
            val memValid = mem.modelSizeMB > 0.0 || mem.peakMemoryMB > 0.0
            (if (textValid) text else null) to (if (memValid) mem else null)
        } catch (_: Exception) {
            null to null
        }
    }

    private fun parseThinking(raw: String): Pair<String, String> {
        val content = StringBuilder()
        val thinking = StringBuilder()
        var i = 0
        while (i < raw.length) {
            val next = THINK_TAGS
                .map { (open, _) -> raw.indexOf(open, i) to open }
                .filter { it.first >= 0 }
                .minByOrNull { it.first }
            if (next == null) {
                content.append(raw, i, raw.length)
                break
            }
            val (start, openTag) = next
            content.append(raw, i, start)
            val closeTag = THINK_TAGS.first { it.first == openTag }.second
            val bodyStart = start + openTag.length
            val end = raw.indexOf(closeTag, bodyStart)
            if (end < 0) {
                if (thinking.isNotEmpty()) thinking.append("\n\n")
                thinking.append(raw, bodyStart, raw.length)
                i = raw.length
            } else {
                if (thinking.isNotEmpty()) thinking.append("\n\n")
                thinking.append(raw, bodyStart, end)
                i = end + closeTag.length
            }
        }
        return content.toString().trim() to thinking.toString().trim()
    }
}
