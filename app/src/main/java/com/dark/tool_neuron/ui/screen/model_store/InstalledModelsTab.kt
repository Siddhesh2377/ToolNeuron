package com.dark.tool_neuron.ui.screen.model_store

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.global.Standards
import com.dark.tool_neuron.global.formatBytes
import com.dark.tool_neuron.models.enums.ProviderType
import com.dark.tool_neuron.models.table_schema.Model
import com.dark.tool_neuron.models.ui.ActionIcon
import com.dark.tool_neuron.models.ui.ActionItem
import com.dark.tool_neuron.ui.components.CaptionText
import com.dark.tool_neuron.ui.components.MultiActionButton
import com.dark.tool_neuron.ui.components.StatusBadge
import com.dark.tool_neuron.ui.theme.maple
import com.dark.tool_neuron.viewmodel.ModelStoreViewModel
import com.dark.tool_neuron.ui.icons.TnIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// ── InstalledModelsTab ──

@Composable
internal fun InstalledModelsTab(
    models: List<Model>,
    deleteInProgress: String?,
    onDelete: (Model) -> Unit,
    viewModel: ModelStoreViewModel
) {
    var selectedModel by remember { mutableStateOf<Model?>(null) }
    var showDeleteDialog by remember { mutableStateOf<Model?>(null) }

    if (models.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Standards.SpacingLg)
            ) {
                Icon(
                    imageVector = TnIcons.Database,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "No installed models",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Standards.SpacingMd, vertical = Standards.SpacingSm),
            verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
        ) {
            items(models, key = { it.id }) { model ->
                InstalledModelCard(
                    model = model,
                    isDeleting = deleteInProgress == model.id,
                    onShowDetails = { selectedModel = model },
                    onDelete = { showDeleteDialog = model }
                )
            }
        }
    }

    selectedModel?.let { model ->
        ModelDetailsDialog(
            model = model,
            viewModel = viewModel,
            onDismiss = { selectedModel = null }
        )
    }

    showDeleteDialog?.let { model ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Model") },
            text = {
                Text("Are you sure you want to delete ${model.modelName}? This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(model)
                        showDeleteDialog = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ── InstalledModelCard ──

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun InstalledModelCard(
    model: Model,
    isDeleting: Boolean,
    onShowDetails: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(Standards.CardSmallCornerRadius)
    ) {
        Row(
            modifier = Modifier.padding(Standards.CardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
        ) {
            // Provider type icon
            Icon(
                imageVector = when (model.providerType) {
                    ProviderType.GGUF -> TnIcons.Sparkles
                    else -> TnIcons.Photo
                },
                contentDescription = null,
                modifier = Modifier.size(Standards.IconMd),
                tint = if (model.isActive) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.modelName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val sizeText by produceState("Calculating...", model.modelPath) {
                    value = withContext(Dispatchers.IO) {
                        val modelFile = File(model.modelPath)
                        if (modelFile.exists()) {
                            val sizeBytes = if (modelFile.isDirectory) {
                                modelFile.walkTopDown().sumOf { it.length() }
                            } else {
                                modelFile.length()
                            }
                            val sizeFormatted = formatBytes(sizeBytes)
                            val typeLabel = when (model.providerType) {
                                ProviderType.DIFFUSION -> "SD"
                                else -> model.providerType.name
                            }
                            val storageLabel = if (modelFile.isDirectory) "Folder" else "File"
                            "$typeLabel  ·  $storageLabel  ·  $sizeFormatted"
                        } else {
                            model.providerType.name
                        }
                    }
                }
                CaptionText(text = sizeText)
            }

            // Status dot
            StatusBadge(
                text = if (model.isActive) "Active" else "",
                isActive = model.isActive
            )

            // Actions
            if (isDeleting) {
                Box(
                    modifier = Modifier.size(Standards.ActionIconSize),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator(
                        modifier = Modifier.size(Standards.IconMd),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                MultiActionButton(
                    actions = listOf(
                        ActionItem(
                            icon = ActionIcon.Vector(TnIcons.InfoCircle),
                            onClick = onShowDetails,
                            contentDescription = "Details"
                        ),
                        ActionItem(
                            icon = ActionIcon.Vector(TnIcons.Trash),
                            onClick = onDelete,
                            contentDescription = "Delete"
                        )
                    )
                )
            }
        }
    }
}

// ── ModelDetailsDialog ──

@Composable
internal fun ModelDetailsDialog(
    model: Model,
    viewModel: ModelStoreViewModel,
    onDismiss: () -> Unit
) {
    var config by remember { mutableStateOf<com.dark.tool_neuron.models.table_schema.ModelConfig?>(null) }
    var configLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(model.id) {
        config = viewModel.getModelConfig(model.id)
        configLoaded = true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = model.modelName,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Standards.SpacingMd),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                val typeLabel = when (model.providerType) {
                    ProviderType.DIFFUSION -> "Stable Diffusion"
                    ProviderType.GGUF -> "GGUF (LLM)"
                    ProviderType.TTS -> "Text-to-Speech"
                }
                DetailRow("Type", typeLabel)
                DetailRow("Status", if (model.isActive) "Active" else "Inactive")

                val detailSizeText by produceState("Calculating...", model.modelPath) {
                    value = withContext(Dispatchers.IO) {
                        val modelFile = File(model.modelPath)
                        if (modelFile.exists()) {
                            val sizeBytes = if (modelFile.isDirectory) {
                                modelFile.walkTopDown().sumOf { it.length() }
                            } else {
                                modelFile.length()
                            }
                            formatBytes(sizeBytes)
                        } else "Not found"
                    }
                }
                val detailStorageType by produceState("--", model.modelPath) {
                    value = withContext(Dispatchers.IO) {
                        val modelFile = File(model.modelPath)
                        if (modelFile.exists()) {
                            if (modelFile.isDirectory) "Folder" else "File"
                        } else "--"
                    }
                }
                val detailFileCount by produceState("", model.modelPath) {
                    value = withContext(Dispatchers.IO) {
                        val modelFile = File(model.modelPath)
                        if (modelFile.exists() && modelFile.isDirectory) {
                            "${modelFile.walkTopDown().count { it.isFile }}"
                        } else ""
                    }
                }
                if (detailStorageType != "--") {
                    DetailRow("Storage", detailStorageType)
                    DetailRow("Size", detailSizeText)
                    if (detailFileCount.isNotEmpty()) {
                        DetailRow("Files", detailFileCount)
                    }
                }

                DetailRow("Path", model.modelPath)

                if (configLoaded && config != null) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = Standards.SpacingXs),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    when (model.providerType) {
                        ProviderType.GGUF -> {
                            val schema = com.dark.tool_neuron.models.engine_schema.GgufEngineSchema.fromJson(
                                config!!.modelLoadingParams,
                                config!!.modelInferenceParams
                            )
                            Text(
                                text = "Loading Config",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            DetailRow("Context Size", "${schema.loadingParams.ctxSize}")
                            DetailRow("Batch Size", "${schema.loadingParams.batchSize}")
                            DetailRow("Threads", if (schema.loadingParams.threads == 0) "Auto" else "${schema.loadingParams.threads}")
                            DetailRow("Memory Map", if (schema.loadingParams.useMmap) "Enabled" else "Disabled")

                            Spacer(modifier = Modifier.height(Standards.SpacingXs))
                            Text(
                                text = "Inference Config",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            DetailRow("Temperature", "${schema.inferenceParams.temperature}")
                            DetailRow("Top K", "${schema.inferenceParams.topK}")
                            DetailRow("Top P", "${schema.inferenceParams.topP}")
                            DetailRow("Max Tokens", "${schema.inferenceParams.maxTokens}")
                        }

                        ProviderType.DIFFUSION -> {
                            val loadingObj = config!!.modelLoadingParams?.let { json ->
                                try { org.json.JSONObject(json) } catch (_: Exception) { null }
                            }
                            val inferenceObj = config!!.modelInferenceParams?.let { json ->
                                try { org.json.JSONObject(json) } catch (_: Exception) { null }
                            }

                            Text(
                                text = "Model Config",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (loadingObj != null) {
                                DetailRow("Resolution", "${loadingObj.optInt("width", 512)} x ${loadingObj.optInt("height", 512)}")
                                DetailRow("Execution", if (loadingObj.optBoolean("run_on_cpu", false)) "CPU" else "NPU")
                                DetailRow("Text Embedding", "${loadingObj.optInt("text_embedding_size", 768)}")
                                DetailRow("Safety Mode", if (loadingObj.optBoolean("safety_mode", false)) "On" else "Off")
                            }

                            if (inferenceObj != null) {
                                Spacer(modifier = Modifier.height(Standards.SpacingXs))
                                Text(
                                    text = "Inference Config",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                DetailRow("Steps", "${inferenceObj.optInt("steps", 28)}")
                                DetailRow("CFG Scale", "${inferenceObj.optDouble("cfg_scale", 7.0)}")
                                DetailRow("Scheduler", inferenceObj.optString("scheduler", "dpm"))
                            }
                        }

                        ProviderType.TTS -> {
                            val ttsObj = config!!.modelInferenceParams?.let { json ->
                                try { org.json.JSONObject(json) } catch (_: Exception) { null }
                            }
                            Text(
                                text = "TTS Config",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (ttsObj != null) {
                                DetailRow("Voice", ttsObj.optString("voice", "F1"))
                                DetailRow("Speed", "${ttsObj.optDouble("speed", 1.05)}")
                                DetailRow("Language", ttsObj.optString("language", "en"))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

// ── DetailRow ──

@Composable
internal fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (label == "Path") maple else null
        )
    }
}
