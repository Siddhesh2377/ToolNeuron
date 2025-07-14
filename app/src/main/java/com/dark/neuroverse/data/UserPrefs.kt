package com.dark.neuroverse.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

object UserPrefs {
    private val TERMS_ACCEPTED_KEY = booleanPreferencesKey("terms_accepted")
    private val CURRENT_MODEL_KEY = stringPreferencesKey("current_model")

    fun getCurrentModel(context: Context): Flow<String?> {
        return context.dataStore.data.map { it[CURRENT_MODEL_KEY] }
    }

    suspend fun setCurrentModel(context: Context, model: String) {
        context.dataStore.edit { it[CURRENT_MODEL_KEY] = model }
    }

    fun isTermsAccepted(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { it[TERMS_ACCEPTED_KEY] == true }
    }

    suspend fun setTermsAccepted(context: Context, accepted: Boolean) {
        context.dataStore.edit { it[TERMS_ACCEPTED_KEY] = accepted }
    }
}