package com.dark.tool_neuron.viewModel.home_screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.model.LocalModel
import com.mp.ai_engine.models.llm_models.ModelType
import com.mp.ai_engine.workers.installer.ModelInstaller
import com.mp.ai_engine.workers.model.ModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/*
    * Load & Un-Load Models ( Text | IMAGE_GEN )
    * */
class HomeScreenViewModel : ViewModel() {

    private val _selectedModel = MutableStateFlow(LocalModel("", "", ModelType.NONE))
    val selectedModel: StateFlow<LocalModel> = _selectedModel.asStateFlow()

    private val _installedModels = MutableStateFlow<List<LocalModel>>(emptyList())
    val installedModels: StateFlow<List<LocalModel>> = _installedModels.asStateFlow()

    private val _isDialogSelected = MutableStateFlow(false)
    val isDialogSelected: StateFlow<Boolean> = _isDialogSelected.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            observeModels()
        }
    }

    suspend fun observeModels() {
        val ggufModel = ModelInstaller.getInstalledGGUFModels()
        val diffusionModels = ModelInstaller.getInstalledDiffusionModels()

        ggufModel.forEach {
            _installedModels.value += LocalModel(it.id, it.modelName, ModelType.TEXT)
        }
        diffusionModels.forEach {
            _installedModels.value += LocalModel(it.id, it.name, ModelType.IMAGE_GEN)
        }
    }


    fun loadModel(modelID: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val decodedModel = ModelInstaller.findModel(modelID) ?: return@launch
            decodedModel.ggufModel.let {
                if (it == null) return@let
                val result = ModelManager.gguf().loadTextModel(it.toJson())
                if (result) {
                    _selectedModel.value = LocalModel(it.id, it.modelName, ModelType.TEXT)
                }
            }
            decodedModel.diffusionModel.let {
                if (it == null) return@let
                val result = ModelManager.diffusion().loadModel(it.toJson())
                if (result) {
                    _selectedModel.value = LocalModel(it.id, it.name, ModelType.IMAGE_GEN)
                }
            }
        }
    }

    fun setIsDialogOpen(boolean: Boolean){
        _isDialogSelected.value = boolean
    }
}