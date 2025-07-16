package com.dark.neuroverse.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

object UserPrefs {
    private val JNI_INSTALLED_KEY = booleanPreferencesKey("jni_installed")

    fun isJNIInstalled(context: Context): Flow<Boolean?> {
        return context.dataStore.data.map { it[JNI_INSTALLED_KEY] }
    }

    suspend fun markJNIInstalled(context: Context, model: Boolean) {
        context.dataStore.edit { it[JNI_INSTALLED_KEY] = model }
    }
}