package com.dark.tool_neuron.tts

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.mp.ai_supertonic_tts.SupertonicTTS
import com.mp.ai_supertonic_tts.callback.TTSCallback
import com.mp.ai_supertonic_tts.models.AudioFormat
import com.mp.ai_supertonic_tts.models.Language
import com.mp.ai_supertonic_tts.models.SynthesisResult
import com.mp.ai_supertonic_tts.models.TTSConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

@SuppressLint("StaticFieldLeak")
object TTSManager {

    private const val TAG = "TTSManager"
    private const val TTS_MODEL_DIR_NAME = "supertonic-2"

    private var tts: SupertonicTTS? = null
    private var context: Context? = null

    private val _isModelLoaded = MutableStateFlow(false)
    val isModelLoaded: StateFlow<Boolean> = _isModelLoaded.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPlayingMsgId = MutableStateFlow<String?>(null)
    val currentPlayingMsgId: StateFlow<String?> = _currentPlayingMsgId.asStateFlow()

    private val _synthProgress = MutableStateFlow(0f)
    val synthProgress: StateFlow<Float> = _synthProgress.asStateFlow()

    private val _isSynthesizing = MutableStateFlow(false)
    val isSynthesizing: StateFlow<Boolean> = _isSynthesizing.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<String>>(emptyList())
    val availableVoices: StateFlow<List<String>> = _availableVoices.asStateFlow()

    fun init(appContext: Context, autoLoad: Boolean = true) {
        context = appContext.applicationContext
        tts = SupertonicTTS(appContext)
        Log.d(TAG, "TTSManager initialized")

        if (autoLoad) {
            // Auto-load model if it exists in the models directory
            val modelsDir = File(appContext.filesDir, "models/$TTS_MODEL_DIR_NAME")
            if (modelsDir.exists() && modelsDir.isDirectory) {
                val success = loadModel(modelsDir.absolutePath)
                Log.d(TAG, "Auto-loaded TTS model: $success")
            }
        }
    }

    fun loadModel(modelDir: String, useNNAPI: Boolean = false): Boolean {
        val engine = tts ?: return false
        return try {
            val success = engine.loadModel(modelDir, useNNAPI)
            _isModelLoaded.value = success
            if (success) {
                _availableVoices.value = engine.getAvailableVoices()
                Log.d(TAG, "TTS model loaded from $modelDir, voices: ${_availableVoices.value}")
            } else {
                Log.e(TAG, "Failed to load TTS model from $modelDir")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error loading TTS model", e)
            _isModelLoaded.value = false
            false
        }
    }

    fun isLoaded(): Boolean = _isModelLoaded.value

    fun getAvailableVoices(): List<String> = _availableVoices.value

    suspend fun speak(text: String, settings: TTSSettings = TTSSettings(), msgId: String? = null) {
        val engine = tts ?: return
        if (!_isModelLoaded.value) {
            Log.w(TAG, "TTS model not loaded, cannot speak")
            return
        }

        // Stop any current playback
        stopPlayback()

        _currentPlayingMsgId.value = msgId
        _isPlaying.value = true
        _isSynthesizing.value = true
        _synthProgress.value = 0f

        try {
            withContext(Dispatchers.IO) {
                val config = TTSConfig(
                    speed = settings.speed,
                    steps = settings.steps,
                    language = parseLanguage(settings.language),
                    voice = settings.voice,
                    useNNAPI = settings.useNNAPI
                )

                engine.speak(text, config, object : TTSCallback {
                    override fun onSynthesisStart(textLength: Int, chunkCount: Int) {
                        Log.d(TAG, "Synthesis started: $textLength chars, $chunkCount chunks")
                        _isSynthesizing.value = true
                    }

                    override fun onChunkProgress(chunkIndex: Int, totalChunks: Int) {
                        _synthProgress.value = if (totalChunks > 0) {
                            chunkIndex.toFloat() / totalChunks
                        } else 0f
                    }

                    override fun onAudioReady(result: SynthesisResult) {
                        _isSynthesizing.value = false
                        _synthProgress.value = 1f
                        Log.d(TAG, "Audio ready: ${result.durationMs}ms, RTF=${result.realtimeFactor}")
                    }

                    override fun onError(error: String) {
                        Log.e(TAG, "TTS error: $error")
                        _isSynthesizing.value = false
                        _isPlaying.value = false
                        _currentPlayingMsgId.value = null
                    }
                })
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during TTS speak", e)
        } finally {
            _isPlaying.value = false
            _currentPlayingMsgId.value = null
            _isSynthesizing.value = false
        }
    }

    suspend fun synthesize(text: String, settings: TTSSettings = TTSSettings()): SynthesisResult? {
        val engine = tts ?: return null
        if (!_isModelLoaded.value) return null

        return withContext(Dispatchers.IO) {
            try {
                val config = TTSConfig(
                    speed = settings.speed,
                    steps = settings.steps,
                    language = parseLanguage(settings.language),
                    voice = settings.voice,
                    useNNAPI = settings.useNNAPI
                )
                engine.synthesize(text, config)
            } catch (e: Exception) {
                Log.e(TAG, "Error during TTS synthesis", e)
                null
            }
        }
    }

    fun stopPlayback() {
        tts?.stopPlayback()
        _isPlaying.value = false
        _currentPlayingMsgId.value = null
        _isSynthesizing.value = false
        _synthProgress.value = 0f
    }

    fun pausePlayback() {
        tts?.pausePlayback()
    }

    fun resumePlayback() {
        tts?.resumePlayback()
    }

    fun setVolume(volume: Float) {
        tts?.setVolume(volume.coerceIn(0f, 1f))
    }

    fun release() {
        stopPlayback()
        tts?.release()
        tts = null
        _isModelLoaded.value = false
        _availableVoices.value = emptyList()
        Log.d(TAG, "TTSManager released")
    }

    fun getModelDirectory(): String? {
        val ctx = context ?: return null
        val dir = File(ctx.filesDir, "models/$TTS_MODEL_DIR_NAME")
        return if (dir.exists()) dir.absolutePath else null
    }

    private fun parseLanguage(tag: String): Language {
        return when (tag.lowercase()) {
            "en" -> Language.EN
            "ko" -> Language.KO
            "es" -> Language.ES
            "pt" -> Language.PT
            "fr" -> Language.FR
            else -> Language.EN
        }
    }
}
