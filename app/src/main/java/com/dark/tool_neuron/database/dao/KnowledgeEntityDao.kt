package com.dark.tool_neuron.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dark.tool_neuron.models.table_schema.EntityType
import com.dark.tool_neuron.models.table_schema.KnowledgeEntity

@Dao
interface KnowledgeEntityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: KnowledgeEntity)

    @Update
    suspend fun update(entity: KnowledgeEntity)

    @Delete
    suspend fun delete(entity: KnowledgeEntity)

    @Query("SELECT * FROM knowledge_entities ORDER BY last_seen DESC")
    suspend fun getAll(): List<KnowledgeEntity>

    @Query("SELECT * FROM knowledge_entities WHERE id = :id")
    suspend fun getById(id: String): KnowledgeEntity?

    @Query("SELECT * FROM knowledge_entities WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getByName(name: String): KnowledgeEntity?

    @Query("SELECT * FROM knowledge_entities WHERE type = :type ORDER BY mention_count DESC")
    suspend fun getByType(type: EntityType): List<KnowledgeEntity>

    @Query("SELECT * FROM knowledge_entities WHERE name LIKE '%' || :query || '%'")
    suspend fun searchByName(query: String): List<KnowledgeEntity>

    @Query("SELECT COUNT(*) FROM knowledge_entities")
    suspend fun count(): Int

    @Query("DELETE FROM knowledge_entities")
    suspend fun deleteAll()
}
