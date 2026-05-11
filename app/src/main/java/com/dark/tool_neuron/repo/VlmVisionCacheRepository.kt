package com.dark.tool_neuron.repo

import android.content.Context
import android.util.Log
import com.dark.hxs.HexStorage
import com.dark.hxs.HxsRecord
import com.dark.hxs_encryptor.HxsEncryptor
import com.dark.tool_neuron.data.AppKeyStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VlmVisionCacheRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val keyStore: AppKeyStore,
    private val encryptor: HxsEncryptor,
) {
    private val storage = HexStorage()

    init {
        val dir = File(context.filesDir, SECURE_DIR).apply { mkdirs() }
        val base = dir.absolutePath

        val dek = keyStore.unwrapOrCreateDek()
        val signerHash = keyStore.installSignerHash()
        val userKey = encryptor.deriveKey(ikm = dek, salt = signerHash, info = USER_KEY_INFO)

        val opened = openOrRebuild(base, dek, userKey)
        if (!opened) throw SecurityException("Failed to open encrypted vlm_vision_cache vault")

        storage.ensureCollection(COLLECTION)
        storage.addIndex(COLLECTION, TAG_KEY, HexStorage.WIRE_BYTES)
    }

    fun isCached(modelTag: String, imageSha256: ByteArray): Boolean =
        findRecord(compositeKey(modelTag, imageSha256)) != null

    fun markCached(
        modelTag: String,
        imageSha256: ByteArray,
        sizeBytes: Long,
        imageMaxTokens: Int,
    ) {
        val key = compositeKey(modelTag, imageSha256)
        val now = System.currentTimeMillis().toString()
        val existing = findRecord(key)
        if (existing != null) {
            existing.putString(TAG_LAST_ACCESSED, now)
            storage.update(COLLECTION, existing)
        } else {
            val record = HxsRecord.build {
                putString(TAG_KEY, key)
                putString(TAG_MODEL_TAG, modelTag)
                putBytes(TAG_IMAGE_SHA256, imageSha256)
                putString(TAG_IMAGE_MAX_TOKENS, imageMaxTokens.toString())
                putString(TAG_SIZE_BYTES, sizeBytes.toString())
                putString(TAG_CREATED_AT, now)
                putString(TAG_LAST_ACCESSED, now)
            }
            storage.put(COLLECTION, record)
        }
        storage.flushAll()
    }

    fun touch(modelTag: String, imageSha256: ByteArray) {
        val key = compositeKey(modelTag, imageSha256)
        val existing = findRecord(key) ?: return
        existing.putString(TAG_LAST_ACCESSED, System.currentTimeMillis().toString())
        storage.update(COLLECTION, existing)
        storage.flushAll()
    }

    fun remove(modelTag: String, imageSha256: ByteArray) {
        val existing = findRecord(compositeKey(modelTag, imageSha256)) ?: return
        storage.delete(COLLECTION, existing.id)
        storage.flushAll()
    }

    fun stats(): Stats {
        val all = runCatching { storage.getAll(COLLECTION) }.getOrNull().orEmpty()
        val totalBytes = all.sumOf { it.getString(TAG_SIZE_BYTES, "0").toLongOrNull() ?: 0L }
        return Stats(entryCount = all.size, totalBytes = totalBytes)
    }

    private fun compositeKey(modelTag: String, imageSha256: ByteArray): String =
        modelTag + "::" + imageSha256.toHex()

    private fun findRecord(key: String): HxsRecord? =
        storage.queryString(COLLECTION, TAG_KEY, key).firstOrNull()

    private fun openOrRebuild(base: String, dek: ByteArray, userKey: ByteArray): Boolean {
        if (storage.exists(base)) {
            if (storage.openEncrypted(base, dek, userKey, encryptor)) return true
            File(base).deleteRecursively()
            File(base).mkdirs()
        }
        return storage.createEncrypted(base, dek, userKey, encryptor)
    }

    data class Stats(val entryCount: Int, val totalBytes: Long)

    companion object {
        private const val TAG = "VlmVisionCacheRepo"
        private const val COLLECTION = "vlm_vision_cache"
        private const val SECURE_DIR = "vlm_vision_cache_v1"
        private const val USER_KEY_INFO = "tn.vlm_vision_cache.user_key.v2"

        private const val TAG_KEY = 1
        private const val TAG_MODEL_TAG = 2
        private const val TAG_IMAGE_SHA256 = 3
        private const val TAG_IMAGE_MAX_TOKENS = 4
        private const val TAG_SIZE_BYTES = 5
        private const val TAG_CREATED_AT = 6
        private const val TAG_LAST_ACCESSED = 7

        fun sha256(bytes: ByteArray): ByteArray =
            MessageDigest.getInstance("SHA-256").digest(bytes)

        private fun ByteArray.toHex(): String =
            joinToString(separator = "") { "%02x".format(it) }
    }
}
