package com.dark.tool_neuron.repo.ums

import com.dark.tool_neuron.data.Tags
import com.dark.tool_neuron.data.UmsCollections
import com.dark.tool_neuron.models.table_schema.EntityType
import com.dark.tool_neuron.models.table_schema.KnowledgeEntity
import com.dark.tool_neuron.models.table_schema.KnowledgeRelation
import com.dark.ums.UmsRecord
import com.dark.ums.UnifiedMemorySystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UmsKnowledgeRepository(private val ums: UnifiedMemorySystem) {

    private val entities = UmsCollections.KNOWLEDGE_ENTITIES
    private val relations = UmsCollections.KNOWLEDGE_RELATIONS

    fun init() {
        ums.ensureCollection(entities)
        ums.ensureCollection(relations)
        ums.addIndex(entities, Tags.KgEntity.ENTITY_ID, UnifiedMemorySystem.WIRE_BYTES)
        ums.addIndex(entities, Tags.KgEntity.NAME, UnifiedMemorySystem.WIRE_BYTES)
        ums.addIndex(entities, Tags.KgEntity.TYPE, UnifiedMemorySystem.WIRE_BYTES)
        ums.addIndex(relations, Tags.KgRelation.ENTITY_ID, UnifiedMemorySystem.WIRE_BYTES)
        ums.addIndex(relations, Tags.KgRelation.SUBJECT_ID, UnifiedMemorySystem.WIRE_BYTES)
        ums.addIndex(relations, Tags.KgRelation.OBJECT_ID, UnifiedMemorySystem.WIRE_BYTES)
        ums.addIndex(relations, Tags.KgRelation.PERSONA_ID, UnifiedMemorySystem.WIRE_BYTES)
    }

    // ── Entity CRUD ──

    suspend fun insertEntity(entity: KnowledgeEntity) = withContext(Dispatchers.IO) {
        ums.put(entities, entity.toRecord())
    }

    // ── Relation CRUD ──

    suspend fun insertRelation(relation: KnowledgeRelation) = withContext(Dispatchers.IO) {
        ums.put(relations, relation.toRecord())
    }

    suspend fun getAllRelations(): List<KnowledgeRelation> = withContext(Dispatchers.IO) {
        ums.getAll(relations).map { it.toKnowledgeRelation() }
            .sortedByDescending { it.createdAt }
    }

}

// ── KnowledgeEntity serialization ──

private fun KnowledgeEntity.toRecord(existingId: Int = 0): UmsRecord {
    val b = UmsRecord.create()
    if (existingId != 0) b.id(existingId)
    b.putString(Tags.KgEntity.ENTITY_ID, id)
    b.putString(Tags.KgEntity.NAME, name)
    b.putString(Tags.KgEntity.TYPE, type.name)
    if (embedding != null) b.putBytes(Tags.KgEntity.EMBEDDING, embedding)
    b.putTimestamp(Tags.KgEntity.FIRST_SEEN, firstSeen)
    b.putTimestamp(Tags.KgEntity.LAST_SEEN, lastSeen)
    b.putInt(Tags.KgEntity.MENTION_COUNT, mentionCount)
    return b.build()
}

private fun UmsRecord.toKnowledgeEntity(): KnowledgeEntity = KnowledgeEntity(
    id = getString(Tags.KgEntity.ENTITY_ID) ?: "",
    name = getString(Tags.KgEntity.NAME) ?: "",
    type = getString(Tags.KgEntity.TYPE)?.let {
        runCatching { EntityType.valueOf(it) }.getOrNull()
    } ?: EntityType.THING,
    embedding = getBytes(Tags.KgEntity.EMBEDDING),
    firstSeen = getTimestamp(Tags.KgEntity.FIRST_SEEN) ?: System.currentTimeMillis(),
    lastSeen = getTimestamp(Tags.KgEntity.LAST_SEEN) ?: System.currentTimeMillis(),
    mentionCount = getInt(Tags.KgEntity.MENTION_COUNT) ?: 1
)

// ── KnowledgeRelation serialization ──

private fun KnowledgeRelation.toRecord(existingId: Int = 0): UmsRecord {
    val b = UmsRecord.create()
    if (existingId != 0) b.id(existingId)
    b.putString(Tags.KgRelation.ENTITY_ID, id)
    b.putString(Tags.KgRelation.SUBJECT_ID, subjectId)
    b.putString(Tags.KgRelation.PREDICATE, predicate)
    b.putString(Tags.KgRelation.OBJECT_ID, objectId)
    b.putFloat(Tags.KgRelation.CONFIDENCE, confidence)
    if (sourceFactId != null) b.putString(Tags.KgRelation.SOURCE_FACT_ID, sourceFactId)
    b.putTimestamp(Tags.KgRelation.CREATED_AT, createdAt)
    if (personaId != null) b.putString(Tags.KgRelation.PERSONA_ID, personaId)
    return b.build()
}

private fun UmsRecord.toKnowledgeRelation(): KnowledgeRelation = KnowledgeRelation(
    id = getString(Tags.KgRelation.ENTITY_ID) ?: "",
    subjectId = getString(Tags.KgRelation.SUBJECT_ID) ?: "",
    predicate = getString(Tags.KgRelation.PREDICATE) ?: "",
    objectId = getString(Tags.KgRelation.OBJECT_ID) ?: "",
    confidence = getFloat(Tags.KgRelation.CONFIDENCE) ?: 1.0f,
    sourceFactId = getString(Tags.KgRelation.SOURCE_FACT_ID),
    createdAt = getTimestamp(Tags.KgRelation.CREATED_AT) ?: System.currentTimeMillis(),
    personaId = getString(Tags.KgRelation.PERSONA_ID)
)
