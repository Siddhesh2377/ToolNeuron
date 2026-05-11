package com.dark.tool_neuron.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
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
class SttRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recordJob: Job? = null
    @Volatile private var recorder: AudioRecord? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val buffer = ArrayList<Float>(SAMPLE_RATE * 30)

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED

    fun start(): Boolean {
        if (_isRecording.value) return true
        if (!hasPermission()) return false
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        ).coerceAtLeast(4096)
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_FLOAT,
                minBuf * 2,
            )
        } catch (t: SecurityException) {
            Log.e(TAG, "AudioRecord create failed (permission)", t)
            return false
        } catch (t: Throwable) {
            Log.e(TAG, "AudioRecord create failed", t)
            return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            try { rec.release() } catch (_: Throwable) {}
            return false
        }
        recorder = rec
        synchronized(buffer) { buffer.clear() }
        _isRecording.value = true
        recordJob = scope.launch {
            val chunk = FloatArray(CHUNK_SAMPLES)
            try {
                rec.startRecording()
                while (_isRecording.value) {
                    val n = rec.read(chunk, 0, chunk.size, AudioRecord.READ_BLOCKING)
                    if (n <= 0) continue
                    var peak = 0f
                    for (i in 0 until n) {
                        val v = chunk[i]
                        if (v > peak) peak = v else if (-v > peak) peak = -v
                    }
                    _amplitude.value = peak
                    synchronized(buffer) {
                        for (i in 0 until n) buffer.add(chunk[i])
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "AudioRecord loop failed", t)
            }
        }
        return true
    }

    fun stop(): FloatArray {
        if (!_isRecording.value && recorder == null) return FloatArray(0)
        _isRecording.value = false
        recordJob?.cancel()
        recordJob = null
        val rec = recorder
        recorder = null
        if (rec != null) {
            try { rec.stop() } catch (_: Throwable) {}
            try { rec.release() } catch (_: Throwable) {}
        }
        val snapshot = synchronized(buffer) {
            val arr = FloatArray(buffer.size)
            for (i in buffer.indices) arr[i] = buffer[i]
            buffer.clear()
            arr
        }
        _amplitude.value = 0f
        return snapshot
    }

    fun cancel() {
        if (!_isRecording.value && recorder == null) return
        _isRecording.value = false
        recordJob?.cancel()
        recordJob = null
        val rec = recorder
        recorder = null
        if (rec != null) {
            try { rec.stop() } catch (_: Throwable) {}
            try { rec.release() } catch (_: Throwable) {}
        }
        synchronized(buffer) { buffer.clear() }
        _amplitude.value = 0f
    }

    fun release() {
        cancel()
        scope.cancel()
    }

    companion object {
        const val SAMPLE_RATE = 16000
        private const val CHUNK_SAMPLES = 1024
        private const val TAG = "SttRecorder"
    }
}
