// SherpaTTSDatabaseAccessObject.kt
package com.mp.ai_engine.databases.sherpa_tts

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mp.ai_engine.models.llm_models.SherpaTTSDatabaseModel
import kotlinx.coroutines.flow.Flow

@Dao
interface SherpaTTSDatabaseAccessObject {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(model: SherpaTTSDatabaseModel)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(models: List<SherpaTTSDatabaseModel>)

    @Update
    suspend fun update(model: SherpaTTSDatabaseModel)

    @Query("UPDATE sherpa_tts_models SET lastUsedAt = :timestamp WHERE id = :modelId")
    suspend fun updateLastUsed(modelId: String, timestamp: Long = System.currentTimeMillis())

    @Delete
    suspend fun delete(model: SherpaTTSDatabaseModel)

    @Query("DELETE FROM sherpa_tts_models WHERE id = :modelId")
    suspend fun deleteById(modelId: String)

    @Query("DELETE FROM sherpa_tts_models")
    suspend fun deleteAll()

    @Query("SELECT * FROM sherpa_tts_models WHERE id = :modelId LIMIT 1")
    suspend fun getById(modelId: String): SherpaTTSDatabaseModel?

    @Query("SELECT * FROM sherpa_tts_models WHERE id = :modelId LIMIT 1")
    fun getByIdFlow(modelId: String): Flow<SherpaTTSDatabaseModel?>

    @Query("SELECT * FROM sherpa_tts_models WHERE modelName = :modelName LIMIT 1")
    suspend fun getByName(modelName: String): SherpaTTSDatabaseModel?

    @Query("SELECT * FROM sherpa_tts_models ORDER BY lastUsedAt DESC")
    suspend fun getAll(): List<SherpaTTSDatabaseModel>

    @Query("SELECT * FROM sherpa_tts_models ORDER BY lastUsedAt DESC")
    fun getAllFlow(): Flow<List<SherpaTTSDatabaseModel>>

    @Query("SELECT * FROM sherpa_tts_models ORDER BY modelName ASC")
    fun getAllByNameFlow(): Flow<List<SherpaTTSDatabaseModel>>

    @Query("SELECT * FROM sherpa_tts_models ORDER BY createdAt DESC")
    fun getAllByCreatedFlow(): Flow<List<SherpaTTSDatabaseModel>>

    @Query("""
        SELECT * FROM sherpa_tts_models 
        WHERE modelName LIKE '%' || :query || '%' 
        OR modelDescription LIKE '%' || :query || '%'
        ORDER BY lastUsedAt DESC
    """)
    fun searchFlow(query: String): Flow<List<SherpaTTSDatabaseModel>>

    @Query("""
        SELECT * FROM sherpa_tts_models 
        WHERE modelName LIKE '%' || :query || '%' 
        OR modelDescription LIKE '%' || :query || '%'
        ORDER BY lastUsedAt DESC
    """)
    suspend fun search(query: String): List<SherpaTTSDatabaseModel>

    @Query("SELECT COUNT(*) FROM sherpa_tts_models")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM sherpa_tts_models")
    fun getCountFlow(): Flow<Int>

    @Query("SELECT EXISTS(SELECT 1 FROM sherpa_tts_models WHERE id = :modelId)")
    suspend fun exists(modelId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM sherpa_tts_models WHERE modelName = :modelName)")
    suspend fun existsByName(modelName: String): Boolean
}