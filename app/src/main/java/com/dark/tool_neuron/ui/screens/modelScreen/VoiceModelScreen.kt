package com.dark.tool_neuron.ui.screens.modelScreen

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Download
import androidx.compose.material.icons.twotone.GraphicEq
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dark.ai_module.model.ModelData
import com.dark.ai_module.model.ModelProvider
import com.dark.ai_module.model.ModelType
import com.dark.tool_neuron.model.DownloadState
import com.dark.tool_neuron.ui.theme.rDP
import com.dark.tool_neuron.ui.theme.rSp
import com.dark.tool_neuron.viewModel.ModelScreenViewModel
import java.io.File

@Composable
fun SherpaONNXTab(viewModel: ModelScreenViewModel) {
    val context = LocalContext.current
    val downloadStates by viewModel.downloadStates.collectAsState()

    val sttModels = remember { getSTTModelList(context) }
    val ttsModels = remember { getTTSModelList(context) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(rDP(16.dp)),
        verticalArrangement = Arrangement.spacedBy(rDP(12.dp))
    ) {
        if (sttModels.isNotEmpty()) {
            item {
                CategoryHeader("Speech-to-Text", sttModels.size)
            }
            items(sttModels, key = { it.id }) { model ->
                ModernModelCard(
                    modelData = model,
                    downloadState = downloadStates[model.modelUrl.toString()],
                    viewModel = viewModel
                )
            }
        }

        if (ttsModels.isNotEmpty()) {
            item {
                Spacer(Modifier.height(rDP(8.dp)))
                CategoryHeader("Text-to-Speech", ttsModels.size)
            }
            items(ttsModels, key = { it.id }) { model ->
                ModernModelCard(
                    modelData = model,
                    downloadState = downloadStates[model.modelUrl.toString()],
                    viewModel = viewModel
                )
            }
        }

        if (sttModels.isEmpty() && ttsModels.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.TwoTone.GraphicEq,
                    title = "No audio models",
                    subtitle = "Coming soon!"
                )
            }
        }
    }
}

@Composable
private fun EmptyState(
    icon: ImageVector, title: String, subtitle: String, compact: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(rDP(if (compact) 24.dp else 48.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(rDP(12.dp))
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(rDP(if (compact) 56.dp else 80.dp)),
            shadowElevation = rDP(0.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(rDP(if (compact) 28.dp else 40.dp)),
                    tint = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                )
            }
        }
        Text(
            title,
            style = if (compact) MaterialTheme.typography.titleMedium.copy(
                fontSize = rSp(MaterialTheme.typography.titleMedium.fontSize)
            )
            else MaterialTheme.typography.titleLarge.copy(
                fontSize = rSp(MaterialTheme.typography.titleLarge.fontSize)
            ),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = rSp(MaterialTheme.typography.bodyMedium.fontSize)
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CategoryHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = rDP(8.dp)),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = rSp(MaterialTheme.typography.titleMedium.fontSize),
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
        Surface(
            shape = RoundedCornerShape(rDP(8.dp)),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Text(
                "$count",
                modifier = Modifier.padding(horizontal = rDP(10.dp), vertical = rDP(4.dp)),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = rSp(MaterialTheme.typography.labelMedium.fontSize)
                ),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun Chip(text: String, isHighlighted: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(rDP(8.dp)),
        color = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = rDP(10.dp), vertical = rDP(4.dp)),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = rSp(MaterialTheme.typography.labelSmall.fontSize)
            ),
            color = if (isHighlighted) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = rSp(MaterialTheme.typography.bodyMedium.fontSize)
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = rSp(MaterialTheme.typography.bodyMedium.fontSize),
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}


