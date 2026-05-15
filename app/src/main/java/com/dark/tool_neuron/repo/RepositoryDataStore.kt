package com.dark.tool_neuron.repo

import android.content.Context
import com.dark.tool_neuron.model.HFRepository
import com.dark.tool_neuron.model.ModelCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RepositoryDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val file = File(context.filesDir, "config/repositories.json").apply {
        parentFile?.mkdirs()
    }

    private val _repositories = MutableStateFlow(load())
    val repositories: StateFlow<List<HFRepository>> = _repositories.asStateFlow()

    private fun load(): List<HFRepository> {
        if (!file.exists()) return DEFAULT_REPOSITORIES
        return try {
            val arr = JSONArray(file.readText())
            val saved = (0 until arr.length()).map { arr.getJSONObject(it).toRepo() }
                .filter { it.id !in REMOVED_IDS }
            val savedIds = saved.map { it.id }.toSet()
            val newDefaults = DEFAULT_REPOSITORIES.filter { it.id !in savedIds }
            val merged = if (newDefaults.isNotEmpty()) saved + newDefaults else saved
            if (merged.size != arr.length()) save(merged)
            merged
        } catch (_: Exception) { DEFAULT_REPOSITORIES }
    }

    private fun save(repos: List<HFRepository>) {
        val arr = JSONArray()
        repos.forEach { arr.put(it.toJson()) }
        file.writeText(arr.toString())
        _repositories.value = repos
    }

    fun addRepository(repo: HFRepository) {
        save(_repositories.value + repo)
    }

    fun removeRepository(repoId: String) {
        save(_repositories.value.filter { it.id != repoId })
    }

    fun toggleRepository(repoId: String) {
        save(_repositories.value.map {
            if (it.id == repoId) it.copy(isEnabled = !it.isEnabled) else it
        })
    }

    fun updateRepository(repo: HFRepository) {
        save(_repositories.value.map {
            if (it.id == repo.id) repo else it
        })
    }

    private fun HFRepository.toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("repoPath", repoPath)
        put("isEnabled", isEnabled); put("category", category.name)
    }

    private fun JSONObject.toRepo(): HFRepository = HFRepository(
        id = getString("id"),
        name = getString("name"),
        repoPath = getString("repoPath"),
        isEnabled = optBoolean("isEnabled", true),
        category = try { ModelCategory.valueOf(optString("category", "GENERAL")) }
                   catch (_: Exception) { ModelCategory.GENERAL },
    )

    companion object {
        val DEFAULT_REPOSITORIES = listOf(
            // LFM (text)
            HFRepository("lfm25-350m", "LFM 2.5 350M", "LiquidAI/LFM2.5-350M-GGUF"),
            // LFM (vision) — kept; small enough to be a default VLM
            HFRepository("lfm2-vl-450m", "LFM2-VL 450M", "LiquidAI/LFM2-VL-450M-GGUF"),
            // Qwen (text) — small, tool-calling tested
            HFRepository("qwen3-0.6b", "Qwen3 0.6B", "Qwen/Qwen3-0.6B-GGUF"),
            HFRepository("unsloth-qwen3_5-0_8b", "Qwen3.5 0.8B", "unsloth/Qwen3.5-0.8B-GGUF"),
            HFRepository("unsloth-qwen3_5-4b", "Qwen3.5 4B", "unsloth/Qwen3.5-4B-GGUF"),
            // Qwen (vision)
            HFRepository("qwen3-vl-2b", "Qwen3-VL 2B Instruct", "Qwen/Qwen3-VL-2B-Instruct-GGUF"),
            // Mistral
            HFRepository("mistral-7b-v03", "Mistral 7B Instruct v0.3", "bartowski/Mistral-7B-Instruct-v0.3-GGUF"),
            // Gemma
            HFRepository("gemma3-1b-it", "Gemma 3 1B IT", "unsloth/gemma-3-1b-it-GGUF"),
            HFRepository("gemma4-e2b-it", "Gemma 4 E2B IT", "unsloth/gemma-4-E2B-it-GGUF"),
            // Tool-calling champion (per project memory)
            HFRepository("smollm3-3b", "SmolLM3 3B", "HuggingFaceTB/SmolLM3-3B-GGUF"),
            // General-purpose pick
            HFRepository("phi35-mini", "Phi 3.5 Mini Instruct", "unsloth/Phi-3.5-mini-instruct-GGUF"),
        )

        private val REMOVED_IDS = setOf("sd-qnn", "sd-mnn", "sd-cyberrealistic-qnn")
    }
}
