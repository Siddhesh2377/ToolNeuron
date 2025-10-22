package com.dark.neuroverse.viewModel.chatViewModel

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.os.RemoteException
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.dark.ai_module.workers.ModelManager
import com.dark.neuroverse.data.UserPrefs
import com.mp.ai_core.tts.TtsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.TimeSource

class TTSViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return TTSViewModel(context.applicationContext) as T
    }
}

class TTSViewModel(context: Context) : ViewModel() {
    companion object {
        private const val TAG = "TTSViewModel"
        private const val OUTPUT_FILENAME = "generated.wav"
    }

    @SuppressLint("StaticFieldLeak")
    private val context: Context = context.applicationContext

    private var mediaPlayer: MediaPlayer? = null
    private lateinit var track: AudioTrack
    private var stopped: Boolean = false
    private var samplesChannel = Channel<FloatArray>()

    private var _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    suspend fun initTTS(context: Context) {
        val ttsModel = ModelManager.getTTSModels() ?: return

        Log.d(TAG, "Loading TTS model from ${ttsModel.modelPath}")
        Log.i(TAG, "Start to initialize TTS")
        val json = """
        {
          "modelDir": "${File(ttsModel.modelPath)}/kokoro-en-v0_19",
          "modelName": "model.onnx",
          "voices": "voices.bin",
          "dataDir": "${File(ttsModel.modelPath)}/kokoro-en-v0_19/espeak-ng-data",
          "lang": "eng"
        }
        """.trimIndent()

        try {
            TtsEngine.loadFromJson(context, json)
        } catch (e: RemoteException) {
            Log.e(TAG, "TTS load RPC failed", e)
        }
        Log.i(TAG, "Finish initializing TTS")
        initAudioTrack()
    }

    fun unLoadTTS() {
        TtsEngine.tts?.release()
    }

    @SuppressLint("DefaultLocale")
    suspend fun onGenerate(text: String, speakerId: Int): String = withContext(Dispatchers.IO) {
        TtsEngine.tts?.currentSid = UserPrefs.getTTSVoiceId(context).firstOrNull() ?: speakerId
        stopped = false
        _isPlaying.value = true
        val normalizedText = normalizeText(text)

        // Always re-create the channel to avoid sending to a closed one
        samplesChannel = Channel(Channel.BUFFERED)

        // Make sure track is ready
        if (!::track.isInitialized) initAudioTrack()

        track.pause()
        track.flush()
        track.play()

        // Launch playback coroutine safely
        val playbackJob = launch {
            try {
                for (samples in samplesChannel) {
                    if (stopped) break
                    track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Audio write failed", e)
            } finally {
                _isPlaying.value = false
            }
        }

        val startTime = TimeSource.Monotonic.markNow()

        val audio =
            TtsEngine.tts!!.generateWithCallback(normalizedText, callback = ::callback).also {
                _isPlaying.value = false
                Log.d(TAG, "Audio generated")
                it
            }

        playbackJob.cancelAndJoin()

        val elapsed = startTime.elapsedNow().inWholeMilliseconds.toFloat() / 1000
        val audioDuration = audio.samples.size / TtsEngine.tts!!.sampleRate().toFloat()
        val filename = "${context.filesDir.absolutePath}/$OUTPUT_FILENAME"
        audio.save(filename)

        String.format(
            "Threads: %d\nElapsed: %.3f s\nAudio: %.3f s\nRTF: %.3f",
            TtsEngine.tts!!.config.model.numThreads,
            elapsed,
            audioDuration,
            elapsed / audioDuration
        )
    }


    fun stopMediaPlayer() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    fun onClickPlay() {
        val filename = context.filesDir.absolutePath + "/${OUTPUT_FILENAME}"
        stopMediaPlayer()
        mediaPlayer = MediaPlayer.create(
            context, Uri.fromFile(File(filename))
        )
        mediaPlayer?.start()
    }

    fun onClickStop() {
        stopped = true
        track.pause()
        track.flush()
        stopMediaPlayer()
    }

    private fun callback(samples: FloatArray, process: Float): Int {
        return if (!stopped) {
            val samplesCopy = samples.copyOf()
            CoroutineScope(Dispatchers.IO).launch {
                samplesChannel.send(samplesCopy)
            }
            1
        } else {
            track.stop()
            Log.i(TAG, "Callback stopped")
            0
        }
    }

    private fun initAudioTrack() {
        Log.i(TAG, "Start to initialize AudioTrack")
        val sampleRate = TtsEngine.tts!!.sampleRate()
        val bufLength = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT
        )
        Log.i(TAG, "sampleRate: $sampleRate, buffLength: $bufLength")

        val attr = AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_MEDIA).build()

        val format = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setSampleRate(sampleRate).build()

        track = AudioTrack(
            attr, format, bufLength, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        track.play()
        Log.i(TAG, "Finish initializing AudioTrack")
    }

    private fun normalizeText(raw: String): String {
        return raw.replace("\u2011", "-")        // non-breaking hyphen → normal hyphen
            .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1") // remove bold markdown
            .replace(Regex("\\s+"), " ")   // collapse whitespace
            .trim()
    }

    override fun onCleared() {
        super.onCleared()
        unLoadTTS()
    }
}