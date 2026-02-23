package com.dark.tool_neuron.worker

import android.util.Log
import com.dark.tool_neuron.database.dao.AiMemoryDao
import com.dark.tool_neuron.database.dao.KnowledgeEntityDao
import com.dark.tool_neuron.database.dao.KnowledgeRelationDao
import com.dark.tool_neuron.engine.EmbeddingEngine
import com.dark.tool_neuron.engine.GenerationEvent
import com.dark.tool_neuron.models.table_schema.AiMemory
import com.dark.tool_neuron.models.table_schema.MemoryCategory
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Extracts personal facts from conversations and manages the AI memory lifecycle.
 *
 * Architecture inspired by:
 * - Mem0: AUDN cycle (Add/Update/Delete/Noop) for memory management
 * - Venice Memoria: On-device extraction with importance filtering
 * - MemoryBank: Forgetting curve with access-based reinforcement
 * - ChatGPT: Prompt injection for retrieval simplicity
 */
class MemoryExtractor(
    private val aiMemoryDao: AiMemoryDao,
    private val generationManager: GenerationManager,
    private val embeddingEngine: EmbeddingEngine? = null,
    private val knowledgeEntityDao: KnowledgeEntityDao? = null,
    private val knowledgeRelationDao: KnowledgeRelationDao? = null
) {
    companion object {
        private const val TAG = "MemoryExtractor"
        private const val EXTRACTION_MAX_TOKENS = 256
        private const val SIMILARITY_THRESHOLD = 0.7f
        private const val COSINE_SIMILARITY_THRESHOLD = 0.75f
        private const val DEFAULT_RETRIEVAL_LIMIT = 15
        private const val RECENCY_DECAY_RATE = 0.01f
        private const val MIN_MESSAGE_LENGTH = 20
    }

    // ========================================================================
    // Embedding helpers
    // ========================================================================

    /**
     * Check if the embedding engine is ready for use.
     */
    private fun isEmbeddingAvailable(): Boolean {
        return embeddingEngine != null && embeddingEngine.isInitialized()
    }

    // ========================================================================
    // Extraction
    // ========================================================================

    /**
     * Extract facts from a conversation turn and store them.
     * Should be called in a background coroutine after each assistant response.
     */
    suspend fun extractAndStore(
        userMessage: String,
        assistantResponse: String,
        chatId: String?,
        personaName: String? = null,
        personaId: String? = null
    ) {
        // Skip very short exchanges (greetings, acknowledgments)
        if (userMessage.length < MIN_MESSAGE_LENGTH && assistantResponse.length < MIN_MESSAGE_LENGTH) {
            Log.d(TAG, "Skipping extraction: messages too short")
            return
        }

        if (!generationManager.isTextModelLoaded()) {
            Log.d(TAG, "Skipping extraction: no text model loaded")
            return
        }

        try {
            val facts = extractFacts(userMessage, assistantResponse, personaName)
            if (facts.isNotEmpty()) {
                Log.d(TAG, "Extracted ${facts.size} candidate facts")
                deduplicateAndStore(facts, chatId, personaId)
            } else {
                Log.d(TAG, "No facts extracted from conversation")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Memory extraction failed: ${e.message}")
        }
    }

    /**
     * Build extraction prompt, generate with LLM, parse output into facts.
     */
    private suspend fun extractFacts(
        userMessage: String,
        assistantResponse: String,
        personaName: String? = null
    ): List<String> {
        val extractionPrompt = buildExtractionPrompt(userMessage, assistantResponse, personaName)

        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content",
                "You extract personal facts about the user from conversations. Output one fact per line. If no personal facts found, output only NONE."
            ))
            put(JSONObject().put("role", "user").put("content", extractionPrompt))
        }

        val response = StringBuilder()
        try {
            generationManager.generateMultiTurnStreaming(
                messages.toString(), EXTRACTION_MAX_TOKENS
            ).collect { event ->
                when (event) {
                    is GenerationEvent.Token -> response.append(event.text)
                    is GenerationEvent.Done -> {}
                    is GenerationEvent.Error -> throw Exception(event.message)
                    else -> {}
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Extraction generation failed: ${e.message}")
            return emptyList()
        }

        return parseExtractedFacts(response.toString())
    }

    private fun buildExtractionPrompt(
        userMessage: String,
        assistantResponse: String,
        personaName: String? = null
    ): String {
        val userTrunc = userMessage.take(600)
        val assistTrunc = assistantResponse.take(400)

        val personaWarning = if (!personaName.isNullOrBlank()) {
            "\nIMPORTANT: The assistant is roleplaying as \"$personaName\". Only extract facts the USER stated about THEMSELVES. NEVER extract anything $personaName said about itself — those are fictional character traits, not user facts.\n"
        } else ""

        return """Extract personal facts about the user from this conversation. Include:
- Name, age, birthday, location
- Job, company, profession, education
- Preferences (likes, dislikes, favorites)
- Interests, hobbies, activities
- Family, relationships, pets
- Goals, plans, habits
- Emotional states or recurring feelings

Rules:
- One fact per line, written in third person ("User likes...", "User works at...")
- Only facts the USER explicitly shared — never infer or assume
- If no personal facts found, output only: NONE
$personaWarning
User: $userTrunc
Assistant: $assistTrunc

Facts:"""
    }

    /**
     * Parse the raw LLM output into individual fact strings.
     */
    private fun parseExtractedFacts(raw: String): List<String> {
        val trimmed = raw.trim()
        if (trimmed.equals("NONE", ignoreCase = true) || trimmed.isEmpty()) {
            return emptyList()
        }

        return trimmed.lines()
            .map { it.trim() }
            .map { it.removePrefix("-").removePrefix("*").removePrefix("•").trim() }
            .filter { line ->
                line.length >= 5 &&
                !line.equals("NONE", ignoreCase = true) &&
                !line.startsWith("Facts:") &&
                !line.startsWith("No ") &&
                !line.startsWith("None")
            }
    }

    /**
     * AUDN dedup cycle: compare each candidate fact against existing memories.
     * - If similarity > threshold with existing -> UPDATE (overwrite text, bump timestamp)
     * - If no match -> ADD new memory
     *
     * Uses cosine similarity when embeddings are available (threshold: 0.75),
     * falls back to Jaccard token overlap (threshold: 0.7).
     */
    private suspend fun deduplicateAndStore(facts: List<String>, chatId: String?, personaId: String? = null) {
        val existingMemories = if (personaId != null) {
            aiMemoryDao.getAllForPersonaOnce(personaId)
        } else {
            aiMemoryDao.getAllOnce()
        }
        val now = System.currentTimeMillis()
        val effectiveThreshold = if (isEmbeddingAvailable()) COSINE_SIMILARITY_THRESHOLD else SIMILARITY_THRESHOLD

        for (fact in facts) {
            val bestMatch = findBestMatch(fact, existingMemories)

            if (bestMatch != null && bestMatch.second >= effectiveThreshold) {
                // UPDATE: overwrite fact text, bump timestamp
                val updated = bestMatch.first.copy(
                    fact = fact,
                    updatedAt = now,
                    category = categorize(fact)
                )
                aiMemoryDao.update(updated)
                Log.d(TAG, "Updated memory: '${bestMatch.first.fact}' -> '$fact' (sim=${bestMatch.second})")

                // Re-embed the updated fact
                generateAndStoreEmbedding(updated.id, fact)

                // Real-time KG extraction for updated fact
                extractToKnowledgeGraph(updated.id, fact, personaId)
            } else {
                // ADD: new memory
                val memory = AiMemory(
                    fact = fact,
                    category = categorize(fact),
                    sourceChatId = chatId,
                    createdAt = now,
                    updatedAt = now,
                    lastAccessedAt = now,
                    accessCount = 0,
                    personaId = personaId
                )
                aiMemoryDao.insert(memory)
                Log.d(TAG, "Added new memory: '$fact' [${memory.category}] persona=$personaId")

                // Generate and store embedding for the new fact
                generateAndStoreEmbedding(memory.id, fact)

                // Real-time KG extraction for new fact
                extractToKnowledgeGraph(memory.id, fact, personaId)
            }
        }
    }

    /**
     * Generate an embedding for the given text and store it in the database.
     * Fails silently if embedding engine is not available.
     */
    private suspend fun generateAndStoreEmbedding(memoryId: String, text: String) {
        if (!isEmbeddingAvailable()) return

        try {
            val embedding = embeddingEngine!!.embed(text)
            if (embedding != null && embedding.isNotEmpty()) {
                val bytes = embedding.toByteArray()
                aiMemoryDao.updateEmbedding(memoryId, bytes)
                Log.d(TAG, "Stored embedding for memory $memoryId (${embedding.size} dims)")
            } else {
                Log.w(TAG, "Embedding returned null/empty for: ${text.take(50)}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate embedding for memory $memoryId: ${e.message}")
        }
    }

    /**
     * Real-time knowledge graph extraction from a new/updated fact.
     * Uses regex patterns to extract entity-relation triples.
     * Fails silently if KG DAOs are not available.
     */
    private suspend fun extractToKnowledgeGraph(memoryId: String, fact: String, personaId: String? = null) {
        val entityDao = knowledgeEntityDao ?: return
        val relationDao = knowledgeRelationDao ?: return

        try {
            val triples = KnowledgeGraphBuilder.extractTriplesFromFact(fact)
            for (triple in triples) {
                KnowledgeGraphBuilder.storeTriple(entityDao, relationDao, triple, memoryId, personaId)
            }
            if (triples.isNotEmpty()) {
                Log.d(TAG, "Extracted ${triples.size} KG triples from fact: ${fact.take(50)}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "KG extraction failed for fact $memoryId: ${e.message}")
        }
    }

    /**
     * Find the most similar existing memory to a candidate fact.
     * Uses cosine similarity when embeddings are available, falls back to Jaccard.
     * Returns (memory, similarity) or null if no memories exist.
     */
    private suspend fun findBestMatch(
        candidate: String,
        existingMemories: List<AiMemory>
    ): Pair<AiMemory, Float>? {
        if (existingMemories.isEmpty()) return null

        // Try embedding-based matching first
        val candidateEmbedding = if (isEmbeddingAvailable()) {
            try {
                embeddingEngine!!.embed(candidate)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to embed candidate for dedup, falling back to Jaccard: ${e.message}")
                null
            }
        } else null

        val useEmbeddings = candidateEmbedding != null && candidateEmbedding.isNotEmpty()

        var bestMemory: AiMemory? = null
        var bestSim = 0f

        for (memory in existingMemories) {
            val sim = if (useEmbeddings && memory.embedding != null) {
                // Embedding-based cosine similarity (more accurate for semantic dedup)
                val memoryEmb = memory.embedding.toFloatArray()
                cosineSimilarity(candidateEmbedding!!, memoryEmb).coerceIn(0f, 1f)
            } else {
                // Jaccard fallback
                textSimilarity(candidate, memory.fact)
            }
            if (sim > bestSim) {
                bestSim = sim
                bestMemory = memory
            }
        }

        if (useEmbeddings) {
            Log.d(TAG, "Dedup match (embedding): best_sim=$bestSim for '${candidate.take(50)}'")
        }

        return bestMemory?.let { it to bestSim }
    }

    /**
     * Jaccard similarity between two strings (token-level).
     */
    private fun textSimilarity(a: String, b: String): Float {
        val tokensA = tokenize(a)
        val tokensB = tokenize(b)
        if (tokensA.isEmpty() && tokensB.isEmpty()) return 1f
        val intersection = tokensA.intersect(tokensB).size
        val union = tokensA.union(tokensB).size
        return if (union > 0) intersection.toFloat() / union else 0f
    }

    private fun tokenize(text: String): Set<String> {
        return text.lowercase()
            .split(Regex("[\\s\\p{Punct}]+"))
            .filter { it.length >= 2 }
            .toSet()
    }

    /**
     * Simple keyword-based categorization. No LLM call needed.
     */
    private fun categorize(fact: String): MemoryCategory {
        val lower = fact.lowercase()
        return when {
            containsAny(lower, "name is", "called", "years old", "born", "live in",
                "lives in", "from", "age is", "birthday", "moved to", "located in") -> MemoryCategory.PERSONAL

            containsAny(lower, "prefer", "like", "love", "hate", "favorite",
                "enjoy", "dislike", "rather", "fond of", "can't stand") -> MemoryCategory.PREFERENCE

            containsAny(lower, "work", "job", "company", "engineer", "developer",
                "profession", "career", "manager", "team", "colleague",
                "office", "salary", "business", "employ") -> MemoryCategory.WORK

            containsAny(lower, "hobby", "interest", "play", "watch", "read",
                "study", "learn", "sport", "game", "music", "movie",
                "book", "travel", "cook", "garden", "paint") -> MemoryCategory.INTEREST

            else -> MemoryCategory.GENERAL
        }
    }

    private fun containsAny(text: String, vararg keywords: String): Boolean {
        return keywords.any { text.contains(it) }
    }

    // ========================================================================
    // Retrieval
    // ========================================================================

    /**
     * Score and retrieve top-K memories relevant to a query.
     *
     * When embedding engine is available:
     *   Scoring: cosine_similarity * 0.6 + recency * 0.25 + access_factor * 0.15
     *
     * Fallback (no embedding engine):
     *   Scoring: keyword_relevance * 0.5 + recency * 0.3 + access_factor * 0.2
     *
     * Also updates lastAccessedAt and accessCount for retrieved memories (reinforcement).
     */
    suspend fun retrieveRelevant(
        query: String,
        limit: Int = DEFAULT_RETRIEVAL_LIMIT,
        personaId: String? = null
    ): List<AiMemory> {
        val allMemories = if (personaId != null) {
            aiMemoryDao.getAllForPersonaOnce(personaId)
        } else {
            aiMemoryDao.getAllOnce()
        }
        if (allMemories.isEmpty()) return emptyList()

        val now = System.currentTimeMillis()
        val dayMs = 86_400_000L

        // Try embedding-based retrieval first
        val queryEmbedding = if (isEmbeddingAvailable()) {
            try {
                embeddingEngine!!.embed(query)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to embed query, falling back to Jaccard: ${e.message}")
                null
            }
        } else null

        val useEmbeddings = queryEmbedding != null && queryEmbedding.isNotEmpty()

        val scored = allMemories.map { memory ->
            // Recency factor: exponential decay based on days since last update
            val daysSinceUpdate = ((now - memory.updatedAt).toFloat() / dayMs).coerceAtLeast(0f)
            val recencyFactor = exp(-RECENCY_DECAY_RATE * daysSinceUpdate)

            // Access factor: frequently accessed memories are more important
            val accessFactor = min(1f, memory.accessCount / 10f)

            val score = if (useEmbeddings && memory.embedding != null) {
                // Embedding-based scoring
                val memoryEmbedding = memory.embedding.toFloatArray()
                val cosine = cosineSimilarity(queryEmbedding!!, memoryEmbedding)
                val clampedCosine = cosine.coerceIn(0f, 1f)
                clampedCosine * 0.6f + recencyFactor * 0.25f + accessFactor * 0.15f
            } else {
                // Jaccard fallback
                val queryTokens = tokenize(query)
                val memoryTokens = tokenize(memory.fact)
                val overlap = memoryTokens.intersect(queryTokens).size
                val keywordRelevance = if (memoryTokens.isNotEmpty()) {
                    overlap.toFloat() / memoryTokens.size
                } else 0f
                keywordRelevance * 0.5f + recencyFactor * 0.3f + accessFactor * 0.2f
            }

            memory to score
        }

        val topMemories = scored
            .sortedByDescending { it.second }
            .take(limit)

        // Reinforce accessed memories (update lastAccessedAt and accessCount)
        for ((memory, _) in topMemories) {
            val updated = memory.copy(
                lastAccessedAt = now,
                accessCount = memory.accessCount + 1
            )
            aiMemoryDao.update(updated)
        }

        if (useEmbeddings) {
            val embeddingCount = allMemories.count { it.embedding != null }
            Log.d(TAG, "Embedding retrieval: $embeddingCount/${allMemories.size} memories have embeddings")
        } else {
            Log.d(TAG, "Jaccard fallback retrieval (embedding engine not available)")
        }

        return topMemories.map { it.first }
    }

    /**
     * Format memories for injection into system prompt.
     * Uses KG query expansion when available for broader retrieval.
     * Returns empty string if no memories available.
     */
    suspend fun buildMemoryBlock(query: String, personaId: String? = null): String {
        // Expand query using KG entities if available
        val expandedQuery = if (knowledgeEntityDao != null && knowledgeRelationDao != null) {
            try {
                KnowledgeGraphBuilder.expandQueryWithKG(query, knowledgeEntityDao, knowledgeRelationDao, personaId)
            } catch (e: Exception) {
                Log.w(TAG, "KG query expansion failed: ${e.message}")
                query
            }
        } else query

        val memories = retrieveRelevant(expandedQuery, personaId = personaId)
        if (memories.isEmpty()) return ""

        val factLines = memories.joinToString("\n") { "- ${it.fact}" }
        return "## Facts about the person you are chatting with:\n$factLines"
    }

    // ========================================================================
    // Maintenance
    // ========================================================================

    /**
     * Compute memory strength for display/pruning purposes.
     * strength = recency_factor * access_factor
     */
    fun computeStrength(memory: AiMemory): Float {
        val now = System.currentTimeMillis()
        val dayMs = 86_400_000L
        val daysSinceUpdate = ((now - memory.updatedAt).toFloat() / dayMs).coerceAtLeast(0f)
        val recencyFactor = exp(-RECENCY_DECAY_RATE * daysSinceUpdate)
        val accessFactor = min(1f, memory.accessCount / 5f)
        return recencyFactor * accessFactor.coerceAtLeast(0.1f)
    }

    /**
     * Check if a memory is considered stale (strength < 0.2).
     */
    fun isStale(memory: AiMemory): Boolean {
        return computeStrength(memory) < 0.2f
    }

    /**
     * Delete all stale memories (strength < 0.2).
     * Returns count of deleted memories.
     */
    suspend fun clearStaleMemories(): Int {
        val allMemories = aiMemoryDao.getAllOnce()
        val stale = allMemories.filter { isStale(it) }
        for (memory in stale) {
            aiMemoryDao.delete(memory)
        }
        Log.d(TAG, "Cleared ${stale.size} stale memories")
        return stale.size
    }

    /**
     * Backfill embeddings for all memories that don't have one yet.
     * Useful for migrating existing memories after embedding engine becomes available.
     * Returns count of newly embedded memories.
     */
    suspend fun backfillEmbeddings(): Int {
        if (!isEmbeddingAvailable()) {
            Log.d(TAG, "Cannot backfill: embedding engine not available")
            return 0
        }

        val allMemories = aiMemoryDao.getAllOnce()
        var count = 0
        for (memory in allMemories) {
            if (memory.embedding == null) {
                generateAndStoreEmbedding(memory.id, memory.fact)
                count++
            }
        }
        Log.d(TAG, "Backfilled embeddings for $count memories")
        return count
    }
}

// ========================================================================
// Extension functions for FloatArray <-> ByteArray conversion
// ========================================================================

/**
 * Convert a FloatArray to ByteArray using little-endian ByteBuffer.
 * Each float is 4 bytes, so output size = input.size * 4.
 */
fun FloatArray.toByteArray(): ByteArray {
    val buffer = ByteBuffer.allocate(size * 4).order(ByteOrder.LITTLE_ENDIAN)
    buffer.asFloatBuffer().put(this)
    return buffer.array()
}

/**
 * Convert a ByteArray back to FloatArray using little-endian ByteBuffer.
 * Input size must be a multiple of 4.
 */
fun ByteArray.toFloatArray(): FloatArray {
    val buffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
    val floats = FloatArray(size / 4)
    buffer.asFloatBuffer().get(floats)
    return floats
}

/**
 * Compute cosine similarity between two float vectors.
 * Returns a value in [-1, 1] where 1 means identical direction.
 * Returns 0 if either vector has zero magnitude.
 */
fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    if (a.size != b.size) return 0f

    var dot = 0f
    var normA = 0f
    var normB = 0f
    for (i in a.indices) {
        dot += a[i] * b[i]
        normA += a[i] * a[i]
        normB += b[i] * b[i]
    }

    val denom = sqrt(normA) * sqrt(normB)
    return if (denom > 0f) dot / denom else 0f
}
