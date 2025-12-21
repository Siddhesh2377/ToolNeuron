package com.mp.ai_engine.env

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "system_env")

object SystemEnv {

    private val OPENROUTER_API_KEY = stringPreferencesKey("openrouter_api_key")

    suspend fun setOpenRouterApiKey(context: Context, key: String) {
        context.dataStore.edit { it[OPENROUTER_API_KEY] = key }
    }

    fun getOpenRouterApiKey(context: Context): Flow<String> =
        context.dataStore.data.map { it[OPENROUTER_API_KEY] ?: "" }

}