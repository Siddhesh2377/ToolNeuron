package com.dark.neuroverse.viewModel.stt

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.ai_module.model.ModelData
import com.dark.ai_module.model.ModelType
import com.dark.ai_module.workers.AudioManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class STTViewModel : ViewModel() {

    companion object {
        private const val TAG = "STTViewModel"
        private const val DEFAULT_SAMPLE_RATE = 16000
    }

    // UI State
    private val _uiState = MutableStateFlow(STTUiState())
    val uiState: StateFlow<STTUiState> = _uiState.asStateFlow()

    // Transcription history
    private val _transcriptionHistory = MutableStateFlow<List<TranscriptionItem>>(emptyList())
    val transcriptionHistory: StateFlow<List<TranscriptionItem>> = _transcriptionHistory.asStateFlow()

    // Events
    private val _events = MutableSharedFlow<STTEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<STTEvent> = _events.asSharedFlow()

    // Model info from AudioManager
    val currentModel: StateFlow<ModelData?> = AudioManager.sttModel
    val isProcessing: StateFlow<Boolean> = AudioManager.isSttProcessing

    init {
        // Observe AudioManager state
        viewModelScope.launch {
            combine(
                AudioManager.sttModel,
                AudioManager.isSttProcessing
            ) { model, processing ->
                _uiState.update {
                    it.copy(
                        isReady = model != null && AudioManager.isSttReady(),
                        isTranscribing = processing,
                        activeStreams = AudioManager.getActiveStreamCount()
                    )
                }
            }.collect()
        }
    }

    // Initialize STT model
    fun initialize(modelData: ModelData) {
        if (_uiState.value.isInitializing) {
            Log.w(TAG, "Already initializing")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isInitializing = true, error = null) }

            try {
                val result = AudioManager.loadSttModel(modelData)

                result.onSuccess {
                    Log.i(TAG, "STT initialized successfully")
                    _uiState.update {
                        it.copy(
                            isInitializing = false,
                            isReady = true,
                            status = STTStatus.READY
                        )
                    }
                    _events.emit(STTEvent.InitializationSuccess)
                }

                result.onFailure { error ->
                    Log.e(TAG, "STT initialization failed", error)
                    _uiState.update {
                        it.copy(
                            isInitializing = false,
                            isReady = false,
                            error = error.message ?: "Unknown error",
                            status = STTStatus.ERROR
                        )
                    }
                    _events.emit(STTEvent.InitializationFailed(error.message ?: "Unknown error"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during initialization", e)
                _uiState.update {
                    it.copy(
                        isInitializing = false,
                        error = e.message,
                        status = STTStatus.ERROR
                    )
                }
                _events.emit(STTEvent.InitializationFailed(e.message ?: "Unexpected error"))
            }
        }
    }

    // Transcribe audio from file
    fun transcribeFile(filePath: String, sampleRate: Int = DEFAULT_SAMPLE_RATE) {
        if (_uiState.value.isTranscribing) {
            Log.w(TAG, "Already transcribing")
            return
        }

        if (!_uiState.value.isReady) {
            _events.tryEmit(STTEvent.Error("STT not initialized"))
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isTranscribing = true,
                    currentTranscription = "",
                    error = null
                )
            }

            try {
                val result = AudioManager.transcribeFile(filePath, sampleRate)

                result.onSuccess { text ->
                    val cleanText = text.trim()

                    _uiState.update {
                        it.copy(
                            isTranscribing = false,
                            currentTranscription = cleanText,
                            lastTranscriptionTime = System.currentTimeMillis()
                        )
                    }

                    addToHistory(
                        text = cleanText,
                        source = "File: ${File(filePath).name}",
                        duration = 0
                    )

                    if (cleanText.isEmpty()) {
                        _events.emit(STTEvent.NoSpeechDetected)
                    } else {
                        _events.emit(STTEvent.TranscriptionSuccess(cleanText))
                    }
                }

                result.onFailure { error ->
                    Log.e(TAG, "Transcription failed", error)
                    _uiState.update {
                        it.copy(
                            isTranscribing = false,
                            error = error.message
                        )
                    }
                    _events.emit(STTEvent.TranscriptionFailed(error.message ?: "Unknown error"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during transcription", e)
                _uiState.update {
                    it.copy(
                        isTranscribing = false,
                        error = e.message
                    )
                }
                _events.emit(STTEvent.Error(e.message ?: "Unexpected error"))
            }
        }
    }

    // Transcribe audio samples directly
    fun transcribeSamples(samples: FloatArray, sampleRate: Int = DEFAULT_SAMPLE_RATE) {
        if (_uiState.value.isTranscribing) {
            Log.w(TAG, "Already transcribing")
            return
        }

        if (!_uiState.value.isReady) {
            _events.tryEmit(STTEvent.Error("STT not initialized"))
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isTranscribing = true, error = null) }

            try {
                val result = AudioManager.transcribeSamples(samples, sampleRate)

                result.onSuccess { text ->
                    val cleanText = text.trim()

                    _uiState.update {
                        it.copy(
                            isTranscribing = false,
                            currentTranscription = cleanText,
                            lastTranscriptionTime = System.currentTimeMillis()
                        )
                    }

                    addToHistory(
                        text = cleanText,
                        source = "Recording",
                        duration = samples.size / sampleRate
                    )

                    if (cleanText.isEmpty()) {
                        _events.emit(STTEvent.NoSpeechDetected)
                    } else {
                        _events.emit(STTEvent.TranscriptionSuccess(cleanText))
                    }
                }

                result.onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isTranscribing = false,
                            error = error.message
                        )
                    }
                    _events.emit(STTEvent.TranscriptionFailed(error.message ?: "Unknown error"))
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isTranscribing = false,
                        error = e.message
                    )
                }
                _events.emit(STTEvent.Error(e.message ?: "Unexpected error"))
            }
        }
    }

    // Get available STT models
    suspend fun getAvailableModels(): List<ModelData> {
        return AudioManager.getSttModels()
    }

    // Switch to different model
    fun switchModel(modelData: ModelData) {
        viewModelScope.launch {
            AudioManager.unloadSttModel()
            initialize(modelData)
        }
    }

    // Clear current transcription
    fun clearTranscription() {
        _uiState.update {
            it.copy(
                currentTranscription = "",
                error = null
            )
        }
    }

    // Clear error
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // Add transcription to history
    private fun addToHistory(text: String, source: String, duration: Int) {
        if (text.isBlank()) return

        val item = TranscriptionItem(
            id = System.currentTimeMillis(),
            text = text,
            timestamp = System.currentTimeMillis(),
            source = source,
            durationSeconds = duration
        )

        _transcriptionHistory.update { history ->
            (listOf(item) + history).take(50)
        }
    }

    // Clear history
    fun clearHistory() {
        _transcriptionHistory.value = emptyList()
    }

    // Delete history item
    fun deleteHistoryItem(id: Long) {
        _transcriptionHistory.update { history ->
            history.filter { it.id != id }
        }
    }

    // Release resources
    fun release() {
        viewModelScope.launch {
            AudioManager.unloadSttModel()
            _uiState.update {
                it.copy(
                    isReady = false,
                    status = STTStatus.UNINITIALIZED
                )
            }
        }
    }

    // Get statistics
    fun getStatistics(): STTStatistics {
        val history = _transcriptionHistory.value
        return STTStatistics(
            totalTranscriptions = history.size,
            totalWords = history.sumOf { it.text.split(" ").size },
            averageLength = if (history.isNotEmpty()) {
                history.sumOf { it.text.length } / history.size
            } else 0,
            totalDuration = history.sumOf { it.durationSeconds }
        )
    }
}

// UI State
data class STTUiState(
    val isInitializing: Boolean = false,
    val isReady: Boolean = false,
    val isTranscribing: Boolean = false,
    val currentTranscription: String = "",
    val error: String? = null,
    val status: STTStatus = STTStatus.UNINITIALIZED,
    val activeStreams: Int = 0,
    val lastTranscriptionTime: Long = 0
)

// Transcription history item
data class TranscriptionItem(
    val id: Long,
    val text: String,
    val timestamp: Long,
    val source: String,
    val durationSeconds: Int
)

// Statistics
data class STTStatistics(
    val totalTranscriptions: Int,
    val totalWords: Int,
    val averageLength: Int,
    val totalDuration: Int
)

// Status enum
enum class STTStatus {
    UNINITIALIZED,
    INITIALIZING,
    READY,
    PROCESSING,
    ERROR
}

// Events
sealed class STTEvent {
    object InitializationSuccess : STTEvent()
    data class InitializationFailed(val message: String) : STTEvent()
    data class TranscriptionSuccess(val text: String) : STTEvent()
    data class TranscriptionFailed(val message: String) : STTEvent()
    object NoSpeechDetected : STTEvent()
    data class Error(val message: String) : STTEvent()
}