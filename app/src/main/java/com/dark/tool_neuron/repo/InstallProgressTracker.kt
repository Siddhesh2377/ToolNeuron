package com.dark.tool_neuron.repo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InstallProgressTracker @Inject constructor() {

    private val _extracting = MutableStateFlow<Set<String>>(emptySet())
    val extracting: StateFlow<Set<String>> = _extracting.asStateFlow()

    fun extractStarted(modelId: String) {
        _extracting.value = _extracting.value + modelId
    }

    fun extractFinished(modelId: String) {
        _extracting.value = _extracting.value - modelId
    }
}
