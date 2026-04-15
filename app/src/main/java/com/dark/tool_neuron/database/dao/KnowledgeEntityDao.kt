package com.dark.tool_neuron.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.dark.tool_neuron.models.table_schema.KnowledgeEntity

@Dao
interface KnowledgeEntityDao {
    @Query("SELECT * FROM knowledge_entities ORDER BY last_seen DESC")
    suspend fun getAll(): List<KnowledgeEntity>
}
