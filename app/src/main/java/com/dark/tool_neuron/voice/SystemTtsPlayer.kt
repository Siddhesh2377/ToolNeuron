package com.dark.tool_neuron.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemTtsPlayer @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private var engine: TextToSpeech? = null
    private var ready: CompletableDeferred<Boolean>? = null

    private val _speakingId = MutableStateFlow<String?>(null)
    val speakingId: StateFlow<String?> = _speakingId.asStateFlow()

    suspend fun speak(messageId: String, text: String): Boolean {
        if (!ensureReady()) return false
        val clean = sanitize(text)
        if (clean.isBlank()) return false
        val tts = engine ?: return false
        _speakingId.value = messageId
        return withContext(Dispatchers.Main) {
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, messageId)
            }
            val rc = tts.speak(clean.take(MAX_TTS_CHARS), TextToSpeech.QUEUE_FLUSH, params, messageId)
            rc == TextToSpeech.SUCCESS
        }
    }

    fun stop() {
        _speakingId.value = null
        runCatching { engine?.stop() }
    }

    fun release() {
        stop()
        runCatching { engine?.shutdown() }
        engine = null
        ready = null
    }

    private suspend fun ensureReady(): Boolean {
        ready?.let { return it.await() }
        val created = CompletableDeferred<Boolean>()
        ready = created
        withContext(Dispatchers.Main) {
            engine = TextToSpeech(context.applicationContext) { status ->
                val ok = status == TextToSpeech.SUCCESS
                if (ok) {
                    runCatching { engine?.language = Locale.getDefault() }
                    engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) = Unit
                        override fun onDone(utteranceId: String?) {
                            if (_speakingId.value == utteranceId) _speakingId.value = null
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            if (_speakingId.value == utteranceId) _speakingId.value = null
                        }
                        override fun onError(utteranceId: String?, errorCode: Int) {
                            if (_speakingId.value == utteranceId) _speakingId.value = null
                        }
                    })
                } else {
                    Log.w(TAG, "Android TextToSpeech init failed: $status")
                }
                created.complete(ok)
            }
        }
        return created.await()
    }

    private fun sanitize(text: String): String =
        text.replace(CODE_FENCE, " ")
            .replace(INLINE_CODE, " ")
            .replace(LINK) { it.groupValues[1] }
            .replace(HEADER, "")
            .replace(EMPHASIS, "")
            .replace(EMOJI, "")
            .replace(WHITESPACE, " ")
            .trim()

    private companion object {
        const val TAG = "SystemTtsPlayer"
        const val MAX_TTS_CHARS = 4000
        val CODE_FENCE = Regex("```[\\s\\S]*?```")
        val INLINE_CODE = Regex("`[^`]*`")
        val LINK = Regex("\\[([^]]+)]\\([^)]+\\)")
        val HEADER = Regex("(?m)^#+\\s*")
        val EMPHASIS = Regex("[*_]{1,3}")
        val EMOJI = Regex("[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{FE0F}\\x{200D}]")
        val WHITESPACE = Regex("\\s+")
    }
}
