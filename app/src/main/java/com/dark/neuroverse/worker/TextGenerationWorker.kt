package com.dark.neuroverse.worker

import android.content.Context
import android.util.Log
import com.dark.ai_module.workers.ModelManager
import com.dark.neuroverse.model.ChatUiState
import com.dark.neuroverse.model.DecodeType
import com.dark.neuroverse.model.DecodingMetrics
import com.dark.neuroverse.model.Message
import com.dark.neuroverse.model.Role
import com.dark.neuroverse.model.StreamingState
import com.dark.neuroverse.util.extractPureJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import org.json.JSONObject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Manages text generation, streaming, and token processing.
 * Handles batched UI updates, reasoning pattern extraction, and metrics collection.
 */
object TextGenerationWorker {

    private const val TAG = "TextGenerationWorker"
    private const val BATCH_INTERVAL_MS = 300L
    private const val MAX_THINK_DISPLAY_CHARS = 16_000
    private const val MAX_THOUGHT_SAVE_CHARS = 6_000
    private const val MAX_CONCURRENT_OPERATIONS = 3

    // Coroutine management
    private val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val operationSemaphore = Semaphore(MAX_CONCURRENT_OPERATIONS)
    private var currentGenerationJob: Job? = null
    private var batchingJob: Job? = null

    // State flows
    private val _currentMsgId = MutableStateFlow("")
    val currentMsgId: StateFlow<String> = _currentMsgId.asStateFlow()

    private val _lastDecodingMs = MutableStateFlow<Long?>(null)
    val lastDecodingMs: StateFlow<Long?> = _lastDecodingMs.asStateFlow()

    private val _decodingMetrics = MutableSharedFlow<DecodingMetrics>(extraBufferCapacity = 8)
    val decodingMetrics: SharedFlow<DecodingMetrics> = _decodingMetrics.asSharedFlow()

    // Streaming state
    private var currentStreamingState: StreamingState? = null

    /**
     * Main streaming and rendering function.
     * Handles token-by-token generation with batched UI updates.
     */
    suspend fun streamAndRender(
        prompt: String,
        appContext: Context,
        enableTools: Boolean,
        messageId: String,
        isRegeneration: Boolean = false,
        existingMessages: List<Message>
    ) {
        val startTimeNs = System.nanoTime()
        var firstTokenReceived = false
        _currentMsgId.value = messageId

        UIStateManager.setStateDecoding(messageId, startTimeNs)
        currentStreamingState = StreamingState(messageId = messageId)
        startBatchedUIUpdates(messageId)

        suspend fun finalizeMessage(text: String, thought: String?) {
            batchingJob?.cancel()

            val finalThought = thought?.take(MAX_THOUGHT_SAVE_CHARS)
            ChatManager.updateStreamingMessage(messageId, text, finalThought, isFinal = true)

            // Generate title if no thought (normal conversation)
            ChatManager.generateTitleIfNeeded(useAI = finalThought == null)

            UserDataManager.refreshChatListFromDisk {
                Log.e(TAG, "Error refreshing chat list")
            }

            currentStreamingState = null
        }

        try {
            val fullPrompt = buildFullPrompt(prompt, existingMessages)
            val toolJson = if (enableTools) {
                ToolCallingManager.toolDefinitionBuilder(
                    ToolCallingManager.getSelectedTool()
                ).toString()
            } else ""

            ModelManager.generateStreaming(
                prompt = fullPrompt,
                toolJson = toolJson,
                onToken = { token ->
                    if (!firstTokenReceived) {
                        firstTokenReceived = true
                        val firstTokenTimeNs = System.nanoTime()
                        emitDecodingMetrics(
                            type = if (isRegeneration) DecodeType.REGENERATE else DecodeType.NORMAL,
                            startTimeNs = startTimeNs,
                            firstTokenTimeNs = firstTokenTimeNs,
                            messageId = messageId
                        )
                        UIStateManager.setStateGenerating(messageId, isFirstToken = true)
                    }

                    currentStreamingState?.let { state ->
                        addTokenToBatch(token, state)
                    }
                },
                onToolCalled = { toolName, argsJson ->
                    handleToolExecution(appContext, toolName, argsJson, messageId)
                })

            // Process final output
            currentStreamingState?.let { state ->
                val (finalText, finalThought) = processReasoningPatterns(
                    state.rawBuffer.toString(),
                    state.visibleBuffer.toString(),
                    state.thoughtBuffer.toString()
                )
                finalizeMessage(finalText, finalThought)
            }

        } catch (e: CancellationException) {
            Log.d(TAG, "Streaming cancelled")
            batchingJob?.cancel()
            currentStreamingState?.let { state ->
                finalizeMessage(
                    state.visibleBuffer.toString(), state.thoughtBuffer.toString().ifBlank { null })
            }
            throw e
        } catch (e: Exception) {
            UIStateManager.setStateError("Streaming failed", cause = e)
            batchingJob?.cancel()
            currentStreamingState?.let { state ->
                finalizeMessage(
                    state.visibleBuffer.toString(), state.thoughtBuffer.toString().ifBlank { null })
            }
        } finally {
            UserDataManager.refreshChatListFromDisk {
                Log.e(TAG, "Error refreshing chat list")
            }
            _currentMsgId.value = ""
            if (UIStateManager.uiState.value !is ChatUiState.ExecutingTool) {
                UIStateManager.setStateIdle()
            }
        }
    }

