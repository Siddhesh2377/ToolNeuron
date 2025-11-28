package com.dark.tool_neuron.worker

import android.content.Context
import android.util.Log
import com.dark.ai_module.model.GenerationParams
import com.dark.ai_module.workers.ModelManager
import com.dark.tool_neuron.logger.AppLogger
import com.dark.tool_neuron.model.ChatUiState
import com.dark.tool_neuron.model.CodeCanvas
import com.dark.tool_neuron.model.DecodeType
import com.dark.tool_neuron.model.DecodingMetrics
import com.dark.tool_neuron.model.DecodingStage
import com.dark.tool_neuron.model.Message
import com.dark.tool_neuron.model.Role
import com.dark.tool_neuron.model.StreamingState
import com.dark.tool_neuron.util.extractPureJson
import com.mp.data_hub_lib.model.RagResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    private var currentGenerationJob: Job? = null
    private var batchingJob: Job? = null

    // State flows
    private val _currentMsgId = MutableStateFlow("")
    val currentMsgId: StateFlow<String> = _currentMsgId.asStateFlow()

    private val _lastDecodingMs = MutableStateFlow<Long?>(null)
    val lastDecodingMs: StateFlow<Long?> = _lastDecodingMs.asStateFlow()

    private val _decodingMetrics = MutableStateFlow(DecodingMetrics())
    val decodingMetrics: StateFlow<DecodingMetrics> = _decodingMetrics.asStateFlow()

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
        existingMessages: List<Message>,
        ragResult: RagResult? = null,
        onToolExecution: (String) -> Unit = {}
    ) {
        val startTimeNs = System.nanoTime()
        var firstTokenReceived = false
        var totalTokens = 0
        _currentMsgId.value = messageId

        val root = UserDataManager.getRootNode()

        // Log generation start
        AppLogger.info(
            root = root,
            message = "Text generation started",
            details = mapOf(
                "messageId" to messageId,
                "isRegeneration" to isRegeneration,
                "enableTools" to enableTools,
                "promptLength" to prompt.length,
                "historyCount" to existingMessages.size
            )
        )

        // STAGE 1: Preparing Prompt
        UIStateManager.setStateDecodingStage(
            messageId,
            DecodingStage.PreparingPrompt,
            startTimeNs
        )

        if (enableTools) {
            UIStateManager.setStateDecodingTool()
            AppLogger.info(root, "Tools enabled for generation")
        }

        currentStreamingState = StreamingState(messageId = messageId)

        suspend fun finalizeMessage(text: String, thought: String?) {
            batchingJob?.cancel()

            // STAGE 5: Rendering
            UIStateManager.updateDecodingStage(DecodingStage.Rendering)
            delay(100)

            val finalThought = thought?.take(MAX_THOUGHT_SAVE_CHARS)
            val codeCanvases = extractCodeCanvases(text)

            ChatManager.updateStreamingMessage(
                messageId = messageId,
                text = text,
                thought = finalThought,
                isFinal = true,
                ragResult = ragResult,
                codeCanvas = codeCanvases
            )

            // Log completion
            val totalDurationMs = (System.nanoTime() - startTimeNs) / 1_000_000
            AppLogger.info(
                root = root,
                message = "Text generation completed",
                details = mapOf(
                    "messageId" to messageId,
                    "totalDurationMs" to totalDurationMs,
                    "totalTokens" to totalTokens,
                    "outputLength" to text.length,
                    "hasThought" to (finalThought != null),
                    "codeBlocks" to codeCanvases.size,
                    "tokensPerSecond" to if (totalDurationMs > 0) {
                        String.format("%.2f", (totalTokens * 1000.0) / totalDurationMs)
                    } else "N/A"
                )
            )

            // Launch off critical path
            CoroutineScope(Dispatchers.IO).launch {
                ChatManager.generateTitleIfNeeded(useAI = finalThought == null)
                UserDataManager.refreshChatListFromDisk {
                    Log.e(TAG, "Error refreshing chat list")
                }
            }

            currentStreamingState = null
        }

        try {
            // STAGE 2: Encoding Input
            UIStateManager.updateDecodingStage(DecodingStage.EncodingInput)
            delay(50)

            val fullPrompt = buildFullPrompt(prompt, existingMessages)
            val toolJson = if (enableTools) {
                ToolCallingManager.toolDefinitionBuilder(
                    ToolCallingManager.getSelectedTool()
                ).toString()
            } else ""

            Log.d("TextGenerationWorker", "Tool JSON : $toolJson")

            // STAGE 3: Loading Model (if not already loaded)
            if (!ModelManager.isModelLoaded()) {
                UIStateManager.updateDecodingStage(DecodingStage.LoadingModel)
                AppLogger.info(root, "Loading model for generation")
            }

            // STAGE 4: Decoding
            UIStateManager.updateDecodingStage(DecodingStage.Decoding)
            startBatchedUIUpdates(messageId)

            ModelManager.generateStreaming(
                prompt = fullPrompt,
                gen = GenerationParams(
                    maxTokens = ModelManager.currentModel.value.maxTokens
                ),
                toolJson = toolJson,
                onToken = { token ->
                    totalTokens++

                    if (!firstTokenReceived) {
                        firstTokenReceived = true
                        val firstTokenTimeNs = System.nanoTime()
                        val ttftMs = (firstTokenTimeNs - startTimeNs) / 1_000_000

                        emitDecodingMetrics(
                            type = if (isRegeneration) DecodeType.REGENERATE else DecodeType.NORMAL,
                            startTimeNs = startTimeNs,
                            firstTokenTimeNs = firstTokenTimeNs,
                            messageId = messageId
                        )

                        AppLogger.info(
                            root = root,
                            message = "First token received",
                            details = mapOf(
                                "messageId" to messageId,
                                "ttftMs" to ttftMs
                            )
                        )

                        UIStateManager.setStateGenerating(messageId, isFirstToken = true)
                    }

                    currentStreamingState?.let { state ->
                        addTokenToBatch(token, state)
                    }
                },
                onToolCalled = { toolName, argsJson ->
                    AppLogger.info(
                        root = root,
                        message = "Tool called during generation",
                        details = mapOf(
                            "messageId" to messageId,
                            "toolName" to toolName,
                            "argsLength" to argsJson.length
                        )
                    )
                    handleToolExecution(appContext, toolName, argsJson, messageId, onToolExecution)
                }
            )

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

            AppLogger.warn(
                root = root,
                message = "Text generation cancelled",
                details = mapOf(
                    "messageId" to messageId,
                    "tokensGenerated" to totalTokens,
                    "partialLength" to (currentStreamingState?.visibleBuffer?.length ?: 0)
                )
            )

            batchingJob?.cancel()
            currentStreamingState?.let { state ->
                finalizeMessage(
                    state.visibleBuffer.toString(),
                    state.thoughtBuffer.toString().ifBlank { null }
                )
            }
            throw e

        } catch (e: Exception) {
            Log.e(TAG, "Streaming failed", e)

            AppLogger.error(
                root = root,
                message = "Text generation failed",
                details = mapOf(
                    "messageId" to messageId,
                    "error" to (e.message ?: "Unknown error"),
                    "errorType" to e.javaClass.simpleName,
                    "tokensGenerated" to totalTokens
                )
            )

            UIStateManager.setStateError("Streaming failed", cause = e)
            batchingJob?.cancel()
            currentStreamingState?.let { state ->
                finalizeMessage(
                    state.visibleBuffer.toString(),
                    state.thoughtBuffer.toString().ifBlank { null }
                )
            }
        } finally {
            CoroutineScope(Dispatchers.IO).launch {
                UserDataManager.refreshChatListFromDisk {
                    Log.e(TAG, "Error refreshing chat list")
                }
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
        appContext: Context,
        toolName: String,
        argsJson: String,
        messageId: String,
        onToolExecution: (String) -> Unit
    ) {
        val root = UserDataManager.getRootNode()

        workerScope.launch {
            try {
                ToolCallingManager.executeTool(
                    appContext, toolName, argsJson
                ) { result ->
                    workerScope.launch {
                        try {
                            if (result.has("error")) {
                                val errorMsg = result.getString("error")
                                Log.e(TAG, "Tool execution error: $errorMsg")

                                AppLogger.error(
                                    root = root,
                                    message = "Tool execution error",
                                    details = mapOf(
                                        "messageId" to messageId,
                                        "toolName" to toolName,
                                        "error" to errorMsg
                                    )
                                )

                                ChatManager.updateStreamingMessage(
                                    messageId = messageId,
                                    text = "",
                                    toolError = errorMsg,
                                    isFinal = true
                                )
                                onToolExecution("error")
                            } else {
                                UIStateManager.setStateExecutingTool(toolName, messageId)
                                Log.d(TAG, "Tool executed successfully")

                                AppLogger.info(
                                    root = root,
                                    message = "Tool executed successfully",
                                    details = mapOf(
                                        "messageId" to messageId,
                                        "toolName" to toolName
                                    )
                                )

                                onToolExecution("success")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing tool result", e)

                            AppLogger.error(
                                root = root,
                                message = "Error processing tool result",
                                details = mapOf(
                                    "messageId" to messageId,
                                    "toolName" to toolName,
                                    "error" to (e.message ?: "Unknown error"),
                                    "errorType" to e.javaClass.simpleName
                                )
                            )

                            ChatManager.updateStreamingMessage(
                                messageId = messageId,
                                text = "",
                                toolError = e.message ?: "Unknown error",
                                isFinal = true
                            )
                            onToolExecution("error")
                        } finally {
                            UIStateManager.setStateIdle()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Tool execution failed", e)

                AppLogger.error(
                    root = root,
                    message = "Tool execution failed",
                    details = mapOf(
                        "messageId" to messageId,
                        "toolName" to toolName,
                        "error" to (e.message ?: "Unknown error"),
                        "errorType" to e.javaClass.simpleName
                    )
                )

                ChatManager.updateStreamingMessage(
                    messageId = messageId,
                    text = "",
                    toolError = e.message ?: "Tool execution failed",
                    isFinal = true
                )
                UIStateManager.setStateError(e.message ?: "Tool execution failed")
            } finally {
                UIStateManager.setStateIdle()
            }
        }
    }

    /**
     * Builds full prompt with conversation history.
     */
    fun buildFullPrompt(prompt: String, existingMessages: List<Message>): String {
        // Filter normal conversation messages (ignore code for now)
        val conversationHistory =
            existingMessages.filter { it.role == Role.User || it.role == Role.Assistant }
                .joinToString("\n") { "${it.role.name}: ${it.text}" }

        // Get the last AI-generated code if any
        val lastAICode =
            existingMessages.lastOrNull { it.role == Role.Assistant && it.codeCanvas?.isNotEmpty() == true }?.codeCanvas?.joinToString(
                "\n"
            ) { it.code }

        return buildString {
            if (conversationHistory.isNotBlank()) {
                appendLine("Conversation History:")
                appendLine(conversationHistory)
                appendLine()
            }

            if (!lastAICode.isNullOrBlank()) {
                appendLine("Latest AI Code:")
                appendLine(lastAICode)
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
            modelName = ModelManager.currentModel.value.modelName,
            startedAtNs = startTimeNs,
            firstTokenAtNs = firstTokenTimeNs,
            durationMs = durationMs
        )

        _decodingMetrics.tryEmit(metrics)
    }

    /**
     * Extracts code blocks from a text and converts them into CodeCanvas objects.
     */
    private fun extractCodeCanvases(input: String): List<CodeCanvas> {
        val codeRegex = Regex("(?s)```(\\w+)?\\n(.*?)```")
        return codeRegex.findAll(input).map { match ->
            val lang = match.groups[1]?.value?.trim()
            val code = match.groups[2]?.value?.trim() ?: ""
            CodeCanvas(code = code, language = lang ?: "text")
        }.toList()
    }

    /**
     * Stops current generation and cleans up resources.
     */
    fun stopGeneration() {
        val root = UserDataManager.getRootNode()
        val currentMsgId = _currentMsgId.value

        AppLogger.info(
            root = root,
            message = "Generation stop requested",
            details = mapOf("messageId" to currentMsgId)
        )

        currentGenerationJob?.cancel()
        batchingJob?.cancel()
        ModelManager.stopGeneration()

        // Handle incomplete messages
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