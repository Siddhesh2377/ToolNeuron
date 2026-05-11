package com.dark.tool_neuron.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.dark.tool_neuron.service.inference.InferenceClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsPlayer @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var playbackJob: Job? = null

    @Volatile private var audioTrack: AudioTrack? = null
    @Volatile private var currentSampleRate: Int = 0

    private val _speakingId = MutableStateFlow<String?>(null)
    val speakingId: StateFlow<String?> = _speakingId.asStateFlow()

    fun speak(messageId: String, text: String) {
        stop()
        val cleaned = sanitize(text)
        if (cleaned.isBlank()) return
        _speakingId.value = messageId
        playbackJob = scope.launch {
            try {
                val chunks = splitIntoSentences(cleaned)
                for (chunk in chunks) {
                    if (!isActive(messageId)) break
                    val samples = InferenceClient.synthesize(chunk) ?: continue
                    if (samples.isEmpty()) continue
                    val rate = InferenceClient.getTtsSampleRate().takeIf { it > 0 } ?: DEFAULT_RATE
                    ensureTrack(rate)
                    val track = audioTrack ?: break
                    if (track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()
                    var offset = 0
                    while (offset < samples.size && isActive(messageId)) {
                        val remaining = samples.size - offset
                        val written = track.write(samples, offset, remaining, AudioTrack.WRITE_BLOCKING)
                        if (written <= 0) break
                        offset += written
                    }
                }
                drainAndStopTrack()
            } catch (t: Throwable) {
                Log.e(TAG, "TTS playback failed", t)
            } finally {
                if (_speakingId.value == messageId) _speakingId.value = null
            }
        }
    }

    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        _speakingId.value = null
        releaseTrack()
    }

    fun release() {
        stop()
        scope.cancel()
    }

    private fun isActive(messageId: String): Boolean = _speakingId.value == messageId

    private fun ensureTrack(sampleRate: Int) {
        val existing = audioTrack
        if (existing != null && currentSampleRate == sampleRate) return
        releaseTrack()
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        ).coerceAtLeast(4096)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .build()
            )
            .setBufferSizeInBytes(minBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        @Suppress("DEPRECATION")
        track.setStereoVolume(1f, 1f).also { _ -> }
        audioTrack = track
        currentSampleRate = sampleRate
    }

    private fun drainAndStopTrack() {
        val track = audioTrack ?: return
        try {
            track.stop()
        } catch (_: Throwable) {}
    }

    private fun releaseTrack() {
        val track = audioTrack ?: return
        audioTrack = null
        currentSampleRate = 0
        try { track.pause() } catch (_: Throwable) {}
        try { track.flush() } catch (_: Throwable) {}
        try { track.release() } catch (_: Throwable) {}
    }

    private fun sanitize(text: String): String {
        val noCode = text.replace(CODE_FENCE, " ")
        val noInlineCode = noCode.replace(INLINE_CODE, " ")
        val noEmphasis = noInlineCode.replace(EMPHASIS, "")
        val noLinks = noEmphasis.replace(LINK) { it.groupValues[1] }
        val noHeaders = noLinks.replace(HEADER, "")
        return noHeaders.replace(WHITESPACE, " ").trim()
    }

    private fun splitIntoSentences(text: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        val softLimit = 180
        for (ch in text) {
            current.append(ch)
            val atBreak = ch == '.' || ch == '!' || ch == '?' || ch == '\n' || ch == '…' || ch == ';'
            val tooLong = current.length >= softLimit && (ch == ',' || ch == ' ')
            if ((atBreak && current.length >= 20) || tooLong) {
                val piece = current.toString().trim()
                if (piece.isNotEmpty()) out.add(piece)
                current.clear()
            }
        }
        val tail = current.toString().trim()
        if (tail.isNotEmpty()) out.add(tail)
        return out
    }

    companion object {
        private const val TAG = "TtsPlayer"
        private const val DEFAULT_RATE = 22050
        private val CODE_FENCE = Regex("```[\\s\\S]*?```")
        private val INLINE_CODE = Regex("`[^`]*`")
        private val EMPHASIS = Regex("[*_]{1,3}")
        private val LINK = Regex("\\[([^]]+)]\\([^)]+\\)")
        private val HEADER = Regex("(?m)^#+\\s*")
        private val WHITESPACE = Regex("\\s+")
    }
}
