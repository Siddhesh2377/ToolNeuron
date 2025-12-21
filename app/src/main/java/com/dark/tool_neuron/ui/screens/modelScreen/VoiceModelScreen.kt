package com.dark.tool_neuron.ui.screens.modelScreen

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dark.ai_module.model.ModelData
import com.dark.ai_module.model.ModelType
import com.dark.ai_module.workers.DownloadState
import com.dark.tool_neuron.ui.theme.rDP
import com.dark.tool_neuron.ui.theme.rSp
import com.dark.tool_neuron.viewModel.ModelScreenViewModel
import com.mp.ai_engine.models.llm_models.ModelProvider
import java.io.File

@Composable
fun SherpaONNXTab(viewModel: ModelScreenViewModel) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    val sttModels = remember { getSTTModelList(context) }
    val ttsModels = remember { getTTSModelList(context) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Tab Row
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = rDP(16.dp))
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(rDP(6.dp))
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(rDP(18.dp))
                        )
                        Text(
                            "STT (${sttModels.size})",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = rSp(MaterialTheme.typography.labelLarge.fontSize)
                            ),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(rDP(6.dp))
                    ) {
                        Icon(
                            Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            modifier = Modifier.size(rDP(18.dp))
                        )
                        Text(
                            "TTS (${ttsModels.size})",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = rSp(MaterialTheme.typography.labelLarge.fontSize)
                            ),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            )
        }

        // Content
        when (selectedTab) {
            0 -> ModelList(models = sttModels, viewModel = viewModel, modelType = "STT")
            1 -> ModelList(models = ttsModels, viewModel = viewModel, modelType = "TTS")
        }
    }
}

@Composable
private fun ModelList(
    models: List<ModelData>,
    viewModel: ModelScreenViewModel,
    modelType: String
) {
    val downloadStates by viewModel.downloadProgress.collectAsState()

    if (models.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(rDP(12.dp))
            ) {
                Icon(
                    if (modelType == "STT") Icons.Default.Mic else Icons.Default.RecordVoiceOver,
                    contentDescription = null,
                    modifier = Modifier.size(rDP(48.dp)),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Text(
                    "No $modelType models available",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = rSp(MaterialTheme.typography.titleMedium.fontSize)
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(rDP(16.dp)),
            verticalArrangement = Arrangement.spacedBy(rDP(12.dp))
        ) {
            items(models, key = { it.id }) { model ->
                CompactModelCard(
                    modelData = model,
                    downloadState = downloadStates[model.modelUrl],
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
private fun CompactModelCard(
    modelData: ModelData,
    downloadState: DownloadState?,
    viewModel: ModelScreenViewModel
) {
    val context = LocalContext.current
    var isInstalled by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }

    val isDownloading = downloadState is DownloadState.Downloading
    val progress = (downloadState as? DownloadState.Downloading)?.progress ?: 0f
    val isComplete = downloadState is DownloadState.Complete
    val errorMessage = (downloadState as? DownloadState.Failed)?.error

    LaunchedEffect(modelData.modelName) {
        viewModel.checkIfInstalled(modelData.modelName) { isInstalled = it }
    }

    LaunchedEffect(isComplete) {
        if (isComplete) isInstalled = true
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(rDP(16.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = rDP(0.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDP(16.dp))
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = modelData.modelName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = rSp(MaterialTheme.typography.titleMedium.fontSize)
                        ),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(rDP(4.dp)))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(rDP(6.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Type Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(rDP(6.dp)))
                                .background(
                                    when (modelData.modelType) {
                                        ModelType.STT -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                        ModelType.TTS -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                        else -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                                    }
                                )
                                .padding(horizontal = rDP(8.dp), vertical = rDP(3.dp))
                        ) {
                            Text(
                                text = when (modelData.modelType) {
                                    ModelType.STT -> "STT"
                                    ModelType.TTS -> "TTS"
                                    else -> "AUDIO"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = rSp(MaterialTheme.typography.labelSmall.fontSize)
                                ),
                                fontWeight = FontWeight.Bold,
                                color = when (modelData.modelType) {
                                    ModelType.STT -> MaterialTheme.colorScheme.onPrimaryContainer
                                    ModelType.TTS -> MaterialTheme.colorScheme.onSecondaryContainer
                                    else -> MaterialTheme.colorScheme.onTertiaryContainer
                                }
                            )
                        }

                        Text(
                            text = "•",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )

                        Text(
                            text = "${modelData.ctxSize} ctx",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = rSp(MaterialTheme.typography.bodySmall.fontSize)
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(rDP(12.dp)))

            // Download Progress
            when {
                isDownloading -> {
                    DownloadProgressSection(
                        progress = progress,
                        onCancel = {
                            viewModel.cancelDownload(
                                modelData.modelName,
                                modelData.modelUrl ?: "",
                                context
                            )
                        }
                    )
                }

                isComplete || isInstalled -> {
                    InstalledSection(
                        onDelete = {
                            viewModel.removeModel(modelData.modelName)
                            isInstalled = false
                        }
                    )
                }

                errorMessage != null -> {
                    ErrorSection(
                        errorMessage = errorMessage,
                        onRetry = {
                            viewModel.startDownload(modelData, context)
                        }
                    )
                }

                else -> {
                    Button(
                        onClick = {
                            viewModel.startDownload(modelData, context)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rDP(44.dp)),
                        shape = RoundedCornerShape(rDP(12.dp)),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = rDP(0.dp),
                            pressedElevation = rDP(0.dp)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(rDP(18.dp))
                        )
                        Spacer(modifier = Modifier.width(rDP(8.dp)))
                        Text(
                            text = "Download Model",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = rSp(MaterialTheme.typography.labelLarge.fontSize)
                            ),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Details Toggle
            if (isInstalled || isComplete) {
                Spacer(modifier = Modifier.height(rDP(8.dp)))
                TextButton(
                    onClick = { showDetails = !showDetails },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (showDetails) "Hide Details" else "Show Details",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = rSp(MaterialTheme.typography.labelMedium.fontSize)
                        ),
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(rDP(4.dp)))
                    Icon(
                        imageVector = if (showDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(rDP(16.dp))
                    )
                }
            }

            // Expanded Details
            AnimatedVisibility(
                visible = showDetails,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(rDP(8.dp))) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(rDP(4.dp)))

                    ModelDetailRow("Temperature", modelData.temp.toString())
                    ModelDetailRow("Top-P", modelData.topP.toString())
                    ModelDetailRow("Max Tokens", modelData.maxTokens.toString())
                    ModelDetailRow("GPU Layers", modelData.gpuLayers.toString())
                    ModelDetailRow("Context Size", modelData.ctxSize.toString())
                }
            }
        }
    }
}

@Composable
private fun ModelDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(rDP(8.dp)))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .padding(horizontal = rDP(12.dp), vertical = rDP(8.dp)),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = rSp(MaterialTheme.typography.bodySmall.fontSize)
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = rSp(MaterialTheme.typography.bodySmall.fontSize)
            ),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun DownloadProgressSection(
    progress: Float,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Downloading...",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = rSp(MaterialTheme.typography.bodySmall.fontSize)
                ),
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "${progress.toInt()}%",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = rSp(MaterialTheme.typography.bodySmall.fontSize)
                ),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(rDP(6.dp)))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(rDP(10.dp))
                .clip(RoundedCornerShape(rDP(5.dp)))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            PixelProgressBar(
                progress = progress / 100f,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(rDP(8.dp)))

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .height(rDP(40.dp)),
            shape = RoundedCornerShape(rDP(10.dp)),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(rDP(16.dp))
            )
            Spacer(modifier = Modifier.width(rDP(6.dp)))
            Text(
                "Cancel",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = rSp(MaterialTheme.typography.labelMedium.fontSize)
                ),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PixelProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pixel_animation")

    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    Canvas(modifier = modifier) {
        val barWidth = size.width * progress
        val pixelSize = 3.dp.toPx()
        val pixelGap = 1.dp.toPx()
        val totalPixelSize = pixelSize + pixelGap

        val numPixelsX = (barWidth / totalPixelSize).toInt()
        val numPixelsY = (size.height / totalPixelSize).toInt().coerceAtLeast(1)

        for (y in 0 until numPixelsY) {
            for (x in 0 until numPixelsX) {
                val xPos = x * totalPixelSize
                val yPos = y * totalPixelSize

                val normalizedX = x.toFloat() / numPixelsX.coerceAtLeast(1)

                val alpha = if (normalizedX > shimmerOffset - 0.2f &&
                    normalizedX < shimmerOffset
                ) {
                    1f
                } else {
                    0.7f
                }

                drawRect(
                    color = Color(0xFF6750A4).copy(alpha = alpha),
                    topLeft = androidx.compose.ui.geometry.Offset(xPos, yPos),
                    size = androidx.compose.ui.geometry.Size(pixelSize, pixelSize)
                )
            }
        }
    }
}

