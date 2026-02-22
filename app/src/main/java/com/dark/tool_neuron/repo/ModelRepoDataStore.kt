package com.dark.tool_neuron.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dark.tool_neuron.models.data.HFModelRepository
import com.dark.tool_neuron.models.data.ModelCategory
import com.dark.tool_neuron.models.data.ModelType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.modelRepoDataStore: DataStore<Preferences> by preferencesDataStore(name = "model_repositories")

class ModelRepositoryDataStore(private val context: Context) {

    companion object {
        private val MODEL_REPOS_KEY = stringPreferencesKey("model_repositories")

        val DEFAULT_REPOSITORIES = listOf(
            // === GENERAL ===
            HFModelRepository(
                id = "qwen2_5_0_5b_instruct",
                name = "Qwen 2.5 Instruct (0.5B)",
                repoPath = "Qwen/Qwen2.5-0.5B-Instruct-GGUF",
                modelType = ModelType.GGUF,
                isEnabled = true,
                category = ModelCategory.GENERAL
            ),
            HFModelRepository(
                id = "unsloth-qwen3",
                name = "Qwen3 8B",
                repoPath = "unsloth/Qwen3-8B-GGUF",
                modelType = ModelType.GGUF,
                isEnabled = true,
                category = ModelCategory.GENERAL
            ),
            HFModelRepository(
                id = "liquidai-lfm2-350m",
                name = "LFM2 350M",
                repoPath = "LiquidAI/LFM2-350M-GGUF",
                modelType = ModelType.GGUF,
                isEnabled = true,
                category = ModelCategory.GENERAL
            ),
            // === UNCENSORED ===
            HFModelRepository(
                id = "gemma3-emophilic-1b",
                name = "Gemma3 Emophilic (1B)",
                repoPath = "Novaciano/Gemma3-Emophilic-1B-GGUF",
                modelType = ModelType.GGUF,
                isEnabled = true,
                category = ModelCategory.UNCENSORED
            ),
            HFModelRepository(
                id = "gemma3-emotional-1b",
                name = "Gemma3 Emotional (1B)",
                repoPath = "mradermacher/Gemma3-Emotional-1B-i1-GGUF",
                modelType = ModelType.GGUF,
                isEnabled = true,
                category = ModelCategory.UNCENSORED
            ),
            HFModelRepository(
                id = "sex-roleplay-1b",
                name = "SEX ROLEPLAY 3.2 (1B)",
                repoPath = "mradermacher/SEX_ROLEPLAY-3.2-1B-i1-GGUF",
                modelType = ModelType.GGUF,
                isEnabled = true,
                category = ModelCategory.UNCENSORED
            )
        )
    }

    val repositories: Flow<List<HFModelRepository>> =
        context.modelRepoDataStore.data.map { preferences ->
            val json = preferences[MODEL_REPOS_KEY]
            if (json != null) {
                try {
                    val saved = Json.decodeFromString<List<HFModelRepository>>(json)
                    // Merge any new default repos not yet in saved data
                    val savedIds = saved.map { it.id }.toSet()
                    val newDefaults = DEFAULT_REPOSITORIES.filter { it.id !in savedIds }
                    if (newDefaults.isNotEmpty()) saved + newDefaults else saved
                } catch (e: Exception) {
                    DEFAULT_REPOSITORIES
                }
            } else {
                DEFAULT_REPOSITORIES
            }
        }

    suspend fun saveRepositories(repos: List<HFModelRepository>) {
        context.modelRepoDataStore.edit { preferences ->
            preferences[MODEL_REPOS_KEY] = Json.encodeToString(repos)
        }
    }

    suspend fun addRepository(repo: HFModelRepository) {
        val current = repositories.first()
        saveRepositories(current + repo)
    }

    suspend fun removeRepository(repoId: String) {
        val current = repositories.first()
        saveRepositories(current.filterNot { it.id == repoId })
    }

    suspend fun toggleRepository(repoId: String) {
        val current = repositories.first()
        saveRepositories(current.map {
            if (it.id == repoId) it.copy(isEnabled = !it.isEnabled)
            else it
        })
    }

    suspend fun updateRepository(repo: HFModelRepository) {
        val current = repositories.first()
        saveRepositories(current.map {
            if (it.id == repo.id) repo else it
        })
    }
}