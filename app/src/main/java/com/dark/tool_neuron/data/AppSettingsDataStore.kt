package com.dark.tool_neuron.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dark.tool_neuron.global.PerformanceMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

class AppSettingsDataStore(private val context: Context) {

    companion object {
        private val STREAMING_ENABLED = booleanPreferencesKey("streaming_enabled")
        private val CHAT_MEMORY_ENABLED = booleanPreferencesKey("chat_memory_enabled")
        private val TOOL_CALLING_ENABLED = booleanPreferencesKey("tool_calling_enabled")
        private val TOOL_CALLING_BYPASS_ENABLED = booleanPreferencesKey("tool_calling_bypass_enabled")
        private val IMAGE_BLUR_ENABLED = booleanPreferencesKey("image_blur_enabled")
        private val LOAD_TTS_ON_START = booleanPreferencesKey("load_tts_on_start")
        private val CODE_HIGHLIGHT_ENABLED = booleanPreferencesKey("code_highlight_enabled")
        private val LAST_CHAT_ID = stringPreferencesKey("last_chat_id")
        private val LAST_MODEL_ID = stringPreferencesKey("last_model_id")
        private val ACTIVE_PERSONA_ID = stringPreferencesKey("active_persona_id")
        private val AI_MEMORY_ENABLED = booleanPreferencesKey("ai_memory_enabled")
        private val SECURITY_MODE = stringPreferencesKey("security_mode")
        private val GUIDE_SEEN = booleanPreferencesKey("showcase_seen") // key kept for backward compat
        private val HARDWARE_PROFILE_JSON = stringPreferencesKey("hardware_profile_json")
        private val HARDWARE_TUNING_ENABLED = booleanPreferencesKey("hardware_tuning_enabled")
        private val PERFORMANCE_MODE = stringPreferencesKey("performance_mode")
        private val ASK_MODEL_RELOAD_DIALOG = booleanPreferencesKey("ask_model_reload_dialog")
    }

    val streamingEnabled: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[STREAMING_ENABLED] ?: true
    }

    val chatMemoryEnabled: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[CHAT_MEMORY_ENABLED] ?: true
    }

    val toolCallingEnabled: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[TOOL_CALLING_ENABLED] ?: true
    }

    val toolCallingBypassEnabled: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[TOOL_CALLING_BYPASS_ENABLED] ?: false
    }

    val imageBlurEnabled: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[IMAGE_BLUR_ENABLED] ?: true
    }

    val loadTTSOnStart: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[LOAD_TTS_ON_START] ?: true
    }

    val codeHighlightEnabled: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[CODE_HIGHLIGHT_ENABLED] ?: true
    }

    suspend fun updateStreamingEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[STREAMING_ENABLED] = enabled }
    }

    suspend fun updateChatMemoryEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[CHAT_MEMORY_ENABLED] = enabled }
    }

    suspend fun updateToolCallingEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[TOOL_CALLING_ENABLED] = enabled }
    }

    suspend fun updateToolCallingBypassEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[TOOL_CALLING_BYPASS_ENABLED] = enabled }
    }

    suspend fun updateImageBlurEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[IMAGE_BLUR_ENABLED] = enabled }
    }

    suspend fun updateLoadTTSOnStart(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[LOAD_TTS_ON_START] = enabled }
    }

    suspend fun updateCodeHighlightEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[CODE_HIGHLIGHT_ENABLED] = enabled }
    }

    val lastChatId: Flow<String?> = context.appSettingsDataStore.data.map { prefs ->
        prefs[LAST_CHAT_ID]
    }

    suspend fun saveLastChatId(chatId: String?) {
        context.appSettingsDataStore.edit { prefs ->
            if (chatId != null) {
                prefs[LAST_CHAT_ID] = chatId
            } else {
                prefs.remove(LAST_CHAT_ID)
            }
        }
    }

    val lastModelId: Flow<String?> = context.appSettingsDataStore.data.map { prefs ->
        prefs[LAST_MODEL_ID]
    }

    suspend fun saveLastModelId(modelId: String?) {
        context.appSettingsDataStore.edit { prefs ->
            if (modelId != null) {
                prefs[LAST_MODEL_ID] = modelId
            } else {
                prefs.remove(LAST_MODEL_ID)
            }
        }
    }

    val activePersonaId: Flow<String?> = context.appSettingsDataStore.data.map { prefs ->
        prefs[ACTIVE_PERSONA_ID]
    }

    suspend fun saveActivePersonaId(personaId: String?) {
        context.appSettingsDataStore.edit { prefs ->
            if (personaId != null) {
                prefs[ACTIVE_PERSONA_ID] = personaId
            } else {
                prefs.remove(ACTIVE_PERSONA_ID)
            }
        }
    }

    val aiMemoryEnabled: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[AI_MEMORY_ENABLED] ?: true
    }

    suspend fun updateAiMemoryEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[AI_MEMORY_ENABLED] = enabled }
    }

    val securityMode: Flow<String> = context.appSettingsDataStore.data.map { prefs ->
        prefs[SECURITY_MODE] ?: "REGULAR"
    }

    suspend fun saveSecurityMode(mode: String) {
        context.appSettingsDataStore.edit { it[SECURITY_MODE] = mode }
    }

    val guideSeen: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[GUIDE_SEEN] ?: false
    }

    suspend fun saveGuideSeen(seen: Boolean) {
        context.appSettingsDataStore.edit { it[GUIDE_SEEN] = seen }
    }

    val hardwareProfileJson: Flow<String?> = context.appSettingsDataStore.data.map { prefs ->
        prefs[HARDWARE_PROFILE_JSON]
    }

    val hardwareTuningEnabled: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[HARDWARE_TUNING_ENABLED] ?: true
    }

    suspend fun saveHardwareProfile(json: String) {
        context.appSettingsDataStore.edit { it[HARDWARE_PROFILE_JSON] = json }
    }

    suspend fun updateHardwareTuningEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[HARDWARE_TUNING_ENABLED] = enabled }
    }

    val performanceMode: Flow<PerformanceMode> = context.appSettingsDataStore.data.map { prefs ->
        val name = prefs[PERFORMANCE_MODE] ?: PerformanceMode.BALANCED.name
        try { PerformanceMode.valueOf(name) } catch (_: Exception) { PerformanceMode.BALANCED }
    }

    suspend fun savePerformanceMode(mode: PerformanceMode) {
        context.appSettingsDataStore.edit { it[PERFORMANCE_MODE] = mode.name }
    }

    val askModelReloadDialog: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[ASK_MODEL_RELOAD_DIALOG] ?: true
    }

    suspend fun updateAskModelReloadDialog(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[ASK_MODEL_RELOAD_DIALOG] = enabled }
    }

    suspend fun clear() {
        context.appSettingsDataStore.edit { it.clear() }
    }
}
