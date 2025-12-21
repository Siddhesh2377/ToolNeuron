package com.mp.ai_engine.managers

import android.content.Context
import com.mp.ai_engine.databases.gguf.GGUFDataBaseProvider
import com.mp.ai_engine.databases.gguf.GGUFDatabaseAccessObject
import com.mp.ai_engine.models.llm_models.GGUFDatabaseModel
import com.mp.ai_engine.models.llm_models.ModelType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

object GGUFModelManager{

    private lateinit var dao: GGUFDatabaseAccessObject
    private val isInitialized = AtomicBoolean(false)

    fun init(context: Context) {
        if (isInitialized.getAndSet(true)) return

        dao = GGUFDataBaseProvider.getDatabase(context).GGUFDatabaseAccessObject()
    }

    suspend fun addModel(model: GGUFDatabaseModel): Result<Unit> = runCatching {
        if (dao.existsByName(model.modelName)) {
            throw IllegalStateException("Model with name '${model.modelName}' already exists")
        }
        dao.insert(model)
    }

    suspend fun addModels(models: List<GGUFDatabaseModel>): Result<Unit> = runCatching {
        dao.insertAll(models)
    }

    suspend fun updateModel(model: GGUFDatabaseModel): Result<Unit> = runCatching {
        if (!dao.exists(model.id)) {
            throw IllegalStateException("Model with id '${model.id}' does not exist")
        }
        dao.update(model)
    }

    suspend fun removeModel(modelId: String): Result<Unit> = runCatching {
        dao.deleteById(modelId)
    }

    suspend fun removeModelWithFile(modelId: String): Result<Unit> = runCatching {
        val model = dao.getById(modelId) ?: throw IllegalStateException("Model not found")
        val file = File(model.modelPath)
        if (file.exists()) {
            file.delete()
        }
        dao.deleteById(modelId)
    }

    suspend fun clearAll(): Result<Unit> = runCatching {
        dao.deleteAll()
    }

    suspend fun clearImported(): Result<Unit> = runCatching {
        dao.deleteImported()
    }

    suspend fun getModel(modelId: String): GGUFDatabaseModel? {
        return dao.getById(modelId)
    }

    fun getModelFlow(modelId: String): Flow<GGUFDatabaseModel?> {
        return dao.getByIdFlow(modelId)
    }

    suspend fun getModelByName(name: String): GGUFDatabaseModel? {
        return dao.getByName(name)
    }

    suspend fun getAllModels(): List<GGUFDatabaseModel> {
        return dao.getAll()
    }

    fun getAllModelsFlow(): Flow<List<GGUFDatabaseModel>> {
        return dao.getAllFlow()
    }

    fun getModelsSortedByName(): Flow<List<GGUFDatabaseModel>> {
        return dao.getAllByNameFlow()
    }

    fun getModelsSortedByCreated(): Flow<List<GGUFDatabaseModel>> {
        return dao.getAllByCreatedFlow()
    }

    suspend fun getModelsByType(type: ModelType): List<GGUFDatabaseModel> {
        return dao.getByType(type)
    }

    fun getModelsByTypeFlow(type: ModelType): Flow<List<GGUFDatabaseModel>> {
        return dao.getByTypeFlow(type)
    }

    fun getTextModels(): Flow<List<GGUFDatabaseModel>> {
        return dao.getTextModelsFlow()
    }

    fun getVLMModels(): Flow<List<GGUFDatabaseModel>> {
        return dao.getVLMModelsFlow()
    }

    fun getEmbeddingModels(): Flow<List<GGUFDatabaseModel>> {
        return dao.getEmbeddingModelsFlow()
    }

    suspend fun getModelsByTag(tag: String): List<GGUFDatabaseModel> {
        return dao.getByTag(tag)
    }

    fun getModelsByTagFlow(tag: String): Flow<List<GGUFDatabaseModel>> {
        return dao.getByTagFlow(tag)
    }

    suspend fun searchModels(query: String): List<GGUFDatabaseModel> {
        return dao.search(query)
    }

    fun searchModelsFlow(query: String): Flow<List<GGUFDatabaseModel>> {
        return dao.searchFlow(query)
    }

