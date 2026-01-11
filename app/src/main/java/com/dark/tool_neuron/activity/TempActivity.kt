package com.dark.tool_neuron.activity

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.ai_sd.BackendState
import com.dark.ai_sd.GenerationState
import com.dark.ai_sd.RuntimeConfig
import com.dark.ai_sd.Schedulers
import com.dark.ai_sd.StableDiffusionManager
import com.dark.ai_sd.generationParams
import com.dark.ai_sd.modelConfig
import com.dark.tool_neuron.ui.theme.NeuroVerseTheme
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class TempActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NeuroVerseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    StableDiffusionTestScreen()
                }
            }
        }
    }
}

class StableDiffusionViewModel() : ViewModel() {
    private lateinit var sdManager: StableDiffusionManager

    lateinit var backendState: StateFlow<BackendState>
    lateinit var generationState: StateFlow<GenerationState>
    lateinit var isGenerating: StateFlow<Boolean>

    @SuppressLint("StaticFieldLeak")
    private lateinit var context: Context

    var isInitialized by mutableStateOf(false)
        private set

    var currentModelType by mutableStateOf<ModelType?>(null)
        private set

    enum class ModelType {
        CPU, NPU
    }

    fun initialize(ctx: Context) {
        if (isInitialized) return

        try {
            context = ctx
            sdManager = StableDiffusionManager.getInstance(context)
            backendState  = sdManager.backendState
            generationState  = sdManager.generationState
            isGenerating = sdManager.isGenerating
            sdManager.initialize(RuntimeConfig("runtime_libs/qnnlibs"))
            isInitialized = true
        } catch (e: Exception) {
            Toast.makeText(context, "Init failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun loadCpuModel() {
        if (!isInitialized) {
            Toast.makeText(context, "Please initialize first", Toast.LENGTH_SHORT).show()
            return
        }

        val model = modelConfig {
            name("SD 1.5 (CPU)")
            modelDir("/storage/emulated/0/Download/Models/sd_model")
            textEmbeddingSize(768)
            runOnCpu(true)
            useCpuClip(true)
        }

        viewModelScope.launch {
            val success = sdManager.loadModel(model, width = 512, height = 512)
            if (success) {
                currentModelType = ModelType.CPU
                Toast.makeText(context, "CPU Model loaded", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to load CPU model", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun loadNpuModel() {
        if (!isInitialized) {
            Toast.makeText(context, "Please initialize first", Toast.LENGTH_SHORT).show()
            return
        }

        val modelDir = "/storage/emulated/0/Download/Models/sd_model"

        // Verify NPU files exist
        val requiredFiles = listOf(
            "clip_v2.mnn",  // Will use CPU CLIP
            "unet.bin",     // NPU version
            "vae_decoder.bin",
            "vae_encoder.bin",
            "tokenizer.json"
        )

        val missingFiles = requiredFiles.filter { !File(modelDir, it).exists() }
        if (missingFiles.isNotEmpty()) {
            val lastError = "Missing files for NPU: ${missingFiles.joinToString()}"
            Log.e("StableDiffusionViewModel", lastError)
            Toast.makeText(context, lastError, Toast.LENGTH_LONG).show()
            return
        }

        val model = modelConfig {
            name("SD 1.5 (NPU)")
            modelDir("/storage/emulated/0/Download/Models/sd_model")
            textEmbeddingSize(768)
            runOnCpu(false)
            useCpuClip(true) // Using MNN clip
        }

        viewModelScope.launch {
            val success = sdManager.loadModel(model, width = 512, height = 512)
            if (success) {
                currentModelType = ModelType.NPU
                Toast.makeText(context, "NPU Model loaded", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to load NPU model", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun generateImage(prompt: String, negativePrompt: String, steps: Int, cfg: Float) {
        if (currentModelType == null) {
            Toast.makeText(context, "Please load a model first", Toast.LENGTH_SHORT).show()
            return
        }

        val params = generationParams {
            prompt(prompt)
            negativePrompt(negativePrompt)
            steps(steps)
            cfgScale(cfg)
            resolution(512, 512)
            scheduler(Schedulers.DPM)
            showProcess(true, stride = 5)
        }

        sdManager.generateImage(params)
    }

    fun cancelGeneration() {
        sdManager.cancelGeneration()
    }

    fun stopBackend() {
        sdManager.stopBackend()
        currentModelType = null
    }

    override fun onCleared() {
        super.onCleared()
        sdManager.cleanup()
    }
}

@Composable
fun StableDiffusionTestScreen(
    viewModel: StableDiffusionViewModel = viewModel {
        StableDiffusionViewModel()
    }
) {
    val ctx = LocalContext.current
    viewModel.initialize(ctx)

    val backendState by viewModel.backendState.collectAsState()
    val generationState by viewModel.generationState.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()



    var prompt by remember { mutableStateOf("a beautiful landscape with mountains and lakes, highly detailed, 4k") }
    var negativePrompt by remember { mutableStateOf("blurry, low quality, distorted, ugly") }
    var steps by remember { mutableStateOf(28) }
    var cfgScale by remember { mutableStateOf(7.5f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Stable Diffusion Test", style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Status Card
        Card(
            modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                containerColor = when (backendState) {
                    is BackendState.Running -> MaterialTheme.colorScheme.primaryContainer
                    is BackendState.Error -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Status", style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Initialized: ${if (viewModel.isInitialized) "✓" else "✗"}",
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = "Backend: ${
                        when (backendState) {
                            is BackendState.Idle -> "Idle"
                            is BackendState.Starting -> "Starting..."
                            is BackendState.Running -> "Running"
                            is BackendState.Error -> "Error: ${(backendState as BackendState.Error).message}"
                        }
                    }", style = MaterialTheme.typography.bodyMedium
                )

                viewModel.currentModelType?.let {
                    Text(
                        text = "Model: ${it.name}", style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Model Loading Buttons
        Text(
            text = "1. Load Model", style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.loadCpuModel() },
                modifier = Modifier.weight(1f),
                enabled = viewModel.isInitialized && backendState !is BackendState.Starting
            ) {
                Text("Load CPU Model")
            }

            Button(
                onClick = { viewModel.loadNpuModel() },
                modifier = Modifier.weight(1f),
                enabled = viewModel.isInitialized && backendState !is BackendState.Starting
            ) {
                Text("Load NPU Model")
            }
        }

        if (backendState is BackendState.Running) {
            Button(
                onClick = { viewModel.stopBackend() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Stop Backend")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Generation Parameters
        Text(
            text = "2. Generation Parameters", style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("Prompt") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = negativePrompt,
            onValueChange = { negativePrompt = it },
            label = { Text("Negative Prompt") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Steps Slider
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Steps: $steps", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = steps.toFloat(),
                onValueChange = { steps = it.toInt() },
                valueRange = 10f..50f,
                steps = 39
            )
        }

        // CFG Scale Slider
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                "CFG Scale: ${"%.1f".format(cfgScale)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = cfgScale, onValueChange = { cfgScale = it }, valueRange = 1f..20f
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Generate Button
        Button(
            onClick = {
                viewModel.generateImage(prompt, negativePrompt, steps, cfgScale)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = backendState is BackendState.Running && !isGenerating
        ) {
            Text(if (isGenerating) "Generating..." else "Generate Image")
        }

        if (isGenerating) {
            Button(
                onClick = { viewModel.cancelGeneration() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Cancel")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Generation Progress
        when (val state = generationState) {
            is GenerationState.Progress -> {
                Card(
                    modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Generating...", style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = state.progress, modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Step ${state.currentStep} of ${state.totalSteps} (${(state.progress * 100).toInt()}%)",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        // Show intermediate image if available
                        state.intermediateImage?.let { bitmap ->
                            Spacer(modifier = Modifier.height(16.dp))
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Intermediate preview",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                            )
                        }
                    }
                }
            }

            is GenerationState.Complete -> {
                Card(
                    modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Generation Complete!",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )

                        state.seed?.let {
                            Text(
                                text = "Seed: $it", style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Image(
                            bitmap = state.bitmap.asImageBitmap(),
                            contentDescription = "Generated image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                // Save image logic here
                                val fileName =
                                    "generated_${state.seed ?: System.currentTimeMillis()}.png"
                                // Implement save functionality
                                Toast.makeText(
                                    ctx, "Image ready to save as $fileName", Toast.LENGTH_SHORT
                                ).show()
                            }, modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save Image")
                        }
                    }
                }
            }

            is GenerationState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Error",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            else -> {}
        }
    }
}