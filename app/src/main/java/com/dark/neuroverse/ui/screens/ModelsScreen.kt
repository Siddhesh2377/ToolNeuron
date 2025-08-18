package com.dark.neuroverse.ui.screens


import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.FileOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.ai_module.data.ModelsList.getModelList
import com.dark.ai_module.model.ModelsData
import com.dark.neuroverse.ui.components.CollapsableButton
import com.dark.neuroverse.ui.components.ModelDialog
import com.dark.neuroverse.ui.components.StandardBottomBar
import com.dark.neuroverse.ui.theme.Success
import com.dark.neuroverse.ui.theme.onSuccess
import com.dark.neuroverse.ui.theme.rDP
import com.dark.neuroverse.viewModel.DownloadState
import com.dark.neuroverse.viewModel.ModelScreenViewModel
import com.dark.neuroverse.viewModel.ModelScreenViewModelFactory
import java.io.File

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ModelsScreen(onNext: () -> Unit) {
    val context = LocalContext.current
    val models = getModelList(context)
    val factory = remember { ModelScreenViewModelFactory(context) }
    val viewModel: ModelScreenViewModel = viewModel(factory = factory)

    var isEnabled by remember { mutableStateOf(false) }
    val installedModels by viewModel.models.collectAsState()

    LaunchedEffect(installedModels) {
        isEnabled = installedModels.isNotEmpty()
    }
    var show by remember { mutableStateOf(false) }
    // --- File Picker Setup ---
    var selectedModelPath by remember { mutableStateOf<File?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.loadModelDetailsFromFile(it, context) { file ->
                selectedModelPath = file
            }
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 26.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {


            Text(
                "Choose Your\nModels",
                modifier = Modifier.padding(top = 24.dp, bottom = 12.dp),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif
                )
            )

            // In your Composable
            if (show) {
                val isLoading by viewModel.isLoading.collectAsState()

                if (isLoading) {
                    AlertDialog(onDismissRequest = {}, confirmButton = {}, text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularWavyProgressIndicator(waveSpeed = 50.dp)
                            Spacer(Modifier.height(16.dp))
                            Text("Loading model…", style = MaterialTheme.typography.bodyLarge)
                        }
                    })
                } else {
                    ModelDialog(
                        modelInfo = selectedModelPath ?: File("default_model.gguf"),
                        onDismiss = { show = false },
                        onSave = {
                            val modelsData = it


                            Log.d("ModelDialog", "ModelDialog: $modelsData")
                            viewModel.loadModel(modelsData)
                        })
                }
            }


            Button(onClick = {
                // Launch file picker only for .gguf
                filePickerLauncher.launch("application/octet-stream")
                viewModel.updateLoadingState(true)
                show = true
            }) {
                Icon(Icons.TwoTone.FileOpen, "Models")
                Spacer(Modifier.width(12.dp))
                Text("Import")
            }
        }

        val downloadStates by viewModel.downloadStates.collectAsState()

        LazyColumn(Modifier.weight(1f)) {
            items(models) { modelData ->
                val state = downloadStates[modelData.modeName] ?: DownloadState()

                ModelCard(
                    modelsData = modelData,
                    isDownloading = state.isDownloading,
                    progress = state.progress,
                    onDownloadComplete = state.isComplete,
                    viewModel = viewModel,
                    onDownload = { viewModel.startDownload(modelData) })
            }
        }

        StandardBottomBar(Modifier.padding(bottom = 14.dp)) {
            CollapsableButton(
                text = "Finish", icon = Icons.AutoMirrored.Default.ArrowForward, enabled = isEnabled
            ) { onNext() }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ModelCard(
    modelsData: ModelsData,
    isDownloading: Boolean = false,
    onDownloadComplete: Boolean = false,
    progress: Float = 0f,
    viewModel: ModelScreenViewModel,
    onDownload: () -> Unit = {}
) {

    val context = LocalContext.current
    var isInstalled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.checkIfInstalled(modelsData.modeName, onResult = {
            isInstalled = it
        })
    }

    LaunchedEffect(onDownloadComplete) {
        if (onDownloadComplete) {
            isInstalled = true
        }
    }

    val buttonColor = if (!isInstalled) {
        ButtonDefaults.buttonColors()
    } else {
        ButtonDefaults.buttonColors(containerColor = onSuccess, contentColor = Success)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                modelsData.modeName,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                modelsData.modelDescription, style = MaterialTheme.typography.bodyLarge
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Text(
                    buildAnnotatedString {
                        withStyle(
                            style = SpanStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = MaterialTheme.typography.titleMedium.fontSize
                            )
                        ) {
                            append("Details\n")
                        }
                        append("\u2023 Context Size: ${modelsData.modelCtxSize}\n")
                        append("\u2023 Model Size: ${modelsData.modelSize} MB\n")
                        append("\u2023 Tool Call: ${modelsData.toolUse.uppercase()}")
                    })
            }

            AnimatedVisibility(isDownloading) {
                LinearWavyProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    progress = { progress },
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {

                Button(onClick = {
                    if (!isInstalled) {
                        if (isDownloading) {
                            viewModel.cancelDownload(modelsData.modeName, modelsData.modelPath)
                        } else {
                            onDownload()
                        }
                    }
                }, colors = buttonColor) {
                    Crossfade(isInstalled) {
                        when (it) {
                            true -> {
                                Icon(Icons.Default.Check, contentDescription = "")
                            }

                            false -> {
                                Crossfade(isDownloading) { isDownload ->
                                    when (isDownload) {
                                        true -> {
                                            Icon(
                                                Icons.Default.Stop,
                                                contentDescription = "Cancel Download",
                                            )
                                        }

                                        false -> {
                                            Icon(
                                                Icons.Default.ArrowCircleDown,
                                                contentDescription = "Download",
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.width(rDP(8.dp)))

                AnimatedVisibility(isInstalled) {
                    Button(
                        onClick = {
                            viewModel.removeModel(modelsData.modeName)
                            isInstalled = false
                        }, colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.TwoTone.Delete, contentDescription = "")
                    }
                }

                Spacer(Modifier.weight(1f))

                IconButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, modelsData.modelPageLink.toUri())
                    context.startActivity(intent)
                }) {
                    Icon(Icons.AutoMirrored.Default.OpenInNew, contentDescription = "")
                }
            }
        }
    }
}