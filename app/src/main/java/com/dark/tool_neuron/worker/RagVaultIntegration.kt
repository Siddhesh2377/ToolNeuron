package com.dark.tool_neuron.worker

import android.content.Context
import com.dark.tool_neuron.BuildConfig
import com.dark.tool_neuron.engine.EmbeddingEngine
import com.dark.tool_neuron.models.table_schema.InstalledRag
import com.dark.tool_neuron.models.table_schema.RagSourceType
import com.dark.tool_neuron.neuron_example.GraphSettings
import com.dark.tool_neuron.neuron_example.NeuronGraph
import com.dark.tool_neuron.neuron_example.NeuronNode
import com.dark.tool_neuron.neuron_example.NodeMetadata
import com.dark.tool_neuron.neuron_example.SourceType
import com.dark.tool_neuron.repo.RagRepository
import com.memoryvault.FileItem
import com.memoryvault.MemoryVault
import com.memoryvault.MessageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.dark.tool_neuron.global.AppPaths
import java.io.File
import java.util.UUID

class RagVaultIntegration(
    private val context: Context,
    private val ragRepository: RagRepository,
    private val embeddingEngine: EmbeddingEngine
) {
    private var memoryVault: MemoryVault? = null

    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (memoryVault == null) {
            memoryVault = MemoryVault(
                context = context,
                keyAlias = BuildConfig.ALIAS
            )
            memoryVault?.initialize()
        }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        memoryVault?.close()
        memoryVault = null
    }

    suspend fun createRagFromVaultMessages(
        name: String,
        description: String,
        category: String? = null,
        tags: Set<String>? = null,
        fromTime: Long? = null,
        toTime: Long? = null,
        domain: String = "memory-vault",
        ragTags: List<String> = emptyList()
    ): Result<InstalledRag> = withContext(Dispatchers.IO) {
        try {
            val vault = memoryVault ?: return@withContext Result.failure(Exception("Vault not initialized"))

            if (!embeddingEngine.isInitialized()) {
                return@withContext Result.failure(Exception("Embedding provider not initialized"))
            }

            val messages = vault.getMessages(
                category = category,
                tags = tags,
                fromTime = fromTime,
                toTime = toTime,
                limit = 1000
            )

            if (messages.isEmpty()) {
                return@withContext Result.failure(Exception("No messages found with the specified filters"))
            }

            val graph = NeuronGraph(embeddingEngine, GraphSettings.DEFAULT)

            val nodes = messages.mapIndexed { index, message ->
                NeuronNode(
                    id = message.id,
                    content = message.content,
                    sourceType = SourceType.TEXT,
                    metadata = NodeMetadata(
                        sourceId = "memory-vault",
                        sourceName = name,
                        position = index,
                        timestamp = message.timestamp,
                        extras = buildMap {
                            message.category?.let { put("category", it) }
                            if (message.tags.isNotEmpty()) {
                                put("tags", message.tags.joinToString(","))
                            }
                        }
                    )
                )
            }

            for (node in nodes) {
                graph.addNode(node)
            }

            val ragId = UUID.randomUUID().toString()
            val ragsDir = AppPaths.rags(context)
            val destFile = File(ragsDir, "$ragId.neuron")

            val payload = graph.serialize()
            destFile.writeBytes(payload)

            val ragInfo = InstalledRag(
                id = ragId,
                name = name,
                description = description,
                sourceType = RagSourceType.MEMORY_VAULT,
                filePath = destFile.absolutePath,
                nodeCount = nodes.size,
                embeddingDimension = embeddingEngine.getDimension(),
                embeddingModel = embeddingEngine.getModelName(),
                domain = domain,
                tags = ragTags.joinToString(","),
                sizeBytes = destFile.length()
            )

            ragRepository.insertRag(ragInfo)
            Result.success(ragInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchVaultSemantically(
        query: String,
        limit: Int = 10,
        threshold: Float = 0.7f
    ): List<ScoredVaultContent> = withContext(Dispatchers.IO) {
        try {
            if (!embeddingEngine.isInitialized()) {
                return@withContext emptyList()
            }

            val vault = memoryVault ?: return@withContext emptyList()
            val queryEmbedding = embeddingEngine.embed(query) ?: return@withContext emptyList()

            vault.semanticSearch(queryEmbedding, limit, threshold).map { scored ->
                ScoredVaultContent(
                    id = scored.item.id,
                    content = when (val item = scored.item) {
                        is MessageItem -> item.content
                        is FileItem -> String(item.data)
                        else -> ""
                    },
                    score = scored.score,
                    timestamp = scored.item.timestamp,
                    category = scored.item.category,
                    tags = scored.item.tags
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getVaultStats(): VaultStatsInfo? = withContext(Dispatchers.IO) {
        try {
            val vault = memoryVault ?: return@withContext null
            val stats = vault.getStats()

            VaultStatsInfo(
                totalItems = stats.totalItems,
                totalSizeBytes = stats.totalSizeBytes,
                messageCount = stats.messageCount,
                fileCount = stats.fileCount,
                embeddingCount = stats.embeddingCount,
                customDataCount = stats.customDataCount,
                compressionRatio = stats.compressionRatio
            )
        } catch (e: Exception) {
            null
        }
    }

}

data class ScoredVaultContent(
    val id: String,
    val content: String,
    val score: Float,
    val timestamp: Long,
    val category: String?,
    val tags: Set<String>
)

data class VaultStatsInfo(
    val totalItems: Int,
    val totalSizeBytes: Long,
    val messageCount: Int,
    val fileCount: Int,
    val embeddingCount: Int,
    val customDataCount: Int,
    val compressionRatio: Float
) {
    fun getFormattedSize(): String {
        return when {
            totalSizeBytes < 1024 -> "$totalSizeBytes B"
            totalSizeBytes < 1024 * 1024 -> "${totalSizeBytes / 1024} KB"
            else -> "${totalSizeBytes / (1024 * 1024)} MB"
        }
    }
}