package com.dark.neuroverse.viewModel

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dark.ai_module.ai.Neuron
import com.dark.neuroverse.data.UserPrefs
import com.dark.neuroverse.model.ChatINFO
import com.dark.neuroverse.model.FileAttachment
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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import javax.crypto.SecretKey

class ChattingViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ChattingViewModel(context) as T
    }
}

@SuppressLint("StaticFieldLeak")
class ChattingViewModel(private val context: Context) : ViewModel() {

    //region -- State Variables

    private val key =
        MutableStateFlow<SecretKey>(getOrCreateHardwareBackedAesKey(com.dark.neuroverse.BuildConfig.ALIAS))
    private val rootNode = MutableStateFlow<NeuronTree>(readBrainFile(key.value, context))

    private val _chatTitle = MutableStateFlow("")
    val chatTitle: StateFlow<String> = _chatTitle

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    private val _streamingBuffer = MutableStateFlow("")
    val streamingBuffer: StateFlow<String> = _streamingBuffer

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating

    private val _chatList = MutableStateFlow(emptyList<ChatINFO>())
    val chatList: StateFlow<List<ChatINFO>> = _chatList

    private val _attachedFiles = MutableStateFlow<List<FileAttachment>>(emptyList())
    val attachedFiles: StateFlow<List<FileAttachment>> = _attachedFiles

    val professionalism = MutableStateFlow(5.0f) // default mid-level
    val emotional = MutableStateFlow(5.0f)       // default mid-level


    val chatId = MutableStateFlow("")
    //endregion

    //region -- Init
    init {
        CoroutineScope(Dispatchers.IO).launch {
            key.value = getOrCreateHardwareBackedAesKey(com.dark.neuroverse.BuildConfig.ALIAS)
            rootNode.value = readBrainFile(key.value, context)

            val root = rootNode.value.getNodeDirect("root")
            val chatHistory = getDefaultChatHistory(root)

            val validChats = NeuronTree(chatHistory).getAllChildrenRecursive()
                .filter { it.data.content.isNotBlank() }

            if (validChats.isNotEmpty()) {
                val firstChat = validChats.first()
                Log.d("init", "Auto-loading chat ID: ${firstChat.id}")
                loadChatById(firstChat.id)
            } else {
                Log.d("init", "No valid chats found.")
            }

            updateChatList()
            clearAttachment()
            rootNode.value.printTree()
            val p = UserPrefs.getModelPParams(context).firstOrNull() ?: 5.0f
            val e = UserPrefs.getModelEParams(context).firstOrNull() ?: 5.0f

            professionalism.value = p
            emotional.value = e
        }
    }
    //endregion

    //region -- Public Functions
//    fun handleFileUri(context: Context, uri: Uri) {
//        viewModelScope.launch(Dispatchers.IO) {
//            try {
//                val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
//                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
//                    if (cursor.moveToFirst()) cursor.getString(index) else "unknown_file"
//                } ?: "unknown_file"
//
//                val placeholder = FileAttachment(doc = DOC(fileName, "", "", ""), isLoading = true)
//
//                // Add and capture its index
//                val index = _attachedFiles.updateAndGet { it + placeholder }.lastIndex
//
//                val tempFile = File(context.cacheDir, fileName)
//                context.contentResolver.openInputStream(uri)?.use { input ->
//                    tempFile.outputStream().use { output -> input.copyTo(output) }
//                }
//
//                val summary = DocReader.read(tempFile)
//
//                _attachedFiles.update { currentList ->
//                    currentList.toMutableList().apply {
//                        this[index] = FileAttachment(doc = summary, isLoading = false)
//                    }
//                }
//
//            } catch (e: Exception) {
//                Log.e("FilePicker", "Failed to load file", e)
//            }
//        }
//    }

    private inline fun <T> MutableStateFlow<T>.updateAndGet(update: (T) -> T): T {
        val newValue = update(this.value)
        this.value = newValue
        return newValue
    }

