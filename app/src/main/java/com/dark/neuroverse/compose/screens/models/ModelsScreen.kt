package com.dark.neuroverse.compose.screens.models

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.neuroverse.compose.screens.home.chat.BottomBar
import com.dark.ai_manager.ai.data.ModelsData
import com.dark.ai_manager.ai.data.repo.ModelsList.getModelList
import com.dark.neuroverse.ui.theme.Success
import com.dark.neuroverse.ui.theme.onSuccess
import com.dark.neuroverse.viewModel.ModelScreenViewModel
import com.dark.neuroverse.viewModel.ModelScreenViewModelFactory
import com.dark.neuroverse.worker.downloadFile
import kotlinx.coroutines.launch
import java.io.File
import com.dark.neuroverse.compose.components.systemui.StandardBottomBar
import com.dark.neuroverse.compose.components.CollapsableButton
import com.dark.neuroverse.worker.model.ModelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

@Composable
fun ModelsScreen(onNext: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val models = getModelList(context)
    val factory = remember { ModelScreenViewModelFactory(context) }
    val viewModel: ModelScreenViewModel = viewModel(factory = factory)

    var isEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isEnabled = ModelManager.isAnyModelInstalled()
    }

    Box(Modifier.fillMaxSize()){
        LazyColumn {
            items(models) { modelData ->
                var progress by remember { mutableFloatStateOf(0f) }
                var isDownloading by remember { mutableStateOf(false) }
                var message by remember { mutableStateOf("") }
                var onDownloadComplete by remember { mutableStateOf(false) }

                ModelCard(
                    modelsData = modelData,
                    isDownloading = isDownloading,
                    progress = progress,
                    onDownloadComplete = onDownloadComplete,
                    viewModel = viewModel,
                    onDownload = {
                        scope.launch {
                            isDownloading = true
                            downloadFile(
                                fileUrl = modelData.modelLink,
                                outputFile = File(modelData.modelPath),
                                onProgress = { prog ->
                                    progress = prog
                                },
                                onComplete = {
                                    isDownloading = false
                                    message = "Download Complete"
                                    viewModel.addModel(modelData)
                                    onDownloadComplete = true
                                    isEnabled = true
                                },
                                onError = { e ->
                                    isDownloading = false
                                    message = "Failed: ${e.message}"
                                    onDownloadComplete = false
                                }
                            )
                        }
                    }
                )
            }
        }





        StandardBottomBar(Modifier.align(Alignment.BottomCenter)){
            CollapsableButton(text = "Finish", icon = Icons.AutoMirrored.Default.ArrowForward, enabled = isEnabled){
                onNext()
            }
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
                modelsData.modelDescription,
                style = MaterialTheme.typography.bodyLarge
            )
            AnimatedVisibility(isDownloading) {
                LinearWavyProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    progress = { progress },
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    onDownload()
                }, colors = buttonColor) {
                    Crossfade(isInstalled){
                        when (it) {
                            true -> {
                                Icon(Icons.Default.Check, contentDescription = "")
                            }

                            false -> {
                                Crossfade(isDownloading) {
                                    when (it) {
                                        true -> {
                                            Icon(
                                                Icons.Default.Stop,
                                                contentDescription = "",
                                            )
                                        }

                                        false -> {
                                            Icon(Icons.Default.ArrowCircleDown, contentDescription = "")
                                        }
                                    }
                                }
                            }
                        }
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