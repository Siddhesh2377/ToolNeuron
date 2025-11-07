package com.dark.neuroverse.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.ai_module.model.LoadState
import com.dark.ai_module.model.ModelData
import com.dark.ai_module.model.ModelProvider
import com.dark.ai_module.workers.ModelManager
import com.dark.neuroverse.ui.theme.NeuroVerseTheme
import com.dark.neuroverse.viewModel.ModelScreenViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class ModelLoadingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val passedPath = intent.getStringExtra(EXTRA_RESULT_FILE_PATH)
        setContent {
            NeuroVerseTheme {
                ModelLoadingScreen(incomingPath = passedPath)
            }
        }
    }

    companion object {
        const val EXTRA_RESULT_FILE_PATH = "gguf_file_path"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelLoadingScreen(
    incomingPath: String?,
    viewModel: ModelScreenViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    var modelData by remember { mutableStateOf<ModelData?>(null) }
    var modelInfo by remember { mutableStateOf<JSONObject?>(null) }
    var loadState by remember { mutableStateOf<LoadState>(LoadState.Idle) }
    var showDuplicateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(incomingPath) {
        val path = incomingPath ?: return@LaunchedEffect
        val file = File(path)

        if (!file.exists() || !file.isFile) {
            loadState = LoadState.Error("Invalid file path")
            return@LaunchedEffect
        }

        loadState = LoadState.Loading(0f)

        withContext(Dispatchers.IO) {
            val initialModel = ModelData(
                modelName = file.nameWithoutExtension,
                modelPath = file.absolutePath,
                providerName = ModelProvider.LocalGGUF.toString()
            )

            ModelManager.loadGenerationModel(initialModel) { state ->
                loadState = state
                if (state is LoadState.OnLoaded) {
                    val infoJson = ModelManager.getModelInfo()
                    if (infoJson != null) {
                        val parsed = runCatching { JSONObject(infoJson) }.getOrNull()

                        val architecture = parsed?.optString("architecture") ?: "unknown"
                        val ctxTrain = parsed?.optInt("n_ctx_train") ?: 4096
                        val chatTemplate = parsed?.optString("chat_template")?.takeIf {
                            it.isNotBlank() && it != "null"
                        }

                        // C++ code already provides fallback, so chatTemplate is never null
                        modelData = initialModel.copy(
                            modelName = file.nameWithoutExtension,
                            ctxSize = minOf(ctxTrain, 4096),
                            chatTemplate = chatTemplate,
                            architecture = architecture.lowercase()
                        )
                        modelInfo = parsed
                    }
                }
            }
        }
    }

    fun saveModel(model: ModelData) {
        scope.launch {
            val exists = ModelManager.checkIfInstalled(model.modelName)
            if (exists) {
                showDuplicateDialog = true
            } else {
                viewModel.addModel(model)
                Toast.makeText(context, "Model saved", Toast.LENGTH_SHORT).show()
                ModelManager.unloadGenerationModel()
                activity?.finish()
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Import Model", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = {
                        ModelManager.unloadGenerationModel()
                        activity?.finish()
                    }) {
                        Icon(Icons.Default.Close, "Close")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedContent(
                targetState = loadState,
                transitionSpec = {
                    fadeIn(tween(300)) togetherWith fadeOut(tween(200))
                }
            ) { state ->
                when (state) {
                    is LoadState.Idle, is LoadState.Loading -> LoadingView()
                    is LoadState.OnLoaded -> {
                        modelData?.let { model ->
                            ModelDetailsEditor(
                                model = model,
                                modelInfo = modelInfo,
                                onModelChange = { modelData = it },
                                onSave = { saveModel(it) }
                            )
                        }
                    }
                    is LoadState.Error -> ErrorView(state.message) {
                        ModelManager.unloadGenerationModel()
                        activity?.finish()
                    }
                }
            }
        }
    }

    if (showDuplicateDialog) {
        AlertDialog(
            onDismissRequest = { showDuplicateDialog = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Model Already Exists") },
            text = { Text("A model with this name already exists. Would you like to overwrite it?") },
            confirmButton = {
                Button(
                    onClick = {
                        modelData?.let { model ->
                            scope.launch {
                                ModelManager.removeModel(model.modelName)
                                viewModel.addModel(model)
                                Toast.makeText(context, "Model replaced", Toast.LENGTH_SHORT).show()
                                ModelManager.unloadGenerationModel()
                                activity?.finish()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Overwrite")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDuplicateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun LoadingView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp),
            strokeWidth = 6.dp
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "Analyzing model...",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Reading architecture and metadata",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ErrorView(message: String, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Error,
            null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "Failed to Load Model",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onDismiss) {
            Text("Close")
        }
    }
}

@Composable
fun ModelDetailsEditor(
    model: ModelData,
    modelInfo: JSONObject?,
    onModelChange: (ModelData) -> Unit,
    onSave: (ModelData) -> Unit
) {
    val scrollState = rememberScrollState()
    var currentModel by remember { mutableStateOf(model) }

    LaunchedEffect(model) {
        currentModel = model
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DetectedInfoCard(modelInfo)

        BasicSettingsCard(currentModel) {
            currentModel = it
            onModelChange(it)
        }

        PerformanceCard(currentModel) {
            currentModel = it
            onModelChange(it)
        }

        SamplingCard(currentModel) {
            currentModel = it
            onModelChange(it)
        }

        Button(
            onClick = { onSave(currentModel) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Check, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Save Model", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun DetectedInfoCard(modelInfo: JSONObject?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "Model Information",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            modelInfo?.let { info ->
                InfoRow("Architecture", info.optString("architecture", "Unknown"))
                InfoRow("Model Name", info.optString("name", "Unknown"))

                val vocabSize = info.optInt("n_vocab", 0)
                if (vocabSize > 0) InfoRow("Vocabulary", "$vocabSize tokens")

                val ctxTrain = info.optInt("n_ctx_train", 0)
                if (ctxTrain > 0) InfoRow("Training Context", "$ctxTrain tokens")

                val embedding = info.optInt("n_embd", 0)
                if (embedding > 0) InfoRow("Embedding Dim", embedding.toString())

                val layers = info.optInt("n_layer", 0)
                if (layers > 0) InfoRow("Layers", layers.toString())

                val vocabType = info.optString("vocab_type", "")
                if (vocabType.isNotBlank()) InfoRow("Tokenizer", vocabType.uppercase())

                val templateType = info.optString("template_type", "")
                if (templateType.isNotBlank() && templateType != "null") {
                    InfoRow("Template Format", templateType.replaceFirstChar { it.uppercase() })
                }
            }
        }
    }
}

@Composable
fun BasicSettingsCard(model: ModelData, onChange: (ModelData) -> Unit) {
    ExpandableCard("Basic Settings", Icons.Default.Settings) {
        OutlinedTextField(
            value = model.modelName,
            onValueChange = { onChange(model.copy(modelName = it)) },
            label = { Text("Model Name") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Tool Calling Support", fontWeight = FontWeight.Medium)
                Text(
                    "Enable function calling capabilities",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = model.isToolCalling,
                onCheckedChange = { onChange(model.copy(isToolCalling = it)) }
            )
        }
    }
}

@Composable
fun PerformanceCard(model: ModelData, onChange: (ModelData) -> Unit) {
    ExpandableCard("Performance Settings", Icons.Default.Speed) {
        SliderWithValue(
            "CPU Threads",
            model.threads.toFloat(),
            1f..Runtime.getRuntime().availableProcessors().toFloat(),
            isInt = true
        ) { onChange(model.copy(threads = it.toInt())) }

        SliderWithValue(
            "GPU Layers",
            model.gpuLayers.toFloat(),
            0f..60f,
            isInt = true
        ) { onChange(model.copy(gpuLayers = it.toInt())) }

        SliderWithValue(
            "Context Size",
            model.ctxSize.toFloat(),
            512f..16384f,
            isInt = true,
            step = 512f
        ) { onChange(model.copy(ctxSize = it.toInt())) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Use MMAP", fontWeight = FontWeight.Medium)
            Switch(
                checked = model.useMMAP,
                onCheckedChange = { onChange(model.copy(useMMAP = it)) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Use MLOCK", fontWeight = FontWeight.Medium)
            Switch(
                checked = model.useMLOCK,
                onCheckedChange = { onChange(model.copy(useMLOCK = it)) }
            )
        }
    }
}

@Composable
fun SamplingCard(model: ModelData, onChange: (ModelData) -> Unit) {
    ExpandableCard("Sampling Settings", Icons.Default.Tune, initiallyExpanded = false) {
        SliderWithValue(
            "Temperature",
            model.temp,
            0f..2f
        ) { onChange(model.copy(temp = it)) }

        SliderWithValue(
            "Top K",
            model.topK.toFloat(),
            0f..100f,
            isInt = true
        ) { onChange(model.copy(topK = it.toInt())) }

        SliderWithValue(
            "Top P",
            model.topP,
            0f..1f
        ) { onChange(model.copy(topP = it)) }

        SliderWithValue(
            "Min P",
            model.minP,
            0f..1f
        ) { onChange(model.copy(minP = it)) }

        SliderWithValue(
            "Max Tokens",
            model.maxTokens.toFloat(),
            256f..8192f,
            isInt = true,
            step = 256f
        ) { onChange(model.copy(maxTokens = it.toInt())) }
    }
}

@Composable
fun ExpandableCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    initiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp)),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        icon,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        "Toggle"
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit = shrinkVertically(spring(Spring.DampingRatioMediumBouncy)) + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
fun SliderWithValue(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    isInt: Boolean = false,
    step: Float = 0f,
    onChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontWeight = FontWeight.Medium)
            Text(
                if (isInt) value.toInt().toString() else "%.2f".format(value),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = if (step > 0f) ((range.endInclusive - range.start) / step).toInt() - 1 else 0
        )
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}