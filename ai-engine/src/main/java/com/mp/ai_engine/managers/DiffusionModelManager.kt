package com.mp.ai_engine.managers

import android.content.Context
import com.mp.ai_engine.databases.diffusion_model.DiffusionModelDataBaseProvider
import com.mp.ai_engine.databases.diffusion_model.DiffusionModelDatabaseAccessObject
import com.mp.ai_engine.models.image_models.DiffusionDatabaseModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

object DiffusionModelManager {

    private lateinit var dao: DiffusionModelDatabaseAccessObject
    private val isInitialized = AtomicBoolean(false)

    fun init(context: Context) {
        if (isInitialized.getAndSet(true)) return
        dao = DiffusionModelDataBaseProvider.getDatabase(context).DiffusionModelDatabaseAccessObject()
    }

    // ==================== Add/Remove Operations ====================

    suspend fun addModel(model: DiffusionDatabaseModel): Result<Unit> = runCatching {
        if (dao.getModelById(model.id) != null) {
            throw IllegalStateException("Model with id '${model.id}' already exists")
        }
        dao.insertModel(model)
    }

    suspend fun removeModel(modelId: String): Result<Unit> = runCatching {
        dao.deleteModelById(modelId)
    }

    suspend fun removeModelWithFile(modelId: String): Result<Unit> = runCatching {
        val model = dao.getModelById(modelId) ?: throw IllegalStateException("Model not found")
        val file = File(model.modelFolder)
        if (file.exists()) {
            file.delete()
        }
        dao.deleteModelById(modelId)
    }

    // ==================== Get Operations ====================

    suspend fun getModel(modelId: String): DiffusionDatabaseModel? {
        return dao.getModelById(modelId)
    }

    suspend fun getAllModels(): List<DiffusionDatabaseModel> {
        return dao.getAllModels()
    }

    suspend fun getCpuModels(): List<DiffusionDatabaseModel> {
        return dao.getCpuModels()
    }

    fun getCpuModelsFlow(): Flow<List<DiffusionDatabaseModel>> {
        return dao.getCpuModelsFlow()
    }

    suspend fun getNpuModels(): List<DiffusionDatabaseModel> {
        return dao.getNpuModels()
    }

    fun getNpuModelsFlow(): Flow<List<DiffusionDatabaseModel>> {
        return dao.getNpuModelsFlow()
    }

    // ==================== Search Operations ====================

    suspend fun searchModels(query: String): List<DiffusionDatabaseModel> {
        return dao.searchModels(query)
    }

    fun searchModelsFlow(query: String): Flow<List<DiffusionDatabaseModel>> {
        return dao.searchModelsFlow(query)
    }

    // ==================== Update Operations ====================

    suspend fun updateModelPrompts(
        modelId: String,
        prompt: String,
        negativePrompt: String
    ): Result<Unit> = runCatching {
        require(dao.getModelById(modelId) != null) { "Model with id '$modelId' does not exist" }
        dao.updateModelPrompts(modelId, prompt, negativePrompt)
    }

    // ==================== Validation Operations ====================

    suspend fun modelExists(modelId: String): Boolean {
        return dao.getModelById(modelId) != null
    }

    suspend fun validateModel(modelId: String): Result<Boolean> = runCatching {
        val model = dao.getModelById(modelId) ?: return@runCatching false
        File(model.modelFolder).exists()
    }

    fun getValidModels(): Flow<List<DiffusionDatabaseModel>> {
        return dao.getCpuModelsFlow().map { cpuModels ->
            val npuModels = dao.getNpuModels()
            (cpuModels + npuModels).filter { File(it.modelFolder).exists() }
        }
    }

    fun getInvalidModels(): Flow<List<DiffusionDatabaseModel>> {
        return dao.getCpuModelsFlow().map { cpuModels ->
            val npuModels = dao.getNpuModels()
            (cpuModels + npuModels).filter { !File(it.modelFolder).exists() }
        }
    }

    suspend fun cleanupInvalidModels(): Result<Int> = runCatching {
        val allModels = dao.getAllModels()
        val invalidModels = allModels.filter { !File(it.modelFolder).exists() }
        invalidModels.forEach { dao.deleteModelById(it.id) }
        invalidModels.size
    }

    // ==================== Storage Operations ====================

    suspend fun getModelCount(): Int {
        return dao.getModelCount()
    }

    suspend fun getTotalStorageUsed(): Long {
        val models = dao.getAllModels()
        return models.sumOf { model ->
            val file = File(model.modelFolder)
            if (file.exists()) file.length() else 0L
        }
    }

    fun getTotalStorageUsedFlow(): Flow<Long> {
        return dao.getCpuModelsFlow().map { cpuModels ->
            val npuModels = dao.getNpuModels()
            (cpuModels + npuModels).sumOf { model ->
                val file = File(model.modelFolder)
                if (file.exists()) file.length() else 0L
            }
        }
    }
}