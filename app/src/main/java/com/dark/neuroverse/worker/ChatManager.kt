package com.dark.neuroverse.worker

import android.content.Context
import android.util.Log
import com.dark.ai_module.data.ModelsList
import com.dark.ai_module.workers.ModelManager
import com.dark.neuroverse.BuildConfig
import com.dark.neuroverse.model.ChatList
import com.dark.neuroverse.model.CodeCanvas
import com.dark.neuroverse.model.DecodingMetrics
import com.dark.neuroverse.model.Message
import com.dark.neuroverse.model.Role
import com.dark.neuroverse.model.RunningTool
import com.dark.neuroverse.model.ToolOutput
import com.dark.neuroverse.userdata.addNewChat
import com.dark.neuroverse.userdata.getDefaultChatHistory
import com.dark.neuroverse.userdata.helpers.ModelStateHelper
import com.dark.neuroverse.userdata.ntds.neuron_tree.NeuronNode
import com.dark.neuroverse.userdata.ntds.neuron_tree.NeuronTree
import com.dark.neuroverse.userdata.saveTree
import com.mp.data_hub_lib.model.RagResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Chat Manager
 * - Maintains CRUD operations for chat list
 * - Manages currently loaded chat state
 * - Handles message updates and streaming
 * - Generates chat titles
 */
object ChatManager {
    private const val TAG = "ChatManager"

    // State flows
    private val _chatList = MutableStateFlow<List<ChatList>>(emptyList())
    val chatList: StateFlow<List<ChatList>> = _chatList.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _currentChatID = MutableStateFlow("")
    val currentChatId: StateFlow<String> = _currentChatID.asStateFlow()

    private val _currentChatTitle = MutableStateFlow("")
    val currentChatTitle: StateFlow<String> = _currentChatTitle.asStateFlow()

    // Streaming state
    private var streamingMessageIndex = -1

    /**
     * Refreshes chat list from disk.
     */
    fun refreshChats() {
        UserDataManager.refreshChatListFromDisk(onLoaded = {
            _chatList.value = it
            Log.d(TAG, "Refreshed chat list: ${it.size} chats")
        })
    }

    /**
     * Persist the current chat (create or update) to the encrypted brain file.
     *
     * @param messages   The chat history to write.
     * @param chatTitle  Title shown in the UI.
     * @param chatId     Empty string if this is a brand‑new chat; otherwise the node id of the chat to update.
     * @param rootNode   **Real** root node that is already in memory (`UserDataManager.rootNode.value`).
     * @param appContext Android context – used only to write the file.
     */
    fun saveChat(
        messages: List<Message>,
        chatTitle: String,
        chatId: String,
        rootNode: NeuronNode,
        appContext: Context
    ) {
        try {
            Log.d(TAG, "=== Starting saveChat ===")

            if (messages.isEmpty() && chatTitle.isBlank()) {
                Log.d(TAG, "No content to save – skipping")
                return
            }

            Log.d(TAG, "Messages: $messages")
            Log.d(TAG, "Root node: $rootNode")
            Log.d(TAG, "Saving chat: id=$chatId, title='$chatTitle', messages=${messages.size}")

            val currentTimestamp = System.currentTimeMillis()

            val jsonData = JSONObject().apply {
                put("title", chatTitle)
                put("timestamp", currentTimestamp)
                put(
                    "conversations", JSONArray(
                        Json.encodeToString(
                            kotlinx.serialization.builtins.ListSerializer(Message.serializer()),
                            messages
                        )
                    )
                )
            }

            val chatHistory = getDefaultChatHistory(rootNode)
            val existingNode = if (chatId.isNotBlank()) {
                chatHistory.children.firstOrNull { it.id == chatId }
            } else null

            when {
                existingNode != null -> {
                    Log.d(TAG, "Updating existing chat node: ${existingNode.id}")
                    existingNode.data.content = jsonData.toString()
                }

                messages.isNotEmpty() -> {
                    Log.d(TAG, "Creating new chat node")
                    val newNode = addNewChat(rootNode, jsonData)
                    _currentChatID.value = newNode.id
                    Log.d(TAG, "Root node: $rootNode")
                    Log.d(TAG, "Node Ref: $newNode")
                    Log.d(TAG, "Created new chat with id: ${newNode.id}")
                }
            }

            saveTree(NeuronTree(rootNode), appContext, BuildConfig.ALIAS)
            Log.d(TAG, "Chat saved successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to save chat", e)
            UIStateManager.setStateError("Failed to save chat", cause = e)
        } finally {
            refreshChats()
        }
    }

