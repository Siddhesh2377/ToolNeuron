package com.dark.tool_neuron.repo

import android.content.Context
import android.net.Uri
import android.util.Log
import com.dark.tn_security.TnCode
import com.dark.tn_security.TnModule
import com.dark.tn_security.TnSecurity
import com.dark.tn_security.TnStage
import com.dark.tool_neuron.data.AppPreferences
import com.dark.tool_neuron.model.ChatDocument
import com.dark.tool_neuron.model.ModelInfo
import com.dark.tool_neuron.model.enums.ProviderType
import com.dark.tool_neuron.service.inference.InferenceClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RagManager"

@Singleton
class RagManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentRepo: DocumentRepository,
    private val modelRepo: ModelRepository,
    private val ragPrefs: RagPreferences,
    private val keywordIndex: RagKeywordIndex,
    private val reranker: RagReranker,
    private val queryRewriter: RagQueryRewriter,
    private val docSummarizer: RagDocSummarizer,
    private val raptor: RagRaptor,
    private val appPrefs: AppPreferences,
    private val sourceVault: SourceFileVault,
) {
    private val opsMutex = Mutex()
    private val readyMutex = Mutex()
    private val raptorMutex = Mutex()
    private val ingestedDocIds = mutableSetOf<String>()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _activeEmbeddingName = MutableStateFlow<String?>(null)
    val activeEmbeddingName: StateFlow<String?> = _activeEmbeddingName.asStateFlow()

    private val _retrievalStatus = MutableStateFlow<RetrievalStatus>(RetrievalStatus.Idle)
    val retrievalStatus: StateFlow<RetrievalStatus> = _retrievalStatus.asStateFlow()

    sealed interface RetrievalStatus {
        data object Idle : RetrievalStatus
        data object RewritingQuery : RetrievalStatus
        data object Searching : RetrievalStatus
        data object Reranking : RetrievalStatus
    }

    private val _deepIndexing = MutableStateFlow<Set<String>>(emptySet())
    val deepIndexing: StateFlow<Set<String>> = _deepIndexing.asStateFlow()

    private val _raptorBuilding = MutableStateFlow<Set<String>>(emptySet())
    val raptorBuilding: StateFlow<Set<String>> = _raptorBuilding.asStateFlow()

    val defaultEmbeddingModelId: StateFlow<String?> = ragPrefs.defaultEmbeddingModelId

    fun hasEmbeddingModelInstalled(): Boolean =
        modelRepo.models.value.any { it.providerType == ProviderType.EMBEDDING }

    fun documentsForChat(chatId: String): List<ChatDocument> =
        documentRepo.getDocumentsForChat(chatId)

    fun allDocuments(): List<ChatDocument> = documentRepo.getAllDocuments()

    fun setDefaultEmbeddingModelId(modelId: String?) {
        ragPrefs.setDefaultEmbeddingModelId(modelId)
    }

    suspend fun ensureReady(): Boolean = readyMutex.withLock {
        if (_isReady.value) return@withLock true

        val created = InferenceClient.ragEnsureReady(
            threads = 0,
            chunkSize = 256,
            chunkOverlap = 32,
            dims = 256,
            topK = 64,
            topN = DENSE_CANDIDATES,
            lateChunking = true,
        )
        if (!created) {
            Log.e(TAG, "Failed to create RAG engine in :inference")
            TnSecurity.error(
                code = TnCode.MODEL_LOAD_FAIL,
                stage = TnStage.INIT,
                module = TnModule.TN_APP,
                message = "RAG engine create() returned false in :inference",
                suggestion = "Check :inference service is bound and RAGEngine.create() native call is reachable",
            )
            return@withLock false
        }

        val model = pickEmbeddingModel() ?: run {
            Log.w(TAG, "No embedding model installed")
            TnSecurity.error(
                code = TnCode.MODEL_LOAD_FAIL,
                stage = TnStage.INIT,
                module = TnModule.TN_APP,
                message = "No embedding model installed; RAG cannot start",
                suggestion = "Install EmbeddingGemma (or any embedding GGUF) from Model Store",
            )
            return@withLock false
        }

        val loaded = InferenceClient.ragLoadEmbeddingModel(model.path)
        if (!loaded) {
            Log.e(TAG, "Failed to load embedding model at ${model.path}")
            TnSecurity.error(
                code = TnCode.MODEL_LOAD_FAIL,
                stage = TnStage.LOAD,
                module = TnModule.TN_APP,
                message = "Failed to load embedding model at ${model.path}",
                suggestion = "Verify the file exists, isn't truncated, and matches the dims expected by the RAG index",
            )
            return@withLock false
        }

        _activeEmbeddingName.value = model.name
        _isReady.value = true

        runCatching { restoreIndexSnapshot() }.onFailure { err ->
            Log.w(TAG, "vector snapshot restore failed", err)
        }

        Log.i(TAG, "RAG ready with model: ${model.name}")
        true
    }

    private val snapshotFile: File by lazy {
        File(context.filesDir, "rag_vector_snapshot_v1.bin")
    }

    private suspend fun restoreIndexSnapshot() = withContext(Dispatchers.IO) {
        val f = snapshotFile
        if (!f.exists() || f.length() <= 0) return@withContext
        val bytes = runCatching { f.readBytes() }.getOrNull() ?: return@withContext
        val rc = opsMutex.withLock { InferenceClient.ragImportIndex(bytes) }
        if (rc == 0) {
            documentRepo.getAllDocuments().forEach { doc ->
                ingestedDocIds += doc.id
                if (doc.isRaptorIndexed) {
                    documentRepo.updateDocument(doc.copy(isRaptorIndexed = false))
                }
            }
            Log.i(TAG, "vector snapshot restored: ${bytes.size / 1024} KB, ${ingestedDocIds.size} docs")
        } else {
            Log.w(TAG, "vector snapshot import failed (rc=$rc) — deleting and re-embedding on demand")
            TnSecurity.error(
                code = TnCode.UNKNOWN,
                stage = TnStage.RAG_INGEST,
                module = TnModule.TN_APP,
                message = "vector snapshot import failed rc=$rc; will re-embed on demand",
                suggestion = "rc -1=magic, -2=version, -3=dim, -4=model fingerprint, -5=corrupt, -6=engine not ready",
            )
            runCatching { f.delete() }
        }
    }

    suspend fun saveIndexSnapshot() = withContext(Dispatchers.IO) {
        if (!_isReady.value) return@withContext
        val bytes = opsMutex.withLock { InferenceClient.ragExportIndex() } ?: return@withContext
        val tmp = File(snapshotFile.parentFile, snapshotFile.name + ".tmp")
        runCatching {
            tmp.outputStream().use { it.write(bytes) }
            if (!tmp.renameTo(snapshotFile)) {
                tmp.copyTo(snapshotFile, overwrite = true)
                tmp.delete()
            }
            Log.i(TAG, "vector snapshot saved: ${bytes.size / 1024} KB")
        }.onFailure { err ->
            Log.w(TAG, "vector snapshot save failed", err)
            runCatching { tmp.delete() }
        }
    }

    private fun deleteIndexSnapshot() {
        runCatching { snapshotFile.delete() }
    }

    private fun pickEmbeddingModel(): ModelInfo? {
        val installed = modelRepo.models.value.filter { it.providerType == ProviderType.EMBEDDING }
        if (installed.isEmpty()) return null
        val preferredId = ragPrefs.defaultEmbeddingModelId.value
        return installed.firstOrNull { it.id == preferredId } ?: installed.first()
    }

    suspend fun hydrateChat(chatId: String): List<ChatDocument> = withContext(Dispatchers.IO) {
        val records = documentRepo.getDocumentsForChat(chatId)
        if (records.isEmpty()) return@withContext emptyList()
        if (!ensureReady()) return@withContext records

        records.forEach { doc ->
            if (doc.id in ingestedDocIds) return@forEach
            if (doc.sourceId.isBlank()) return@forEach
            val bytes = sourceVault.read(doc.sourceId) ?: return@forEach
            val chunks = ingestBytesWithFallback(bytes, doc.mimeType, doc.name, doc.id)
            if (chunks >= 0) {
                ingestedDocIds += doc.id
                indexKeywordsIfTextLike(doc.id, chatId, doc.sourceId, doc.name, doc.mimeType, bytes)
                if (doc.isRaptorIndexed) {
                    documentRepo.updateDocument(doc.copy(isRaptorIndexed = false))
                }
            } else {
                Log.w(TAG, "hydrate failed for ${doc.id}: code=$chunks")
            }
        }
        records
    }

    suspend fun ingestDocument(
        chatId: String,
        uri: Uri,
        displayName: String,
        size: Long,
        mimeType: String?,
    ): Result<ChatDocument> = withContext(Dispatchers.IO) {
        if (!ensureReady()) {
            return@withContext Result.failure(
                IllegalStateException("Embedding model not loaded. Install EmbeddingGemma from Model Store.")
            )
        }

        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
            ?: return@withContext Result.failure(IllegalStateException("Could not read document"))

        if (bytes.isEmpty()) return@withContext Result.failure(IllegalStateException("Document is empty"))

        val effectiveMime = mimeType ?: context.contentResolver.getType(uri)
        val sourceId = sha256Hex(bytes)
        val docId = "$chatId:$sourceId"

        val existing = documentRepo.getDocumentsForChat(chatId).firstOrNull { it.id == docId }
        if (existing != null) return@withContext Result.success(existing)

        if (!sourceVault.write(sourceId, bytes)) {
            return@withContext Result.failure(IllegalStateException("Could not store document bytes"))
        }

        val chunkCount = ingestBytesWithFallback(bytes, effectiveMime, displayName, docId)

        if (chunkCount < 0) {
            val reason = when (chunkCount) {
                -1 -> "Unsupported document format"
                -2 -> "Could not parse document"
                -3 -> "Document contains no readable text"
                -4 -> "Out of memory during ingest"
                -6 -> "RAG engine not ready in :inference"
                else -> "Indexing failed (code $chunkCount)"
            }
            TnSecurity.error(
                code = if (chunkCount == -4) TnCode.OOM else TnCode.DECODE_FAIL,
                stage = TnStage.RAG_INGEST,
                module = TnModule.TN_APP,
                message = "ingestBytes $displayName: $reason (rc=$chunkCount)",
                suggestion = null,
            )
            return@withContext Result.failure(IllegalStateException(reason))
        }

        ingestedDocIds += docId
        val resolvedMime = effectiveMime ?: "application/octet-stream"
        indexKeywordsIfTextLike(docId, chatId, sourceId, displayName, resolvedMime, bytes)
        val doc = ChatDocument(
            id = docId,
            chatId = chatId,
            sourceId = sourceId,
            name = displayName,
            mimeType = resolvedMime,
            chunkCount = chunkCount,
            sizeBytes = size,
        )
        documentRepo.addDocument(doc)
        saveIndexSnapshot()
        Result.success(doc)
    }

    suspend fun attachExisting(
        currentChatId: String,
        source: ChatDocument,
    ): Result<ChatDocument> = withContext(Dispatchers.IO) {
        if (source.sourceId.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Source document is unavailable"))
        }
        if (!ensureReady()) {
            return@withContext Result.failure(
                IllegalStateException("Embedding model not loaded. Install EmbeddingGemma from Model Store.")
            )
        }
        if (!sourceVault.exists(source.sourceId)) {
            return@withContext Result.failure(IllegalStateException("Source bytes for ${source.name} are missing"))
        }
        val docId = "$currentChatId:${source.sourceId}"
        documentRepo.getDocumentsForChat(currentChatId).firstOrNull { it.id == docId }?.let {
            return@withContext Result.success(it)
        }
        val bytes = sourceVault.read(source.sourceId)
            ?: return@withContext Result.failure(IllegalStateException("Could not read stored bytes"))

        val chunkCount = ingestBytesWithFallback(bytes, source.mimeType, source.name, docId)
        if (chunkCount < 0) {
            return@withContext Result.failure(IllegalStateException("Indexing failed (code $chunkCount)"))
        }
        ingestedDocIds += docId
        indexKeywordsIfTextLike(docId, currentChatId, source.sourceId, source.name, source.mimeType, bytes)
        val doc = ChatDocument(
            id = docId,
            chatId = currentChatId,
            sourceId = source.sourceId,
            name = source.name,
            mimeType = source.mimeType,
            chunkCount = chunkCount,
            sizeBytes = source.sizeBytes,
        )
        documentRepo.addDocument(doc)
        saveIndexSnapshot()
        Result.success(doc)
    }

    suspend fun deepIndex(docId: String): Result<ChatDocument> = withContext(Dispatchers.IO) {
        val doc = documentRepo.getDocument(docId)
            ?: return@withContext Result.failure(IllegalStateException("Document not found"))
        if (doc.isDeepIndexed) return@withContext Result.success(doc)
        if (!isTextLike(doc.mimeType, doc.name)) {
            return@withContext Result.failure(IllegalStateException("Deep Index only supports text-format documents (txt, md, json, code, etc.)"))
        }
        if (doc.sourceId.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Source bytes missing"))
        }
        if (!sourceVault.exists(doc.sourceId)) {
            return@withContext Result.failure(IllegalStateException("Source file missing on disk"))
        }
        if (!ensureReady()) {
            return@withContext Result.failure(IllegalStateException("RAG engine not ready"))
        }

        val bytes = sourceVault.read(doc.sourceId)
            ?: return@withContext Result.failure(IllegalStateException("Failed to read source"))
            val text = extractReadableText(bytes, doc.mimeType, doc.name)
                ?: return@withContext Result.failure(IllegalStateException("Source contains no text"))

        _deepIndexing.value = _deepIndexing.value + docId
        try {
            val summary = docSummarizer.summarize(doc.name, doc.mimeType, text)
                ?: return@withContext Result.failure(IllegalStateException("Could not generate summary — chat model may be busy"))

            val chunks = RagChunker.chunk(text)
            if (chunks.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("No chunks produced"))
            }

            val ctxPrefix = buildString {
                append("[Document context: ")
                append(doc.name)
                append(" — ")
                append(summary.replace("\n", " ").trim())
                append("]\n\n")
            }

            chunks.forEachIndexed { idx, chunk ->
                val subDocId = "$docId::ctx$idx"
                val combined = ctxPrefix + chunk
                val combinedBytes = combined.toByteArray(Charsets.UTF_8)
                val n = opsMutex.withLock {
                    InferenceClient.ragIngestBytes(combinedBytes, "text/plain", "${doc.name} (ctx$idx)", subDocId)
                }
                if (n >= 0) ingestedDocIds += subDocId
                keywordIndex.ingest(subDocId, doc.chatId.orEmpty(), doc.sourceId, listOf(combined))
            }

            val updated = doc.copy(isDeepIndexed = true, chunkCount = doc.chunkCount + chunks.size)
            documentRepo.updateDocument(updated)
            saveIndexSnapshot()
            Log.i(TAG, "deepIndex ${doc.name}: ${chunks.size} contextual chunks added")
            Result.success(updated)
        } finally {
            _deepIndexing.value = _deepIndexing.value - docId
        }
    }

    suspend fun buildRaptorTree(docId: String): Result<ChatDocument> = raptorMutex.withLock {
        val doc = documentRepo.getDocument(docId)
            ?: return@withLock Result.failure(IllegalStateException("Document not found"))
        if (doc.isRaptorIndexed) return@withLock Result.success(doc)
        if (doc.sourceId.isBlank()) return@withLock Result.failure(IllegalStateException("Document source unavailable"))
        if (!isTextLike(doc.mimeType, doc.name)) {
            return@withLock Result.failure(IllegalStateException("RAPTOR currently supports text-format documents only"))
        }
        if (!ensureReady()) {
            return@withLock Result.failure(IllegalStateException("Embedding model not loaded. Install EmbeddingGemma from Model Store."))
        }

        _raptorBuilding.value = _raptorBuilding.value + docId
        try {
            val bytes = withContext(Dispatchers.IO) { sourceVault.read(doc.sourceId) }
                ?: return@withLock Result.failure(IllegalStateException("Source bytes for ${doc.name} are missing"))
            val text = extractReadableText(bytes, doc.mimeType, doc.name)
                ?: return@withLock Result.failure(IllegalStateException("Could not read source text"))

            val tree = raptor.buildTree(text, doc.name) { _, _, _ -> }
                ?: return@withLock Result.failure(IllegalStateException("Tree build failed (load a chat model and try again)"))

            tree.levels.forEachIndexed { levelIdx, nodes ->
                if (levelIdx == 0) return@forEachIndexed
                nodes.forEachIndexed { clusterIdx, summary ->
                    val subDocId = "$docId::raptor:lvl$levelIdx:cluster$clusterIdx"
                    val bytes = summary.toByteArray(Charsets.UTF_8)
                    val chunks = opsMutex.withLock {
                        InferenceClient.ragIngestBytes(bytes, "text/plain", "${doc.name} (lvl$levelIdx)", subDocId)
                    }
                    if (chunks >= 0) {
                        ingestedDocIds += subDocId
                        keywordIndex.ingest(subDocId, doc.chatId.orEmpty(), doc.sourceId, listOf(summary))
                    } else {
                        Log.w(TAG, "raptor ingest failed for $subDocId: code=$chunks")
                    }
                }
            }

            val updated = doc.copy(isRaptorIndexed = true)
            documentRepo.updateDocument(updated)
            saveIndexSnapshot()
            Log.i(TAG, "raptor ${doc.name}: ${tree.levels.size - 1} summary level(s) built")
            Result.success(updated)
        } finally {
            _raptorBuilding.value = _raptorBuilding.value - docId
        }
    }

    suspend fun removeDocument(docId: String) = withContext(Dispatchers.IO) {
        opsMutex.withLock { InferenceClient.ragRemoveDocument(docId) }
        ingestedDocIds -= docId
        keywordIndex.removeDocument(docId)
        val subIds = ingestedDocIds.filter {
            it.startsWith("$docId::ctx") || it.startsWith("$docId::raptor")
        }.toList()
        subIds.forEach { sub ->
            opsMutex.withLock { InferenceClient.ragRemoveDocument(sub) }
            ingestedDocIds -= sub
            keywordIndex.removeDocument(sub)
        }
        val doc = documentRepo.getAllDocuments().firstOrNull { it.id == docId }
        documentRepo.removeDocument(docId)
        val sourceId = doc?.sourceId
        if (!sourceId.isNullOrBlank() && documentRepo.countWithSource(sourceId) == 0) {
            sourceVault.delete(sourceId)
        }
        saveIndexSnapshot()
    }

    suspend fun buildAugmentedPrompt(
        chatId: String,
        query: String,
        originalPrompt: String,
        maxContextTokens: Int = DEFAULT_RAG_BUDGET_TOKENS,
    ): String = augment(chatId, query, originalPrompt, maxContextTokens).augmentedPrompt
        .ifEmpty { originalPrompt }

    suspend fun augment(
        chatId: String,
        query: String,
        originalPrompt: String,
        maxContextTokens: Int = DEFAULT_RAG_BUDGET_TOKENS,
    ): RagAugmentation = withContext(Dispatchers.IO) {
        if (!_isReady.value) return@withContext RagAugmentation.NONE

        try {
            val variants = if (appPrefs.ragMultiQuery) {
                _retrievalStatus.value = RetrievalStatus.RewritingQuery
                queryRewriter.generateVariants(query)
            } else emptyList()
            val queries = (listOf(query) + variants).distinct()

            _retrievalStatus.value = RetrievalStatus.Searching
            val rankings = mutableListOf<List<Pair<Pair<String, Int>, String>>>()
            var totalDense = 0
            var totalBm25 = 0
            val chatPrefix = "$chatId:"
            for (q in queries) {
                val dense = opsMutex.withLock { InferenceClient.ragQueryFiltered(q, chatPrefix) }
                val bm25 = runCatching { keywordIndex.query(q, chatId, topK = KEYWORD_CANDIDATES) }
                    .getOrElse { emptyList() }
                totalDense += dense.size
                totalBm25 += bm25.size
                if (dense.isNotEmpty()) rankings += dense.map { (it.docId to it.chunkIndex) to it.text }
                if (bm25.isNotEmpty()) rankings += bm25.map { (it.docId to it.chunkIndex) to it.text }
            }

            if (rankings.isEmpty()) return@withContext RagAugmentation.NONE

            val fused = rrfFuseMany(rankings)
            if (fused.isEmpty()) return@withContext RagAugmentation.NONE

            val docsByDocId = documentRepo.getDocumentsForChat(chatId).associateBy { it.id }
            val pooled = fused.mapNotNull { hit ->
                val text = hit.text.trim()
                if (text.isEmpty()) return@mapNotNull null
                val parentId = hit.docId.substringBefore("::ctx").substringBefore("::raptor")
                val doc = docsByDocId[parentId] ?: docsByDocId[hit.docId]
                RagChunk(
                    docId = parentId,
                    sourceId = doc?.sourceId ?: parentId.substringAfter(':', missingDelimiterValue = ""),
                    chunkIndex = hit.chunkIndex,
                    score = hit.rrfScore,
                    text = text,
                    name = doc?.name ?: "Document",
                    mimeType = doc?.mimeType ?: "application/octet-stream",
                )
            }
            if (pooled.isEmpty()) return@withContext RagAugmentation.NONE

            val ranked = if (appPrefs.ragSmartRerank) {
                _retrievalStatus.value = RetrievalStatus.Reranking
                reranker.rerank(query, pooled)
            } else pooled

            val budget = maxContextTokens.coerceAtLeast(MIN_RAG_BUDGET_TOKENS)
            val kept = mutableListOf<RagChunk>()
            var used = 0
            for (chunk in ranked) {
                val cost = approxTokens(chunk.text) + PER_CHUNK_OVERHEAD_TOKENS
                if (used + cost > budget) {
                    if (kept.isNotEmpty()) break
                    val available = (budget - PER_CHUNK_OVERHEAD_TOKENS).coerceAtLeast(64)
                    val ratio = available.toFloat() / approxTokens(chunk.text)
                    val truncCharCount = (chunk.text.length * ratio).toInt().coerceAtMost(chunk.text.length)
                    if (truncCharCount <= 0) break
                    kept += chunk.copy(text = chunk.text.take(truncCharCount))
                    used += approxTokens(kept.last().text) + PER_CHUNK_OVERHEAD_TOKENS
                    break
                }
                kept += chunk
                used += cost
                if (kept.size >= FINAL_TOP_N) break
            }
            if (kept.isEmpty()) return@withContext RagAugmentation.NONE
            Log.i(TAG, "RAG augment: queries=${queries.size} dense=$totalDense bm25=$totalBm25 fused=${fused.size} pooled=${pooled.size} kept=${kept.size} rerank=${appPrefs.ragSmartRerank} multiQ=${appPrefs.ragMultiQuery} (~$used / $budget tok)")

            val prompt = buildString {
                append("You have access to context retrieved from the user's documents. ")
                append("Write a complete answer to the user's question using your own words, integrating relevant facts from the passages below. ")
                append("Do not respond with only a name, a phrase, or a citation marker — give a full, useful answer of at least one sentence. ")
                append("Cite passages inline using [1], [2], etc. that correspond to the bracketed numbers. ")
                append("If the context is insufficient, fall back to your general knowledge and answer without citing.\n\n")
                append("<context>\n")
                kept.forEachIndexed { index, chunk ->
                    append("[${index + 1}] ")
                    append(chunk.text)
                    append("\n\n")
                }
                append("</context>\n\n")
                append("User question: ")
                append(originalPrompt)
            }
            RagAugmentation(augmentedPrompt = prompt, chunks = kept)
        } finally {
            _retrievalStatus.value = RetrievalStatus.Idle
        }
    }

    suspend fun summarizeAttachedDocuments(
        chatId: String,
        originalPrompt: String,
        maxContextTokens: Int = DEFAULT_RAG_BUDGET_TOKENS,
    ): RagAugmentation = withContext(Dispatchers.IO) {
        if (!_isReady.value) return@withContext RagAugmentation.NONE
        val docs = documentRepo.getDocumentsForChat(chatId)
            .filter { it.sourceId.isNotBlank() }
            .take(MAX_SUMMARY_DOCS)
        if (docs.isEmpty()) return@withContext RagAugmentation.NONE

        val budget = maxContextTokens.coerceAtLeast(MIN_RAG_BUDGET_TOKENS)
        val kept = mutableListOf<RagChunk>()
        var used = 0

        docs.forEach { doc ->
            val bytes = sourceVault.read(doc.sourceId) ?: return@forEach
            val text = extractReadableText(bytes, doc.mimeType, doc.name)
                ?.replace(Regex("""[ \t\r]+"""), " ")
                ?.replace(Regex("""\n{3,}"""), "\n\n")
                ?.trim()
                ?: return@forEach
            if (text.isBlank()) return@forEach

            val chunks = RagChunker.chunk(
                text = text,
                targetChars = SUMMARY_CHUNK_TARGET_CHARS,
                minChars = SUMMARY_CHUNK_MIN_CHARS,
            )
            if (chunks.isEmpty()) return@forEach

            val indices = representativeIndices(chunks.size, SUMMARY_SECTIONS_PER_DOC)
            for (idx in indices) {
                val chunkText = chunks.getOrNull(idx)?.trim().orEmpty()
                if (chunkText.isBlank()) continue
                val cost = approxTokens(chunkText) + PER_CHUNK_OVERHEAD_TOKENS
                if (used + cost > budget) break
                kept += RagChunk(
                    docId = doc.id,
                    sourceId = doc.sourceId,
                    chunkIndex = idx,
                    score = 1f,
                    text = chunkText,
                    name = doc.name,
                    mimeType = doc.mimeType,
                )
                used += cost
            }
        }
        if (kept.isEmpty()) return@withContext RagAugmentation.NONE

        val prompt = buildString {
            append("The user is asking a document-wide question about the attached file(s). ")
            append("Use the excerpts below to write a useful answer in your own words. ")
            append("If the user asks to learn, teach from the basics first, then expand into the main sections. ")
            append("Cover the main topics, definitions, examples, and notable sections that appear in the excerpts. ")
            append("Do not ask for a follow-up just because the excerpts are not the full file; answer from the available document context. ")
            append("Cite supporting excerpts inline using [1], [2], etc.\n\n")
            append("<document_excerpts>\n")
            kept.forEachIndexed { index, chunk ->
                append("[${index + 1}] ")
                append(chunk.name)
                append(" · section ")
                append(chunk.chunkIndex + 1)
                append("\n")
                append(chunk.text)
                append("\n\n")
            }
            append("</document_excerpts>\n\n")
            append("User request: ")
            append(originalPrompt)
        }

        Log.i(TAG, "RAG document-summary augment: docs=${docs.size} excerpts=${kept.size} (~$used / $budget tok)")
        RagAugmentation(augmentedPrompt = prompt, chunks = kept)
    }

    private data class FusedHit(
        val docId: String,
        val chunkIndex: Int,
        val text: String,
        val rrfScore: Float,
    )

    private fun rrfFuse(
        dense: List<InferenceClient.RagHit>,
        bm25: List<KeywordHit>,
    ): List<FusedHit> {
        val rankings = buildList {
            if (dense.isNotEmpty()) add(dense.map { (it.docId to it.chunkIndex) to it.text })
            if (bm25.isNotEmpty()) add(bm25.map { (it.docId to it.chunkIndex) to it.text })
        }
        return rrfFuseMany(rankings)
    }

    private fun rrfFuseMany(rankings: List<List<Pair<Pair<String, Int>, String>>>): List<FusedHit> {
        if (rankings.isEmpty()) return emptyList()
        val scores = HashMap<Pair<String, Int>, Double>()
        val texts = HashMap<Pair<String, Int>, String>()
        rankings.forEach { ranking ->
            ranking.forEachIndexed { idx, (key, text) ->
                scores.merge(key, 1.0 / (RRF_K + idx + 1)) { a, b -> a + b }
                texts.putIfAbsent(key, text)
            }
        }
        return scores.entries
            .sortedByDescending { it.value }
            .take(FUSED_POOL_SIZE)
            .map { (key, rrf) ->
                FusedHit(
                    docId = key.first,
                    chunkIndex = key.second,
                    text = texts[key].orEmpty(),
                    rrfScore = rrf.toFloat(),
                )
            }
    }

    private fun approxTokens(text: String): Int = (text.length + 3) / 4

    private suspend fun extractReadableText(bytes: ByteArray, mime: String, name: String): String? {
        if (isTextLike(mime, name)) {
            val text = runCatching { bytes.toString(Charsets.UTF_8) }.getOrNull()
            if (!text.isNullOrBlank() && text.count { it == '�' }.toFloat() / text.length.coerceAtLeast(1) <= 0.05f) {
                return text
            }
        }
        runCatching { InferenceClient.ragExtractText(bytes, mime, name) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        zipTextFallback(bytes, name)?.let { return it }
        return plainTextFallback(bytes)
    }

    private suspend fun ingestBytesWithFallback(
        bytes: ByteArray,
        mime: String?,
        name: String?,
        docId: String,
    ): Int {
        val direct = opsMutex.withLock {
            InferenceClient.ragIngestBytes(bytes, mime, name, docId)
        }
        if (direct >= 0) return direct

        val text = extractReadableText(bytes, mime.orEmpty(), name.orEmpty()) ?: return direct
        val fallback = text.trim()
        if (fallback.length < MIN_FALLBACK_TEXT_CHARS) return direct

        val textBytes = fallback.toByteArray(Charsets.UTF_8)
        val rc = opsMutex.withLock {
            InferenceClient.ragIngestBytes(textBytes, "text/plain", name ?: "document.txt", docId)
        }
        return if (rc >= 0) rc else direct
    }

    private fun plainTextFallback(bytes: ByteArray): String? {
        val utf8 = runCatching { bytes.toString(Charsets.UTF_8) }.getOrNull()
        if (!utf8.isNullOrBlank() && isMostlyReadableText(utf8)) return utf8

        val utf16 = runCatching { bytes.toString(Charsets.UTF_16) }.getOrNull()
        if (!utf16.isNullOrBlank() && isMostlyReadableText(utf16)) return utf16
        return null
    }

    private fun isMostlyReadableText(text: String): Boolean {
        if (text.length < MIN_FALLBACK_TEXT_CHARS) return false
        val sample = text.take(20_000)
        val control = sample.count { it.code < 32 && it !in "\n\r\t" }
        val replacement = sample.count { it == '�' }
        val readable = sample.count { !it.isISOControl() || it in "\n\r\t" }
        val total = sample.length.coerceAtLeast(1)
        return control.toFloat() / total < 0.02f &&
            replacement.toFloat() / total < 0.03f &&
            readable.toFloat() / total > 0.85f
    }

    private fun zipTextFallback(bytes: ByteArray, name: String): String? {
        if (!looksLikeZip(bytes, name)) return null
        val out = StringBuilder()
        runCatching {
            ZipInputStream(bytes.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val entryName = entry.name.lowercase()
                    if (!entry.isDirectory && shouldReadZipTextEntry(entryName)) {
                        val text = zip.readBytes()
                            .toString(Charsets.UTF_8)
                            .replace(Regex("""<[^>]+>"""), " ")
                            .replace(Regex("""&(?:amp|lt|gt|quot|apos);""")) { m ->
                                when (m.value) {
                                    "&amp;" -> "&"
                                    "&lt;" -> "<"
                                    "&gt;" -> ">"
                                    "&quot;" -> "\""
                                    "&apos;" -> "'"
                                    else -> " "
                                }
                            }
                            .replace(Regex("""\s+"""), " ")
                            .trim()
                        if (text.isNotBlank()) {
                            if (out.isNotEmpty()) out.append("\n\n")
                            out.append(text)
                        }
                    }
                    if (out.length > MAX_ZIP_FALLBACK_CHARS) return@use
                }
            }
        }.getOrNull()
        return out.toString().takeIf { it.length >= MIN_FALLBACK_TEXT_CHARS }
    }

    private fun looksLikeZip(bytes: ByteArray, name: String): Boolean =
        bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() &&
            bytes[1] == 0x4B.toByte() &&
            name.substringAfterLast('.', "").lowercase() in ZIP_TEXT_EXTS

    private fun shouldReadZipTextEntry(entryName: String): Boolean {
        if (entryName.startsWith("__macosx/")) return false
        if (entryName.endsWith(".xml") || entryName.endsWith(".xhtml") || entryName.endsWith(".html")) return true
        if (entryName.endsWith(".txt") || entryName.endsWith(".md") || entryName.endsWith(".csv")) return true
        return false
    }

    private fun representativeIndices(size: Int, wanted: Int): List<Int> {
        if (size <= 0 || wanted <= 0) return emptyList()
        if (size <= wanted) return (0 until size).toList()
        val out = linkedSetOf<Int>()
        out += 0
        val slots = (wanted - 1).coerceAtLeast(1)
        for (i in 1..slots) {
            out += ((size - 1) * (i.toDouble() / slots)).toInt().coerceIn(0, size - 1)
        }
        return out.toList().sorted()
    }

    suspend fun debugQuery(
        chatId: String,
        query: String,
        budget: Int = DEFAULT_RAG_BUDGET_TOKENS,
    ): RagDebugResult = withContext(Dispatchers.IO) {
        val info = runCatching { InferenceClient.ragInfo() }.getOrNull() ?: "{}"
        if (!_isReady.value || query.isBlank()) {
            return@withContext RagDebugResult(
                query = query,
                isReady = _isReady.value,
                activeChatId = chatId,
                dense = emptyList(),
                bm25 = emptyList(),
                fused = emptyList(),
                contextBlock = "",
                approxContextTokens = 0,
                engineInfo = info,
            )
        }

        val dense = opsMutex.withLock { InferenceClient.ragQueryFiltered(query, "$chatId:") }
        val bm25 = runCatching { keywordIndex.query(query, chatId, KEYWORD_CANDIDATES) }
            .getOrElse { emptyList() }
        val fused = rrfFuse(dense, bm25)

        val aug = augment(chatId, query, query, budget)
        RagDebugResult(
            query = query,
            isReady = true,
            activeChatId = chatId,
            dense = dense.map { RagDebugHit(it.docId, it.chunkIndex, it.score, it.text) },
            bm25 = bm25.map { RagDebugHit(it.docId, it.chunkIndex, it.rank.toFloat(), it.text) },
            fused = fused.map { RagDebugHit(it.docId, it.chunkIndex, it.rrfScore, it.text) },
            contextBlock = if (aug.didAugment) aug.augmentedPrompt.removeSuffix(query).trim() else "",
            approxContextTokens = if (aug.didAugment) approxTokens(aug.augmentedPrompt) else 0,
            engineInfo = info,
        )
    }

    suspend fun release() = opsMutex.withLock {
        InferenceClient.ragRelease()
        ingestedDocIds.clear()
        _isReady.value = false
        _activeEmbeddingName.value = null
    }

    private fun indexKeywordsIfTextLike(
        docId: String,
        chatId: String,
        sourceId: String,
        name: String,
        mime: String,
        bytes: ByteArray,
    ) {
        if (!isTextLike(mime, name)) return
        if (keywordIndex.docCount(docId) > 0) return
        val text = runCatching { bytes.toString(Charsets.UTF_8) }.getOrNull()?.takeIf { it.isNotBlank() } ?: return
        if (text.contains('�') && text.count { it == '�' }.toFloat() / text.length > 0.05f) return
        val chunks = RagChunker.chunk(text)
        if (chunks.isEmpty()) return
        val n = keywordIndex.ingest(docId, chatId, sourceId, chunks)
        Log.i(TAG, "FTS5 indexed $n chunks for $docId (mime=$mime)")
    }

    private fun isTextLike(mime: String?, name: String?): Boolean {
        val m = mime?.lowercase()?.substringBefore(';')?.trim()
        if (!m.isNullOrBlank()) {
            if (m.startsWith("text/")) return true
            if (m in TEXT_LIKE_MIMES) return true
        }
        val ext = name?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase()
        if (!ext.isNullOrBlank() && ext in TEXT_LIKE_EXTS) return true
        return false
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4])
            sb.append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    private companion object {
        val HEX = "0123456789abcdef".toCharArray()
        const val DEFAULT_RAG_BUDGET_TOKENS = 1536
        const val MIN_RAG_BUDGET_TOKENS = 256
        const val PER_CHUNK_OVERHEAD_TOKENS = 8
        const val DENSE_CANDIDATES = 20
        const val KEYWORD_CANDIDATES = 20
        const val FUSED_POOL_SIZE = 12
        const val FINAL_TOP_N = 8
        const val RRF_K = 60
        const val MAX_SUMMARY_DOCS = 3
        const val SUMMARY_SECTIONS_PER_DOC = 10
        const val SUMMARY_CHUNK_TARGET_CHARS = 1200
        const val SUMMARY_CHUNK_MIN_CHARS = 260
        const val MIN_FALLBACK_TEXT_CHARS = 80
        const val MAX_ZIP_FALLBACK_CHARS = 750_000
        val ZIP_TEXT_EXTS = setOf("docx", "xlsx", "pptx", "odt", "ods", "odp", "epub", "zip")
        val TEXT_LIKE_MIMES = setOf(
            "application/json",
            "application/xml",
            "application/rtf",
            "application/x-rtf",
            "application/javascript",
            "application/x-yaml",
            "application/yaml",
        )
        val TEXT_LIKE_EXTS = setOf(
            "txt", "md", "markdown", "json", "xml", "csv", "tsv", "html", "htm", "rtf",
            "yaml", "yml", "log", "ini", "toml", "properties", "kt", "java", "py", "js", "ts",
        )
    }
}
