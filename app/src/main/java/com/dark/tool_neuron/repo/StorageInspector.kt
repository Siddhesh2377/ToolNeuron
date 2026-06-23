package com.dark.tool_neuron.repo

import android.content.Context
import com.dark.tool_neuron.model.enums.ProviderType
import com.dark.tool_neuron.util.VlmPaths
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class StorageCategoryId {
    CHAT_MODELS,
    VLM,
    VOICE,
    DOCUMENTS,
    CHATS,
    CACHE,
    SYSTEM,
}

data class StorageCategorySnapshot(
    val id: StorageCategoryId,
    val sizeBytes: Long,
    val itemCount: Int,
)

data class StorageSnapshot(
    val totalBytes: Long,
    val categories: Map<StorageCategoryId, StorageCategorySnapshot>,
)

enum class StorageMaintenanceMode { QUICK_CLEAN, DETAILED_CHECK, DEEP_MODEL_TEST }

enum class StorageIssueSeverity { INFO, WARNING, ERROR }

data class StorageMaintenanceIssue(
    val title: String,
    val detail: String,
    val severity: StorageIssueSeverity,
    val fixed: Boolean = false,
)

data class StorageMaintenanceReport(
    val mode: StorageMaintenanceMode,
    val checkedCount: Int,
    val issueCount: Int,
    val fixedCount: Int,
    val skippedCount: Int,
    val issues: List<StorageMaintenanceIssue>,
)