    /**
     * Creates a new empty chat.
     */
    fun newChat() {
        _currentChatID.value = ""
        _messages.value = emptyList()
        _currentChatTitle.value = ""
        streamingMessageIndex = -1
        UIStateManager.setStateIdle()
        Log.d(TAG, "New chat created")
    }

    /**
     * Deletes a chat by ID.
     */
    suspend fun deleteChatById(id: String, context: Context) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Deleting chat $id")
            val rootNode = UserDataManager.getRootNode()
            val neuronTree = NeuronTree(rootNode)

            // Delete associated model state
            ModelStateHelper.deleteModelState(rootNode, id)

            // Delete chat node
            neuronTree.deleteNodeById(id)
            UserDataManager.performTreeSave(context)
            refreshChats()

            // Clear current chat if it's the one being deleted
            if (currentChatId.value == id) {
                newChat()
            }

            Log.d(TAG, "Chat $id deleted successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete chat $id", e)
            UIStateManager.setStateError("Failed to delete chat", cause = e)
        }
    }

    /**
     * Loads a chat by ID from disk.
     */
    suspend fun loadChatById(id: String) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Loading chat $id")
            UIStateManager.toggleStateChatLoading(true)

            val root = NeuronTree(UserDataManager.getRootNode()).getNodeDirect("root")
            val chatHistory = getDefaultChatHistory(root)
            val node = NeuronTree(chatHistory).getNodeDirect(id)

            if (node.data.content.isBlank()) {
                UIStateManager.setStateError("Chat not found or empty")
                UIStateManager.toggleStateChatLoading(false)
                return@withContext
            }

            val json = JSONObject(node.data.content)
            val title = json.optString("title", "")
            val conversations = Json.decodeFromString<List<Message>>(
                json.getJSONArray("conversations").toString()
            )

            Log.d(TAG, "Chat $id loaded successfully :: $conversations")

            _currentChatTitle.value = title
            _messages.value = conversations
            _currentChatID.value = id
            streamingMessageIndex = -1

            Log.d(TAG, "Chat $id loaded: $title (${conversations.size} messages)")
            UIStateManager.toggleStateChatLoading(false)

        } catch (e: Exception) {
            Log.e(TAG, "Failed loading chat $id", e)
            UIStateManager.setStateError("Failed loading chat", cause = e)
            UIStateManager.toggleStateChatLoading(false)
        }
    }

    /**
     * Adds a new message to the current chat.
     */
    fun addMessage(message: Message) {
        _messages.update { it + message }

        // Track streaming index if it's an assistant/tool message
        if (message.role == Role.Assistant || message.role == Role.Tool) {
            streamingMessageIndex = _messages.value.size - 1
        }

        Log.d(TAG, "Message added: ${message.role} (id: ${message.id})")
    }

    /**
     * Deletes a message by ID.
     */
    fun deleteMessage(messageId: String) {
        _messages.update { messages ->
            messages.filterNot { it.id == messageId }
        }
        Log.d(TAG, "Message deleted: $messageId")
    }

