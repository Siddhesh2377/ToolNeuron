package com.dark.tool_neuron.repo.ums

import com.dark.tool_neuron.data.Tags
import com.dark.tool_neuron.data.UmsCollections
import com.dark.tool_neuron.models.table_schema.ModelConfig
import com.dark.ums.UmsRecord
import com.dark.ums.UnifiedMemorySystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UmsConfigRepository(private val ums: UnifiedMemorySystem) {

    private val collection = UmsCollections.MODEL_CONFIG

    fun init() {
        ums.ensureCollection(collection)
        ums.addIndex(collection, Tags.Config.ENTITY_ID, UnifiedMemorySystem.WIRE_BYTES)
        ums.addIndex(collection, Tags.Config.MODEL_ID, UnifiedMemorySystem.WIRE_BYTES)
    }

    suspend fun insert(config: ModelConfig) = withContext(Dispatchers.IO) {
        ums.put(collection, config.toRecord())
    }

    suspend fun update(config: ModelConfig) = withContext(Dispatchers.IO) {
        val existing = findRecordId(config.id) ?: return@withContext
        ums.put(collection, config.toRecord(existing))
    }

    suspend fun delete(config: ModelConfig) = withContext(Dispatchers.IO) {
        val recordId = findRecordId(config.id) ?: return@withContext
        ums.delete(collection, recordId)
    }

    suspend fun getByModelId(modelId: String): ModelConfig? = withContext(Dispatchers.IO) {
        ums.queryString(collection, Tags.Config.MODEL_ID, modelId)
            .firstOrNull()?.toModelConfig()
    }

    suspend fun getById(id: String): ModelConfig? = withContext(Dispatchers.IO) {
        ums.queryString(collection, Tags.Config.ENTITY_ID, id)
            .firstOrNull()?.toModelConfig()
    }

    private fun findRecordId(entityId: String): Int? {
        return ums.queryString(collection, Tags.Config.ENTITY_ID, entityId)
            .firstOrNull()?.id?.takeIf { it != 0 }
    }
}

private fun ModelConfig.toRecord(existingId: Int = 0): UmsRecord {
    val b = UmsRecord.create()
    if (existingId != 0) b.id(existingId)
    b.putString(Tags.Config.ENTITY_ID, id)
    b.putString(Tags.Config.MODEL_ID, modelId)
    if (modelLoadingParams != null) b.putString(Tags.Config.LOADING_PARAMS, modelLoadingParams)
    if (modelInferenceParams != null) b.putString(Tags.Config.INFERENCE_PARAMS, modelInferenceParams)
    return b.build()
}

private fun UmsRecord.toModelConfig(): ModelConfig = ModelConfig(
    id = getString(Tags.Config.ENTITY_ID) ?: "",
    modelId = getString(Tags.Config.MODEL_ID) ?: "",
    modelLoadingParams = getString(Tags.Config.LOADING_PARAMS),
    modelInferenceParams = getString(Tags.Config.INFERENCE_PARAMS)
)