@Composable
private fun ModernModelCard(
    modelData: ModelData, downloadState: DownloadState?, viewModel: ModelScreenViewModel
) {
    val context = LocalContext.current
    var isInstalled by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }

    val isDownloading = downloadState?.isDownloading == true
    val progress = downloadState?.progress ?: 0f
    val isComplete = downloadState?.isComplete == true

    LaunchedEffect(modelData.modelName) {
        viewModel.checkIfInstalled(modelData.modelName) { isInstalled = it }
    }

    LaunchedEffect(isComplete) {
        if (isComplete) isInstalled = true
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(rDP(16.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = rDP(0.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDP(20.dp)),
            verticalArrangement = Arrangement.spacedBy(rDP(12.dp))
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        modelData.modelName,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = rSp(MaterialTheme.typography.titleLarge.fontSize),
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(Modifier.height(rDP(8.dp)))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(rDP(8.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Model Type Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(rDP(6.dp)))
                                .background(
                                    when (modelData.modelType) {
                                        ModelType.STT -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                        ModelType.TTS -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                        else -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                                    }
                                )
                                .padding(horizontal = rDP(10.dp), vertical = rDP(4.dp))
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Chip("${modelData.ctxSize} ctx")

                        if (modelData.isToolCalling) {
                            Chip("Tools", isHighlighted = true)
                        }
                    }
                }

                if (isInstalled && !isDownloading) {
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(rDP(44.dp)),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Icon(
                            Icons.TwoTone.Info,
                            contentDescription = "Info",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(rDP(20.dp))
                        )
                    }
                }
            }

            // Expanded Details
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(rDP(10.dp))) {
                    HorizontalDivider(
                        modifier = Modifier.alpha(0.3f),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Text(
                        text = "Model Configuration",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = rSp(MaterialTheme.typography.titleSmall.fontSize)
                        ),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(rDP(8.dp))) {
                        DetailRow("Temperature", modelData.temp.toString())
                        DetailRow("Top-P", modelData.topP.toString())
                        DetailRow("Max Tokens", modelData.maxTokens.toString())
                        DetailRow("GPU Layers", modelData.gpuLayers.toString())
                        DetailRow("Context Size", modelData.ctxSize.toString())
                    }
                }
            }

            // Download Progress
            if (isDownloading) {
                Column(verticalArrangement = Arrangement.spacedBy(rDP(8.dp))) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Downloading...",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = rSp(MaterialTheme.typography.labelMedium.fontSize)
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = rSp(MaterialTheme.typography.labelMedium.fontSize),
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rDP(6.dp))
                            .clip(RoundedCornerShape(rDP(3.dp))),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(rDP(8.dp))
            ) {
                Button(
                    onClick = {
                        when {
                            isDownloading -> viewModel.cancelDownload(
                                modelData.modelName, modelData.modelUrl.toString()
                            )

                            !isInstalled -> viewModel.startDownload(modelData, context)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(rDP(48.dp)),
                    enabled = !isInstalled || isDownloading,
                    colors = if (isInstalled && !isDownloading) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    } else ButtonDefaults.buttonColors(),
                    shape = RoundedCornerShape(rDP(12.dp)),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = rDP(0.dp),
                        pressedElevation = rDP(0.dp)
                    )
                ) {
                    Icon(
                        when {
                            isInstalled -> Icons.Outlined.CheckCircle
                            isDownloading -> Icons.TwoTone.Close
                            else -> Icons.TwoTone.Download
                        },
                        contentDescription = null,
                        modifier = Modifier.size(rDP(18.dp))
                    )
                    Spacer(Modifier.width(rDP(8.dp)))
                    Text(
                        when {
                            isInstalled -> "Installed"
                            isDownloading -> "Cancel"
                            else -> "Download"
                        },
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = rSp(MaterialTheme.typography.labelLarge.fontSize)
                        ),
                        fontWeight = FontWeight.Bold
                    )
                }

                AnimatedVisibility(
                    visible = isInstalled && !isDownloading,
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    IconButton(
                        onClick = {
                            viewModel.removeModel(modelData.modelName)
                            isInstalled = false
                        },
                        modifier = Modifier.size(rDP(48.dp)),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Icon(
                            Icons.TwoTone.Delete,
                            contentDescription = "Remove",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(rDP(20.dp))
                        )
                    }
                }
            }
        }
    }
}


private fun getSTTModelList(context: Context): List<ModelData> {
    val modelsDir = File(context.filesDir, "models/stt")
    if (!modelsDir.exists()) modelsDir.mkdirs()

    return listOf(
        ModelData(
            modelName = "Whisper-EN-Small",
            providerName = ModelProvider.LocalGGUF.toString(),
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
            providerName = ModelProvider.LocalGGUF.toString(),
            modelType = ModelType.TTS,
            modelPath = modelsDir.absolutePath,
            modelUrl = "https://github.com/Siddhesh2377/ToolNeuron/releases/download/Beta-4.5/kokoro-en-v0_19.zip",
            ctxSize = 512,
            isImported = false
        )
    )
}