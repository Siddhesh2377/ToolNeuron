package com.memoryvault

import android.content.Context
import android.util.Log
import com.memoryvault.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID

class MemoryVault(
    context: Context,
    private val keyAlias: String,
    val migrationListener: MigrationListener? = null
) {
    private val vaultDir = File(context.filesDir, "memory_vault")
    private val vaultFile = VaultFile(File(vaultDir, "vault.mvlt"))
    private val walFile = File(vaultDir, "vault.wal")

    private val writer: BlockWriter
    private val reader: BlockReader
    private val walManager: WALManager
    private val encryptionManager: EncryptionManager
    private val contentProcessor: ContentProcessor
    private val index: VaultIndex
    private val fullTextIndex: FullTextIndex
    private val vectorIndices = mutableMapOf<Int, VectorIndex>()
    private val dedupManager: DedupManager
    private val freeSpaceManager: FreeSpaceManager
    private val defragManager: DefragManager
    private val validator: VaultValidator
    private val backupManager: BackupManager

    private val mutex = Mutex()
    private var initialized = false

    init {
        // Validate keyAlias configuration
        require(keyAlias != "sample_val") {
            "Invalid keyAlias configuration. Please set ALIAS in local.properties or environment variables."
        }

        vaultDir.mkdirs()
        writer = BlockWriter(vaultFile)
        reader = BlockReader(vaultFile)
        walManager = WALManager(walFile)
        encryptionManager = EncryptionManager(keyAlias)
        contentProcessor = ContentProcessor(encryptionManager)
        index = VaultIndex()
        fullTextIndex = FullTextIndex()
        dedupManager = DedupManager()
        freeSpaceManager = FreeSpaceManager()
        defragManager = DefragManager(vaultFile, reader)
        validator = VaultValidator(vaultFile, reader)
        backupManager = BackupManager(File(vaultDir, "vault.mvlt"))
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
        val messageData = content.toByteArray()
        val contentHash = BlockMetadata.calculateContentHash(messageData)

        val existingBlock = dedupManager.findDuplicate(contentHash)
        if (existingBlock != null) {
            dedupManager.addReference(existingBlock)
            return@withContext existingBlock.toString()
        }

        val processed = contentProcessor.processForWrite(messageData, shouldEncrypt = true)

        val walEntry = WALEntry(
            operation = WALOperation.INSERT,
            timestamp = System.currentTimeMillis(),
            blockId = UUID.randomUUID(),
            data = processed.data
        )
        walManager.append(walEntry)

        val result = writer.writeBlockWithId(
            blockId = walEntry.blockId,
            blockType = BlockType.MESSAGE,
            data = processed.data,
            compressed = processed.compressed,
            encrypted = processed.encrypted,
            timestamp = walEntry.timestamp
        )

        walManager.markCommitted(walEntry.blockId)

        val searchableText = TextTokenizer.extractSearchableText(content)

        val metadata = BlockMetadata(
            blockId = result.blockId,
            blockType = BlockType.MESSAGE,
            fileOffset = result.offset,
            compressedSize = result.size,
            uncompressedSize = processed.originalSize.toLong(),
            timestamp = result.timestamp,
            category = category,
            tags = tags,
            contentHash = contentHash,
            searchableText = searchableText
        )

        index.add(metadata)
        fullTextIndex.addDocument(result.blockId, content)
        dedupManager.register(contentHash, result.blockId)

        checkpoint()

        result.blockId.toString()
    }

    suspend fun addFile(
        file: File,
        mimeType: String,
        category: String? = null,
        tags: Set<String> = emptySet()
    ): String = withContext(Dispatchers.IO) {
        val fileData = file.readBytes()
        val contentHash = BlockMetadata.calculateContentHash(fileData)

        val existingBlock = dedupManager.findDuplicate(contentHash)
        if (existingBlock != null) {
            dedupManager.addReference(existingBlock)
            return@withContext existingBlock.toString()
        }

        val processed = contentProcessor.processForWrite(fileData, shouldEncrypt = true)

        val walEntry = WALEntry(
            operation = WALOperation.INSERT,
            timestamp = System.currentTimeMillis(),
            blockId = UUID.randomUUID(),
            data = processed.data
        )
        walManager.append(walEntry)

        val result = writer.writeBlockWithId(
            blockId = walEntry.blockId,
            blockType = BlockType.FILE,
            data = processed.data,
            compressed = processed.compressed,
            encrypted = processed.encrypted,
            timestamp = walEntry.timestamp
        )

        walManager.markCommitted(walEntry.blockId)

        val metadata = BlockMetadata(
            blockId = result.blockId,
            blockType = BlockType.FILE,
            fileOffset = result.offset,
            compressedSize = result.size,
            uncompressedSize = processed.originalSize.toLong(),
            timestamp = result.timestamp,
            category = category,
            tags = tags,
            contentHash = contentHash,
            searchableText = "${file.name}|$mimeType"
        )

        index.add(metadata)
        dedupManager.register(contentHash, result.blockId)

        result.blockId.toString()
    }

    suspend fun addCustomData(
        dataType: String,
        data: JSONObject,
        category: String? = null,
        tags: Set<String> = emptySet()
    ): String = withContext(Dispatchers.IO) {
        val jsonData = data.toString().toByteArray()
        val contentHash = BlockMetadata.calculateContentHash(jsonData)

        val processed = contentProcessor.processForWrite(jsonData, shouldEncrypt = true)

        val walEntry = WALEntry(
            operation = WALOperation.INSERT,
            timestamp = System.currentTimeMillis(),
            blockId = UUID.randomUUID(),
            data = processed.data
        )
        walManager.append(walEntry)

        val result = writer.writeBlockWithId(
            blockId = walEntry.blockId,
            blockType = BlockType.CUSTOM_DATA,
            data = processed.data,
            compressed = processed.compressed,
            encrypted = processed.encrypted,
            timestamp = walEntry.timestamp
        )

        walManager.markCommitted(walEntry.blockId)

        val metadata = BlockMetadata(
            blockId = result.blockId,
            blockType = BlockType.CUSTOM_DATA,
            fileOffset = result.offset,
            compressedSize = result.size,
            uncompressedSize = processed.originalSize.toLong(),
            timestamp = result.timestamp,
            category = category,
            tags = tags,
            contentHash = contentHash,
            searchableText = dataType
        )

        index.add(metadata)

        checkpoint()

        result.blockId.toString()
    }

    suspend fun addEmbedding(
        vector: FloatArray,
        linkedContentId: String,
        modelName: String = "default"
    ): String = withContext(Dispatchers.IO) {
        val dimension = vector.size

        val vectorIndex = vectorIndices.getOrPut(dimension) {
            VectorIndex(dimension)
        }

        val embeddingData = serializeEmbedding(vector, linkedContentId, modelName)
        val processed = contentProcessor.processForWrite(embeddingData, shouldEncrypt = true)

        val walEntry = WALEntry(
            operation = WALOperation.INSERT,
            timestamp = System.currentTimeMillis(),
            blockId = UUID.randomUUID(),
            data = processed.data
        )
        walManager.append(walEntry)

        val result = writer.writeBlockWithId(
            blockId = walEntry.blockId,
            blockType = BlockType.EMBEDDING,
            data = processed.data,
            compressed = processed.compressed,
            encrypted = processed.encrypted,
            timestamp = walEntry.timestamp
        )

        walManager.markCommitted(walEntry.blockId)

        val contentHash = BlockMetadata.calculateContentHash(embeddingData)

        val metadata = BlockMetadata(
            blockId = result.blockId,
            blockType = BlockType.EMBEDDING,
            fileOffset = result.offset,
            compressedSize = result.size,
            uncompressedSize = processed.originalSize.toLong(),
            timestamp = result.timestamp,
            category = modelName,
            tags = setOf(linkedContentId),
            contentHash = contentHash,
            searchableText = "dim:$dimension|model:$modelName"
        )

        index.add(metadata)
        vectorIndex.add(result.blockId, vector)

        result.blockId.toString()
    }

    suspend fun getById(id: String): VaultItem? = withContext(Dispatchers.IO) {
        val blockId = UUID.fromString(id)
        val metadata = index.get(blockId) ?: return@withContext null

        readVaultItem(metadata)
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

    suspend fun textSearch(query: String): List<VaultItem> = withContext(Dispatchers.IO) {
        val blockIds = fullTextIndex.search(query)
        blockIds.mapNotNull { id ->
            val metadata = index.get(id)
            metadata?.let { readVaultItem(it) }
        }
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

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val blockId = UUID.fromString(id)
        val metadata = index.get(blockId) ?: return@withContext

        val shouldDelete = dedupManager.removeReference(blockId)

        if (shouldDelete) {
            val walEntry = WALEntry(
                operation = WALOperation.DELETE,
                timestamp = System.currentTimeMillis(),
                blockId = blockId,
                data = null
            )
            walManager.append(walEntry)

            index.remove(blockId)
            metadata.searchableText?.let {
                fullTextIndex.removeDocument(blockId, it)
            }

            freeSpaceManager.addFreeSpace(metadata.fileOffset, metadata.compressedSize)

            walManager.markCommitted(blockId)

            checkpoint()
        }
    }

    suspend fun updateTags(id: String, tags: Set<String>) = withContext(Dispatchers.IO) {
        val blockId = UUID.fromString(id)
        val metadata = index.get(blockId) ?: return@withContext

        index.remove(blockId)
        index.add(metadata.copy(tags = tags))
    }

    suspend fun updateCategory(id: String, category: String?) = withContext(Dispatchers.IO) {
        val blockId = UUID.fromString(id)
        val metadata = index.get(blockId) ?: return@withContext

        index.remove(blockId)
        index.add(metadata.copy(category = category))
    }

    suspend fun defragment(onProgress: (Float) -> Unit = {}) = withContext(Dispatchers.IO) {
        val metadata = index.getAllMetadata()
        defragManager.defragment(metadata, onProgress)
    }

    suspend fun validate(): ValidationReport = withContext(Dispatchers.IO) {
        validator.validate()
    }

    suspend fun rebuildIndex() = withContext(Dispatchers.IO) {
        index.clear()
        fullTextIndex.clear()
        dedupManager.clear()

        val metadata = validator.rebuildIndex()
        metadata.forEach { index.add(it) }

        checkpoint()
    }

    suspend fun backup(destination: File): BackupResult = withContext(Dispatchers.IO) {
        checkpoint()
        backupManager.backup(destination, compress = true)
    }

    suspend fun restore(backup: File) = withContext(Dispatchers.IO) {
        close()
        backupManager.restore(backup, decompress = true)
        initialize()
    }

    suspend fun getByCategory(category: String): List<VaultItem> = withContext(Dispatchers.IO) {
        val metadata = index.getByCategory(category)
        metadata.mapNotNull { readVaultItem(it) }
    }

    suspend fun getAllMetadata(): List<BlockMetadata> = withContext(Dispatchers.IO) {
        index.getAllMetadata()
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
        //Log.d("MemoryVault", "Checkpoint started")
        try {
            val metadata = index.getAllMetadata()
            //Log.d("MemoryVault", "Checkpointing ${metadata.size} items")

            // Serialize first
            val indexData = IndexSerializer.serialize(metadata)
            //Log.d("MemoryVault", "Serialized index: ${indexData.size} bytes")

            // Then encrypt
            val encryptedData = encryptionManager.encrypt(indexData)
            val finalData = encryptedData.toBytes()
           // Log.d("MemoryVault", "Encrypted index: ${finalData.size} bytes")

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

        //Log.d("MemoryVault", "Loading index: offset=${header.indexOffset}, size=${header.indexSize}")

        if (header.indexOffset > 0 && header.indexSize > 0) {
            try {
                val encryptedIndexData = vaultFile.readAt(header.indexOffset, header.indexSize.toInt())

             //   Log.d("MemoryVault", "Read encrypted index: ${encryptedIndexData.size} bytes")

                // Decrypt first
                val encryptedData = EncryptedData.fromBytes(encryptedIndexData)
                val decryptedIndexData = encryptionManager.decrypt(encryptedData)

            //    Log.d("MemoryVault", "Decrypted index: ${decryptedIndexData.size} bytes")

                // Then deserialize
                val metadata = IndexSerializer.deserialize(decryptedIndexData)
                metadata.forEach { index.add(it) }

                metadata.forEach { meta ->
                    if (meta.blockType == BlockType.MESSAGE && meta.searchableText != null) {
                        fullTextIndex.addDocument(meta.blockId, meta.searchableText)
                    }
                }

           //     Log.d("MemoryVault", "Loaded ${metadata.size} items from index")
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
                val json = JSONObject(String(decrypted))
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
            else -> null
        }
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