    /**
     * Handles tool execution during streaming.
     */
    private fun handleToolExecution(
        appContext: Context, toolName: String, argsJson: String, messageId: String
    ) {
        workerScope.launch {
            try {
                UIStateManager.setStateExecutingTool(toolName, messageId)

                ToolCallingManager.executeTool(
                    appContext, toolName, argsJson
                ) { result ->
                    workerScope.launch {
                        try {
                            if (result.has("error")) {
                                val errorMsg = result.getString("error")
                                Log.e(TAG, "Tool execution error: $errorMsg")

                                ChatManager.updateStreamingMessage(
                                    messageId = messageId,
                                    text = "",
                                    toolError = errorMsg,
                                    isFinal = true
                                )
                            } else {
                                Log.d(TAG, "Tool executed successfully")
                                // Tool preview already updated in ToolCallingManager
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing tool result", e)
                            ChatManager.updateStreamingMessage(
                                messageId = messageId,
                                text = "",
                                toolError = e.message ?: "Unknown error",
                                isFinal = true
                            )
                        } finally {
                            UIStateManager.setStateIdle()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Tool execution failed", e)
                ChatManager.updateStreamingMessage(
                    messageId = messageId,
                    text = "",
                    toolError = e.message ?: "Tool execution failed",
                    isFinal = true
                )
                UIStateManager.setStateIdle()
            }
        }
    }

    /**
     * Builds full prompt with conversation history.
     */
    fun buildFullPrompt(prompt: String, existingMessages: List<Message>): String {
        val conversationHistory =
            existingMessages.filter { it.role == Role.User || it.role == Role.Assistant }
                .joinToString("\n") { "${it.role.name}: ${it.text}" }

        return buildString {
            if (conversationHistory.isNotBlank()) {
                appendLine("Conversation History:")
                appendLine(conversationHistory)
                appendLine()
            }
            append("User: $prompt")
        }
    }

    /**
     * Adds token to appropriate buffer based on thinking tags.
     */
    fun addTokenToBatch(token: String, state: StreamingState) {
        state.rawBuffer.append(token)

        val lowerToken = token.lowercase()
        when {
            state.inThinkTag && lowerToken.contains("</think>") -> {
                val beforeEnd = token.substringBefore("</think>")
                val afterEnd = token.substringAfter("</think>")
                state.thoughtBuffer.append(beforeEnd)
                state.inThinkTag = false
                state.visibleBuffer.append(afterEnd)
            }

            state.inThinkTag -> {
                state.thoughtBuffer.append(token)
            }

            lowerToken.contains("<think>") -> {
                val beforeStart = token.substringBefore("<think>")
                val afterStart = token.substringAfter("<think>")
                state.visibleBuffer.append(beforeStart)
                state.inThinkTag = true
                state.thoughtBuffer.append(afterStart)
            }

            else -> {
                state.visibleBuffer.append(token)
            }
        }
    }

    /**
     * Starts batched UI updates to reduce rendering overhead.
     */
    private fun startBatchedUIUpdates(messageId: String) {
        batchingJob = workerScope.launch {
            while (currentCoroutineContext().isActive) {
                delay(BATCH_INTERVAL_MS)

                currentStreamingState?.let { state ->
                    val visibleText = state.visibleBuffer.toString()
                    val thinkingText = if (state.thoughtBuffer.isNotEmpty()) {
                        state.thoughtBuffer.toString().takeLast(MAX_THINK_DISPLAY_CHARS)
                    } else null

                    if (visibleText.isNotEmpty() || !thinkingText.isNullOrEmpty()) {
                        ChatManager.updateStreamingMessage(messageId, visibleText, thinkingText)
                    }
                }
            }
        }
    }

    /**
     * Processes various reasoning patterns (JSON, <think> tags, natural language).
     */
    fun processReasoningPatterns(
        rawText: String, visibleText: String, thoughtText: String
    ): Pair<String, String?> {
        // Try JSON format first
        runCatching {
            val json = extractPureJson(rawText)
            val obj = JSONObject(json)
            val final = obj.optString("final", obj.optString("answer", ""))
            val thought = obj.optString("thought", obj.optString("reasoning", ""))
            if (final.isNotBlank() || thought.isNotBlank()) {
                return final to thought.takeIf { it.isNotBlank() }
            }
        }

        // Try <think> tags
        val thinkRegex = Regex("(?is)<think>(.*?)</think>")
        val thinkMatch = thinkRegex.find(rawText)
        if (thinkMatch != null) {
            val extractedThought = thinkMatch.groupValues[1].trim()
            val cleanedVisible = rawText.replace(thinkRegex, "").trim()
            return cleanedVisible to extractedThought
        }

        // Try natural language reasoning pattern
        val reasoningRegex =
            Regex("(?is)(?:reasoning|thoughts?)\\s*:\\s*(.+?)\\s*(?:final|answer)\\s*:\\s*(.+)")
        reasoningRegex.find(rawText)?.let { match ->
            val thought = match.groupValues[1].trim()
            val answer = match.groupValues[2].trim()
            return answer to thought
        }

        // Fallback to raw buffers
        return visibleText to thoughtText.takeIf { it.isNotBlank() }
    }

    /**
     * Emits decoding performance metrics.
     */
    fun emitDecodingMetrics(
        type: DecodeType, startTimeNs: Long, firstTokenTimeNs: Long, messageId: String
    ) {
        val durationMs = (firstTokenTimeNs - startTimeNs) / 1_000_000
        _lastDecodingMs.value = durationMs

        val metrics = DecodingMetrics(
            type = type,
            chatId = ChatManager.currentChatId.value,
            modelId = messageId,
            startedAtNs = startTimeNs,
            firstTokenAtNs = firstTokenTimeNs,
            durationMs = durationMs
        )

        _decodingMetrics.tryEmit(metrics)
    }

    /**
     * Stops current generation and cleans up resources.
     */
    fun stopGeneration() {
        currentGenerationJob?.cancel()
        batchingJob?.cancel()
        ModelManager.stopGeneration()

        // Handle incomplete messages
        val currentMsgId = _currentMsgId.value
        if (currentMsgId.isNotEmpty()) {
            val currentMessage = ChatManager.getCurrentMessageById(currentMsgId)

            if (currentMessage?.role == Role.Tool) {
                ChatManager.updateStreamingMessage(
                    messageId = currentMsgId,
                    text = "Generation cancelled by user",
                    toolError = "Generation cancelled by user",
                    isFinal = true
                )
            }
        }

        currentStreamingState = null
        UIStateManager.setStateIdle()
    }

    /**
     * Cleans up resources.
     */
    fun cleanup() {
        currentGenerationJob?.cancel()
        batchingJob?.cancel()
        currentStreamingState = null
    }
}