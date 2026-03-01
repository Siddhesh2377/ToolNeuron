package com.dark.tool_neuron.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dark.tool_neuron.models.table_schema.Persona
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(persona: Persona)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(personas: List<Persona>)

    @Update
    suspend fun update(persona: Persona)

    @Delete
    suspend fun delete(persona: Persona)

    @Query("SELECT * FROM personas ORDER BY created_at ASC")
    fun getAll(): Flow<List<Persona>>

    @Query("SELECT * FROM personas ORDER BY created_at ASC")
    suspend fun getAllOnce(): List<Persona>

    @Query("SELECT * FROM personas WHERE id = :id")
    suspend fun getById(id: String): Persona?

    @Query("SELECT * FROM personas WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): Persona?

    @Query("SELECT * FROM personas WHERE is_default = 1")
    suspend fun getDefaults(): List<Persona>

    @Query("SELECT COUNT(*) FROM personas")
    suspend fun count(): Int
}
