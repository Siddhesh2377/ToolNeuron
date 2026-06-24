package com.dark.tool_neuron.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemSttRecognizer @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private var recognizer: SpeechRecognizer? = null
    private var pendingResult: CompletableDeferred<String?>? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(): Boolean {
        if (_isRecording.value) return true
        if (!hasPermission() || !isAvailable()) return false
        pendingResult = CompletableDeferred()
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                val sr = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
                recognizer = sr
                sr.setRecognitionListener(listener())
                sr.startListening(intent())
                _isRecording.value = true
            } catch (t: Throwable) {
                _isRecording.value = false
                pendingResult?.complete(null)
            }
        }
        return true
    }

    suspend fun stopAndRecognize(): String? {
        val result = pendingResult ?: return null
        withContext(Dispatchers.Main) {
            runCatching { recognizer?.stopListening() }
        }
        return try {
            withTimeout(RECOGNITION_TIMEOUT_MS) { result.await() }
        } catch (_: TimeoutCancellationException) {
            cancel()
            null
        } finally {
            cleanup()
        }
    }

    fun cancel() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            runCatching { recognizer?.cancel() }
            cleanup()
        }
        pendingResult?.complete(null)
    }

    private fun listener(): RecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) {
            _amplitude.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() {
            _isRecording.value = false
        }
        override fun onError(error: Int) {
            pendingResult?.complete(null)
            cleanup()
        }
        override fun onResults(results: Bundle?) {
            pendingResult?.complete(firstResult(results))
            cleanup()
        }
        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun intent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    private fun cleanup() {
        _isRecording.value = false
        _amplitude.value = 0f
        runCatching { recognizer?.destroy() }
        recognizer = null
        pendingResult = null
    }

    private companion object {
        const val RECOGNITION_TIMEOUT_MS = 20_000L
    }
}
