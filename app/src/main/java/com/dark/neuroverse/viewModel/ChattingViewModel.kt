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
    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating

    fun sendMessage(userInput: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isGenerating.value = true
            _isThinking.value = true
            _streamingBuffer.value = ""

            val timeStamp = System.currentTimeMillis().toString()
            val userMessage = Message(ROLE.USER, userInput, timeStamp)
            _messages.update { it + userMessage }

            val messagesJson = JSONArray()
            val systemMessage = JSONObject()
            systemMessage.put("role", "system")
            systemMessage.put("content", "You are NeuroV AI assistant.")
            messagesJson.put(systemMessage)

            _messages.value.forEach { msg ->
                val msgJson = JSONObject()
                msgJson.put("role", msg.role.name.lowercase())
                msgJson.put("content", msg.content)
                messagesJson.put(msgJson)
            }

            val jsonPayload = JSONObject()
            jsonPayload.put("messages", messagesJson)
            jsonPayload.put("response_format", "text")

            var fullResponse = ""


            Neuron.generateResponseStreaming(jsonPayload.toString()) { chunk ->
                fullResponse += chunk
                _isThinking.value = false
                _isGenerating.value = true

                viewModelScope.launch(Dispatchers.Main) {
                    _streamingBuffer.update { it + chunk }

                    _messages.update { currentList ->
                        val filteredList = currentList.filterNot { it.role == ROLE.SYSTEM && it.timeStamp == timeStamp }
                        filteredList + Message(ROLE.SYSTEM, _streamingBuffer.value, timeStamp)
                    }
                }
            }.also {
                // Streaming has finished, turn off the loader
                _isGenerating.value = false
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
        return _messages.value
            .lastOrNull { it.role == ROLE.SYSTEM }
            ?.content ?: ""
    }

    fun stopGenerating() {
        Neuron.stopGeneration(true).also {
            _isGenerating.value = false
        }
    }
}