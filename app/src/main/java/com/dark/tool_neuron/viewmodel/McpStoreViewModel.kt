package com.dark.tool_neuron.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.models.McpStoreCategories
import com.dark.tool_neuron.models.McpStoreEntry
import com.dark.tool_neuron.models.table_schema.McpServer
import com.dark.tool_neuron.models.table_schema.McpTransportType
import com.dark.tool_neuron.repo.McpServerRepository
import com.dark.tool_neuron.repo.McpStoreRepository
import com.dark.tool_neuron.service.TermuxBridge
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class McpStoreViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val storeRepository: McpStoreRepository,
    private val mcpServerRepository: McpServerRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow(McpStoreCategories.ALL)
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _installedIds = MutableStateFlow<Set<String>>(emptySet())

    private val _installMessage = MutableStateFlow<String?>(null)
    val installMessage: StateFlow<String?> = _installMessage.asStateFlow()

    private val _showTermuxDialog = MutableStateFlow(false)
    val showTermuxDialog: StateFlow<Boolean> = _showTermuxDialog.asStateFlow()

    private val _pendingTermuxEntry = MutableStateFlow<McpStoreEntry?>(null)
    val pendingTermuxEntry: StateFlow<McpStoreEntry?> = _pendingTermuxEntry.asStateFlow()

    val isLoading = storeRepository.isLoading
    val error = storeRepository.error

    val filteredEntries: StateFlow<List<McpStoreEntry>> = combine(
        storeRepository.entries,
        _searchQuery,
        _selectedCategory
    ) { entries, query, category ->
        storeRepository.filterEntries(entries, category, query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val installedIds: StateFlow<Set<String>> = _installedIds.asStateFlow()

    val isTermuxInstalled: Boolean
        get() = TermuxBridge.isTermuxInstalled(appContext)

    init {
        viewModelScope.launch {
            storeRepository.loadEntries()
            refreshInstalledIds()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedCategory(category: String) {
        _selectedCategory.value = category
    }

    fun refresh() {
        viewModelScope.launch {
            storeRepository.refresh()
            refreshInstalledIds()
        }
    }

    fun clearError() {
        storeRepository.clearError()
    }

    fun clearInstallMessage() {
        _installMessage.value = null
    }

    fun dismissTermuxDialog() {
        _showTermuxDialog.value = false
        _pendingTermuxEntry.value = null
    }

    /**
     * Install an MCP store entry as a local McpServer configuration.
     */
    fun installEntry(entry: McpStoreEntry) {
        if (entry.requiresTermux && !TermuxBridge.isTermuxInstalled(appContext)) {
            _pendingTermuxEntry.value = entry
            _showTermuxDialog.value = true
            return
        }

        viewModelScope.launch {
            try {
                val url = if (entry.requiresTermux && entry.defaultPort != null) {
                    TermuxBridge.getLocalServerUrl(entry.defaultPort)
                } else {
                    entry.url
                }

                val transportType = try {
                    McpTransportType.valueOf(entry.transportType)
                } catch (e: Exception) {
                    McpTransportType.SSE
                }

                val server = McpServer(
                    id = McpServer.generateId(),
                    name = entry.name,
                    url = url,
                    transportType = transportType,
                    isEnabled = !entry.requiresTermux,
                    description = entry.description,
                    isLocal = entry.requiresTermux,
                    sourceStoreId = entry.id
                )
                mcpServerRepository.addServerDirect(server)
                _installedIds.value = _installedIds.value + entry.id

                if (entry.requiresTermux && entry.pipPackage != null) {
                    TermuxBridge.pipInstall(appContext, entry.pipPackage)
                    _installMessage.value = "Installed ${entry.name}. Installing pip package in Termux..."
                } else {
                    _installMessage.value = "${entry.name} added to your MCP servers"
                }
            } catch (e: Exception) {
                _installMessage.value = "Failed to install ${entry.name}: ${e.message}"
            }
        }
    }

    /**
     * Proceed with Termux entry installation after user acknowledges the dialog.
     */
    fun proceedWithTermuxInstall() {
        val entry = _pendingTermuxEntry.value ?: return
        _showTermuxDialog.value = false
        _pendingTermuxEntry.value = null
        if (TermuxBridge.isTermuxInstalled(appContext)) {
            installEntry(entry)
        }
    }

    fun openTermuxDownload(context: Context) {
        try {
            val intent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(TermuxBridge.GITHUB_URL)
            )
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            _installMessage.value = "Could not open browser. Visit ${TermuxBridge.GITHUB_URL}"
        }
    }

    private suspend fun refreshInstalledIds() {
        val servers = mcpServerRepository.getAllServersSnapshot()
        _installedIds.value = servers.mapNotNull { it.sourceStoreId }.toSet()
    }
}
