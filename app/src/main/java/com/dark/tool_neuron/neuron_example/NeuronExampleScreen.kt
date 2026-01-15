package com.dark.tool_neuron.neuron_example

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.neuronpacket.LoadingMode
import com.neuronpacket.UserCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeuronExampleScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf("Not Initialized") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var recoveryKey by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("admin123") }

    val example = remember { NeuronPacketExample(context.cacheDir) }
    var loadedDocuments by remember { mutableStateOf<List<DocumentChunk>>(emptyList()) }

    val sampleDocuments = remember {
        listOf(
            DocumentChunk(UUID.randomUUID().toString(), "Machine learning is a subset of artificial intelligence that enables systems to learn from data.", "ml_basics.txt"),
            DocumentChunk(UUID.randomUUID().toString(), "Neural networks are computing systems inspired by biological neural networks in the brain.", "nn_intro.txt"),
            DocumentChunk(UUID.randomUUID().toString(), "Deep learning uses multiple layers of neural networks to progressively extract features.", "deep_learning.txt"),
            DocumentChunk(UUID.randomUUID().toString(), "Natural language processing enables computers to understand and generate human language.", "nlp_guide.txt"),
            DocumentChunk(UUID.randomUUID().toString(), "Computer vision allows machines to interpret and understand visual information from the world.", "cv_overview.txt"),
            DocumentChunk(UUID.randomUUID().toString(), "Reinforcement learning trains agents to make decisions by rewarding desired behaviors.", "rl_basics.txt"),
            DocumentChunk(UUID.randomUUID().toString(), "Transfer learning leverages pre-trained models for new but related tasks.", "transfer_learning.txt"),
            DocumentChunk(UUID.randomUUID().toString(), "Generative AI creates new content such as images, text, and music using learned patterns.", "gen_ai.txt")
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Neuron Packet Demo") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Status: $status", style = MaterialTheme.typography.bodyMedium)

            if (errorMessage.isNotEmpty()) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error)
            }

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        isLoading = true
                        status = "Initializing embedding model..."
                        errorMessage = ""

                        try {
                            val modelFile = File("/storage/emulated/0/Download/Models/embedding/model_fp16.onnx")
                            val tokenizerFile = File("/storage/emulated/0/Download/Models/embedding/tokenizer.json")

                            if (!modelFile.exists() || !tokenizerFile.exists()) {
                                throw Exception("Model files not found")
                            }

                            example.initializeEmbedding(modelFile.absolutePath, tokenizerFile.absolutePath)

                            status = "Generating embeddings..."
                            sampleDocuments.forEach { doc ->
                                doc.embedding = example.generateEmbedding(doc.content)
                            }

                            status = "Ready (${sampleDocuments.size} documents with embeddings)"
                        } catch (e: Exception) {
                            status = "Error"
                            errorMessage = e.message ?: "Unknown error"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && !status.startsWith("Ready")
            ) {
                Text(if (isLoading) "Loading..." else "Initialize Embeddings")
            }

            HorizontalDivider()

            Button(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        isLoading = true
                        status = "Creating packet..."
                        errorMessage = ""

                        try {
                            val outputPath = "${context.cacheDir}/knowledge.neuron"
                            val result = example.createKnowledgePacket(
                                outputPath = outputPath,
                                name = "AI Knowledge Base",
                                documents = sampleDocuments,
                                adminPassword = password,
                                readOnlyUsers = listOf(
                                    UserCredentials("reader123", "Reader")
                                )
                            )

                            if (result.isSuccess) {
                                val export = result.getOrThrow()
                                recoveryKey = export.recoveryKey
                                status = "Packet created! ID: ${export.packetId.take(8)}..."
                            } else {
                                throw result.exceptionOrNull()!!
                            }
                        } catch (e: Exception) {
                            status = "Export failed"
                            errorMessage = e.message ?: "Unknown error"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && status.startsWith("Ready")
            ) {
                Text("Create Neuron Packet")
            }

            if (recoveryKey.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Recovery Key (save this!):", style = MaterialTheme.typography.labelMedium)
                        Text(recoveryKey, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Button(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        isLoading = true
                        status = "Loading packet..."
                        errorMessage = ""

                        try {
                            val packetPath = "${context.cacheDir}/knowledge.neuron"
                            val result = example.loadKnowledgePacket(packetPath, password)

                            if (result.isSuccess) {
                                loadedDocuments = result.getOrThrow()
                                val info = example.getPacketInfo()
                                status = "Loaded ${loadedDocuments.size} documents. Users: ${info?.userCount}"
                            } else {
                                throw result.exceptionOrNull()!!
                            }
                        } catch (e: Exception) {
                            status = "Load failed"
                            errorMessage = e.message ?: "Unknown error"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && recoveryKey.isNotEmpty()
            ) {
                Text("Load Neuron Packet")
            }

            HorizontalDivider()

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search query") },
                placeholder = { Text("e.g., how do neural networks work") },
                modifier = Modifier.fillMaxWidth(),
                enabled = loadedDocuments.isNotEmpty()
            )

            Button(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        isLoading = true
                        searchResults = ""
                        errorMessage = ""

                        try {
                            val results = example.searchSimilar(loadedDocuments, searchQuery)

                            searchResults = buildString {
                                appendLine("Search Results:\n")
                                results.forEachIndexed { idx, result ->
                                    appendLine("${idx + 1}. [${(result.score * 100).toInt()}%] ${result.document.source}")
                                    appendLine("   ${result.document.content.take(100)}...")
                                    appendLine()
                                }
                            }
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Search error"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && loadedDocuments.isNotEmpty() && searchQuery.isNotBlank()
            ) {
                Text(if (isLoading) "Searching..." else "Search")
            }

            if (searchResults.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = searchResults,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        example.closePacket()
                        loadedDocuments = emptyList()
                        status = "Packet closed"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                enabled = loadedDocuments.isNotEmpty()
            ) {
                Text("Close Packet")
            }
        }
    }
}