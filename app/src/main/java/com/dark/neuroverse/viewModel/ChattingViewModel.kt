package com.dark.neuroverse.viewModel

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dark.ai_module.ai.Neuron
import com.dark.neuroverse.BuildConfig
import com.dark.neuroverse.data.DocReader
import com.dark.neuroverse.model.ChatINFO
import com.dark.neuroverse.model.DOC
import com.dark.neuroverse.model.Message
import com.dark.neuroverse.model.ROLE
import com.dark.neuroverse.util.extractPureJson
import com.dark.userdata.addNewChat
import com.dark.userdata.getDefaultChatHistory
import com.dark.userdata.ntds.getOrCreateHardwareBackedAesKey
import com.dark.userdata.ntds.neuron_tree.NeuronTree
import com.dark.userdata.readBrainFile
import com.dark.userdata.saveTree
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.crypto.SecretKey

class ChattingViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChattingViewModel::class.java)) {
            return ChattingViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class ChattingViewModel(private val context: Context) : ViewModel() {

    private lateinit var key: MutableStateFlow<SecretKey>
    private lateinit var rootNode: MutableStateFlow<NeuronTree>
    private val _chatTitle = MutableStateFlow("")
    val chatTitle: StateFlow<String> = _chatTitle
    private val _chatList = MutableStateFlow(emptyList<ChatINFO>())
    val chatList: StateFlow<List<ChatINFO>> = _chatList

    init {
        CoroutineScope(Dispatchers.IO).launch {
            key = MutableStateFlow(getOrCreateHardwareBackedAesKey(BuildConfig.ALIAS))
            val brainTree = readBrainFile(key.value, context)
            rootNode = MutableStateFlow(brainTree)

            val root = rootNode.value.getNodeDirect("root")
            val chatHistory = getDefaultChatHistory(root)
            val validChats = NeuronTree(chatHistory).getAllChildrenRecursive().filter {
                it.data.content.isNotBlank()
            }

            if (validChats.isNotEmpty()) {
                val firstChat = validChats.first()
                Log.d("init", "Auto-loading first valid chat ID: ${firstChat.id}")
                loadChatById(firstChat.id)
                rootNode.value.printTree()
                getChatInfo()
            } else {
                Log.d("init", "No valid chats found. Starting fresh.")
            }
        }

    }

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages
    private val _streamingBuffer = MutableStateFlow("")
    val streamingBuffer: StateFlow<String> = _streamingBuffer
    private val fileData = MutableStateFlow(
        DOC(
            "", "", "", ""
        )
    )

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating
    val chatId = MutableStateFlow("")

    fun sendMessage(userInput: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isGenerating.value = true
            _streamingBuffer.value = ""

            val userTime = System.currentTimeMillis().toString()
            val streamTime = "streaming" // temp ID for live update

            val document = fileData.value

            val userMessage = Message(ROLE.USER, userInput, userTime, document)
            _messages.update { it + userMessage }

            // Add placeholder AI message (initially blank)
            _messages.update { it + Message(ROLE.SYSTEM, "", streamTime) }

            val messagesJson = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "You are NeuroV AI assistant.")
                })

                _messages.value.forEach { msg ->
                    val cleanedContent = msg.content.replace(
                        Regex(
                            "<think>.*?</think>", RegexOption.DOT_MATCHES_ALL
                        ), ""
                    ) // remove all <think>...</think> blocks
                        .trim()

                    if (cleanedContent.isNotBlank()) {
                        put(JSONObject().apply {
                            put("role", msg.role.name.lowercase())
                            put("content", cleanedContent)
                            put("timestamp", msg.timeStamp)
                        })
                    }
                }
            }

            val sanitizedContent = sanitizeForModel(fileData.value.content)

            // Optional: prevent crash by skipping overly large token payloads
            if (roughTokenEstimate(sanitizedContent) > 6048) {
                Log.w("ChattingViewModel", "Document content too large after sanitization – skipping attachment.")
                _isGenerating.value = false
                return@launch
            }

            fileData.value = fileData.value.copy(content = sanitizedContent)


            val jsonPayload = JSONObject().apply {
                put("messages", messagesJson)
                put("response_format", "text")

                if (fileData.value.path.isNotEmpty()) {
                    put("document", JSONObject().apply {
                        put("name", fileData.value.name)
                        put("type", fileData.value.type)
                        put("content", fileData.value.content)
                    })
                }
            }


            Log.d("ChattingViewModel", "Sending payload: ${jsonPayload.toString().length}")
            Log.d("ChattingViewModel", "Payload content: $jsonPayload")


            val fullResponse = Neuron.generateResponseStreaming(jsonPayload.toString()) { chunk ->
                viewModelScope.launch(Dispatchers.Main) {
                    _streamingBuffer.update { it + chunk }

                    // Replace temp system message with updated streaming content
                    _messages.update { current ->
                        current.map {
                            if (it.role == ROLE.SYSTEM && it.timeStamp == streamTime) {
                                it.copy(content = _streamingBuffer.value)
                            } else it
                        }
                    }
                }
            }

            _isGenerating.value = false
            fileData.value = DOC("", "", "", "")

            // Replace temporary message with full final message
            _messages.update { current ->
                current.filterNot { it.role == ROLE.SYSTEM && it.timeStamp == streamTime } + Message(
                    ROLE.SYSTEM, fullResponse, System.currentTimeMillis().toString()
                )
            }

            generateTitle()

                updateConversation()
                rootNode.value.printTree()


        }
    }

    fun handleFileUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val contentResolver = context.contentResolver

                // Step 1: Extract file name from the URI
                val fileName = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndexOpenableColumnsDisplayName()
                    if (cursor.moveToFirst() && nameIndex != -1) {
                        cursor.getString(nameIndex)
                    } else {
                        "unknown_file"
                    }
                } ?: "unknown_file"

                // Step 2: Create actual temp file with original name
                val ext = fileName.substringAfterLast('.', "")
                val baseName = fileName.substringBeforeLast('.', "temp")
                val tempFile = File(context.cacheDir, "$baseName.$ext")

                // Step 3: Copy content
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }

                // Step 4: Send to Python
                fileData.value = DocReader.read(tempFile)

            } catch (e: Exception) {
                Log.e("FilePicker", "Failed to read file: ${e.localizedMessage}", e)
            }
        }
    }

    // Helper Extension
    private fun Cursor.getColumnIndexOpenableColumnsDisplayName(): Int {
        return getColumnIndex(OpenableColumns.DISPLAY_NAME)
    }


    /**
     * Remove messages in batch from index 'from' to 'to' (exclusive)
     * Example: removeMessages(0, messages.size - 1) removes all except last message
     */
    fun removeMessages(from: Int, to: Int) {
        if (from < 0 || to > _messages.value.size || from >= to) return

        _messages.update { currentList ->
            val retained = currentList.toMutableList()
            retained.subList(from, to).clear()
            retained
        }
    }

    /**
     * Returns the latest AI (system) message content, or null if none exists.
     */
    fun getLatestAIResponse(): String {
        return _messages.value.lastOrNull { it.role == ROLE.SYSTEM }?.content ?: ""
    }

    fun stopGenerating() {
        Neuron.stopGeneration(true).also {
            _isGenerating.value = false
        }
        updateConversation()
    }

    private suspend fun generateTitle() {
        val latestAIResponse = getLatestAIResponse()
        if (latestAIResponse.isNotEmpty() && _chatTitle.value.isEmpty()) {
            val prompt = """
                Generate a concise json output with a for Provided Conversation
                Rules : 
                - Title Should be less than 2 words
                - Title Should be in English
                
                Schema :
                { title: String } 
                
                Conversation :
                $latestAIResponse
                
            """.trimIndent()
            val rwa = extractPureJson(Neuron.generateResponseBlocking(prompt))
            val jsonCode = JSONObject(rwa)
            _chatTitle.value = jsonCode.getString("title")
        }
    }

    fun newChat() {
        _messages.value = emptyList()
        _streamingBuffer.value = ""
        _isGenerating.value = false
        Neuron.stopGeneration(true)
        _chatTitle.value = ""
        chatId.value = ""  // Reset ID to force new chat creation

        Log.d("newChat", "Started new chat")
    }

    private fun sanitizeForModel(input: String): String {
        return input
            .replace(Regex("[ ]{2,}"), " ") // collapse extra spaces
            .replace(Regex("\\n{2,}"), "\n") // collapse empty newlines
            .replace(Regex("(?<=\\w)[ ](?=\\w)"), "") // remove letter-spacing (A I → AI)
            .trim()
            .take(3000) + "\n\n[TRUNCATED]"
    }

    private fun roughTokenEstimate(text: String): Int {
        return (text.split(Regex("\\s+")).size * 1.5).toInt()
    }

    private fun updateConversation() {
        Log.d("updateConversation", "Starting updateConversation()")

        val root = rootNode.value.getNodeDirect("root")
        Log.d("updateConversation", "Fetched root node")

        val chatHistory = getDefaultChatHistory(root)
        Log.d("updateConversation", "Retrieved chat history node")

        val tree = NeuronTree(chatHistory)
        Log.d("updateConversation", "Created NeuronTree from chat history")

        val chatNode = if (chatId.value.isBlank()) {
            Log.d("updateConversation", "chatId is blank — will create a new chat node.")
            null
        } else {
            try {
                val node = tree.getNodeDirect(chatId.value)
                Log.d("updateConversation", "Found existing chat node with ID: ${chatId.value}")
                node
            } catch (e: Exception) {
                Log.w("updateConversation", "No existing chat node found with ID: ${chatId.value}, will create new")
                null
            }
        }


        val json = Json
        var combinedMessages: List<Message>

        if (chatNode != null && chatNode.data.content.isNotBlank()) {
            val oldChat = chatNode.data.content
            Log.d("updateConversation", "Old chat content: $oldChat")

            try {
                val jsonArrayString = JSONObject(oldChat).getJSONArray("conversations").toString()
                val oldMessages: List<Message> = json.decodeFromString(jsonArrayString)
                Log.d("updateConversation", "Parsed old messages count: ${oldMessages.size}")
                combinedMessages = oldMessages + _messages.value
            } catch (e: Exception) {
                Log.e("updateConversation", "Failed to parse old chat JSON. Fallback to current messages only.", e)
                combinedMessages = _messages.value
            }
        } else {
            Log.d("updateConversation", "No existing content found. Using current messages only.")
            combinedMessages = _messages.value
        }


        val updatedJson = json.encodeToString(combinedMessages)
        Log.d("updateConversation", "Serialized combined messages to JSON string of length: ${updatedJson.length}")

        val jsonData = JSONObject().apply {
            put("title", _chatTitle.value)
            put("conversations", JSONArray(updatedJson))
        }
        Log.d("updateConversation", "Final JSON object to store: $jsonData")

        if (chatNode != null) {
            chatNode.data.content = jsonData.toString()
            Log.d("updateConversation", "Updated existing chat node content.")
        } else {
            addNewChat(chatHistory, jsonData).also { newNode ->
                chatId.value = newNode.id
                Log.d("updateConversation", "Created new chat node with ID: ${newNode.id}")
            }
        }

        // ✅ Save the updated tree
        saveTree(rootNode.value, context, BuildConfig.ALIAS)
        Log.d("updateConversation", "NeuronTree saved to encrypted file.")

        Log.d("updateConversation", "CHAT ID: ${chatId.value}")

        Log.d("updateConversation", "Finished updateConversation()")
    }

    fun loadChatById(chatIdToLoad: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d("loadChatById", "Loading chat with ID: $chatIdToLoad")

                val root = rootNode.value.getNodeDirect("root")
                val chatHistory = getDefaultChatHistory(root)
                val tree = NeuronTree(chatHistory)
                val chatNode = tree.getNodeDirect(chatIdToLoad)

                val chatContent = chatNode.data.content
                Log.d("loadChatById", "Found chat node, content: $chatContent")

                if (chatContent.isBlank()) {
                    Log.w("loadChatById", "Chat content is empty for ID: $chatIdToLoad — skipping load.")
                    return@launch
                }

                val jsonObject = JSONObject(chatContent)
                val conversationsArray = jsonObject.getJSONArray("conversations").toString()
                val title = jsonObject.optString("title", "")

                val loadedMessages: List<Message> = Json.decodeFromString(conversationsArray)
                Log.d("loadChatById", "Loaded ${loadedMessages.size} messages")

                // Update state
                _messages.value = loadedMessages
                _chatTitle.value = title
                chatId.value = chatIdToLoad

                Log.d("loadChatById", "Chat successfully loaded")

            } catch (e: Exception) {
                Log.e("loadChatById", "Failed to load chat with ID: $chatIdToLoad", e)
            }
        }
    }

    fun getChatInfo(): List<ChatINFO> {
        val list = mutableListOf<ChatINFO>()

        try {
            val root = rootNode.value.getNodeDirect("root")
            val chatHistory = getDefaultChatHistory(root)
            val tree = NeuronTree(chatHistory)

            tree.getAllChildrenRecursive().forEach { node ->
                val content = node.data.content
                if (content.isNotBlank()) {
                    val title = try {
                        JSONObject(content).optString("title", "Untitled")
                    } catch (e: Exception) {
                        "Untitled"
                    }
                    list.add(ChatINFO(node.id, title))
                }
            }

        } catch (e: Exception) {
            Log.e("getChatInfo", "Failed to load chat info", e)
        }

        _chatList.value = list
        return list
    }


}