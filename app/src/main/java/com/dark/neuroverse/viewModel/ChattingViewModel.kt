package com.dark.neuroverse.viewModel

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.ai_module.ai.Neuron
import com.dark.neuroverse.data.DocReader
import com.dark.neuroverse.model.Message
import com.dark.neuroverse.model.ROLE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ChattingViewModel : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages
    private val _streamingBuffer = MutableStateFlow("")
    val streamingBuffer: StateFlow<String> = _streamingBuffer
    private val fileData = MutableStateFlow("")

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating

    fun sendMessage(userInput: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isGenerating.value = true
            _streamingBuffer.value = ""

            val userTime = System.currentTimeMillis().toString()
            val streamTime = "streaming" // temp ID for live update

            val userMessage = Message(ROLE.USER, userInput, userTime)
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
                            if (fileData.value.isNotEmpty()) {
                                put("user_shared_document", fileData.value)
                            }
                        })
                    }
                }
            }


            val jsonPayload = JSONObject().apply {
                put("messages", messagesJson)
                put("response_format", "text")
            }

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

            // Replace temporary message with full final message
            _messages.update { current ->
                current.filterNot { it.role == ROLE.SYSTEM && it.timeStamp == streamTime } + Message(
                    ROLE.SYSTEM, fullResponse, System.currentTimeMillis().toString()
                )
            }
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
        return getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
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
    }

    fun newChat() {
        _messages.value = emptyList()
        _streamingBuffer.value = ""
        _isGenerating.value = false
        Neuron.stopGeneration(true)
    }
}