@Composable
private fun InstalledSection(onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(rDP(8.dp))
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(rDP(10.dp)),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier.padding(rDP(12.dp)),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(rDP(16.dp))
                )
                Spacer(modifier = Modifier.width(rDP(6.dp)))
                Text(
                    text = "Installed",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = rSp(MaterialTheme.typography.labelMedium.fontSize)
                    ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .size(rDP(40.dp))
                .clip(RoundedCornerShape(rDP(10.dp)))
                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(rDP(18.dp))
            )
        }
    }
}

@Composable
private fun ErrorSection(
    errorMessage: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(rDP(10.dp)),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier.padding(rDP(10.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = rSp(MaterialTheme.typography.bodySmall.fontSize)
                    ),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(rDP(8.dp)))

        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .height(rDP(40.dp)),
            shape = RoundedCornerShape(rDP(10.dp))
        ) {
            Text(
                "Retry",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = rSp(MaterialTheme.typography.labelMedium.fontSize)
                ),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun getSTTModelList(context: Context): List<ModelData> {
    val modelsDir = File(context.filesDir, "models/stt")
    if (!modelsDir.exists()) modelsDir.mkdirs()

    return listOf(
        ModelData(
            modelName = "Whisper-EN-Small",
            providerName = ModelProvider.SHERPA.toString(),
            modelType = ModelType.STT,
            modelPath = modelsDir.absolutePath,
            modelUrl = "https://github.com/Siddhesh2377/ToolNeuron/releases/download/Beta-4.5/sherpa-onnx-whisper-tiny.zip",
            ctxSize = 448,
            isImported = false
        )
    )
}

private fun getTTSModelList(context: Context): List<ModelData> {
    val modelsDir = File(context.filesDir, "models/tts")
    if (!modelsDir.exists()) modelsDir.mkdirs()

    return listOf(
        ModelData(
            modelName = "KOR0-TTS-0.19-M",
            providerName = ModelProvider.SHERPA.toString(),
            modelType = ModelType.TTS,
            modelPath = modelsDir.absolutePath,
            modelUrl = "https://github.com/Siddhesh2377/ToolNeuron/releases/download/Beta-4.5/kokoro-en-v0_19.zip",
            ctxSize = 512,
            isImported = false
        )
    )
}