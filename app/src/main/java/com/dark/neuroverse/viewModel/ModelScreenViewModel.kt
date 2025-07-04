package com.dark.neuroverse.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.ai_manager.ai.data.ModelsData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.content.Context
import androidx.lifecycle.ViewModelProvider
import com.dark.neuroverse.worker.model.ModelManager

class ModelScreenViewModel(context: Context) : ViewModel() {

    private val _models = MutableStateFlow<List<ModelsData>>(emptyList())
    val models: StateFlow<List<ModelsData>> = _models

    init {
        ModelManager.init(context)
        observeModels()
    }

    private fun observeModels() {
        viewModelScope.launch {
            ModelManager.observeModels().collectLatest { modelList ->
                _models.value = modelList
            }
        }
    }

    fun addModel(model: ModelsData) {
        viewModelScope.launch {
            ModelManager.addModel(model)
        }
    }

    fun checkIfInstalled(modelName: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val exists = ModelManager.checkIfInstalled(modelName)
            onResult(exists)
        }
    }

    fun removeModel(modelName: String) {
        viewModelScope.launch {
            ModelManager.removeModel(modelName)
        }
    }

    fun getModel(modelName: String, onResult: (ModelsData?) -> Unit) {
        viewModelScope.launch {
            val model = ModelManager.getModel(modelName)
            onResult(model)
        }
    }
}


class ModelScreenViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ModelScreenViewModel::class.java)) {
            return ModelScreenViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

