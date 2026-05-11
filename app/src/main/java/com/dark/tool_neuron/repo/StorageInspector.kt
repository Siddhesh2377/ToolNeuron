package com.dark.tool_neuron.repo

import android.content.Context
import com.dark.tool_neuron.model.enums.ProviderType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
}
