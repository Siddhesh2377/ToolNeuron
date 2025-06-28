package com.dark.neuroverse.utils

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

private val Context.dataStore by preferencesDataStore(name = "settings")

object UserPrefs {
    private val TERMS_ACCEPTED_KEY = booleanPreferencesKey("terms_accepted")
    private val ASSISTANT_ENABLED_KEY = booleanPreferencesKey("assistant_enabled")
    private val ARTIFICIAL_GRENDEL_UNDERSTANDING_KEY = booleanPreferencesKey("artificial_grendel_understanding")
    private val IS_ONBOARDING_COMPLETE_KEY = booleanPreferencesKey("is_onboarding_complete")

    fun isTermsAccepted(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { it[TERMS_ACCEPTED_KEY] == true }
    }

    suspend fun setTermsAccepted(context: Context, accepted: Boolean) {
        context.dataStore.edit { it[TERMS_ACCEPTED_KEY] = accepted }
    }

    fun isAssistantEnabled(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { it[ASSISTANT_ENABLED_KEY] == true }
    }

    suspend fun setAssistantEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[ASSISTANT_ENABLED_KEY] = enabled }
    }

    fun isAGU(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { it[ARTIFICIAL_GRENDEL_UNDERSTANDING_KEY] == true }
    }

    suspend fun setAGU(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[ARTIFICIAL_GRENDEL_UNDERSTANDING_KEY] = enabled }
    }

    fun isOnboardingComplete(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { it[IS_ONBOARDING_COMPLETE_KEY] == true }
    }

    suspend fun setOnboardingComplete(context: Context, complete: Boolean) {
        context.dataStore.edit { it[IS_ONBOARDING_COMPLETE_KEY] = complete }
    }
}
