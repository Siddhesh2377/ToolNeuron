package com.dark.ai_module.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import com.dark.ai_module.model.ModelData
import com.dark.ai_module.model.ModelType
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDAO {
    // Existing methods
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: ModelData)

    @Delete
    suspend fun deleteModel(model: ModelData)

    @Query("SELECT * FROM local_models WHERE modelName = :modelName LIMIT 1")
    suspend fun getModelByName(modelName: String): ModelData?

    @Query("SELECT * FROM local_models WHERE id = :id LIMIT 1")
    suspend fun getModelById(id: String): ModelData?

    @Query("SELECT * FROM local_models")
    fun getAllModels(): Flow<List<ModelData>>

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateModel(model: ModelData)
    @Query("SELECT * FROM local_models WHERE modelType = :type LIMIT 1")
    suspend fun getModelByType(type: ModelType): ModelData?

    // Helper functions for clarity
    suspend fun getTTSModel(): ModelData? = getModelByType(ModelType.TTS)

    suspend fun getSTTModel(): ModelData? = getModelByType(ModelType.STT)
}

@Database(
    entities = [ModelData::class], version = 2, exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ModelDAO(): ModelDAO
}