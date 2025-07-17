package com.dark.neuroverse.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.ai_module.ai.Neuron
import com.dark.neuroverse.model.Message
import com.dark.neuroverse.model.ROLE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class ChattingViewModel : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages
    private val _streamingBuffer = MutableStateFlow("")
    val streamingBuffer: StateFlow<String> = _streamingBuffer

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
                    val cleanedContent = msg.content
                        .replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "") // remove all <think>...</think> blocks
                        .trim()

                    if (cleanedContent.isNotBlank()) {
                        put(JSONObject().apply {
                            put("role", msg.role.name.lowercase())
                            put("content", cleanedContent)
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
                    ROLE.SYSTEM,
                    fullResponse,
                    System.currentTimeMillis().toString()
                )
            }
        }
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

    fun newChat(){
        _messages.value = emptyList()
        _streamingBuffer.value = ""
        _isGenerating.value = false
        Neuron.stopGeneration(true)
    }
}