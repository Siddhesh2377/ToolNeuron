package com.dark.tool_neuron.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.dark.tool_neuron.models.table_schema.ModelConfig

@Dao
interface ModelConfigDao {
    @Query("SELECT * FROM model_config WHERE model_id = :modelId")
    suspend fun getByModelId(modelId: String): ModelConfig?
}
