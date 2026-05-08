package com.dark.tool_neuron.repo

import android.content.Context
import com.dark.hxs.HexStorage
import com.dark.hxs_encryptor.HxsEncryptor
import com.dark.tool_neuron.data.AppKeyStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class KeywordHit(
    val docId: String,
    val chatId: String,
    val sourceId: String,
    val chunkIndex: Int,
    val text: String,
    val rank: Double,
)

@Singleton
class RagKeywordIndex @Inject constructor(
    @ApplicationContext context: Context,
    keyStore: AppKeyStore,
    encryptor: HxsEncryptor,
) {

    private val storage = HexStorage()

    private val encryptorRef = encryptor

    init {
        val dir = File(context.filesDir, SECURE_DIR).apply { mkdirs() }
        val basePath = dir.absolutePath

        val dek = keyStore.unwrapOrCreateDek()
        val signerHash = keyStore.installSignerHash()
        val userKey = encryptor.deriveKey(ikm = dek, salt = signerHash, info = USER_KEY_INFO)

        val opened = openOrRebuild(basePath, dek, userKey)
        if (!opened) throw SecurityException("Failed to open encrypted rag_keyword vault")

        storage.ensureCollection(COLLECTION)
    }

    private fun openOrRebuild(base: String, dek: ByteArray, userKey: ByteArray): Boolean {
        if (storage.exists(base)) {
            if (storage.openEncrypted(base, dek, userKey, encryptorRef)) return true
            File(base).deleteRecursively()
            File(base).mkdirs()
        }
        return storage.createEncrypted(base, dek, userKey, encryptorRef)
    }

    fun ingest(
        docId: String,
        chatId: String,
        sourceId: String,
        chunks: List<String>,
    ): Int {
        if (chunks.isEmpty()) return 0
        var inserted = 0
        chunks.forEachIndexed { idx, text ->
            if (text.isBlank()) return@forEachIndexed
            val r = storage.ragIngest(COLLECTION, docId, chatId, sourceId, idx, text)
            if (r > 0) inserted++
        }
        if (inserted > 0) storage.flush(COLLECTION)
        return inserted
    }

    fun query(query: String, chatId: String, topK: Int): List<KeywordHit> {
        if (query.isBlank() || topK <= 0) return emptyList()
        val records = storage.ragQuery(COLLECTION, query, chatId, topK)
        return records.map { rec ->
            KeywordHit(
                docId = rec.getString(TAG_DOC_ID),
                chatId = rec.getString(TAG_CHAT_ID),
                sourceId = rec.getString(TAG_SOURCE_ID),
                chunkIndex = rec.getInt(TAG_CHUNK_INDEX, 0).toInt(),
                text = rec.getString(TAG_TEXT),
                rank = rec.getDouble(TAG_SCORE, 0.0),
            )
        }
    }

    fun removeDocument(docId: String) {
        val n = storage.ragRemoveDocument(COLLECTION, docId)
        if (n > 0) storage.flush(COLLECTION)
    }

    fun removeChat(chatId: String) {
        val records = storage.queryString(COLLECTION, TAG_CHAT_ID, chatId)
        val docIds = records.map { it.getString(TAG_DOC_ID) }.toSet()
        docIds.forEach { storage.ragRemoveDocument(COLLECTION, it) }
        if (docIds.isNotEmpty()) storage.flush(COLLECTION)
    }

    fun clearAll() {
        storage.ragClear(COLLECTION)
        storage.flush(COLLECTION)
    }

    fun docCount(docId: String): Int = storage.ragDocCount(COLLECTION, docId)

    companion object {
        private const val SECURE_DIR = "rag_keyword_v1"
        private const val COLLECTION = "rag_chunks"
        private const val USER_KEY_INFO = "tn.rag_keyword.user_key.v2"

        private const val TAG_DOC_ID = 1
        private const val TAG_CHAT_ID = 2
        private const val TAG_SOURCE_ID = 3
        private const val TAG_CHUNK_INDEX = 4
        private const val TAG_TEXT = 5
        private const val TAG_SCORE = 6
    }
}
