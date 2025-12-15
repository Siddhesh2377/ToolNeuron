// SherpaSTTDatabaseAccessObject.kt
package com.mp.ai_engine.databases.sherpa_stt

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mp.ai_engine.models.llm_models.SherpaSTTDatabaseModel
import kotlinx.coroutines.flow.Flow

@Dao
interface SherpaSTTDatabaseAccessObject {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(model: SherpaSTTDatabaseModel)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(models: List<SherpaSTTDatabaseModel>)

    @Update
    suspend fun update(model: SherpaSTTDatabaseModel)

    @Query("UPDATE sherpa_stt_models SET lastUsedAt = :timestamp WHERE id = :modelId")
    suspend fun updateLastUsed(modelId: String, timestamp: Long = System.currentTimeMillis())

    @Delete
    suspend fun delete(model: SherpaSTTDatabaseModel)

    @Query("DELETE FROM sherpa_stt_models WHERE id = :modelId")
    suspend fun deleteById(modelId: String)

    @Query("DELETE FROM sherpa_stt_models")
    suspend fun deleteAll()

    @Query("SELECT * FROM sherpa_stt_models WHERE id = :modelId LIMIT 1")
    suspend fun getById(modelId: String): SherpaSTTDatabaseModel?

    @Query("SELECT * FROM sherpa_stt_models WHERE id = :modelId LIMIT 1")
    fun getByIdFlow(modelId: String): Flow<SherpaSTTDatabaseModel?>

    @Query("SELECT * FROM sherpa_stt_models WHERE modelName = :modelName LIMIT 1")
    suspend fun getByName(modelName: String): SherpaSTTDatabaseModel?

    @Query("SELECT * FROM sherpa_stt_models ORDER BY lastUsedAt DESC")
    suspend fun getAll(): List<SherpaSTTDatabaseModel>

    @Query("SELECT * FROM sherpa_stt_models ORDER BY lastUsedAt DESC")
    fun getAllFlow(): Flow<List<SherpaSTTDatabaseModel>>

    @Query("SELECT * FROM sherpa_stt_models ORDER BY modelName ASC")
    fun getAllByNameFlow(): Flow<List<SherpaSTTDatabaseModel>>

    @Query("SELECT * FROM sherpa_stt_models ORDER BY createdAt DESC")
    fun getAllByCreatedFlow(): Flow<List<SherpaSTTDatabaseModel>>

    @Query("""
        SELECT * FROM sherpa_stt_models 
        WHERE modelName LIKE '%' || :query || '%' 
        OR modelDescription LIKE '%' || :query || '%'
        ORDER BY lastUsedAt DESC
    """)
    fun searchFlow(query: String): Flow<List<SherpaSTTDatabaseModel>>

    @Query("""
        SELECT * FROM sherpa_stt_models 
        WHERE modelName LIKE '%' || :query || '%' 
        OR modelDescription LIKE '%' || :query || '%'
        ORDER BY lastUsedAt DESC
    """)
    suspend fun search(query: String): List<SherpaSTTDatabaseModel>

    @Query("SELECT COUNT(*) FROM sherpa_stt_models")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM sherpa_stt_models")
    fun getCountFlow(): Flow<Int>

    @Query("SELECT EXISTS(SELECT 1 FROM sherpa_stt_models WHERE id = :modelId)")
    suspend fun exists(modelId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM sherpa_stt_models WHERE modelName = :modelName)")
    suspend fun existsByName(modelName: String): Boolean
}