@Singleton
class StorageInspector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelRepo: ModelRepository,
    private val chatRepo: ChatRepository,
    private val documentRepo: DocumentRepository,
    private val ragManager: RagManager,
) {
    suspend fun snapshot(): StorageSnapshot = withContext(Dispatchers.IO) {
        val files = context.filesDir
        val cache = context.cacheDir

        val modelsDir = File(files, "models")
        val vlmDir = File(modelsDir, "vlm")
        val voiceDir = File(files, "voice")
        val sourcesDir = File(files, "chat_documents/sources_v2")
        val chatStoreDir = File(files, "chat_store_v2")

        val chatModelsBytes = dirSize(modelsDir) - dirSize(vlmDir)
        val vlmBytes = dirSize(vlmDir)
        val voiceBytes = dirSize(voiceDir)
        val docsBytes = dirSize(sourcesDir)
        val chatsBytes = dirSize(chatStoreDir)
        val cacheBytes = dirSize(cache)

        val installed = modelRepo.models.value
        val chatModelCount = installed.count { isChatModel(it.path, it.providerType) }
        val vlmCount = installed.count { isVlmModel(it.path, it.providerType) }
        val voiceCount = installed.count {
            it.providerType == ProviderType.TTS || it.providerType == ProviderType.STT
        }
        val docCount = documentRepo.getAllDocuments().size
        val chatCount = chatRepo.chats.value.size

        val systemDirs = listOf(
            File(files, "app_bootstrap"),
            File(files, "app_prefs"),
            File(files, "chat_documents_meta_v1"),
            File(files, "rag_keyword_v1"),
            File(files, "config"),
            File(files, "model_store"),
        )
        val systemBytes = systemDirs.sumOf { dirSize(it) }

        val total = chatModelsBytes + vlmBytes + voiceBytes + docsBytes +
                chatsBytes + cacheBytes + systemBytes

        val map = mapOf(
            StorageCategoryId.CHAT_MODELS to StorageCategorySnapshot(
                StorageCategoryId.CHAT_MODELS, chatModelsBytes, chatModelCount,
            ),
            StorageCategoryId.VLM to StorageCategorySnapshot(
                StorageCategoryId.VLM, vlmBytes, vlmCount,
            ),
            StorageCategoryId.VOICE to StorageCategorySnapshot(
                StorageCategoryId.VOICE, voiceBytes, voiceCount,
            ),
            StorageCategoryId.DOCUMENTS to StorageCategorySnapshot(
                StorageCategoryId.DOCUMENTS, docsBytes, docCount,
            ),
            StorageCategoryId.CHATS to StorageCategorySnapshot(
                StorageCategoryId.CHATS, chatsBytes, chatCount,
            ),
            StorageCategoryId.CACHE to StorageCategorySnapshot(
                StorageCategoryId.CACHE, cacheBytes, 0,
            ),
            StorageCategoryId.SYSTEM to StorageCategorySnapshot(
                StorageCategoryId.SYSTEM, systemBytes, systemDirs.count { it.exists() },
            ),
        )

        StorageSnapshot(totalBytes = total, categories = map)
    }

    suspend fun clear(category: StorageCategoryId) = withContext(Dispatchers.IO) {
        when (category) {
            StorageCategoryId.CHAT_MODELS -> clearChatModels()
            StorageCategoryId.VLM -> clearVlm()
            StorageCategoryId.VOICE -> clearVoice()
            StorageCategoryId.DOCUMENTS -> clearDocuments()
            StorageCategoryId.CHATS -> clearChats()
            StorageCategoryId.CACHE -> clearCache()
            StorageCategoryId.SYSTEM -> Unit
        }
    }

    suspend fun maintenance(mode: StorageMaintenanceMode): StorageMaintenanceReport = withContext(Dispatchers.IO) {
        val issues = mutableListOf<StorageMaintenanceIssue>()
        var checked = 0
        var fixed = 0
        var skipped = 0

        fun add(issue: StorageMaintenanceIssue) {
            issues += issue
            if (issue.fixed) fixed++
        }

        if (mode == StorageMaintenanceMode.QUICK_CLEAN) {
            val roots = listOf(context.filesDir, context.cacheDir)
            roots.forEach { root ->
                root.walkTopDown()
                    .filter { it.isFile && (it.name.endsWith(".hxd_tmp") || it.name.startsWith("_archive_")) }
                    .forEach { file ->
                        checked++
                        val bytes = file.length()
                        val deleted = file.delete()
                        add(StorageMaintenanceIssue(
                            title = if (deleted) "Removed leftover file" else "Couldn't remove leftover file",
                            detail = "${file.name} · ${bytes} bytes",
                            severity = if (deleted) StorageIssueSeverity.INFO else StorageIssueSeverity.WARNING,
                            fixed = deleted,
                        ))
                    }
            }
            context.cacheDir.deleteRecursively()
            context.cacheDir.mkdirs()
            add(StorageMaintenanceIssue("Cache refreshed", "Temporary cache directory recreated", StorageIssueSeverity.INFO, fixed = true))
            return@withContext StorageMaintenanceReport(mode, checked, issues.size, fixed, skipped, issues.take(80))
        }

        modelRepo.models.value.forEach { model ->
            checked++
            val path = model.path
            val file = File(path)
            when {
                path.isBlank() -> add(StorageMaintenanceIssue("Broken model record", model.name, StorageIssueSeverity.ERROR))
                !path.startsWith("content://") && !file.exists() ->
                    add(StorageMaintenanceIssue("Missing model file", "${model.name} · $path", StorageIssueSeverity.ERROR))
                !path.startsWith("content://") && file.isFile && file.length() <= 0L ->
                    add(StorageMaintenanceIssue("Empty model file", "${model.name} · $path", StorageIssueSeverity.ERROR))
                !path.startsWith("content://") && file.isDirectory && file.walkTopDown().none { it.isFile && it.length() > 0L } ->
                    add(StorageMaintenanceIssue("Empty model folder", "${model.name} · $path", StorageIssueSeverity.ERROR))
            }
            if (model.providerType == ProviderType.VISION_CHAT && !path.startsWith("content://")) {
                val projector = VlmPaths.colocatedMmproj(file)
                if (projector == null || !projector.exists() || projector.length() <= 0L) {
                    add(StorageMaintenanceIssue("Vision projector missing", model.name, StorageIssueSeverity.ERROR))
                }
            }
            if (mode == StorageMaintenanceMode.DEEP_MODEL_TEST) {
                skipped += deepCheck(model.providerType, file, path, model.id, model.name, ::add)
            }
        }

        val referenced = modelRepo.models.value.asSequence()
            .mapNotNull { it.path.takeIf { p -> p.isNotBlank() && !p.startsWith("content://") } }
            .map { File(it).canonicalFile }
            .toSet()
        val modelRoots = listOf(
            File(context.filesDir, "models"),
            File(context.filesDir, "sd_models"),
            File(context.filesDir, "sd_upscalers"),
            File(context.filesDir, "voice"),
        )
        modelRoots.filter { it.exists() }.forEach { root ->
            root.walkTopDown()
                .filter { it.isFile && it.length() > 0L }
                .forEach { file ->
                    val canonical = file.canonicalFile
                    val owned = referenced.any { ref ->
                        canonical == ref || canonical.path.startsWith(ref.path + File.separator) || ref.path.startsWith(canonical.parentFile?.path.orEmpty())
                    }
                    if (!owned && mode == StorageMaintenanceMode.DETAILED_CHECK) {
                        add(StorageMaintenanceIssue("Possible orphan file", file.path, StorageIssueSeverity.WARNING))
                    }
                }
        }

        StorageMaintenanceReport(mode, checked, issues.size, fixed, skipped, issues.take(120))
    }

    private fun clearChatModels() {
        val models = modelRepo.models.value.filter { isChatModel(it.path, it.providerType) }
        models.forEach { model ->
            val file = File(model.path)
            if (file.exists()) file.delete()
            modelRepo.delete(model.id)
        }
    }

    private fun clearVlm() {
        val models = modelRepo.models.value.filter { isVlmModel(it.path, it.providerType) }
        val parents = mutableSetOf<File>()
        models.forEach { model ->
            val f = File(model.path)
            f.parentFile?.let { parents.add(it) }
            modelRepo.delete(model.id)
        }
        parents.forEach { it.deleteRecursively() }
        val vlmRoot = File(context.filesDir, "models/vlm")
        vlmRoot.listFiles()?.forEach { it.deleteRecursively() }
    }

    private fun clearVoice() {
        val models = modelRepo.models.value.filter {
            it.providerType == ProviderType.TTS || it.providerType == ProviderType.STT
        }
        models.forEach { model ->
            val f = File(model.path)
            f.parentFile?.deleteRecursively()
            modelRepo.delete(model.id)
        }
        File(context.filesDir, "voice").deleteRecursively()
    }

    private suspend fun clearDocuments() {
        documentRepo.clearAll()
        ragManager.release()
        File(context.filesDir, "chat_documents/sources_v2").deleteRecursively()
        File(context.filesDir, "chat_documents/sources").deleteRecursively()
    }

    private fun clearChats() {
        val chats = chatRepo.chats.value.toList()
        chats.forEach { chatRepo.deleteChat(it.id) }
    }

    private fun clearCache() {
        context.cacheDir.deleteRecursively()
        context.cacheDir.mkdirs()
    }

    private fun isChatModel(path: String, type: ProviderType): Boolean {
        if (type != ProviderType.GGUF) return false
        return !path.contains("/models/vlm/")
    }

    private fun isVlmModel(path: String, type: ProviderType): Boolean {
        if (type != ProviderType.GGUF) return false
        return path.contains("/models/vlm/")
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var total = 0L
        val stack = ArrayDeque<File>()
        stack.addLast(dir)
        while (stack.isNotEmpty()) {
            val f = stack.removeLast()
            if (f.isDirectory) {
                f.listFiles()?.forEach { stack.addLast(it) }
            } else {
                total += f.length()
            }
        }
        return total
    }

    private fun deepCheck(
        type: ProviderType,
        file: File,
        path: String,
        modelId: String,
        name: String,
        add: (StorageMaintenanceIssue) -> Unit,
    ): Int {
        if (path.startsWith("content://")) {
            add(StorageMaintenanceIssue("Skipped content URI", name, StorageIssueSeverity.INFO))
            return 1
        }
        if (!file.exists()) return 0
        when (type) {
            ProviderType.GGUF, ProviderType.VISION_CHAT, ProviderType.TOOL_SEARCH, ProviderType.EMBEDDING -> {
                val gguf = if (file.isFile) file else file.walkTopDown().firstOrNull { it.isFile && it.extension.equals("gguf", true) }
                if (gguf == null) add(StorageMaintenanceIssue("GGUF file missing", name, StorageIssueSeverity.ERROR))
                else if (!hasMagic(gguf, "GGUF")) add(StorageMaintenanceIssue("GGUF header check failed", gguf.name, StorageIssueSeverity.ERROR))
            }
            ProviderType.IMAGE_UPSCALER -> {
                if (!file.extension.equals("mnn", true) && !file.extension.equals("bin", true)) {
                    add(StorageMaintenanceIssue("Unexpected upscaler file type", file.name, StorageIssueSeverity.WARNING))
                }
            }
            ProviderType.IMAGE_GEN -> {
                val configLike = file.walkTopDown().any { it.isFile && it.name in setOf("tokenizer.json", "unet.bin", "unet.mnn", "clip.mnn", "vae_decoder.mnn") }
                if (!configLike) add(StorageMaintenanceIssue("Image model layout looks incomplete", name, StorageIssueSeverity.ERROR))
            }
            ProviderType.TTS, ProviderType.STT -> {
                val hasConfig = file.walkTopDown().any { it.isFile && it.extension.equals("json", true) }
                if (!hasConfig) add(StorageMaintenanceIssue("Voice config not found", name, StorageIssueSeverity.WARNING))
            }
        }
        modelRepo.getConfig(modelId)?.let { config ->
            runCatching { JSONObject(config.loadingParamsJson.ifBlank { "{}" }) }
                .onFailure { add(StorageMaintenanceIssue("Loading config JSON invalid", name, StorageIssueSeverity.ERROR)) }
            runCatching { JSONObject(config.inferenceParamsJson.ifBlank { "{}" }) }
                .onFailure { add(StorageMaintenanceIssue("Inference config JSON invalid", name, StorageIssueSeverity.ERROR)) }
        }
        return 0
    }

    private fun hasMagic(file: File, magic: String): Boolean = runCatching {
        file.inputStream().use { input ->
            val bytes = ByteArray(magic.length)
            input.read(bytes) == bytes.size && bytes.decodeToString() == magic
        }
    }.getOrDefault(false)
}
