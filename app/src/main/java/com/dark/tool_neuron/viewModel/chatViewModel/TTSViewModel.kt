package com.dark.tool_neuron.viewModel.chatViewModel

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.ai_module.workers.AudioManager
import com.dark.ai_module.workers.ModelManager
import com.dark.tool_neuron.BuildConfig
import com.dark.tool_neuron.data.UserPrefs
import com.dark.tool_neuron.userdata.ntds.neuron_tree.NodeData
import com.dark.tool_neuron.userdata.ntds.neuron_tree.NodeType
import com.dark.tool_neuron.userdata.ntds.neuron_tree.NeuronNode
import com.dark.tool_neuron.userdata.saveTree
import com.dark.tool_neuron.userdata.readBrainFile
import com.dark.tool_neuron.userdata.ntds.getOrCreateHardwareBackedAesKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.util.UUID
import kotlin.time.TimeSource

class TTSViewModel() : ViewModel() {

    companion object {
        private const val TAG = "TTSViewModel"
        private const val BRAIN_ALIAS = BuildConfig.ALIAS
    }

    private var generatedAudio = FloatArray(0)
    private var totalSamples = 0
    private var currentSampleIndex = 0
    private var currentAudioMetadata: AudioMetadata? = null

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

    // Data class for audio storage
    @Serializable
    data class SavedAudioData(
        val id: String = UUID.randomUUID().toString(),
        val originalText: String,
        val normalizedText: String,
        val speakerId: Int,
        val sampleRate: Int,
        val totalSamples: Int,
        val audioDuration: Float,
        val generationTime: Float,
        val rtf: Float,
        val timestamp: Long = System.currentTimeMillis(),
        // Store audio as Base64 or chunked references
        val audioDataB64: String? = null
    )

    private data class AudioMetadata(
        val originalText: String,
        val normalizedText: String,
        val speakerId: Int,
        val sampleRate: Int,
        val startTime: TimeSource.Monotonic.ValueTimeMark
    )

