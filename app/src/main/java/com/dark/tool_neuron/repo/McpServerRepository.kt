package com.dark.tool_neuron.repo

import com.dark.tool_neuron.database.dao.McpServerDao
import com.dark.tool_neuron.models.table_schema.McpConnectionStatus
import com.dark.tool_neuron.models.table_schema.McpServer
import com.dark.tool_neuron.models.table_schema.McpTransportType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing MCP (Model Context Protocol) server configurations
 */
@Singleton
class McpServerRepository @Inject constructor(
    private val mcpServerDao: McpServerDao
) {
    // Runtime connection status tracking (not persisted)
    private val _connectionStatuses = MutableStateFlow<Map<String, McpConnectionStatus>>(emptyMap())
    val connectionStatuses: StateFlow<Map<String, McpConnectionStatus>> = _connectionStatuses.asStateFlow()

    /**
     * Get all configured MCP servers
     */
    fun getAllServers(): Flow<List<McpServer>> = mcpServerDao.getAllServers()

    /**
     * Get only enabled MCP servers
     */
    fun getEnabledServers(): Flow<List<McpServer>> = mcpServerDao.getEnabledServers()

    /**
     * Get a specific server by ID
     */
    suspend fun getServerById(id: String): McpServer? = mcpServerDao.getServerById(id)

    /**
     * Add a new MCP server
     */
    suspend fun addServer(
        name: String,
        url: String,
        transportType: McpTransportType = McpTransportType.SSE,
        apiKey: String? = null,
        description: String = ""
    ): McpServer {
        val server = McpServer(
            id = McpServer.generateId(),
            name = name,
            url = url.trim(),
            transportType = transportType,
            apiKey = apiKey?.trim()?.takeIf { it.isNotEmpty() },
            description = description.trim(),
            isEnabled = true,
            connectionStatus = McpConnectionStatus.DISCONNECTED,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        mcpServerDao.insertServer(server)
        return server
    }

    /**
     * Update an existing MCP server
     */
    suspend fun updateServer(server: McpServer) {
        mcpServerDao.updateServer(server.copy(updatedAt = System.currentTimeMillis()))
    }

    /**
     * Delete an MCP server
     */
    suspend fun deleteServer(id: String) {
        mcpServerDao.deleteServerById(id)
        // Remove from runtime status tracking
        _connectionStatuses.value = _connectionStatuses.value - id
    }

    /**
     * Toggle server enabled/disabled state
     */
    suspend fun setServerEnabled(id: String, enabled: Boolean) {
        mcpServerDao.updateServerEnabled(id, enabled)
        if (!enabled) {
            // When disabled, set status to disconnected
            updateConnectionStatus(id, McpConnectionStatus.DISCONNECTED)
        }
    }

    /**
     * Update the runtime connection status of a server
     */
    fun updateConnectionStatus(serverId: String, status: McpConnectionStatus, error: String? = null) {
        _connectionStatuses.value = _connectionStatuses.value + (serverId to status)
    }

    /**
     * Update last connected timestamp
     */
    suspend fun updateLastConnected(id: String) {
        mcpServerDao.updateLastConnected(id, System.currentTimeMillis())
    }

    /**
     * Get the count of all servers
     */
    fun getServerCount(): Flow<Int> = mcpServerDao.getServerCount()

    /**
     * Get the count of enabled servers
     */
    fun getEnabledServerCount(): Flow<Int> = mcpServerDao.getEnabledServerCount()

    /**
     * Get the current connection status for a server
     */
    fun getConnectionStatus(serverId: String): McpConnectionStatus {
        return _connectionStatuses.value[serverId] ?: McpConnectionStatus.DISCONNECTED
    }
}
