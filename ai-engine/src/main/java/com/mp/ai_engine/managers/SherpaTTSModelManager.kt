package com.mp.ai_engine.managers


import android.content.Context
import com.mp.ai_engine.databases.sherpa_tts.SherpaTTSDataBaseProvider
import com.mp.ai_engine.databases.sherpa_tts.SherpaTTSDatabaseAccessObject
import com.mp.ai_engine.models.llm_models.SherpaTTSDatabaseModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

object SherpaTTSModelManager {

    private lateinit var dao: SherpaTTSDatabaseAccessObject
    private val isInitialized = AtomicBoolean(false)

    fun init(context: Context) {
        if (isInitialized.getAndSet(true)) return

        dao = SherpaTTSDataBaseProvider.getDatabase(context).SherpaTTSDatabaseAccessObject()
    }

    suspend fun addModel(model: SherpaTTSDatabaseModel): Result<Unit> = runCatching {
        if (dao.existsByName(model.modelName)) {
            throw IllegalStateException("Model with name '${model.modelName}' already exists")
        }
        dao.insert(model)
    }

    suspend fun addModels(models: List<SherpaTTSDatabaseModel>): Result<Unit> = runCatching {
        dao.insertAll(models)
    }

    suspend fun updateModel(model: SherpaTTSDatabaseModel): Result<Unit> = runCatching {
        if (!dao.exists(model.id)) {
            throw IllegalStateException("Model with id '${model.id}' does not exist")
        }
        dao.update(model)
    }

    suspend fun removeModel(modelId: String): Result<Unit> = runCatching {
        dao.deleteById(modelId)
    }

    suspend fun removeModelWithFiles(modelId: String): Result<Unit> = runCatching {
        val model = dao.getById(modelId) ?: throw IllegalStateException("Model not found")
        val dir = File(model.modelDir)
        if (dir.exists() && dir.isDirectory) {
            dir.deleteRecursively()
        }
        dao.deleteById(modelId)
    }

    suspend fun clearAll(): Result<Unit> = runCatching {
        dao.deleteAll()
    }

    suspend fun getModel(modelId: String): SherpaTTSDatabaseModel? {
        return dao.getById(modelId)
    }

    fun getModelFlow(modelId: String): Flow<SherpaTTSDatabaseModel?> {
        return dao.getByIdFlow(modelId)
    }

    suspend fun getModelByName(name: String): SherpaTTSDatabaseModel? {
        return dao.getByName(name)
    }

    suspend fun getAllModels(): List<SherpaTTSDatabaseModel> {
        return dao.getAll()
    }

    fun getAllModelsFlow(): Flow<List<SherpaTTSDatabaseModel>> {
        return dao.getAllFlow()
    }

    fun getModelsSortedByName(): Flow<List<SherpaTTSDatabaseModel>> {
        return dao.getAllByNameFlow()
    }

    fun getModelsSortedByCreated(): Flow<List<SherpaTTSDatabaseModel>> {
        return dao.getAllByCreatedFlow()
    }

    suspend fun searchModels(query: String): List<SherpaTTSDatabaseModel> {
        return dao.search(query)
    }

    fun searchModelsFlow(query: String): Flow<List<SherpaTTSDatabaseModel>> {
        return dao.searchFlow(query)
    }

    fun getValidModels(): Flow<List<SherpaTTSDatabaseModel>> {
        return dao.getAllFlow().map { models ->
            models.filter { File(it.modelDir).exists() }
        }
    }

    fun getInvalidModels(): Flow<List<SherpaTTSDatabaseModel>> {
        return dao.getAllFlow().map { models ->
            models.filter { !File(it.modelDir).exists() }
        }
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

    suspend fun modelExists(modelId: String): Boolean {
        return dao.exists(modelId)
    }

    suspend fun modelExistsByName(name: String): Boolean {
        return dao.existsByName(name)
    }

    suspend fun validateModel(modelId: String): Result<Boolean> = runCatching {
        val model = dao.getById(modelId) ?: return@runCatching false
        File(model.modelDir).exists()
    }

    suspend fun cleanupInvalidModels(): Result<Int> = runCatching {
        val allModels = dao.getAll()
        val invalidModels = allModels.filter { !File(it.modelDir).exists() }
        invalidModels.forEach { dao.delete(it) }
        invalidModels.size
    }

    suspend fun getTotalStorageUsed(): Long {
        val models = dao.getAll()
        return models.sumOf { model ->
            val dir = File(model.modelDir)
            if (dir.exists() && dir.isDirectory) {
                dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            } else 0L
        }
    }

    fun getTotalStorageUsedFlow(): Flow<Long> {
        return dao.getAllFlow().map { models ->
            models.sumOf { model ->
                val dir = File(model.modelDir)
                if (dir.exists() && dir.isDirectory) {
                    dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                } else 0L
            }
        }
    }

    suspend fun getModelsByVoiceCount(minVoices: Int): List<SherpaTTSDatabaseModel> {
        return dao.getAll().filter { it.voices.size >= minVoices }
    }

    fun getModelsByVoiceCountFlow(minVoices: Int): Flow<List<SherpaTTSDatabaseModel>> {
        return dao.getAllFlow().map { models ->
            models.filter { it.voices.size >= minVoices }
        }
    }

    suspend fun exportModelConfig(modelId: String): Result<String> = runCatching {
        val model = dao.getById(modelId) ?: throw IllegalStateException("Model not found")
        buildString {
            appendLine("Model: ${model.modelName}")
            appendLine("Description: ${model.modelDescription}")
            appendLine("Sample Rate: ${model.sampleRate} Hz")
            appendLine("Speed Control: ${model.speedControl}x")
            appendLine("Number of Voices: ${model.voices.size}")
            model.voices.forEachIndexed { index, voice ->
                appendLine("Voice ${index + 1}: ${voice.name} (${voice.gender}, ${voice.tone})")
            }
        }
    }
}