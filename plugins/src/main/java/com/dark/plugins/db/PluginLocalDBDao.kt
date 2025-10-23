package com.dark.plugins.db

import androidx.room.*
import com.dark.plugins.model.InstalledPlugin
import kotlinx.coroutines.flow.Flow

@Dao
interface PluginLocalDBDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlugin(plugin: InstalledPlugin)

    @Query("SELECT * FROM plugins ORDER BY pluginName ASC")
    fun getInstalledPlugins(): Flow<List<InstalledPlugin>>

    @Query("SELECT * FROM plugins WHERE pluginName = :name LIMIT 1")
    suspend fun getByName(name: String): InstalledPlugin?

    @Query("DELETE FROM plugins WHERE pluginName = :name")
    suspend fun deleteByName(name: String)

    @Query("DELETE FROM plugins")
    suspend fun deleteInstalledPlugins()

    @Query("SELECT * FROM plugins WHERE tools LIKE '%' || :toolName || '%'")
    fun getByToolName(toolName: String): Flow<List<InstalledPlugin>>

    @Query("SELECT * FROM plugins")
    suspend fun getAllRaw(): List<InstalledPlugin>
}
