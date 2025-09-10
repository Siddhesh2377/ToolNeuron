package com.dark.neuroverse.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.ExperimentalMaterial3Api
import com.dark.neuroverse.ui.theme.NeuroVerseTheme

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.ai_module.model.ModelsData
import com.dark.neuroverse.viewModel.ModelScreenViewModel
import com.dark.neuroverse.viewModel.ModelScreenViewModelFactory
import com.mp.ai_core.NativeLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class ModelLoadingActivity: ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NeuroVerseTheme {
                ModelLoadingScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ModelLoadingScreen(
    viewModel: ModelScreenViewModel = viewModel(factory = ModelScreenViewModelFactory(LocalContext.current))
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()

    // UI state
    var file by remember { mutableStateOf<File?>(null) }
    var loading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var infoJson by remember { mutableStateOf<JSONObject?>(null) }

    // User‑editable fields (same choices as your import dialog)
    var name by remember { mutableStateOf(TextFieldValue("")) }
    var ctxSize by remember { mutableStateOf(TextFieldValue("2048")) }
    var toolCalling by remember { mutableStateOf(false) }

    // File picker
    val pickModel = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModel.loadModelDetailsFromFile(uri, context) { f ->
            file = f
            // kick off native introspection
            loading = true
            loadError = null
            scope.launch(Dispatchers.IO) {
                val native = NativeLib()
                val ok = native.initModel(
                    path = f.absolutePath,
                    threads = maxOf(1, Runtime.getRuntime().availableProcessors()/2),
                    gpuLayers = 0,
                    useMMAP = true,
                    useMLOCK = false,
                    ctxSize = 512,
                    temp = 0.7f,
                    topK = 20,
                    topP = 0.9f,
                    minP = 0f
                )
                if (!ok) {
                    withContext(Dispatchers.Main) {
                        loading = false
                        loadError = "Failed to load model."
                    }
                    return@launch
                }
                val raw = runCatching { native.nativeGetModelInfo() }.getOrElse { "" }
                val parsed = runCatching { JSONObject(raw) }.getOrNull()
                withContext(Dispatchers.Main) {
                    infoJson = parsed
                    loading = false
                    name = TextFieldValue(deriveModelName(f))
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Model importing") },
                scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
            )
        }
    ) { innerPadding ->
        Surface(Modifier.fillMaxSize().padding(innerPadding)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                val cardShape = RoundedCornerShape(18.dp)

                AnimatedContent(
                    targetState = Triple(file, loading, infoJson),
                    transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                    label = "modelLoaderState"
                ) { (f, isLoading, info) ->
                    when {
                        f == null -> ImportCard(shape = cardShape) { pickModel.launch("application/octet-stream") }
                        isLoading -> LoadingCard(shape = cardShape)
                        else -> DetailsCard(
                            shape = cardShape,
                            file = f,
                            info = info,
                            name = name,
                            onName = { name = it },
                            ctxSize = ctxSize,
                            onCtx = { ctxSize = it },
                            toolCalling = toolCalling,
                            onToolCalling = { toolCalling = it }
                        ) {
                            // Save
                            val sizeMb = ((f.length() / 1024.0 / 1024.0).toInt())
                            val model = ModelsData(
                                modeName = name.text.ifBlank { deriveModelName(f) },
                                modelDescription = buildString {
                                    if (info != null && info.optJSONObject("core") != null) {
                                        val core = info.getJSONObject("core")
                                        append("embd=")
                                        append(core.optInt("n_embd"))
                                        append(", layers=")
                                        append(core.optInt("n_layer"))
                                        append(", heads=")
                                        append(core.optInt("n_head"))
                                    } else append("Imported local model")
                                },
                                modelCtxSize = ctxSize.text.toIntOrNull() ?: 2048,
                                toolUse = if (toolCalling) "tools" else "none",
                                modelLink = "",
                                modelPageLink = "",
                                modelPath = f.absolutePath,
                                chatTemplate = "",
                                modelSize = sizeMb
                            )
                            viewModel.addModel(model)
                            Toast.makeText(context, "Model saved", Toast.LENGTH_SHORT).show()
                            context.startActivity(Intent(context, MainActivity::class.java))
                            activity?.finish()
                        }
                    }
                }

                loadError?.let { err ->
                    Text(
                        err,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportCard(shape: RoundedCornerShape, onClick: () -> Unit) {
    OutlinedCard(
        modifier = Modifier
            .width(320.dp)
            .wrapContentHeight()
            .clickable { onClick() },
        shape = shape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Import your model", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "Tap to pick a .gguf file",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingCard(shape: RoundedCornerShape) {
    OutlinedCard(
        modifier = Modifier.width(320.dp),
        shape = shape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // Wavy (indeterminate) progress while we read & probe the model
            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Text("Reading model file & collecting info…", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DetailsCard(
    shape: RoundedCornerShape,
    file: File,
    info: JSONObject?,
    name: TextFieldValue,
    onName: (TextFieldValue) -> Unit,
    ctxSize: TextFieldValue,
    onCtx: (TextFieldValue) -> Unit,
    toolCalling: Boolean,
    onToolCalling: (Boolean) -> Unit,
    onSave: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.widthIn(min = 320.dp, max = 420.dp),
        shape = shape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Model details", style = MaterialTheme.typography.titleMedium)

            // Name
            OutlinedTextField(
                value = name, onValueChange = onName, singleLine = true,
                label = { Text("Model name") }, modifier = Modifier.fillMaxWidth()
            )

            // Basic facts
            val sizeMb = remember(file) { (file.length() / 1024.0 / 1024.0).toInt() }
            InfoRow("File", file.name)
            InfoRow("Size", "$sizeMb MB")

            // Show a few core fields if present
            info?.optJSONObject("core")?.let { core ->
                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
                Text("Detected core dims", style = MaterialTheme.typography.labelLarge)
                val embd = core.optInt("n_embd", -1).takeIf { it > 0 }
                val layers = core.optInt("n_layer", -1).takeIf { it > 0 }
                val heads = core.optInt("n_head", -1).takeIf { it > 0 }
                if (embd != null) InfoRow("n_embd", embd.toString())
                if (layers != null) InfoRow("n_layer", layers.toString())
                if (heads != null) InfoRow("n_head", heads.toString())
            }

            HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
            Text("Your preferences", style = MaterialTheme.typography.labelLarge)

            // Context size input
            OutlinedTextField(
                value = ctxSize,
                onValueChange = { v -> onCtx(v.copy(text = v.text.filter { it.isDigit() })) },
                label = { Text("Context size (tokens)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Tool‑calling toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Tool‑calling model")
                Switch(checked = toolCalling, onCheckedChange = onToolCalling)
            }

            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("Save model") }
        }
    }
}

@Composable private fun InfoRow(k: String, v: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun deriveModelName(f: File): String = f.name.substringBeforeLast('.')
