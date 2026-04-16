package com.memoryvault

import android.content.Context
import android.util.Log
import com.memoryvault.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MemoryVault(
    context: Context,
    private val keyAlias: String,
    val migrationListener: MigrationListener? = null
) {
    private val vaultDir = java.io.File(context.filesDir, "memory_vault")
    private val vaultFile = VaultFile(java.io.File(vaultDir, "vault.mvlt"))
    private val walFile = java.io.File(vaultDir, "vault.wal")

    private val reader: BlockReader
    private val writer: BlockWriter
    private val walManager: WALManager
    private val encryptionManager: EncryptionManager
    private val contentProcessor: ContentProcessor
    private val index: VaultIndex
    private val fullTextIndex: FullTextIndex
    private val vectorIndices = mutableMapOf<Int, VectorIndex>()
    private val freeSpaceManager: FreeSpaceManager

    private val mutex = Mutex()
    private var initialized = false

    init {
        vaultDir.mkdirs()
        reader = BlockReader(vaultFile)
        writer = BlockWriter(vaultFile)
        walManager = WALManager(walFile)
        encryptionManager = EncryptionManager(keyAlias)
        contentProcessor = ContentProcessor(encryptionManager)
        index = VaultIndex()
        fullTextIndex = FullTextIndex()
        freeSpaceManager = FreeSpaceManager()
    }

    suspend fun initialize() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (initialized) return@withContext

            vaultFile.open()
            walManager.open()

            // Handle fresh vault - use new key from start
            if (!vaultFile.exists() || vaultFile.size() == 0L) {
                val header = VaultHeader(keyVersion = 1)  // Fresh vaults use new key
                vaultFile.writeAt(0, header.toBytes())
                initialized = true
                return@withContext
            }

            // Check if migration is needed
            val headerBytes = vaultFile.readAt(0, VaultHeader.HEADER_SIZE)
            val header = VaultHeader.fromBytes(headerBytes)

            if (header.keyVersion.toInt() == 0) {
                // Migration needed
                Log.d("MemoryVault", "Encryption key migration needed")
                migrationListener?.onMigrationStarted()

                try {
                    val migrationManager = MigrationManager(vaultFile, reader, vaultDir)
                    val result = migrationManager.migrate(
                        newKeyAlias = keyAlias,
                        onProgress = { percent ->
                            migrationListener?.onMigrationProgress(percent)
                        }
                    )

                    when (result) {
                        is MigrationResult.Success -> {
                            Log.d("MemoryVault", "Migration completed: ${result.blocksReEncrypted} blocks re-encrypted")
                            migrationListener?.onMigrationComplete()
                        }
                        is MigrationResult.Failure -> {
                            Log.e("MemoryVault", "Migration failed", result.error)
                            migrationListener?.onMigrationFailed(result.error)
                            throw result.error
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MemoryVault", "Migration failed with exception", e)
                    migrationListener?.onMigrationFailed(e)
                    throw e
                }
            }

            val uncommitted = walManager.getUncommittedEntries()
            if (uncommitted.isNotEmpty()) {
                recoverFromWAL(uncommitted)
            }

            loadIndex()

            initialized = true
        }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!initialized) return@withContext

            checkpoint()
            walManager.close()
            vaultFile.close()

            initialized = false
        }
    }

    suspend fun addMessage(
        content: String,
        category: String? = null,
        tags: Set<String> = emptySet()
    ): String = withContext(Dispatchers.IO) {
        val data = content.toByteArray()
        val blockId = java.util.UUID.randomUUID()
        
        val processed = processForWrite(data)
        
        val result = writer.writeBlockWithId(
            blockId = blockId,
            blockType = BlockType.MESSAGE,
            data = processed.data,
            compressed = processed.compressed,
            encrypted = processed.encrypted
        )

        val metadata = BlockMetadata(
            blockId = blockId,
            blockType = BlockType.MESSAGE,
            fileOffset = result.offset,
            compressedSize = processed.data.size.toLong(),
            uncompressedSize = data.size.toLong(),
            timestamp = result.timestamp,
            category = category,
            tags = tags,
            contentHash = BlockMetadata.calculateContentHash(data),
            searchableText = content.take(200)
        )

        index.add(metadata)
        fullTextIndex.addDocument(blockId, content)
        
        walManager.append(WALEntry(WALOperation.INSERT, result.timestamp, blockId, null))
        
        blockId.toString()
    }

    suspend fun addFile(
        fileName: String,
        data: ByteArray,
        mimeType: String,
        category: String? = null,
        tags: Set<String> = emptySet()
    ): String = withContext(Dispatchers.IO) {
        val blockId = java.util.UUID.randomUUID()
        
        val processed = processForWrite(data)
        
        val result = writer.writeBlockWithId(
            blockId = blockId,
            blockType = BlockType.FILE,
            data = processed.data,
            compressed = processed.compressed,
            encrypted = processed.encrypted
        )

        val metadata = BlockMetadata(
            blockId = blockId,
            blockType = BlockType.FILE,
            fileOffset = result.offset,
            compressedSize = processed.data.size.toLong(),
            uncompressedSize = data.size.toLong(),
            timestamp = result.timestamp,
            category = category,
            tags = tags,
            contentHash = BlockMetadata.calculateContentHash(data),
            searchableText = "$fileName|$mimeType"
        )

        index.add(metadata)
        
        walManager.append(WALEntry(WALOperation.INSERT, result.timestamp, blockId, null))
        
        blockId.toString()
    }

    suspend fun addCustomData(
        dataType: String,
        data: org.json.JSONObject,
        category: String? = null,
        tags: Set<String> = emptySet()
    ): String = withContext(Dispatchers.IO) {
        val bytes = data.toString().toByteArray()
        val blockId = java.util.UUID.randomUUID()
        
        val processed = processForWrite(bytes)
        
        val result = writer.writeBlockWithId(
            blockId = blockId,
            blockType = BlockType.CUSTOM_DATA,
            data = processed.data,
            compressed = processed.compressed,
            encrypted = processed.encrypted
        )

        val metadata = BlockMetadata(
            blockId = blockId,
            blockType = BlockType.CUSTOM_DATA,
            fileOffset = result.offset,
            compressedSize = processed.data.size.toLong(),
            uncompressedSize = bytes.size.toLong(),
            timestamp = result.timestamp,
            category = category,
            tags = tags,
            contentHash = BlockMetadata.calculateContentHash(bytes),
            searchableText = dataType
        )

        index.add(metadata)
        
        walManager.append(WALEntry(WALOperation.INSERT, result.timestamp, blockId, null))
        
        blockId.toString()
    }

    suspend fun addEmbedding(
        vector: FloatArray,
        linkedContentId: String,
        modelName: String,
        category: String? = null,
        tags: Set<String> = emptySet()
    ): String = withContext(Dispatchers.IO) {
        val bytes = serializeEmbedding(vector, linkedContentId, modelName)
        val blockId = java.util.UUID.randomUUID()
        
        val processed = processForWrite(bytes)
        
        val result = writer.writeBlockWithId(
            blockId = blockId,
            blockType = BlockType.EMBEDDING,
            data = processed.data,
            compressed = processed.compressed,
            encrypted = processed.encrypted
        )

        val metadata = BlockMetadata(
            blockId = blockId,
            blockType = BlockType.EMBEDDING,
            fileOffset = result.offset,
            compressedSize = processed.data.size.toLong(),
            uncompressedSize = bytes.size.toLong(),
            timestamp = result.timestamp,
            category = category,
            tags = tags,
            contentHash = BlockMetadata.calculateContentHash(bytes),
            searchableText = modelName
        )

        index.add(metadata)
        
        // Add to vector index if it exists, or create it
        val vectorIndex = vectorIndices.getOrPut(vector.size) { VectorIndex(vector.size) }
        vectorIndex.add(blockId, vector)
        
        walManager.append(WALEntry(WALOperation.INSERT, result.timestamp, blockId, null))
        
        blockId.toString()
    }

    private fun serializeEmbedding(vector: FloatArray, linkedId: String, model: String): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val dos = java.io.DataOutputStream(output)
        dos.writeInt(vector.size)
        vector.forEach { dos.writeFloat(it) }
        dos.writeUTF(linkedId)
        dos.writeUTF(model)
        return output.toByteArray()
    }

    private fun processForWrite(data: ByteArray): ProcessedData {
        var processed = data
        var compressed = false
        
        if (CompressionUtils.shouldCompress(data)) {
            processed = CompressionUtils.compress(data)
            compressed = true
        }
        
        val encrypted = encryptionManager.encrypt(processed)
        return ProcessedData(encrypted.toBytes(), compressed, true)
    }

    private data class ProcessedData(val data: ByteArray, val compressed: Boolean, val encrypted: Boolean)

    suspend fun getById(id: String): VaultItem? = withContext(Dispatchers.IO) {
        val uuid = try { java.util.UUID.fromString(id) } catch (_: Exception) { return@withContext null }
        index.get(uuid)?.let { readVaultItem(it) }
    }

    suspend fun getMessages(
        category: String? = null,
        tags: Set<String>? = null,
        fromTime: Long? = null,
        toTime: Long? = null,
        limit: Int = 100
    ): List<MessageItem> = withContext(Dispatchers.IO) {
        var results = index.getByType(BlockType.MESSAGE)

        category?.let { cat ->
            results = results.filter { it.category == cat }
        }

        tags?.let { t ->
            results = results.filter { meta -> t.all { tag -> tag in meta.tags } }
        }

        if (fromTime != null || toTime != null) {
            results = results.filter { meta ->
                val time = meta.timestamp
                (fromTime == null || time >= fromTime) && (toTime == null || time <= toTime)
            }
        }

        results.sortedByDescending { it.timestamp }
            .take(limit)
            .mapNotNull { readVaultItem(it) as? MessageItem }
    }

    suspend fun semanticSearch(
        embedding: FloatArray,
        limit: Int = 10,
        threshold: Float = 0.7f
    ): List<ScoredVaultItem> = withContext(Dispatchers.IO) {
        val vectorIndex = vectorIndices[embedding.size] ?: return@withContext emptyList()

        val results = vectorIndex.search(embedding, limit, threshold)

        results.mapNotNull { result ->
            val metadata = index.get(result.blockId)
            metadata?.let {
                val item = readVaultItem(it)
                item?.let { ScoredVaultItem(it, result.score) }
            }
        }
    }

    suspend fun getByCategory(category: String): List<VaultItem> = withContext(Dispatchers.IO) {
        val metadata = index.getByCategory(category)
        metadata.mapNotNull { readVaultItem(it) }
    }

    suspend fun getStats(): VaultStats = withContext(Dispatchers.IO) {
        val allMetadata = index.getAllMetadata()

        val totalSize = allMetadata.sumOf { it.compressedSize }
        val uncompressedSize = allMetadata.sumOf { it.uncompressedSize }
        val wastedSpace = freeSpaceManager.getTotalFreeSpace()

        val messageCount = allMetadata.count { it.blockType == BlockType.MESSAGE }
        val fileCount = allMetadata.count { it.blockType == BlockType.FILE }
        val embeddingCount = allMetadata.count { it.blockType == BlockType.EMBEDDING }
        val customCount = allMetadata.count { it.blockType == BlockType.CUSTOM_DATA }

        val oldestTime = allMetadata.minOfOrNull { it.timestamp } ?: 0L
        val newestTime = allMetadata.maxOfOrNull { it.timestamp } ?: 0L

        val compressionRatio = if (uncompressedSize > 0) {
            totalSize.toFloat() / uncompressedSize.toFloat()
        } else 1f

        VaultStats(
            totalItems = allMetadata.size,
            totalSizeBytes = totalSize,
            wastedSpaceBytes = wastedSpace,
            messageCount = messageCount,
            fileCount = fileCount,
            embeddingCount = embeddingCount,
            customDataCount = customCount,
            oldestItem = oldestTime,
            newestItem = newestTime,
            indexSizeBytes = allMetadata.size * BlockMetadata.METADATA_SIZE.toLong(),
            compressionRatio = compressionRatio
        )
    }

    private suspend fun checkpoint() {
        try {
            val metadata = index.getAllMetadata()

            // Serialize first
            val indexData = IndexSerializer.serialize(metadata)

            // Then encrypt
            val encryptedData = encryptionManager.encrypt(indexData)
            val finalData = encryptedData.toBytes()

            val indexOffset = vaultFile.size()
            vaultFile.writeAt(indexOffset, finalData)

            val header = VaultHeader(
                keyVersion = 1,  // Always use new key for checkpoints
                indexOffset = indexOffset,
                indexSize = finalData.size.toLong(),
                modifiedTime = System.currentTimeMillis()
            )
            vaultFile.writeAt(0, header.toBytes())

            walManager.checkpoint(indexOffset)
            walManager.truncate()

            Log.d("MemoryVault", "Checkpoint completed successfully")
        } catch (e: Exception) {
            Log.e("MemoryVault", "Checkpoint failed", e)
            throw e
        }
    }

    private suspend fun loadIndex() {
        val headerBytes = vaultFile.readAt(0, VaultHeader.HEADER_SIZE)
        val header = VaultHeader.fromBytes(headerBytes)

        if (header.indexOffset > 0 && header.indexSize > 0) {
            try {
                val encryptedIndexData = vaultFile.readAt(header.indexOffset, header.indexSize.toInt())

                // Decrypt first
                val encryptedData = EncryptedData.fromBytes(encryptedIndexData)
                val decryptedIndexData = encryptionManager.decrypt(encryptedData)

                // Then deserialize
                val metadata = IndexSerializer.deserialize(decryptedIndexData)
                metadata.forEach { index.add(it) }

                metadata.forEach { meta ->
                    if (meta.blockType == BlockType.MESSAGE && meta.searchableText != null) {
                        fullTextIndex.addDocument(meta.blockId, meta.searchableText)
                    }
                }
            } catch (e: Exception) {
                Log.e("MemoryVault", "Failed to load index", e)
                // If load fails, start fresh
                Log.d("MemoryVault", "Starting with empty index")
            }
        } else {
            Log.d("MemoryVault", "No index to load, starting fresh")
        }
    }

    private suspend fun recoverFromWAL(entries: List<WALEntry>) {
        entries.forEach { entry ->
            when (entry.operation) {
                WALOperation.INSERT -> {
                }
                WALOperation.DELETE -> {
                    index.remove(entry.blockId)
                }
                WALOperation.UPDATE -> {
                }
            }
        }
    }

    private suspend fun readVaultItem(metadata: BlockMetadata): VaultItem? {
        val block = reader.readBlock(metadata.fileOffset)
        val decrypted = contentProcessor.processForRead(
            data = block.data,
            originalSize = metadata.uncompressedSize.toInt(),
            compressed = block.header.compressionFlag,
            encrypted = block.header.encryptionFlag
        )

        return when (metadata.blockType) {
            BlockType.MESSAGE -> {
                val content = String(decrypted)
                MessageItem(
                    id = metadata.blockId.toString(),
                    timestamp = metadata.timestamp,
                    category = metadata.category,
                    tags = metadata.tags,
                    content = content
                )
            }
            BlockType.FILE -> {
                val parts = metadata.searchableText?.split("|") ?: listOf("unknown", "unknown")
                FileItem(
                    id = metadata.blockId.toString(),
                    timestamp = metadata.timestamp,
                    category = metadata.category,
                    tags = metadata.tags,
                    fileName = parts[0],
                    mimeType = parts.getOrNull(1) ?: "unknown",
                    size = metadata.uncompressedSize,
                    data = decrypted
                )
            }
            BlockType.CUSTOM_DATA -> {
                val json = org.json.JSONObject(String(decrypted))
                CustomDataItem(
                    id = metadata.blockId.toString(),
                    timestamp = metadata.timestamp,
                    category = metadata.category,
                    tags = metadata.tags,
                    dataType = metadata.searchableText ?: "unknown",
                    data = json
                )
            }
            BlockType.EMBEDDING -> {
                val (vector, linkedId, model) = deserializeEmbedding(decrypted)
                EmbeddingItem(
                    id = metadata.blockId.toString(),
                    timestamp = metadata.timestamp,
                    category = metadata.category,
                    tags = metadata.tags,
                    vector = vector,
                    linkedContentId = linkedId,
                    modelName = model
                )
            }
        }
    }

    private fun deserializeEmbedding(data: ByteArray): Triple<FloatArray, String, String> {
        val input = java.io.ByteArrayInputStream(data)
        val dis = java.io.DataInputStream(input)

        val size = dis.readInt()
        val vector = FloatArray(size) { dis.readFloat() }
        val linkedId = dis.readUTF()
        val model = dis.readUTF()

        return Triple(vector, linkedId, model)
    }
}
