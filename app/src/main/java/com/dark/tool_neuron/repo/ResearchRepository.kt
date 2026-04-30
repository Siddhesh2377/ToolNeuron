package com.dark.tool_neuron.repo

import android.content.Context
import com.dark.hxs.HexStorage
import com.dark.hxs.HxsRecord
import com.dark.hxs_encryptor.HxsEncryptor
import com.dark.tool_neuron.data.AppKeyStore
import com.dark.tool_neuron.model.ResearchDocument
import com.dark.tool_neuron.model.ResearchPhase
import com.dark.tool_neuron.model.StructuredDoc
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResearchRepository @Inject constructor(
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
        if (!opened) throw SecurityException("Failed to open research vault")

        storage.ensureCollection(COL_DOCS)
        storage.ensureCollection(COL_RUNS)
        storage.addIndex(COL_DOCS, TAG_DOC_ID, HexStorage.WIRE_BYTES)
        storage.addIndex(COL_DOCS, TAG_DOC_ORIGIN_CHAT, HexStorage.WIRE_BYTES)
        storage.addIndex(COL_RUNS, TAG_RUN_ID, HexStorage.WIRE_BYTES)
    }

    private fun openOrRebuild(base: String, dek: ByteArray, userKey: ByteArray): Boolean {
        if (storage.exists(base)) {
            if (storage.openEncrypted(base, dek, userKey, encryptor)) return true
            File(base).deleteRecursively()
            File(base).mkdirs()
        }
        return storage.createEncrypted(base, dek, userKey, encryptor)
    }

    fun saveDocument(doc: ResearchDocument) {
        storage.queryString(COL_DOCS, TAG_DOC_ID, doc.docId).forEach {
            storage.delete(COL_DOCS, it.id)
        }
        storage.put(COL_DOCS, doc.toRecord())
        storage.flush(COL_DOCS)
    }

    fun getDocument(docId: String): ResearchDocument? =
        storage.queryString(COL_DOCS, TAG_DOC_ID, docId).firstOrNull()?.toResearchDocument()

    fun allDocuments(): List<ResearchDocument> =
        storage.getAll(COL_DOCS).map { it.toResearchDocument() }.sortedByDescending { it.createdAt }

    fun deleteDocument(docId: String) {
        storage.queryString(COL_DOCS, TAG_DOC_ID, docId).forEach {
            storage.delete(COL_DOCS, it.id)
        }
        storage.flush(COL_DOCS)
    }

    fun saveRunSnapshot(
        runId: String,
        question: String,
        phase: ResearchPhase,
        startedAt: Long,
        finishedAt: Long? = null,
        docId: String? = null,
        cancelReason: String? = null,
        iterationLogJson: String = "[]",
    ) {
        storage.queryString(COL_RUNS, TAG_RUN_ID, runId).forEach {
            storage.delete(COL_RUNS, it.id)
        }
        val record = HxsRecord.build {
            putString(TAG_RUN_ID, runId)
            docId?.takeIf { it.isNotBlank() }?.let { putString(TAG_RUN_DOC_ID, it) }
            putString(TAG_RUN_QUESTION, question)
            putString(TAG_RUN_PHASE, phase.name)
            putString(TAG_RUN_ITER_LOG, iterationLogJson)
            putTimestamp(TAG_RUN_STARTED_AT, startedAt)
            finishedAt?.let { putTimestamp(TAG_RUN_FINISHED_AT, it) }
            cancelReason?.takeIf { it.isNotBlank() }?.let { putString(TAG_RUN_CANCEL_REASON, it) }
        }
        storage.put(COL_RUNS, record)
        storage.flush(COL_RUNS)
    }

    fun getRunPhase(runId: String): ResearchPhase {
        val rec = storage.queryString(COL_RUNS, TAG_RUN_ID, runId).firstOrNull()
            ?: return ResearchPhase.Idle
        val name = rec.getString(TAG_RUN_PHASE)
        return runCatching { ResearchPhase.valueOf(name) }.getOrDefault(ResearchPhase.Idle)
    }

    fun deleteRun(runId: String) {
        storage.queryString(COL_RUNS, TAG_RUN_ID, runId).forEach {
            storage.delete(COL_RUNS, it.id)
        }
        storage.flush(COL_RUNS)
    }

    private fun ResearchDocument.toRecord(): HxsRecord {
        val d = this
        return HxsRecord.build {
            putString(TAG_DOC_ID, d.docId)
            putString(TAG_DOC_TITLE, d.title)
            putString(TAG_DOC_ORIGIN_CHAT, d.originChatId)
            putString(TAG_DOC_ORIGIN_MSG, d.originMessageId)
            putString(TAG_DOC_QUESTION, d.question)
            putString(TAG_DOC_STRUCT_JSON, d.structured.toJson())
            putTimestamp(TAG_DOC_CREATED_AT, d.createdAt)
            putTimestamp(TAG_DOC_DURATION, d.durationMs)
            putString(TAG_DOC_MODEL_ID, d.modelId)
            putInt(TAG_DOC_ITERATIONS, d.iterationsUsed.toLong())
        }
    }

    private fun HxsRecord.toResearchDocument(): ResearchDocument = ResearchDocument(
        docId = getString(TAG_DOC_ID),
        title = getString(TAG_DOC_TITLE),
        originChatId = getString(TAG_DOC_ORIGIN_CHAT),
        originMessageId = getString(TAG_DOC_ORIGIN_MSG),
        question = getString(TAG_DOC_QUESTION),
        structured = StructuredDoc.fromJson(getString(TAG_DOC_STRUCT_JSON)),
        createdAt = getTimestamp(TAG_DOC_CREATED_AT),
        durationMs = getTimestamp(TAG_DOC_DURATION),
        modelId = getString(TAG_DOC_MODEL_ID),
        iterationsUsed = getInt(TAG_DOC_ITERATIONS, 0L).toInt(),
    )

    companion object {
        private const val SECURE_DIR = "research_v1"
        private const val USER_KEY_INFO = "tn.research.user_key.v2"

        private const val COL_DOCS = "research_documents"
        private const val COL_RUNS = "research_runs"

        private const val TAG_DOC_ID = 1
        private const val TAG_DOC_TITLE = 2
        private const val TAG_DOC_ORIGIN_CHAT = 3
        private const val TAG_DOC_ORIGIN_MSG = 4
        private const val TAG_DOC_QUESTION = 5
        private const val TAG_DOC_STRUCT_JSON = 6
        private const val TAG_DOC_CREATED_AT = 7
        private const val TAG_DOC_DURATION = 8
        private const val TAG_DOC_MODEL_ID = 9
        private const val TAG_DOC_ITERATIONS = 10

        private const val TAG_RUN_ID = 1
        private const val TAG_RUN_DOC_ID = 2
        private const val TAG_RUN_QUESTION = 3
        private const val TAG_RUN_PHASE = 4
        private const val TAG_RUN_ITER_LOG = 5
        private const val TAG_RUN_STARTED_AT = 6
        private const val TAG_RUN_FINISHED_AT = 7
        private const val TAG_RUN_CANCEL_REASON = 8
    }
}
