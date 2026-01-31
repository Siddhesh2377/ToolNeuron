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
            // Existing repositories (with categories added)
            HFModelRepository(
                id = "qwen2_5_0_5b_instruct",
                name = "Qwen 2.5 Instruct (0.5B)",
                repoPath = "Qwen/Qwen2.5-0.5B-Instruct-GGUF",
                modelType = ModelType.GGUF,
                isEnabled = true,
                category = ModelCategory.GENERAL
            ),
            HFModelRepository(
                id = "pars_medical_llama_3b",
                name = "Pars Medical LLaMA (3B)",
                repoPath = "HexQuant/Pars-Medical-o1-Llama-FFT-GGUF",
                modelType = ModelType.GGUF,
                isEnabled = true,
                category = ModelCategory.MEDICAL
            ),

            // GENERAL CATEGORY (2 new repos)
            HFModelRepository(
                id = "unsloth-deepseek-r1",
                name = "DeepSeek R1 Qwen3 8B",
                repoPath = "unsloth/DeepSeek-R1-0528-Qwen3-8B-GGUF",
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

            // MEDICAL CATEGORY (8 new repos)
            HFModelRepository(
                id = "m42-llama3-med42",
                name = "Llama3 Med42 8B",
                repoPath = "m42-health/Llama3-Med42-8B",
                modelType = ModelType.GGUF,
                isEnabled = true,
                category = ModelCategory.MEDICAL
            ),
            HFModelRepository(
                id = "dr-tulu-8b",
                name = "DR Tulu 8B",
                repoPath = "mradermacher/DR-Tulu-8B-i1-GGUF",
                modelType = ModelType.GGUF,
                isEnabled = true,
                category = ModelCategory.MEDICAL
            ),
            HFModelRepository(
                id = "deepseek-r1-drugdetection",
                name = "DeepSeek R1 Drug Detection 70B",
                repoPath = "mradermacher/DeepSeek-R1-Distill-Llama-70B-DrugDetection-i1-GGUF",
                modelType = ModelType.GGUF,
                isEnabled = true,
                category = ModelCategory.MEDICAL
            ),
            HFModelRepository(
                id = "psychocounsel-theraspace",
                name = "PsychoCounsel Theraspace 8B",
                repoPath = "mradermacher/PsychoCounsel-Theraspace-Llama3-8B-i1-GGUF",
                modelType = ModelType.GGUF,
                isEnabled = true,
                category = ModelCategory.MEDICAL
            ),

            // RESEARCH CATEGORY (5 new repos)
            HFModelRepository(
                id = "liquidai-lfm2",
                name = "LiquidAI LFM2 8B",
                repoPath = "LiquidAI/LFM2-8B-A1B-GGUF",
                modelType = ModelType.GGUF,
                isEnabled = true,
                category = ModelCategory.RESEARCH
            ),
            HFModelRepository(
                id = "deephermes-reasoning",
                name = "DeepHermes 3 Llama 8B",
                repoPath = "Mungert/DeepHermes-3-Llama-3-8B-Preview-GGUF",
                modelType = ModelType.GGUF,
                isEnabled = true,
                category = ModelCategory.RESEARCH
            ),

            // CODING CATEGORY
            HFModelRepository(
                id = "deepseek-coder",
                name = "DeepSeek Coder 6.7B",
                repoPath = "deepseek-ai/deepseek-coder-6.7b-instruct",
                modelType = ModelType.GGUF,
                isEnabled = true,
                category = ModelCategory.CODING
            ),
            HFModelRepository(
                id = "ruvltra-claude-code",
                name = "Ruvltra Claude Code 0.5B",
                repoPath = "ruv/ruvltra-claude-code",
                modelType = ModelType.GGUF,
                isEnabled = true,
                category = ModelCategory.CODING
            ),

            // UNCENSORED CATEGORY
            HFModelRepository(
                id = "gemma3-emophilic",
                name = "Gemma3 Emophilic 1B",
                repoPath = "Novaciano/Gemma3-Emophilic-1B-GGUF",
                modelType = ModelType.GGUF,
                isEnabled = true,
                category = ModelCategory.UNCENSORED
            ),

            // BUSINESS CATEGORY (2 new repos)
            HFModelRepository(
                id = "granite-business",
                name = "Granite 3.3 Business 8B",
                repoPath = "unsloth/granite-3.3-8b-instruct-GGUF",
                modelType = ModelType.GGUF,
                isEnabled = true,
                category = ModelCategory.BUSINESS
            ),
            HFModelRepository(
                id = "pdfai-llama",
                name = "PDFai Llama 3.1 8B",
                repoPath = "mradermacher/PDFai-Llama-3.1-8B-GGUF",
                modelType = ModelType.GGUF,
                isEnabled = true,
                category = ModelCategory.BUSINESS
            ),

            // CYBERSECURITY CATEGORY (2 new repos)
            HFModelRepository(
                id = "colibri-cybersec",
                name = "Colibri Cybersecurity 8B",
                repoPath = "mradermacher/Colibri_8b_v0.1-GGUF",
                modelType = ModelType.GGUF,
                isEnabled = true,
                category = ModelCategory.CYBERSECURITY
            ),
            HFModelRepository(
                id = "seneca-cybersec",
                name = "Seneca Cybersecurity 32B",
                repoPath = "AlicanKiraz0/Seneca-Cybersecurity-LLM-x-DeepSeek-R1-Distill-Qwen-32B-v1.3-Q4_K_M-GGUF",
                modelType = ModelType.GGUF,
                isEnabled = true,
                category = ModelCategory.CYBERSECURITY
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