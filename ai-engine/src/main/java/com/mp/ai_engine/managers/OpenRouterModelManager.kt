package com.mp.ai_engine.managers

import android.content.Context
import com.mp.ai_engine.databases.open_router.OpenRouterDataBaseProvider
import com.mp.ai_engine.databases.open_router.OpenRouterDatabaseAccessObject
import com.mp.ai_engine.models.llm_models.ModelType
import com.mp.ai_engine.models.llm_models.OpenRouterDatabaseModel
import com.mp.ai_engine.models.llm_models.isFree
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicBoolean

object OpenRouterModelManager{
    private lateinit var dao: OpenRouterDatabaseAccessObject
    private val isInitialized = AtomicBoolean(false)

    fun init(context: Context) {
        if (isInitialized.getAndSet(true)) return

        dao = OpenRouterDataBaseProvider.getDatabase(context).OpenRouterDatabaseAccessObject()
    }

    suspend fun addModel(model: OpenRouterDatabaseModel): Result<Unit> = runCatching {
        if (dao.existsByModelId(model.modelId)) {
            throw IllegalStateException("Model with ID '${model.modelId}' already exists")
        }
        dao.insert(model)
    }

    suspend fun addModels(models: List<OpenRouterDatabaseModel>): Result<Unit> = runCatching {
        dao.insertAll(models)
    }

    suspend fun updateModel(model: OpenRouterDatabaseModel): Result<Unit> = runCatching {
        if (!dao.exists(model.id)) {
            throw IllegalStateException("Model with id '${model.id}' does not exist")
        }
        dao.update(model)
    }

    suspend fun removeModel(modelId: String): Result<Unit> = runCatching {
        dao.deleteById(modelId)
    }

    suspend fun clearAll(): Result<Unit> = runCatching {
        dao.deleteAll()
    }

    suspend fun clearNonFavorites(): Result<Unit> = runCatching {
        dao.deleteNonFavorites()
    }

    suspend fun getModel(modelId: String): OpenRouterDatabaseModel? {
        return dao.getById(modelId)
    }

    fun getModelFlow(modelId: String): Flow<OpenRouterDatabaseModel?> {
        return dao.getByIdFlow(modelId)
    }

    suspend fun getModelByName(name: String): OpenRouterDatabaseModel? {
        return dao.getByName(name)
    }

    suspend fun getAllModels(): List<OpenRouterDatabaseModel> {
        return dao.getAll()
    }

    fun getAllModelsFlow(): Flow<List<OpenRouterDatabaseModel>> {
        return dao.getAllFlow()
    }

    fun getModelsSortedByName(): Flow<List<OpenRouterDatabaseModel>> {
        return dao.getAllByNameFlow()
    }

    fun getModelsSortedByCreated(): Flow<List<OpenRouterDatabaseModel>> {
        return dao.getAllByCreatedFlow()
    }

    suspend fun getModelsByType(type: ModelType): List<OpenRouterDatabaseModel> {
        return dao.getByType(type)
    }

    fun getModelsByTypeFlow(type: ModelType): Flow<List<OpenRouterDatabaseModel>> {
        return dao.getByTypeFlow(type)
    }

    fun getToolsCapableModels(): Flow<List<OpenRouterDatabaseModel>> {
        return dao.getToolsCapableFlow()
    }

    fun getVisionCapableModels(): Flow<List<OpenRouterDatabaseModel>> {
        return dao.getVisionCapableFlow()
    }

    fun getStreamingCapableModels(): Flow<List<OpenRouterDatabaseModel>> {
        return dao.getStreamingCapableFlow()
    }

    suspend fun getFavorites(): List<OpenRouterDatabaseModel> {
        return dao.getFavorites()
    }

    fun getFavoritesFlow(): Flow<List<OpenRouterDatabaseModel>> {
        return dao.getFavoritesFlow()
    }

    suspend fun toggleFavorite(modelId: String): Result<Unit> = runCatching {
        val model = dao.getById(modelId) ?: throw IllegalStateException("Model not found")
        dao.updateFavorite(modelId, !model.isFavorite)
    }

    suspend fun setFavorite(modelId: String, isFavorite: Boolean): Result<Unit> = runCatching {
        dao.updateFavorite(modelId, isFavorite)
    }

    suspend fun getModelsByTag(tag: String): List<OpenRouterDatabaseModel> {
        return dao.getByTag(tag)
    }

    fun getModelsByTagFlow(tag: String): Flow<List<OpenRouterDatabaseModel>> {
        return dao.getByTagFlow(tag)
    }

    fun getFreeModels(): Flow<List<OpenRouterDatabaseModel>> {
        return dao.getFreeModelsFlow()
    }

    fun getPaidModelsByPrice(): Flow<List<OpenRouterDatabaseModel>> {
        return dao.getPaidModelsByPriceFlow()
    }

    fun getCheapestModels(limit: Int = 5): Flow<List<OpenRouterDatabaseModel>> {
        return dao.getPaidModelsByPriceFlow().map { it.take(limit) }
    }

    suspend fun searchModels(query: String): List<OpenRouterDatabaseModel> {
        return dao.search(query)
    }

    fun searchModelsFlow(query: String): Flow<List<OpenRouterDatabaseModel>> {
        return dao.searchFlow(query)
    }

    suspend fun updateSamplingParams(
        modelId: String,
        temp: Float,
        topP: Float
    ): Result<Unit> = runCatching {
        require(temp >= 0f) { "Temperature must be >= 0" }
        require(topP in 0f..1f) { "Top-P must be between 0 and 1" }
        dao.updateSamplingParams(modelId, temp, topP)
    }

