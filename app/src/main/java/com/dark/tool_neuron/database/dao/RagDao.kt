package com.dark.tool_neuron.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dark.tool_neuron.models.table_schema.InstalledRag
import com.dark.tool_neuron.models.table_schema.RagStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface RagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rag: InstalledRag)

    @Update
    suspend fun update(rag: InstalledRag)

    @Query("DELETE FROM installed_rags WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM installed_rags WHERE id = :id")
    suspend fun getById(id: String): InstalledRag?

    @Query("SELECT * FROM installed_rags ORDER BY created_at DESC")
    fun getAllRags(): Flow<List<InstalledRag>>

    @Query("SELECT * FROM installed_rags ORDER BY created_at DESC")
    suspend fun getAllRagsOnce(): List<InstalledRag>

    @Query("SELECT * FROM installed_rags WHERE status = 'LOADED' ORDER BY last_loaded_at DESC")
    fun getLoadedRags(): Flow<List<InstalledRag>>

    @Query("SELECT * FROM installed_rags WHERE is_enabled = 1 ORDER BY created_at DESC")
    fun getEnabledRags(): Flow<List<InstalledRag>>

    @Query("SELECT * FROM installed_rags WHERE is_enabled = 1")
    suspend fun getEnabledRagsOnce(): List<InstalledRag>

    @Query("UPDATE installed_rags SET status = :status, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: RagStatus, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE installed_rags SET is_enabled = :isEnabled, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateEnabled(id: String, isEnabled: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE installed_rags SET status = 'LOADED', last_loaded_at = :loadedAt, updated_at = :loadedAt WHERE id = :id")
    suspend fun markAsLoaded(id: String, loadedAt: Long = System.currentTimeMillis())

    @Query("UPDATE installed_rags SET status = 'INSTALLED', updated_at = :updatedAt WHERE id = :id")
    suspend fun markAsUnloaded(id: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE installed_rags SET status = 'INSTALLED' WHERE status = 'LOADED'")
    suspend fun unloadAllRags()

    @Query("SELECT COUNT(*) FROM installed_rags")
    suspend fun getRagCount(): Int

    @Query("SELECT COUNT(*) FROM installed_rags WHERE status = 'LOADED'")
    suspend fun getLoadedRagCount(): Int

}