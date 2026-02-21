package com.dark.tool_neuron.repo

import android.content.Context
import android.util.Log
import com.dark.tool_neuron.models.McpStoreCategories
import com.dark.tool_neuron.models.McpStoreEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for fetching and managing MCP Store registry entries.
 * Loads a bundled fallback from assets and can refresh from a remote URL.
 */
@Singleton
class McpStoreRepository @Inject constructor(
    private val context: Context,
    private val mcpServerRepository: McpServerRepository
) {
    companion object {
        private const val TAG = "McpStoreRepository"
        private const val REGISTRY_ASSET = "mcp-registry.json"
        private const val REMOTE_REGISTRY_URL =
            "https://raw.githubusercontent.com/Siddhesh2377/ToolNeuron/re-write/app/src/main/assets/mcp-registry.json"
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient()

    private val _entries = MutableStateFlow<List<McpStoreEntry>>(emptyList())
    val entries: StateFlow<List<McpStoreEntry>> = _entries.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Load registry entries. Tries remote first, falls back to bundled asset.
     */
    suspend fun loadEntries() {
        if (_entries.value.isNotEmpty() && !_isLoading.value) return
        _isLoading.value = true
        _error.value = null
        try {
            val remote = fetchRemoteRegistry()
            if (remote != null && remote.isNotEmpty()) {
                _entries.value = remote
                Log.d(TAG, "Loaded ${remote.size} entries from remote registry")
            } else {
                val local = loadBundledRegistry()
                _entries.value = local
                Log.d(TAG, "Loaded ${local.size} entries from bundled registry")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load registry, falling back to bundled", e)
            try {
                _entries.value = loadBundledRegistry()
            } catch (e2: Exception) {
                _error.value = "Failed to load MCP store: ${e2.message}"
                Log.e(TAG, "Failed to load bundled registry", e2)
            }
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Force refresh from remote registry.
     */
    suspend fun refresh() {
        _entries.value = emptyList()
        loadEntries()
    }

    /**
     * Filter entries by category and search query.
     */
    fun filterEntries(
        entries: List<McpStoreEntry>,
        category: String,
        searchQuery: String
    ): List<McpStoreEntry> {
        return entries.filter { entry ->
            val matchesCategory = category == McpStoreCategories.ALL ||
                    entry.category.equals(category, ignoreCase = true) ||
                    (category == McpStoreCategories.LOCAL && entry.requiresTermux)
            val matchesSearch = searchQuery.isBlank() ||
                    entry.name.contains(searchQuery, ignoreCase = true) ||
                    entry.description.contains(searchQuery, ignoreCase = true) ||
                    entry.tags.any { it.contains(searchQuery, ignoreCase = true) }
            matchesCategory && matchesSearch
        }
    }

    /**
     * Check if a store entry is already installed as an MCP server.
     */
    suspend fun isInstalled(storeEntryId: String): Boolean {
        // Check all servers for a matching sourceStoreId
        val servers = mcpServerRepository.getAllServersSnapshot()
        return servers.any { it.sourceStoreId == storeEntryId }
    }

    fun clearError() {
        _error.value = null
    }

    private suspend fun fetchRemoteRegistry(): List<McpStoreEntry>? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(REMOTE_REGISTRY_URL).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext null
                json.decodeFromString<List<McpStoreEntry>>(body)
            } else {
                Log.w(TAG, "Remote registry returned ${response.code}")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not fetch remote registry: ${e.message}")
            null
        }
    }

    private suspend fun loadBundledRegistry(): List<McpStoreEntry> = withContext(Dispatchers.IO) {
        val inputStream = context.assets.open(REGISTRY_ASSET)
        val jsonString = inputStream.bufferedReader().use { it.readText() }
        json.decodeFromString(jsonString)
    }
}
