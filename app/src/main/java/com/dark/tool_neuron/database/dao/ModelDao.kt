package com.dark.tool_neuron.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.dark.tool_neuron.models.table_schema.Model

@Dao
interface ModelDao {
    @Query("SELECT * FROM models")
    suspend fun getAllOnce(): List<Model>
}
