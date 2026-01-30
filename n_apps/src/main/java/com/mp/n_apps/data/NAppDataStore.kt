package com.mp.n_apps.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.nappDataStore: DataStore<Preferences> by preferencesDataStore(name = "napp_api")

class NAppDataStore(private val context: Context) {

    companion object {
        private val AGENTS_JSON = stringPreferencesKey("agents_json")
        private val MAX_AGENT_ROUNDS = intPreferencesKey("max_agent_rounds")

        // Legacy keys (for migration)
        private val LEGACY_API_KEY = stringPreferencesKey("napp_api_key")
        private val LEGACY_API_URL = stringPreferencesKey("napp_api_url")
        private val LEGACY_API_MODEL = stringPreferencesKey("napp_api_model")

        const val DEFAULT_URL = "https://api.groq.com/openai"
        const val DEFAULT_MODEL = "openai/gpt-oss-20b"
        const val DEFAULT_MAX_ROUNDS = 10
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ── Agents list ──

    val agents: Flow<List<AgentConfig>> = context.nappDataStore.data.map { prefs ->
        val raw = prefs[AGENTS_JSON]
        if (raw != null) {
            try {
                json.decodeFromString<AgentConfigList>(raw).agents
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    // ── Active agent (derived) ──

    val activeAgent: Flow<AgentConfig?> = agents.map { list ->
        list.firstOrNull { it.isActive }
    }

    // ── Max rounds ──

    val maxRounds: Flow<Int> = context.nappDataStore.data.map { prefs ->
        prefs[MAX_AGENT_ROUNDS] ?: DEFAULT_MAX_ROUNDS
    }

    // ── CRUD Operations ──

    suspend fun addAgent(agent: AgentConfig) {
        context.nappDataStore.edit { prefs ->
            val current = readAgentsList(prefs)
            val newAgent = if (current.isEmpty()) agent.copy(isActive = true) else agent
            val updated = current + newAgent
            prefs[AGENTS_JSON] = json.encodeToString(AgentConfigList(updated))
        }
    }

    suspend fun updateAgent(agent: AgentConfig) {
        context.nappDataStore.edit { prefs ->
            val current = readAgentsList(prefs)
            val updated = current.map { if (it.id == agent.id) agent else it }
            prefs[AGENTS_JSON] = json.encodeToString(AgentConfigList(updated))
        }
    }

    suspend fun deleteAgent(agentId: String) {
        context.nappDataStore.edit { prefs ->
            val current = readAgentsList(prefs)
            val updated = current.filter { it.id != agentId }
            val result = if (updated.none { it.isActive } && updated.isNotEmpty()) {
                updated.mapIndexed { i, a -> if (i == 0) a.copy(isActive = true) else a }
            } else {
                updated
            }
            prefs[AGENTS_JSON] = json.encodeToString(AgentConfigList(result))
        }
    }

    suspend fun setActiveAgent(agentId: String) {
        context.nappDataStore.edit { prefs ->
            val current = readAgentsList(prefs)
            val updated = current.map { it.copy(isActive = it.id == agentId) }
            prefs[AGENTS_JSON] = json.encodeToString(AgentConfigList(updated))
        }
    }

    suspend fun setMaxRounds(rounds: Int) {
        context.nappDataStore.edit { prefs ->
            prefs[MAX_AGENT_ROUNDS] = rounds.coerceIn(1, 50)
        }
    }

    // ── Migration from legacy keys ──

    suspend fun migrateIfNeeded() {
        val prefs = context.nappDataStore.data.first()
        if (prefs[AGENTS_JSON] != null) return

        val legacyKey = prefs[LEGACY_API_KEY] ?: ""
        val legacyUrl = prefs[LEGACY_API_URL] ?: ""
        val legacyModel = prefs[LEGACY_API_MODEL] ?: ""

        if (legacyKey.isNotBlank() || legacyUrl.isNotBlank() || legacyModel.isNotBlank()) {
            val migrated = AgentConfig(
                id = UUID.randomUUID().toString(),
                name = "Default Agent",
                providerUrl = legacyUrl.ifBlank { DEFAULT_URL },
                modelName = legacyModel.ifBlank { DEFAULT_MODEL },
                apiKey = legacyKey,
                isActive = true
            )
            context.nappDataStore.edit { it[AGENTS_JSON] = json.encodeToString(AgentConfigList(listOf(migrated))) }
        }
    }

    // ── Helper ──

    private fun readAgentsList(prefs: Preferences): List<AgentConfig> {
        val raw = prefs[AGENTS_JSON] ?: return emptyList()
        return try {
            json.decodeFromString<AgentConfigList>(raw).agents
        } catch (_: Exception) {
            emptyList()
        }
    }
}
