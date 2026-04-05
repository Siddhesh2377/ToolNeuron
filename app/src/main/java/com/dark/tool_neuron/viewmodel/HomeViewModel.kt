package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor() : ViewModel() {

    private val _actionWindowExpanded = MutableStateFlow(false)
    val actionWindowExpanded = _actionWindowExpanded.asStateFlow()

    fun toggleActionWindow() {
        _actionWindowExpanded.value = !_actionWindowExpanded.value
    }

    fun collapseActionWindow() {
        _actionWindowExpanded.value = false
    }
}
