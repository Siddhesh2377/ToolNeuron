package com.dark.tool_neuron.repo

import com.dark.tool_neuron.models.table_schema.Model
import com.dark.tool_neuron.models.table_schema.ModelConfig
import com.dark.tool_neuron.repo.ums.UmsConfigRepository
import com.dark.tool_neuron.repo.ums.UmsModelRepository
import kotlinx.coroutines.flow.Flow

class ModelRepository(
    private val modelRepo: UmsModelRepository,
    private val configRepo: UmsConfigRepository
) {

    fun getAllModels(): Flow<List<Model>> = modelRepo.getAll()

    suspend fun getModelById(id: String): Model? = modelRepo.getById(id)

    suspend fun insertModel(model: Model) = modelRepo.insert(model)

    suspend fun updateModel(model: Model) = modelRepo.update(model)

    suspend fun deleteModel(model: Model) = modelRepo.delete(model)

    suspend fun getConfigByModelId(modelId: String): ModelConfig? = configRepo.getByModelId(modelId)

    suspend fun insertConfig(config: ModelConfig) = configRepo.insert(config)

    suspend fun updateConfig(config: ModelConfig) = configRepo.update(config)

    suspend fun deleteConfig(config: ModelConfig) = configRepo.delete(config)
}
