package com.mp.data_hub_lib.worker

import android.content.Context
import android.util.Log
import com.mp.data_hub_lib.DataNativeLib
import com.mp.data_hub_lib.db.DataHubDAO
import com.mp.data_hub_lib.db.DataHubDatabase
import com.mp.data_hub_lib.db.DataHubDatabaseProvider
import com.mp.data_hub_lib.model.DataSetModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import kotlin.math.sqrt

class DataHubWorker(
    private val context: Context,
    database: DataHubDatabase = DataHubDatabaseProvider.getDatabase(context),
    val dataNativeLib: DataNativeLib = DataNativeLib()
) {
    private val dataHubDAO: DataHubDAO = database.dataHubDAO()

    private var coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Document data class
    data class Document(
        val id: String, val text: String, val category: String, val embedding: List<Float>
    )

    // Load a data pack
    fun loadPack(dfvdsafds: String, sfbsdkbc: String, onResult: (Boolean, String?) -> Unit) {
        coroutineScope.launch {
            val success = dataNativeLib.loadVecx(dfvdsafds, sfbsdkbc)
            onResult(success, if (success) null else "Failed to load pack")
            coroutineScope.cancel()
        }
    }

    fun installDataPack(srcFile: File, key: String, onResult: (Boolean) -> Unit = {}) {
        coroutineScope.launch {
            try {
                Log.d("DataPack", "Starting installation for ${srcFile.name}")

                // Ensure base folder exists
                val baseDir = File(context.filesDir, "dataHub").apply { mkdirs() }
                Log.d("DataPack", "Base directory ready at: ${baseDir.absolutePath}")

                // Target path for this data pack
                val root = File(baseDir, srcFile.name)
                Log.d("DataPack", "Target path set to: ${root.absolutePath}")

                // Copy (overwrite if exists)
                Log.d("DataPack", "Copying file...")
                srcFile.copyTo(root, overwrite = true)
                Log.d("DataPack", "File copied successfully")

                // Load encrypted vecx
                Log.d("DataPack", "Loading vecx with key... $key")
                val ok = dataNativeLib.loadVecx(root.absolutePath, key)
                if (!ok) {
                    Log.e("DataPack", "Vecx load failed for ${root.name}")
                    onResult(false)
                    return@launch
                }
                Log.d("DataPack", "Vecx loaded successfully")

                // Load manifest
                Log.d("DataPack", "Loading manifest...")
                val manifest = dataNativeLib.loadManifest() ?: run {
                    Log.e("DataPack", "Manifest load failed")
                    onResult(false)
                    return@launch
                }
                Log.d("DataPack", "Manifest loaded: name=${manifest.name}, author=${manifest.author}")

                // Build model
                val docCount = getDocumentCount()
                Log.d("DataPack", "Document count: $docCount")

                val model = DataSetModel(
                    modelName = manifest.name,
                    modelDescription = manifest.description,
                    modelPath = root.absolutePath,
                    modelAuthor = manifest.author,
                    modelCreated = manifest.issued,
                    documentCount = docCount
                )

                // Insert or overwrite
                Log.d("DataPack", "Inserting model into database...")
                dataHubDAO.insertModel(model)
                Log.d("DataPack", "Model inserted successfully: ${model.modelName}")

                onResult(true)
                Log.d("DataPack", "Installation completed successfully")

            } catch (e: Exception) {
                Log.e("DataPack", "Failed to install model: ${e.message}", e)
                onResult(false)
            }
        }
    }


    // Get document count from "m" entity
    private fun getDocumentCount(): Int {
        return try {
            val mJson = dataNativeLib.getEntity("m")
            val mArr = JSONArray(mJson)
            mArr.length()
        } catch (e: Exception) {
            0
        }
    }

    // Get all documents for a model
    fun getDocumentsForModel(onResult: (List<Document>?) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dJson = dataNativeLib.getEntity("D")
                val dArr = JSONArray(dJson)
                val documents = mutableListOf<Document>()

                for (i in 0 until dArr.length()) {
                    val docObj = dArr.getJSONObject(i)
                    val embeddingArr = docObj.getJSONArray("embedding")
                    val embedding = mutableListOf<Float>()

                    for (j in 0 until embeddingArr.length()) {
                        embedding.add(embeddingArr.getDouble(j).toFloat())
                    }

                    documents.add(
                        Document(
                            id = docObj.getString("id"),
                            text = docObj.getString("text"),
                            category = docObj.optString("category", "Unknown"),
                            embedding = embedding
                        )
                    )
                }

                withContext(Dispatchers.Main) {
                    onResult(documents)
                }
            } catch (e: Exception) {
                println("Failed to get documents: ${e.message}")
                withContext(Dispatchers.Main) {
                    onResult(null)
                }
            }
        }
    }

    // Search documents using cosine similarity
    fun searchDataSets(
        queryEmbedding: FloatArray,
        topK: Int = 5,
        onResult: (List<Document>?) -> Unit
    ) {
        getDocumentsForModel { documents ->
            if (documents == null) {
                onResult(null)
                return@getDocumentsForModel
            }

            CoroutineScope(Dispatchers.Default).launch {
                val results = documents.map { doc ->
                    val similarity = cosineSimilarity(doc.embedding.toFloatArray(), queryEmbedding)
                    doc to similarity
                }.sortedByDescending { it.second }.take(topK).map { it.first }

                withContext(Dispatchers.Main) {
                    onResult(results)
                }
            }
        }
    }

    // Cosine similarity calculation
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Double {
        if (a.size != b.size) return 0.0
        val dot = a.zip(b).sumOf { (x, y) -> (x * y).toDouble() }
        val normA = sqrt(a.sumOf { (it * it).toDouble() })
        val normB = sqrt(b.sumOf { (it * it).toDouble() })
        return if (normA != 0.0 && normB != 0.0) dot / (normA * normB) else 0.0
    }

    // Database operations
    fun getAllModels(): StateFlow<List<DataSetModel>> {
        return dataHubDAO.getAllModels().stateIn(
            CoroutineScope(Dispatchers.IO), SharingStarted.Lazily, emptyList()
        )
    }


    suspend fun getModelByName(modelName: String): DataSetModel? =
        dataHubDAO.getModelByName(modelName)

    fun deleteModel(modelName: String, onResult: (Boolean) -> Unit = {}) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val model = dataHubDAO.getModelByName(modelName)
                if (model != null) {
                    val datasetDir = File(model.modelPath)
                    if (datasetDir.exists()) {
                        datasetDir.deleteRecursively()
                    }
                    dataHubDAO.deleteModel(model)
                    withContext(Dispatchers.Main) {
                        onResult(true)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onResult(false)
                    }
                }
            } catch (e: Exception) {
                println("Failed to delete model: ${e.message}")
                withContext(Dispatchers.Main) {
                    onResult(false)
                }
            }
        }
    }
}