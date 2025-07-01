package com.dark.neuroverse.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.neuroverse.data.model.ModelsData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.content.Context
import com.dark.neuroverse.data.db.DatabaseProvider

class ModelScreenViewModel(context: Context) : ViewModel() {

    private val _models = MutableStateFlow<List<ModelsData>>(emptyList())
    val models: StateFlow<List<ModelsData>> = _models

    private val dao = DatabaseProvider.getDatabase(context).ModelDAO()

    init {
        observeModels()
    }

    private fun observeModels() {
        viewModelScope.launch {
            dao.getAllModels().collectLatest { modelList ->
                _models.value = modelList
            }
        }
    }

    fun addModel(model: ModelsData) {
        viewModelScope.launch {
            dao.insertModel(model)
        }
    }

    fun checkIfInstalled(modelName: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val exists = dao.getModelByName(modelName) != null
            onResult(exists)
        }
    }

    fun removeModel(modelName: String) {
        viewModelScope.launch {
            val model = dao.getModelByName(modelName)
            if (model != null) {
                dao.deleteModel(model)
            }
        }
    }

    fun getModel(modelName: String, onResult: (ModelsData?) -> Unit) {
        viewModelScope.launch {
            val model = dao.getModelByName(modelName)
            onResult(model)
        }
    }
}
