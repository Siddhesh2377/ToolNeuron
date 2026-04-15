package com.dark.tool_neuron.tts

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.ttsDataStore: DataStore<Preferences> by preferencesDataStore(name = "tts_settings")

class TTSDataStore(private val context: Context) {

    companion object {
        private val VOICE_KEY = stringPreferencesKey("tts_voice")
        private val SPEED_KEY = floatPreferencesKey("tts_speed")
        private val STEPS_KEY = intPreferencesKey("tts_steps")
        private val LANGUAGE_KEY = stringPreferencesKey("tts_language")
        private val AUTO_SPEAK_KEY = booleanPreferencesKey("tts_auto_speak")
        private val USE_NNAPI_KEY = booleanPreferencesKey("tts_use_nnapi")
    }

    val settings: Flow<TTSSettings> = context.ttsDataStore.data.map { prefs ->
        TTSSettings(
            voice = prefs[VOICE_KEY] ?: "F1",
            speed = prefs[SPEED_KEY] ?: 1.05f,
            steps = prefs[STEPS_KEY] ?: 2,
            language = prefs[LANGUAGE_KEY] ?: "en",
            autoSpeak = prefs[AUTO_SPEAK_KEY] ?: false,
            useNNAPI = prefs[USE_NNAPI_KEY] ?: false
        )
    }

    suspend fun updateVoice(voice: String) {
        context.ttsDataStore.edit { it[VOICE_KEY] = voice }
    }

    suspend fun updateSpeed(speed: Float) {
        context.ttsDataStore.edit { it[SPEED_KEY] = speed }
    }

    suspend fun updateSteps(steps: Int) {
        context.ttsDataStore.edit { it[STEPS_KEY] = steps }
    }

    suspend fun updateLanguage(language: String) {
        context.ttsDataStore.edit { it[LANGUAGE_KEY] = language }
    }

    suspend fun updateAutoSpeak(enabled: Boolean) {
        context.ttsDataStore.edit { it[AUTO_SPEAK_KEY] = enabled }
    }

    suspend fun updateUseNNAPI(enabled: Boolean) {
        context.ttsDataStore.edit { it[USE_NNAPI_KEY] = enabled }
    }
}
