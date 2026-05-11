package com.dark.tool_neuron.ui.screens.model_store

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.model.ModelConfig
import com.dark.tool_neuron.model.ModelInfo
import com.dark.tool_neuron.model.enums.PathType
import com.dark.tool_neuron.model.enums.ProviderType
import com.dark.tool_neuron.model.ui.ActionIcon
import com.dark.tool_neuron.model.ui.ActionItem
import com.dark.tool_neuron.ui.components.CaptionText
import com.dark.tool_neuron.ui.components.MultiActionButton
import com.dark.tool_neuron.ui.components.StatusBadge
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.ui.theme.maple
import com.dark.tool_neuron.util.VlmPaths
import com.dark.tool_neuron.util.extractParameterCount
import com.dark.tool_neuron.util.extractQuantization
import com.dark.download_manager.formatBytes
import com.dark.tool_neuron.viewmodel.ModelStoreViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun InstalledModelsTab(
    models: List<ModelInfo>,
    deleteInProgress: String?,
    onDelete: (ModelInfo) -> Unit,
    onLoad: (ModelInfo) -> Unit,
    onUnload: () -> Unit,
    viewModel: ModelStoreViewModel
) {
    val dimens = LocalDimens.current
    val context = LocalContext.current
    val defaultEmbeddingId by viewModel.defaultEmbeddingModelId.collectAsStateWithLifecycle()

    var selectedModel by remember { mutableStateOf<ModelInfo?>(null) }
    var showDeleteDialog by remember { mutableStateOf<ModelInfo?>(null) }
    var pendingImport by remember {
        mutableStateOf<Triple<android.net.Uri, String, Long>?>(null)
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            var name = "model.gguf"
            var size = 0L
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIdx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (nameIdx >= 0) name = it.getString(nameIdx)
                    if (sizeIdx >= 0) size = it.getLong(sizeIdx)
                }
            }
            pendingImport = Triple(uri, name, size)
        }
    }

    if (models.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(dimens.spacingLg)
            ) {
                Icon(TnIcons.Database, null, Modifier.size(64.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                Text("No installed models", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = dimens.spacingMd, vertical = dimens.spacingSm),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingSm)
        ) {
            item(key = "import") {
                ImportLocalCard {
                    filePicker.launch(arrayOf("application/octet-stream", "*/*"))
                }
            }

            items(models, key = { it.id }) { model ->
                val isDefaultEmbedding = model.providerType == ProviderType.EMBEDDING &&
                    model.id == defaultEmbeddingId
                InstalledModelCard(
                    model = model,
                    isDeleting = deleteInProgress == model.id,
                    isDefaultEmbedding = isDefaultEmbedding,
                    onShowDetails = { selectedModel = model },
                    onDelete = { showDeleteDialog = model },
                    onLoad = { onLoad(model) },
                    onUnload = onUnload,
                    onToggleDefaultEmbedding = {
                        viewModel.setDefaultEmbeddingModel(
                            if (isDefaultEmbedding) null else model.id
                        )
                    },
                )
            }
        }
    }

    selectedModel?.let { model ->
        ModelDetailsDialog(model, viewModel) { selectedModel = null }
    }

    showDeleteDialog?.let { model ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Model") },
            text = { Text("Are you sure you want to delete ${model.name}? This action cannot be undone.") },
            confirmButton = {
                Button(onClick = { onDelete(model); showDeleteDialog = null }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("Cancel") }
            }
        )
    }

    pendingImport?.let { (uri, name, size) ->
        ModelImportTypePicker(
            fileName = name,
            onPick = { type ->
                viewModel.importLocalModel(uri, name, size, type)
                pendingImport = null
            },
            onDismiss = { pendingImport = null },
        )
    }
}

