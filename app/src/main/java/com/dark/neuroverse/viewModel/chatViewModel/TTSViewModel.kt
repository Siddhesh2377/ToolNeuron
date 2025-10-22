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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import java.io.File
import kotlin.time.TimeSource

class TTSViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return TTSViewModel(
            context.applicationContext,
            TtsServiceFactory.createTtsService()
        ) as T
    }
}

class TTSViewModel(
    context: Context,
    private val ttsService: ITtsService
) : ViewModel() {

    companion object {
        private const val TAG = "TTSViewModel"
    }

    private val context: Context = context.applicationContext
    private var generatedAudio = FloatArray(0)
    private var totalSamples = 0
    private var currentSampleIndex = 0

    private lateinit var track: AudioTrack
    private var generationJob: Job? = null
    private var progressJob: Job? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _generationStatus = MutableStateFlow<String?>(null)
    val generationStatus = _generationStatus.asStateFlow()

    private val _audioProgress = MutableStateFlow(0f)
    val audioProgress = _audioProgress.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized = _isInitialized.asStateFlow()

    // Initialize TTS
    suspend fun initTTS() {
        val ttsModel = ModelManager.getTTSModels() ?: run {
            Log.e(TAG, "No TTS model available")
            return
        }

        val config = TtsConfig(
            modelDir = "${File(ttsModel.modelPath)}/kokoro-en-v0_19",
            modelName = "model.onnx",
            voices = "voices.bin",
            dataDir = "${File(ttsModel.modelPath)}/kokoro-en-v0_19/espeak-ng-data",
            lang = "eng"
        )

        try {
            ttsService.initialize(config)
            _isInitialized.value = true
            Log.i(TAG, "TTS initialized successfully")
        } catch (e: Exception) {
            _isInitialized.value = false
            Log.e(TAG, "Failed to initialize TTS", e)
            throw e
        }
    }

    // Reset everything safely
    suspend fun resetTTS() {
        Log.i(TAG, "Resetting TTS and AudioTrack")
        generationJob?.cancelAndJoin()
        progressJob?.cancelAndJoin()

        ttsService.release()

        if (::track.isInitialized) {
            try {
                track.pause()
                track.flush()
                track.release()
            } catch (_: Exception) {}
        }

        generatedAudio = FloatArray(0)
        totalSamples = 0
        currentSampleIndex = 0
        _audioProgress.value = 0f
        _isPlaying.value = false
        _generationStatus.value = null

        initTTS()
        initAudioTrack()
        Log.i(TAG, "TTS and AudioTrack reset complete")
    }

    // Generate and play audio
    fun generateAndPlayAudio(text: String) {
        generationJob?.cancel()
        generationJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val speakerId = UserPrefs.getTTSVoiceId(context).firstOrNull() ?: 0
                _isPlaying.value = true
                _generationStatus.value = "Generating..."

                if (!::track.isInitialized) initAudioTrack()
                track.pause()
                track.flush()
                track.play()

                val startTime = TimeSource.Monotonic.markNow()

                ttsService.generateAudioStream(text, speakerId)
                    .catch { e ->
                        if (e !is CancellationException) {
                            _generationStatus.value = "Error: ${e.message}"
                            Log.e(TAG, "Audio stream error", e)
                        }
                    }
                    .collect { chunk ->
                        generatedAudio += chunk.samples
                        totalSamples += chunk.samples.size
                        track.write(chunk.samples, 0, chunk.samples.size, AudioTrack.WRITE_BLOCKING)
                        currentSampleIndex += chunk.samples.size
                        _audioProgress.value = currentSampleIndex.toFloat() / totalSamples
                    }

                val ttsInfo = ttsService.getTtsInfo()
                val audioDuration = ttsInfo?.let { totalSamples / it.sampleRate.toFloat() } ?: 0f
                val elapsed = startTime.elapsedNow().inWholeMilliseconds.toFloat() / 1000

                _generationStatus.value = "Elapsed: %.3f s | Audio: %.3f s | RTF: %.3f"
                    .format(elapsed, audioDuration, if (audioDuration > 0) elapsed / audioDuration else 0f)

            } catch (e: Exception) {
                if (e !is CancellationException) {
                    _generationStatus.value = "Error: ${e.message}"
                    Log.e(TAG, "Generation failed", e)
                }
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
            try {
                track.pause()
                track.flush()
            } catch (_: Exception) {}
        }

        _isPlaying.value = false
        _generationStatus.value = "Stopped"
    }

    fun pausePlayback() {
        if (::track.isInitialized && _isPlaying.value) {
            track.pause()
            _isPlaying.value = false
            _generationStatus.value = "Paused"
            progressJob?.cancel()
        }
    }

    fun resumePlayback() {
        if (::track.isInitialized && !_isPlaying.value) {
            track.play()
            _isPlaying.value = true
            _generationStatus.value = "Playing Audio"
            startProgressTicker()
        }
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch(Dispatchers.IO) {
            while (_isPlaying.value && currentSampleIndex < totalSamples) {
                _audioProgress.value = currentSampleIndex.toFloat() / totalSamples
                delay(50)
            }
        }
    }

    suspend fun seekTo(position: Float) = withContext(Dispatchers.IO) {
        if (generatedAudio.isEmpty() || !::track.isInitialized) return@withContext

        val targetIndex = (position * totalSamples).toInt().coerceIn(0, totalSamples - 1)
        currentSampleIndex = targetIndex

        track.pause()
        track.flush()
        track.play()

        _isPlaying.value = true
        _generationStatus.value = "Playing from ${(position * 100).toInt()}%"

        val chunkSize = 2048
        progressJob?.cancel()
        progressJob = viewModelScope.launch(Dispatchers.IO) {
            while (currentSampleIndex < totalSamples && _isPlaying.value) {
                val end = (currentSampleIndex + chunkSize).coerceAtMost(totalSamples)
                val chunk = generatedAudio.copyOfRange(currentSampleIndex, end)
                track.write(chunk, 0, chunk.size, AudioTrack.WRITE_BLOCKING)
                currentSampleIndex += chunk.size
                _audioProgress.value = currentSampleIndex.toFloat() / totalSamples
            }
            _isPlaying.value = false
        }
    }

    private fun initAudioTrack() {
        val ttsInfo = ttsService.getTtsInfo() ?: throw IllegalStateException("TTS not initialized")
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

        track = AudioTrack(attr, format, bufLength, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE)
        Log.i(TAG, "AudioTrack initialized with sampleRate=$sampleRate, buffer=$bufLength")
    }

    override fun onCleared() {
        super.onCleared()
        generationJob?.cancel()
        ttsService.release()
        if (::track.isInitialized) track.release()
        Log.i(TAG, "ViewModel cleared, resources released")
    }

    fun normalizeText(raw: String): String {
        return raw
            // Normalize dashes
            .replace(Regex("[\u2010\u2011\u2012\u2013\u2014\u2015]"), "-")
            // Remove all kinds of quotes / apostrophes / backticks / double quotes
            .replace(Regex("[\"'`‘’“”‹›«»]+"), "")
            // Remove pipes (tables)
            .replace(Regex("\\|+"), "")
            // Remove Markdown links/images
            .replace(Regex("!\\[[^\\]]*]\\([^)]*\\)"), "") // ![alt](url)
            .replace(Regex("\\[[^\\]]*]\\([^)]*\\)"), "")  // [text](url)
            // Remove Markdown formatting
            .replace(Regex("\\*\\*|\\*|~~|__|`"), "")
            // Remove HTML tags
            .replace(Regex("<[^>]+>"), "")
            // Remove URLs
            .replace(Regex("https?://\\S+|www\\.\\S+"), "")
            // Remove emojis / hidden/unprintable chars
            .replace(Regex("[^\\p{L}\\p{N}\\p{P}\\p{Z}\\n@#\$%&*\\-]"), "")
            // Normalize multiple spaces/tabs to a single space
            .replace(Regex("[\\s]+"), " ")
            // Normalize multiple newlines to exactly 2
            .replace(Regex("(\\n\\s*){2,}"), "\n\n")
            .trim()
    }

}
