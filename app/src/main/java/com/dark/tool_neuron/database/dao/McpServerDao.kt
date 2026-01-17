package com.dark.tool_neuron.database.dao

import androidx.room.*
import com.dark.tool_neuron.models.table_schema.McpServer
import kotlinx.coroutines.flow.Flow

@Dao
interface McpServerDao {
    
    @Query("SELECT * FROM mcp_servers ORDER BY name ASC")
    fun getAllServers(): Flow<List<McpServer>>
    
    @Query("SELECT * FROM mcp_servers WHERE isEnabled = 1 ORDER BY name ASC")
    fun getEnabledServers(): Flow<List<McpServer>>
    
    @Query("SELECT * FROM mcp_servers WHERE id = :id")
    suspend fun getServerById(id: String): McpServer?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: McpServer)
    
    @Update
    suspend fun updateServer(server: McpServer)
    
    @Delete
    suspend fun deleteServer(server: McpServer)
    
    @Query("DELETE FROM mcp_servers WHERE id = :id")
    suspend fun deleteServerById(id: String)
    
    @Query("UPDATE mcp_servers SET isEnabled = :isEnabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateServerEnabled(id: String, isEnabled: Boolean, updatedAt: Long)
    
    @Query("UPDATE mcp_servers SET lastConnectedAt = :timestamp, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateLastConnected(id: String, timestamp: Long, updatedAt: Long)
    
    @Query("SELECT COUNT(*) FROM mcp_servers")
    fun getServerCount(): Flow<Int>
    
    @Query("SELECT COUNT(*) FROM mcp_servers WHERE isEnabled = 1")
    fun getEnabledServerCount(): Flow<Int>
}
