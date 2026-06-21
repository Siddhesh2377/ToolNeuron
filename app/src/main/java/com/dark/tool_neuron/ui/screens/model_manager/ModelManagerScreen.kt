package com.dark.tool_neuron.ui.screens.model_manager

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.model.ModelInfo
import com.dark.tool_neuron.model.enums.PathType
import com.dark.tool_neuron.model.enums.ProviderType
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.CaptionText
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.screens.model_store.ModelTypePickerDialog
import com.dark.tool_neuron.ui.screens.model_store.providerTypeLabel
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.util.extractParameterCount
import com.dark.tool_neuron.util.extractQuantization
import com.dark.download_manager.formatBytes
import com.dark.tool_neuron.viewmodel.ModelStoreViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerScreen(
    viewModel: ModelStoreViewModel,
    onBack: () -> Unit,
    onEditModel: (modelId: String) -> Unit,
) {
    val dimens = LocalDimens.current
    val installed by viewModel.installedModels.collectAsStateWithLifecycle()
    val deleteInProgress by viewModel.deleteInProgress.collectAsStateWithLifecycle()
    val backupStatus by viewModel.backupStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let { viewModel.exportModels(it) } }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.importModels(it) } }
    var pendingDelete by remember { mutableStateOf<ModelInfo?>(null) }
    var pendingTypeChange by remember { mutableStateOf<ModelInfo?>(null) }
    var selectedType by remember { mutableStateOf<ProviderType?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Model Settings", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Edit configs, categories, delete installed models",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    ActionButton(
                        onClickListener = onBack,
                        icon = TnIcons.ArrowLeft,
                        contentDescription = "Back",
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        if (installed.isEmpty()) {
            EmptyState(modifier = Modifier.padding(padding))
        } else {
            val grouped = remember(installed) {
                SECTIONS.mapNotNull { section ->
                    val items = installed.filter { it.providerType == section.type }
                    if (items.isEmpty()) null else section to items
                }
            }
            val visibleGroups = remember(grouped, selectedType) {
                if (selectedType == null) grouped else grouped.filter { it.first.type == selectedType }
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    horizontal = dimens.screenPadding,
                    vertical = dimens.spacingSm,
                ),
                verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
            ) {
                item(key = "backup-actions") {
                    BackupActionsCard(
                        status = backupStatus,
                        onExport = {
                            exportLauncher.launch("Tool-Neuron-models.zip")
                        },
                        onImport = {
                            importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                        },
                    )
                }
                item(key = "tabs") {
                    ModelTypeTabs(
                        groups = grouped.map { it.first },
                        selectedType = selectedType,
                        onSelect = { selectedType = it },
                    )
                }
                visibleGroups.forEach { (section, items) ->
                    item(key = "header-${section.type.name}") {
                        SectionHeader(label = section.label, count = items.size, blurb = section.blurb)
                    }
                    items(items, key = { it.id }) { model ->
                        ModelManagerCard(
                            model = model,
                            isDeleting = deleteInProgress == model.id,
                            onEdit = { onEditModel(model.id) },
                            onChangeType = { pendingTypeChange = model },
                            onDelete = { pendingDelete = model },
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(backupStatus) {
        val status = backupStatus ?: return@LaunchedEffect
        Toast.makeText(context, status, Toast.LENGTH_SHORT).show()
        viewModel.clearBackupStatus()
    }

    pendingDelete?.let { model ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete model") },
            text = { Text("Delete ${model.name} and its config? This cannot be undone.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteModel(model)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }

    pendingTypeChange?.let { model ->
        ModelTypePickerDialog(
            title = "Model type",
            fileName = model.name,
            selectedType = model.providerType,
            onPick = { type ->
                viewModel.updateModelProviderType(model.id, type)
                pendingTypeChange = null
            },
            onDismiss = { pendingTypeChange = null },
        )
    }
}

@Composable
private fun BackupActionsCard(
    status: String?,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = tnShapes.cardSmall,
    ) {
        Column(
            modifier = Modifier.padding(dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingXs),
        ) {
            Text(
                text = "Model backup",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            CaptionText(
                text = "Export or restore installed models, roles, and per-model config as a ToolNeuron ZIP.",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs)) {
                Button(onClick = onExport) { Text("Export") }
                Button(onClick = onImport) { Text("Import") }
            }
            if (!status.isNullOrBlank()) CaptionText(text = status)
        }
    }
}

@Composable
private fun ModelManagerCard(
    model: ModelInfo,
    isDeleting: Boolean,
    onEdit: () -> Unit,
    onChangeType: () -> Unit,
    onDelete: () -> Unit,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = tnShapes.cardSmall,
    ) {
        Row(
            modifier = Modifier.padding(dimens.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            Icon(
                TnIcons.Sparkles,
                contentDescription = null,
                modifier = Modifier.size(dimens.iconMd),
                tint = if (model.isActive) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
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
                    val params = remember(model.name) { extractParameterCount(model.name) }
                    val quant = remember(model.path, model.name) {
                        extractQuantization(model.path) ?: extractQuantization(model.name)
                    }
                    if (!params.isNullOrBlank()) Tag(text = params)
                    if (!quant.isNullOrBlank()) Tag(text = quant)
                }
                val typeLabel = providerTypeLabel(model.providerType)
                val sourceLabel = if (model.pathType == PathType.CONTENT_URI) "Local" else "Downloaded"
                CaptionText(
                    text = if (model.fileSize > 0)
                        "$typeLabel  ·  $sourceLabel  ·  ${formatBytes(model.fileSize)}"
                    else "$typeLabel  ·  $sourceLabel",
                )
            }

            if (isDeleting) {
                Box(
                    modifier = Modifier.size(dimens.actionIconSize),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(dimens.iconMd),
                        strokeWidth = 2.dp,
                    )
                }
            } else {
                ActionButton(
                    onClickListener = onEdit,
                    icon = TnIcons.Edit,
                    contentDescription = "Edit config",
                )
                ActionButton(
                    onClickListener = onChangeType,
                    icon = TnIcons.Settings,
                    contentDescription = "Change type",
                )
                ActionButton(
                    onClickListener = onDelete,
                    icon = TnIcons.Trash,
                    contentDescription = "Delete model",
                )
            }
        }
    }
}

@Composable
private fun ModelTypeTabs(
    groups: List<Section>,
    selectedType: ProviderType?,
    onSelect: (ProviderType?) -> Unit,
) {
    val dimens = LocalDimens.current
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
        contentPadding = PaddingValues(vertical = dimens.spacingXs),
    ) {
        item(key = "all") {
            FilterChip(
                selected = selectedType == null,
                onClick = { onSelect(null) },
                label = { Text("All") },
            )
        }
        items(groups, key = { it.type.name }) { section ->
            FilterChip(
                selected = selectedType == section.type,
                onClick = { onSelect(section.type) },
                label = { Text(section.tabLabel) },
            )
        }
    }
}

private data class Section(
    val type: ProviderType,
    val label: String,
    val blurb: String,
    val tabLabel: String,
)

private val SECTIONS = listOf(
    Section(ProviderType.GGUF,      "Chat models",       "Used for the conversation in the chat screen.", "Chat"),
    Section(ProviderType.VISION_CHAT, "Vision chat models", "Used for chat with image attachments.", "Vision"),
    Section(ProviderType.TOOL_SEARCH, "Tool/Search models", "Used for query planning and structured tool calls.", "Tools"),
    Section(ProviderType.EMBEDDING, "Embedding models",  "Used to index documents you attach to chats (RAG).", "RAG"),
    Section(ProviderType.IMAGE_GEN, "Image generation",  "Used by the local image workspace.", "Image"),
    Section(ProviderType.IMAGE_UPSCALER, "Image upscalers", "Used by image upscale mode.", "Upscale"),
    Section(ProviderType.TTS,       "Text-to-Speech",    "Reads model replies aloud.", "TTS"),
    Section(ProviderType.STT,       "Speech-to-Text",    "Transcribes voice input.", "STT"),
)

@Composable
private fun SectionHeader(label: String, count: Int, blurb: String) {
    val dimens = LocalDimens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = dimens.spacingMd, bottom = dimens.spacingXxs),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = LocalTnShapes.current.full,
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
                )
            }
        }
        Text(
            text = blurb,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Tag(text: String) {
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
private fun EmptyState(modifier: Modifier = Modifier) {
    val dimens = LocalDimens.current
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            Icon(
                imageVector = TnIcons.Database,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "No installed models",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Download one from the Model Store first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
