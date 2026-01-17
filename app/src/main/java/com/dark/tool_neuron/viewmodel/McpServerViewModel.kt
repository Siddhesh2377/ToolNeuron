package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.models.table_schema.McpConnectionStatus
import com.dark.tool_neuron.models.table_schema.McpServer
import com.dark.tool_neuron.models.table_schema.McpTransportType
import com.dark.tool_neuron.repo.McpServerRepository
import com.dark.tool_neuron.service.McpClientService
import com.dark.tool_neuron.service.McpTestResult
import com.dark.tool_neuron.service.McpToolInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for a single MCP server with runtime status
 */
data class McpServerUiState(
    val server: McpServer,
    val connectionStatus: McpConnectionStatus = McpConnectionStatus.DISCONNECTED,
    val isTesting: Boolean = false,
    val lastTestResult: McpTestResult? = null
)

/**
 * ViewModel for managing MCP (Model Context Protocol) servers
 */
@HiltViewModel
class McpServerViewModel @Inject constructor(
    private val repository: McpServerRepository,
    private val mcpClientService: McpClientService
) : ViewModel() {
    
    // All servers with their runtime status
    val servers: StateFlow<List<McpServerUiState>> = combine(
        repository.getAllServers(),
        repository.connectionStatuses
    ) { servers, statuses ->
        servers.map { server ->
            McpServerUiState(
                server = server,
                connectionStatus = statuses[server.id] ?: McpConnectionStatus.DISCONNECTED
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    // Server count
    val serverCount: StateFlow<Int> = repository.getServerCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    
    // Enabled server count
    val enabledServerCount: StateFlow<Int> = repository.getEnabledServerCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    
    // Currently selected server for editing
    private val _selectedServer = MutableStateFlow<McpServer?>(null)
    val selectedServer: StateFlow<McpServer?> = _selectedServer.asStateFlow()
    
    // Dialog state
    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog: StateFlow<Boolean> = _showAddDialog.asStateFlow()
    
    private val _showEditDialog = MutableStateFlow(false)
    val showEditDialog: StateFlow<Boolean> = _showEditDialog.asStateFlow()
    
    // Test result for the current dialog
    private val _testingServerId = MutableStateFlow<String?>(null)
    val testingServerId: StateFlow<String?> = _testingServerId.asStateFlow()
    
    private val _testResult = MutableStateFlow<McpTestResult?>(null)
    val testResult: StateFlow<McpTestResult?> = _testResult.asStateFlow()
    
    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // Error state
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    /**
     * Show the add server dialog
     */
    fun showAddServerDialog() {
        _selectedServer.value = null
        _testResult.value = null
        _showAddDialog.value = true
    }
    
    /**
     * Hide the add server dialog
     */
    fun hideAddServerDialog() {
        _showAddDialog.value = false
        _testResult.value = null
    }
    
    /**
     * Show the edit server dialog
     */
    fun showEditServerDialog(server: McpServer) {
        _selectedServer.value = server
        _testResult.value = null
        _showEditDialog.value = true
    }
    
    /**
     * Hide the edit server dialog
     */
    fun hideEditServerDialog() {
        _showEditDialog.value = false
        _selectedServer.value = null
        _testResult.value = null
    }
    
    /**
     * Add a new MCP server
     */
    fun addServer(
        name: String,
        url: String,
        transportType: McpTransportType = McpTransportType.SSE,
        apiKey: String? = null,
        description: String = ""
    ) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                repository.addServer(name, url, transportType, apiKey, description)
                hideAddServerDialog()
            } catch (e: Exception) {
                _error.value = "Failed to add server: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Update an existing MCP server
     */
    fun updateServer(server: McpServer) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                repository.updateServer(server)
                hideEditServerDialog()
            } catch (e: Exception) {
                _error.value = "Failed to update server: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Delete an MCP server
     */
    fun deleteServer(serverId: String) {
        viewModelScope.launch {
            try {
                repository.deleteServer(serverId)
            } catch (e: Exception) {
                _error.value = "Failed to delete server: ${e.message}"
            }
        }
    }
    
    /**
     * Toggle server enabled state
     */
    fun toggleServerEnabled(serverId: String, enabled: Boolean) {
        viewModelScope.launch {
            try {
                repository.setServerEnabled(serverId, enabled)
            } catch (e: Exception) {
                _error.value = "Failed to update server: ${e.message}"
            }
        }
    }
    
    /**
     * Test connection to a server
     */
    fun testConnection(server: McpServer) {
        viewModelScope.launch {
            try {
                _testingServerId.value = server.id
                _testResult.value = null
                repository.updateConnectionStatus(server.id, McpConnectionStatus.CONNECTING)
                
                val result = mcpClientService.testConnection(server)
                _testResult.value = result
                
                if (result.success) {
                    repository.updateConnectionStatus(server.id, McpConnectionStatus.CONNECTED)
                    repository.updateLastConnected(server.id)
                } else {
                    repository.updateConnectionStatus(server.id, McpConnectionStatus.ERROR, result.message)
                }
            } catch (e: Exception) {
                _testResult.value = McpTestResult(
                    success = false,
                    message = "Test failed: ${e.message}"
                )
                repository.updateConnectionStatus(server.id, McpConnectionStatus.ERROR, e.message)
            } finally {
                _testingServerId.value = null
            }
        }
    }
    
    /**
     * Test connection with provided parameters (for add/edit dialog)
     */
    fun testConnectionWithParams(
        name: String,
        url: String,
        transportType: McpTransportType,
        apiKey: String?
    ) {
        viewModelScope.launch {
            try {
                _testingServerId.value = "new"
                _testResult.value = null
                
                val tempServer = McpServer(
                    id = "test",
                    name = name,
                    url = url,
                    transportType = transportType,
                    apiKey = apiKey?.takeIf { it.isNotBlank() }
                )
                
                val result = mcpClientService.testConnection(tempServer)
                _testResult.value = result
            } catch (e: Exception) {
                _testResult.value = McpTestResult(
                    success = false,
                    message = "Test failed: ${e.message}"
                )
            } finally {
                _testingServerId.value = null
            }
        }
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _error.value = null
    }
    
    /**
     * Clear test result
     */
    fun clearTestResult() {
        _testResult.value = null
    }
}
