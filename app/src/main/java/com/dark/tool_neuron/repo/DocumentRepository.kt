package com.dark.tool_neuron.repo

import android.content.Context
import android.util.Log
import com.dark.hxs.HexStorage
import com.dark.hxs.HxsRecord
import com.dark.hxs_encryptor.HxsEncryptor
import com.dark.tool_neuron.data.AppKeyStore
import com.dark.tool_neuron.model.ChatDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val keyStore: AppKeyStore,
    private val encryptor: HxsEncryptor,
) {
    private val storage = HexStorage()

    init {
        val secureDir = File(context.filesDir, SECURE_DIR).apply { mkdirs() }
        val secureBase = secureDir.absolutePath

        val dek = keyStore.unwrapOrCreateDek()
        val signerHash = keyStore.installSignerHash()
        val userKey = encryptor.deriveKey(ikm = dek, salt = signerHash, info = USER_KEY_INFO)

        val opened = openOrRebuild(secureBase, dek, userKey)
        if (!opened) throw SecurityException("Failed to open encrypted chat_documents vault")

        storage.ensureCollection(COLLECTION)
        storage.addIndex(COLLECTION, TAG_ID, HexStorage.WIRE_BYTES)
        storage.addIndex(COLLECTION, TAG_CHAT_ID, HexStorage.WIRE_BYTES)

        migrateLegacyPlaintextIfNeeded()
    }

    private fun openOrRebuild(base: String, dek: ByteArray, userKey: ByteArray): Boolean {
        if (storage.exists(base)) {
            if (storage.openEncrypted(base, dek, userKey, encryptor)) return true
            File(base).deleteRecursively()
            File(base).mkdirs()
        }
        return storage.createEncrypted(base, dek, userKey, encryptor)
    }

    private fun migrateLegacyPlaintextIfNeeded() {
        val legacyDir = File(context.filesDir, LEGACY_DIR)
        if (!legacyDir.isDirectory) return
        val legacyTopFiles = legacyDir.listFiles { f -> f.isFile } ?: return
        if (legacyTopFiles.isEmpty()) return

        val legacy = HexStorage()
        try {
            if (!legacy.exists(legacyDir.absolutePath)) return
            if (!legacy.openPlaintext(legacyDir.absolutePath)) return

            val records = runCatching { legacy.getAll(COLLECTION) }.getOrNull().orEmpty()
            if (records.isEmpty()) {
                legacyTopFiles.forEach { runCatching { it.delete() } }
                return
            }

            var migrated = 0
            records.forEach { rec ->
                val doc = runCatching { rec.toChatDocument() }.getOrNull() ?: return@forEach
                runCatching { addDocument(doc) }.onSuccess { migrated++ }
            }
            Log.i(TAG, "Migrated $migrated/${records.size} legacy doc records")
            if (migrated == records.size) {
                legacyTopFiles.forEach { runCatching { it.delete() } }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Legacy migration failed", t)
        } finally {
            runCatching { legacy.close() }
        }
    }

    fun getDocumentsForChat(chatId: String): List<ChatDocument> {
        return storage.queryString(COLLECTION, TAG_CHAT_ID, chatId).map { it.toChatDocument() }
    }

    fun getAllDocuments(): List<ChatDocument> {
        return storage.getAll(COLLECTION).map { it.toChatDocument() }
    }

    fun addDocument(doc: ChatDocument) {
        storage.queryString(COLLECTION, TAG_ID, doc.id).forEach {
            storage.delete(COLLECTION, it.id)
        }
        storage.put(COLLECTION, doc.toRecord())
        storage.flush(COLLECTION)
    }

    fun updateDocument(doc: ChatDocument) {
        addDocument(doc)
    }

    fun getDocument(docId: String): ChatDocument? =
        storage.queryString(COLLECTION, TAG_ID, docId).firstOrNull()?.toChatDocument()

    fun removeDocument(docId: String) {
        val records = storage.queryString(COLLECTION, TAG_ID, docId)
        records.forEach { storage.delete(COLLECTION, it.id) }
        storage.flush(COLLECTION)
    }

    fun removeAllForChat(chatId: String) {
        val records = storage.queryString(COLLECTION, TAG_CHAT_ID, chatId)
        records.forEach { storage.delete(COLLECTION, it.id) }
        storage.flush(COLLECTION)
    }

    fun clearAll() {
        val records = storage.getAll(COLLECTION)
        records.forEach { storage.delete(COLLECTION, it.id) }
        storage.flush(COLLECTION)
    }

    fun countWithSource(sourceId: String): Int {
        if (sourceId.isBlank()) return 0
        return storage.getAll(COLLECTION).count { rec ->
            rec.getString(TAG_SOURCE_ID) == sourceId
        }
    }

    companion object {
        private const val TAG = "DocumentRepository"
        private const val COLLECTION = "chat_documents"

        private const val SECURE_DIR = "chat_documents_meta_v1"
        private const val LEGACY_DIR = "chat_documents"

        private const val USER_KEY_INFO = "tn.chat_documents.user_key.v2"

        private const val TAG_ID = 1
        private const val TAG_CHAT_ID = 2
        private const val TAG_NAME = 3
        private const val TAG_MIME_TYPE = 4
        private const val TAG_CHUNK_COUNT = 5
        private const val TAG_SIZE_BYTES = 6
        private const val TAG_ADDED_AT = 7
        private const val TAG_SOURCE_ID = 8
        private const val TAG_IS_DEEP_INDEXED = 9
        private const val TAG_IS_RAPTOR_INDEXED = 10
    }

    private fun ChatDocument.toRecord(): HxsRecord {
        val d = this
        return HxsRecord.build {
            putString(TAG_ID, d.id)
            putString(TAG_CHAT_ID, d.chatId ?: "")
            putString(TAG_NAME, d.name)
            putString(TAG_MIME_TYPE, d.mimeType)
            putTimestamp(TAG_CHUNK_COUNT, d.chunkCount.toLong())
            putTimestamp(TAG_SIZE_BYTES, d.sizeBytes)
            putTimestamp(TAG_ADDED_AT, d.addedAt)
            putString(TAG_SOURCE_ID, d.sourceId)
            putBool(TAG_IS_DEEP_INDEXED, d.isDeepIndexed)
            putBool(TAG_IS_RAPTOR_INDEXED, d.isRaptorIndexed)
        }
    }

    private fun HxsRecord.toChatDocument(): ChatDocument = ChatDocument(
        id = getString(TAG_ID),
        chatId = getString(TAG_CHAT_ID),
        name = getString(TAG_NAME),
        mimeType = getString(TAG_MIME_TYPE),
        chunkCount = getTimestamp(TAG_CHUNK_COUNT).toInt(),
        sizeBytes = getTimestamp(TAG_SIZE_BYTES),
        addedAt = getTimestamp(TAG_ADDED_AT),
        sourceId = getString(TAG_SOURCE_ID),
        isDeepIndexed = getBool(TAG_IS_DEEP_INDEXED, false),
        isRaptorIndexed = getBool(TAG_IS_RAPTOR_INDEXED, false),
    )
}
