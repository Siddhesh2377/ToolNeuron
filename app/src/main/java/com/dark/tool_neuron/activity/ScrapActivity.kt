package com.dark.tool_neuron.activity

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.dark.tool_neuron.ui.theme.NeuroVerseTheme
import com.mp.ai_engine.diffusion.IDiffusionCallback
import com.mp.ai_engine.gguf.IGGUFCallback
import com.mp.ai_engine.models.image_models.DiffusionDatabaseModel
import com.mp.ai_engine.models.llm_models.CloudModel
import com.mp.ai_engine.models.llm_models.ModelType
import com.mp.ai_engine.models.llm_tasks.*
import com.mp.ai_engine.workers.installer.ModelInstaller
import com.mp.ai_engine.workers.model.ModelManager
import com.mp.ai_engine.workers.model.internal_model_worker.DiffusionModelWorker
import com.mp.ai_engine.workers.model.internal_model_worker.GGUFModelWorker
import kotlinx.coroutines.*
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.util.UUID

class ScrapActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ModelInstaller.initialize(this)

        setContent {
            NeuroVerseTheme {
                MainScreen()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ModelManager.shutdown(applicationContext)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Installer", "Text Gen", "Image Gen")
    val icons = listOf(Icons.Default.Download, Icons.Default.TextFields, Icons.Default.Image)

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("AI Model Manager") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) },
                            icon = { Icon(icons[index], null) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> ModelInstallerScreen()
                1 -> TextGeneratorScreen()
                2 -> ImageGeneratorScreen()
            }
        }
    }
}

@Composable
fun ModelInstallerScreen() {
    var statusMessage by remember { mutableStateOf("Ready to install models") }
    var selectedModel by remember { mutableStateOf(getTestModels()[0]) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatusCard(statusMessage)

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(getTestModels()) { model ->
                ModelCard(
                    model = model,
                    isSelected = model == selectedModel,
                    onClick = { selectedModel = model }
                )
            }
        }

        ActionButtons(
            selectedModel = selectedModel,
            onStatusChange = { statusMessage = it }
        )
    }
}

