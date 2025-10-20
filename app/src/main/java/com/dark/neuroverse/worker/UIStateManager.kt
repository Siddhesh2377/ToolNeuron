package com.dark.neuroverse.worker

import com.dark.neuroverse.model.ChatUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object UIStateManager{
    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Idle)
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private fun setUIState(state: ChatUiState) {
        _uiState.value = state
    }

    fun setStateIdle() {
        setUIState(ChatUiState.Idle)
    }

    fun setStateLoading(message: String){
        setUIState(ChatUiState.Loading(message))
    }

    fun setStateGenerating(msgID: String, isFirstToken: Boolean = false){
        setUIState(ChatUiState.Generating(msgID, isFirstToken))
    }

    fun setStateError(error: String, isRetryable: Boolean = true, cause: Throwable? = null){
        setUIState(ChatUiState.Error(error, isRetryable, cause))
    }

    fun setStateDecoding(msgID: String, time: Long){
        setUIState(ChatUiState.DecodingStream(msgID, time))
    }

    fun setStateExecutingTool(toolName: String, messageID: String){
        setUIState(ChatUiState.ExecutingTool(toolName, messageID))
    }

    fun setStateGeneratingTitle(){
        setUIState(ChatUiState.GeneratingTitle)
    }

    fun toggleStateModelLoading(isLoading: Boolean) {
        if (isLoading) {
            setStateLoading("Loading model...")
        } else {
            setStateIdle()
        }
    }

    fun toggleStateChatLoading(isLoading: Boolean) {
        if (isLoading) {
            setStateLoading("Loading Chats...")
        } else {
            setStateIdle()
        }
    }

    fun toggleSwitchingModels(isSwitching: Boolean) {
        if (isSwitching) {
            setStateLoading("Switching models...")
        } else {
            setStateIdle()
        }
    }

    fun isGenerating(): Boolean {
        return _uiState.value is ChatUiState.Generating
    }
}