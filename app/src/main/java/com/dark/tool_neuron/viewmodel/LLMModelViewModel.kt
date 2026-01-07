package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.models.enums.ProviderType
import com.dark.tool_neuron.models.table_schema.Model
import com.dark.tool_neuron.models.table_schema.ModelConfig
import com.dark.tool_neuron.repo.ModelRepository
import com.dark.tool_neuron.worker.LlmModelWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class LLMModelViewModel(
    private val repository: ModelRepository
) : ViewModel() {

    val installedModels: Flow<List<Model>> = repository.getAllModels()
    val currentModelID = MutableStateFlow("")

    suspend fun getModelConfig(modelId: String): ModelConfig?{
       return repository.getConfigByModelId(modelId)
    }

    fun loadModel(model: Model) {
        viewModelScope.launch {
            val config = getModelConfig(model.id) ?: return@launch
            LlmModelWorker.loadGgufModel(model, config)
            currentModelID.value = model.id
        }
    }

}
