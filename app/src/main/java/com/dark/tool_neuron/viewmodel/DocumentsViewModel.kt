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
class DocumentsViewModel @Inject constructor(
    private val repository: ResearchRepository,
) : ViewModel() {

    private val _documents = MutableStateFlow<List<ResearchDocument>>(emptyList())
    val documents: StateFlow<List<ResearchDocument>> = _documents.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { repository.allDocuments() }
            _documents.value = list
        }
    }

    fun delete(docId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.deleteDocument(docId) }
            refresh()
        }
    }
}
