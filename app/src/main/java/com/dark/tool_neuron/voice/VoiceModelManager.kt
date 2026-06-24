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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
    private val systemTts: SystemTtsPlayer,
    private val systemStt: SystemSttRecognizer,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ttsLock = Mutex()
    private val sttLock = Mutex()
    @Volatile private var activeRecordingBackend: VoiceBackend = VoiceBackend.OFFLINE

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val speakingId: StateFlow<String?> = combine(ttsPlayer.speakingId, systemTts.speakingId) { offline, system ->
        offline ?: system
    }.stateIn(scope, SharingStarted.Eagerly, null)
    val isRecording: StateFlow<Boolean> = combine(sttRecorder.isRecording, systemStt.isRecording) { offline, system ->
        offline || system
    }.stateIn(scope, SharingStarted.Eagerly, false)
    val recordingAmplitude: StateFlow<Float> = combine(sttRecorder.amplitude, systemStt.amplitude) { offline, system ->
        maxOf(offline, system)
    }.stateIn(scope, SharingStarted.Eagerly, 0f)

    fun clearError() { _error.value = null }

    fun hasTts(): Boolean = when (ttsBackend()) {
        VoiceBackend.OFFLINE -> findActiveTts() != null
        VoiceBackend.ANDROID_SYSTEM -> true
        VoiceBackend.AUTO -> findActiveTts() != null || true
    }

    fun hasStt(): Boolean = when (sttBackend()) {
        VoiceBackend.OFFLINE -> findActiveStt() != null
        VoiceBackend.ANDROID_SYSTEM -> systemStt.isAvailable()
        VoiceBackend.AUTO -> findActiveStt() != null || systemStt.isAvailable()
    }

    suspend fun unloadStt() {
        systemStt.cancel()
        try { InferenceClient.unloadSttModel() } catch (_: Exception) {}
    }

    suspend fun unloadTts() {
        ttsPlayer.stop()
        systemTts.stop()
        try { InferenceClient.unloadTtsModel() } catch (_: Exception) {}
    }

    fun sttPermissionGranted(): Boolean = sttRecorder.hasPermission() || systemStt.hasPermission()

    private fun ttsBackend(): VoiceBackend = VoiceBackend.fromKey(prefs.get().voiceTtsBackend)

    private fun sttBackend(): VoiceBackend = VoiceBackend.fromKey(prefs.get().voiceSttBackend)

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
        return when (ttsBackend()) {
            VoiceBackend.ANDROID_SYSTEM -> speakWithSystem(messageId, text)
            VoiceBackend.OFFLINE -> speakWithOffline(messageId, text, allowSystemFallback = false)
            VoiceBackend.AUTO -> speakWithOffline(messageId, text, allowSystemFallback = true)
        }
    }

    fun stopSpeaking() {
        ttsPlayer.stop()
        systemTts.stop()
    }

    private suspend fun speakWithOffline(
        messageId: String,
        text: String,
        allowSystemFallback: Boolean,
    ): Boolean {
        val model = findActiveTts()
        if (model == null) {
            return if (allowSystemFallback) {
                speakWithSystem(messageId, text)
            } else {
                _error.value = "No TTS model installed. Import one in Voice settings."
                false
            }
        }
        val ok = ensureTtsLoaded() ?: return if (allowSystemFallback) speakWithSystem(messageId, text) else false
        if (!ok) return if (allowSystemFallback) speakWithSystem(messageId, text) else false
        ttsPlayer.speak(messageId, text)
        return true
    }

    private suspend fun speakWithSystem(messageId: String, text: String): Boolean {
        val ok = systemTts.speak(messageId, text)
        if (!ok) _error.value = "Android system TTS is unavailable on this device"
        return ok
    }

    fun startRecording(): Boolean {
        if (!sttRecorder.hasPermission() && !systemStt.hasPermission()) {
            _error.value = "Microphone permission required"
            return false
        }
        val backend = resolveSttBackendForStart()
        if (backend != VoiceBackend.ANDROID_SYSTEM && findActiveStt() == null) {
            _error.value = "No STT model installed. Import one in Voice settings."
            return false
        }
        if (backend == VoiceBackend.ANDROID_SYSTEM && !systemStt.isAvailable()) {
            _error.value = "Android system STT is unavailable on this device"
            return false
        }
        activeRecordingBackend = backend
        val started = when (backend) {
            VoiceBackend.ANDROID_SYSTEM -> systemStt.start()
            VoiceBackend.OFFLINE, VoiceBackend.AUTO -> sttRecorder.start()
        }
        if (!started) {
            _error.value = when (backend) {
                VoiceBackend.ANDROID_SYSTEM -> "Android system STT is unavailable on this device"
                else -> "Failed to start recording"
            }
        }
        return started
    }

    private fun resolveSttBackendForStart(): VoiceBackend = when (sttBackend()) {
        VoiceBackend.ANDROID_SYSTEM -> VoiceBackend.ANDROID_SYSTEM
        VoiceBackend.OFFLINE -> {
            if (findActiveStt() == null) {
                _error.value = "No STT model installed. Import one in Voice settings."
            }
            VoiceBackend.OFFLINE
        }
        VoiceBackend.AUTO -> {
            if (findActiveStt() != null) VoiceBackend.OFFLINE else VoiceBackend.ANDROID_SYSTEM
        }
    }

    fun cancelRecording() {
        sttRecorder.cancel()
        systemStt.cancel()
    }

    suspend fun stopRecordingAndRecognize(): String? = withContext(Dispatchers.IO) {
        if (activeRecordingBackend == VoiceBackend.ANDROID_SYSTEM) {
            val text = systemStt.stopAndRecognize()
            if (text == null) {
                _error.value = "Transcription failed"
            } else if (text.isBlank()) {
                _error.value = "No speech detected"
            }
            return@withContext text
        }
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