@Composable
fun TextGeneratorScreen() {
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
        StatusCard(statusMessage)

        OutputCard(Modifier.fillMaxWidth()
            .weight(1f), result)

        Button(
            onClick = {
                scope.launch(Dispatchers.IO) {
                    isRunning = true
                    statusMessage = "Loading model..."
                    result = ""

                    val model = ModelInstaller.findModel("f0ed656c-fb89-4d20-a40a-c8932d87688f")?.ggufModel
                    if (model == null) {
                        statusMessage = "✗ Model not found"
                        isRunning = false
                        return@launch
                    }

                    val ggufWorker = ModelManager.gguf()

                    val config = model.toJson()

                    ggufWorker.loadTextModel(
                        config
                    )

                    statusMessage = "Running inference..."

                    val deferred = CompletableDeferred<String>()

                    ggufWorker.generateText(
                        "Hi How are you ?",
                        100,
                        "",
                        object : IGGUFCallback.Stub() {
                            override fun onNewToken(token: String) {
                                result += token
                            }

                            override fun onToolCall(
                                toolName: String?,
                                toolArgs: String?
                            ) { }

                            override fun onComplete(finalResult: String) {
                                deferred.complete(finalResult)
                            }

                            override fun onError(error: String?) {
                                deferred.completeExceptionally(Exception(error))
                            }

                        }
                    )
                    result = deferred.await()
                    statusMessage = "✓ Complete"
                    isRunning = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isRunning
        ) {
            Icon(if (isRunning) Icons.Default.HourglassEmpty else Icons.Default.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text(if (isRunning) "Running..." else "Run Model")
        }
    }
}

@Composable
fun ImageGeneratorScreen() {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var generatedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) } // Add preview state
    var isGenerating by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Ready to generate") }
    var progress by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatusCard(statusMessage)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                // Show preview during generation, final image when complete
                val displayBitmap = if (isGenerating) previewBitmap else generatedBitmap

                if (displayBitmap != null) {
                    Image(
                        bitmap = displayBitmap.asImageBitmap(),
                        contentDescription = if (isGenerating) "Preview" else "Generated Image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )

                    // Optional: Show "Preview" badge during generation
                    if (isGenerating && previewBitmap != null) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp),
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        ) {
                            Text(
                                "Preview",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Image,
                            null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Generated image will appear here",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (isGenerating) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Button(
            onClick = {
                scope.launch(Dispatchers.IO) {
                    isGenerating = true
                    statusMessage = "Loading model..."
                    progress = 0f
                    previewBitmap = null // Clear previous preview

                    var model = ModelInstaller.findModel("aa24e027-17c9-4640-bc9d-890f9da50726")?.diffusionModel
                    if (model == null) {
                        statusMessage = "✗ Model not found"
                        isGenerating = false
                        return@launch
                    }

                    val diffusionWorker = ModelManager.diffusion()

                    val config = model.toJson()

                    diffusionWorker.loadModel(
                        config
                    )

                    val task = DiffusionTask(
                        id = UUID.randomUUID().toString(),
                        prompt = """
                            a Girl with black Dress
                        """.trimIndent(),
                        negativePrompt = "blurry, low quality",
                        steps = 25,
                        cfg = 7f,
                        events = object : DMStreamEvents {
                            override fun onProgress(p: Float, step: Int, totalSteps: Int) {

                            }

                            override fun onPreview(previewBmp: Bitmap, step: Int, totalSteps: Int) {

                            }

                            override fun onComplete(bitmap: Bitmap, seed: Long?) {

                            }

                            override fun onError(error: String) {
                                statusMessage = "✗ Error: $error"
                                previewBitmap = null
                            }
                        },
                        result = CompletableDeferred()
                    )

                    diffusionWorker.generateImage(
                        task.prompt,
                        task.negativePrompt,
                        task.steps,
                        task.cfg,
                        task.width,
                        task.height,
                        task.denoiseStrength,
                        task.useOpenCL,
                        task.scheduler,
                        task.seed ?: 0,
                        object : IDiffusionCallback.Stub() {
                            override fun onProgress(
                                p: Float,
                                step: Int,
                                totalSteps: Int
                            ) {
                                statusMessage = "Generating image..."
                                progress = p
                                isGenerating = true
                                statusMessage = "Step $step/$totalSteps"
                            }

                            override fun onPreview(
                                previewImage: Bitmap?,
                                step: Int,
                                totalSteps: Int
                            ) {
                                // Update preview bitmap on main thread
                                scope.launch(Dispatchers.Main) {
                                    previewBitmap = previewImage
                                }
                            }

                            override fun onComplete(
                                finalImage: Bitmap?,
                                seed: Long
                            ) {
                                generatedBitmap = finalImage
                                previewBitmap = null // Clear preview
                                isGenerating = false
                                statusMessage = "✓ Generated (seed: $seed)"
                            }

                            override fun onError(error: String?) {
                                statusMessage = "✗ Error: $error"
                                previewBitmap = null
                                isGenerating = false
                            }
                        }
                    )

//                    model = model.copy(
//                        runOnCpu = false,
//                        useOpenCL = false,
//                        useCpuClip = true,
//                        scheduler = "euler_a"
//                    )


                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isGenerating
        ) {
            Icon(if (isGenerating) Icons.Default.HourglassEmpty else Icons.Default.Image, null)
            Spacer(Modifier.width(8.dp))
            Text(if (isGenerating) "Generating..." else "Generate Image")
        }
    }
}

@Composable
fun StatusCard(message: String) {
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
            Text("Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun OutputCard(modifier: Modifier, text: String) {
    Card(
        modifier = modifier

    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                "Output",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text.ifEmpty { "Model output will appear here..." },
                style = MaterialTheme.typography.bodyMedium,
                color = if (text.isEmpty()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

@Composable
fun ActionButtons(
    selectedModel: CloudModel,
    onStatusChange: (String) -> Unit
) {
    val scope = rememberCoroutineScope()

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
                        onStatusChange("Installing ${selectedModel.modelName}...")
                        ModelInstaller.install(
                            cloudModel = selectedModel,
                            downloadUrl = selectedModel.metaData["downloadLink"].toString(),
                            onSuccess = { onStatusChange("✓ Installation successful") },
                            onError = { onStatusChange("✗ Error: $it") }
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
                        val isInstalled = ModelInstaller.isModelInstalled(selectedModel)
                        onStatusChange(if (isInstalled) "✓ Installed" else "✗ Not installed")
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
                            val path = ModelInstaller.getModelPath(selectedModel)
                            val size = ModelInstaller.getModelSize(selectedModel)
                            onStatusChange("Path: ${path ?: "Not found"}\nSize: ${formatBytes(size)}")
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Info, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Info")
                }

                Button(
                    onClick = {
                        scope.launch {
                            ModelInstaller.deleteModel(
                                modelId = selectedModel.modelName,
                                onSuccess = { onStatusChange("✓ Deleted") },
                                onError = { onStatusChange("✗ Delete error: $it") }
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
                    ModelType.IMAGE_GEN -> Icons.Default.Image
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
                    Icons.Default.CheckCircle,
                    "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

fun getTestModels() = listOf(
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
    ),
    CloudModel(
        modelName = "AbsoluteReality",
        modelDescription = "Real Image",
        providerName = "DIFFUSION",
        modelType = ModelType.IMAGE_GEN,
        modelFileSize = "980 MB",
        metaData = mapOf(
            "downloadLink" to "https://huggingface.co/xororz/sd-qnn/resolve/main/AbsoluteReality_qnn2.28_min.zip?download=true",
            "run-on-cpu" to "false"
        )
    )
)

fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.2f KB".format(bytes / 1024.0)
    bytes < 1024 * 1024 * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
}