package com.dark.tool_neuron.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dark.tool_neuron.models.table_schema.KnowledgeRelation

@Dao
interface KnowledgeRelationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(relation: KnowledgeRelation)

    @Update
    suspend fun update(relation: KnowledgeRelation)

    @Delete
    suspend fun delete(relation: KnowledgeRelation)

    @Query("SELECT * FROM knowledge_relations ORDER BY created_at DESC")
    suspend fun getAll(): List<KnowledgeRelation>

    /** Get all relations where the entity is the subject. */
    @Query("SELECT * FROM knowledge_relations WHERE subject_id = :entityId")
    suspend fun getSubjectRelations(entityId: String): List<KnowledgeRelation>

    /** Get all relations where the entity is the object. */
    @Query("SELECT * FROM knowledge_relations WHERE object_id = :entityId")
    suspend fun getObjectRelations(entityId: String): List<KnowledgeRelation>

    /** Get all relations involving an entity (as subject or object). */
    @Query("SELECT * FROM knowledge_relations WHERE subject_id = :entityId OR object_id = :entityId")
    suspend fun getRelationsForEntity(entityId: String): List<KnowledgeRelation>

    /** Check if a specific relation already exists. */
    @Query("SELECT * FROM knowledge_relations WHERE subject_id = :subjectId AND predicate = :predicate AND object_id = :objectId LIMIT 1")
    suspend fun findRelation(subjectId: String, predicate: String, objectId: String): KnowledgeRelation?

    @Query("SELECT COUNT(*) FROM knowledge_relations")
    suspend fun count(): Int

    @Query("DELETE FROM knowledge_relations")
    suspend fun deleteAll()

    // ==================== Per-persona queries ====================

    /** Get relations for an entity scoped to a persona (includes global where persona_id IS NULL). */
    @Query("SELECT * FROM knowledge_relations WHERE (subject_id = :entityId OR object_id = :entityId) AND (persona_id = :personaId OR persona_id IS NULL)")
    suspend fun getRelationsForEntityAndPersona(entityId: String, personaId: String): List<KnowledgeRelation>

    /** Check for duplicate relation scoped to a persona. */
    @Query("SELECT * FROM knowledge_relations WHERE subject_id = :subjectId AND predicate = :predicate AND object_id = :objectId AND (persona_id = :personaId OR (:personaId IS NULL AND persona_id IS NULL)) LIMIT 1")
    suspend fun findRelationForPersona(subjectId: String, predicate: String, objectId: String, personaId: String?): KnowledgeRelation?

    /** Delete all relations for a specific persona (does NOT delete global relations). */
    @Query("DELETE FROM knowledge_relations WHERE persona_id = :personaId")
    suspend fun deleteAllForPersona(personaId: String)
}
