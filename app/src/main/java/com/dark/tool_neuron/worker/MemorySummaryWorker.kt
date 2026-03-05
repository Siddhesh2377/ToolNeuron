package com.dark.tool_neuron.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dark.tool_neuron.data.VaultManager
import com.dark.tool_neuron.models.table_schema.AiMemory
import com.dark.tool_neuron.models.table_schema.MemoryCategory
import com.dark.tool_neuron.repo.ums.UmsMemoryRepository
import java.util.UUID

/**
 * Background worker that consolidates raw memory facts into category summaries.
 *
 * Runs periodically (every 6 hours) when device is idle + charging.
 * Groups unsummarized facts by category, then either:
 *   - Uses the loaded LLM to generate a natural-language summary
 *   - Falls back to rule-based concatenation if no model is loaded
 *
 * Source facts are marked as summarized and linked to the summary via summary_group_id.
 */
class MemorySummaryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "MemorySummaryWorker"
        const val WORK_NAME = "memory_summary"

        /** Minimum unsummarized facts per category before triggering a summary. */
        private const val MIN_FACTS_FOR_SUMMARY = 5

        /** Maximum facts to include in a single summary prompt. */
        private const val MAX_FACTS_PER_SUMMARY = 20
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting memory summarization")

        return try {
            val memoryRepo = VaultManager.memoryRepo ?: return Result.retry()
            val unsummarized = memoryRepo.getUnsummarized()

            if (unsummarized.isEmpty()) {
                Log.d(TAG, "No unsummarized memories, skipping")
                return Result.success()
            }

            // Group by (category, personaId) for per-persona summaries
            val grouped = unsummarized.groupBy { it.category to it.personaId }
            var summaryCount = 0

            for ((key, facts) in grouped) {
                val (category, personaId) = key
                if (facts.size < MIN_FACTS_FOR_SUMMARY) {
                    Log.d(TAG, "Category $category (persona=$personaId) has ${facts.size} facts (< $MIN_FACTS_FOR_SUMMARY), skipping")
                    continue
                }

                val toSummarize = facts.take(MAX_FACTS_PER_SUMMARY)
                val summaryText = generateSummary(category, toSummarize)

                if (summaryText != null) {
                    storeSummary(memoryRepo, category, summaryText, toSummarize, personaId)
                    summaryCount++
                }
            }

            Log.d(TAG, "Summarization complete: $summaryCount summaries created")

            // Auto-cleanup: remove stale memories (strength < 0.2)
            // These are old, never-accessed facts that have decayed past usefulness
            try {
                val allMemories = memoryRepo.getAllOnce()
                val now = System.currentTimeMillis()
                val dayMs = 86_400_000L
                var staleCount = 0
                for (memory in allMemories) {
                    // Skip summaries — only prune raw facts
                    if (memory.summaryGroupId != null && !memory.isSummarized) continue
                    val daysSinceUpdate = ((now - memory.updatedAt).toFloat() / dayMs).coerceAtLeast(0f)
                    val recencyFactor = kotlin.math.exp(-0.01f * daysSinceUpdate)
                    val accessFactor = kotlin.math.min(1f, memory.accessCount / 5f).coerceAtLeast(0.1f)
                    val strength = recencyFactor * accessFactor
                    if (strength < 0.1f) { // Very stale — stricter than manual cleanup
                        memoryRepo.delete(memory)
                        staleCount++
                    }
                }
                if (staleCount > 0) Log.d(TAG, "Auto-cleaned $staleCount stale memories")
            } catch (e: Exception) {
                Log.w(TAG, "Stale memory cleanup failed: ${e.message}")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Summarization failed: ${e.message}", e)
            Result.retry()
        }
    }

    /**
     * Generate a summary from a list of facts.
     * Tries LLM first, falls back to rule-based concatenation.
     */
    private suspend fun generateSummary(
        category: MemoryCategory,
        facts: List<AiMemory>
    ): String? {
        val factTexts = facts.map { it.fact }

        // Try LLM summarization if a model is loaded
        if (LlmModelWorker.isGgufModelLoaded.value) {
            val llmSummary = generateLLMSummary(category, factTexts)
            if (llmSummary != null) return llmSummary
        }

        // Fallback: rule-based concatenation
        return generateRuleBasedSummary(category, factTexts)
    }

    /**
     * Use the loaded LLM to generate a natural-language summary.
     */
    private suspend fun generateLLMSummary(
        category: MemoryCategory,
        facts: List<String>
    ): String? {
        val factsBlock = facts.joinToString("\n") { "- $it" }
        val prompt = """Summarize these ${category.name.lowercase()} facts about the user into 2-3 concise sentences. Combine related information. Keep only important details.

Facts:
$factsBlock

Summary:"""

        val messages = org.json.JSONArray().apply {
            put(org.json.JSONObject().put("role", "system").put("content",
                "You are a memory summarizer. Output only the summary, nothing else."
            ))
            put(org.json.JSONObject().put("role", "user").put("content", prompt))
        }

        val response = StringBuilder()
        try {
            LlmModelWorker.ggufGenerateMultiTurnStreaming(
                messages.toString(), 128
            ).collect { event ->
                when (event) {
                    is com.dark.tool_neuron.engine.GenerationEvent.Token -> response.append(event.text)
                    is com.dark.tool_neuron.engine.GenerationEvent.Done -> {}
                    is com.dark.tool_neuron.engine.GenerationEvent.Error -> throw Exception(event.message)
                    else -> {}
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "LLM summarization failed, falling back to rule-based: ${e.message}")
            return null
        }

        val result = response.toString().trim()
        return if (result.length >= 10) result else null
    }

    /**
     * Rule-based fallback: deduplicate and concatenate facts.
     */
    private fun generateRuleBasedSummary(
        category: MemoryCategory,
        facts: List<String>
    ): String {
        // Remove near-duplicates (simple token overlap check)
        val unique = mutableListOf<String>()
        for (fact in facts) {
            val tokens = fact.lowercase().split(Regex("[\\s\\p{Punct}]+")).filter { it.length >= 2 }.toSet()
            val isDuplicate = unique.any { existing ->
                val existingTokens = existing.lowercase().split(Regex("[\\s\\p{Punct}]+")).filter { it.length >= 2 }.toSet()
                val overlap = tokens.intersect(existingTokens).size
                val union = tokens.union(existingTokens).size
                union > 0 && overlap.toFloat() / union > 0.6f
            }
            if (!isDuplicate) unique.add(fact)
        }

        val categoryLabel = when (category) {
            MemoryCategory.PERSONAL -> "Personal info"
            MemoryCategory.PREFERENCE -> "Preferences"
            MemoryCategory.WORK -> "Work & career"
            MemoryCategory.INTEREST -> "Interests & hobbies"
            MemoryCategory.GENERAL -> "General"
        }

        return "$categoryLabel: ${unique.joinToString(". ")}."
    }

    /**
     * Store the summary and mark source facts as summarized.
     */
    private suspend fun storeSummary(
        memoryRepo: UmsMemoryRepository,
        category: MemoryCategory,
        summaryText: String,
        sourceFacts: List<AiMemory>,
        personaId: String? = null
    ) {
        val groupId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        // Store summary as a new memory entry (inherits personaId from source facts)
        val summaryMemory = AiMemory(
            fact = summaryText,
            category = category,
            createdAt = now,
            updatedAt = now,
            lastAccessedAt = now,
            accessCount = sourceFacts.sumOf { it.accessCount },
            isSummarized = false,
            summaryGroupId = groupId,
            personaId = personaId
        )
        memoryRepo.insert(summaryMemory)

        // Mark source facts as summarized
        val sourceIds = sourceFacts.map { it.id }
        memoryRepo.markSummarized(sourceIds, groupId)

        Log.d(TAG, "Created summary for $category (${sourceFacts.size} facts → group $groupId)")
    }
}
