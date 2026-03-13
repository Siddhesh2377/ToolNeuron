package com.dark.tool_neuron.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.io.IOException

data class RecordedAudioClip(
    val file: File,
    val durationMillis: Long
)

class ChatAudioRecorder(
    private val appContext: Context
) {
    private var mediaRecorder: MediaRecorder? = null
    private var activeOutputFile: File? = null
    private var recordingStartedAtMs: Long? = null

    @Throws(IOException::class, IllegalStateException::class)
    fun startRecording() {
        check(mediaRecorder == null) { "A microphone recording is already in progress" }

        val outputDirectory = File(appContext.cacheDir, CACHE_DIRECTORY_NAME)
        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            throw IOException("Failed to prepare microphone cache directory")
        }

        val outputFile = File.createTempFile("chat-mic-", OUTPUT_EXTENSION, outputDirectory)
        val recorder = createMediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(DEFAULT_AUDIO_BITRATE)
            setAudioSamplingRate(DEFAULT_AUDIO_SAMPLE_RATE)
            setOutputFile(outputFile.absolutePath)
        }

        try {
            recorder.prepare()
            recorder.start()
        } catch (e: IOException) {
            recorder.release()
            deleteClip(outputFile)
            throw IOException("Unable to prepare microphone recording", e)
        } catch (e: RuntimeException) {
            recorder.release()
            deleteClip(outputFile)
            throw IllegalStateException("Unable to start microphone recording", e)
        }

        mediaRecorder = recorder
        activeOutputFile = outputFile
        recordingStartedAtMs = SystemClock.elapsedRealtime()
    }

    @Throws(IllegalStateException::class)
    fun stopRecording(): RecordedAudioClip {
        val recorder = mediaRecorder ?: throw IllegalStateException("No microphone recording is in progress")
        val outputFile = activeOutputFile
            ?: throw IllegalStateException("No microphone recording file is available")
        val startedAtMs = recordingStartedAtMs
            ?: throw IllegalStateException("Microphone recording start time is unavailable")

        try {
            recorder.stop()
        } catch (e: RuntimeException) {
            releaseRecorder(resetFirst = false)
            deleteClip(outputFile)
            throw IllegalStateException(
                "Microphone recording could not be finalized. Try recording a little longer.",
                e
            )
        }

        releaseRecorder(resetFirst = true)

        return RecordedAudioClip(
            file = outputFile,
            durationMillis = (SystemClock.elapsedRealtime() - startedAtMs).coerceAtLeast(0L)
        )
    }

    fun cancelRecording() {
        val recorder = mediaRecorder ?: return
        val outputFile = activeOutputFile

        try {
            recorder.stop()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Discarding incomplete microphone recording", e)
        } finally {
            releaseRecorder(resetFirst = true)
        }

        outputFile?.let(::deleteClip)
    }

    fun deleteClip(file: File) {
        if (file.exists() && !file.delete()) {
            Log.w(TAG, "Failed to delete temporary audio clip: ${file.absolutePath}")
        }
    }

    fun release() {
        if (mediaRecorder != null) {
            cancelRecording()
        }
    }

    @Suppress("DEPRECATION")
    private fun createMediaRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(appContext)
        } else {
            MediaRecorder()
        }
    }

    private fun releaseRecorder(resetFirst: Boolean) {
        val recorder = mediaRecorder
        if (recorder != null) {
            if (resetFirst) {
                try {
                    recorder.reset()
                } catch (e: RuntimeException) {
                    Log.w(TAG, "Failed to reset MediaRecorder before release", e)
                }
            }
            try {
                recorder.release()
            } catch (e: RuntimeException) {
                Log.w(TAG, "Failed to release MediaRecorder", e)
            }
        }

        mediaRecorder = null
        activeOutputFile = null
        recordingStartedAtMs = null
    }

    companion object {
        private const val TAG = "ChatAudioRecorder"
        private const val CACHE_DIRECTORY_NAME = "audio-recordings"
        private const val OUTPUT_EXTENSION = ".m4a"
        private const val DEFAULT_AUDIO_BITRATE = 128_000
        private const val DEFAULT_AUDIO_SAMPLE_RATE = 44_100
    }
}
