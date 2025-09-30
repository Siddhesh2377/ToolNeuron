package com.dark.ai_module.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.dark.ai_module.model.ModelProps
import com.dark.ai_module.model.ModelWithProps
import com.dark.ai_module.model.ModelsData
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDAO {
    // Existing methods
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: ModelsData)

    @Delete
    suspend fun deleteModel(model: ModelsData)

    @Query("SELECT * FROM local_models WHERE modeName = :modelName LIMIT 1")
    suspend fun getModelByName(modelName: String): ModelsData?

    @Query("SELECT * FROM local_models")
    fun getAllModels(): Flow<List<ModelsData>>

    @Transaction
    @Query("SELECT * FROM local_models")
    fun getModelsWithProps(): Flow<List<ModelWithProps>>

    // Props methods
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProps(props: ModelProps)

    @Query("SELECT * FROM model_props WHERE modelId = :modelId LIMIT 1")
    suspend fun getPropsForModel(modelId: Int): ModelProps?

    @Query(
        """
        UPDATE model_props 
        SET chatTemplate = :chatTemplate, 
            systemPrompt = :systemPrompt, 
            temperature = :temperature, 
            topK = :topK, 
            topP = :topP, 
            minP = :minP 
        WHERE modelId = :modelId
    """
    )
    suspend fun updateProps(
        modelId: Int,
        chatTemplate: String,
        systemPrompt: String,
        temperature: Float,
        topK: Int,
        topP: Float,
        minP: Int
    )
}


@Database(entities = [ModelsData::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ModelDAO(): ModelDAO
}