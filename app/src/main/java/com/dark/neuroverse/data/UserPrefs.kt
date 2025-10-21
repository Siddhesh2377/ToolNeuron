package com.dark.neuroverse.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

object UserPrefs {

    private val OPENROUTER_API_KEY = stringPreferencesKey("openrouter_api_key")
    private val OPENROUTER_BASE_URL = stringPreferencesKey("openrouter_base_url")
    private val TTS_VOICE_ID = intPreferencesKey("tts_voice_id")

    suspend fun setOpenRouterApiKey(context: Context, key: String) {
        context.dataStore.edit { it[OPENROUTER_API_KEY] = key }
    }

    fun getOpenRouterApiKey(context: Context): Flow<String> =
        context.dataStore.data.map { it[OPENROUTER_API_KEY] ?: "" }

    suspend fun setOpenRouterBaseUrl(context: Context, url: String) {
        context.dataStore.edit { it[OPENROUTER_BASE_URL] = url }
    }

    fun getOpenRouterBaseUrl(context: Context): Flow<String> =
        context.dataStore.data.map { it[OPENROUTER_BASE_URL] ?: "https://openrouter.ai/api/v1" }

    suspend fun setTTSVoiceId(context: Context, voiceId: Int) {
        context.dataStore.edit { it[TTS_VOICE_ID] = voiceId }
    }

    fun getTTSVoiceId(context: Context): Flow<Int> =
        context.dataStore.data.map { it[TTS_VOICE_ID] ?: 0 }

}