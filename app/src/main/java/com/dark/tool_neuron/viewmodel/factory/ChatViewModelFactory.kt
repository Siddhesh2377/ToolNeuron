package com.dark.tool_neuron.viewmodel.factory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.dark.tool_neuron.repo.McpServerRepository
import com.dark.tool_neuron.service.McpClientService
import com.dark.tool_neuron.viewmodel.ChatViewModel
import com.dark.tool_neuron.worker.ChatManager
import com.dark.tool_neuron.worker.GenerationManager

class ChatViewModelFactory(
    private val chatManager: ChatManager,
    private val generationManager: GenerationManager,
    private val mcpServerRepository: McpServerRepository,
    private val mcpClientService: McpClientService
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            return ChatViewModel(
                chatManager,
                generationManager,
                mcpServerRepository,
                mcpClientService
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
