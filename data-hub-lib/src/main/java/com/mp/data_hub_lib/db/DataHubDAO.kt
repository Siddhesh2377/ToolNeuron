package com.mp.data_hub_lib.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mp.data_hub_lib.model.DataSetModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

@Dao
interface DataHubDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: DataSetModel)

    @Update
    suspend fun updateModel(model: DataSetModel)

    @Delete
    suspend fun deleteModel(model: DataSetModel)

    @Query("SELECT * FROM data_hub_models WHERE modelName = :modelName LIMIT 1")
    suspend fun getModelByName(modelName: String): DataSetModel?

    @Query("SELECT * FROM data_hub_models")
    fun getAllModels(): Flow<List<DataSetModel>>

    @Query("SELECT * FROM data_hub_models WHERE modelName LIKE '%' || :query || '%' OR modelDescription LIKE '%' || :query || '%'")
    fun searchModels(query: String): Flow<List<DataSetModel>>
}
