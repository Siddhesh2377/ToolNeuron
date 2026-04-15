package com.dark.tool_neuron.neuron_example

import com.dark.tool_neuron.engine.EmbeddingEngine
import com.dark.tool_neuron.models.messages.Messages
import com.dark.tool_neuron.models.messages.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.UUID
import kotlin.math.sqrt

// ============================================================================
// Data Models
// ============================================================================

@Serializable
data class GraphSettings(
    val edgeThreshold: Float = 0.75f,
    val maxEdgesPerNode: Int = 10,
    val traversalDepth: Int = 2,  // Increased from 1 to get more context
    val chunkSizeTokens: Int = 256,
    val chunkOverlapTokens: Int = 40,  // Will use sentence overlap instead
    val minChunkLength: Int = 20
) {
    companion object {
        val DEFAULT = GraphSettings()

        // Optimized for medical/technical documents with lots of IDs and data
        val TECHNICAL = GraphSettings(
            edgeThreshold = 0.70f,  // Slightly lower for technical content
            maxEdgesPerNode = 15,   // More connections for related data
            traversalDepth = 2,     // Get more context
            chunkSizeTokens = 200,  // Smaller chunks for precise matching
            chunkOverlapTokens = 40,
            minChunkLength = 15
        )
    }
}

@Serializable
enum class SourceType {
    TEXT,
    CHAT,
    PDF,
    CUSTOM
}

@Serializable
enum class EdgeType {
    SEMANTIC,      // Based on embedding similarity
    SEQUENTIAL,    // Next/prev in original document
    EXPLICIT       // User-defined link (future)
}

@Serializable
data class NeuronEdge(
    val targetId: String,
    val weight: Float,
    val type: EdgeType
)

@Serializable
data class NodeMetadata(
    val sourceId: String = "",           // Original document/chat ID
    val sourceName: String = "",         // Display name
    val position: Int = 0,               // Position in source
    val timestamp: Long = System.currentTimeMillis(),
    val extras: Map<String, String> = emptyMap()
)

