package com.dark.tool_neuron.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dark.tool_neuron.models.table_schema.AiMemory
import com.dark.tool_neuron.models.table_schema.MemoryCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface AiMemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: AiMemory)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(memories: List<AiMemory>)

    @Update
    suspend fun update(memory: AiMemory)

    @Delete
    suspend fun delete(memory: AiMemory)

    @Query("SELECT * FROM ai_memories ORDER BY updated_at DESC")
    fun getAll(): Flow<List<AiMemory>>

    @Query("SELECT * FROM ai_memories ORDER BY updated_at DESC")
    suspend fun getAllOnce(): List<AiMemory>

    @Query("SELECT * FROM ai_memories WHERE category = :category ORDER BY updated_at DESC")
    suspend fun getByCategory(category: MemoryCategory): List<AiMemory>

    @Query("SELECT * FROM ai_memories WHERE fact LIKE '%' || :query || '%'")
    suspend fun search(query: String): List<AiMemory>

    @Query("DELETE FROM ai_memories")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM ai_memories")
    suspend fun count(): Int

    @Query("UPDATE ai_memories SET embedding = :embedding WHERE id = :id")
    suspend fun updateEmbedding(id: String, embedding: ByteArray)

    @Query("SELECT * FROM ai_memories WHERE embedding IS NOT NULL")
    suspend fun getAllWithEmbeddings(): List<AiMemory>

    /** Get all unsummarized facts (for L2 summary worker). */
    @Query("SELECT * FROM ai_memories WHERE is_summarized = 0 AND summary_group_id IS NULL ORDER BY category, updated_at DESC")
    suspend fun getUnsummarized(): List<AiMemory>

    /** Mark a batch of facts as summarized with a shared group id. */
    @Query("UPDATE ai_memories SET is_summarized = 1, summary_group_id = :groupId WHERE id IN (:ids)")
    suspend fun markSummarized(ids: List<String>, groupId: String)

    /** Get all summaries (facts that have a summary_group_id but are NOT marked as summarized source facts). */
    @Query("SELECT * FROM ai_memories WHERE summary_group_id IS NOT NULL AND is_summarized = 0 ORDER BY updated_at DESC")
    suspend fun getSummaries(): List<AiMemory>

    /** Get only global memories (no persona). Used when no persona is active. */
    @Query("SELECT * FROM ai_memories WHERE persona_id IS NULL ORDER BY updated_at DESC")
    suspend fun getGlobalOnce(): List<AiMemory>

    // ==================== Per-persona queries ====================

    /** Get all memories for a persona (includes global memories where persona_id IS NULL). */
    @Query("SELECT * FROM ai_memories WHERE persona_id = :personaId OR persona_id IS NULL ORDER BY updated_at DESC")
    suspend fun getAllForPersonaOnce(personaId: String): List<AiMemory>

    /** Get unsummarized memories for a persona (includes global). */
    @Query("SELECT * FROM ai_memories WHERE (persona_id = :personaId OR persona_id IS NULL) AND is_summarized = 0 AND summary_group_id IS NULL ORDER BY category, updated_at DESC")
    suspend fun getUnsummarizedForPersona(personaId: String): List<AiMemory>

    /** Delete all memories for a specific persona (does NOT delete global memories). */
    @Query("DELETE FROM ai_memories WHERE persona_id = :personaId")
    suspend fun deleteAllForPersona(personaId: String)

    /** Count memories for a specific persona (excludes global). */
    @Query("SELECT COUNT(*) FROM ai_memories WHERE persona_id = :personaId")
    suspend fun countForPersona(personaId: String): Int
}
