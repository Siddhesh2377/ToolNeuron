package com.dark.tool_neuron.neuron_example

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ln

data class BM25Result(
    val nodeId: String,
    val score: Float
)

/**
 * Lightweight FTS4 wrapper using Android's in-memory SQLite for BM25 text search.
 * Built at runtime when a graph is loaded (not serialized).
 *
 * Uses FTS4 (available on all Android versions) with matchinfo('pcnalx') to compute
 * Okapi BM25 scores manually.
 */
class ChunkSearchIndex {

    private var db: SQLiteDatabase? = null

    companion object {
        private const val TAG = "ChunkSearchIndex"
        // BM25 parameters
        private const val K1 = 1.2f
        private const val B = 0.75f
    }

    init {
        db = SQLiteDatabase.create(null).apply {
            execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS chunks USING fts4(node_id, content, notindexed=node_id)")
        }
    }

    /**
     * Batch insert nodes into the FTS4 index.
     */
    fun populate(nodes: Collection<NeuronNode>) {
        val database = db ?: return
        database.beginTransaction()
        try {
            val stmt = database.compileStatement("INSERT INTO chunks(node_id, content) VALUES (?, ?)")
            for (node in nodes) {
                stmt.bindString(1, node.id)
                stmt.bindString(2, node.content)
                stmt.executeInsert()
                stmt.clearBindings()
            }
            database.setTransactionSuccessful()
            Log.d(TAG, "FTS4 index built: ${nodes.size} documents")
        } finally {
            database.endTransaction()
        }
    }

    /**
     * Search the FTS4 index using BM25 ranking.
     * Tokenizes query, joins with OR for broad matching.
     * Computes Okapi BM25 from matchinfo('pcnalx').
     */
    fun search(query: String, limit: Int = 20): List<BM25Result> {
        val database = db ?: return emptyList()

        val tokens = query
            .split(Regex("\\s+"))
            .map { it.replace(Regex("[^\\w]"), "") }
            .filter { it.length >= 2 }

        if (tokens.isEmpty()) return emptyList()

        val ftsQuery = tokens.joinToString(" OR ")

        val results = mutableListOf<BM25Result>()
        try {
            database.rawQuery(
                "SELECT node_id, matchinfo(chunks, 'pcnalx') FROM chunks WHERE chunks MATCH ?",
                arrayOf(ftsQuery)
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val nodeId = cursor.getString(0)
                    val matchInfoBlob = cursor.getBlob(1)
                    val score = computeBM25(matchInfoBlob)
                    if (score > 0f) {
                        results.add(BM25Result(nodeId, score))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "FTS4 search failed: ${e.message}")
        }

        return results
            .sortedByDescending { it.score }
            .take(limit)
    }

    /**
     * Compute Okapi BM25 score from FTS4 matchinfo('pcnalx') blob.
     *
     * matchinfo('pcnalx') returns an array of unsigned 32-bit integers:
     *   [p, c, n, a_0..a_{c-1}, l_0..l_{c-1}, x_data...]
     * where x_data has p*c groups of 3 values each:
     *   [hits_this_row, hits_all_rows, docs_with_hit]
     *
     * We only score column 1 (content), skipping column 0 (node_id).
     */
    private fun computeBM25(blob: ByteArray): Float {
        val buffer = ByteBuffer.wrap(blob).order(ByteOrder.nativeOrder())
        val ints = IntArray(blob.size / 4) { buffer.getInt() }

        if (ints.size < 3) return 0f

        val p = ints[0]  // number of matchable phrases
        val c = ints[1]  // number of columns (2: node_id, content)
        val n = ints[2]  // total number of rows

        if (n == 0 || c < 2) return 0f

        // a[j] = average token count for column j (starts at index 3)
        val avgDlContent = ints[3 + 1].toFloat() // column 1 = content

        // l[j] = token count for current row, column j (starts at index 3 + c)
        val dlContent = ints[3 + c + 1].toFloat() // column 1 = content

        // x_data starts at index 3 + 2*c
        // For each phrase i, column j: x[3 + 2*c + 3*(i*c + j) + 0..2]
        val xBase = 3 + 2 * c

        var score = 0f
        for (i in 0 until p) {
            // Only score content column (j=1)
            val j = 1
            val xIdx = xBase + 3 * (i * c + j)
            if (xIdx + 2 >= ints.size) break

            val tf = ints[xIdx].toFloat()      // hits in this row
            val df = ints[xIdx + 2].toFloat()   // docs containing this phrase

            if (tf <= 0f || df <= 0f) continue

            // IDF: log((N - df + 0.5) / (df + 0.5))
            val idf = ln((n - df + 0.5f) / (df + 0.5f))
            if (idf <= 0f) continue

            // TF normalization: (tf * (k1 + 1)) / (tf + k1 * (1 - b + b * dl/avgdl))
            val avgDl = if (avgDlContent > 0f) avgDlContent else 1f
            val tfNorm = (tf * (K1 + 1f)) / (tf + K1 * (1f - B + B * dlContent / avgDl))

            score += idf * tfNorm
        }

        return score
    }

    /**
     * Clear all entries from the index.
     */
    fun clear() {
        try {
            db?.execSQL("DELETE FROM chunks")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear index: ${e.message}")
        }
    }

    /**
     * Close the in-memory SQLite database.
     */
    fun close() {
        try {
            db?.close()
            db = null
            Log.d(TAG, "FTS4 index closed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to close index: ${e.message}")
        }
    }
}
