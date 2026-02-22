package com.dark.tool_neuron.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.engine.EmbeddingEngine
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
    private val ragVaultIntegration: RagVaultIntegration,
    private val embeddingEngine: EmbeddingEngine
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

    private val _isQuerying = MutableStateFlow(false)
    val isQuerying: StateFlow<Boolean> = _isQuerying.asStateFlow()

    private val _showMemoryOverlay = MutableStateFlow(false)
    val showMemoryOverlay: StateFlow<Boolean> = _showMemoryOverlay.asStateFlow()

    private val _memoryEntryCount = MutableStateFlow(0)
    val memoryEntryCount: StateFlow<Int> = _memoryEntryCount.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var isVaultInitialized = false

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
                _error.value = "Failed to initialize memory vault: ${e.message}"
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

    /**
     * Query the memory vault semantically and return a formatted context string
     * for injecting into the LLM prompt.
     */
    suspend fun queryMemory(query: String, limit: Int = 5): String {
        if (!isVaultInitialized || !_isMemoryEnabled.value) return ""
        if (!embeddingEngine.isInitialized()) {
            Log.w(TAG, "Embedding engine not initialized, cannot query memory")
            return ""
        }

        _isQuerying.value = true
        return try {
            val results = ragVaultIntegration.searchVaultSemantically(query, limit)
            _memoryResults.value = results

            if (results.isEmpty()) {
                Log.d(TAG, "No memory results for query: $query")
                ""
            } else {
                Log.d(TAG, "Found ${results.size} memory results for query")
                buildMemoryContext(results)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying memory", e)
            _error.value = "Memory query failed: ${e.message}"
            ""
        } finally {
            _isQuerying.value = false
        }
    }

    fun clearMemoryResults() {
        _memoryResults.value = emptyList()
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

    fun clearError() {
        _error.value = null
    }

    private fun buildMemoryContext(results: List<ScoredVaultContent>): String {
        val builder = StringBuilder()
        builder.append("### Relevant Memories from Personal Knowledge Vault:\n\n")
        for ((index, result) in results.withIndex()) {
            val scorePercent = (result.score * 100).toInt()
            val contentPreview = result.content.take(500)
            builder.append("${index + 1}. [$scorePercent% match] $contentPreview\n")
            result.category?.let { builder.append("   Category: $it\n") }
            if (result.tags.isNotEmpty()) {
                builder.append("   Tags: ${result.tags.joinToString(", ")}\n")
            }
            builder.append("\n")
        }
        return builder.toString()
    }
}
