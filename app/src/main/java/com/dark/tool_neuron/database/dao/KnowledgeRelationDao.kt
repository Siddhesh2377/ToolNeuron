package com.dark.tool_neuron.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.dark.tool_neuron.models.table_schema.KnowledgeRelation

@Dao
interface KnowledgeRelationDao {
    @Query("SELECT * FROM knowledge_relations ORDER BY created_at DESC")
    suspend fun getAll(): List<KnowledgeRelation>
}
