package com.dark.ai_module.workers

import android.content.Context
import com.dark.ai_module.db.DatabaseProvider
import com.dark.ai_module.db.ModelDAO
import com.dark.ai_module.model.ModelsData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

object ModelManager {

    private var daoInitialized = false
    private lateinit var dao: ModelDAO

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

    suspend fun getModel(modelName: String): ModelsData? {
        return dao.getModelByName(modelName)
    }

    suspend fun isAnyModelInstalled(): Boolean {
        return dao.getAllModels().first().isNotEmpty()
    }
}