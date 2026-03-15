package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.models.vault.ChatInfo
import com.dark.tool_neuron.state.AppStateManager
import com.dark.tool_neuron.worker.ChatManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val chatManager: ChatManager
) : ViewModel() {

    private val _chats = MutableStateFlow<List<ChatInfo>>(emptyList())
    val chats: StateFlow<List<ChatInfo>> = _chats

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _isDialogOpen = MutableStateFlow(false)
    val isDialogOpen: StateFlow<Boolean> = _isDialogOpen

    init {
        loadChats()
    }

    fun loadChats() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            chatManager.getAllChats().onSuccess { chatList ->
                _chats.value = chatList
            }.onFailure { e ->
                _error.value = "Failed to load chats: ${e.message}"
            }
            AppStateManager.unRefreshChat()
            _isLoading.value = false
        }
    }

    fun createNewChat(onCreated: (String) -> Unit) {
        viewModelScope.launch {
            _error.value = null
            AppStateManager.chatRefreshed()
            chatManager.createNewChat().onSuccess { chatId ->
                loadChats()
                onCreated(chatId)
            }.onFailure { e ->
                _error.value = "Failed to create chat: ${e.message}"
            }
        }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            _error.value = null

            chatManager.deleteChat(chatId).onSuccess {
                loadChats()
            }.onFailure { e ->
                _error.value = "Failed to delete chat: ${e.message}"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}