package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.hxs_encryptor.PolicyEngine
import com.dark.native_server.BindMode
import com.dark.tool_neuron.data.SessionHolder
import com.dark.tool_neuron.model.ModelInfo
import com.dark.tool_neuron.model.enums.ProviderType
import com.dark.tool_neuron.repo.ModelRepository
import com.dark.tool_neuron.service.server.ServerController
import com.dark.tool_neuron.service.server.ServerRequestEvent
import com.dark.tool_neuron.service.server.ServerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ServerViewModel @Inject constructor(
    private val controller: ServerController,
    private val session: SessionHolder,
    private val modelRepo: ModelRepository,
) : ViewModel() {

    val state: StateFlow<ServerState> = controller.state
    val requestEvents: StateFlow<List<ServerRequestEvent>> = controller.requestEvents

    private val _port = MutableStateFlow(controller.port())
    val port: StateFlow<Int> = _port.asStateFlow()

    private val _bindMode = MutableStateFlow(controller.bindMode())
    val bindMode: StateFlow<BindMode> = _bindMode.asStateFlow()

    private val _tokenVisible = MutableStateFlow(false)
    val tokenVisible: StateFlow<Boolean> = _tokenVisible.asStateFlow()

    val installedChatModels: StateFlow<List<ModelInfo>> = modelRepo.models
        .map { list -> list.filter { it.providerType == ProviderType.GGUF } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _selectedModelId = MutableStateFlow(controller.selectedModelId().ifBlank { null })
    val selectedModelId: StateFlow<String?> = _selectedModelId.asStateFlow()

    init {
        ensureSelectionValid()
    }

    private fun ensureSelectionValid() {
        val current = _selectedModelId.value
        val installed = installedChatModels.value
        if (current != null && installed.none { it.id == current }) {
            _selectedModelId.value = null
            controller.setSelectedModelId("")
        }
        if (_selectedModelId.value == null && installed.size == 1) {
            selectModel(installed.first().id)
        }
    }

    fun selectModel(modelId: String) {
        _selectedModelId.value = modelId
        controller.setSelectedModelId(modelId)
    }

    fun start() {
        controller.setPort(_port.value)
        controller.setBindMode(_bindMode.value)
        controller.markConfigured()
        controller.start()
    }

    fun stop() {
        controller.stop()
        _tokenVisible.value = false
    }

    fun setPort(port: Int) {
        val clamped = port.coerceIn(1024, 65535)
        _port.value = clamped
        controller.setPort(clamped)
    }

    fun setBindMode(mode: BindMode) {
        _bindMode.value = mode
        controller.setBindMode(mode)
    }

    fun revealToken(): Boolean {
        if (!session.isAllowed(PolicyEngine.Feature.AUTH_VERIFY)) return false
        _tokenVisible.value = true
        return true
    }

    fun hideToken() {
        _tokenVisible.value = false
    }

    fun currentToken(): String = controller.currentToken()

    fun rotateToken() {
        controller.rotateToken()
        _tokenVisible.value = false
    }

    fun maskedToken(): String = maskedSnapshot()

    private fun maskedSnapshot(): String {
        val raw = controller.currentToken()
        if (raw.isBlank()) return ""
        val head = raw.take(6)
        return "$head${"•".repeat(8)}"
    }
}