    suspend fun updateMaxTokens(modelId: String, maxTokens: Int): Result<Unit> = runCatching {
        require(maxTokens > 0) { "Max tokens must be > 0" }
        dao.updateMaxTokens(modelId, maxTokens)
    }

    suspend fun markAsUsed(modelId: String): Result<Unit> = runCatching {
        dao.updateLastUsed(modelId)
    }

    suspend fun getModelCount(): Int {
        return dao.getCount()
    }

    fun getModelCountFlow(): Flow<Int> {
        return dao.getCountFlow()
    }

    suspend fun getModelCountByType(type: ModelType): Int {
        return dao.getCountByType(type)
    }

    suspend fun modelExists(modelId: String): Boolean {
        return dao.exists(modelId)
    }

    suspend fun modelExistsByModelId(modelId: String): Boolean {
        return dao.existsByModelId(modelId)
    }

    suspend fun estimateTotalCost(
        modelId: String,
        promptTokens: Int,
        completionTokens: Int
    ): Result<Float> = runCatching {
        val model = dao.getById(modelId) ?: throw IllegalStateException("Model not found")
        val promptCost = (promptTokens / 1_000_000f) * model.promptCostPer1M
        val completionCost = (completionTokens / 1_000_000f) * model.completionCostPer1M
        promptCost + completionCost
    }

    suspend fun getTotalFavoriteCost(): Float {
        return dao.getTotalFavoriteCost() ?: 0f
    }

    fun getModelsByCapabilities(
        needsTools: Boolean = false,
        needsVision: Boolean = false,
        needsStreaming: Boolean = false
    ): Flow<List<OpenRouterDatabaseModel>> {
        return dao.getAllFlow().map { models ->
            models.filter { model ->
                (!needsTools || model.supportsTools) &&
                (!needsVision || model.supportsVision) &&
                (!needsStreaming || model.supportsStreaming)
            }
        }
    }

    fun getRecommendedModels(): Flow<List<OpenRouterDatabaseModel>> {
        return dao.getAllFlow().map { models ->
            models.filter { it.isFavorite || it.isFree() }
                .sortedWith(
                    compareByDescending<OpenRouterDatabaseModel> { it.isFavorite }
                        .thenBy { it.promptCostPer1M }
                        .thenByDescending { it.lastUsedAt }
                )
        }
    }

    fun getModelsByPriceRange(minCost: Float, maxCost: Float): Flow<List<OpenRouterDatabaseModel>> {
        return dao.getAllFlow().map { models ->
            models.filter { it.promptCostPer1M in minCost..maxCost }
                .sortedBy { it.promptCostPer1M }
        }
    }

    suspend fun duplicateModel(modelId: String, newName: String): Result<OpenRouterDatabaseModel> = runCatching {
        val original = dao.getById(modelId) ?: throw IllegalStateException("Model not found")
        if (dao.existsByModelId(newName)) {
            throw IllegalStateException("Model with ID '$newName' already exists")
        }
        val duplicate = original.copy(
            id = java.util.UUID.randomUUID().toString(),
            modelName = newName,
            modelId = newName,
            createdAt = System.currentTimeMillis(),
            lastUsedAt = System.currentTimeMillis()
        )
        dao.insert(duplicate)
        duplicate
    }

    suspend fun exportModelConfig(modelId: String): Result<String> = runCatching {
        val model = dao.getById(modelId) ?: throw IllegalStateException("Model not found")
        buildString {
            appendLine("Model: ${model.modelName}")
            appendLine("Model ID: ${model.modelId}")
            appendLine("Type: ${model.modelType}")
            appendLine("Endpoint: ${model.endpoint}")
            appendLine("Context Size: ${model.ctxSize}")
            appendLine("Max Tokens: ${model.maxTokens}")
            appendLine("Temperature: ${model.temperature}")
            appendLine("Top-P: ${model.topP}")
            appendLine("Supports Tools: ${model.supportsTools}")
            appendLine("Supports Vision: ${model.supportsVision}")
            appendLine("Supports Streaming: ${model.supportsStreaming}")
            appendLine("Prompt Cost/1M: $${model.promptCostPer1M}")
            appendLine("Completion Cost/1M: $${model.completionCostPer1M}")
            appendLine("Is Free: ${model.isFree()}")
        }
    }

    suspend fun bulkUpdateFavorites(modelIds: List<String>, isFavorite: Boolean): Result<Unit> = runCatching {
        modelIds.forEach { dao.updateFavorite(it, isFavorite) }
    }

    suspend fun bulkUpdateMaxTokens(modelIds: List<String>, maxTokens: Int): Result<Unit> = runCatching {
        require(maxTokens > 0) { "Max tokens must be > 0" }
        modelIds.forEach { dao.updateMaxTokens(it, maxTokens) }
    }

    suspend fun syncFromServer(serverModels: List<OpenRouterDatabaseModel>): Result<SyncResult> = runCatching {
        val existingModels = dao.getAll()
        val existingIds = existingModels.map { it.modelId }.toSet()
        
        val newModels = serverModels.filter { it.modelId !in existingIds }
        val updatedModels = serverModels.filter { it.modelId in existingIds }
        
        dao.insertAll(newModels)
        updatedModels.forEach { dao.update(it) }
        
        SyncResult(
            added = newModels.size,
            updated = updatedModels.size,
            total = serverModels.size
        )
    }

    data class SyncResult(
        val added: Int,
        val updated: Int,
        val total: Int
    )
}