@Composable
private fun ImportLocalCard(onClick: () -> Unit) {
    val dimens = LocalDimens.current
    Surface(
        onClick = onClick,
        shape = LocalTnShapes.current.card,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(dimens.spacingMd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm)
        ) {
            Icon(TnIcons.Download, null, Modifier.size(20.dp), MaterialTheme.colorScheme.primary)
            Column {
                Text("Import local model", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text("Pick a .gguf chat or embedding model from storage", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun InstalledModelCard(
    model: ModelInfo,
    isDeleting: Boolean,
    isDefaultEmbedding: Boolean,
    onShowDetails: () -> Unit,
    onDelete: () -> Unit,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
    onToggleDefaultEmbedding: () -> Unit,
) {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current
    val isEmbedding = model.providerType == ProviderType.EMBEDDING

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = shapes.cardSmall
    ) {
        Row(
            modifier = Modifier.padding(dimens.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm)
        ) {
            Icon(
                TnIcons.Sparkles, null,
                Modifier.size(dimens.iconMd),
                tint = if (model.isActive || isDefaultEmbedding) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacingXxs),
                ) {
                    Text(
                        model.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    val quant = remember(model.path, model.name) {
                        extractQuantization(model.path) ?: extractQuantization(model.name)
                    }
                    val params = remember(model.name) { extractParameterCount(model.name) }
                    val isVlm = remember(model.path) {
                        model.pathType == PathType.FILE &&
                            model.path.contains("/${VlmPaths.VLM_DIR}/")
                    }
                    if (isVlm) ModelTag(text = "VLM")
                    if (!params.isNullOrBlank()) ModelTag(text = params)
                    if (!quant.isNullOrBlank()) ModelTag(text = quant)
                }
                val sizeText by produceState("Calculating...", model.path) {
                    value = withContext(Dispatchers.IO) {
                        if (model.fileSize > 0) {
                            val typeLabel = model.providerType.name
                            val sourceLabel = if (model.pathType == PathType.CONTENT_URI) "Local" else "Downloaded"
                            "$typeLabel  \u00B7  $sourceLabel  \u00B7  ${formatBytes(model.fileSize)}"
                        } else {
                            val f = File(model.path)
                            if (f.exists()) {
                                "${model.providerType.name}  \u00B7  ${formatBytes(f.length())}"
                            } else model.providerType.name
                        }
                    }
                }
                CaptionText(text = sizeText)
            }

            val badgeText = when {
                isEmbedding && isDefaultEmbedding -> "Default"
                model.isActive -> "Active"
                else -> ""
            }
            StatusBadge(text = badgeText, isActive = model.isActive || isDefaultEmbedding)

            if (isDeleting) {
                Box(Modifier.size(dimens.actionIconSize), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(dimens.iconMd), strokeWidth = 2.dp)
                }
            } else {
                MultiActionButton(
                    actions = buildList {
                        if (isEmbedding) {
                            val starIcon = if (isDefaultEmbedding) TnIcons.Star else TnIcons.StarOutline
                            val starLabel = if (isDefaultEmbedding) "Unset default" else "Set as default"
                            add(ActionItem(ActionIcon.Vector(starIcon), onToggleDefaultEmbedding, starLabel))
                        }
                        if (model.isActive) {
                            add(ActionItem(ActionIcon.Vector(TnIcons.PlayerStop), onUnload, "Unload"))
                        }
                        add(ActionItem(ActionIcon.Vector(TnIcons.InfoCircle), onShowDetails, "Details"))
                        add(ActionItem(ActionIcon.Vector(TnIcons.Trash), onDelete, "Delete"))
                    }
                )
            }
        }
    }
}

@Composable
internal fun ModelDetailsDialog(
    model: ModelInfo,
    viewModel: ModelStoreViewModel,
    onDismiss: () -> Unit
) {
    var config by remember { mutableStateOf<ModelConfig?>(null) }
    var configLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(model.id) {
        config = viewModel.getModelConfig(model.id)
        configLoaded = true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(model.name, style = MaterialTheme.typography.titleLarge) },
        text = {
            val dimens = LocalDimens.current
            Column(
                verticalArrangement = Arrangement.spacedBy(dimens.spacingMd),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                DetailRow("Type", when (model.providerType) {
                    ProviderType.GGUF -> "GGUF (LLM)"
                    ProviderType.TTS -> "Text-to-Speech"
                    ProviderType.STT -> "Speech-to-Text"
                    ProviderType.EMBEDDING -> "Embedding (RAG)"
                    ProviderType.IMAGE_GEN -> "Image Generation"
                    ProviderType.IMAGE_UPSCALER -> "Upscaler"
                })
                DetailRow("Status", if (model.isActive) "Active" else "Inactive")

                val sizeText by produceState("Calculating...", model.path) {
                    value = withContext(Dispatchers.IO) {
                        if (model.fileSize > 0) formatBytes(model.fileSize)
                        else {
                            val f = File(model.path)
                            if (f.exists()) formatBytes(f.length()) else "Not found"
                        }
                    }
                }
                DetailRow("Size", sizeText)
                DetailRow("Path", model.path)

                if (configLoaded && config != null) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = dimens.spacingXs),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    Text("Loading Config", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    DetailRow("Loading Params", config!!.loadingParamsJson)
                    Spacer(Modifier.height(dimens.spacingXs))
                    Text("Inference Config", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    DetailRow("Inference Params", config!!.inferenceParamsJson)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun ModelTag(text: String) {
    Surface(
        shape = LocalTnShapes.current.full,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
internal fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = if (label == "Path") maple else null)
    }
}
