package com.dark.neuroverse.activity

import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.dark.neuroverse.BuildConfig
import com.dark.neuroverse.ui.theme.NeuroVerseTheme
import com.mp.data_hub_lib.manager.DataHubManager
import com.mp.data_hub_lib.worker.BrainDecoder
import com.mp.data_hub_lib.worker.BrainRoot
import com.mp.data_hub_lib.worker.Doc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ScrapActivity : ComponentActivity() {

    private val embeddingModelPath = "/storage/emulated/0/Download/ai/all-MiniLM-L6-v2-Q8_0.gguf"
    private val m1 = "/storage/emulated/0/Download/Models/Kodify-Nano-2.0.Q8_0.gguf"

    @OptIn(ExperimentalMaterial3Api::class)
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val zipFile = File(downloadsDir, "ai/protected_embeddings.zip")

//        setContent {
//            NeuroVerseTheme {
//                var query by remember { mutableStateOf("") }
//                var isProcessing by remember { mutableStateOf(false) }
//                var errorMessage by remember { mutableStateOf("") }
//                var brainRoot by remember { mutableStateOf<BrainRoot?>(null) }
//                var searchResults by remember { mutableStateOf<List<Doc>>(emptyList()) }
//                var currentModelName by remember { mutableStateOf<String?>(null) }
//                var response by remember { mutableStateOf("") }
//                var stats by remember { mutableStateOf<GenerationStats?>(null) }
//
//                LaunchedEffect(Unit) {
//                    DataHubManager.loadPack(, zipFile.absolutePath) { success, modelName ->
//                        if (success && modelName != null) {
//                            currentModelName = modelName
//                            val mJson = DataHubManager.getWorker()?.dataNativeLib?.getEntity("D")
//                            val root = BrainDecoder.loadBrain(mJson ?: "")
//                            brainRoot = root
//                        } else {
//                            errorMessage = "Failed to install datapack."
//                        }
//                    }
//                }
//
//                fun performLocalSearch(queryEmbedding: FloatArray) {
//                    if (brainRoot != null) {
//                        searchResults = DataHubManager.search(queryEmbedding)
//                        Log.d("BrainDecoder", "Search results: $searchResults")
//                    } else {
//                        errorMessage = "Brain not loaded"
//                    }
//                }
//
//                Scaffold(
//                    topBar = { TopAppBar(title = { Text("NeuroVerse (Scrap RAG)") }) },
//                    floatingActionButton = {
//                        FloatingActionButton(onClick = {
//                            val queryEmbedding = FloatArray(384) { 0.1f }
//                            performLocalSearch(queryEmbedding)
//                        }) {
//                            Icon(Icons.Default.Add, contentDescription = "Search")
//                        }
//                    }
//                ) { padding ->
//                    Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
//                        when (status) {
//                            DataHubManager.Status.LOADING -> {
//                                CenteredLoading("Loading datapack...")
//                            }
//                            DataHubManager.Status.ERROR -> {
//                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
//                                    Text(
//                                        text = "Error: $errorMessage",
//                                        modifier = Modifier.padding(16.dp),
//                                        color = MaterialTheme.colorScheme.onErrorContainer
//                                    )
//                                }
//                            }
//                            DataHubManager.Status.READY -> {
//                                OutlinedTextField(
//                                    value = query,
//                                    onValueChange = { query = it },
//                                    label = { Text("Enter query for RAG") },
//                                    modifier = Modifier.fillMaxWidth(),
//                                    enabled = !isProcessing
//                                )
//
//                                Spacer(modifier = Modifier.height(8.dp))
//
//                                Button(
//                                    onClick = {
//                                        lifecycleScope.launch {
//                                            performRAG(
//                                                query = query,
//                                                onUpdate = { response = it },
//                                                onSearchResults = { searchResults = it },
//                                                onDone = { stats = it },
//                                                onError = { errorMessage = it },
//                                                onProcessingChange = { isProcessing = it }
//                                            )
//                                        }
//                                    },
//                                    enabled = !isProcessing && query.isNotBlank(),
//                                    modifier = Modifier.fillMaxWidth()
//                                ) {
//                                    if (isProcessing) {
//                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
//                                        Spacer(modifier = Modifier.size(8.dp))
//                                        Text("Processing...")
//                                    } else {
//                                        Text("Search & Generate")
//                                    }
//                                }
//
//                                Spacer(modifier = Modifier.height(12.dp))
//
//                                LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
//                                    if (searchResults.isNotEmpty()) {
//                                        items(searchResults) { doc ->
//                                            Card(modifier = Modifier.fillMaxWidth()) {
//                                                Column(modifier = Modifier.padding(12.dp)) {
//                                                    Text(text = "Similarity: ${String.format("%.3f", doc.similarity)}", style = MaterialTheme.typography.labelSmall)
//                                                    Spacer(modifier = Modifier.height(4.dp))
//                                                    Text(text = doc.text, style = MaterialTheme.typography.bodySmall)
//                                                }
//                                            }
//                                        }
//                                    }
//
//                                    if (response.isNotBlank()) {
//                                        item {
//                                            Card(modifier = Modifier.fillMaxWidth()) {
//                                                Text(response, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
//                                            }
//                                        }
//                                    }
//
//                                    stats?.let { s ->
//                                        item {
//                                            Card(modifier = Modifier.fillMaxWidth()) {
//                                                Column(modifier = Modifier.padding(12.dp)) {
//                                                    Text("Generation Stats:")
//                                                    Spacer(modifier = Modifier.height(4.dp))
//                                                    Text("Tokens: ${s.tokenCount}")
//                                                    Text("Time: ${s.totalTime}ms")
//                                                    Text("Speed: ${String.format("%.2f", s.tokensPerSecond)} tokens/sec")
//                                                }
//                                            }
//                                        }
//                                    }
//                                }
//                            }
//                            else -> {
//                                CenteredLoading("Initializing...")
//                            }
//                        }
//                    }
//                }
//            }
//        }
    }

    @Composable
    private fun CenteredLoading(text: String) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(text)
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
        onError("")

        try {
            if (query.isBlank()) {
                onError("Please enter a valid query")
                return@withContext
            }

            val startTime = System.currentTimeMillis()

            val queryEmbedding = DataHubManager.getQueryEmbedding(query, embeddingModelPath)
            if (queryEmbedding == null) {
                onError("Failed to get query embedding")
                return@withContext
            }

            val topDocs = DataHubManager.search(queryEmbedding, topK = 5)
            withContext(Dispatchers.Main) {
                onSearchResults(topDocs)
            }

            if (topDocs.isEmpty()) {
                onError("No relevant documents found")
                return@withContext
            }

            val context = topDocs.joinToString("\n\n") { "Context: ${it.text}" }
            val prompt = """Based on the following context, please answer the question.
                Context:
                $context
                Question: $query
                Answer:"""

            val native = DataHubManager.getNative() ?: return@withContext
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

            var tokenCount = 0
            var fullResponse = ""

            native.generateStreaming(
                prompt = prompt,
                maxTokens = 512,
                uiScope = CoroutineScope(Dispatchers.Main),
                onStart = {
                    onUpdate("")
                },
                onGenerate = { token ->
                    fullResponse += token
                    tokenCount++
                    onUpdate(fullResponse)
                },
                onError = { err ->
                    onError("Generation error: $err")
                    Log.e("ScrapActivity", "Generation error: $err")
                },
                onDone = {
                    val totalTime = System.currentTimeMillis() - startTime
                    val tokensPerSecond = if (totalTime > 0) tokenCount * 1000f / totalTime else 0f
                    onDone(GenerationStats(tokenCount, totalTime, tokensPerSecond))
                }
            )

        } catch (e: Exception) {
            Log.e("ScrapActivity", "RAG error: ${e.message}", e)
            onError("Unexpected error: ${e.message}")
        } finally {
            onProcessingChange(false)
        }
    }

    data class GenerationStats(val tokenCount: Int, val totalTime: Long, val tokensPerSecond: Float)
}