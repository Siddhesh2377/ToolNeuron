package com.dark.neuroverse.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

object UserPrefs {
    private val MODEL_P_PARAMS_KEY = floatPreferencesKey("model_p_params")
    private val MODEL_E_PARAMS_KEY = floatPreferencesKey("model_e_params")

    private val OPENROUTER_API_KEY = stringPreferencesKey("openrouter_api_key")
    private val OPENROUTER_BASE_URL = stringPreferencesKey("openrouter_base_url")

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


    fun getModelPParams(context: Context): Flow<Float?> {
        return context.dataStore.data.map { it[MODEL_P_PARAMS_KEY] }
    }

    suspend fun setModelPParams(context: Context, params: Float) {
        context.dataStore.edit { it[MODEL_P_PARAMS_KEY] = params }
    }

    fun getModelEParams(context: Context): Flow<Float?> {
        return context.dataStore.data.map { it[MODEL_E_PARAMS_KEY] }
    }

    suspend fun setModelEParams(context: Context, params: Float) {
        context.dataStore.edit { it[MODEL_E_PARAMS_KEY] = params }
    }
}