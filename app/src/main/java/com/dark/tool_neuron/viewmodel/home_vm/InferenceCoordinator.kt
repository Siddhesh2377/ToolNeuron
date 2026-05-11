package com.dark.tool_neuron.viewmodel.home_vm

import android.app.Application
import android.net.Uri
import android.util.Log
import com.dark.gguf_lib.ImageQuality
import com.dark.tool_neuron.data.AppPreferences
import com.dark.tool_neuron.model.ChatMessage
import com.dark.tool_neuron.model.Citation
import com.dark.tool_neuron.model.MemoryMetrics
import com.dark.tool_neuron.model.TextMetrics
import com.dark.tool_neuron.repo.ChatRepository
import com.dark.tool_neuron.repo.RagAugmentation
import com.dark.tool_neuron.repo.RagChunk
import com.dark.tool_neuron.repo.RagCitationMatcher
import com.dark.tool_neuron.repo.RagManager
import com.dark.tool_neuron.service.inference.InferenceClient
import com.dark.tool_neuron.service.inference.InferenceEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transformWhile
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "InferenceCoordinator"
private const val MAX_TOOL_ITERATIONS = 3
private const val MAX_FOLLOWUPS = 3
private const val ROLE_USER = "user"
private const val ROLE_ASSISTANT = "assistant"
private const val ROLE_TOOL = "tool"
private val THINK_TAGS = listOf(
    "<think>" to "</think>",
    "[THINK]" to "[/THINK]",
    "<reasoning>" to "</reasoning>",
)
private val NEED_MORE_REGEX = Regex("""\[NEED_MORE:\s*(.+?)]""", RegexOption.DOT_MATCHES_ALL)
private const val DEEP_RESEARCH_INSTRUCTION =
    "Instruction: if the context above is insufficient to fully answer the user's question, " +
        "do NOT guess. Instead end your response with exactly one line: " +
        "[NEED_MORE: <a focused query for follow-up retrieval>] and stop. " +
        "If the context is sufficient, answer normally and DO NOT emit any [NEED_MORE] marker."

data class GenerationOutcome(
    val content: String,
    val thinking: String,
    val error: String?,
    val textMetrics: TextMetrics?,
    val memoryMetrics: MemoryMetrics?,
    val citations: List<Citation> = emptyList(),
)