    // Initialize TTS
    suspend fun initTTS() {
        val ttsModel = ModelManager.getTTSModel() ?: run {
            Log.e(TAG, "No TTS model available")
            return
        }

        try {
            AudioManager.loadTtsModel(ttsModel)
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

        AudioManager.unloadTtsModel()

        if (::track.isInitialized) {
            try {
                track.pause()
                track.flush()
                track.release()
            } catch (_: Exception) {
            }
        }

        generatedAudio = FloatArray(0)
        totalSamples = 0
        currentSampleIndex = 0
        currentAudioMetadata = null
        _audioProgress.value = 0f
        _isPlaying.value = false
        _generationStatus.value = null

        initTTS()
        initAudioTrack()
        Log.i(TAG, "TTS and AudioTrack reset complete")
    }

    // Generate and play audio
    fun generateAndPlayAudio(text: String, context: Context, autoSave: Boolean = true,    messageId: String? = null) {
        generationJob?.cancel()
        generationJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val speakerId = UserPrefs.getTTSVoiceId(context).firstOrNull() ?: 0

                if (messageId != null && autoSave) {
                    val savedAudio = findAudioByText(context, text, speakerId)
                    if (savedAudio != null && savedAudio.audioDataB64 != null) {
                        Log.d(TAG, "Found existing audio, playing from cache")
                        playSavedAudio(savedAudio)
                        return@launch
                    }
                }

                _isPlaying.value = true
                _generationStatus.value = "Generating..."

                // Normalize text for TTS
                val normalizedText = normalizeText(text)
                Log.d(TAG, "Original: $text")
                Log.d(TAG, "Normalized: $normalizedText")

                if (!::track.isInitialized) initAudioTrack()
                track.pause()
                track.flush()
                track.play()

                val startTime = TimeSource.Monotonic.markNow()
                val ttsInfo = JSONObject(AudioManager.getAudioInfo()).getJSONObject("tts")
                val sampleRate = ttsInfo.getInt("sample_rate")

                // Store metadata for later saving
                currentAudioMetadata = AudioMetadata(
                    originalText = text,
                    normalizedText = normalizedText,
                    speakerId = speakerId,
                    sampleRate = sampleRate,
                    startTime = startTime
                )

                // Reset audio buffer
                generatedAudio = FloatArray(0)
                totalSamples = 0
                currentSampleIndex = 0

                Log.d(TAG, "Generating audio from SID: $speakerId")

                AudioManager.generateTts(normalizedText, speakerId, onAudioChunk = { chunk ->
                    generatedAudio += chunk
                    totalSamples += chunk.size
                    track.write(chunk, 0, chunk.size, AudioTrack.WRITE_BLOCKING)
                    currentSampleIndex += chunk.size
                    _audioProgress.value = currentSampleIndex.toFloat() / totalSamples
                })

                val audioDuration = totalSamples / sampleRate.toFloat()
                val elapsed = startTime.elapsedNow().inWholeMilliseconds.toFloat() / 1000

                _generationStatus.value = "Elapsed: %.3f s | Audio: %.3f s | RTF: %.3f".format(
                    elapsed, audioDuration, if (audioDuration > 0) elapsed / audioDuration else 0f
                )

                // Auto-save if requested
                if (autoSave) {
                    saveCompletedAudio(context, elapsed, audioDuration)
                }

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

    // Save completed audio to NeuronTree
    private suspend fun saveCompletedAudio(
        context: Context,
        generationTime: Float,
        audioDuration: Float,
        messageId: String? = null
    ) = withContext(Dispatchers.IO) {
        val metadata = currentAudioMetadata ?: return@withContext

        try {
            val rtf = if (audioDuration > 0) generationTime / audioDuration else 0f

            val audioData = SavedAudioData(
                id = messageId ?: UUID.randomUUID().toString(), // Use message ID if provided
                originalText = metadata.originalText,
                normalizedText = metadata.normalizedText,
                speakerId = metadata.speakerId,
                sampleRate = metadata.sampleRate,
                totalSamples = totalSamples,
                audioDuration = audioDuration,
                generationTime = generationTime,
                rtf = rtf,
                audioDataB64 = if (totalSamples < 100000) {
                    encodeAudioToBase64(generatedAudio)
                } else null
            )

            saveAudioToTree(context, audioData)
            Log.i(TAG, "Audio saved successfully: ${audioData.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save audio", e)
        }
    }

    // Save audio data to NeuronTree structure
    private fun saveAudioToTree(context: Context, audioData: SavedAudioData) {
        val key = getOrCreateHardwareBackedAesKey(BRAIN_ALIAS)
        val tree = readBrainFile(key, context)

        // Get or create savedTTS node
        val savedTTSNode = tree.getNodeDirectOrNull("savedTTS")
            ?: NeuronNode("savedTTS", NodeData("", NodeType.OPERATOR)).also {
                tree.addChild(tree.root.id, it)
            }

        // Create new audio node
        val audioJson = Json.encodeToString(audioData)
        val audioNode = NeuronNode(
            id = audioData.id,
            data = NodeData(audioJson, NodeType.LEAF)
        )

        tree.addChild(savedTTSNode.id, audioNode)
        saveTree(tree, context, BRAIN_ALIAS)
    }

    // Encode audio to Base64 (for small clips)
    private fun encodeAudioToBase64(audio: FloatArray): String {
        val bytes = ByteArray(audio.size * 4)
        val buffer = java.nio.ByteBuffer.wrap(bytes)
        audio.forEach { buffer.putFloat(it) }
        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    }

    // Normalize text specifically for TTS
    fun normalizeText(raw: String): String {
        return raw
            // Replace question marks and periods with commas for natural pauses
            .replace(Regex("[.?!]+"), ",")
            // Replace exclamations with commas
            .replace(Regex("[;:]+"), ",")
            // Normalize dashes
            .replace(Regex("[\u2010\u2011\u2012\u2013\u2014\u2015]"), "-")
            // Remove all quotes/apostrophes/backticks
            .replace(Regex("""["'`“”‘’‚‛‹›«»]+"""), "")

            // Remove pipes (tables)
            .replace(Regex("\\|+"), "")
            // Remove Markdown links/images
            .replace(Regex("!\\[[^]]*]\\([^)]*\\)"), "")
            .replace(Regex("\\[[^]]*]\\([^)]*\\)"), "")
            // Remove Markdown formatting
            .replace(Regex("\\*\\*|\\*|~~|__|`"), "")
            // Remove HTML tags
            .replace(Regex("<[^>]+>"), "")
            // Remove URLs
            .replace(Regex("https?://\\S+|www\\.\\S+"), "")
            // Remove emojis/unprintable chars
            .replace(Regex("[^\\p{L}\\p{N}\\p{P}\\p{Z}\\n@#\$%&*\\-,]"), "")
            // Normalize multiple commas to single comma
            .replace(Regex(",+"), ",")
            // Normalize multiple spaces to single space
            .replace(Regex("[\\s]+"), " ")
            // Normalize multiple newlines to single newline
            .replace(Regex("(\\n\\s*){2,}"), "\n")
            // Remove leading/trailing commas and spaces
            .replace(Regex("^[,\\s]+|[,\\s]+$"), "")
            .trim()
    }

    // Retrieve saved audio from tree
    suspend fun getSavedAudio(context: Context, audioId: String): SavedAudioData? =
        withContext(Dispatchers.IO) {
            try {
                val key = getOrCreateHardwareBackedAesKey(BRAIN_ALIAS)
                val tree = readBrainFile(key, context)
                val audioNode = tree.getNodeDirectOrNull(audioId) ?: return@withContext null
                Json.decodeFromString<SavedAudioData>(audioNode.data.content)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to retrieve audio: $audioId", e)
                null
            }
        }

    // List all saved audio
    suspend fun listSavedAudio(context: Context): List<SavedAudioData> =
        withContext(Dispatchers.IO) {
            try {
                val key = getOrCreateHardwareBackedAesKey(BRAIN_ALIAS)
                val tree = readBrainFile(key, context)
                val savedTTSNode = tree.getNodeDirectOrNull("savedTTS") ?: return@withContext emptyList()

                savedTTSNode.getChildNodes().mapNotNull { node ->
                    try {
                        Json.decodeFromString<SavedAudioData>(node.data.content)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse audio node: ${node.id}", e)
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to list saved audio", e)
                emptyList()
            }
        }

    fun stopPlayback() {
        Log.i(TAG, "Stopping playback")
        generationJob?.cancel()
        AudioManager.stopTts()

        if (::track.isInitialized) {
            try {
                track.pause()
                track.flush()
            } catch (_: Exception) {
            }
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
        val ttsInfo = JSONObject(AudioManager.getAudioInfo()).getJSONObject("tts")
        val sampleRate = ttsInfo.getInt("sample_rate")

        val bufLength = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT
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
            android.media.AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        Log.i(TAG, "AudioTrack initialized with sampleRate=$sampleRate, buffer=$bufLength")
    }

    override fun onCleared() {
        super.onCleared()
        generationJob?.cancel()
        AudioManager.unloadTtsModel()
        if (::track.isInitialized) track.release()
        Log.i(TAG, "ViewModel cleared, resources released")
    }

    // Add this function to TTSViewModel
    suspend fun playAgainFromMessage(
        context: Context,
        messageId: String,
        currentText: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentSpeakerId = UserPrefs.getTTSVoiceId(context).firstOrNull() ?: 0

            // Try to find saved audio for this message
            val savedAudio = findAudioByText(context, currentText, currentSpeakerId)

            if (savedAudio != null && savedAudio.audioDataB64 != null) {
                // Play saved audio
                playSavedAudio(savedAudio)
                true
            } else {
                // No saved audio or speaker changed, regenerate
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to replay audio", e)
            false
        }
    }

    // Find audio by text and speaker ID
    private suspend fun findAudioByText(
        context: Context,
        text: String,
        speakerId: Int
    ): SavedAudioData? = withContext(Dispatchers.IO) {
        try {
            val normalizedSearch = normalizeText(text)
            val allAudio = listSavedAudio(context)

            // Find matching audio with same speaker ID
            allAudio.firstOrNull { audio ->
                audio.normalizedText == normalizedSearch &&
                        audio.speakerId == speakerId
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to find audio", e)
            null
        }
    }

    // Play saved audio from Base64
    private suspend fun playSavedAudio(audioData: SavedAudioData) = withContext(Dispatchers.IO) {
        try {
            if (audioData.audioDataB64 == null) {
                Log.e(TAG, "No audio data available")
                return@withContext
            }

            _isPlaying.value = true
            _generationStatus.value = "Playing saved audio..."

            // Decode Base64 audio
            val decodedAudio = decodeAudioFromBase64(audioData.audioDataB64)

            // Initialize track if needed
            if (!::track.isInitialized) initAudioTrack()

            track.pause()
            track.flush()
            track.play()

            // Set up for playback
            generatedAudio = decodedAudio
            totalSamples = decodedAudio.size
            currentSampleIndex = 0

            currentAudioMetadata = AudioMetadata(
                originalText = audioData.originalText,
                normalizedText = audioData.normalizedText,
                speakerId = audioData.speakerId,
                sampleRate = audioData.sampleRate,
                startTime = TimeSource.Monotonic.markNow()
            )

            // Play in chunks
            val chunkSize = 2048
            while (currentSampleIndex < totalSamples && _isPlaying.value) {
                val end = (currentSampleIndex + chunkSize).coerceAtMost(totalSamples)
                val chunk = generatedAudio.copyOfRange(currentSampleIndex, end)
                track.write(chunk, 0, chunk.size, AudioTrack.WRITE_BLOCKING)
                currentSampleIndex += chunk.size
                _audioProgress.value = currentSampleIndex.toFloat() / totalSamples
            }

            _generationStatus.value = "Duration: %.2fs | RTF: %.3f".format(
                audioData.audioDuration,
                audioData.rtf
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play saved audio", e)
            _generationStatus.value = "Error: ${e.message}"
        } finally {
            _isPlaying.value = false
        }
    }

    // Decode Base64 audio
    private fun decodeAudioFromBase64(base64: String): FloatArray {
        val bytes = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
        val buffer = java.nio.ByteBuffer.wrap(bytes)
        val floatArray = FloatArray(bytes.size / 4)

        for (i in floatArray.indices) {
            floatArray[i] = buffer.getFloat()
        }

        return floatArray
    }
}