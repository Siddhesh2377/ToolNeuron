package com.dark.tool_neuron.activity

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.dark.tool_neuron.ui.theme.NeuroVerseTheme
import com.mp.ai_engine.models.llm_models.CloudModel
import com.mp.ai_engine.models.llm_models.ModelType
import com.mp.ai_engine.models.llm_tasks.GGUFStreamEvents
import com.mp.ai_engine.models.llm_tasks.GGUFTask
import com.mp.ai_engine.workers.installer.ModelInstaller
import com.mp.ai_engine.workers.model.internal_model_worker.GGUFModelWorker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class ScrapActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        ModelInstaller.initialize(this)

        setContent {
            NeuroVerseTheme {
                MainTabScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTabScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Model Installer", "Model Runner")

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("AI Model Manager") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) },
                            icon = {
                                Icon(
                                    imageVector = if (index == 0) Icons.Default.Download else Icons.Default.PlayArrow,
                                    contentDescription = title
                                )
                            })
                    }
                }
            }
        }) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> ModelInstallerScreen()
                1 -> ModelRunnerScreen()
            }
        }
    }
}

@Composable
fun ModelInstallerScreen() {
    val scope = rememberCoroutineScope()
    var statusMessage by remember { mutableStateOf("Ready to install models") }
    var isInstalled by remember { mutableStateOf(false) }

    val testModels = remember {
        listOf(
            CloudModel(
                modelName = "Llama-3.2-1B-Q4",
                modelDescription = "Llama 3.2 1B quantized model",
                providerName = "GGUF",
                modelType = ModelType.TEXT,
                modelFileSize = "700 MB",
                metaData = mapOf(
                    "downloadLink" to "https://huggingface.co/bartowski/SmolLM2-135M-Instruct-GGUF/resolve/main/SmolLM2-135M-Instruct-IQ3_M.gguf?download=true",
                    "architecture" to "LLAMA",
                    "ctxSize" to "8192",
                    "gpu-layers" to "35"
                )
            ), CloudModel(
                modelName = "claude-3.5-sonnet",
                modelDescription = "Claude 3.5 Sonnet via OpenRouter",
                providerName = "OpenRouter",
                modelType = ModelType.TEXT,
                modelFileSize = "N/A",
                metaData = mapOf(
                    "modelId" to "anthropic/claude-3.5-sonnet",
                    "apiEndpoint" to "https://openrouter.ai/api/v1/chat/completions",
                    "supportsTools" to "true",
                    "supportsVision" to "true"
                )
            ), CloudModel(
                modelName = "whisper-tiny-en",
                modelDescription = "Whisper Tiny English STT",
                providerName = "SHERPA-ONNX-STT",
                modelType = ModelType.STT,
                modelFileSize = "39 MB",
                metaData = mapOf(
                    "downloadLink" to "https://github.com/Siddhesh2377/ToolNeuron/releases/download/Beta-4.5/whisper-tiny-en.zip",
                    "encoder" to "whisper-tiny-en-encoder.onnx",
                    "decoder" to "whisper-tiny-en-decoder.onnx",
                    "tokens" to "tokens.txt"
                )
            ), CloudModel(
                modelName = "piper-en-amy",
                modelDescription = "Piper TTS English Amy voice",
                providerName = "SHERPA-ONNX-TTS",
                modelType = ModelType.TTS,
                modelFileSize = "15 MB",
                metaData = mapOf(
                    "downloadLink" to "https://github.com/Siddhesh2377/ToolNeuron/releases/download/Beta-4.5/kokoro-en-v0_19.zip",
                    "modelFileName" to "en_US-amy-low.onnx",
                    "voicesFileName" to "voices.json",
                    "voices" to """[{"id":0,"name":"Amy","gender":"Female","tone":"Natural"}]"""
                )
            )
        )
    }

    var selectedModel by remember { mutableStateOf(testModels[0]) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = statusMessage, style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(testModels) { model ->
                ModelCard(
                    model = model,
                    isSelected = model == selectedModel,
                    onClick = { selectedModel = model })
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            statusMessage = "Installing ${selectedModel.modelName}..."
                            ModelInstaller.install(
                                cloudModel = selectedModel,
                                downloadUrl = selectedModel.metaData["downloadLink"].toString(),
                                onSuccess = {
                                    statusMessage = "✓ Installation successful"
                                },
                                onError = { error ->
                                    statusMessage = "✗ Error: $error"
                                })
                        }, modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Download, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Install")
                    }

                    Button(
                        onClick = {
                            isInstalled = ModelInstaller.isModelInstalled(selectedModel)
                            statusMessage = if (isInstalled) {
                                "✓ Model is installed"
                            } else {
                                "✗ Model not installed"
                            }
                        }, modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CheckCircle, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Check")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                statusMessage = "Getting model info..."
                                val path = ModelInstaller.getModelPath(selectedModel)
                                val size = ModelInstaller.getModelSize(selectedModel)
                                statusMessage = buildString {
                                    appendLine("Path: ${path ?: "Not found"}")
                                    append("Size: ${formatBytes(size)}")
                                }
                            }
                        }, modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Info, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Info")
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                statusMessage = "Deleting..."
                                ModelInstaller.deleteModel(
                                    modelName = selectedModel.modelName,
                                    onSuccess = {
                                        statusMessage = "✓ Model deleted"
                                    },
                                    onError = { error ->
                                        statusMessage = "✗ Delete error: $error"
                                    })
                            }
                        }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Delete")
                    }
                }
            }
        }
    }
}

