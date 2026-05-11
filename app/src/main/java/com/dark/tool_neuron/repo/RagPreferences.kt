package com.dark.tool_neuron.repo

import android.content.Context
import com.dark.hxs.HexStorage
import com.dark.hxs.HxsRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RagPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val storage = HexStorage()

    private val _defaultEmbeddingModelId = MutableStateFlow<String?>(null)
    val defaultEmbeddingModelId: StateFlow<String?> = _defaultEmbeddingModelId.asStateFlow()

    init {
        val dir = File(context.filesDir, "rag_prefs")
        dir.mkdirs()
        val path = dir.absolutePath
        if (storage.exists(path)) storage.openPlaintext(path) else storage.createPlaintext(path)
        storage.ensureCollection(COLLECTION)
        storage.addIndex(COLLECTION, TAG_KEY, HexStorage.WIRE_BYTES)
        _defaultEmbeddingModelId.value = readString(KEY_DEFAULT_EMBEDDING)
    }

    fun setDefaultEmbeddingModelId(modelId: String?) {
        if (modelId.isNullOrBlank()) {
            removeKey(KEY_DEFAULT_EMBEDDING)
            _defaultEmbeddingModelId.value = null
        } else {
            writeString(KEY_DEFAULT_EMBEDDING, modelId)
            _defaultEmbeddingModelId.value = modelId
        }
    }

    private fun readString(key: String): String? {
        val record = storage.queryString(COLLECTION, TAG_KEY, key).firstOrNull() ?: return null
        return record.getString(TAG_VALUE).takeIf { it.isNotEmpty() }
    }

    private fun writeString(key: String, value: String) {
        storage.queryString(COLLECTION, TAG_KEY, key).forEach { storage.delete(COLLECTION, it.id) }
        val record = HxsRecord.build {
            putString(TAG_KEY, key)
            putString(TAG_VALUE, value)
        }
        storage.put(COLLECTION, record)
        storage.flush(COLLECTION)
    }

    private fun removeKey(key: String) {
        val records = storage.queryString(COLLECTION, TAG_KEY, key)
        records.forEach { storage.delete(COLLECTION, it.id) }
        storage.flush(COLLECTION)
    }

    companion object {
        private const val COLLECTION = "rag_prefs"
        private const val KEY_DEFAULT_EMBEDDING = "default_embedding_model_id"

        private const val TAG_KEY = 1
        private const val TAG_VALUE = 2
    }
}
