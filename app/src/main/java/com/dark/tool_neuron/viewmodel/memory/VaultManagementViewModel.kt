package com.dark.tool_neuron.viewmodel.memory

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.data.VaultManager
import com.dark.tool_neuron.models.vault.ChatInfo
import com.dark.tool_neuron.models.vault.VaultStatistics
import kotlinx.coroutines.launch

class VaultManagementViewModel : ViewModel() {
    var vaultStats by mutableStateOf<VaultStatistics?>(null)
        private set

    var isLoading by mutableStateOf(false)
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
                val chatRepo = VaultManager.chatRepo ?: return@launch
                vaultStats = chatRepo.getVaultStats()
            } catch (e: Exception) {
                Log.e("VaultManagementVM", "Failed to load vault stats", e)
            } finally {
                isLoading = false
            }
        }
    }

    fun loadChatList() {
        viewModelScope.launch {
            try {
                isLoading = true
                val chatRepo = VaultManager.chatRepo ?: return@launch
                chatList = chatRepo.getAllChats()
            } catch (e: Exception) {
                Log.e("VaultManagementVM", "Failed to load chats", e)
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
                // UMS handles its own WAL compaction; no manual defrag needed
                defragProgress = 1f
                loadVaultStats()
            } catch (e: Exception) {
                Log.e("VaultManagementVM", "Defragmentation failed", e)
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
                val chatRepo = VaultManager.chatRepo ?: return@launch
                chatRepo.deleteChat(chatId)
                loadChatList()
            } catch (e: Exception) {
                Log.e("VaultManagementVM", "Failed to delete chat", e)
            } finally {
                isLoading = false
            }
        }
    }

}
