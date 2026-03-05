package com.dark.tool_neuron.viewmodel.factory

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.dark.tool_neuron.viewmodel.ChatViewModel
import com.dark.tool_neuron.worker.ChatManager

class ChatViewModelFactory(
    private val context: Context,
    private val chatManager: ChatManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            return ChatViewModel(context, chatManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
