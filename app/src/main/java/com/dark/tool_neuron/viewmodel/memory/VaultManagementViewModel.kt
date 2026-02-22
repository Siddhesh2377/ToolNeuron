package com.dark.tool_neuron.viewmodel.memory

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.models.messages.Messages
import com.dark.tool_neuron.models.vault.ChatInfo
import com.dark.tool_neuron.vault.VaultHelper
import com.memoryvault.core.VaultStats
import kotlinx.coroutines.launch

class VaultManagementViewModel : ViewModel() {
    var vaultStats by mutableStateOf<VaultStats?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var showError by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf("")
        private set

    var defragProgress by mutableStateOf(0f)
        private set

    var isDefragging by mutableStateOf(false)
        private set

    var chatList by mutableStateOf<List<ChatInfo>>(emptyList())
        private set

    fun loadVaultStats() {
        viewModelScope.launch {
            try {
                isLoading = true
                vaultStats = VaultHelper.getVault().getStats()
            } catch (e: Exception) {
                showError = true
                errorMessage = e.message ?: "Failed to load vault stats"
            } finally {
                isLoading = false
            }
        }
    }

    fun loadChatList() {
        viewModelScope.launch {
            try {
                isLoading = true
                chatList = VaultHelper.getAllChats()
            } catch (e: Exception) {
                showError = true
                errorMessage = e.message ?: "Failed to load chats"
            } finally {
                isLoading = false
            }
        }
    }

    fun performDefragmentation() {
        viewModelScope.launch {
            try {
                isDefragging = true
                defragProgress = 0f
                VaultHelper.performMaintenance()
                defragProgress = 1f
                loadVaultStats()
            } catch (e: Exception) {
                showError = true
                errorMessage = e.message ?: "Defragmentation failed"
            } finally {
                isDefragging = false
                defragProgress = 0f
            }
        }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            try {
                isLoading = true
                VaultHelper.deleteChat(chatId)
                loadChatList()
            } catch (e: Exception) {
                showError = true
                errorMessage = e.message ?: "Failed to delete chat"
            } finally {
                isLoading = false
            }
        }
    }

    fun dismissError() {
        showError = false
        errorMessage = ""
    }
}
