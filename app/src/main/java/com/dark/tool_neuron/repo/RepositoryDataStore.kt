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
            HFRepository("lfm25-12b-instruct", "LFM 2.5 1.2B Instruct", "LiquidAI/LFM2.5-1.2B-Instruct-GGUF"),
            HFRepository("lfm25-12b-thinking", "LFM 2.5 1.2B Thinking", "unsloth/LFM2.5-1.2B-Thinking-GGUF"),
            // LFM (vision) — kept; small enough to be a default VLM
            HFRepository("lfm2-vl-450m", "LFM2-VL 450M", "LiquidAI/LFM2-VL-450M-GGUF"),
            HFRepository("lfm25-vl-16b", "LFM2.5-VL 1.6B", "LiquidAI/LFM2.5-VL-1.6B-GGUF"),
            // Qwen (text) — small, tool-calling tested
            HFRepository("qwen25-05b-instruct", "Qwen2.5 0.5B Instruct", "unsloth/Qwen2.5-0.5B-Instruct-GGUF"),
            HFRepository("qwen3-0.6b", "Qwen3 0.6B", "Qwen/Qwen3-0.6B-GGUF"),
            HFRepository("unsloth-qwen3_5-0_8b", "Qwen3.5 0.8B", "unsloth/Qwen3.5-0.8B-GGUF"),
            HFRepository("unsloth-qwen3_5-4b", "Qwen3.5 4B", "unsloth/Qwen3.5-4B-GGUF"),
            // Qwen (vision)
            HFRepository("qwen3-vl-2b", "Qwen3-VL 2B Instruct", "Qwen/Qwen3-VL-2B-Instruct-GGUF"),
            // Embeddings / RAG
            HFRepository("nomic-embed-text-v15", "Nomic Embed Text v1.5", "nomic-ai/nomic-embed-text-v1.5-GGUF", category = ModelCategory.RESEARCH),
            HFRepository("bge-small-en-v15-q8", "BGE Small EN v1.5", "ggml-org/bge-small-en-v1.5-Q8_0-GGUF", category = ModelCategory.RESEARCH),
            HFRepository("e5-small-v2-q8", "E5 Small v2", "ggml-org/e5-small-v2-Q8_0-GGUF", category = ModelCategory.RESEARCH),
            HFRepository("bge-m3", "BGE M3 Embedding", "gpustack/bge-m3-GGUF", category = ModelCategory.RESEARCH),
            HFRepository("qwen3-embedding-4b", "Qwen3 Embedding 4B", "Qwen/Qwen3-Embedding-4B-GGUF", category = ModelCategory.RESEARCH),
            // Code-focused chat
            HFRepository("qwen3-coder-30b-a3b", "Qwen3 Coder 30B A3B", "unsloth/Qwen3-Coder-30B-A3B-Instruct-GGUF", category = ModelCategory.CODING),
            // Mistral
            HFRepository("mistral-7b-v03", "Mistral 7B Instruct v0.3", "bartowski/Mistral-7B-Instruct-v0.3-GGUF"),
            // Gemma
            HFRepository("gemma3-270m-it", "Gemma 3 270M IT", "unsloth/gemma-3-270m-it-GGUF"),
            HFRepository("gemma3-1b-it", "Gemma 3 1B IT", "unsloth/gemma-3-1b-it-GGUF"),
            HFRepository("gemma4-e2b-it", "Gemma 4 E2B IT", "unsloth/gemma-4-E2B-it-GGUF"),
            // Tool-calling champion (per project memory)
            HFRepository("smollm2-360m-instruct", "SmolLM2 360M Instruct", "HuggingFaceTB/SmolLM2-360M-Instruct-GGUF"),
            HFRepository("smollm3-3b", "SmolLM3 3B", "HuggingFaceTB/SmolLM3-3B-GGUF"),
            // General-purpose pick
            HFRepository("phi35-mini", "Phi 3.5 Mini Instruct", "unsloth/Phi-3.5-mini-instruct-GGUF"),
            // Reasoning / thinking
            HFRepository("deepseek-r1-qwen-15b", "DeepSeek-R1 Distill Qwen 1.5B", "unsloth/DeepSeek-R1-Distill-Qwen-1.5B-GGUF"),
            HFRepository("deepseek-r1-qwen-7b", "DeepSeek-R1 Distill Qwen 7B", "unsloth/DeepSeek-R1-Distill-Qwen-7B-GGUF"),
            // Uncensored / roleplay (Llama 3.2 1B class — ~700 MB–1.3 GB)
            // mradermacher/* repos pulled — their GGUF-embedded chat templates crash gguf_lib's minja eval (SIGBUS)
            HFRepository("novaciano-sorete-1b", "Sorete 1B", "Novaciano/Sorete-1B-GGUF", category = ModelCategory.UNCENSORED),
            HFRepository("novaciano-chronos-1b", "Chronos 1B (iMatrix)", "Novaciano/Chronos-1B-iMatrix-GGUF", category = ModelCategory.UNCENSORED),
            HFRepository("novaciano-toxic-npc-1b", "Toxic NPC 1B", "Novaciano/Toxic.NPC-1B-GGUF", category = ModelCategory.UNCENSORED),
            HFRepository("novaciano-doctor-horror-1b", "Doctor Horror 1B", "Novaciano/Doctor_Horror-3.2-1B-GGUF", category = ModelCategory.UNCENSORED),
        )

        private val REMOVED_IDS = setOf(
            "sd-qnn", "sd-mnn", "sd-cyberrealistic-qnn",
            "mrad-emo-sex-v2-1b", "mrad-sex-fusion-1b",
        )
    }
}
