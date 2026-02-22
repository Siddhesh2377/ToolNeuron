package com.dark.tool_neuron.neuron_example

import android.util.Log
import kotlin.math.sqrt

enum class RetrievalConfidence { HIGH, MEDIUM, LOW }

data class RetrievalResult(
    val results: List<QueryResult>,
    val confidence: RetrievalConfidence,
    val compressedContext: String
)

/**
 * Orchestrates the full retrieval pipeline:
 * vector search -> BM25 search -> RRF fusion -> MMR diversity -> confidence -> edge expansion -> compression
 */
object RetrievalPipeline {

    private const val TAG = "RetrievalPipeline"

    /**
     * Main query entry point.
     */
    fun query(
        allNodes: Map<String, NeuronNode>,
        queryText: String,
        queryEmbedding: FloatArray,
        searchIndex: ChunkSearchIndex?,
        topK: Int,
        settings: GraphSettings,
        expandEdgesFn: (NeuronNode, Int) -> List<NeuronNode>
    ): RetrievalResult {
        if (allNodes.isEmpty()) {
            return RetrievalResult(emptyList(), RetrievalConfidence.LOW, "")
        }

        // 1. Vector search — cosine similarity over all nodes
        val vectorResults = vectorSearch(allNodes, queryEmbedding)
        Log.d(TAG, "Vector search returned ${vectorResults.size} results")

        // 2. BM25 search — query FTS5 index
        val bm25Results = if (searchIndex != null) {
            bm25Search(searchIndex, queryText)
        } else {
            emptyList()
        }
        Log.d(TAG, "BM25 search returned ${bm25Results.size} results")

        // 3. Reciprocal Rank Fusion — combine both ranked lists
        val fusedResults = reciprocalRankFusion(vectorResults, bm25Results)
        Log.d(TAG, "RRF produced ${fusedResults.size} fused results")

        // 4. MMR diversity selection — pick top-K diverse results
        val diverseResults = mmrSelect(fusedResults, queryEmbedding, allNodes, topK)
        Log.d(TAG, "MMR selected ${diverseResults.size} diverse results")

        // 5. Assess confidence
        val confidence = assessConfidence(fusedResults.map { it.second })
        Log.d(TAG, "Retrieval confidence: $confidence")

        // 6. Expand by edges — graph traversal for context
        val expandedResults = diverseResults.map { (nodeId, score) ->
            val node = allNodes[nodeId] ?: return@map null
            val connectedNodes = expandEdgesFn(node, settings.traversalDepth)
            QueryResult(
                node = node,
                score = score,
                connectedNodes = connectedNodes
            )
        }.filterNotNull()

        // 7. Compress context — dedup + filter
        val compressedContext = compressContext(expandedResults, queryText)

        return RetrievalResult(expandedResults, confidence, compressedContext)
    }

    /**
     * Vector search: cosine similarity over all nodes, returning (nodeId, score) ranked list.
     */
    private fun vectorSearch(
        allNodes: Map<String, NeuronNode>,
        queryEmbedding: FloatArray
    ): List<Pair<String, Float>> {
        return allNodes.values
            .mapNotNull { node ->
                val emb = node.embedding ?: return@mapNotNull null
                val score = cosineSimilarity(queryEmbedding, emb)
                if (score > 0.1f) node.id to score else null
            }
            .sortedByDescending { it.second }
    }

    /**
     * BM25 search via FTS5 index.
     */
    private fun bm25Search(
        searchIndex: ChunkSearchIndex,
        queryText: String
    ): List<Pair<String, Float>> {
        return searchIndex.search(queryText, limit = 20)
            .map { it.nodeId to it.score }
    }

    /**
     * Reciprocal Rank Fusion: combine two ranked lists by rank position.
     * RRF_score(d) = sum(1/(k + rank_i(d))) for each retrieval system.
     * Nodes appearing in both lists get boosted.
     */
    fun reciprocalRankFusion(
        vectorResults: List<Pair<String, Float>>,
        bm25Results: List<Pair<String, Float>>,
        k: Int = 60
    ): List<Pair<String, Float>> {
        val scores = mutableMapOf<String, Float>()

        // Score from vector search ranks
        vectorResults.forEachIndexed { rank, (nodeId, _) ->
            scores[nodeId] = (scores[nodeId] ?: 0f) + 1f / (k + rank + 1)
        }

        // Score from BM25 ranks
        bm25Results.forEachIndexed { rank, (nodeId, _) ->
            scores[nodeId] = (scores[nodeId] ?: 0f) + 1f / (k + rank + 1)
        }

        return scores.entries
            .map { it.key to it.value }
            .sortedByDescending { it.second }
    }

