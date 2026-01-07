package com.dark.tool_neuron.viewmodel.factory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.dark.tool_neuron.viewmodel.ChatListViewModel
import com.dark.tool_neuron.worker.ChatManager

class ChatListViewModelFactory(
    private val chatManager: ChatManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatListViewModel::class.java)) {
            return ChatListViewModel(chatManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}