data class NeuronNode(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val sourceType: SourceType,
    val metadata: NodeMetadata = NodeMetadata(),
    var embedding: FloatArray? = null,
    val edges: MutableList<NeuronEdge> = mutableListOf()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as NeuronNode
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

data class QueryResult(
    val node: NeuronNode,
    val score: Float,
    val connectedNodes: List<NeuronNode> = emptyList(),
    val hopDistance: Int = 0
)

data class GraphStats(
    val nodeCount: Int,
    val edgeCount: Int,
    val sourceCount: Int,
    val avgEdgesPerNode: Float
)

// ============================================================================
// Semantic Chunker
// ============================================================================

object SemanticChunker {

    private val SENTENCE_ENDINGS = listOf(". ", "! ", "? ", ".\n", "!\n", "?\n")
    private val PARAGRAPH_MARKERS = listOf("\n\n", "\r\n\r\n")

    /**
     * Split text into semantic chunks at paragraph/sentence boundaries.
     */
    fun chunkText(
        text: String,
        settings: GraphSettings,
        sourceId: String,
        sourceName: String,
        sourceType: SourceType
    ): List<NeuronNode> {
        val cleanText = text.trim()
        if (cleanText.length < settings.minChunkLength) {
            return if (cleanText.isNotEmpty()) {
                listOf(createNode(cleanText, sourceType, sourceId, sourceName, 0))
            } else {
                emptyList()
            }
        }

        // First try paragraph-based splitting
        val paragraphs = splitByParagraphs(cleanText)

        val chunks = mutableListOf<String>()
        var currentChunk = StringBuilder()
        var estimatedTokens = 0

        for (paragraph in paragraphs) {
            val paragraphTokens = estimateTokens(paragraph)

            if (estimatedTokens + paragraphTokens <= settings.chunkSizeTokens) {
                if (currentChunk.isNotEmpty()) currentChunk.append("\n\n")
                currentChunk.append(paragraph)
                estimatedTokens += paragraphTokens
            } else {
                // Current chunk is full
                if (currentChunk.isNotEmpty()) {
                    chunks.add(currentChunk.toString())
                }

                // Check if paragraph itself is too large
                if (paragraphTokens > settings.chunkSizeTokens) {
                    // Split paragraph by sentences
                    val sentenceChunks = splitLargeParagraph(paragraph, settings)
                    chunks.addAll(sentenceChunks)
                    currentChunk = StringBuilder()
                    estimatedTokens = 0
                } else {
                    currentChunk = StringBuilder(paragraph)
                    estimatedTokens = paragraphTokens
                }
            }
        }

        // Add remaining chunk
        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString())
        }

        // Apply overlap for context continuity
        val overlappedChunks = applyOverlap(chunks, settings.chunkOverlapTokens)

        return overlappedChunks.mapIndexed { index, chunk ->
            createNode(chunk, sourceType, sourceId, sourceName, index)
        }
    }

    /**
     * Convert chat messages into nodes.
     * Groups consecutive messages by conversation flow.
     */
    fun chunkChatMessages(
        messages: List<Messages>,
        chatId: String,
        chatName: String,
        settings: GraphSettings
    ): List<NeuronNode> {
        val nodes = mutableListOf<NeuronNode>()

        // Option 1: Each message as a node (simple, preserves structure)
        messages.forEachIndexed { index, message ->
            val content = message.content.content
            if (content.isNotBlank() && content.length >= settings.minChunkLength) {
                val rolePrefix = if (message.role == Role.User) "[User] " else "[Assistant] "
                nodes.add(
                    NeuronNode(
                        id = message.msgId,
                        content = rolePrefix + content,
                        sourceType = SourceType.CHAT,
                        metadata = NodeMetadata(
                            sourceId = chatId,
                            sourceName = chatName,
                            position = index,
                            timestamp = System.currentTimeMillis(),
                            extras = mapOf("role" to message.role.name)
                        )
                    )
                )
            }
        }

        return nodes
    }

    private fun splitByParagraphs(text: String): List<String> {
        return text.split(Regex("\n\\s*\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun splitLargeParagraph(paragraph: String, settings: GraphSettings): List<String> {
        val sentences = splitBySentences(paragraph)
        val chunks = mutableListOf<String>()
        var current = StringBuilder()
        var tokens = 0

        for (sentence in sentences) {
            val sentenceTokens = estimateTokens(sentence)
            if (tokens + sentenceTokens <= settings.chunkSizeTokens) {
                if (current.isNotEmpty()) current.append(" ")
                current.append(sentence)
                tokens += sentenceTokens
            } else {
                if (current.isNotEmpty()) {
                    chunks.add(current.toString())
                }
                current = StringBuilder(sentence)
                tokens = sentenceTokens
            }
        }

        if (current.isNotEmpty()) {
            chunks.add(current.toString())
        }

        return chunks
    }

    private fun splitBySentences(text: String): List<String> {
        val sentences = mutableListOf<String>()
        var remaining = text

        while (remaining.isNotEmpty()) {
            var earliestIndex = remaining.length
            var foundEnding = ""

            for (ending in SENTENCE_ENDINGS) {
                val index = remaining.indexOf(ending)
                if (index in 0 until earliestIndex) {
                    earliestIndex = index
                    foundEnding = ending
                }
            }

            if (earliestIndex < remaining.length) {
                sentences.add(remaining.substring(0, earliestIndex + foundEnding.length).trim())
                remaining = remaining.substring(earliestIndex + foundEnding.length)
            } else {
                sentences.add(remaining.trim())
                break
            }
        }

        return sentences.filter { it.isNotEmpty() }
    }

    private fun applyOverlap(chunks: List<String>, overlapTokens: Int): List<String> {
        if (chunks.size <= 1 || overlapTokens <= 0) return chunks

        return chunks.mapIndexed { index, chunk ->
            if (index == 0) {
                chunk
            } else {
                // Get last 1-2 complete sentences from previous chunk for better context
                val prevChunk = chunks[index - 1]
                val overlapText = getLastSentences(prevChunk, 2)
                if (overlapText.isNotEmpty()) {
                    "$overlapText\n\n$chunk"
                } else {
                    chunk
                }
            }
        }
    }

    /**
     * Get the last N complete sentences from text for better context overlap
     */
    private fun getLastSentences(text: String, n: Int): String {
        val sentences = mutableListOf<String>()
        var remaining = text

        // Find all sentence boundaries
        while (remaining.isNotEmpty()) {
            var earliestIndex = remaining.length
            var foundEnding = ""

            for (ending in SENTENCE_ENDINGS) {
                val index = remaining.indexOf(ending)
                if (index in 0 until earliestIndex) {
                    earliestIndex = index
                    foundEnding = ending
                }
            }

            if (earliestIndex < remaining.length) {
                sentences.add(remaining.substring(0, earliestIndex + foundEnding.length).trim())
                remaining = remaining.substring(earliestIndex + foundEnding.length)
            } else {
                if (remaining.isNotBlank()) {
                    sentences.add(remaining.trim())
                }
                break
            }
        }

        return sentences.takeLast(n).joinToString(" ")
    }

    private fun estimateTokens(text: String): Int {
        val words = text.split(Regex("\\s+")).size
        val punctuation = text.count { it in ".,!?;:()[]{}\"'-" }
        return ((words * 1.5) + (punctuation * 0.5)).toInt()
    }

    private fun createNode(
        content: String,
        sourceType: SourceType,
        sourceId: String,
        sourceName: String,
        position: Int
    ): NeuronNode {
        return NeuronNode(
            content = content,
            sourceType = sourceType,
            metadata = NodeMetadata(
                sourceId = sourceId,
                sourceName = sourceName,
                position = position
            )
        )
    }
}

// ============================================================================
// Neuron Graph
// ============================================================================

class NeuronGraph(
    private val embeddingEngine: EmbeddingEngine,
    var settings: GraphSettings = GraphSettings.DEFAULT
) {
    private val nodes = mutableMapOf<String, NeuronNode>()
    private val mutex = Mutex()
    private var searchIndex: ChunkSearchIndex? = null

    val nodeCount: Int get() = nodes.size
    val edgeCount: Int get() = nodes.values.sumOf { it.edges.size } / 2  // Edges counted twice

    fun getEmbeddingModelName(): String = embeddingEngine.getModelName()
    fun getEmbeddingDimension(): Int = embeddingEngine.getDimension()

    fun getStats(): GraphStats {
        val sources = nodes.values.map { it.metadata.sourceId }.distinct().size
        val totalEdges = nodes.values.sumOf { it.edges.size }
        return GraphStats(
            nodeCount = nodes.size,
            edgeCount = totalEdges / 2,
            sourceCount = sources,
            avgEdgesPerNode = if (nodes.isEmpty()) 0f else totalEdges.toFloat() / nodes.size
        )
    }

    fun getAllNodes(): List<NeuronNode> = nodes.values.toList()

    fun getNode(id: String): NeuronNode? = nodes[id]

    internal fun getNodesMap(): Map<String, NeuronNode> = nodes

    /**
     * Rebuild the FTS5 search index from all current nodes.
     * Called after deserialize() and after adding new nodes.
     */
    private fun rebuildSearchIndex() {
        try {
            searchIndex?.close()
            val index = ChunkSearchIndex()
            index.populate(nodes.values)
            searchIndex = index
        } catch (e: Exception) {
            android.util.Log.e("NeuronGraph", "Failed to build FTS5 index: ${e.message}")
            searchIndex = null
        }
    }

    /**
     * Clean up FTS5 resources.
     */
    fun close() {
        searchIndex?.close()
        searchIndex = null
    }

    // ========================================================================
    // Add Content
    // ========================================================================

    /**
     * Add text content to the graph.
     * Automatically chunks, embeds, and builds edges.
     */
    suspend fun addText(
        text: String,
        sourceName: String,
        sourceId: String = UUID.randomUUID().toString()
    ): Result<List<NeuronNode>> = withContext(Dispatchers.IO) {
        try {
            if (!embeddingEngine.isInitialized()) {
                return@withContext Result.failure(Exception("Embedding provider not initialized"))
            }

            // Chunk the text
            val newNodes = SemanticChunker.chunkText(
                text = text,
                settings = settings,
                sourceId = sourceId,
                sourceName = sourceName,
                sourceType = SourceType.TEXT
            )

            if (newNodes.isEmpty()) {
                return@withContext Result.success(emptyList())
            }

            // Embed and add nodes
            val addedNodes = addNodesWithEmbeddings(newNodes)

            // Add sequential edges between chunks from same source
            addSequentialEdges(addedNodes)

            // Keep FTS5 index in sync
            rebuildSearchIndex()

            Result.success(addedNodes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Add chat messages to the graph.
     */
    suspend fun addChatMessages(
        messages: List<Messages>,
        chatId: String,
        chatName: String
    ): Result<List<NeuronNode>> = withContext(Dispatchers.IO) {
        try {
            if (!embeddingEngine.isInitialized()) {
                return@withContext Result.failure(Exception("Embedding provider not initialized"))
            }

            val newNodes = SemanticChunker.chunkChatMessages(messages, chatId, chatName, settings)

            if (newNodes.isEmpty()) {
                return@withContext Result.success(emptyList())
            }

            val addedNodes = addNodesWithEmbeddings(newNodes)
            addSequentialEdges(addedNodes)

            // Keep FTS5 index in sync
            rebuildSearchIndex()

            Result.success(addedNodes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Add a single pre-built node (for custom use).
     */
    suspend fun addNode(node: NeuronNode): Result<NeuronNode> = withContext(Dispatchers.IO) {
        try {
            if (!embeddingEngine.isInitialized()) {
                return@withContext Result.failure(Exception("Embedding provider not initialized"))
            }

            val nodeWithEmbedding = if (node.embedding == null) {
                node.copy().also { it.embedding = embeddingEngine.embed(node.content) }
            } else {
                node
            }

            mutex.withLock {
                // Build edges to existing nodes
                buildEdgesForNode(nodeWithEmbedding)
                nodes[nodeWithEmbedding.id] = nodeWithEmbedding
            }

            Result.success(nodeWithEmbedding)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun addNodesWithEmbeddings(newNodes: List<NeuronNode>): List<NeuronNode> {
        // Generate embeddings for all nodes
        val embeddings = embeddingEngine.embedBatch(newNodes.map { it.content })

        val nodesWithEmbeddings = newNodes.mapIndexed { index, node ->
            node.also { it.embedding = embeddings[index] }
        }

        mutex.withLock {
            // Add nodes and build edges
            for (node in nodesWithEmbeddings) {
                if (node.embedding != null) {
                    buildEdgesForNode(node)
                    nodes[node.id] = node
                }
            }
        }

        return nodesWithEmbeddings.filter { it.embedding != null }
    }

    private fun buildEdgesForNode(newNode: NeuronNode) {
        val newEmbedding = newNode.embedding ?: return

        val similarities = mutableListOf<Pair<String, Float>>()

        // Calculate similarity with all existing nodes
        for ((id, existingNode) in nodes) {
            val existingEmbedding = existingNode.embedding ?: continue
            val similarity = cosineSimilarity(newEmbedding, existingEmbedding)

            if (similarity >= settings.edgeThreshold) {
                similarities.add(id to similarity)
            }
        }

        // Sort by similarity and take top K
        val topEdges = similarities
            .sortedByDescending { it.second }
            .take(settings.maxEdgesPerNode)

        // Add bidirectional edges
        for ((targetId, weight) in topEdges) {
            newNode.edges.add(NeuronEdge(targetId, weight, EdgeType.SEMANTIC))
            nodes[targetId]?.edges?.add(NeuronEdge(newNode.id, weight, EdgeType.SEMANTIC))
        }

        // Prune existing nodes if they exceed max edges
        for ((targetId, _) in topEdges) {
            pruneEdges(nodes[targetId])
        }
    }

    private fun addSequentialEdges(orderedNodes: List<NeuronNode>) {
        for (i in 0 until orderedNodes.size - 1) {
            val current = orderedNodes[i]
            val next = orderedNodes[i + 1]

            // Only add if same source
            if (current.metadata.sourceId == next.metadata.sourceId) {
                current.edges.add(NeuronEdge(next.id, 1.0f, EdgeType.SEQUENTIAL))
                next.edges.add(NeuronEdge(current.id, 1.0f, EdgeType.SEQUENTIAL))
            }
        }
    }

    private fun pruneEdges(node: NeuronNode?) {
        node ?: return
        if (node.edges.size <= settings.maxEdgesPerNode) return

        // Keep sequential edges, prune semantic by weight
        val sequential = node.edges.filter { it.type == EdgeType.SEQUENTIAL }
        val semantic = node.edges
            .filter { it.type == EdgeType.SEMANTIC }
            .sortedByDescending { it.weight }
            .take(settings.maxEdgesPerNode - sequential.size)

        node.edges.clear()
        node.edges.addAll(sequential)
        node.edges.addAll(semantic)
    }

    // ========================================================================
    // Query
    // ========================================================================

    /**
     * Query the graph and return relevant nodes with their connections.
     * Uses hybrid search: semantic similarity + keyword matching
     */
    suspend fun query(
        queryText: String,
        topK: Int = 5,
        expandConnections: Boolean = true
    ): List<QueryResult> = withContext(Dispatchers.IO) {
        if (!embeddingEngine.isInitialized()) {
            android.util.Log.e("NeuronGraph", "Query failed: Embedding engine not initialized")
            return@withContext emptyList()
        }

        if (nodes.isEmpty()) {
            android.util.Log.e("NeuronGraph", "Query failed: Graph has no nodes")
            return@withContext emptyList()
        }

        android.util.Log.d("NeuronGraph", "Querying graph with ${nodes.size} nodes for: $queryText")

        val queryEmbedding = embeddingEngine.embed(queryText)
        if (queryEmbedding == null) {
            android.util.Log.e("NeuronGraph", "Query failed: Could not generate embedding for query")
            return@withContext emptyList()
        }

        // Extract important keywords from query (IDs, names, specific terms)
        val keywords = extractKeywords(queryText)
        android.util.Log.d("NeuronGraph", "Extracted keywords: $keywords")

        // Use a lower threshold for queries - 0.25 (25%) is more practical for RAG
        // Edge building uses settings.edgeThreshold (0.75), but queries need to be more permissive
        val threshold = 0.25f
        android.util.Log.d("NeuronGraph", "Using similarity threshold: $threshold")

        // Calculate hybrid scores (semantic + keyword)
        val allScores = nodes.values
            .mapNotNull { node ->
                node.embedding?.let { emb ->
                    val semanticScore = cosineSimilarity(queryEmbedding, emb)

                    // Calculate keyword match score (0.0 to 1.0)
                    val keywordScore = calculateKeywordScore(node.content, keywords)

                    // Hybrid score: 70% semantic + 30% keyword
                    // If exact keyword match, boost significantly
                    val hybridScore = if (keywordScore > 0.5f) {
                        // Strong keyword match - boost semantic score
                        (semanticScore * 0.6f) + (keywordScore * 0.4f)
                    } else {
                        // Weak or no keyword match - rely more on semantic
                        (semanticScore * 0.8f) + (keywordScore * 0.2f)
                    }

                    Triple(node, hybridScore, semanticScore)
                }
            }

        val maxScore = allScores.maxOfOrNull { it.second } ?: 0f
        val maxSemanticScore = allScores.maxOfOrNull { it.third } ?: 0f
        android.util.Log.d("NeuronGraph", "Calculated ${allScores.size} scores - max hybrid: $maxScore, max semantic: $maxSemanticScore")

        // Filter and sort by hybrid score
        val topResults = allScores
            .filter { it.second >= threshold }
            .sortedByDescending { it.second }
            .take(topK)

        android.util.Log.d("NeuronGraph", "Found ${topResults.size} nodes above threshold (top hybrid scores: ${topResults.take(3).map { "%.3f".format(it.second) }})")

        // Build results with connected nodes
        topResults.map { (node, hybridScore, _) ->
            val connectedNodes = if (expandConnections) {
                expandByEdges(node, settings.traversalDepth)
            } else {
                emptyList()
            }

            QueryResult(
                node = node,
                score = hybridScore,
                connectedNodes = connectedNodes
            )
        }
    }

    /**
     * Advanced query using the full retrieval pipeline (RRF + MMR + compression).
     * Returns RetrievalResult with confidence assessment and compressed context.
     * Falls back to legacy query() if search index is unavailable.
     */
    suspend fun queryWithPipeline(
        queryText: String,
        topK: Int = 5
    ): RetrievalResult = withContext(Dispatchers.IO) {
        if (!embeddingEngine.isInitialized() || nodes.isEmpty()) {
            return@withContext RetrievalResult(emptyList(), RetrievalConfidence.LOW, "")
        }

        val queryEmbedding = embeddingEngine.embed(queryText)
        if (queryEmbedding == null) {
            return@withContext RetrievalResult(emptyList(), RetrievalConfidence.LOW, "")
        }

        // If search index is available, use the full pipeline
        if (searchIndex != null) {
            android.util.Log.d("NeuronGraph", "Using retrieval pipeline for query: $queryText")
            return@withContext RetrievalPipeline.query(
                allNodes = nodes,
                queryText = queryText,
                queryEmbedding = queryEmbedding,
                searchIndex = searchIndex,
                topK = topK,
                settings = settings,
                expandEdgesFn = { node, depth -> expandByEdges(node, depth) }
            )
        }

        // Fallback: use legacy query and wrap in RetrievalResult
        android.util.Log.d("NeuronGraph", "Search index unavailable, falling back to legacy query")
        val legacyResults = query(queryText, topK)
        val contextBuilder = StringBuilder()
        for (result in legacyResults) {
            contextBuilder.append(result.node.content.take(500))
            contextBuilder.append("\n\n")
        }
        RetrievalResult(
            results = legacyResults,
            confidence = if (legacyResults.isNotEmpty()) RetrievalConfidence.MEDIUM else RetrievalConfidence.LOW,
            compressedContext = contextBuilder.toString().trim()
        )
    }

    /**
     * Extract important keywords from query text
     * Focuses on: IDs (patterns like XX-YYYY-NNN), capitalized words, quoted text
     */
    private fun extractKeywords(text: String): List<String> {
        val keywords = mutableListOf<String>()

        // Extract ID patterns (e.g., TU-2024-002, ID: 12345, etc.)
        val idPattern = Regex("""[A-Z]{2,}-\d{4}-\d{3}|\b(?:ID|id):\s*[\w-]+""")
        idPattern.findAll(text).forEach {
            keywords.add(it.value.trim())
        }

        // Extract quoted text
        val quotedPattern = Regex(""""([^"]+)"""")
        quotedPattern.findAll(text).forEach {
            keywords.add(it.groupValues[1])
        }

        // Extract capitalized words (likely names, places, etc.)
        val capitalizedPattern = Regex("""\b[A-Z][a-z]+(?:\s+[A-Z][a-z]+)*\b""")
        capitalizedPattern.findAll(text).forEach {
            if (it.value.length > 2) { // Ignore short words like "ID"
                keywords.add(it.value)
            }
        }

        // Extract important technical terms (numbers, codes)
        val technicalPattern = Regex("""\b\d+(?:\.\d+)?(?:[a-zA-Z]+)?\b""")
        technicalPattern.findAll(text).forEach {
            keywords.add(it.value)
        }

        return keywords.distinct()
    }

    /**
     * Calculate keyword match score between content and keywords
     */
    private fun calculateKeywordScore(content: String, keywords: List<String>): Float {
        if (keywords.isEmpty()) return 0f

        val contentLower = content.lowercase()
        var matchCount = 0
        var exactMatchCount = 0

        for (keyword in keywords) {
            val keywordLower = keyword.lowercase()

            // Check for exact match
            if (content.contains(keyword, ignoreCase = true)) {
                exactMatchCount++
                matchCount++
            }
            // Check for partial match
            else if (contentLower.contains(keywordLower)) {
                matchCount++
            }
        }

        // Score: exact matches count double
        val score = (exactMatchCount * 2f + matchCount) / (keywords.size * 2f)
        return score.coerceIn(0f, 1f)
    }

    private fun expandByEdges(startNode: NeuronNode, depth: Int): List<NeuronNode> {
        if (depth <= 0) return emptyList()

        val visited = mutableSetOf(startNode.id)
        val result = mutableListOf<NeuronNode>()
        var currentLevel = listOf(startNode)

        repeat(depth) {
            val nextLevel = mutableListOf<NeuronNode>()
            for (node in currentLevel) {
                for (edge in node.edges) {
                    if (edge.targetId !in visited) {
                        visited.add(edge.targetId)
                        nodes[edge.targetId]?.let { targetNode ->
                            result.add(targetNode)
                            nextLevel.add(targetNode)
                        }
                    }
                }
            }
            currentLevel = nextLevel
        }

        return result
    }

    // ========================================================================
    // Serialization
    // ========================================================================

    fun serialize(): ByteArray {
        val bos = ByteArrayOutputStream()
        val dos = DataOutputStream(bos)

        // Write settings
        val settingsJson = Json.encodeToString(settings)
        dos.writeUTF(settingsJson)

        // Write embedding info
        dos.writeUTF(embeddingEngine.getModelName())
        dos.writeInt(embeddingEngine.getDimension())

        // Write nodes
        dos.writeInt(nodes.size)
        for (node in nodes.values) {
            writeNode(dos, node)
        }

        return bos.toByteArray()
    }

    private fun writeNode(dos: DataOutputStream, node: NeuronNode) {
        dos.writeUTF(node.id)
        dos.writeUTF(node.content)
        dos.writeUTF(node.sourceType.name)

        // Metadata
        dos.writeUTF(node.metadata.sourceId)
        dos.writeUTF(node.metadata.sourceName)
        dos.writeInt(node.metadata.position)
        dos.writeLong(node.metadata.timestamp)
        dos.writeInt(node.metadata.extras.size)
        for ((key, value) in node.metadata.extras) {
            dos.writeUTF(key)
            dos.writeUTF(value)
        }

        // Embedding
        val hasEmbedding = node.embedding != null
        dos.writeBoolean(hasEmbedding)
        if (hasEmbedding) {
            dos.writeInt(node.embedding!!.size)
            for (f in node.embedding!!) {
                dos.writeFloat(f)
            }
        }

        // Edges
        dos.writeInt(node.edges.size)
        for (edge in node.edges) {
            dos.writeUTF(edge.targetId)
            dos.writeFloat(edge.weight)
            dos.writeUTF(edge.type.name)
        }
    }

    suspend fun deserialize(data: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val bis = ByteArrayInputStream(data)
            val dis = DataInputStream(bis)

            // Read settings
            val settingsJson = dis.readUTF()
            settings = Json.decodeFromString(settingsJson)

            // Read embedding info
            val modelName = dis.readUTF()
            val dimension = dis.readInt()

            // Verify embedding compatibility
            if (embeddingEngine.isInitialized()) {
                if (embeddingEngine.getDimension() != dimension) {
                    return@withContext Result.failure(
                        Exception("Embedding dimension mismatch: expected $dimension, got ${embeddingEngine.getDimension()}")
                    )
                }
            }

            // Read nodes
            val nodeCount = dis.readInt()
            mutex.withLock {
                nodes.clear()
                repeat(nodeCount) {
                    val node = readNode(dis)
                    nodes[node.id] = node
                }
            }

            // Build FTS5 index for the loaded graph
            rebuildSearchIndex()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun readNode(dis: DataInputStream): NeuronNode {
        val id = dis.readUTF()
        val content = dis.readUTF()
        val sourceType = SourceType.valueOf(dis.readUTF())

        // Metadata
        val sourceId = dis.readUTF()
        val sourceName = dis.readUTF()
        val position = dis.readInt()
        val timestamp = dis.readLong()
        val extrasSize = dis.readInt()
        val extras = mutableMapOf<String, String>()
        repeat(extrasSize) {
            extras[dis.readUTF()] = dis.readUTF()
        }
        val metadata = NodeMetadata(sourceId, sourceName, position, timestamp, extras)

        // Embedding
        val hasEmbedding = dis.readBoolean()
        val embedding = if (hasEmbedding) {
            val size = dis.readInt()
            FloatArray(size) { dis.readFloat() }
        } else null

        // Edges
        val edgeCount = dis.readInt()
        val edges = mutableListOf<NeuronEdge>()
        repeat(edgeCount) {
            val targetId = dis.readUTF()
            val weight = dis.readFloat()
            val edgeType = EdgeType.valueOf(dis.readUTF())
            edges.add(NeuronEdge(targetId, weight, edgeType))
        }

        return NeuronNode(id, content, sourceType, metadata, embedding, edges)
    }

    // ========================================================================
    // Utility
    // ========================================================================

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f

        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator > 0) dotProduct / denominator else 0f
    }
}