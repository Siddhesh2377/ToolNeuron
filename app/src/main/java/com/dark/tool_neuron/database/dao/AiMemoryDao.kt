package com.dark.tool_neuron.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.dark.tool_neuron.models.table_schema.AiMemory

@Dao
interface AiMemoryDao {
    @Query("SELECT * FROM ai_memories ORDER BY updated_at DESC")
    suspend fun getAllOnce(): List<AiMemory>
}
