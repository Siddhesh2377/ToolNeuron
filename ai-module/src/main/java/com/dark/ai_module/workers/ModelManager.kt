package com.dark.ai_module.workers

import android.content.Context
import com.dark.ai_module.ai.Neuron
import com.dark.ai_module.db.DatabaseProvider
import com.dark.ai_module.db.ModelDAO
import com.dark.ai_module.model.ModelsData
import com.dark.ai_module.model.getDefaultModelData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import java.io.File

object ModelManager {

    private var daoInitialized = false
    private lateinit var dao: ModelDAO
    private var currentModel: MutableStateFlow<ModelsData> = MutableStateFlow(getDefaultModelData())
    private var modelParams =
        MutableStateFlow(Pair(ModelParams.Professional(), ModelParams.Emotional()))

    fun getModel() = currentModel

    fun getModelParams() = modelParams

    fun setCurrentModel(model: ModelsData) {
        currentModel.value = model
    }



    fun updateModelParams(
        professional: ModelParams.Professional = ModelParams.Professional(),
        emotional: ModelParams.Emotional = ModelParams.Emotional()
    ) {
        modelParams.value = Pair(professional, emotional)
    }

    fun loadModel(context: Context, modelData: ModelsData, onLoaded: () -> Unit) {

        Neuron.loadModel(
            File(modelData.modelPath),
            context = context,
            systemPrompt = "You are a helpful assistant.",
            contextLength = 8024,
            chatTemplate = modelData.chatTemplate
        ) {
            currentModel = MutableStateFlow(modelData)
            currentModel.value = modelData
            onLoaded()
        }
    }


    fun init(context: Context) {
        if (!daoInitialized) {
            dao = DatabaseProvider.getDatabase(context).ModelDAO()
            daoInitialized = true
        }
    }

    fun observeModels(): Flow<List<ModelsData>> {
        return dao.getAllModels()
    }

    suspend fun addModel(model: ModelsData) {
        dao.insertModel(model)
    }

    suspend fun checkIfInstalled(modelName: String): Boolean {
        return dao.getModelByName(modelName) != null
    }

    suspend fun removeModel(modelName: String) {
        val model = dao.getModelByName(modelName)
        if (model != null) {
            dao.deleteModel(model)
        }
    }

    suspend fun getFirstModel(): ModelsData? {
        return dao.getAllModels().first().firstOrNull()
    }

    suspend fun getModel(modelName: String): ModelsData? {
        return dao.getModelByName(modelName)
    }

    suspend fun isAnyModelInstalled(): Boolean {
        return dao.getAllModels().first().isNotEmpty()
    }
}

object ModelParams {
    data class Professional(val float: Float = 3.5f)
    data class Emotional(val float: Float = 7.6f)
}