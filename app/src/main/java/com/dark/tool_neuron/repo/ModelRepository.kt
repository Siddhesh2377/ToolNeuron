package com.dark.tool_neuron.repo

import android.content.Context
import com.dark.hxs.HexStorage
import com.dark.hxs.HxsRecord
import com.dark.tool_neuron.model.ModelConfig
import com.dark.tool_neuron.model.ModelInfo
import com.dark.tool_neuron.model.enums.PathType
import com.dark.tool_neuron.model.enums.ProviderType
import com.dark.tool_neuron.util.VlmPaths
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val storage = HexStorage()
    private val _models = MutableStateFlow<List<ModelInfo>>(emptyList())
    val models: StateFlow<List<ModelInfo>> = _models.asStateFlow()

    init {
        val dir = File(context.filesDir, "model_store")
        dir.mkdirs()
        val path = dir.absolutePath
        if (storage.exists(path)) {
            storage.openPlaintext(path)
        } else {
            storage.createPlaintext(path)
        }
        storage.ensureCollection(COL_MODELS)
        storage.ensureCollection(COL_CONFIG)
        storage.addIndex(COL_MODELS, TAG_ID, HexStorage.WIRE_BYTES)
        refresh()
    }

    fun refresh() {
        val all = storage.getAll(COL_MODELS).map { it.toModelInfo() }
        val seen = HashSet<String>(all.size)
        _models.value = all.filter { seen.add(it.id) }
    }

    fun insert(model: ModelInfo, config: ModelConfig? = null) {
        val existing = storage.queryString(COL_MODELS, TAG_ID, model.id)
        existing.forEach { storage.delete(COL_MODELS, it.id) }
        storage.put(COL_MODELS, model.toRecord())
        storage.flush(COL_MODELS)
        if (config != null) {
            val existingConfigs = storage.queryString(COL_CONFIG, TAG_CONFIG_MODEL_ID, model.id)
            existingConfigs.forEach { storage.delete(COL_CONFIG, it.id) }
            storage.put(COL_CONFIG, config.toRecord())
            storage.flush(COL_CONFIG)
        }
        refresh()
    }

    fun delete(modelId: String) {
        val records = storage.queryString(COL_MODELS, TAG_ID, modelId)
        records.forEach { storage.delete(COL_MODELS, it.id) }
        val configs = storage.queryString(COL_CONFIG, TAG_CONFIG_MODEL_ID, modelId)
        configs.forEach { storage.delete(COL_CONFIG, it.id) }
        storage.flushAll()
        refresh()
    }

    fun getConfig(modelId: String): ModelConfig? {
        val records = storage.queryString(COL_CONFIG, TAG_CONFIG_MODEL_ID, modelId)
        return records.firstOrNull()?.toModelConfig()
    }

    fun updateConfig(config: ModelConfig) {
        val existing = storage.queryString(COL_CONFIG, TAG_CONFIG_MODEL_ID, config.modelId)
        existing.forEach { storage.delete(COL_CONFIG, it.id) }
        storage.put(COL_CONFIG, config.toRecord())
        storage.flush(COL_CONFIG)
    }

    fun setActive(modelId: String) {
        val all = storage.getAll(COL_MODELS)
        all.forEach { record ->
            val wasActive = record.getBool(TAG_IS_ACTIVE)
            val isTarget = record.getString(TAG_ID) == modelId
            if (wasActive || isTarget) {
                record.putBool(TAG_IS_ACTIVE, isTarget)
                storage.update(COL_MODELS, record)
            }
        }
        storage.flush(COL_MODELS)
        refresh()
    }

    fun getModelById(modelId: String): ModelInfo? {
        return storage.queryString(COL_MODELS, TAG_ID, modelId)
            .firstOrNull()?.toModelInfo()
    }

    fun updateProviderType(modelId: String, providerType: ProviderType) {
        val records = storage.queryString(COL_MODELS, TAG_ID, modelId)
        records.forEach { record ->
            record.putString(TAG_PROVIDER_TYPE, providerType.name)
            if (providerType != ProviderType.GGUF && providerType != ProviderType.VISION_CHAT) {
                record.putBool(TAG_IS_ACTIVE, false)
            }
            storage.update(COL_MODELS, record)
        }
        storage.flush(COL_MODELS)
        refresh()
    }

    fun getModelsDir(): File {
        val dir = File(context.filesDir, "models")
        dir.mkdirs()
        return dir
    }

    fun modelFile(modelId: String, originalFileName: String? = null): File {
        val safeId = modelId.replace('/', '_').replace(':', '_')
        val name = when {
            !originalFileName.isNullOrBlank() -> {
                val ext = originalFileName.substringAfterLast('.', "").lowercase()
                val base = if (ext.isNotBlank() && safeId.lowercase().endsWith(".$ext")) {
                    safeId.dropLast(ext.length + 1)
                } else {
                    safeId
                }
                if (ext.isBlank()) base else "$base.$ext"
            }
            safeId.contains('.') -> safeId
            else -> "$safeId.gguf"
        }
        return File(getModelsDir(), name)
    }

    fun vlmModelFile(repoPath: String, originalFileName: String): File {
        val folder = VlmPaths.vlmFolder(getModelsDir(), repoPath)
        folder.mkdirs()
        val leaf = originalFileName.substringAfterLast('/')
        return File(folder, leaf)
    }

    fun vlmMmprojFile(repoPath: String, mmprojFileName: String): File {
        val folder = VlmPaths.vlmFolder(getModelsDir(), repoPath)
        folder.mkdirs()
        val leaf = mmprojFileName.substringAfterLast('/')
        return File(folder, leaf)
    }

    fun voiceDir(kind: String): File {
        val dir = File(context.filesDir, "voice/$kind")
        dir.mkdirs()
        return dir
    }

    fun voiceArchiveFile(kind: String, modelId: String, fileName: String): File {
        val dir = voiceDir(kind)
        val safe = modelId.replace('/', '_').replace(':', '_')
        val ext = fileName.substringAfterLast('.', "tar.bz2")
        val leaf = if (fileName.endsWith(".tar.bz2")) "$safe.tar.bz2" else "$safe.$ext"
        return File(dir, "_archive_$leaf")
    }

    fun imageModelDir(modelId: String): File {
        val safe = modelId.replace('/', '_').replace(':', '_')
        return File(context.filesDir, "sd_models/$safe").apply { mkdirs() }
    }

    fun imageModelArchive(modelId: String, fileName: String): File {
        val safe = modelId.replace('/', '_').replace(':', '_')
        val parent = File(context.filesDir, "sd_models").apply { mkdirs() }
        val ext = fileName.substringAfterLast('.', "zip")
        return File(parent, "_archive_${safe}.$ext")
    }

    fun imageUpscalerFile(modelId: String, fileName: String): File {
        val safe = modelId.replace('/', '_').replace(':', '_')
        val parent = File(context.filesDir, "sd_upscalers/$safe").apply { mkdirs() }
        val leaf = fileName.substringAfterLast('/')
        return File(parent, leaf)
    }



    companion object {
        private const val COL_MODELS = "models"
        private const val COL_CONFIG = "model_config"

        private const val TAG_ID = 1
        private const val TAG_NAME = 2
        private const val TAG_PATH = 3
        private const val TAG_PATH_TYPE = 4
        private const val TAG_PROVIDER_TYPE = 5
        private const val TAG_FILE_SIZE = 6
        private const val TAG_IS_ACTIVE = 7

        private const val TAG_CONFIG_ID = 1
        private const val TAG_CONFIG_MODEL_ID = 2
        private const val TAG_CONFIG_LOADING = 3
        private const val TAG_CONFIG_INFERENCE = 4
    }


    private fun ModelInfo.toRecord(): HxsRecord {
        val m = this
        return HxsRecord.build {
            putString(TAG_ID, m.id)
            putString(TAG_NAME, m.name)
            putString(TAG_PATH, m.path)
            putString(TAG_PATH_TYPE, m.pathType.name)
            putString(TAG_PROVIDER_TYPE, m.providerType.name)
            putTimestamp(TAG_FILE_SIZE, m.fileSize)
            putBool(TAG_IS_ACTIVE, m.isActive)
        }
    }

    private fun HxsRecord.toModelInfo(): ModelInfo = ModelInfo(
        id = getString(TAG_ID),
        name = getString(TAG_NAME),
        path = getString(TAG_PATH),
        pathType = try { PathType.valueOf(getString(TAG_PATH_TYPE)) } catch (_: Exception) { PathType.FILE },
        providerType = try { ProviderType.valueOf(getString(TAG_PROVIDER_TYPE)) } catch (_: Exception) { ProviderType.GGUF },
        fileSize = getTimestamp(TAG_FILE_SIZE),
        isActive = getBool(TAG_IS_ACTIVE),
    )

    private fun ModelConfig.toRecord(): HxsRecord {
        val c = this
        return HxsRecord.build {
            putString(TAG_CONFIG_ID, c.id)
            putString(TAG_CONFIG_MODEL_ID, c.modelId)
            putString(TAG_CONFIG_LOADING, c.loadingParamsJson)
            putString(TAG_CONFIG_INFERENCE, c.inferenceParamsJson)
        }
    }

    private fun HxsRecord.toModelConfig(): ModelConfig = ModelConfig(
        id = getString(TAG_CONFIG_ID),
        modelId = getString(TAG_CONFIG_MODEL_ID),
        loadingParamsJson = getString(TAG_CONFIG_LOADING),
        inferenceParamsJson = getString(TAG_CONFIG_INFERENCE),
    )
}
