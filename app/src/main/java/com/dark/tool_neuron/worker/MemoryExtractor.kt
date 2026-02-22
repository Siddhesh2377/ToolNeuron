package com.dark.tool_neuron.worker

import android.util.Log
import com.dark.tool_neuron.database.dao.AiMemoryDao
import com.dark.tool_neuron.engine.GenerationEvent
import com.dark.tool_neuron.models.table_schema.AiMemory
import com.dark.tool_neuron.models.table_schema.MemoryCategory
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.exp
import kotlin.math.min

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
    private val generationManager: GenerationManager
) {
    companion object {
        private const val TAG = "MemoryExtractor"
        private const val EXTRACTION_MAX_TOKENS = 128
        private const val SIMILARITY_THRESHOLD = 0.7f
        private const val DEFAULT_RETRIEVAL_LIMIT = 15
        private const val RECENCY_DECAY_RATE = 0.01f
        private const val MIN_MESSAGE_LENGTH = 20
    }

    /**
     * Extract facts from a conversation turn and store them.
     * Should be called in a background coroutine after each assistant response.
     */
    suspend fun extractAndStore(
        userMessage: String,
        assistantResponse: String,
        chatId: String?,
        personaName: String? = null
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
                deduplicateAndStore(facts, chatId)
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
        // Truncate to keep prompt small
        val userTrunc = userMessage.take(500)
        val assistTrunc = assistantResponse.take(500)

        val personaWarning = if (!personaName.isNullOrBlank()) {
            "\nIMPORTANT: The assistant is roleplaying as \"$personaName\". Only extract facts the USER stated about THEMSELVES. Do NOT extract anything the assistant said about itself or its character.\n"
        } else ""

        return """Extract facts about the user from this conversation. Only personal information the user shared about themselves (name, preferences, job, location, interests, habits). One fact per line. If none, say NONE.
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
     * - If Jaccard similarity > threshold with existing → UPDATE (overwrite text, bump timestamp)
     * - If no match → ADD new memory
     */
    private suspend fun deduplicateAndStore(facts: List<String>, chatId: String?) {
        val existingMemories = aiMemoryDao.getAllOnce()
        val now = System.currentTimeMillis()

        for (fact in facts) {
            val bestMatch = findBestMatch(fact, existingMemories)

            if (bestMatch != null && bestMatch.second >= SIMILARITY_THRESHOLD) {
                // UPDATE: overwrite fact text, bump timestamp
                val updated = bestMatch.first.copy(
                    fact = fact,
                    updatedAt = now,
                    category = categorize(fact)
                )
                aiMemoryDao.update(updated)
                Log.d(TAG, "Updated memory: '${bestMatch.first.fact}' → '$fact' (sim=${bestMatch.second})")
            } else {
                // ADD: new memory
                val memory = AiMemory(
                    fact = fact,
                    category = categorize(fact),
                    sourceChatId = chatId,
                    createdAt = now,
                    updatedAt = now,
                    lastAccessedAt = now,
                    accessCount = 0
                )
                aiMemoryDao.insert(memory)
                Log.d(TAG, "Added new memory: '$fact' [${memory.category}]")
            }
        }
    }

    /**
     * Find the most similar existing memory to a candidate fact.
     * Returns (memory, similarity) or null if no memories exist.
     */
    private fun findBestMatch(
        candidate: String,
        existingMemories: List<AiMemory>
    ): Pair<AiMemory, Float>? {
        if (existingMemories.isEmpty()) return null

        var bestMemory: AiMemory? = null
        var bestSim = 0f

        for (memory in existingMemories) {
            val sim = textSimilarity(candidate, memory.fact)
            if (sim > bestSim) {
                bestSim = sim
                bestMemory = memory
            }
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
     * Scoring: keyword_relevance * 0.5 + recency * 0.3 + access_factor * 0.2
     *
     * Also updates lastAccessedAt and accessCount for retrieved memories (reinforcement).
     */
    suspend fun retrieveRelevant(
        query: String,
        limit: Int = DEFAULT_RETRIEVAL_LIMIT
    ): List<AiMemory> {
        val allMemories = aiMemoryDao.getAllOnce()
        if (allMemories.isEmpty()) return emptyList()

        val queryTokens = tokenize(query)
        val now = System.currentTimeMillis()
        val dayMs = 86_400_000L

        val scored = allMemories.map { memory ->
            val memoryTokens = tokenize(memory.fact)

            // Keyword relevance: overlap between memory tokens and query tokens
            val overlap = memoryTokens.intersect(queryTokens).size
            val keywordRelevance = if (memoryTokens.isNotEmpty()) {
                overlap.toFloat() / memoryTokens.size
            } else 0f

            // Recency factor: exponential decay based on days since last update
            val daysSinceUpdate = ((now - memory.updatedAt).toFloat() / dayMs).coerceAtLeast(0f)
            val recencyFactor = exp(-RECENCY_DECAY_RATE * daysSinceUpdate)

            // Access factor: frequently accessed memories are more important
            val accessFactor = min(1f, memory.accessCount / 10f)

            val score = keywordRelevance * 0.5f + recencyFactor * 0.3f + accessFactor * 0.2f

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

        return topMemories.map { it.first }
    }

    /**
     * Format memories for injection into system prompt.
     * Returns empty string if no memories available.
     */
    suspend fun buildMemoryBlock(query: String): String {
        val memories = retrieveRelevant(query)
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
}
