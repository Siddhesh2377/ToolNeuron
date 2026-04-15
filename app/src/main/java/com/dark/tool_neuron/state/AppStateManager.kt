package com.dark.tool_neuron.state

import android.util.Log
import com.dark.tool_neuron.models.state.AppState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppStateManager {

    private const val TAG = "AppStateManager"

    private val _appState = MutableStateFlow<AppState>(AppState.Welcome)
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    // Current loaded model info
    private var currentModelName: String? = null
    private var hasMessages: Boolean = false

    private val _isChatRefreshed = MutableStateFlow(false)
    val isChatRefreshed: StateFlow<Boolean> = _isChatRefreshed.asStateFlow()

    // ── Model Reload Signal ──

    private val _reloadModelRequested = MutableStateFlow(false)
    val reloadModelRequested: StateFlow<Boolean> = _reloadModelRequested.asStateFlow()

    fun requestModelReload() { _reloadModelRequested.value = true }
    fun clearReloadRequest() { _reloadModelRequested.value = false }

    // Model loading progress tracking
    private var loadingStartTime: Long = 0

    /**
     * Only emit a new state if it differs from the current value, avoiding redundant recompositions.
     */
    private fun setStateIfChanged(newState: AppState) {
        if (_appState.value != newState) {
            _appState.value = newState
        }
    }

    /**
     * Update when a model starts loading
     */
    fun setLoadingModel(modelName: String, progress: Float = 0f) {
        if (progress == 0f) {
            loadingStartTime = System.currentTimeMillis()
        }
        currentModelName = modelName
        setStateIfChanged(AppState.LoadingModel(modelName, progress))
    }

    /**
     * Update when model loading completes
     */
    fun setModelLoaded(modelName: String) {
        currentModelName = modelName
        val loadingTime = System.currentTimeMillis() - loadingStartTime
        Log.d(TAG, "Model loaded in ${loadingTime}ms")
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
            setStateIfChanged(AppState.GeneratingText(it))
        }
    }

    /**
     * Update when image generation starts
     */
    fun setGeneratingImage() {
        currentModelName?.let {
            setStateIfChanged(AppState.GeneratingImage(it))
        }
    }

    /**
     * Update when plugin tool execution starts
     */
    fun setExecutingPlugin(pluginName: String, toolName: String) {
        setStateIfChanged(AppState.ExecutingPlugin(pluginName, toolName))
    }

    /**
     * Update when plugin tool execution completes (success or failure)
     */
    fun setPluginExecutionComplete(
        pluginName: String,
        toolName: String,
        success: Boolean,
        executionTimeMs: Long,
        errorMessage: String? = null
    ) {
        setStateIfChanged(AppState.PluginExecutionComplete(
            pluginName = pluginName,
            toolName = toolName,
            success = success,
            executionTimeMs = executionTimeMs,
            errorMessage = errorMessage
        ))
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
        setStateIfChanged(AppState.Error(message, currentModelName))
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
     * Internal: Determine the correct idle state based on current conditions
     */
    private fun updateIdleState() {
        setStateIfChanged(when {
            currentModelName == null && !hasMessages -> AppState.Welcome
            currentModelName == null && hasMessages -> AppState.NoModelLoaded
            currentModelName != null -> AppState.ModelLoaded(currentModelName!!)
            else -> AppState.Welcome
        })
    }

}