    /**
     * Maximal Marginal Relevance: diversity-aware top-K selection.
     * Greedy: pick candidate maximizing lambda * relevance - (1 - lambda) * max_sim_to_selected.
     * Prevents returning near-identical chunks.
     */
    fun mmrSelect(
        candidates: List<Pair<String, Float>>,
        queryEmbedding: FloatArray,
        allNodes: Map<String, NeuronNode>,
        topK: Int,
        lambda: Float = 0.7f
    ): List<Pair<String, Float>> {
        if (candidates.isEmpty()) return emptyList()
        if (candidates.size <= topK) return candidates

        val selected = mutableListOf<Pair<String, Float>>()
        val remaining = candidates.toMutableList()

        // Normalize RRF scores to [0, 1] for fair comparison with cosine similarity
        val maxScore = remaining.maxOf { it.second }
        val minScore = remaining.minOf { it.second }
        val scoreRange = maxScore - minScore

        while (selected.size < topK && remaining.isNotEmpty()) {
            var bestIdx = 0
            var bestMmrScore = Float.NEGATIVE_INFINITY

            for (i in remaining.indices) {
                val (nodeId, rrfScore) = remaining[i]
                val normalizedRelevance = if (scoreRange > 0) (rrfScore - minScore) / scoreRange else 1f

                // Max similarity to already selected nodes
                val maxSimToSelected = if (selected.isEmpty()) {
                    0f
                } else {
                    val candidateEmb = allNodes[nodeId]?.embedding
                    if (candidateEmb != null) {
                        selected.maxOf { (selId, _) ->
                            val selEmb = allNodes[selId]?.embedding
                            if (selEmb != null) cosineSimilarity(candidateEmb, selEmb) else 0f
                        }
                    } else {
                        0f
                    }
                }

                val mmrScore = lambda * normalizedRelevance - (1f - lambda) * maxSimToSelected
                if (mmrScore > bestMmrScore) {
                    bestMmrScore = mmrScore
                    bestIdx = i
                }
            }

            selected.add(remaining.removeAt(bestIdx))
        }

        return selected
    }

    /**
     * Assess retrieval confidence based on top RRF scores.
     * HIGH: top score >= 0.025 (appears in both lists with good ranks)
     * MEDIUM: top score >= 0.015
     * LOW: below thresholds or empty
     */
    fun assessConfidence(topScores: List<Float>): RetrievalConfidence {
        if (topScores.isEmpty()) return RetrievalConfidence.LOW

        val topScore = topScores.first()
        return when {
            topScore >= 0.025f -> RetrievalConfidence.HIGH
            topScore >= 0.015f -> RetrievalConfidence.MEDIUM
            else -> RetrievalConfidence.LOW
        }
    }

    /**
     * Compress context: deduplicate, filter irrelevant sentences, order by score, truncate.
     */
    fun compressContext(
        results: List<QueryResult>,
        query: String,
        maxChunks: Int = 5
    ): String {
        if (results.isEmpty()) return ""

        val queryTokens = tokenize(query)

        // Collect chunks ordered by score (highest first — mitigates "lost in the middle" bias)
        val chunks = results
            .sortedByDescending { it.score }
            .take(maxChunks)

        // Deduplicate: Jaccard similarity > 0.8 between chunk token sets -> merge
        val deduplicated = mutableListOf<QueryResult>()
        for (chunk in chunks) {
            val chunkTokens = tokenize(chunk.node.content).toSet()
            val isDuplicate = deduplicated.any { existing ->
                val existingTokens = tokenize(existing.node.content).toSet()
                jaccardSimilarity(chunkTokens, existingTokens) > 0.8f
            }
            if (!isDuplicate) {
                deduplicated.add(chunk)
            }
        }

        // Filter sentences: remove sentences with zero token overlap with query
        val compressedChunks = deduplicated.map { result ->
            val sentences = splitSentences(result.node.content)
            val filtered = sentences.filter { sentence ->
                val sentenceTokens = tokenize(sentence).toSet()
                sentenceTokens.any { it in queryTokens }
            }
            // If filtering removes everything, keep the original (better than empty)
            if (filtered.isEmpty()) {
                result.node.content.take(500)
            } else {
                filtered.joinToString(" ")
            }
        }

        return compressedChunks.joinToString("\n\n")
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

    private fun tokenize(text: String): List<String> {
        return text.lowercase()
            .split(Regex("[\\s\\p{Punct}]+"))
            .filter { it.length >= 2 }
    }

    private fun jaccardSimilarity(a: Set<String>, b: Set<String>): Float {
        if (a.isEmpty() && b.isEmpty()) return 1f
        val intersection = a.intersect(b).size
        val union = a.union(b).size
        return if (union > 0) intersection.toFloat() / union else 0f
    }

    private fun splitSentences(text: String): List<String> {
        return text.split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
}
