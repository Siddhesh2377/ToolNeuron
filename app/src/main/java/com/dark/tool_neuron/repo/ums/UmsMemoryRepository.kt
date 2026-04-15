package com.dark.tool_neuron.repo.ums

import com.dark.tool_neuron.data.Tags
import com.dark.tool_neuron.data.UmsCollections
import com.dark.tool_neuron.models.table_schema.AiMemory
import com.dark.tool_neuron.models.table_schema.MemoryCategory
import com.dark.ums.UmsRecord
import com.dark.ums.UnifiedMemorySystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

class UmsMemoryRepository(private val ums: UnifiedMemorySystem) {

    private val collection = UmsCollections.MEMORIES
    private val _allMemories = MutableStateFlow<List<AiMemory>>(emptyList())

    fun init() {
        ums.ensureCollection(collection)
        ums.addIndex(collection, Tags.Memory.ENTITY_ID, UnifiedMemorySystem.WIRE_BYTES)
        ums.addIndex(collection, Tags.Memory.PERSONA_ID, UnifiedMemorySystem.WIRE_BYTES)
        ums.addIndex(collection, Tags.Memory.CATEGORY, UnifiedMemorySystem.WIRE_BYTES)
        refreshCache()
    }

    private fun refreshCache() {
        _allMemories.value = ums.getAll(collection).map { it.toAiMemory() }
            .sortedByDescending { it.updatedAt }
    }

    suspend fun insert(memory: AiMemory) = withContext(Dispatchers.IO) {
        ums.put(collection, memory.toRecord())
        refreshCache()
    }

    suspend fun update(memory: AiMemory) = withContext(Dispatchers.IO) {
        val existing = findRecordId(memory.id) ?: return@withContext
        ums.put(collection, memory.toRecord(existing))
        refreshCache()
    }

    suspend fun delete(memory: AiMemory) = withContext(Dispatchers.IO) {
        val recordId = findRecordId(memory.id) ?: return@withContext
        ums.delete(collection, recordId)
        refreshCache()
    }

    fun getAll(): Flow<List<AiMemory>> = _allMemories

    suspend fun getAllOnce(): List<AiMemory> = withContext(Dispatchers.IO) {
        ums.getAll(collection).map { it.toAiMemory() }
            .sortedByDescending { it.updatedAt }
    }

    suspend fun search(query: String): List<AiMemory> = withContext(Dispatchers.IO) {
        val lower = query.lowercase()
        ums.getAll(collection).map { it.toAiMemory() }
            .filter { it.fact.lowercase().contains(lower) }
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        val all = ums.getAll(collection)
        all.forEach { ums.delete(collection, it.id) }
        refreshCache()
    }

    suspend fun count(): Int = withContext(Dispatchers.IO) {
        ums.count(collection)
    }

    private fun findRecordId(entityId: String): Int? {
        return ums.queryString(collection, Tags.Memory.ENTITY_ID, entityId)
            .firstOrNull()?.id?.takeIf { it != 0 }
    }
}

private fun AiMemory.toRecord(existingId: Int = 0): UmsRecord {
    val b = UmsRecord.create()
    if (existingId != 0) b.id(existingId)
    b.putString(Tags.Memory.ENTITY_ID, id)
    b.putString(Tags.Memory.FACT, fact)
    b.putString(Tags.Memory.CATEGORY, category.name)
    if (sourceChatId != null) b.putString(Tags.Memory.SOURCE_CHAT_ID, sourceChatId)
    b.putTimestamp(Tags.Memory.CREATED_AT, createdAt)
    b.putTimestamp(Tags.Memory.UPDATED_AT, updatedAt)
    b.putTimestamp(Tags.Memory.LAST_ACCESSED_AT, lastAccessedAt)
    b.putInt(Tags.Memory.ACCESS_COUNT, accessCount)
    if (embedding != null) b.putBytes(Tags.Memory.EMBEDDING, embedding)
    b.putBool(Tags.Memory.IS_SUMMARIZED, isSummarized)
    if (summaryGroupId != null) b.putString(Tags.Memory.SUMMARY_GROUP_ID, summaryGroupId)
    if (personaId != null) b.putString(Tags.Memory.PERSONA_ID, personaId)
    return b.build()
}

private fun UmsRecord.toAiMemory(): AiMemory = AiMemory(
    id = getString(Tags.Memory.ENTITY_ID) ?: "",
    fact = getString(Tags.Memory.FACT) ?: "",
    category = getString(Tags.Memory.CATEGORY)?.let {
        runCatching { MemoryCategory.valueOf(it) }.getOrNull()
    } ?: MemoryCategory.GENERAL,
    sourceChatId = getString(Tags.Memory.SOURCE_CHAT_ID),
    createdAt = getTimestamp(Tags.Memory.CREATED_AT) ?: System.currentTimeMillis(),
    updatedAt = getTimestamp(Tags.Memory.UPDATED_AT) ?: System.currentTimeMillis(),
    lastAccessedAt = getTimestamp(Tags.Memory.LAST_ACCESSED_AT) ?: System.currentTimeMillis(),
    accessCount = getInt(Tags.Memory.ACCESS_COUNT) ?: 0,
    embedding = getBytes(Tags.Memory.EMBEDDING),
    isSummarized = getBool(Tags.Memory.IS_SUMMARIZED) ?: false,
    summaryGroupId = getString(Tags.Memory.SUMMARY_GROUP_ID),
    personaId = getString(Tags.Memory.PERSONA_ID)
)
