package com.dark.tool_neuron.data_packs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ml.shubham0204.sentence_embeddings.SentenceEmbedding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.pow
import kotlin.math.sqrt

data class PersonProfile(
    val name: String,
    val description: String,
    var embedding: FloatArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PersonProfile

        if (name != other.name) return false
        if (description != other.description) return false
        if (!embedding.contentEquals(other.embedding)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        return result
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataPackScreen() {

    var modelStatus by remember { mutableStateOf("Not Initialized") }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val sentenceEmbedding = remember { SentenceEmbedding() }

    // Sample dataset
    val profiles = remember {
        mutableStateListOf(
            PersonProfile("Emma", "A talented software engineer who loves coding and mountain hiking. She enjoys reading sci-fi novels and playing the guitar."),
            PersonProfile("Sophia", "Creative graphic designer with a passion for art and photography. She loves traveling to new places and trying exotic foods."),
            PersonProfile("Olivia", "Medical student who dreams of becoming a pediatrician. She volunteers at animal shelters and enjoys yoga and meditation."),
            PersonProfile("Ava", "Professional dancer and choreographer. She teaches ballet to children and enjoys classical music and painting."),
            PersonProfile("Isabella", "Environmental scientist working on climate change research. She loves nature, camping, and wildlife photography."),
            PersonProfile("Mia", "High school teacher passionate about mathematics. She enjoys solving puzzles, playing chess, and gardening."),
            PersonProfile("Charlotte", "Entrepreneur running her own bakery. She loves baking, cooking shows, and spending time with family."),
            PersonProfile("Amelia", "Journalist covering technology news. She's interested in AI, robotics, and futuristic innovations.")
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Similarity Search") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status
            Text(
                text = "Status: $modelStatus",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Initialize Button
            Button(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        isLoading = true
                        modelStatus = "Initializing..."
                        errorMessage = ""

                        try {
                            val modelFile = File("/storage/emulated/0/Download/Models/embedding/model_fp16.onnx")
                            val tokenizerFile = File("/storage/emulated/0/Download/Models/embedding/tokenizer.json")

                            if (!modelFile.exists() || !tokenizerFile.exists()) {
                                throw Exception("Model files not found")
                            }

                            val tokenizerBytes = tokenizerFile.readBytes()

                            sentenceEmbedding.init(
                                modelFilepath = modelFile.absolutePath,
                                tokenizerBytes = tokenizerBytes,
                                useTokenTypeIds = true,
                                outputTensorName = "sentence_embedding",
                                useFP16 = false,
                                useXNNPack = false,
                                normalizeEmbeddings = true
                            )

                            // Generate embeddings for all profiles
                            profiles.forEach { profile ->
                                profile.embedding = sentenceEmbedding.encode(profile.description)
                            }

                            modelStatus = "Ready (${profiles.size} profiles loaded)"
                        } catch (e: Exception) {
                            modelStatus = "Error"
                            errorMessage = e.message ?: "Unknown error"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && modelStatus == "Not Initialized"
            ) {
                Text(if (isLoading) "Loading..." else "Initialize & Load Dataset")
            }

            // Error Message
            if (errorMessage.isNotEmpty()) {
                Text(
                    text = "Error: $errorMessage",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            HorizontalDivider()

            // Search Section
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search query") },
                placeholder = { Text("e.g., someone who loves technology") },
                modifier = Modifier.fillMaxWidth(),
                enabled = modelStatus.startsWith("Ready"),
                minLines = 2
            )

            Button(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        isLoading = true
                        searchResults = ""
                        errorMessage = ""

                        try {
                            // Generate embedding for search query
                            val queryEmbedding = sentenceEmbedding.encode(searchQuery)

                            // Calculate similarity with all profiles
                            val results = profiles.map { profile ->
                                val similarity = cosineSimilarity(queryEmbedding, profile.embedding!!)
                                Triple(profile.name, profile.description, similarity)
                            }.sortedByDescending { it.third }

                            // Format results
                            searchResults = buildString {
                                appendLine("Search Results:\n")
                                results.forEachIndexed { index, (name, desc, score) ->
                                    appendLine("${index + 1}. $name (${(score * 100).toInt()}% match)")
                                    appendLine("   $desc")
                                    appendLine()
                                }
                            }

                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Error during search"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && modelStatus.startsWith("Ready") && searchQuery.isNotBlank()
            ) {
                Text(if (isLoading) "Searching..." else "Search")
            }

            // Results
            if (searchResults.isNotEmpty()) {
                HorizontalDivider()

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = searchResults,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// Helper function for cosine similarity
private fun cosineSimilarity(x1: FloatArray, x2: FloatArray): Float {
    var mag1 = 0.0f
    var mag2 = 0.0f
    var product = 0.0f
    for (i in x1.indices) {
        mag1 += x1[i].pow(2)
        mag2 += x2[i].pow(2)
        product += x1[i] * x2[i]
    }
    mag1 = sqrt(mag1)
    mag2 = sqrt(mag2)
    return product / (mag1 * mag2)
}