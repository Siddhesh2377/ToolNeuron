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
}
