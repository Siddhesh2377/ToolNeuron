package com.dark.plugins.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dark.plugins.model.PluginLocalDB
import kotlinx.coroutines.flow.Flow

@Dao
interface PluginLocalDBDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlugin(plugin: PluginLocalDB)

    @Query("SELECT * FROM plugins ORDER BY pluginName ASC")
    fun getAll(): Flow<List<PluginLocalDB>>

    @Query("SELECT * FROM plugins WHERE pluginName = :name LIMIT 1")
    suspend fun getByName(name: String): PluginLocalDB?

    @Query("DELETE FROM plugins WHERE pluginName = :name")
    suspend fun deleteByName(name: String)

    @Query("DELETE FROM plugins")
    suspend fun deleteAll()

    @Query("SELECT * FROM plugins WHERE tools LIKE '%' || :toolName || '%'")
    fun getByToolName(toolName: String): Flow<List<PluginLocalDB>>

    @Query("SELECT * FROM plugins")
    suspend fun getAllRaw(): List<PluginLocalDB>
}