package com.mp.ai_engine.databases.gguf

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mp.ai_engine.models.llm_models.GGUFDatabaseModel
import com.mp.ai_engine.models.llm_models.ModelType
import kotlinx.coroutines.flow.Flow

@Dao
interface GGUFDatabaseAccessObject {

    // ========== INSERT ==========

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(model: GGUFDatabaseModel)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(models: List<GGUFDatabaseModel>)

    // ========== UPDATE ==========

    @Update
    suspend fun update(model: GGUFDatabaseModel)

    @Query("UPDATE gguf_models SET lastUsedAt = :timestamp WHERE id = :modelId")
    suspend fun updateLastUsed(modelId: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE gguf_models SET gpuLayers = :gpuLayers WHERE id = :modelId")
    suspend fun updateGpuLayers(modelId: String, gpuLayers: Int)

    @Query("UPDATE gguf_models SET threads = :threads WHERE id = :modelId")
    suspend fun updateThreads(modelId: String, threads: Int)

    @Query("UPDATE gguf_models SET `temp` = :temp, topP = :topP, topK = :topK WHERE id = :modelId")
    suspend fun updateSamplingParams(modelId: String, temp: Float, topP: Float, topK: Int)

    @Query("UPDATE gguf_models SET systemPrompt = :systemPrompt WHERE id = :modelId")
    suspend fun updateSystemPrompt(modelId: String, systemPrompt: String)

    // ========== DELETE ==========

    @Delete
    suspend fun delete(model: GGUFDatabaseModel)

    @Query("DELETE FROM gguf_models WHERE id = :modelId")
    suspend fun deleteById(modelId: String)

    @Query("DELETE FROM gguf_models")
    suspend fun deleteAll()

    @Query("DELETE FROM gguf_models WHERE isImported = 1")
    suspend fun deleteImported()

    // ========== QUERY - SINGLE ==========

    @Query("SELECT * FROM gguf_models WHERE id = :modelId LIMIT 1")
    suspend fun getById(modelId: String): GGUFDatabaseModel?

    @Query("SELECT * FROM gguf_models WHERE id = :modelId LIMIT 1")
    fun getByIdFlow(modelId: String): Flow<GGUFDatabaseModel?>

    @Query("SELECT * FROM gguf_models WHERE modelName = :modelName LIMIT 1")
    suspend fun getByName(modelName: String): GGUFDatabaseModel?

    // ========== QUERY - ALL ==========

    @Query("SELECT * FROM gguf_models ORDER BY lastUsedAt DESC")
    suspend fun getAll(): List<GGUFDatabaseModel>

    @Query("SELECT * FROM gguf_models ORDER BY lastUsedAt DESC")
    fun getAllFlow(): Flow<List<GGUFDatabaseModel>>

    @Query("SELECT * FROM gguf_models ORDER BY modelName ASC")
    fun getAllByNameFlow(): Flow<List<GGUFDatabaseModel>>

    @Query("SELECT * FROM gguf_models ORDER BY createdAt DESC")
    fun getAllByCreatedFlow(): Flow<List<GGUFDatabaseModel>>

    // ========== QUERY - BY TYPE ==========

    @Query("SELECT * FROM gguf_models WHERE modelType = :type ORDER BY lastUsedAt DESC")
    suspend fun getByType(type: ModelType): List<GGUFDatabaseModel>

    @Query("SELECT * FROM gguf_models WHERE modelType = :type ORDER BY lastUsedAt DESC")
    fun getByTypeFlow(type: ModelType): Flow<List<GGUFDatabaseModel>>

    @Query("SELECT * FROM gguf_models WHERE modelType = 'TEXT' ORDER BY lastUsedAt DESC")
    fun getTextModelsFlow(): Flow<List<GGUFDatabaseModel>>

    @Query("SELECT * FROM gguf_models WHERE modelType = 'VLM' ORDER BY lastUsedAt DESC")
    fun getVLMModelsFlow(): Flow<List<GGUFDatabaseModel>>

    @Query("SELECT * FROM gguf_models WHERE modelType = 'EMBEDDING' ORDER BY lastUsedAt DESC")
    fun getEmbeddingModelsFlow(): Flow<List<GGUFDatabaseModel>>

    // ========== QUERY - BY TAGS ==========

    @Query("SELECT * FROM gguf_models WHERE tags LIKE '%' || :tag || '%' ORDER BY lastUsedAt DESC")
    fun getByTagFlow(tag: String): Flow<List<GGUFDatabaseModel>>

    @Query("SELECT * FROM gguf_models WHERE tags LIKE '%' || :tag || '%' ORDER BY lastUsedAt DESC")
    suspend fun getByTag(tag: String): List<GGUFDatabaseModel>

    // ========== QUERY - SEARCH ==========

    @Query("""
        SELECT * FROM gguf_models 
        WHERE modelName LIKE '%' || :query || '%' 
        OR modelDescription LIKE '%' || :query || '%'
        OR tags LIKE '%' || :query || '%'
        ORDER BY lastUsedAt DESC
    """)
    fun searchFlow(query: String): Flow<List<GGUFDatabaseModel>>

    @Query("""
        SELECT * FROM gguf_models 
        WHERE modelName LIKE '%' || :query || '%' 
        OR modelDescription LIKE '%' || :query || '%'
        OR tags LIKE '%' || :query || '%'
        ORDER BY lastUsedAt DESC
    """)
    suspend fun search(query: String): List<GGUFDatabaseModel>

    // ========== QUERY - FILTERS ==========

    @Query("SELECT * FROM gguf_models WHERE isImported = 1 ORDER BY lastUsedAt DESC")
    fun getImportedFlow(): Flow<List<GGUFDatabaseModel>>

    @Query("SELECT * FROM gguf_models WHERE isImported = 0 ORDER BY lastUsedAt DESC")
    fun getDownloadedFlow(): Flow<List<GGUFDatabaseModel>>

    // ========== UTILITY ==========

    @Query("SELECT COUNT(*) FROM gguf_models")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM gguf_models")
    fun getCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM gguf_models WHERE modelType = :type")
    suspend fun getCountByType(type: ModelType): Int

    @Query("SELECT EXISTS(SELECT 1 FROM gguf_models WHERE id = :modelId)")
    suspend fun exists(modelId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM gguf_models WHERE modelName = :modelName)")
    suspend fun existsByName(modelName: String): Boolean
    
}