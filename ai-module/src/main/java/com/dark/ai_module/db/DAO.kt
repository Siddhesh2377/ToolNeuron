package com.dark.ai_module.db

import androidx.room.Insert
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import com.dark.ai_module.model.ModelsData
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDAO {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: ModelsData)

    @Delete
    suspend fun deleteModel(model: ModelsData)

    @Query("SELECT * FROM local_models WHERE modeName = :modelName LIMIT 1")
    suspend fun getModelByName(modelName: String): ModelsData?

    @Query("SELECT * FROM local_models")
    fun getAllModels(): Flow<List<ModelsData>>
}



@Database(entities = [ModelsData::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ModelDAO(): ModelDAO
}