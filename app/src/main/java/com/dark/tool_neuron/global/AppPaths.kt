package com.dark.tool_neuron.global

import android.content.Context
import java.io.File

/**
 * Centralized path registry for all app directories.
 * All file I/O should reference paths from here — never hardcode directory names.
 */
object AppPaths {

    // ── Core Directories ──

    /** Unified Memory System storage */
    fun ums(context: Context): File =
        File(context.filesDir, "ums")

    /** Legacy encrypted vault (migration only) */
    fun memoryVault(context: Context): File =
        File(context.filesDir, "memory_vault")

    /** Legacy vault file (migration only) */
    fun vaultFile(context: Context): File =
        File(context.filesDir, "memory_vault/vault.mvlt")

    // ── Model Directories ──

    /** Root models directory */
    fun models(context: Context): File =
        File(context.filesDir, "models")

    /** Specific model directory (for diffusion) */
    fun modelDir(context: Context, modelId: String): File =
        File(models(context), modelId)

    /** Specific GGUF model file */
    fun modelFile(context: Context, modelId: String): File =
        File(models(context), "$modelId.gguf")

    /** TTS model directory */
    fun ttsModel(context: Context): File =
        File(models(context), "supertonic-2")

    /** Embedding model file */
    fun embeddingModel(context: Context): File =
        File(context.filesDir, "embedding_model/all-MiniLM-L6-v2-Q5_K_M.gguf")

    /** Temporary download directory for a model */
    fun tempDownloads(context: Context, modelId: String): File =
        File(context.filesDir, "temp_downloads/$modelId")

    /** Prompt KV cache directory */
    fun promptCache(context: Context): File =
        File(context.cacheDir, "prompt_cache")

    // ── Data Directories ──

    /** RAG databases */
    fun rags(context: Context): File =
        File(context.filesDir, "rags").also { it.mkdirs() }

    /** Specific RAG file */
    fun ragFile(context: Context, ragId: String): File =
        File(rags(context), "$ragId.neuron")

    /** Persona avatar images */
    fun personaAvatars(context: Context): File =
        File(context.filesDir, "persona_avatars")

    // ── Image Tools ──

    /** Image tool model weights (upscaler, segmenter, lama, depth, style) */
    fun imageTools(context: Context): File =
        File(context.filesDir, "image_tools")

    /** Specific image tool model file */
    fun imageToolModel(context: Context, fileName: String): File =
        File(imageTools(context), fileName)

    // ── Agent Space ──

    fun agentProjects(context: Context): File =
        File(context.filesDir, "agent_projects").also { it.mkdirs() }

    fun agentProjectDir(context: Context, projectId: String): File =
        File(agentProjects(context), projectId).also { it.mkdirs() }
}