    fun getImportedModels(): Flow<List<GGUFDatabaseModel>> {
        return dao.getImportedFlow()
    }

    fun getDownloadedModels(): Flow<List<GGUFDatabaseModel>> {
        return dao.getDownloadedFlow()
    }

    fun getValidModels(): Flow<List<GGUFDatabaseModel>> {
        return dao.getAllFlow().map { models ->
            models.filter { File(it.modelPath).exists() }
        }
    }

    fun getInvalidModels(): Flow<List<GGUFDatabaseModel>> {
        return dao.getAllFlow().map { models ->
            models.filter { !File(it.modelPath).exists() }
        }
    }

    suspend fun updateGpuLayers(modelId: String, layers: Int): Result<Unit> = runCatching {
        require(layers >= 0) { "GPU layers must be >= 0" }
        dao.updateGpuLayers(modelId, layers)
    }

    suspend fun updateThreads(modelId: String, threads: Int): Result<Unit> = runCatching {
        require(threads > 0) { "Threads must be > 0" }
        dao.updateThreads(modelId, threads)
    }

    suspend fun updateSamplingParams(
        modelId: String,
        temp: Float,
        topP: Float,
        topK: Int
    ): Result<Unit> = runCatching {
        require(temp >= 0f) { "Temperature must be >= 0" }
        require(topP in 0f..1f) { "Top-P must be between 0 and 1" }
        require(topK > 0) { "Top-K must be > 0" }
        dao.updateSamplingParams(modelId, temp, topP, topK)
    }

    suspend fun updateSystemPrompt(modelId: String, prompt: String): Result<Unit> = runCatching {
        dao.updateSystemPrompt(modelId, prompt)
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

    suspend fun modelExistsByName(name: String): Boolean {
        return dao.existsByName(name)
    }

    suspend fun validateModel(modelId: String): Result<Boolean> = runCatching {
        val model = dao.getById(modelId) ?: return@runCatching false
        File(model.modelPath).exists()
    }

    suspend fun cleanupInvalidModels(): Result<Int> = runCatching {
        val allModels = dao.getAll()
        val invalidModels = allModels.filter { !File(it.modelPath).exists() }
        invalidModels.forEach { dao.delete(it) }
        invalidModels.size
    }

    suspend fun getTotalStorageUsed(): Long {
        val models = dao.getAll()
        return models.sumOf { model ->
            val file = File(model.modelPath)
            if (file.exists()) file.length() else 0L
        }
    }

    fun getTotalStorageUsedFlow(): Flow<Long> {
        return dao.getAllFlow().map { models ->
            models.sumOf { model ->
                val file = File(model.modelPath)
                if (file.exists()) file.length() else 0L
            }
        }
    }

    suspend fun duplicateModel(modelId: String, newName: String): Result<GGUFDatabaseModel> = runCatching {
        val original = dao.getById(modelId) ?: throw IllegalStateException("Model not found")
        if (dao.existsByName(newName)) {
            throw IllegalStateException("Model with name '$newName' already exists")
        }
        val duplicate = original.copy(
            id = java.util.UUID.randomUUID().toString(),
            modelName = newName,
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
            appendLine("Type: ${model.modelType}")
            appendLine("Architecture: ${model.architecture}")
            appendLine("Context Size: ${model.ctxSize}")
            appendLine("GPU Layers: ${model.gpuLayers}")
            appendLine("Threads: ${model.threads}")
            appendLine("Temperature: ${model.temp}")
            appendLine("Top-K: ${model.topK}")
            appendLine("Top-P: ${model.topP}")
            appendLine("System Prompt: ${model.systemPrompt}")
        }
    }

    suspend fun bulkUpdateGpuLayers(modelIds: List<String>, layers: Int): Result<Unit> = runCatching {
        require(layers >= 0) { "GPU layers must be >= 0" }
        modelIds.forEach { dao.updateGpuLayers(it, layers) }
    }

    suspend fun bulkUpdateThreads(modelIds: List<String>, threads: Int): Result<Unit> = runCatching {
        require(threads > 0) { "Threads must be > 0" }
        modelIds.forEach { dao.updateThreads(it, threads) }
    }
}