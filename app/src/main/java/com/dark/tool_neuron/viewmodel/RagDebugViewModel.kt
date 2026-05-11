package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.model.Chat
import com.dark.tool_neuron.repo.ChatRepository
import com.dark.tool_neuron.repo.RagDebugResult
import com.dark.tool_neuron.repo.RagManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RagDebugViewModel @Inject constructor(
    private val ragManager: RagManager,
    private val chatRepo: ChatRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _selectedChatId = MutableStateFlow<String?>(null)
    val selectedChatId: StateFlow<String?> = _selectedChatId.asStateFlow()

    private val _result = MutableStateFlow<RagDebugResult?>(null)
    val result: StateFlow<RagDebugResult?> = _result.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    val ragReady: StateFlow<Boolean> = ragManager.isReady
    val embeddingName: StateFlow<String?> = ragManager.activeEmbeddingName

    fun chats(): List<Chat> = chatRepo.chats.value

    fun setQuery(value: String) { _query.value = value }

    fun selectChat(chatId: String?) { _selectedChatId.value = chatId }

    fun run() {
        val q = _query.value.trim()
        val chatId = _selectedChatId.value ?: chats().firstOrNull()?.id
        if (q.isEmpty() || chatId == null) return
        if (_selectedChatId.value == null) _selectedChatId.value = chatId
        _isRunning.value = true
        viewModelScope.launch {
            try {
                _result.value = ragManager.debugQuery(chatId, q)
            } finally {
                _isRunning.value = false
            }
        }
    }
}
