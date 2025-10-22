package com.dark.neuroverse.viewModel.chatViewModel

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dark.ai_module.workers.ModelManager
import com.dark.neuroverse.data.UserPrefs
import com.mp.ai_core.tts.ITtsService
import com.mp.ai_core.tts.TtsConfig
import com.mp.ai_core.tts.TtsServiceFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.TimeSource

class TTSViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return TTSViewModel(
            context = context.applicationContext,
            ttsService = TtsServiceFactory.createTtsService()
        ) as T
    }
}

class TTSViewModel(
    context: Context,
    private val ttsService: ITtsService // Dependency injection
) : ViewModel() {

    companion object {
        private const val TAG = "TTSViewModel"
    }

    @SuppressLint("StaticFieldLeak")
    private val context: Context = context.applicationContext

    private lateinit var track: AudioTrack
    private var generationJob: Job? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _generationStatus = MutableStateFlow<String?>(null)
    val generationStatus = _generationStatus.asStateFlow()

    private val _audioProgress = MutableStateFlow(0f)
    val audioProgress = _audioProgress.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized = _isInitialized.asStateFlow()

    suspend fun initTTS() {
        val ttsModel = ModelManager.getTTSModels() ?: run {
            Log.e(TAG, "No TTS model available")
            return
        }

        Log.d(TAG, "Loading TTS model from ${ttsModel.modelPath}")

        val config = TtsConfig(
            modelDir = "${File(ttsModel.modelPath)}/kokoro-en-v0_19",
            modelName = "model.onnx",
            voices = "voices.bin",
            dataDir = "${File(ttsModel.modelPath)}/kokoro-en-v0_19/espeak-ng-data",
            lang = "eng"
        )

        try {
            ttsService.initialize(config)
            Log.i(TAG, "TTS initialized successfully")
            _isInitialized.value = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TTS", e)
            _isInitialized.value = false
            throw e
        }
    }

    @SuppressLint("DefaultLocale")
    fun generateAndPlayAudio(text: String) {
        // Cancel any ongoing generation
        generationJob?.cancel()

        generationJob = viewModelScope.launch {
            try {
                val speakerId = UserPrefs.getTTSVoiceId(context).firstOrNull() ?: 0
                val normalizedText = normalizeText(text)

                _isPlaying.value = true
                _generationStatus.value = "Generating..."

                // Initialize audio track
                if (!::track.isInitialized) {
                    initAudioTrack()
                }

                track.pause()
                track.flush()
                track.play()

                val startTime = TimeSource.Monotonic.markNow()
                var totalSamples = 0

                // Collect and play audio chunks as they arrive
                ttsService.generateAudioStream(normalizedText, speakerId)
                    .catch { e ->
                        Log.e(TAG, "Error in audio stream", e)
                        _generationStatus.value = "Error: ${e.message}"
                    }
                    .collect { chunk ->
                        withContext(Dispatchers.IO) {
                            track.write(
                                chunk.samples,
                                0,
                                chunk.samples.size,
                                AudioTrack.WRITE_BLOCKING
                            )
                            totalSamples += chunk.samples.size
                        }
                    }

                val elapsed = startTime.elapsedNow().inWholeMilliseconds.toFloat() / 1000
                val ttsInfo = ttsService.getTtsInfo()
                val audioDuration = if (ttsInfo != null) {
                    totalSamples / ttsInfo.sampleRate.toFloat()
                } else {
                    0f
                }

                _generationStatus.value = String.format(
                    "Elapsed: %.3f s | Audio: %.3f s | RTF: %.3f",
                    elapsed,
                    audioDuration,
                    if (audioDuration > 0) elapsed / audioDuration else 0f
                )

                Log.i(TAG, "Audio generation and playback completed")

            } catch (e: Exception) {
                Log.e(TAG, "Error during generation", e)
                _generationStatus.value = "Error: ${e.message}"
            } finally {
                _isPlaying.value = false
            }
        }
    }

    fun stopPlayback() {
        Log.i(TAG, "Stopping playback")
        generationJob?.cancel()
        ttsService.stop()

        if (::track.isInitialized) {
            track.pause()
            track.flush()
        }

        _isPlaying.value = false
        _generationStatus.value = "Stopped"
    }

    private fun initAudioTrack() {
        Log.i(TAG, "Initializing AudioTrack")

        val ttsInfo = ttsService.getTtsInfo()
            ?: throw IllegalStateException("TTS not initialized")

        val sampleRate = ttsInfo.sampleRate
        val bufLength = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )

        val attr = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setSampleRate(sampleRate)
            .build()

        track = AudioTrack(
            attr,
            format,
            bufLength,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )

        Log.i(TAG, "AudioTrack initialized: sampleRate=$sampleRate, bufLength=$bufLength")
    }

    private fun normalizeText(raw: String): String {
        return raw.replace("\u2011", "-")
            .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    override fun onCleared() {
        super.onCleared()
        Log.i(TAG, "ViewModel cleared, releasing resources")
        generationJob?.cancel()
        ttsService.release()

        if (::track.isInitialized) {
            track.release()
        }
    }
}