// Update the updateStreamingMessage function in ChatManager.kt

    /**
     * Updates a streaming message with new text/thought.
     * ✅ Improved handling for tool messages and empty text
     */
    fun updateStreamingMessage(
        messageId: String,
        text: String,
        thought: String? = null,
        toolError: String? = null,
        isFinal: Boolean = false,
        ragResult: RagResult? = null,
        codeCanvas: List<CodeCanvas>? = null
    ) {
        _messages.update { messages ->
            messages.mapIndexed { index, message ->
                // Match by ID or streaming index
                if (message.id == messageId || index == streamingMessageIndex) {
                    val finalId = if (isFinal && messageId == "-1") {
                        UUID.randomUUID().toString()
                    } else {
                        message.id
                    }

                    // ✅ Handle tool calling error
                    if (toolError != null && message.role == Role.Tool) {
                        return@mapIndexed message.copy(
                            id = finalId, text = "", // ✅ Don't show "Error: Empty text received"
                            thought = thought, tool = message.tool?.copy(
                                toolPreview = "Error: $toolError", toolOutput = ToolOutput(
                                    pluginName = message.tool.toolName,
                                    output = JSONObject().apply {
                                        put("error", toolError)
                                        put("ok", false)
                                    }.toString()
                                )
                            ), codeCanvas = codeCanvas ?: message.codeCanvas
                        )
                    }

                    // ✅ For tool messages, empty text is acceptable (will be filled by summary or remain empty)
                    if (isFinal && text.isBlank() && message.role == Role.Tool && toolError == null) {
                        // Tool executed successfully but no summary yet
                        return@mapIndexed message.copy(
                            id = finalId,
                            text = "", // Will be filled by summarization if user requests it
                            thought = thought,
                            ragResult = ragResult,
                            codeCanvas = codeCanvas ?: message.codeCanvas
                        )
                    }

                    // ✅ Handle empty text error for non-tool messages
                    if (isFinal && text.isBlank() && message.role != Role.Tool) {
                        return@mapIndexed message.copy(
                            id = finalId,
                            text = "Error: Empty response received",
                            thought = thought,
                            ragResult = ragResult,
                            codeCanvas = codeCanvas ?: message.codeCanvas
                        )
                    }

                    // ✅ Normal update
                    return@mapIndexed message.copy(
                        id = finalId,
                        text = text,
                        thought = thought,
                        ragResult = ragResult,
                        codeCanvas = codeCanvas ?: message.codeCanvas
                    )
                } else {
                    message
                }
            }
        }

        // Reset streaming index on final update
        if (isFinal) {
            streamingMessageIndex = -1
        }
    }

    /**
     * ✅ NEW: Updates tool preview with retry verification
     */
    fun updateToolPreview(messageId: String, toolOutput: ToolOutput): Boolean {
        var updateSuccess = false

        _messages.update { messages ->
            messages.map { message ->
                if (message.id == messageId) {
                    val pretty = try {
                        JSONObject(toolOutput.output).toString(2)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to prettify tool output", e)
                        toolOutput.output
                    }

                    Log.d(TAG, "Tool preview updated for message: $messageId")
                    updateSuccess = true

                    message.copy(
                        tool = RunningTool(
                            toolName = toolOutput.pluginName,
                            toolPreview = pretty,
                            toolOutput = toolOutput
                        )
                    )
                } else {
                    message
                }
            }
        }

        if (!updateSuccess) {
            Log.e(TAG, "Failed to update tool preview for message: $messageId")
        }

        return updateSuccess
    }

    /**
     * Gets a message by ID.
     */
    fun getCurrentMessageById(messageId: String): Message? {
        return _messages.value.firstOrNull { it.id == messageId }
    }

    /**
     * Generates a title for the current chat if one doesn't already exist.
     *
     * @param useAI If true, uses the LLM to generate a smart title. If false, creates a simple title from user message.
     * @param forceRegenerate If true, regenerates title even if one exists
     */
    suspend fun generateTitleIfNeeded(
        useAI: Boolean = false, forceRegenerate: Boolean = false
    ) = withContext(Dispatchers.IO) {
        // Skip if title already exists (unless forcing regeneration)
        if (_currentChatTitle.value.isNotBlank() && !forceRegenerate) {
            Log.d(TAG, "Title already exists, skipping generation")
            return@withContext
        }

        val messages = _messages.value
        if (messages.size < 2) {
            Log.d(TAG, "Not enough messages for title generation")
            return@withContext
        }

        val firstUserMessage = messages.firstOrNull { it.role == Role.User }?.text?.trim().orEmpty()
        if (firstUserMessage.isBlank()) {
            Log.d(TAG, "No user message found for title generation")
            return@withContext
        }

        try {
            UIStateManager.setStateGeneratingTitle()

            val generatedTitle = if (useAI) {
                generateTitleWithAI(firstUserMessage)
            } else {
                generateTitleFromText(firstUserMessage)
            }

            _currentChatTitle.value = generatedTitle
            Log.d(TAG, "Title generated: $generatedTitle")

        } catch (e: Exception) {
            // Fallback to simple title on any error
            _currentChatTitle.value = generateTitleFromText(firstUserMessage)
            Log.e(TAG, "Title generation failed, using fallback", e)
        } finally {
            UIStateManager.setStateIdle()
        }
    }

    /**
     * Generates a smart title using the LLM.
     * Falls back to simple text extraction on failure.
     */
    private suspend fun generateTitleWithAI(userMessage: String): String =
        withContext(Dispatchers.IO) {
            return@withContext try {
                // Save original system prompt to restore later
                val originalSystemPrompt = ModelManager.currentModel.value.systemPrompt
                val originalChatTemplate = ModelManager.currentModel.value.chatTemplate

                val titlePrompt = """
                Generate a concise, descriptive title (3-6 words) for this conversation.
                Rules:
                - Be specific and descriptive
                - Use title case
                - No quotes or punctuation at the end
                - Capture the main topic or question
                - Keep it under 50 characters
                
                User: $userMessage
                
                Title:
            """.trimIndent()

                // Configure for title generation
                ModelManager.setSystemPrompt("You are a title generator. Output only the title, nothing else.")
                ModelManager.setChatTemplate(ModelsList.defaultChatTemplate)

                // Generate title with token limit
                val titleBuilder = StringBuilder()
                var tokenCount = 0
                val maxTokens = 15

                ModelManager.generateStreaming(
                    prompt = titlePrompt,
                    toolJson = "",
                    onToken = { token ->
                        if (tokenCount < maxTokens) {
                            titleBuilder.append(token)
                            tokenCount++
                        }
                    },
                    onToolCalled = { _, _ -> })

                // Restore original configuration
                ModelManager.setSystemPrompt(originalSystemPrompt)
                ModelManager.setChatTemplate(originalChatTemplate ?: "")

                // Clean and validate the generated title
                val rawTitle = titleBuilder.toString().trim()
                val cleanTitle = cleanTitleString(rawTitle)

                if (cleanTitle.isBlank() || cleanTitle.length < 3) {
                    return@withContext generateTitleFromText(userMessage)
                }

                Log.d(TAG, "AI-generated title: $cleanTitle")
                cleanTitle

            } catch (e: Exception) {
                Log.e(TAG, "AI title generation failed, using fallback", e)
                // Fallback to simple extraction
                return@withContext generateTitleFromText(userMessage)
            }
        }

    /**
     * Creates a simple title by extracting the first few words from the message.
     * Always succeeds with a valid title.
     */
    private fun generateTitleFromText(userMessage: String): String {
        val rawTitle = userMessage.trim()
        val cleanTitle = cleanTitleString(rawTitle)

        return if (cleanTitle.isBlank() || cleanTitle.length < 3) {
            // Ultimate fallback: take first 6 words
            userMessage.split(" ").take(6).joinToString(" ").take(48).trim().ifBlank { "New Chat" }
        } else {
            cleanTitle
        }
    }

    /**
     * Cleans and normalizes a title string.
     * Removes quotes, punctuation, extra whitespace, and enforces length limits.
     */
    private fun cleanTitleString(rawTitle: String): String {
        return rawTitle.replace(Regex("^[\"']|[\"']$"), "")  // Remove leading/trailing quotes
            .replace(Regex("[.!?]+$"), "")         // Remove trailing punctuation
            .replace(Regex("\\s+"), " ")           // Normalize whitespace
            .take(50)                              // Enforce max length
            .trim()
    }

    /**
     * Sets the current chat title directly.
     * Useful for manual title updates.
     */
    fun setCurrentChatTitle(title: String) {
        _currentChatTitle.value = title
        Log.d(TAG, "Chat title set: $title")
    }

    /**
     * Gets the current number of messages.
     */
    fun getMessageCount(): Int = _messages.value.size

    /**
     * Checks if current chat has unsaved changes.
     */
    fun hasUnsavedChanges(): Boolean {
        return _messages.value.isNotEmpty() && _currentChatID.value.isEmpty()
    }

    fun updateDecodingMetrix(decodingMetrics:DecodingMetrics, messageId: String) {
        _messages.update { messages ->
            messages.map { message ->
                if (message.id == messageId) {
                    message.copy(decodingMetrics = decodingMetrics)
                } else {
                    message
                }
            }
        }
    }
}