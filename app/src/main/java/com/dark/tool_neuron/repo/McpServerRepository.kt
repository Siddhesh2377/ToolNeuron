package com.dark.tool_neuron.repo

import android.util.Log
import com.dark.tool_neuron.database.dao.McpServerDao
import com.dark.tool_neuron.models.table_schema.McpConnectionStatus
import com.dark.tool_neuron.models.table_schema.McpServer
import com.dark.tool_neuron.models.table_schema.McpTransportType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing MCP (Model Context Protocol) server configurations
 */
@Singleton
class McpServerRepository @Inject constructor(
    private val mcpServerDao: McpServerDao
) {
    companion object {
        private const val TAG = "McpServerRepository"
    }
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
     * @throws IllegalArgumentException if the URL is not valid
     */
    suspend fun addServer(
        name: String,
        url: String,
        transportType: McpTransportType = McpTransportType.SSE,
        apiKey: String? = null,
        description: String = ""
    ): McpServer {
        val trimmedUrl = url.trim()
        
        // Validate URL format
        val validatedUrl = try {
            val uri = URI(trimmedUrl)
            if (uri.scheme.isNullOrBlank() || uri.host.isNullOrBlank()) {
                throw IllegalArgumentException("Invalid server URL: missing scheme or host")
            }
            if (uri.scheme != "http" && uri.scheme != "https") {
                throw IllegalArgumentException("Invalid server URL scheme: ${uri.scheme}")
            }
            trimmedUrl
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid server URL format: '$trimmedUrl'", e)
        }
        
        val server = McpServer(
            id = McpServer.generateId(),
            name = name,
            url = validatedUrl,
            transportType = transportType,
            apiKey = apiKey?.trim()?.takeIf { it.isNotEmpty() },
            description = description.trim(),
            isEnabled = true,
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
        mcpServerDao.updateServerEnabled(id, enabled, System.currentTimeMillis())
        if (!enabled) {
            // When disabled, set status to disconnected
            updateConnectionStatus(id, McpConnectionStatus.DISCONNECTED)
        }
    }

    /**
     * Update the runtime connection status of a server
     * @param serverId The ID of the server
     * @param status The new connection status
     * @param error Optional error message when status is ERROR
     */
    fun updateConnectionStatus(serverId: String, status: McpConnectionStatus, error: String? = null) {
        if (error != null && status == McpConnectionStatus.ERROR) {
            Log.w(TAG, "MCP server $serverId connection error: $error")
        }
        _connectionStatuses.value = _connectionStatuses.value + (serverId to status)
    }

    /**
     * Update last connected timestamp
     */
    suspend fun updateLastConnected(id: String) {
        val now = System.currentTimeMillis()
        mcpServerDao.updateLastConnected(id, now, now)
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
