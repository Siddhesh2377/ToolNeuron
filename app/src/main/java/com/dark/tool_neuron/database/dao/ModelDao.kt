package com.dark.tool_neuron.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dark.tool_neuron.models.enums.ProviderType
import com.dark.tool_neuron.models.table_schema.Model
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(model: Model)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(models: List<Model>)
    
    @Update
    suspend fun update(model: Model)
    
    @Delete
    suspend fun delete(model: Model)
    
    @Query("SELECT * FROM models WHERE id = :id")
    suspend fun getById(id: String): Model?

    @Query("SELECT * FROM models WHERE model_name = :name")
    suspend fun getByName(name: String): Model?
    
    @Query("SELECT * FROM models WHERE is_active = 1")
    fun getAllActive(): Flow<List<Model>>
    
    @Query("SELECT * FROM models")
    fun getAll(): Flow<List<Model>>
    
    @Query("SELECT * FROM models WHERE provider_type = :providerType")
    fun getByProvider(providerType: ProviderType): Flow<List<Model>>
    
    @Query("UPDATE models SET is_active = :isActive WHERE id = :id")
    suspend fun updateActiveStatus(id: String, isActive: Boolean)
}