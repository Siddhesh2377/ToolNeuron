package com.dark.tool_neuron.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.dark.tool_neuron.ui.theme.NeuroVerseTheme
import com.mp.ai_engine.models.CloudModel
import com.mp.ai_engine.models.ModelType
import com.mp.ai_engine.workers.installer.ModelInstaller
import kotlinx.coroutines.launch

class ScrapActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        ModelInstaller.initialize(this)

        setContent {
            NeuroVerseTheme {
                ModelInstallerTestScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelInstallerTestScreen() {
    val scope = rememberCoroutineScope()
    var statusMessage by remember { mutableStateOf("Ready") }
    var isInstalled by remember { mutableStateOf(false) }
    var modelSize by remember { mutableStateOf(0L) }
    var storageInfo by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }

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
            ),
            CloudModel(
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
            ),
            CloudModel(
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
            ),
            CloudModel(
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Model Installer Test") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Status",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Text(
                text = "Select Test Model",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(testModels) { model ->
                    ModelCard(
                        model = model,
                        isSelected = model == selectedModel,
                        onClick = { selectedModel = model }
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Actions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

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
                                        statusMessage = "✓ Installation started successfully"
                                    },
                                    onError = { error ->
                                        statusMessage = "✗ Error: $error"
                                    }
                                )
                            },
                            modifier = Modifier.weight(1f)
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
                                    "✗ Model is NOT installed"
                                }
                            },
                            modifier = Modifier.weight(1f)
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
                                    statusMessage = "Verifying..."
                                    val verified = ModelInstaller.verifyModel(selectedModel)
                                    statusMessage = if (verified) {
                                        "✓ Model verified successfully"
                                    } else {
                                        "✗ Model verification failed"
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Verified, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Verify")
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    val path = ModelInstaller.getModelPath(selectedModel)
                                    statusMessage = if (path != null) {
                                        "Path: $path"
                                    } else {
                                        "✗ Model path not found"
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Folder, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Path")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    statusMessage = "Calculating size..."
                                    modelSize = ModelInstaller.getModelSize(selectedModel)
                                    statusMessage = "Size: ${formatBytes(modelSize)}"
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Storage, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Size")
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    statusMessage = "Deleting..."
                                    ModelInstaller.deleteModel(
                                        modelName = selectedModel.modelName,
                                        onSuccess = {
                                            statusMessage = "✓ Model deleted successfully"
                                        },
                                        onError = { error ->
                                            statusMessage = "✗ Delete error: $error"
                                        }
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Delete, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Delete")
                        }
                    }

                    HorizontalDivider()

                    Button(
                        onClick = {
                            storageInfo = ModelInstaller.getInstalledModelsInfo()
                            val total = storageInfo["total"] ?: 0L
                            statusMessage = buildString {
                                appendLine("Storage Info:")
                                appendLine("GGUF: ${formatBytes(storageInfo["gguf"] ?: 0L)}")
                                appendLine("TTS: ${formatBytes(storageInfo["sherpa_tts"] ?: 0L)}")
                                appendLine("STT: ${formatBytes(storageInfo["sherpa_stt"] ?: 0L)}")
                                append("Total: ${formatBytes(total)}")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Info, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Storage Info")
                    }
                }
            }
        }
    }
}

@Composable
fun ModelCard(
    model: CloudModel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(12.dp)
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
                    else -> Icons.Default.Help
                },
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
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