    fun clearAttachment() {
        _attachedFiles.value = emptyList()
    }

    fun clearAttachment(index: Int) {
        _attachedFiles.update { it.toMutableList().apply { removeAt(index) } }
    }

    fun sendMessage(userInput: String) {
        if (userInput.isBlank()) {
            Log.w("ChatVM", "sendMessage() skipped due to blank input")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isGenerating.value = true
                _streamingBuffer.value = ""

                val finalizedDocs =
                    _attachedFiles.value.filter { !it.isLoading && it.doc.path.isNotBlank() }
                clearAttachment()

                val time = System.currentTimeMillis().toString()
                val userMessage = Message(
                    role = ROLE.USER,
                    content = userInput,
                    timeStamp = time,
                    document = finalizedDocs as MutableList<FileAttachment>
                )

                val placeholder = Message(ROLE.SYSTEM, "", "streaming")
                _messages.update { it + userMessage + placeholder }

                val inputJson = buildInputPayload(finalizedDocs)
                val inputStr = inputJson.toString()

                if (inputStr.isBlank()) {
                    Log.e("ChatVM", "Input JSON is blank. Cancelling inference.")
                    _isGenerating.value = false
                    return@launch
                }

                val fullResponse = ""

                _isGenerating.value = false

                _messages.update {
                    it.filterNot { m -> m.role == ROLE.SYSTEM && m.timeStamp == "streaming" } + Message(
                        ROLE.SYSTEM,
                        fullResponse,
                        System.currentTimeMillis().toString()
                    )
                }

                generateTitle()
                updateConversation()
                updateChatList()
                _attachedFiles.value = emptyList()
            } catch (e: Exception) {
                Log.e("ChatVM", "sendMessage failed", e)
                _isGenerating.value = false
            }
        }
    }

    fun loadChatById(chatIdToLoad: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val root = rootNode.value.getNodeDirect("root")
                val chatHistory = getDefaultChatHistory(root)
                val node = NeuronTree(chatHistory).getNodeDirect(chatIdToLoad)

                if (node.data.content.isBlank()) return@launch

                val json = JSONObject(node.data.content)
                val title = json.optString("title", "")
                val conversations = Json.decodeFromString<List<Message>>(
                    json.getJSONArray("conversations").toString()
                )

                _chatTitle.value = title
                _messages.value = conversations
                chatId.value = chatIdToLoad

            } catch (e: Exception) {
                Log.e("loadChatById", "Failed loading chat $chatIdToLoad", e)
            }
        }
    }

    fun newChat() {
        _messages.value = emptyList()
        _streamingBuffer.value = ""
        _chatTitle.value = ""
        chatId.value = ""
        _isGenerating.value = false
        Neuron.stopGeneration()
    }

    fun stopGenerating() {
        Neuron.stopGeneration()
        _isGenerating.value = false
        updateConversation()
    }
    //endregion

    //region -- Private Helpers

    fun updateChatList() {
        try {
            _chatList.value = emptyList()
            val chatInfo = mutableListOf<ChatINFO>()
            val root = rootNode.value.getNodeDirect("root")
            val history = getDefaultChatHistory(root)

            NeuronTree(history).getAllChildrenRecursive().forEach { node ->
                if (node.data.content.isNotBlank()) {
                    val title = runCatching {
                        JSONObject(node.data.content).optString("title", "Untitled")
                    }.getOrElse { "Untitled" }

                    chatInfo.add(ChatINFO(node.id, title))
                }
            }

            _chatList.value = chatInfo

        } catch (e: Exception) {
            Log.e("updateChatList", "Failed loading chat titles", e)
        }
    }

    private suspend fun generateTitle() {
        if (_chatTitle.value.isNotBlank()) return

        val response = getLatestAIResponse()
        if (response.isEmpty()) return

        val prompt = """
            Generate a concise json output with a for Provided Conversation
            Rules : 
            - Title Should be less than 2 words
            - Title Should be in English
            
            Schema :
            { title: String } 
            
            Conversation :
            $response
        """.trimIndent()

        runCatching {
            val result = Neuron.generateAndWait(prompt)
            val title = JSONObject(extractPureJson(result)).getString("title")
            _chatTitle.value = title
        }.onFailure {
            Log.w("generateTitle", "Failed to generate title", it)
        }
    }

    private fun getLatestAIResponse(): String {
        return _messages.value.lastOrNull { it.role == ROLE.SYSTEM }?.content.orEmpty()
    }

    private fun updateConversation() {
        try {
            val root = rootNode.value.getNodeDirect("root")
            val history = getDefaultChatHistory(root)
            val tree = NeuronTree(history)

            val node = chatId.value.takeIf { it.isNotBlank() }?.let { id ->
                runCatching { tree.getNodeDirect(id) }.getOrNull()
            }

            val old = node?.data?.content.orEmpty()
            val messages = runCatching {
                val jsonArrayString = JSONObject(old).getJSONArray("conversations").toString()
                Json.decodeFromString<List<Message>>(jsonArrayString)
            }.getOrElse { emptyList() }

            val combined = messages + _messages.value
            val jsonData = JSONObject().apply {
                put("title", _chatTitle.value)
                put("conversations", JSONArray(Json.encodeToString(combined)))
            }

            if (node != null) {
                node.data.content = jsonData.toString()
            } else {
                val newNode = addNewChat(history, jsonData)
                chatId.value = newNode.id
            }

            saveTree(rootNode.value, context, com.dark.neuroverse.BuildConfig.ALIAS)
        } catch (e: Exception) {
            Log.e("updateConversation", "Failed updating chat", e)
        }
    }

    private fun buildInputPayload(finalizedDocs: List<FileAttachment>): JSONObject {
        val arr = JSONArray()

        arr.put(JSONObject().apply {
            put("role", "system")
            put("content", "You are NeuroV AI assistant.")
        })

        _messages.value.forEach { msg ->
            val clean =
                msg.content.replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "")
                    .trim()
            if (clean.isNotBlank()) {
                arr.put(JSONObject().apply {
                    put("role", msg.role.name.lowercase())
                    put("content", clean)
                    put("timestamp", msg.timeStamp)
                })
            }
        }

        return JSONObject().apply {
            put("messages", arr)
            put("response_format", "text")
            put("professionalism", professionalism.value)
            put("emotional", emotional.value)

            if (finalizedDocs.isNotEmpty()) {
                put("documents", JSONArray().apply {
                    finalizedDocs.forEach {
                        put(JSONObject().apply {
                            put("name", it.doc.name)
                            put("type", it.doc.type)
                            put("content", sanitizeForModel(it.doc.content))
                        })
                    }
                })
            }
        }
    }

    private fun sanitizeForModel(input: String): String {
        return input.replace(Regex("[ ]{2,}"), " ").replace(Regex("\\n{2,}"), "\n")
            .replace(Regex("(?<=\\w)[ ](?=\\w)"), "").trim().take(3000) + "\n\n[TRUNCATED]"
    }

    private fun roughTokenEstimate(text: String): Int {
        return (text.split(Regex("\\s+")).size * 1.5).toInt()
    }

    fun deleteChatById(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Delete the node from the tree
                rootNode.value.deleteNodeById(id)

                // 2. Save updated tree to disk
                saveTree(rootNode.value, context, com.dark.neuroverse.BuildConfig.ALIAS)

                // 3. Update chat list for UI
                updateChatList()

                // 4. If deleted chat was active, reset UI state
                if (chatId.value == id) {
                    withContext(Dispatchers.Main) {
                        _messages.value = emptyList()
                        _chatTitle.value = ""
                        chatId.value = ""
                    }
                }
            } catch (e: Exception) {
                Log.e("deleteChatById", "Failed to delete chat $id", e)
            }
        }
    }


    //endregion
}