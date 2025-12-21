package com.mp.ai_engine.databases.open_router

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mp.ai_engine.models.llm_models.ModelType
import com.mp.ai_engine.models.llm_models.OpenRouterDatabaseModel
import kotlinx.coroutines.flow.Flow

@Dao
interface OpenRouterDatabaseAccessObject {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(model: OpenRouterDatabaseModel)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(models: List<OpenRouterDatabaseModel>)

    // ========== UPDATE ==========

    @Update
    suspend fun update(model: OpenRouterDatabaseModel)

    @Query("UPDATE openrouter_models SET lastUsedAt = :timestamp WHERE id = :modelId")
    suspend fun updateLastUsed(modelId: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE openrouter_models SET isFavorite = :isFavorite WHERE id = :modelId")
    suspend fun updateFavorite(modelId: String, isFavorite: Boolean)

    @Query("UPDATE openrouter_models SET temperature = :temp, topP = :topP WHERE id = :modelId")
    suspend fun updateSamplingParams(modelId: String, temp: Float, topP: Float)

    @Query("UPDATE openrouter_models SET maxTokens = :maxTokens WHERE id = :modelId")
    suspend fun updateMaxTokens(modelId: String, maxTokens: Int)

    // ========== DELETE ==========

    @Delete
    suspend fun delete(model: OpenRouterDatabaseModel)

    @Query("DELETE FROM openrouter_models WHERE id = :modelId")
    suspend fun deleteById(modelId: String)

    @Query("DELETE FROM openrouter_models")
    suspend fun deleteAll()

    @Query("DELETE FROM openrouter_models WHERE isFavorite = 0")
    suspend fun deleteNonFavorites()

    // ========== QUERY - SINGLE ==========

    @Query("SELECT * FROM openrouter_models WHERE id = :modelId LIMIT 1")
    suspend fun getById(modelId: String): OpenRouterDatabaseModel?

    @Query("SELECT * FROM openrouter_models WHERE id = :modelId LIMIT 1")
    fun getByIdFlow(modelId: String): Flow<OpenRouterDatabaseModel?>

    @Query("SELECT * FROM openrouter_models WHERE modelName = :modelName LIMIT 1")
    suspend fun getByName(modelName: String): OpenRouterDatabaseModel?

    // ========== QUERY - ALL ==========

    @Query("SELECT * FROM openrouter_models ORDER BY lastUsedAt DESC")
    suspend fun getAll(): List<OpenRouterDatabaseModel>

    @Query("SELECT * FROM openrouter_models ORDER BY lastUsedAt DESC")
    fun getAllFlow(): Flow<List<OpenRouterDatabaseModel>>

    @Query("SELECT * FROM openrouter_models ORDER BY modelName ASC")
    fun getAllByNameFlow(): Flow<List<OpenRouterDatabaseModel>>

    @Query("SELECT * FROM openrouter_models ORDER BY createdAt DESC")
    fun getAllByCreatedFlow(): Flow<List<OpenRouterDatabaseModel>>

    // ========== QUERY - BY TYPE ==========

    @Query("SELECT * FROM openrouter_models WHERE modelType = :type ORDER BY lastUsedAt DESC")
    suspend fun getByType(type: ModelType): List<OpenRouterDatabaseModel>

    @Query("SELECT * FROM openrouter_models WHERE modelType = :type ORDER BY lastUsedAt DESC")
    fun getByTypeFlow(type: ModelType): Flow<List<OpenRouterDatabaseModel>>

    // ========== QUERY - BY CAPABILITIES ==========

    @Query("SELECT * FROM openrouter_models WHERE supportsTools = 1 ORDER BY lastUsedAt DESC")
    fun getToolsCapableFlow(): Flow<List<OpenRouterDatabaseModel>>

    @Query("SELECT * FROM openrouter_models WHERE supportsVision = 1 ORDER BY lastUsedAt DESC")
    fun getVisionCapableFlow(): Flow<List<OpenRouterDatabaseModel>>

    @Query("SELECT * FROM openrouter_models WHERE supportsStreaming = 1 ORDER BY lastUsedAt DESC")
    fun getStreamingCapableFlow(): Flow<List<OpenRouterDatabaseModel>>

    // ========== QUERY - BY FAVORITES ==========

    @Query("SELECT * FROM openrouter_models WHERE isFavorite = 1 ORDER BY lastUsedAt DESC")
    fun getFavoritesFlow(): Flow<List<OpenRouterDatabaseModel>>

    @Query("SELECT * FROM openrouter_models WHERE isFavorite = 1 ORDER BY lastUsedAt DESC")
    suspend fun getFavorites(): List<OpenRouterDatabaseModel>

    // ========== QUERY - BY TAGS ==========

    @Query("SELECT * FROM openrouter_models WHERE tags LIKE '%' || :tag || '%' ORDER BY lastUsedAt DESC")
    fun getByTagFlow(tag: String): Flow<List<OpenRouterDatabaseModel>>

    @Query("SELECT * FROM openrouter_models WHERE tags LIKE '%' || :tag || '%' ORDER BY lastUsedAt DESC")
    suspend fun getByTag(tag: String): List<OpenRouterDatabaseModel>

    // ========== QUERY - BY PRICING ==========

    @Query("""
        SELECT * FROM openrouter_models 
        WHERE promptCostPer1M = 0.0 AND completionCostPer1M = 0.0 
        ORDER BY lastUsedAt DESC
    """)
    fun getFreeModelsFlow(): Flow<List<OpenRouterDatabaseModel>>

    @Query("""
        SELECT * FROM openrouter_models 
        WHERE promptCostPer1M > 0.0 OR completionCostPer1M > 0.0 
        ORDER BY promptCostPer1M ASC
    """)
    fun getPaidModelsByPriceFlow(): Flow<List<OpenRouterDatabaseModel>>

    // ========== QUERY - SEARCH ==========

    @Query("""
        SELECT * FROM openrouter_models 
        WHERE modelName LIKE '%' || :query || '%' 
        OR modelDescription LIKE '%' || :query || '%'
        OR modelId LIKE '%' || :query || '%'
        OR tags LIKE '%' || :query || '%'
        ORDER BY lastUsedAt DESC
    """)
    fun searchFlow(query: String): Flow<List<OpenRouterDatabaseModel>>

    @Query("""
        SELECT * FROM openrouter_models 
        WHERE modelName LIKE '%' || :query || '%' 
        OR modelDescription LIKE '%' || :query || '%'
        OR modelId LIKE '%' || :query || '%'
        OR tags LIKE '%' || :query || '%'
        ORDER BY lastUsedAt DESC
    """)
    suspend fun search(query: String): List<OpenRouterDatabaseModel>

    // ========== UTILITY ==========

    @Query("SELECT COUNT(*) FROM openrouter_models")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM openrouter_models")
    fun getCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM openrouter_models WHERE modelType = :type")
    suspend fun getCountByType(type: ModelType): Int

    @Query("SELECT EXISTS(SELECT 1 FROM openrouter_models WHERE id = :modelId)")
    suspend fun exists(modelId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM openrouter_models WHERE modelId = :modelId)")
    suspend fun existsByModelId(modelId: String): Boolean

    @Query("SELECT SUM(promptCostPer1M) FROM openrouter_models WHERE isFavorite = 1")
    suspend fun getTotalFavoriteCost(): Float?
    
}