package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.repo.StorageCategoryId
import com.dark.tool_neuron.repo.StorageInspector
import com.dark.tool_neuron.repo.StorageSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StorageUiState(
    val snapshot: StorageSnapshot? = null,
    val isLoading: Boolean = true,
    val pendingClear: StorageCategoryId? = null,
    val message: String? = null,
)

@HiltViewModel
class StorageViewModel @Inject constructor(
    private val inspector: StorageInspector,
) : ViewModel() {

    private val _state = MutableStateFlow(StorageUiState())
    val state: StateFlow<StorageUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val snap = inspector.snapshot()
            _state.value = _state.value.copy(snapshot = snap, isLoading = false)
        }
    }

    fun requestClear(category: StorageCategoryId) {
        if (category == StorageCategoryId.SYSTEM) return
        _state.value = _state.value.copy(pendingClear = category)
    }

    fun cancelClear() {
        _state.value = _state.value.copy(pendingClear = null)
    }

    fun confirmClear() {
        val target = _state.value.pendingClear ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(pendingClear = null, isLoading = true)
            inspector.clear(target)
            val snap = inspector.snapshot()
            _state.value = _state.value.copy(
                snapshot = snap,
                isLoading = false,
                message = clearedMessage(target),
            )
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private fun clearedMessage(id: StorageCategoryId): String = when (id) {
        StorageCategoryId.CHAT_MODELS -> "Chat models cleared"
        StorageCategoryId.VLM -> "Vision models cleared"
        StorageCategoryId.VOICE -> "Voice models cleared"
        StorageCategoryId.DOCUMENTS -> "Documents cleared"
        StorageCategoryId.CHATS -> "Chat history cleared"
        StorageCategoryId.CACHE -> "Cache cleared"
        StorageCategoryId.SYSTEM -> ""
    }
}
