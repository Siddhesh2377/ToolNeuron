package com.dark.neuroverse.data.db

import androidx.room.Insert
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Query
import androidx.room.RoomDatabase
import com.dark.neuroverse.data.model.ModelsData
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDAO {

    @Insert
    suspend fun insertModel(model: ModelsData)

    @Delete
    suspend fun deleteModel(model: ModelsData)

    @Query("SELECT * FROM local_models WHERE modeName = :modelName LIMIT 1")
    suspend fun getModelByName(modelName: String): ModelsData?

    @Query("SELECT * FROM local_models")
    fun getAllModels(): kotlinx.coroutines.flow.Flow<List<ModelsData>>
}



@Database(entities = [ModelsData::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ModelDAO(): ModelDAO
}