@Composable
fun ModelRunnerScreen() {
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Ready to run model") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = statusMessage, style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Output",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = result.ifEmpty { "Model output will appear here..." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (result.isEmpty()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }

        Button(
            onClick = {
                scope.launch(Dispatchers.IO) {
                    isRunning = true
                    statusMessage = "Loading model..."
                    result = ""

                    val model = ModelInstaller.findModel("Llama-3.2-1B-Q4")
                    val ggufModel = model?.ggufModel

                    if (ggufModel == null) {
                        statusMessage = "✗ Model not found. Please install it first."
                        isRunning = false
                        return@launch
                    }

                    val modelWorker = GGUFModelWorker()
                    modelWorker.loadModel(ggufModel)

                    statusMessage = "Running inference..."
                    val deferred = CompletableDeferred<String>()

                    val task = GGUFTask(
                        id = UUID.randomUUID().toString(),
                        "Hello, how are you?",
                        maxTokens = 100,
                        events = object : GGUFStreamEvents {
                            override fun onToken(token: String) {
                                result += token
                                Log.d("ModelRunner", "Token: $token")
                            }

                            override fun onTool(toolName: String, toolArgs: String) {
                                // Handle tool calls if needed
                            }
                        },
                        result = deferred
                    )

                    modelWorker.runTask(task)
                    result = deferred.await()
                    statusMessage = "✓ Inference complete"
                    isRunning = false
                }
            }, modifier = Modifier.fillMaxWidth(), enabled = !isRunning
        ) {
            Icon(
                imageVector = if (isRunning) Icons.Default.HourglassEmpty else Icons.Default.PlayArrow,
                contentDescription = null
            )
            Spacer(Modifier.width(8.dp))
            Text(if (isRunning) "Running..." else "Run Model")
        }
    }
}

@Composable
fun ModelCard(
    model: CloudModel, isSelected: Boolean, onClick: () -> Unit
) {
    Card(
        onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ), shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (model.modelType) {
                    ModelType.TEXT -> Icons.Default.TextFields
                    ModelType.TTS -> Icons.Default.RecordVoiceOver
                    ModelType.STT -> Icons.Default.Mic
                    else -> Icons.AutoMirrored.Filled.Help
                },
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.modelName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = model.modelDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${model.providerName} • ${model.modelFileSize}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.2f KB".format(bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
    }
}