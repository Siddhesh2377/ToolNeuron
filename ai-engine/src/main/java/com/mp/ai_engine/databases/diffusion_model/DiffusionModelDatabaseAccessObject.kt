package com.mp.ai_engine.databases.diffusion_model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mp.ai_engine.models.image_models.DiffusionDatabaseModel
import kotlinx.coroutines.flow.Flow

@Dao
interface DiffusionModelDatabaseAccessObject {

    // Insert operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: DiffusionDatabaseModel)

    @Query("DELETE FROM diffusion_db WHERE id = :modelId")
    suspend fun deleteModelById(modelId: String)

    // Query operations - Single model
    @Query("SELECT * FROM diffusion_db WHERE id = :modelId")
    suspend fun getModelById(modelId: String): DiffusionDatabaseModel?

    // Query operations - Multiple models
    @Query("SELECT * FROM diffusion_db ORDER BY name ASC")
    suspend fun getAllModels(): List<DiffusionDatabaseModel>

    // CPU models
    @Query("SELECT * FROM diffusion_db WHERE runOnCpu = 1 ORDER BY name ASC")
    suspend fun getCpuModels(): List<DiffusionDatabaseModel>

    @Query("SELECT * FROM diffusion_db WHERE runOnCpu = 1 ORDER BY name ASC")
    fun getCpuModelsFlow(): Flow<List<DiffusionDatabaseModel>>

    // NPU models
    @Query("SELECT * FROM diffusion_db WHERE runOnCpu = 0 ORDER BY name ASC")
    suspend fun getNpuModels(): List<DiffusionDatabaseModel>

    @Query("SELECT * FROM diffusion_db WHERE runOnCpu = 0 ORDER BY name ASC")
    fun getNpuModelsFlow(): Flow<List<DiffusionDatabaseModel>>

    // Search operations
    @Query("SELECT * FROM diffusion_db WHERE name LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' ORDER BY name ASC")
    suspend fun searchModels(query: String): List<DiffusionDatabaseModel>

    @Query("SELECT * FROM diffusion_db WHERE name LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchModelsFlow(query: String): Flow<List<DiffusionDatabaseModel>>

    // Count operations
    @Query("SELECT COUNT(*) FROM diffusion_db")
    suspend fun getModelCount(): Int

    @Query("UPDATE diffusion_db SET defaultPrompt = :prompt, defaultNegativePrompt = :negativePrompt WHERE id = :modelId")
    suspend fun updateModelPrompts(modelId: String, prompt: String, negativePrompt: String)


}