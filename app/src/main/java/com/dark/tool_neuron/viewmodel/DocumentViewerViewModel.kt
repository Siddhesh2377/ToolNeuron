package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.model.ResearchDocument
import com.dark.tool_neuron.repo.ResearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class DocumentViewerViewModel @Inject constructor(
    private val repository: ResearchRepository,
) : ViewModel() {

    private val _document = MutableStateFlow<ResearchDocument?>(null)
    val document: StateFlow<ResearchDocument?> = _document.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun load(docId: String) {
        viewModelScope.launch {
            _loading.value = true
            val doc = withContext(Dispatchers.IO) { repository.getDocument(docId) }
            _document.value = doc
            _loading.value = false
        }
    }
}
