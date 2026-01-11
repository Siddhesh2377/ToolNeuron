package com.dark.tool_neuron.state

import com.dark.tool_neuron.models.state.AppState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppStateManager {
    
    private val _appState = MutableStateFlow<AppState>(AppState.Welcome)
    val appState: StateFlow<AppState> = _appState.asStateFlow()
    
    // Current loaded model info
    private var currentModelName: String? = null
    private var hasMessages: Boolean = false
    private val _isChatRefreshed = MutableStateFlow(false)
    val isChatRefreshed: StateFlow<Boolean> = _isChatRefreshed.asStateFlow()
    
    /**
     * Update when a model starts loading
     */
    fun setLoadingModel(modelName: String, progress: Float = 0f) {
        currentModelName = modelName
        _appState.value = AppState.LoadingModel(modelName, progress)
    }
    
    /**
     * Update when model loading completes
     */
    fun setModelLoaded(modelName: String) {
        currentModelName = modelName
        updateIdleState()
    }
    
    /**
     * Update when model is unloaded
     */
    fun setModelUnloaded() {
        currentModelName = null
        updateIdleState()
    }

    fun chatRefreshed() {
        _isChatRefreshed.value = true
    }

    fun unRefreshChat() {
        _isChatRefreshed.value = false
    }
    
    /**
     * Update when text generation starts
     */
    fun setGeneratingText() {
        currentModelName?.let {
            _appState.value = AppState.GeneratingText(it)
        }
    }
    
    /**
     * Update when image generation starts
     */
    fun setGeneratingImage() {
        currentModelName?.let {
            _appState.value = AppState.GeneratingImage(it)
        }
    }
    
    /**
     * Update when audio generation starts
     */
    fun setGeneratingAudio() {
        currentModelName?.let {
            _appState.value = AppState.GeneratingAudio(it)
        }
    }
    
    /**
     * Update when generation completes (returns to idle)
     */
    fun setGenerationComplete() {
        updateIdleState()
    }
    
    /**
     * Update when an error occurs
     */
    fun setError(message: String) {
        _appState.value = AppState.Error(message, currentModelName)
    }
    
    /**
     * Clear error and return to appropriate idle state
     */
    fun clearError() {
        updateIdleState()
    }
    
    /**
     * Update message count (affects welcome screen logic)
     */
    fun setHasMessages(hasMessages: Boolean) {
        this.hasMessages = hasMessages
        // Only update if we're in an idle state
        if (_appState.value is AppState.Welcome ||
            _appState.value is AppState.NoModelLoaded ||
            _appState.value is AppState.ModelLoaded) {
            updateIdleState()
        }
    }
    
    /**
     * Get current state (synchronous)
     */
    fun getCurrentState(): AppState = _appState.value
    
    /**
     * Check if currently generating
     */
    fun isGenerating(): Boolean = _appState.value is AppState.GeneratingText ||
            _appState.value is AppState.GeneratingImage ||
            _appState.value is AppState.GeneratingAudio
    
    /**
     * Check if model is loaded
     */
    fun isModelLoaded(): Boolean = currentModelName != null
    
    /**
     * Internal: Determine the correct idle state based on current conditions
     */
    private fun updateIdleState() {
        _appState.value = when {
            currentModelName == null && !hasMessages -> AppState.Welcome
            currentModelName == null && hasMessages -> AppState.NoModelLoaded
            currentModelName != null -> AppState.ModelLoaded(currentModelName!!)
            else -> AppState.Welcome
        }
    }
    
    /**
     * Reset to initial state (useful for testing or logout)
     */
    fun reset() {
        currentModelName = null
        hasMessages = false
        _appState.value = AppState.Welcome
    }
}