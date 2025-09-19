package com.dark.neuroverse.activity

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.dark.neuroverse.ui.theme.NeuroVerseTheme
import com.mp.ai_core.EmbeddingManager
import com.mp.ai_core.NativeLib
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.sqrt

// --- Data models ---
@Serializable
data class BrainRoot(val docs: List<BrainDoc>)

@Serializable
data class BrainDoc(
    val text: String, val embedding: List<Float>
)

data class Doc(
    val text: String, val similarity: Double
)

data class GenerationStats(
    val tokenCount: Int, val totalTime: Long, val tokensPerSecond: Float
)

// --- Brain Decoder and Vector Store ---
object BrainDecoder {
    private const val KEY = "secret123"
    private var docs: List<BrainDoc> = emptyList()
    private var isLoaded = false

    fun decodeXorFromBytes(bytes: ByteArray): String {
        val keyBytes = KEY.toByteArray(Charsets.UTF_8)
        val result = ByteArray(bytes.size)
        for (i in bytes.indices) {
            result[i] = (bytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
        }
        return result.toString(Charsets.UTF_8)
    }

    fun loadBrain(path: String): BrainRoot? {
        return try {
            val raw = File(path).readBytes()
            val decoded = decodeXorFromBytes(raw)
            val brainRoot = Json.decodeFromString<BrainRoot>(decoded)
            docs = brainRoot.docs
            isLoaded = true
            Log.i("BrainDecoder", "Loaded ${docs.size} documents")
            brainRoot
        } catch (e: Exception) {
            Log.e("BrainDecoder", "Failed to load brain: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    fun search(queryEmbedding: FloatArray, topK: Int = 5): List<Doc> {
        if (!isLoaded || docs.isEmpty()) {
            Log.w("BrainDecoder", "Brain not loaded or empty")
            return emptyList()
        }

        return docs.map { doc ->
            val similarity = cosineSimilarity(doc.embedding.toFloatArray(), queryEmbedding)
            Doc(doc.text, similarity)
        }.sortedByDescending { it.similarity }.take(topK)
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Double {
        if (a.size != b.size) return 0.0

        val dot = a.zip(b).sumOf { (x, y) -> (x * y).toDouble() }
        val normA = sqrt(a.sumOf { (it * it).toDouble() })
        val normB = sqrt(b.sumOf { (it * it).toDouble() })

        return if (normA != 0.0 && normB != 0.0) dot / (normA * normB) else 0.0
    }
}

// Enum to represent the state of the model and data loading
enum class AppStatus {
    LOADING_MODEL,
    LOADING_BRAIN,
    READY,
    ERROR
}

// --- Activity ---
class TempActivity : ComponentActivity() {

    private val m1 = "/storage/emulated/0/Download/Models/Kodify-Nano-2.0.Q8_0.gguf"
    private val native = NativeLib()
    private lateinit var embeddingManager: EmbeddingManager
    private val brainPath = "/storage/emulated/0/Download/ai/embeddings.brain"

    private val appStatus = MutableStateFlow(AppStatus.LOADING_MODEL)

    @SuppressLint("DefaultLocale")
    @RequiresApi(Build.VERSION_CODES.S)
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Fix: Initialize embeddingManager before setContent
        embeddingManager = EmbeddingManager(native)

        setContent {
            NeuroVerseTheme {
                val status by appStatus.collectAsState()
                var query by remember { mutableStateOf("") }
                var response by remember { mutableStateOf("") }
                var searchResults by remember { mutableStateOf<List<Doc>>(emptyList()) }
                var isProcessing by remember { mutableStateOf(false) }
                var stats by remember { mutableStateOf<GenerationStats?>(null) }
                var errorMessage by remember { mutableStateOf("") }

                // All initialization logic in a single LaunchedEffect for sequential execution
                LaunchedEffect(Unit) {
                    withContext(Dispatchers.IO) {
                        try {
                            // 1. Initialize embedding model
                            Log.d("TempActivity", "Starting model initialization")
                            appStatus.value = AppStatus.LOADING_MODEL
                            val modelInitResult = embeddingManager.initializeEmbedding(
                                modelPath = "/storage/emulated/0/Download/ai/all-MiniLM-L6-v2-Q8_0.gguf"
                            )

                            modelInitResult.onFailure {
                                Log.e("TempActivity", "Embedding init failed: ${it.message}")
                                appStatus.value = AppStatus.ERROR
                                errorMessage = "Embedding model failed to initialize."
                                return@withContext
                            }

                            // 2. Load the brain data
                            Log.d("TempActivity", "Model ready. Loading brain data...")
                            appStatus.value = AppStatus.LOADING_BRAIN
                            val brainRoot = BrainDecoder.loadBrain(brainPath)

                            if (brainRoot == null) {
                                Log.e("TempActivity", "Brain file failed to load.")
                                appStatus.value = AppStatus.ERROR
                                errorMessage = "Failed to load knowledge base."
                                return@withContext
                            }

                            // 3. All ready
                            Log.d("TempActivity", "Brain loaded. App is ready.")
                            appStatus.value = AppStatus.READY
                            errorMessage = ""
                            withContext(Dispatchers.Main) {
                                response = "App is ready. You can now search."
                            }

                        } catch (e: Exception) {
                            Log.e("TempActivity", "Initialization error: ${e.message}", e)
                            appStatus.value = AppStatus.ERROR
                            errorMessage = "Unexpected error during startup: ${e.message}"
                        }
                    }
                }

                Scaffold(
                    topBar = {
                        TopAppBar(title = { Text("RAG Brain Viewer") })
                    },
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .padding(padding)
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Check status and show appropriate loading screen
                        when (status) {
                            AppStatus.LOADING_MODEL -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text("Loading AI model...")
                                }
                            }
                            AppStatus.LOADING_BRAIN -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text("Loading knowledge base...")
                                }
                            }
                            AppStatus.ERROR -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Error: $errorMessage",
                                            modifier = Modifier.padding(16.dp),
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }
                            AppStatus.READY -> {
                                // Content for when the app is ready to use
                                OutlinedTextField(
                                    value = query,
                                    onValueChange = { query = it },
                                    label = { Text("Enter your query") },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !isProcessing
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Button(
                                    onClick = {
                                        lifecycleScope.launch {
                                            performRAG(
                                                query = query,
                                                onUpdate = { response = it },
                                                onSearchResults = { searchResults = it },
                                                onDone = { stats = it },
                                                onError = { errorMessage = it },
                                                onProcessingChange = { isProcessing = it }
                                            )
                                        }
                                    },
                                    enabled = !isProcessing && query.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (isProcessing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp), strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Processing...")
                                    } else {
                                        Text("Search & Generate")
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (searchResults.isNotEmpty()) {
                                        item {
                                            Text(
                                                "Search Results:",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        items(searchResults) { doc ->
                                            Card {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Text(
                                                        "Similarity: ${String.format("%.3f", doc.similarity)}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        doc.text,
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                            }
                                        }
                                        item { Spacer(modifier = Modifier.height(8.dp)) }
                                    }

                                    if (response.isNotEmpty()) {
                                        item {
                                            Text(
                                                "Generated Response:",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        item {
                                            Card {
                                                Text(
                                                    response,
                                                    modifier = Modifier.padding(16.dp),
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                        }
                                    }

                                    stats?.let { genStats ->
                                        item {
                                            Card {
                                                Column(modifier = Modifier.padding(16.dp)) {
                                                    Text(
                                                        "Generation Stats:",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text("Tokens: ${genStats.tokenCount}")
                                                    Text("Time: ${genStats.totalTime}ms")
                                                    Text(
                                                        "Speed: ${String.format("%.2f", genStats.tokensPerSecond)} tokens/sec"
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun performRAG(
        query: String,
        onUpdate: (String) -> Unit,
        onSearchResults: (List<Doc>) -> Unit,
        onDone: (GenerationStats) -> Unit,
        onError: (String) -> Unit,
        onProcessingChange: (Boolean) -> Unit
    ) = withContext(Dispatchers.IO) {
        onProcessingChange(true)
        onError("") // Clear previous errors

        try {
            if (query.isBlank()) {
                onError("Please enter a valid query")
                return@withContext
            }

            val startTime = System.currentTimeMillis()

            // Get query embedding
            Log.i("TempActivity", "Getting embedding for query: $query")
            val queryEmbedding = embeddingManager.getEmbedding(query).getOrElse {
                onError("Error embedding query: ${it.message}")
                Log.e("TempActivity", "Error embedding query: ${it.message}")
                return@withContext
            }

            // Search similar documents
            Log.i("TempActivity", "Searching similar documents...")
            val topDocs = BrainDecoder.search(queryEmbedding, topK = 5)

            withContext(Dispatchers.Main) {
                onSearchResults(topDocs)
            }

            if (topDocs.isEmpty()) {
                onError("No relevant documents found")
                return@withContext
            }

            // Prepare context and prompt
            val context = topDocs.joinToString("\n\n") { "Context: ${it.text}" }
            val prompt = """Based on the following context, please answer the question.
                Context:
                $context
                Question: $query
                Answer:"""

            Log.i("TempActivity", "Initializing model for generation...")

            // Initialize model
            val ok = native.initModel(
                path = m1,
                threads = (Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1),
                gpuLayers = 10,
                useMMAP = true,
                useMLOCK = false,
                ctxSize = 4096,
                temp = 0.7f,
                topK = 40,
                topP = 0.9f,
                minP = 0.0f
            )

            if (!ok) {
                onError("Failed to initialize model at $m1")
                return@withContext
            }

            Log.i("TempActivity", "Starting text generation...")

            var tokenCount = 0
            var fullResponse = ""

            // Generate response
            native.generateStreaming(
                prompt = prompt,
                maxTokens = 512,
                uiScope = CoroutineScope(Dispatchers.Main),
                onStart = {
                    Log.i("TempActivity", "Generation started")
                    onUpdate("")
                },
                onGenerate = { token ->
                    fullResponse += token
                    tokenCount++
                    onUpdate(fullResponse)
                },
                onError = { err ->
                    Log.e("TempActivity", "Generation error: $err")
                    onError("Generation error: $err")
                },
                onDone = {
                    val totalTime = System.currentTimeMillis() - startTime
                    val tokensPerSecond = if (totalTime > 0) tokenCount * 1000f / totalTime else 0f
                    Log.i(
                        "TempActivity",
                        "Generation completed. Tokens: $tokenCount, Time: ${totalTime}ms"
                    )
                    onDone(GenerationStats(tokenCount, totalTime, tokensPerSecond))
                })

        } catch (e: Exception) {
            Log.e("TempActivity", "RAG error: ${e.message}", e)
            onError("Unexpected error: ${e.message}")
        } finally {
            onProcessingChange(false)
        }
    }
}
