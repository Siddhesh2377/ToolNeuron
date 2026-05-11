package com.dark.tool_neuron.voice

import android.util.Log
import com.dark.tool_neuron.data.AppPreferences
import com.dark.tool_neuron.model.ModelInfo
import com.dark.tool_neuron.model.enums.ProviderType
import com.dark.tool_neuron.repo.ModelRepository
import com.dark.tool_neuron.service.inference.InferenceClient
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "VoiceModelManager"

@Singleton
class VoiceModelManager @Inject constructor(
    private val modelRepo: ModelRepository,
    private val prefs: Lazy<AppPreferences>,
    private val ttsPlayer: TtsPlayer,
    private val sttRecorder: SttRecorder,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ttsLock = Mutex()
    private val sttLock = Mutex()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val speakingId: StateFlow<String?> = ttsPlayer.speakingId
    val isRecording: StateFlow<Boolean> = sttRecorder.isRecording
    val recordingAmplitude: StateFlow<Float> = sttRecorder.amplitude

    fun clearError() { _error.value = null }

    fun hasTts(): Boolean = findActiveTts() != null
    fun hasStt(): Boolean = findActiveStt() != null

    suspend fun unloadStt() {
        try { InferenceClient.unloadSttModel() } catch (_: Exception) {}
    }

    suspend fun unloadTts() {
        ttsPlayer.stop()
        try { InferenceClient.unloadTtsModel() } catch (_: Exception) {}
    }

    fun sttPermissionGranted(): Boolean = sttRecorder.hasPermission()

    private fun findActiveTts(): ModelInfo? {
        val models = modelRepo.models.value.filter { it.providerType == ProviderType.TTS }
        if (models.isEmpty()) return null
        val preferred = prefs.get().activeTtsModelId
        return models.firstOrNull { it.id == preferred } ?: models.first()
    }

    private fun findActiveStt(): ModelInfo? {
        val models = modelRepo.models.value.filter { it.providerType == ProviderType.STT }
        if (models.isEmpty()) return null
        val preferred = prefs.get().activeSttModelId
        return models.firstOrNull { it.id == preferred } ?: models.first()
    }

    suspend fun speak(messageId: String, text: String): Boolean {
        val ok = ensureTtsLoaded() ?: return false
        if (!ok) return false
        ttsPlayer.speak(messageId, text)
        return true
    }

    fun stopSpeaking() { ttsPlayer.stop() }

    fun startRecording(): Boolean {
        if (!sttRecorder.hasPermission()) {
            _error.value = "Microphone permission required"
            return false
        }
        if (findActiveStt() == null) {
            _error.value = "No STT model installed. Import one in Voice settings."
            return false
        }
        val started = sttRecorder.start()
        if (!started) _error.value = "Failed to start recording"
        return started
    }

    fun cancelRecording() { sttRecorder.cancel() }

    suspend fun stopRecordingAndRecognize(): String? = withContext(Dispatchers.IO) {
        val samples = sttRecorder.stop()
        if (samples.isEmpty()) {
            _error.value = "No audio captured"
            return@withContext null
        }
        val loaded = ensureSttLoaded() ?: return@withContext null
        if (!loaded) return@withContext null
        val text = InferenceClient.recognize(samples, SttRecorder.SAMPLE_RATE)
        if (text == null) {
            _error.value = "Transcription failed"
        } else if (text.isBlank()) {
            _error.value = "No speech detected"
        }
        text
    }

    private suspend fun ensureTtsLoaded(): Boolean? = ttsLock.withLock {
        if (InferenceClient.isTtsLoaded.value) return@withLock true
        val model = findActiveTts() ?: run {
            _error.value = "No TTS model installed. Import one in Voice settings."
            return@withLock null
        }
        val configJson = modelRepo.getConfig(model.id)?.loadingParamsJson ?: "{}"
        if (configJson.isBlank() || configJson == "{}") {
            _error.value = "TTS model ${model.name} has no config. Re-import it."
            return@withLock false
        }
        try {
            val ok = InferenceClient.loadTtsModel(configJson)
            if (!ok) _error.value = "Failed to load TTS model ${model.name}"
            ok
        } catch (t: Throwable) {
            Log.e(TAG, "loadTtsModel failed", t)
            _error.value = t.message ?: "TTS load failed"
            false
        }
    }

    private suspend fun ensureSttLoaded(): Boolean? = sttLock.withLock {
        if (InferenceClient.isSttLoaded.value) return@withLock true
        val model = findActiveStt() ?: run {
            _error.value = "No STT model installed. Import one in Voice settings."
            return@withLock null
        }
        val configJson = modelRepo.getConfig(model.id)?.loadingParamsJson ?: "{}"
        if (configJson.isBlank() || configJson == "{}") {
            _error.value = "STT model ${model.name} has no config. Re-import it."
            return@withLock false
        }
        try {
            val ok = InferenceClient.loadSttModel(configJson)
            if (!ok) _error.value = "Failed to load STT model ${model.name}"
            ok
        } catch (t: Throwable) {
            Log.e(TAG, "loadSttModel failed", t)
            _error.value = t.message ?: "STT load failed"
            false
        }
    }
}
