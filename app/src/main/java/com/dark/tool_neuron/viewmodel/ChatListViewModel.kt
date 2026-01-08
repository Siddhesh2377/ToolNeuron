package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.models.vault.ChatInfo
import com.dark.tool_neuron.worker.ChatManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ChatListViewModel(
    private val chatManager: ChatManager
) : ViewModel() {

    private val _chats = MutableStateFlow<List<ChatInfo>>(emptyList())
    val chats: StateFlow<List<ChatInfo>> = _chats

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

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

            _isLoading.value = false
        }
    }

    fun createNewChat(onCreated: (String) -> Unit) {
        viewModelScope.launch {
            _error.value = null

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

    fun searchMessages(query: String) {
        viewModelScope.launch {
            _searchQuery.value = query

            if (query.isEmpty()) {
                loadChats()
                return@launch
            }

            _isLoading.value = true
            _error.value = null

            chatManager.searchMessages(query).onSuccess { messages ->
                val chatIds = messages.map { it.msgId }.distinct()
                val filteredChats = _chats.value.filter { chat ->
                    chatIds.contains(chat.chatId)
                }
                _chats.value = filteredChats
            }.onFailure { e ->
                _error.value = "Search failed: ${e.message}"
            }

            _isLoading.value = false
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        loadChats()
    }

    fun exportChat(chatId: String, exportPath: String) {
        viewModelScope.launch {
            _error.value = null

            chatManager.exportChat(chatId, exportPath).onSuccess {
                // Success feedback
            }.onFailure { e ->
                _error.value = "Export failed: ${e.message}"
            }
        }
    }

    fun importChat(importPath: String, onImported: (String) -> Unit) {
        viewModelScope.launch {
            _error.value = null

            chatManager.importChat(importPath).onSuccess { chatId ->
                loadChats()
                onImported(chatId)
            }.onFailure { e ->
                _error.value = "Import failed: ${e.message}"
            }
        }
    }

    fun openDialog() {
        _isDialogOpen.value = true
    }

    fun closeDialog() {
        _isDialogOpen.value = false
    }

    fun clearError() {
        _error.value = null
    }
}