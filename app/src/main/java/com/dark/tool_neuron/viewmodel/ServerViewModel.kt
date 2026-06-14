package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.hxs_encryptor.PolicyEngine
import com.dark.native_server.BindMode
import com.dark.tool_neuron.data.SessionHolder
import com.dark.tool_neuron.model.ModelInfo
import com.dark.tool_neuron.model.enums.PathType
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
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

    val anyEngineInstalled: StateFlow<Boolean> = modelRepo.models
        .map { list -> list.any { it.pathType == PathType.FILE } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val chatModels: StateFlow<List<ModelInfo>> = modelRepo.models
        .map { list ->
            list.filter { it.pathType == PathType.FILE && it.providerType == ProviderType.GGUF }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _selectedChatModelId = MutableStateFlow(controller.selectedModelId())
    val selectedChatModelId: StateFlow<String> = _selectedChatModelId.asStateFlow()

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

    fun setChatDefaultModel(modelId: String) {
        _selectedChatModelId.value = modelId
        controller.setChatDefaultModelId(modelId)
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
