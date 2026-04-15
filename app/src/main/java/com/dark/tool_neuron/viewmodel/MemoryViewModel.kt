package com.dark.tool_neuron.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.worker.RagVaultIntegration
import com.dark.tool_neuron.worker.ScoredVaultContent
import com.dark.tool_neuron.worker.VaultStatsInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val ragVaultIntegration: RagVaultIntegration
) : ViewModel() {

    companion object {
        private const val TAG = "MemoryViewModel"
    }

    private val _isMemoryEnabled = MutableStateFlow(false)
    val isMemoryEnabled: StateFlow<Boolean> = _isMemoryEnabled.asStateFlow()

    private val _memoryResults = MutableStateFlow<List<ScoredVaultContent>>(emptyList())
    val memoryResults: StateFlow<List<ScoredVaultContent>> = _memoryResults.asStateFlow()

    private val _vaultStats = MutableStateFlow<VaultStatsInfo?>(null)
    val vaultStats: StateFlow<VaultStatsInfo?> = _vaultStats.asStateFlow()

    private val _showMemoryOverlay = MutableStateFlow(false)
    val showMemoryOverlay: StateFlow<Boolean> = _showMemoryOverlay.asStateFlow()

    private val _memoryEntryCount = MutableStateFlow(0)
    val memoryEntryCount: StateFlow<Int> = _memoryEntryCount.asStateFlow()

    @Volatile private var isVaultInitialized = false

    init {
        initializeVault()
    }

    private fun initializeVault() {
        viewModelScope.launch {
            try {
                ragVaultIntegration.initialize()
                isVaultInitialized = true
                refreshStats()
                Log.d(TAG, "Memory vault initialized")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize memory vault", e)
            }
        }
    }

    fun setMemoryEnabled(enabled: Boolean) {
        _isMemoryEnabled.value = enabled
        if (enabled && !isVaultInitialized) {
            initializeVault()
        }
    }

    fun toggleMemoryOverlay() {
        _showMemoryOverlay.value = !_showMemoryOverlay.value
    }

    fun dismissMemoryOverlay() {
        _showMemoryOverlay.value = false
    }

    fun refreshStats() {
        viewModelScope.launch {
            try {
                val stats = ragVaultIntegration.getVaultStats()
                _vaultStats.value = stats
                _memoryEntryCount.value = stats?.totalItems ?: 0
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing vault stats", e)
            }
        }
    }

}
