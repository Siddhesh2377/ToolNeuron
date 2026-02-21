package com.dark.tool_neuron.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.setupDataStore: DataStore<Preferences> by preferencesDataStore(name = "setup_preferences")

class SetupDataStore(private val context: Context) {

    companion object {
        private val SETUP_SKIPPED = booleanPreferencesKey("setup_skipped")
        private val SETUP_COMPLETED = booleanPreferencesKey("setup_completed")
    }

    val isSetupSkipped: Flow<Boolean> = context.setupDataStore.data.map { prefs ->
        prefs[SETUP_SKIPPED] ?: false
    }

    val isSetupCompleted: Flow<Boolean> = context.setupDataStore.data.map { prefs ->
        prefs[SETUP_COMPLETED] ?: false
    }

    val isSetupDone: Flow<Boolean> = context.setupDataStore.data.map { prefs ->
        (prefs[SETUP_COMPLETED] ?: false) || (prefs[SETUP_SKIPPED] ?: false)
    }

    suspend fun skipSetup() {
        context.setupDataStore.edit { it[SETUP_SKIPPED] = true }
    }

    suspend fun completeSetup() {
        context.setupDataStore.edit { it[SETUP_COMPLETED] = true }
    }
}
