package com.dark.tool_neuron.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dark.tool_neuron.database.dao.KnowledgeEntityDao
import com.dark.tool_neuron.database.dao.KnowledgeRelationDao
import com.dark.tool_neuron.di.AppContainer
import com.dark.tool_neuron.models.table_schema.EntityType
import com.dark.tool_neuron.models.table_schema.KnowledgeEntity
import com.dark.tool_neuron.models.table_schema.KnowledgeRelation

/**
 * Builds a knowledge graph from memory facts.
 *
 * Two modes:
 *   1. Rule-based (realtime): Regex patterns extract entities and relations from facts.
 *      Called from MemoryExtractor after each new fact is stored.
 *   2. LLM-based (idle): WorkManager background job for deeper extraction.
 *      Runs every 12 hours when device is idle + charging.
 *
 * Graph structure:
 *   - Entities: named things (people, places, topics, preferences)
 *   - Relations: directed edges between entities (likes, lives_in, works_at, etc.)
 *
 * Retrieval:
 *   BFS traversal from query-matched entities, up to maxHops depth.
 *   Returns natural language context for system prompt injection.
 */
class KnowledgeGraphBuilder(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "KnowledgeGraphBuilder"
        const val WORK_NAME = "knowledge_graph"

        private val EXTRACTION_PATTERNS = listOf(
            ExtractionRule(
                Regex("(?:user|i|they)\\s+(?:live|lives|lived|living)\\s+in\\s+(.+)", RegexOption.IGNORE_CASE),
                subjectName = "User", predicate = "lives_in", objectGroup = 1, objectType = EntityType.PLACE
            ),
            ExtractionRule(
                Regex("(?:user|i|they)\\s+(?:is|am|are)\\s+from\\s+(.+)", RegexOption.IGNORE_CASE),
                subjectName = "User", predicate = "from", objectGroup = 1, objectType = EntityType.PLACE
            ),
            ExtractionRule(
                Regex("(?:user|i|they)\\s+(?:work|works|worked|working)\\s+(?:at|for)\\s+(.+)", RegexOption.IGNORE_CASE),
                subjectName = "User", predicate = "works_at", objectGroup = 1, objectType = EntityType.THING
            ),
            ExtractionRule(
                Regex("(?:user|i|they)\\s+(?:like|likes|love|loves|enjoy|enjoys)\\s+(.+)", RegexOption.IGNORE_CASE),
                subjectName = "User", predicate = "likes", objectGroup = 1, objectType = EntityType.TOPIC
            ),
            ExtractionRule(
                Regex("(?:user|i|they)\\s+(?:dislike|dislikes|hate|hates|can't stand)\\s+(.+)", RegexOption.IGNORE_CASE),
                subjectName = "User", predicate = "dislikes", objectGroup = 1, objectType = EntityType.TOPIC
            ),
            ExtractionRule(
                Regex("(?:user|my|their)'?s?\\s+(friend|sister|brother|mother|father|partner|wife|husband|colleague|boss|teacher)\\s+(?:is\\s+)?([A-Z][a-z]+)", RegexOption.IGNORE_CASE),
                subjectName = "User", predicate = "RELATION_GROUP", objectGroup = 2, objectType = EntityType.PERSON, predicateGroup = 1
            ),
            ExtractionRule(
                Regex("(?:user|i|they)\\s+(?:is|am|are)\\s+a\\s+(.+)", RegexOption.IGNORE_CASE),
                subjectName = "User", predicate = "is_a", objectGroup = 1, objectType = EntityType.THING
            ),
            ExtractionRule(
                Regex("(?:user|i|they)\\s+(?:study|studies|studied|studying)\\s+(.+)", RegexOption.IGNORE_CASE),
                subjectName = "User", predicate = "studies", objectGroup = 1, objectType = EntityType.TOPIC
            ),
            ExtractionRule(
                Regex("(?:user|i|they)\\s+(?:is|am|are)\\s+interested\\s+in\\s+(.+)", RegexOption.IGNORE_CASE),
                subjectName = "User", predicate = "interested_in", objectGroup = 1, objectType = EntityType.TOPIC
            ),
            ExtractionRule(
                Regex("(?:user|i|they)\\s+(?:move|moves|moved|moving)\\s+to\\s+(.+)", RegexOption.IGNORE_CASE),
                subjectName = "User", predicate = "lives_in", objectGroup = 1, objectType = EntityType.PLACE
            ),
            ExtractionRule(
                Regex("(?:user|my|their)'?s?\\s+name\\s+is\\s+([A-Z][a-z]+)", RegexOption.IGNORE_CASE),
                subjectName = "User", predicate = "named", objectGroup = 1, objectType = EntityType.PERSON
            )
        )

        /**
         * Extract entity-relation triples from a single fact using regex patterns.
         * Static so it can be called from MemoryExtractor for real-time extraction.
         */
        fun extractTriplesFromFact(fact: String): List<ExtractedTriple> {
            val triples = mutableListOf<ExtractedTriple>()

            for (rule in EXTRACTION_PATTERNS) {
                val match = rule.pattern.find(fact) ?: continue

                val objectName = match.groupValues.getOrNull(rule.objectGroup)
                    ?.trim()
                    ?.removeSuffix(".")
                    ?.take(100)
                    ?: continue

                if (objectName.length < 2) continue

                val predicate = if (rule.predicateGroup != null) {
                    match.groupValues.getOrNull(rule.predicateGroup)?.lowercase() ?: rule.predicate
                } else {
                    rule.predicate
                }

                triples.add(
                    ExtractedTriple(
                        subjectName = rule.subjectName,
                        subjectType = EntityType.PERSON,
                        predicate = predicate,
                        objectName = objectName,
                        objectType = rule.objectType
                    )
                )
            }

            return triples
        }

        /**
         * Store an extracted triple: ensure entities exist, create relation if new.
         * Static so it can be called from MemoryExtractor for real-time extraction.
         */
        suspend fun storeTriple(
            entityDao: KnowledgeEntityDao,
            relationDao: KnowledgeRelationDao,
            triple: ExtractedTriple,
            sourceFactId: String,
            personaId: String? = null
        ) {
            val now = System.currentTimeMillis()

            val subject = entityDao.getByName(triple.subjectName)?.let { existing ->
                existing.copy(lastSeen = now, mentionCount = existing.mentionCount + 1)
                    .also { entityDao.update(it) }
            } ?: KnowledgeEntity(
                name = triple.subjectName, type = triple.subjectType,
                firstSeen = now, lastSeen = now
            ).also { entityDao.insert(it) }

            val obj = entityDao.getByName(triple.objectName)?.let { existing ->
                existing.copy(lastSeen = now, mentionCount = existing.mentionCount + 1)
                    .also { entityDao.update(it) }
            } ?: KnowledgeEntity(
                name = triple.objectName, type = triple.objectType,
                firstSeen = now, lastSeen = now
            ).also { entityDao.insert(it) }

            val existing = relationDao.findRelationForPersona(subject.id, triple.predicate, obj.id, personaId)
            if (existing == null) {
                relationDao.insert(
                    KnowledgeRelation(
                        subjectId = subject.id, predicate = triple.predicate,
                        objectId = obj.id, sourceFactId = sourceFactId, createdAt = now,
                        personaId = personaId
                    )
                )
                Log.d(TAG, "New triple: ${triple.subjectName} -[${triple.predicate}]-> ${triple.objectName}")
            } else {
                relationDao.update(existing.copy(confidence = (existing.confidence + 0.1f).coerceAtMost(1.0f)))
            }
        }

        /**
         * Build a natural-language context string from the knowledge graph.
         * BFS traversal from entities matching the query, up to maxHops depth.
         * Relations are ranked by confidence * mentionCount for relevance.
         * Can be called directly from ChatViewModel.
         */
        suspend fun buildContextForQuery(
            query: String,
            entityDao: KnowledgeEntityDao,
            relationDao: KnowledgeRelationDao,
            maxHops: Int = 2,
            personaId: String? = null
        ): String {
            val queryWords = query.lowercase().split(Regex("[\\s\\p{Punct}]+")).filter { it.length >= 3 }
            val allEntities = entityDao.getAll()
            val entityById = allEntities.associateBy { it.id }

            val seedEntities = allEntities.filter { entity ->
                val entityNameLower = entity.name.lowercase()
                queryWords.any { word -> entityNameLower.contains(word) || word.contains(entityNameLower) }
            }.toMutableSet()

            // Always include the "User" entity
            allEntities.find { it.name == "User" }?.let { seedEntities.add(it) }

            if (seedEntities.isEmpty()) return ""

            // BFS traversal collecting scored relations
            data class ScoredRelation(
                val subject: String, val predicate: String, val obj: String,
                val score: Float, val depth: Int
            )

            val visited = mutableSetOf<String>()
            val frontier = ArrayDeque(seedEntities.map { it.id })
            val scoredRelations = mutableListOf<ScoredRelation>()

            var depth = 0
            while (frontier.isNotEmpty() && depth < maxHops) {
                val nextFrontier = mutableListOf<String>()
                val currentBatch = frontier.toList()
                frontier.clear()

                for (entityId in currentBatch) {
                    if (entityId in visited) continue
                    visited.add(entityId)

                    val relations = if (personaId != null) {
                        relationDao.getRelationsForEntityAndPersona(entityId, personaId)
                    } else {
                        relationDao.getRelationsForEntity(entityId)
                    }
                    for (rel in relations) {
                        val subjectEntity = entityById[rel.subjectId]
                        val objectEntity = entityById[rel.objectId]
                        if (subjectEntity != null && objectEntity != null) {
                            // Score: confidence * log(mentionCount+1) * depth_decay
                            val mentionScore = kotlin.math.ln((subjectEntity.mentionCount + objectEntity.mentionCount).toFloat() / 2f + 1f)
                            val depthDecay = 1f / (1f + depth * 0.5f) // closer = more relevant
                            val score = rel.confidence * mentionScore * depthDecay

                            // Boost if query words match the relation's entities
                            val queryBoost = if (queryWords.any {
                                objectEntity.name.lowercase().contains(it) ||
                                subjectEntity.name.lowercase().contains(it)
                            }) 2.0f else 1.0f

                            scoredRelations.add(ScoredRelation(
                                subjectEntity.name, rel.predicate, objectEntity.name,
                                score * queryBoost, depth
                            ))

                            if (rel.subjectId !in visited) nextFrontier.add(rel.subjectId)
                            if (rel.objectId !in visited) nextFrontier.add(rel.objectId)
                        }
                    }
                }

                frontier.addAll(nextFrontier)
                depth++
            }

            if (scoredRelations.isEmpty()) return ""

            val lines = scoredRelations
                .distinctBy { "${it.subject}|${it.predicate}|${it.obj}" }
                .sortedByDescending { it.score }
                .take(12) // Top-K ranked relations
                .map { "- ${it.subject} ${predicateToVerb(it.predicate)} ${it.obj}" }

            return "## What you know about them:\n${lines.joinToString("\n")}"
        }

        /**
         * Expand a query using KG entities for better memory retrieval.
         * Returns the original query + related entity names from the graph.
         */
        suspend fun expandQueryWithKG(
            query: String,
            entityDao: KnowledgeEntityDao,
            relationDao: KnowledgeRelationDao,
            personaId: String? = null
        ): String {
            val queryWords = query.lowercase().split(Regex("[\\s\\p{Punct}]+")).filter { it.length >= 3 }
            val allEntities = entityDao.getAll()

            // Find entities matching query words
            val matchedEntities = allEntities.filter { entity ->
                val nameLower = entity.name.lowercase()
                queryWords.any { word -> nameLower.contains(word) || word.contains(nameLower) }
            }

            if (matchedEntities.isEmpty()) return query

            // Get directly related entities (1-hop) to expand the query
            val relatedNames = mutableSetOf<String>()
            for (entity in matchedEntities) {
                val relations = if (personaId != null) {
                    relationDao.getRelationsForEntityAndPersona(entity.id, personaId)
                } else {
                    relationDao.getRelationsForEntity(entity.id)
                }
                for (rel in relations) {
                    val relatedEntity = allEntities.find {
                        it.id == (if (rel.subjectId == entity.id) rel.objectId else rel.subjectId)
                    }
                    relatedEntity?.let { relatedNames.add(it.name) }
                }
            }

            // Append related entity names to the query for broader retrieval
            val expansion = relatedNames.filter { it != "User" }.take(5).joinToString(" ")
            return if (expansion.isNotBlank()) "$query $expansion" else query
        }

        private fun predicateToVerb(predicate: String): String = when (predicate) {
            "lives_in" -> "lives in"
            "from" -> "is from"
            "works_at" -> "works at"
            "likes" -> "likes"
            "dislikes" -> "dislikes"
            "is_a" -> "is a"
            "studies" -> "studies"
            "interested_in" -> "is interested in"
            "named" -> "is named"
            "friend" -> "'s friend is"
            "sister" -> "'s sister is"
            "brother" -> "'s brother is"
            "mother" -> "'s mother is"
            "father" -> "'s father is"
            "partner" -> "'s partner is"
            "wife" -> "'s wife is"
            "husband" -> "'s husband is"
            "colleague" -> "'s colleague is"
            "boss" -> "'s boss is"
            "teacher" -> "'s teacher is"
            else -> predicate.replace("_", " ")
        }
    }

    data class ExtractionRule(
        val pattern: Regex,
        val subjectName: String,
        val predicate: String,
        val objectGroup: Int,
        val objectType: EntityType,
        val predicateGroup: Int? = null
    )

    data class ExtractedTriple(
        val subjectName: String,
        val subjectType: EntityType,
        val predicate: String,
        val objectName: String,
        val objectType: EntityType
    )

    // ==================== WorkManager entry point ====================

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting knowledge graph build")

        return try {
            val db = AppContainer.getDatabase()
            val memoryDao = db.aiMemoryDao()
            val entityDao = db.knowledgeEntityDao()
            val relationDao = db.knowledgeRelationDao()

            val allMemories = memoryDao.getAllOnce()
            val existingRelations = relationDao.getAll()
            val graphedFactIds = existingRelations.mapNotNull { it.sourceFactId }.toSet()
            val ungraphed = allMemories.filter { it.id !in graphedFactIds }

            if (ungraphed.isEmpty()) {
                Log.d(TAG, "No ungraphed memories, skipping")
                return Result.success()
            }

            var extracted = 0
            for (memory in ungraphed) {
                val triples = extractTriplesFromFact(memory.fact)
                for (triple in triples) {
                    storeTriple(entityDao, relationDao, triple, memory.id, memory.personaId)
                    extracted++
                }
            }

            Log.d(TAG, "Knowledge graph build complete: $extracted triples from ${ungraphed.size} facts")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Knowledge graph build failed: ${e.message}", e)
            Result.retry()
        }
    }
}
