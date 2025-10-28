package com.dark.neuroverse.activity

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.ai_module.model.LoadState
import com.dark.ai_module.model.ModelData
import com.dark.ai_module.model.ModelProvider
import com.dark.ai_module.workers.ModelManager
import com.dark.neuroverse.ui.theme.NeuroVerseTheme
import com.dark.neuroverse.viewModel.ModelScreenViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * ModelLoadingActivity (v2):
 * - Receives an absolute file path via Intent extra "gguf_file_path".
 * - No SAF picker. Immediately probes the model with NativeLib and shows details.
 * - Lets user edit name / ctx size / tool-calling, then saves to DB.
 */
class ModelLoadingActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.S)
    @OptIn(ExperimentalMaterial3Api::class)
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ModelLoadingScreen(
    incomingPath: String?, viewModel: ModelScreenViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // --- State ---
    var file by remember(incomingPath) { mutableStateOf(incomingPath?.let { File(it) }) }
    var loading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var infoJson by remember { mutableStateOf<JSONObject?>(null) }

    var name by remember { mutableStateOf(TextFieldValue("")) }
    var ctxSize by remember { mutableStateOf(TextFieldValue("2048")) }
    var toolCalling by remember { mutableStateOf(false) }

    // Duplicate handling
    var showDupDialog by remember { mutableStateOf(false) }
    var pendingModel by remember { mutableStateOf<ModelData?>(null) }

    fun saveAndExit(model: ModelData) {
        viewModel.addModel(model)
        try {
            Toast.makeText(context, "Model saved", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("ModelLoadingScreen", "Error showing toast: ${e.message}")
        }
        context.startActivity(Intent(context, MainActivity::class.java))
        activity?.finish()
    }

    // Kick off probing as soon as we have a valid file
    LaunchedEffect(file) {
        val f = file
        if (f == null) return@LaunchedEffect
        if (!f.exists() || !f.isFile) {
            loadError = "Invalid file path"
            return@LaunchedEffect
        }
        loading = true
        loadError = null
        infoJson = null
        withContext(Dispatchers.IO) {
            ModelManager.loadGenerationModel(
                ModelData(
                    modelName = f.name,
                    modelPath = f.absolutePath,
                    providerName = ModelProvider.LocalGGUF.toString()
                ), onLoaded = {
                    if (it !is LoadState.OnLoaded) {
                        return@loadGenerationModel
                    }
                })
            val raw = runCatching { ModelManager.getModelInfo() ?: "" }.getOrElse { "" }
            val parsed = runCatching { JSONObject(raw) }.getOrNull()
            withContext(Dispatchers.Main) {
                infoJson = parsed
                loading = false
                name = TextFieldValue(deriveModelName(f))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Model importing") },
                scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
            )
        }) { innerPadding ->
        Surface(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                val cardShape = RoundedCornerShape(18.dp)

                AnimatedContent(
                    targetState = Triple(file, loading, infoJson),
                    transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                    label = "modelLoaderState"
                ) { (f, isLoading, info) ->
                    when {
                        f == null -> MissingPathCard(
                            shape = cardShape, onClose = { activity?.finish() })

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
                            onToolCalling = { toolCalling = it }) {
                            val candidate = ModelData(
                                modelName = name.text.ifBlank { deriveModelName(f) },
                                ctxSize = ctxSize.text.toIntOrNull() ?: 2048,
                                isToolCalling = toolCalling,
                                modelPath = f.absolutePath,
                                providerName = ModelProvider.LocalGGUF.toString(),
                            )

                            viewModel.checkIfInstalled(candidate.modelName) { exists ->
                                if (exists) {
                                    pendingModel = candidate
                                    showDupDialog = true
                                } else {
                                    saveAndExit(candidate)
                                }
                            }
                        }
                    }
                }

                loadError?.let { err ->
                    Text(
                        err,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                    )
                }

                if (showDupDialog) {
                    val dup = pendingModel
                    if (dup != null) {
                        AlertDialog(
                            onDismissRequest = { showDupDialog = false },
                            confirmButton = {
                                TextButton(
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    onClick = {
                                        showDupDialog = false
                                        viewModel.removeModel(dup.modelName)
                                        saveAndExit(dup)
                                    }) { Text("Overwrite") }
                            },
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            dismissButton = {
                                TextButton(onClick = { showDupDialog = false }) { Text("Cancel") }
                            },
                            title = { Text("Name already exists") },
                            text = { Text("A model named \"${dup.modelName}\" already exists. Overwrite it, or cancel and choose a different name.") })
                    }
                }
            }
        }
    }
}

@Composable
private fun MissingPathCard(shape: RoundedCornerShape, onClose: () -> Unit) {
    OutlinedCard(
        modifier = Modifier
            .widthIn(min = 320.dp)
            .wrapContentHeight(),
        shape = shape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No file provided", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "This screen expects a direct .gguf file path in the Intent extras.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onClose) { Text("Close") }
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
            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Text(
                "Reading model file & collecting info…", style = MaterialTheme.typography.bodyMedium
            )
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

            OutlinedTextField(
                value = name,
                onValueChange = onName,
                singleLine = true,
                label = { Text("Model name") },
                modifier = Modifier.fillMaxWidth()
            )

            val sizeMb = remember(file) { (file.length() / 1024.0 / 1024.0).toInt() }
            InfoRow("Size", "$sizeMb MB")

            info?.optJSONObject("core")?.let { core ->
                HorizontalDivider()
                Text("Detected core dims", style = MaterialTheme.typography.labelLarge)
                val embd = core.optInt("n_embd", -1).takeIf { it > 0 }
                val layers = core.optInt("n_layer", -1).takeIf { it > 0 }
                val heads = core.optInt("n_head", -1).takeIf { it > 0 }
                if (embd != null) InfoRow("n_embd", embd.toString())
                if (layers != null) InfoRow("n_layer", layers.toString())
                if (heads != null) InfoRow("n_head", heads.toString())
            }

            HorizontalDivider()
            Text("Your preferences", style = MaterialTheme.typography.labelLarge)

            OutlinedTextField(
                value = ctxSize,
                onValueChange = { v -> onCtx(v.copy(text = v.text.filter { it.isDigit() })) },
                label = { Text("Context size (tokens)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

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

@Composable
private fun InfoRow(k: String, v: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            k,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(v, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun deriveModelName(f: File): String = f.name.substringBeforeLast('.')
