package com.dark.tool_neuron.repo

import android.content.Context
import android.net.Uri
import com.dark.tool_neuron.database.dao.RagDao
import com.dark.tool_neuron.models.table_schema.InstalledRag
import com.dark.tool_neuron.models.table_schema.RagSourceType
import com.dark.tool_neuron.models.table_schema.RagStatus
import com.dark.tool_neuron.neuron_example.NeuronGraph
import com.dark.tool_neuron.neuron_example.RetrievalConfidence
import com.dark.tool_neuron.neuron_example.RetrievalResult
import com.dark.tool_neuron.util.DocumentParser
import com.neuronpacket.NeuronPacketManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.dark.tool_neuron.global.AppPaths
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class RagRepository(
    private val ragDao: RagDao,
    private val context: Context
) {
    private val ragsDir: File by lazy {
        AppPaths.rags(context)
    }

    // Loaded graphs in memory
    private val loadedGraphs = ConcurrentHashMap<String, NeuronGraph>()

    // Password cache for encrypted RAGs (cleared when app terminates)
    private val passwordCache = ConcurrentHashMap<String, String>()

    // ==================== Database Operations ====================

    fun getAllRags(): Flow<List<InstalledRag>> = ragDao.getAllRags()

    suspend fun getAllRagsOnce(): List<InstalledRag> = ragDao.getAllRagsOnce()

    fun getLoadedRags(): Flow<List<InstalledRag>> = ragDao.getLoadedRags()

    fun getEnabledRags(): Flow<List<InstalledRag>> = ragDao.getEnabledRags()

    suspend fun getEnabledRagsOnce(): List<InstalledRag> = ragDao.getEnabledRagsOnce()

    suspend fun getRagById(id: String): InstalledRag? = ragDao.getById(id)

    suspend fun insertRag(rag: InstalledRag) = ragDao.insert(rag)

    suspend fun updateRag(rag: InstalledRag) = ragDao.update(rag)

    suspend fun deleteRag(id: String) {
        ragDao.deleteById(id)
        // Also delete the file
        val ragFile = File(ragsDir, "$id.neuron")
        if (ragFile.exists()) {
            ragFile.delete()
        }
        // Remove from loaded graphs and password cache
        loadedGraphs.remove(id)?.close()
        passwordCache.remove(id)
    }

    suspend fun updateRagStatus(id: String, status: RagStatus) = ragDao.updateStatus(id, status)

    suspend fun updateRagEnabled(id: String, isEnabled: Boolean) = ragDao.updateEnabled(id, isEnabled)

    suspend fun unloadAllRags() {
        ragDao.unloadAllRags()
        loadedGraphs.values.forEach { it.close() }
        loadedGraphs.clear()
        // Don't clear password cache - keep passwords until app terminates
    }

    /**
     * Clear all cached passwords (call on app termination)
     */
    fun clearPasswordCache() {
        android.util.Log.d("RagRepository", "Clearing password cache for ${passwordCache.size} RAGs")
        passwordCache.clear()
    }

    /**
     * Sync database state with in-memory state on app startup.
     * Since loadedGraphs is empty after app restart, mark all RAGs as unloaded
     * so the UI correctly reflects that no RAGs are loaded.
     */
    suspend fun syncLoadedStateOnStartup() = withContext(Dispatchers.IO) {
        // Only unload RAGs from DB if they're not actually loaded in memory
        // This prevents unloading RAGs when ViewModel is recreated during navigation
        if (loadedGraphs.isEmpty()) {
            android.util.Log.d("RagRepository", "No RAGs in memory, marking all as unloaded in DB")
            ragDao.unloadAllRags()
        } else {
            android.util.Log.d("RagRepository", "Found ${loadedGraphs.size} RAGs in memory, keeping DB state")
        }
    }

    suspend fun getRagCount(): Int = ragDao.getRagCount()

    suspend fun getLoadedRagCount(): Int = ragDao.getLoadedRagCount()

    // ==================== File Operations ====================

    suspend fun installRagFromUri(uri: Uri, name: String? = null): Result<InstalledRag> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Cannot open file"))

            // Generate unique ID
            val ragId = java.util.UUID.randomUUID().toString()
            val destFile = File(ragsDir, "$ragId.neuron")

            // Copy file to app directory
            inputStream.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // Try to read metadata from the packet
            val packetManager = NeuronPacketManager()
            val openResult = packetManager.open(destFile)

            val ragInfo = if (openResult.isSuccess) {
                val info = packetManager.getPacketInfo()
                val metadata = parseMetadataJson(info?.metadataJson)
                val isEncrypted = (info?.userCount ?: 0) > 0
                val loadingMode = info?.loadingMode?.value ?: 0
                packetManager.close()

                android.util.Log.d("RagRepository", "Imported RAG: isEncrypted=$isEncrypted, userCount=${info?.userCount}")

                InstalledRag(
                    id = ragId,
                    name = name ?: metadata?.optString("name") ?: "Imported RAG",
                    description = metadata?.optString("description") ?: "",
                    sourceType = RagSourceType.NEURON_PACKET,
                    filePath = destFile.absolutePath,
                    nodeCount = metadata?.optInt("nodeCount") ?: 0,
                    embeddingDimension = metadata?.optInt("embeddingDimension") ?: 0,
                    embeddingModel = metadata?.optString("embeddingModel") ?: "",
                    domain = metadata?.optString("domain") ?: "general",
                    language = metadata?.optString("language") ?: "en",
                    version = metadata?.optString("version") ?: "1.0",
                    tags = metadata?.optString("tags") ?: "",
                    status = RagStatus.INSTALLED,
                    sizeBytes = destFile.length(),
                    isEncrypted = isEncrypted,
                    loadingMode = loadingMode,
                    hasAdminAccess = false // User imported it, they don't have admin access yet
                )
            } else {
                InstalledRag(
                    id = ragId,
                    name = name ?: "Imported RAG",
                    sourceType = RagSourceType.NEURON_PACKET,
                    filePath = destFile.absolutePath,
                    status = RagStatus.INSTALLED,
                    sizeBytes = destFile.length(),
                    isEncrypted = false,
                    loadingMode = 0,
                    hasAdminAccess = false
                )
            }

            ragDao.insert(ragInfo)
            Result.success(ragInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createRagFromText(
        name: String,
        description: String,
        text: String,
        graph: NeuronGraph,
        domain: String = "general",
        tags: List<String> = emptyList()
    ): Result<InstalledRag> = withContext(Dispatchers.IO) {
        try {
            val addResult = graph.addText(text, name)
            if (addResult.isFailure) {
                return@withContext Result.failure(addResult.exceptionOrNull() ?: Exception("Failed to add text"))
            }

            val nodes = addResult.getOrThrow()
            val ragId = java.util.UUID.randomUUID().toString()
            val destFile = File(ragsDir, "$ragId.neuron")

            // Serialize and save
            val payload = graph.serialize()
            destFile.writeBytes(payload)

            val ragInfo = InstalledRag(
                id = ragId,
                name = name,
                description = description,
                sourceType = RagSourceType.TEXT,
                filePath = destFile.absolutePath,
                nodeCount = nodes.size,
                embeddingDimension = nodes.firstOrNull()?.embedding?.size ?: 0,
                domain = domain,
                tags = tags.joinToString(","),
                status = RagStatus.LOADED,  // Set as LOADED since graph is in memory
                isEnabled = true,  // Enable by default for immediate use
                sizeBytes = destFile.length()
            )

            try {
                ragDao.insert(ragInfo)
            } catch (e: Exception) {
                destFile.delete()
                return@withContext Result.failure(e)
            }
            loadedGraphs[ragId] = graph
            Result.success(ragInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createRagFromChat(
        name: String,
        description: String,
        chatId: String,
        messages: List<com.dark.tool_neuron.models.messages.Messages>,
        graph: NeuronGraph,
        domain: String = "general",
        tags: List<String> = emptyList()
    ): Result<InstalledRag> = withContext(Dispatchers.IO) {
        try {
            val addResult = graph.addChatMessages(messages, chatId, name)
            if (addResult.isFailure) {
                return@withContext Result.failure(addResult.exceptionOrNull() ?: Exception("Failed to add chat"))
            }

            val nodes = addResult.getOrThrow()
            val ragId = java.util.UUID.randomUUID().toString()
            val destFile = File(ragsDir, "$ragId.neuron")

            val payload = graph.serialize()
            destFile.writeBytes(payload)

            val ragInfo = InstalledRag(
                id = ragId,
                name = name,
                description = description,
                sourceType = RagSourceType.CHAT,
                filePath = destFile.absolutePath,
                nodeCount = nodes.size,
                embeddingDimension = nodes.firstOrNull()?.embedding?.size ?: 0,
                domain = domain,
                tags = tags.joinToString(","),
                status = RagStatus.LOADED,  // Set as LOADED since graph is in memory
                isEnabled = true,  // Enable by default for immediate use
                sizeBytes = destFile.length()
            )

            try {
                ragDao.insert(ragInfo)
            } catch (e: Exception) {
                destFile.delete()
                return@withContext Result.failure(e)
            }
            loadedGraphs[ragId] = graph
            Result.success(ragInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createRagFromFile(
        name: String,
        description: String,
        fileUri: Uri,
        graph: NeuronGraph,
        domain: String = "general",
        tags: List<String> = emptyList()
    ): Result<InstalledRag> = withContext(Dispatchers.IO) {
        try {
            // Get MIME type from content resolver
            val mimeType = context.contentResolver.getType(fileUri)
            android.util.Log.d("RagRepository", "Creating RAG from file with MIME type: $mimeType")

            // Parse document using DocumentParser
            val parseResult = DocumentParser.parseDocument(fileUri, context, mimeType)
            if (parseResult.isFailure) {
                return@withContext Result.failure(
                    parseResult.exceptionOrNull() ?: Exception("Failed to parse document")
                )
            }

            val content = parseResult.getOrThrow()
            android.util.Log.d("RagRepository", "Parsed ${content.length} characters from document")

            val addResult = graph.addText(content, name)
            if (addResult.isFailure) {
                return@withContext Result.failure(addResult.exceptionOrNull() ?: Exception("Failed to add file content"))
            }

            val nodes = addResult.getOrThrow()
            val ragId = java.util.UUID.randomUUID().toString()
            val destFile = File(ragsDir, "$ragId.neuron")

            val payload = graph.serialize()
            destFile.writeBytes(payload)

            val ragInfo = InstalledRag(
                id = ragId,
                name = name,
                description = description,
                sourceType = RagSourceType.FILE,
                filePath = destFile.absolutePath,
                nodeCount = nodes.size,
                embeddingDimension = nodes.firstOrNull()?.embedding?.size ?: 0,
                domain = domain,
                tags = tags.joinToString(","),
                status = RagStatus.LOADED,  // Set as LOADED since graph is in memory
                isEnabled = true,  // Enable by default for immediate use
                sizeBytes = destFile.length()
            )

            try {
                ragDao.insert(ragInfo)
            } catch (e: Exception) {
                destFile.delete()
                return@withContext Result.failure(e)
            }
            loadedGraphs[ragId] = graph
            Result.success(ragInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== Graph Operations ====================

    suspend fun loadGraph(ragId: String, graph: NeuronGraph, password: String? = null): Result<NeuronGraph> = withContext(Dispatchers.IO) {
        try {
            val rag = ragDao.getById(ragId) ?: return@withContext Result.failure(Exception("RAG not found"))
            val file = File(rag.filePath ?: return@withContext Result.failure(Exception("RAG file path is null")))

            if (!file.exists()) {
                return@withContext Result.failure(Exception("RAG file not found"))
            }

            // For encrypted RAGs, try to use cached password first
            var effectivePassword = password
            if (rag.sourceType == RagSourceType.NEURON_PACKET && rag.isEncrypted) {
                if (password == null) {
                    // Check cache
                    effectivePassword = passwordCache[ragId]
                    if (effectivePassword != null) {
                        android.util.Log.d("RagRepository", "Using cached password for RAG: ${rag.name}")
                    } else {
                        return@withContext Result.failure(Exception("This RAG is encrypted. Please provide a password to load it."))
                    }
                } else {
                    // Cache the provided password
                    passwordCache[ragId] = password
                    android.util.Log.d("RagRepository", "Cached password for RAG: ${rag.name}")
                }
            }

            // For neuron packets, we need to decrypt
            if (rag.sourceType == RagSourceType.NEURON_PACKET && effectivePassword != null) {
                val packetManager = NeuronPacketManager()
                try {
                    packetManager.open(file)
                    val authResult = packetManager.authenticate(effectivePassword)
                    if (authResult.isFailure) {
                        return@withContext Result.failure(authResult.exceptionOrNull() ?: Exception("Authentication failed"))
                    }
                    val payloadResult = packetManager.decryptPayload(authResult.getOrThrow())
                    if (payloadResult.isFailure) {
                        return@withContext Result.failure(payloadResult.exceptionOrNull() ?: Exception("Decryption failed"))
                    }
                    val deserializeResult = graph.deserialize(payloadResult.getOrThrow())
                    if (deserializeResult.isFailure) {
                        return@withContext Result.failure(deserializeResult.exceptionOrNull() ?: Exception("Deserialization failed"))
                    }
                } finally {
                    packetManager.close()
                }
            } else {
                // Direct file read
                val payload = file.readBytes()
                val deserializeResult = graph.deserialize(payload)
                if (deserializeResult.isFailure) {
                    return@withContext Result.failure(deserializeResult.exceptionOrNull() ?: Exception("Deserialization failed"))
                }
            }

            loadedGraphs[ragId] = graph
            ragDao.markAsLoaded(ragId)
            // Also enable the RAG when loaded so it can be queried
            ragDao.updateEnabled(ragId, true)
            Result.success(graph)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun unloadGraph(ragId: String) {
        loadedGraphs.remove(ragId)?.close()
        ragDao.markAsUnloaded(ragId)
        // Don't clear password - keep it cached for re-loading
    }

    // ==================== Query Operations ====================

    suspend fun queryAllLoadedGraphs(
        query: String,
        topK: Int = 5
    ): List<Pair<InstalledRag, List<com.dark.tool_neuron.neuron_example.QueryResult>>> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Pair<InstalledRag, List<com.dark.tool_neuron.neuron_example.QueryResult>>>()

        android.util.Log.d("RagRepository", "Querying ${loadedGraphs.size} loaded graphs for: $query")

        val snapshot = loadedGraphs.toMap()
        for ((ragId, graph) in snapshot) {
            val rag = ragDao.getById(ragId) ?: continue
            android.util.Log.d("RagRepository", "Checking RAG: ${rag.name}, isEnabled: ${rag.isEnabled}, nodeCount: ${graph.nodeCount}")

            if (!rag.isEnabled) {
                android.util.Log.w("RagRepository", "Skipping disabled RAG: ${rag.name}")
                continue
            }

            val queryResults = graph.query(query, topK)
            android.util.Log.d("RagRepository", "RAG ${rag.name} returned ${queryResults.size} results")

            if (queryResults.isNotEmpty()) {
                results.add(rag to queryResults)
            }
        }

        android.util.Log.d("RagRepository", "Total RAGs with results: ${results.size}")
        results
    }

    /**
     * Aggregated retrieval result across all loaded graphs.
     */
    data class AggregatedRetrievalResult(
        val ragResults: List<Pair<InstalledRag, RetrievalResult>>,
        val overallConfidence: RetrievalConfidence,
        val combinedContext: String
    )

    /**
     * Query all loaded graphs using the advanced retrieval pipeline.
     * Returns aggregated results with confidence and compressed context.
     */
    suspend fun queryAllLoadedGraphsWithPipeline(
        query: String,
        topK: Int = 5
    ): AggregatedRetrievalResult = withContext(Dispatchers.IO) {
        val ragResults = mutableListOf<Pair<InstalledRag, RetrievalResult>>()

        val snapshot = loadedGraphs.toMap()
        android.util.Log.d("RagRepository", "Pipeline query on ${snapshot.size} loaded graphs for: $query")

        for ((ragId, graph) in snapshot) {
            val rag = ragDao.getById(ragId)
            if (rag == null) {
                android.util.Log.w("RagRepository", "RAG $ragId in loadedGraphs but not in database — skipping")
                continue
            }
            if (!rag.isEnabled) {
                android.util.Log.w("RagRepository", "RAG ${rag.name} is disabled — skipping")
                continue
            }

            val nodeCount = graph.getAllNodes().size
            android.util.Log.d("RagRepository", "Querying RAG '${rag.name}' with $nodeCount nodes")

            val result = graph.queryWithPipeline(query, topK)
            android.util.Log.d("RagRepository", "RAG '${rag.name}' returned ${result.results.size} results, confidence=${result.confidence}")

            if (result.results.isNotEmpty()) {
                ragResults.add(rag to result)
            }
        }

        // Overall confidence = minimum confidence across all queried graphs
        val overallConfidence = if (ragResults.isEmpty()) {
            RetrievalConfidence.LOW
        } else {
            ragResults.minOf { it.second.confidence }
        }

        // Concatenated compressed contexts
        val combinedContext = ragResults.joinToString("\n\n") { (_, result) ->
            result.compressedContext
        }

        android.util.Log.d("RagRepository", "Pipeline query: ${ragResults.size} RAGs with results, confidence=$overallConfidence")

        AggregatedRetrievalResult(ragResults, overallConfidence, combinedContext)
    }

    suspend fun createSecureRagFromText(
        name: String,
        description: String,
        text: String,
        graph: NeuronGraph,
        domain: String = "general",
        tags: List<String> = emptyList(),
        adminPassword: String,
        readOnlyUsers: List<com.neuronpacket.UserCredentials> = emptyList(),
        loadingMode: com.neuronpacket.LoadingMode = com.neuronpacket.LoadingMode.EMBEDDED,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<InstalledRag> = withContext(Dispatchers.IO) {
        try {
            onProgress(0.1f, "Adding text to graph...")
            val addResult = graph.addText(text, name)
            if (addResult.isFailure) {
                return@withContext Result.failure(addResult.exceptionOrNull() ?: Exception("Failed to add text"))
            }

            val nodes = addResult.getOrThrow()
            onProgress(0.5f, "Serializing graph...")

            val payload = graph.serialize()
            val ragId = java.util.UUID.randomUUID().toString()
            val destFile = File(ragsDir, "$ragId.neuron")

            onProgress(0.7f, "Encrypting RAG...")
            val packetManager = NeuronPacketManager()
            val metadata = com.neuronpacket.PacketMetadata(
                packetId = ragId,
                name = name,
                description = description,
                domain = domain,
                tags = tags,
                loadingMode = loadingMode
            )

            val config = com.neuronpacket.ExportConfig(
                adminPassword = adminPassword,
                readOnlyUsers = readOnlyUsers,
                loadingMode = loadingMode,
                compress = true
            )

            val exportResult = packetManager.export(destFile, metadata, payload, config)
            if (exportResult.isFailure) {
                return@withContext Result.failure(exportResult.exceptionOrNull() ?: Exception("Export failed"))
            }

            onProgress(0.9f, "Saving to database...")
            val ragInfo = InstalledRag(
                id = ragId,
                name = name,
                description = description,
                sourceType = RagSourceType.NEURON_PACKET,
                filePath = destFile.absolutePath,
                nodeCount = nodes.size,
                embeddingDimension = graph.getEmbeddingDimension(),
                embeddingModel = graph.getEmbeddingModelName(),
                domain = domain,
                tags = tags.joinToString(","),
                status = RagStatus.INSTALLED,
                isEnabled = false,
                sizeBytes = destFile.length(),
                isEncrypted = true,
                loadingMode = loadingMode.value,
                hasAdminAccess = true
            )

            try {
                ragDao.insert(ragInfo)
            } catch (e: Exception) {
                destFile.delete()
                return@withContext Result.failure(e)
            }
            onProgress(1.0f, "Complete!")
            Result.success(ragInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createSecureRagFromFile(
        name: String,
        description: String,
        fileUri: Uri,
        graph: NeuronGraph,
        domain: String = "general",
        tags: List<String> = emptyList(),
        adminPassword: String,
        readOnlyUsers: List<com.neuronpacket.UserCredentials> = emptyList(),
        loadingMode: com.neuronpacket.LoadingMode = com.neuronpacket.LoadingMode.EMBEDDED,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<InstalledRag> = withContext(Dispatchers.IO) {
        try {
            onProgress(0.1f, "Parsing document...")

            // Get MIME type from content resolver
            val mimeType = context.contentResolver.getType(fileUri)
            val fileType = DocumentParser.getFileTypeName(mimeType)
            android.util.Log.d("RagRepository", "Creating secure RAG from $fileType file with MIME type: $mimeType")

            // Parse document using DocumentParser
            val parseResult = DocumentParser.parseDocument(fileUri, context, mimeType)
            if (parseResult.isFailure) {
                return@withContext Result.failure(
                    parseResult.exceptionOrNull() ?: Exception("Failed to parse document")
                )
            }

            val content = parseResult.getOrThrow()
            android.util.Log.d("RagRepository", "Parsed ${content.length} characters from $fileType document")

            onProgress(0.3f, "Adding content to graph...")
            val addResult = graph.addText(content, name)
            if (addResult.isFailure) {
                return@withContext Result.failure(addResult.exceptionOrNull() ?: Exception("Failed to add file content"))
            }

            val nodes = addResult.getOrThrow()
            onProgress(0.6f, "Serializing graph...")

            val payload = graph.serialize()
            val ragId = java.util.UUID.randomUUID().toString()
            val destFile = File(ragsDir, "$ragId.neuron")

            onProgress(0.8f, "Encrypting RAG...")
            val packetManager = NeuronPacketManager()
            val metadata = com.neuronpacket.PacketMetadata(
                packetId = ragId,
                name = name,
                description = description,
                domain = domain,
                tags = tags,
                loadingMode = loadingMode
            )

            val config = com.neuronpacket.ExportConfig(
                adminPassword = adminPassword,
                readOnlyUsers = readOnlyUsers,
                loadingMode = loadingMode,
                compress = true
            )

            val exportResult = packetManager.export(destFile, metadata, payload, config)
            if (exportResult.isFailure) {
                return@withContext Result.failure(exportResult.exceptionOrNull() ?: Exception("Export failed"))
            }

            onProgress(0.95f, "Saving to database...")
            val ragInfo = InstalledRag(
                id = ragId,
                name = name,
                description = description,
                sourceType = RagSourceType.NEURON_PACKET,
                filePath = destFile.absolutePath,
                nodeCount = nodes.size,
                embeddingDimension = graph.getEmbeddingDimension(),
                embeddingModel = graph.getEmbeddingModelName(),
                domain = domain,
                tags = tags.joinToString(","),
                status = RagStatus.INSTALLED,
                isEnabled = false,
                sizeBytes = destFile.length(),
                isEncrypted = true,
                loadingMode = loadingMode.value,
                hasAdminAccess = true
            )

            try {
                ragDao.insert(ragInfo)
            } catch (e: Exception) {
                destFile.delete()
                return@withContext Result.failure(e)
            }
            onProgress(1.0f, "Complete!")
            Result.success(ragInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== Utility ====================

    private fun parseMetadataJson(json: String?): JSONObject? {
        return try {
            json?.let { JSONObject(it) }
        } catch (e: Exception) {
            null
        }
    }

}