@Singleton
class InferenceCoordinator @Inject constructor(
    private val app: Application,
    private val chatRepo: ChatRepository,
    private val toolCallCoordinator: ToolCallCoordinator,
    private val ragManager: RagManager,
    private val modelSession: ModelSessionManager,
    private val appPrefs: AppPreferences,
) {
    suspend fun run(
        chatId: String,
        onStreamingUpdate: (content: String, thinking: String) -> Unit,
        onToolExecuted: (List<ChatMessage>) -> Unit,
        onMetrics: (TextMetrics?, MemoryMetrics?) -> Unit,
    ): GenerationOutcome {
        val deepResearchEnabled = appPrefs.ragDeepResearch &&
            ragManager.documentsForChat(chatId).isNotEmpty()

        var cleanContent = ""
        var thinkingContent = ""
        var errorMessage: String? = null
        var textMetrics: TextMetrics? = null
        var memoryMetrics: MemoryMetrics? = null
        var ragAugmentation: RagAugmentation = RagAugmentation.NONE
        val toolResults = mutableListOf<Pair<String, Boolean>>()
        val followupBlocks = mutableListOf<String>()
        val followupChunks = mutableListOf<RagChunk>()
        var totalCitedChunks = 0

        researchLoop@ for (round in 0..MAX_FOLLOWUPS) {
            cleanContent = ""
            thinkingContent = ""

            toolLoop@ for (iteration in 0 until MAX_TOOL_ITERATIONS) {
                var rawAssistant = ""
                var pendingToolCall: InferenceEvent.ToolCall? = null

                val rawMessages = chatRepo.getMessages(chatId)
                val messages = if (iteration == 0) {
                    val (msgs, aug) = augmentLastUser(
                        chatId = chatId,
                        messages = rawMessages,
                        followups = followupBlocks,
                        deepResearchEnabled = deepResearchEnabled,
                    )
                    if (round == 0) {
                        ragAugmentation = aug
                        if (aug.chunks.isNotEmpty()) totalCitedChunks = aug.chunks.size
                    }
                    msgs
                } else {
                    rawMessages
                }

                val lastUser = messages.lastOrNull { it.role == ROLE_USER }
                val userImages = lastUser?.imageUris.orEmpty()
                val vlmRoute = iteration == 0 &&
                    round == 0 &&
                    userImages.isNotEmpty() &&
                    InferenceClient.isVlmLoaded.value

                val historyJson = buildMessagesJson(
                    messages = messages,
                    vlmLastUserId = if (vlmRoute) lastUser?.id else null,
                )

                val stream: Flow<InferenceEvent> = if (vlmRoute) {
                    val uris = userImages.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
                    val quality = runCatching { ImageQuality.valueOf(appPrefs.vlmImageQuality) }
                        .getOrDefault(ImageQuality.MEDIUM)
                    InferenceClient.generateVlm(app, historyJson, uris, modelSession.maxTokens.value, quality)
                } else {
                    InferenceClient.generateMultiTurn(historyJson, modelSession.maxTokens.value)
                }

                stream
                    .transformWhile { event ->
                        emit(event)
                        event !is InferenceEvent.Done && event !is InferenceEvent.Error
                    }
                    .collect { event ->
                        when (event) {
                            is InferenceEvent.Token -> {
                                rawAssistant += event.text
                                val (clean, think) = parseThinking(rawAssistant)
                                cleanContent = stripNeedMoreMarker(clean)
                                thinkingContent = think
                                onStreamingUpdate(cleanContent, thinkingContent)
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

            if (errorMessage != null) break@researchLoop
            if (!deepResearchEnabled || round >= MAX_FOLLOWUPS) break@researchLoop

            val rawForMarker = cleanContent
            val followupQuery = extractNeedMoreQuery(rawForMarker)?.trim().orEmpty()
            if (followupQuery.isEmpty()) break@researchLoop

            Log.i(TAG, "deep-research follow-up #${round + 1}: query=\"${followupQuery.take(120)}\"")

            val budget = computeRagBudget(chatRepo.getMessages(chatId))
            val followupAug = ragManager.augment(chatId, followupQuery, "", budget)
            if (!followupAug.didAugment) break@researchLoop

            val startIndex = totalCitedChunks + 1
            val block = buildFollowupBlock(followupQuery, followupAug, startIndex)
            totalCitedChunks = startIndex + followupAug.chunks.size - 1
            followupBlocks += block
            followupChunks += followupAug.chunks
            onStreamingUpdate("", "")
        }

        val firstSuccessId = toolResults.firstOrNull { it.second }?.first
        if (firstSuccessId != null) {
            toolResults
                .filter { it.first != firstSuccessId }
                .forEach { chatRepo.deleteMessage(it.first) }
            onToolExecuted(chatRepo.getMessages(chatId))
        }

        val allChunks = if (followupChunks.isEmpty()) {
            ragAugmentation.chunks
        } else {
            (ragAugmentation.chunks + followupChunks).distinctBy { it.sourceId to it.chunkIndex }
        }
        val citations = if (allChunks.isNotEmpty() && cleanContent.isNotBlank()) {
            RagCitationMatcher.match(cleanContent, allChunks)
        } else {
            emptyList()
        }

        return GenerationOutcome(
            content = cleanContent,
            thinking = thinkingContent,
            error = errorMessage,
            textMetrics = textMetrics,
            memoryMetrics = memoryMetrics,
            citations = citations,
        )
    }

    private suspend fun augmentLastUser(
        chatId: String,
        messages: List<ChatMessage>,
        followups: List<String>,
        deepResearchEnabled: Boolean,
    ): Pair<List<ChatMessage>, RagAugmentation> {
        val lastUserIdx = messages.indexOfLast { it.role == ROLE_USER }
        if (lastUserIdx < 0) return messages to RagAugmentation.NONE
        val original = messages[lastUserIdx]

        val ragReady = ragManager.isReady.value
        val budget = computeRagBudget(messages)
        val aug = if (ragReady) {
            ragManager.augment(chatId, original.content, original.content, budget)
        } else {
            RagAugmentation.NONE
        }

        val ragApplied = aug.didAugment
        if (!ragApplied && followups.isEmpty()) return messages to RagAugmentation.NONE

        val builder = StringBuilder()
        builder.append(if (ragApplied) aug.augmentedPrompt else original.content)
        if (deepResearchEnabled && ragApplied && followups.isEmpty()) {
            builder.append("\n\n").append(DEEP_RESEARCH_INSTRUCTION)
        }
        followups.forEachIndexed { idx, block ->
            builder.append("\n\n")
            builder.append(block)
            val isLast = idx == followups.lastIndex
            if (deepResearchEnabled && !isLast) {
                builder.append("\n\n").append(DEEP_RESEARCH_INSTRUCTION)
            }
        }
        if (followups.isNotEmpty()) {
            builder.append("\n\nNow write the complete answer using all available context. Cite passages inline as [N].")
        }

        val finalContent = builder.toString()
        if (finalContent == original.content) return messages to RagAugmentation.NONE
        Log.i(
            TAG,
            "prompt augmented (rag=$ragApplied, deep=$deepResearchEnabled, followups=${followups.size}, len ${original.content.length} -> ${finalContent.length}, budget=$budget tok)",
        )
        val updated = messages.toMutableList().also {
            it[lastUserIdx] = original.copy(content = finalContent)
        }
        return updated to aug
    }

    private fun buildFollowupBlock(
        followupQuery: String,
        aug: RagAugmentation,
        startIndex: Int,
    ): String = buildString {
        append("Continue answering the user's question. ")
        append("Additional context retrieved for \"")
        append(followupQuery)
        append("\":\n\n<context>\n")
        aug.chunks.forEachIndexed { idx, chunk ->
            append('[')
            append(startIndex + idx)
            append("] ")
            append(chunk.text)
            append("\n\n")
        }
        append("</context>")
    }

    private fun extractNeedMoreQuery(content: String): String? {
        val match = NEED_MORE_REGEX.find(content) ?: return null
        return match.groupValues.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun stripNeedMoreMarker(content: String): String {
        if (!content.contains("[NEED_MORE")) return content
        return NEED_MORE_REGEX.replace(content, "").trimEnd()
    }

    private fun computeRagBudget(messages: List<ChatMessage>): Int {
        val ctx = modelSession.contextSize.value
        val output = modelSession.maxTokens.value
        val historyChars = messages.sumOf { it.content.length + it.thinkingContent.length }
        val historyTokens = (historyChars + 3) / 4
        val safetyMargin = 256
        val available = ctx - output - historyTokens - safetyMargin
        return available.coerceIn(256, 4096)
    }

    private fun buildMessagesJson(
        messages: List<ChatMessage>,
        vlmLastUserId: String? = null,
    ): String {
        val marker = if (vlmLastUserId != null) InferenceClient.getVlmDefaultMarker() else ""
        val arr = JSONArray()
        messages.forEach { msg ->
            when (msg.role) {
                ROLE_USER, ROLE_ASSISTANT, ROLE_TOOL -> {
                    val content = if (msg.id == vlmLastUserId && marker.isNotEmpty()) {
                        if (msg.content.isBlank()) marker else "$marker\n${msg.content}"
                    } else {
                        msg.content
                    }
                    arr.put(JSONObject().apply {
                        put("role", msg.role)
                        put("content", content)
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
            // Current RSS isn't in the per-turn metrics callback (peak only).
            // Pull it from the side channel — one AIDL call per turn end is
            // cheap and gives the live process resident size for the action
            // window display.
            val currentRss = readCurrentRssMb()
            val mem = MemoryMetrics(
                modelSizeMB = o.optDouble("modelSizeMB", 0.0),
                contextSizeMB = o.optDouble("contextSizeMB", 0.0),
                peakMemoryMB = o.optDouble("peakMemoryMB", 0.0),
                currentMemoryMB = currentRss,
                usagePercent = o.optDouble("memoryUsagePercent", 0.0),
            )
            val textValid = text.tokensPerSecond > 0.0 || text.generatedTokens > 0
            val memValid = mem.modelSizeMB > 0.0 || mem.peakMemoryMB > 0.0
            (if (textValid) text else null) to (if (memValid) mem else null)
        } catch (_: Exception) {
            null to null
        }
    }

    private fun readCurrentRssMb(): Double {
        val raw = InferenceClient.getMemoryStatsJson() ?: return 0.0
        return runCatching { JSONObject(raw).optDouble("current_rss_mb", 0.0) }.getOrDefault(0.0)
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
