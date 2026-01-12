package com.dark.tool_neuron.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

class TermsDataStore(private val context: Context) {
    
    companion object {
        private val TERMS_ACCEPTED_KEY = booleanPreferencesKey("terms_accepted")
        private val TERMS_ACCEPTED_TIMESTAMP = longPreferencesKey("terms_accepted_timestamp")
        private val TERMS_VERSION_KEY = longPreferencesKey("terms_version")
        
        // Update this version number whenever T&C change
        private const val CURRENT_TERMS_VERSION = 1L
    }

    /**
     * Check if user has accepted the current version of terms
     */
    val hasAcceptedTerms: Flow<Boolean> = context.dataStore.data.map { preferences ->
        val accepted = preferences[TERMS_ACCEPTED_KEY] ?: false
        val version = preferences[TERMS_VERSION_KEY] ?: 0L
        
        // User must accept if they haven't accepted OR if terms version changed
        accepted && version >= CURRENT_TERMS_VERSION
    }

    /**
     * Save terms acceptance
     */
    suspend fun acceptTerms() {
        context.dataStore.edit { preferences ->
            preferences[TERMS_ACCEPTED_KEY] = true
            preferences[TERMS_ACCEPTED_TIMESTAMP] = System.currentTimeMillis()
            preferences[TERMS_VERSION_KEY] = CURRENT_TERMS_VERSION
        }
    }

    /**
     * Reset terms acceptance (for testing or if user wants to review again)
     */
    suspend fun resetTermsAcceptance() {
        context.dataStore.edit { preferences ->
            preferences.remove(TERMS_ACCEPTED_KEY)
            preferences.remove(TERMS_ACCEPTED_TIMESTAMP)
            preferences.remove(TERMS_VERSION_KEY)
        }
    }

    /**
     * Get timestamp when terms were accepted
     */
    val termsAcceptedTimestamp: Flow<Long?> = context.dataStore.data.map { preferences ->
        preferences[TERMS_ACCEPTED_TIMESTAMP]
    }
}