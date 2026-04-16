package com.dark.tool_neuron.repo

import com.dark.tool_neuron.models.table_schema.Model
import com.dark.tool_neuron.models.table_schema.ModelConfig
import com.dark.tool_neuron.repo.ums.UmsConfigRepository
import com.dark.tool_neuron.repo.ums.UmsModelRepository
import kotlinx.coroutines.flow.Flow

class ModelRepository(
    private val modelRepoProvider: () -> UmsModelRepository?,
    private val configRepoProvider: () -> UmsConfigRepository?
) {
    private val modelRepo get() = modelRepoProvider()
    private val configRepo get() = configRepoProvider()

    fun isReady(): Boolean = modelRepo != null && configRepo != null

    private fun ensureReady() {
        if (!isReady()) throw IllegalStateException("VaultManager not initialized")
    }

    fun getAllModels(): Flow<List<Model>> {
        val repo = modelRepo ?: return kotlinx.coroutines.flow.flowOf(emptyList())
        return repo.getAll()
    }

    suspend fun getModelById(id: String): Model? {
        val repo = modelRepo ?: return null
        return repo.getById(id)
    }

    suspend fun getModelByName(name: String): Model? {
        val repo = modelRepo ?: return null
        return repo.getByName(name)
    }

    fun getModelsByProvider(providerType: com.dark.tool_neuron.models.enums.ProviderType): Flow<List<Model>> {
        val repo = modelRepo ?: return kotlinx.coroutines.flow.flowOf(emptyList())
        return repo.getByProvider(providerType)
    }

    suspend fun insertModel(model: Model) {
        ensureReady()
        modelRepo?.insert(model)
    }

    suspend fun updateModel(model: Model) {
        ensureReady()
        modelRepo?.update(model)
    }

    suspend fun deleteModel(model: Model) {
        ensureReady()
        modelRepo?.delete(model)
    }

    suspend fun getConfigByModelId(modelId: String): ModelConfig? {
        val repo = configRepo ?: return null
        return repo.getByModelId(modelId)
    }

    suspend fun insertConfig(config: ModelConfig) {
        ensureReady()
        configRepo?.insert(config)
    }

    suspend fun updateConfig(config: ModelConfig) {
        ensureReady()
        configRepo?.update(config)
    }

    suspend fun deleteConfig(config: ModelConfig) {
        ensureReady()
        configRepo?.delete(config)
    }
}
