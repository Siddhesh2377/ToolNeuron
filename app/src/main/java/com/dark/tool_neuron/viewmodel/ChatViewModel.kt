package com.dark.tool_neuron.viewmodel

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.engine.GenerationEvent
import com.dark.tool_neuron.models.messages.ContentType
import com.dark.tool_neuron.models.messages.MessageContent
import com.dark.tool_neuron.models.messages.Messages
import com.dark.tool_neuron.models.messages.Role
import com.dark.tool_neuron.worker.ChatManager
import com.dark.tool_neuron.worker.GenerationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ChatViewModel(
    private val chatManager: ChatManager,
    private val generationManager: GenerationManager
) : ViewModel() {

    private val _messages = mutableStateListOf<Messages>()
    val messages: SnapshotStateList<Messages> = _messages

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _currentChatId = MutableStateFlow<String?>(null)
    val currentChatId: StateFlow<String?> = _currentChatId

    private var currentAssistantMessageId: String? = null
    private var currentAssistantMessageIndex: Int = -1

    fun loadChat(chatId: String) {
        viewModelScope.launch {
            _currentChatId.value = chatId
            chatManager.getChatMessages(chatId).onSuccess { loadedMessages ->
                _messages.clear()
                _messages.addAll(loadedMessages)
            }.onFailure { e ->
                _error.value = "Failed to load chat: ${e.message}"
            }
        }
    }

    fun sendMessage(prompt: String, maxTokens: Int = 512) {
        val chatId = _currentChatId.value
        if (chatId == null) {
            Log.d("ChatViewModel", "No chat selected")
            _error.value = "No chat selected"
            return
        }

        if (!generationManager.isModelLoaded()) {
            _error.value = "Please load a model first"
            return
        }

        if (_isGenerating.value) {
            return
        }

        viewModelScope.launch {
            chatManager.addUserMessage(chatId, prompt).onSuccess { userMessage ->
                _messages.add(userMessage)
                generate(chatId, prompt, maxTokens)
            }.onFailure { e ->
                _error.value = "Failed to save message: ${e.message}"
            }
        }
    }

    private fun generate(chatId: String, prompt: String, maxTokens: Int) {
        viewModelScope.launch {
            _isGenerating.value = true
            _error.value = null

            val assistantMessage = Messages(
                role = Role.Assistant,
                content = MessageContent(contentType = ContentType.Text, content = "")
            )
            currentAssistantMessageId = assistantMessage.msgId
            currentAssistantMessageIndex = _messages.size
            _messages.add(assistantMessage)

            var fullContent = ""
            var finalMetrics: com.mp.ai_gguf.models.DecodingMetrics? = null

            try {
                val conversationPrompt = generationManager.buildConversationPrompt(
                    _messages.dropLast(1),
                    prompt
                )

                generationManager.generateStreaming(conversationPrompt, maxTokens).collect { event ->
                    when (event) {
                        is GenerationEvent.Token -> {
                            fullContent += event.text
                            if (currentAssistantMessageIndex >= 0 &&
                                currentAssistantMessageIndex < _messages.size) {
                                val current = _messages[currentAssistantMessageIndex]
                                _messages[currentAssistantMessageIndex] = current.copy(
                                    content = current.content.copy(content = fullContent)
                                )
                            }
                        }
                        is GenerationEvent.Done -> {
                            _isGenerating.value = false

                            val finalMessage = Messages(
                                msgId = currentAssistantMessageId!!,
                                role = Role.Assistant,
                                content = MessageContent(
                                    contentType = ContentType.Text,
                                    content = fullContent
                                ),
                                decodingMetrics = finalMetrics
                            )

                            chatManager.addAssistantMessage(
                                chatId,
                                fullContent,
                                finalMetrics
                            )

                            currentAssistantMessageId = null
                            currentAssistantMessageIndex = -1
                        }
                        is GenerationEvent.Error -> {
                            _isGenerating.value = false
                            _error.value = event.message

                            if (currentAssistantMessageIndex >= 0 &&
                                currentAssistantMessageIndex < _messages.size) {
                                val current = _messages[currentAssistantMessageIndex]
                                _messages[currentAssistantMessageIndex] = current.copy(
                                    content = current.content.copy(
                                        content = "Error: ${event.message}"
                                    )
                                )
                            }

                            currentAssistantMessageId = null
                            currentAssistantMessageIndex = -1
                        }
                        is GenerationEvent.Metrics -> {
                            finalMetrics = event.metrics

                            if (currentAssistantMessageIndex >= 0 &&
                                currentAssistantMessageIndex < _messages.size) {
                                val current = _messages[currentAssistantMessageIndex]
                                _messages[currentAssistantMessageIndex] =
                                    current.copy(decodingMetrics = event.metrics)
                            }
                        }
                        is GenerationEvent.ToolCall -> {}
                    }
                }
            } catch (e: Exception) {
                _isGenerating.value = false
                _error.value = e.message
                currentAssistantMessageId = null
                currentAssistantMessageIndex = -1
            }
        }
    }

    fun stop() {
        generationManager.stopGeneration()
        _isGenerating.value = false
        currentAssistantMessageId = null
        currentAssistantMessageIndex = -1
    }

    fun clearMessages() {
        _messages.clear()
        currentAssistantMessageId = null
        currentAssistantMessageIndex = -1
        _error.value = null
    }

    fun clearError() {
        _error.value = null
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            chatManager.deleteMessage(messageId).onSuccess {
                _messages.removeIf { it.msgId == messageId }
            }.onFailure { e ->
                _error.value = "Failed to delete message: ${e.message}"
            }
        }
    }
}