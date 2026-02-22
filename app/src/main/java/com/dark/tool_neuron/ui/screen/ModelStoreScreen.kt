package com.dark.tool_neuron.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.tool_neuron.R
import com.dark.tool_neuron.models.data.HFModelRepository
import com.dark.tool_neuron.models.data.HuggingFaceModel
import com.dark.tool_neuron.models.data.ModelCategory
import com.dark.tool_neuron.models.data.ModelType
import com.dark.tool_neuron.models.table_schema.Model
import com.dark.tool_neuron.repo.ValidationResult
import com.dark.tool_neuron.service.ModelDownloadService
import com.dark.tool_neuron.global.Standards
import com.dark.tool_neuron.models.enums.ProviderType
import com.dark.tool_neuron.models.ui.ActionIcon
import com.dark.tool_neuron.models.ui.ActionItem
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.ActionProgressButton
import com.dark.tool_neuron.ui.components.ActionSwitch
import com.dark.tool_neuron.ui.components.CaptionText
import com.dark.tool_neuron.ui.components.MultiActionButton
import com.dark.tool_neuron.ui.components.SectionHeader
import com.dark.tool_neuron.ui.components.StandardCard
import com.dark.tool_neuron.ui.components.StatusBadge
import com.dark.tool_neuron.ui.theme.maple
import com.dark.tool_neuron.ui.theme.rDp
import com.dark.tool_neuron.utils.SizeCategory
import com.dark.tool_neuron.viewmodel.ModelStoreViewModel
import com.dark.tool_neuron.viewmodel.SortOption
import java.io.File
import java.util.Locale

enum class StoreTab {
    MODELS, INSTALLED, SETTINGS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelStoreScreen(
    onNavigateBack: () -> Unit, viewModel: ModelStoreViewModel = viewModel()
) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val models by viewModel.filteredModels.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val downloadStates by viewModel.downloadStates.collectAsState()
    val installedModels by viewModel.installedModels.collectAsState()
    val deviceInfo by viewModel.deviceInfo.collectAsState()
    val deleteInProgress by viewModel.deleteInProgress.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (showSearch) {
                SearchAppBar(searchQuery = searchQuery, onSearchQueryChange = {
                    searchQuery = it
                    viewModel.filterModels(it)
                }, onCloseSearch = {
                    showSearch = false
                    searchQuery = ""
                    viewModel.filterModels("")
                })
            } else {
                TopAppBar(title = { Text("Model Store") }, navigationIcon = {
                    ActionButton(
                        onClickListener = onNavigateBack,
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }, actions = {
                    if (selectedTab == StoreTab.MODELS) {
                        ActionButton(
                            onClickListener = { viewModel.refreshModels() },
                            icon = Icons.Default.Refresh,
                            contentDescription = "Refresh"
                        )
                        ActionButton(
                            onClickListener = { showSearch = true },
                            icon = Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    }
                })
            }
        }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab Row
            SecondaryTabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == StoreTab.MODELS,
                    onClick = { viewModel.selectTab(StoreTab.MODELS) },
                    text = {
                        Text(
                            "Store",
                            fontWeight = if (selectedTab == StoreTab.MODELS) FontWeight.SemiBold else FontWeight.Normal
                        )
                    })
                Tab(
                    selected = selectedTab == StoreTab.INSTALLED,
                    onClick = { viewModel.selectTab(StoreTab.INSTALLED) },
                    text = {
                        Text(
                            "Installed",
                            fontWeight = if (selectedTab == StoreTab.INSTALLED) FontWeight.SemiBold else FontWeight.Normal
                        )
                    })
                Tab(
                    selected = selectedTab == StoreTab.SETTINGS,
                    onClick = { viewModel.selectTab(StoreTab.SETTINGS) },
                    text = {
                        Text(
                            "Settings",
                            fontWeight = if (selectedTab == StoreTab.SETTINGS) FontWeight.SemiBold else FontWeight.Normal
                        )
                    })
            }

            // Tab Content
            AnimatedContent(
                targetState = selectedTab, transitionSpec = {
                    fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) togetherWith fadeOut(
                        animationSpec = spring(stiffness = Spring.StiffnessMedium)
                    )
                }, label = "tab_content"
            ) { tab ->
                when (tab) {
                    StoreTab.MODELS -> ModelsTab(
                        models = models,
                        isLoading = isLoading,
                        error = error,
                        downloadStates = downloadStates,
                        installedModelNames = installedModels.map { it.modelName }.toSet(),
                        viewModel = viewModel,
                        onDownload = { viewModel.downloadModel(it) },
                        onCancelDownload = { modelId -> viewModel.cancelDownload(modelId) },
                        onRetry = { viewModel.loadModels() })

                    StoreTab.INSTALLED -> InstalledModelsTab(
                        models = installedModels,
                        deleteInProgress = deleteInProgress,
                        onDelete = { viewModel.deleteModel(it) }
                    )

                    StoreTab.SETTINGS -> SettingsTab(
                        deviceInfo = deviceInfo, viewModel = viewModel
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModelsTab(
    models: List<HuggingFaceModel>,
    isLoading: Boolean,
    error: String?,
    downloadStates: Map<String, ModelDownloadService.DownloadState>,
    installedModelNames: Set<String>,
    viewModel: ModelStoreViewModel,
    onDownload: (HuggingFaceModel) -> Unit,
    onCancelDownload: (String) -> Unit,
    onRetry: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ModelFiltersSection(viewModel = viewModel)

        when {
            isLoading && models.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            }

            error != null && models.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(rDp(16.dp))
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.error),
                            contentDescription = null,
                            modifier = Modifier.size(rDp(48.dp)),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Error loading models",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = onRetry) {
                            Text("Retry")
                        }
                    }
                }
            }

            models.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(rDp(48.dp)),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "No models found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            else -> {
                // Group models by repository for organized display
                val groupedModels = remember(models) {
                    models.groupBy { model ->
                        when (model.modelType) {
                            ModelType.GGUF -> model.repositoryUrl.ifEmpty { "Unknown" }
                            ModelType.SD -> if (model.runOnCpu) "CPU Image Models" else "NPU Image Models"
                            ModelType.TTS -> "Text-to-Speech"
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (isLoading) Modifier.blur(rDp(4.dp)) else Modifier),
                        contentPadding = PaddingValues(horizontal = rDp(12.dp), vertical = rDp(8.dp)),
                        verticalArrangement = Arrangement.spacedBy(rDp(6.dp)),
                        flingBehavior = ScrollableDefaults.flingBehavior()
                    ) {
                        groupedModels.forEach { (group, groupModels) ->
                            // Group header
                            item(key = "header-$group") {
                                Text(
                                    text = group,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(
                                        top = rDp(8.dp),
                                        bottom = rDp(2.dp),
                                        start = rDp(4.dp)
                                    )
                                )
                            }

                            items(
                                items = groupModels,
                                key = { model -> model.id }
                            ) { model ->
                                ModelCard(
                                    model = model,
                                    isInstalled = installedModelNames.contains(model.name),
                                    downloadState = downloadStates[model.id],
                                    onDownload = { onDownload(model) },
                                    onCancelDownload = { onCancelDownload(model.id) }
                                )
                            }
                        }
                    }

                    if (isLoading) {
                        LoadingIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InstalledModelsTab(
    models: List<Model>,
    deleteInProgress: String?,
    onDelete: (Model) -> Unit
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
                verticalArrangement = Arrangement.spacedBy(rDp(16.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = null,
                    modifier = Modifier.size(rDp(64.dp)),
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
            contentPadding = PaddingValues(horizontal = rDp(12.dp), vertical = rDp(8.dp)),
            verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingSm))
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

    // Model Details Dialog
    selectedModel?.let { model ->
        ModelDetailsDialog(
            model = model,
            onDismiss = { selectedModel = null }
        )
    }

    // Delete Confirmation Dialog
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

@Composable
private fun InstalledModelCard(
    model: Model,
    isDeleting: Boolean,
    onShowDetails: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(rDp(Standards.CardSmallCornerRadius))
    ) {
        Row(
            modifier = Modifier.padding(rDp(Standards.CardPadding)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingSm))
        ) {
            // Provider type icon
            Icon(
                painter = painterResource(
                    when (model.providerType) {
                        ProviderType.GGUF -> R.drawable.smart_temp_message
                        else -> R.drawable.vl_models
                    }
                ),
                contentDescription = null,
                modifier = Modifier.size(rDp(Standards.IconMd)),
                tint = if (model.isActive) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Name + subtitle
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.modelName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val modelFile = File(model.modelPath)
                val sizeText = if (modelFile.exists()) {
                    val sizeInMB = modelFile.length() / (1024 * 1024)
                    "${model.providerType.name}  ·  ${sizeInMB} MB"
                } else {
                    model.providerType.name
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
                    modifier = Modifier.size(rDp(Standards.ActionIconSize)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(rDp(Standards.IconMd)),
                        strokeWidth = rDp(2.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                MultiActionButton(
                    actions = listOf(
                        ActionItem(
                            icon = ActionIcon.Vector(Icons.Default.Info),
                            onClick = onShowDetails,
                            contentDescription = "Details"
                        ),
                        ActionItem(
                            icon = ActionIcon.Vector(Icons.Default.Delete),
                            onClick = onDelete,
                            contentDescription = "Delete"
                        )
                    )
                )
            }
        }
    }
}

@Composable
private fun ModelDetailsDialog(
    model: Model,
    onDismiss: () -> Unit
) {
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
                verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
            ) {
                DetailRow("ID", model.id)
                DetailRow("Provider", model.providerType.name)
                DetailRow("Status", if (model.isActive) "Active" else "Inactive")
                DetailRow("Path", model.modelPath)

                val modelFile = File(model.modelPath)
                if (modelFile.exists()) {
                    val sizeInMB = modelFile.length() / (1024 * 1024)
                    DetailRow("Size", "${sizeInMB} MB")
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

@Composable
private fun DetailRow(label: String, value: String) {
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

@Composable
private fun SettingsTab(
    deviceInfo: Map<String, String>, viewModel: ModelStoreViewModel
) {
    val repositories by viewModel.repositories.collectAsStateWithLifecycle(emptyList())
    val validationResults by viewModel.validationResults.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingRepository by remember { mutableStateOf<HFModelRepository?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = rDp(Standards.SpacingLg), vertical = rDp(Standards.SpacingSm)),
        verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingSm))
    ) {
        // Device Info Section
        item {
            DeviceInfoCard(deviceInfo)
        }

        // Repositories Section
        item {
            SectionHeader(
                title = "Model Repositories",
                action = {
                    ActionButton(
                        onClickListener = { showAddDialog = true },
                        icon = Icons.Default.Add,
                        contentDescription = "Add Repository"
                    )
                }
            )
        }

        items(repositories, key = { it.id }) { repo ->
            RepositoryCard(
                repository = repo,
                validationResult = validationResults[repo.id],
                onToggle = { viewModel.toggleRepository(repo.id) },
                onEdit = { editingRepository = repo },
                onValidate = { viewModel.validateRepository(repo) },
                onDelete = { viewModel.removeRepository(repo.id) }
            )
        }
    }

    if (showAddDialog) {
        AddRepositoryDialog(onDismiss = { showAddDialog = false }, onAdd = { repo ->
            viewModel.addRepository(repo)
            showAddDialog = false
        })
    }

    editingRepository?.let { repo ->
        EditRepositoryDialog(
            repository = repo,
            onDismiss = { editingRepository = null },
            onSave = { updatedRepo ->
                viewModel.updateRepository(updatedRepo)
                editingRepository = null
            }
        )
    }
}

@Composable
private fun DeviceInfoCard(deviceInfo: Map<String, String>) {
    var expanded by remember { mutableStateOf(false) }
    val entries = deviceInfo.entries.toList()
    val previewEntries = entries.take(3)
    val remainingEntries = entries.drop(3)

    StandardCard(
        title = "Device Information",
        iconRes = R.drawable.prompt,
        trailing = {
            if (remainingEntries.isNotEmpty()) {
                ActionButton(
                    onClickListener = { expanded = !expanded },
                    icon = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingXs))) {
            previewEntries.forEach { (key, value) ->
                DeviceInfoRow(
                    label = key.replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
                    },
                    value = value
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingXs))) {
                    remainingEntries.forEach { (key, value) ->
                        DeviceInfoRow(
                            label = key.replaceFirstChar {
                                if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
                            },
                            value = value
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun RepositoryCard(
    repository: HFModelRepository,
    validationResult: ValidationResult?,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onValidate: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(rDp(Standards.CardSmallCornerRadius)),
        onClick = onValidate
    ) {
        Column(
            modifier = Modifier.padding(rDp(Standards.CardPadding)),
            verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingXs))
        ) {
            // Row 1: validation dot + name + actions + switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingSm))
            ) {
                // Validation status dot
                val dotColor = when (validationResult) {
                    is ValidationResult.Valid -> MaterialTheme.colorScheme.primary
                    is ValidationResult.Invalid -> MaterialTheme.colorScheme.error
                    is ValidationResult.Checking -> MaterialTheme.colorScheme.tertiary
                    null -> MaterialTheme.colorScheme.outlineVariant
                }
                if (validationResult is ValidationResult.Checking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(rDp(10.dp)),
                        strokeWidth = rDp(1.5.dp),
                        color = dotColor
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(rDp(6.dp))
                            .background(dotColor, RoundedCornerShape(50))
                    )
                }

                // Repo name
                Text(
                    text = repository.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (repository.isEnabled) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )

                // Grouped Edit + Delete
                MultiActionButton(
                    actions = listOf(
                        ActionItem(
                            icon = ActionIcon.Vector(Icons.Default.Edit),
                            onClick = onEdit,
                            contentDescription = "Edit"
                        ),
                        ActionItem(
                            icon = ActionIcon.Vector(Icons.Default.Delete),
                            onClick = onDelete,
                            contentDescription = "Delete"
                        )
                    )
                )

                // Toggle
                ActionSwitch(
                    checked = repository.isEnabled,
                    onCheckedChange = { onToggle() }
                )
            }

            // Row 2: repo path + category + GGUF count (inline)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingXs)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = repository.repoPath,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = maple,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                CaptionText(text = "·")
                CaptionText(text = repository.category.displayName)

                if (validationResult is ValidationResult.Valid) {
                    CaptionText(text = "·")
                    CaptionText(
                        text = "${validationResult.ggufFileCount} GGUF",
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (validationResult is ValidationResult.Invalid) {
                    CaptionText(text = "·")
                    CaptionText(
                        text = validationResult.reason,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun AddRepositoryDialog(
    onDismiss: () -> Unit, onAdd: (HFModelRepository) -> Unit
) {
    var repoName by remember { mutableStateOf("") }
    var repoPath by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ModelType.GGUF) }

    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add Repository") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(rDp(12.dp))) {
            OutlinedTextField(
                value = repoName,
                onValueChange = { repoName = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = repoPath,
                onValueChange = { repoPath = it },
                label = { Text("Repository Path") },
                placeholder = { Text("username/repo-name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
            ) {
                FilterChip(
                    selected = selectedType == ModelType.GGUF,
                    onClick = { selectedType = ModelType.GGUF },
                    label = { Text("GGUF") })
                FilterChip(
                    selected = selectedType == ModelType.SD,
                    onClick = { selectedType = ModelType.SD },
                    label = { Text("Stable Diffusion") })
            }
        }
    }, confirmButton = {
        Button(
            onClick = {
                if (repoName.isNotBlank() && repoPath.isNotBlank()) {
                    onAdd(
                        HFModelRepository(
                            id = repoPath.replace("/", "-"),
                            name = repoName,
                            repoPath = repoPath,
                            modelType = selectedType
                        )
                    )
                }
            }, enabled = repoName.isNotBlank() && repoPath.isNotBlank()
        ) {
            Text("Add")
        }
    }, dismissButton = {
        TextButton(onClick = onDismiss) {
            Text("Cancel")
        }
    })
}

@Composable
private fun EditRepositoryDialog(
    repository: HFModelRepository,
    onDismiss: () -> Unit,
    onSave: (HFModelRepository) -> Unit
) {
    var repoName by remember { mutableStateOf(repository.name) }
    var repoPath by remember { mutableStateOf(repository.repoPath) }
    var selectedType by remember { mutableStateOf(repository.modelType) }
    var selectedCategory by remember { mutableStateOf(repository.category) }
    var showCategoryDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Repository") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(rDp(12.dp))) {
                OutlinedTextField(
                    value = repoName,
                    onValueChange = { repoName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = repoPath,
                    onValueChange = { repoPath = it },
                    label = { Text("Repository Path") },
                    placeholder = { Text("username/repo-name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Model Type
                Text(
                    text = "Model Type",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
                ) {
                    FilterChip(
                        selected = selectedType == ModelType.GGUF,
                        onClick = { selectedType = ModelType.GGUF },
                        label = { Text("GGUF") }
                    )
                    FilterChip(
                        selected = selectedType == ModelType.SD,
                        onClick = { selectedType = ModelType.SD },
                        label = { Text("Stable Diffusion") }
                    )
                }

                // Category Dropdown
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Category chips displayed as a grid
                Column(verticalArrangement = Arrangement.spacedBy(rDp(8.dp)), modifier = Modifier.scrollable(rememberScrollState(), orientation = Orientation.Horizontal)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
                    ) {
                        FilterChip(
                            selected = selectedCategory == ModelCategory.GENERAL,
                            onClick = { selectedCategory = ModelCategory.GENERAL },
                            label = { Text("General") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedCategory == ModelCategory.MEDICAL,
                            onClick = { selectedCategory = ModelCategory.MEDICAL },
                            label = { Text("Medical") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
                    ) {
                        FilterChip(
                            selected = selectedCategory == ModelCategory.RESEARCH,
                            onClick = { selectedCategory = ModelCategory.RESEARCH },
                            label = { Text("Research") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedCategory == ModelCategory.CODING,
                            onClick = { selectedCategory = ModelCategory.CODING },
                            label = { Text("Coding") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
                    ) {
                        FilterChip(
                            selected = selectedCategory == ModelCategory.UNCENSORED,
                            onClick = { selectedCategory = ModelCategory.UNCENSORED },
                            label = { Text("Uncensored") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedCategory == ModelCategory.BUSINESS,
                            onClick = { selectedCategory = ModelCategory.BUSINESS },
                            label = { Text("Business") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
                    ) {
                        FilterChip(
                            selected = selectedCategory == ModelCategory.CYBERSECURITY,
                            onClick = { selectedCategory = ModelCategory.CYBERSECURITY },
                            label = { Text("Cybersecurity") },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (repoName.isNotBlank() && repoPath.isNotBlank()) {
                        onSave(
                            repository.copy(
                                name = repoName,
                                repoPath = repoPath,
                                modelType = selectedType,
                                category = selectedCategory
                            )
                        )
                    }
                },
                enabled = repoName.isNotBlank() && repoPath.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchAppBar(
    searchQuery: String, onSearchQueryChange: (String) -> Unit, onCloseSearch: () -> Unit
) {
    TopAppBar(title = {
        TextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search models...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }, navigationIcon = {
        IconButton(onClick = onCloseSearch) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close search")
        }
    })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelFiltersSection(
    viewModel: ModelStoreViewModel
) {
    val selectedModelType by viewModel.selectedModelType.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val selectedParameters by viewModel.selectedParameters.collectAsState()
    val selectedQuantizations by viewModel.selectedQuantizations.collectAsState()
    val selectedSizeCategory by viewModel.selectedSizeCategory.collectAsState()
    val sortBy by viewModel.sortBy.collectAsState()

    var showAdvancedFilters by remember { mutableStateOf(false) }

    val activeFilterCount = listOf(
        selectedModelType != null,
        selectedCategory != null,
        selectedParameters.isNotEmpty(),
        selectedQuantizations.isNotEmpty(),
        selectedSizeCategory != null
    ).count { it }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = rDp(8.dp))
    ) {
        // Model type filter (always visible, top-level)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = rDp(16.dp)),
            horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
        ) {
            FilterChip(
                selected = selectedModelType == null,
                onClick = { viewModel.filterByModelType(null) },
                label = { Text("All") }
            )
            FilterChip(
                selected = selectedModelType == ModelType.GGUF,
                onClick = { viewModel.filterByModelType(ModelType.GGUF) },
                label = { Text("LLM (GGUF)") }
            )
            FilterChip(
                selected = selectedModelType == ModelType.SD,
                onClick = { viewModel.filterByModelType(ModelType.SD) },
                label = { Text("Image (SD)") }
            )
            FilterChip(
                selected = selectedModelType == ModelType.TTS,
                onClick = { viewModel.filterByModelType(ModelType.TTS) },
                label = { Text("TTS") }
            )
        }

        Spacer(modifier = Modifier.height(rDp(6.dp)))

        // Category filter (only show for GGUF or All)
        if (selectedModelType == null || selectedModelType == ModelType.GGUF) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = rDp(16.dp)),
                horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { viewModel.filterByCategory(null) },
                    label = { Text("All") }
                )
                ModelCategory.values().forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { viewModel.filterByCategory(category) },
                        label = { Text(category.displayName) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(rDp(6.dp)))
        }

        // Advanced filters toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = rDp(16.dp)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { showAdvancedFilters = !showAdvancedFilters }
            ) {
                Icon(
                    imageVector = if (showAdvancedFilters) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (showAdvancedFilters) "Hide" else "Show",
                    modifier = Modifier.size(rDp(20.dp))
                )
                Spacer(modifier = Modifier.width(rDp(4.dp)))
                Text("Advanced Filters")
                if (activeFilterCount > 0) {
                    Spacer(modifier = Modifier.width(rDp(4.dp)))
                    AssistChip(
                        onClick = {},
                        label = { Text(activeFilterCount.toString()) },
                        modifier = Modifier.height(rDp(24.dp))
                    )
                }
            }

            if (activeFilterCount > 0) {
                TextButton(onClick = { viewModel.clearAllFilters() }) {
                    Text("Clear All")
                }
            }
        }

        // Advanced filters section
        AnimatedVisibility(
            visible = showAdvancedFilters,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rDp(16.dp), vertical = rDp(8.dp)),
                verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
            ) {
                // Parameter count filter (GGUF only)
                if (selectedModelType == null || selectedModelType == ModelType.GGUF) {
                    Column(verticalArrangement = Arrangement.spacedBy(rDp(6.dp))) {
                        Text(
                            text = "Parameters",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
                        ) {
                            listOf("0.5B", "1B", "3B", "6.7B", "8B", "32B", "70B").forEach { param ->
                                FilterChip(
                                    selected = param in selectedParameters,
                                    onClick = { viewModel.toggleParameterFilter(param) },
                                    label = { Text(param) }
                                )
                            }
                        }
                    }

                    // Quantization filter (GGUF only)
                    Column(verticalArrangement = Arrangement.spacedBy(rDp(6.dp))) {
                        Text(
                            text = "Quantization",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
                        ) {
                            listOf("Q4_0", "Q5_0", "Q8_0", "Q4_K_M", "Q5_K_M", "Q6_K").forEach { quant ->
                                FilterChip(
                                    selected = quant in selectedQuantizations,
                                    onClick = { viewModel.toggleQuantizationFilter(quant) },
                                    label = { Text(quant) }
                                )
                            }
                        }
                    }
                }

                // Size category filter
                Column(verticalArrangement = Arrangement.spacedBy(rDp(6.dp))) {
                    Text(
                        text = "Size",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
                    ) {
                        SizeCategory.entries.forEach { size ->
                            FilterChip(
                                selected = selectedSizeCategory == size,
                                onClick = {
                                    viewModel.filterBySizeCategory(
                                        if (selectedSizeCategory == size) null else size
                                    )
                                },
                                label = { Text(size.displayName) }
                            )
                        }
                    }
                }

                // Sort option
                Column(verticalArrangement = Arrangement.spacedBy(rDp(6.dp))) {
                    Text(
                        text = "Sort By",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
                    ) {
                        FilterChip(
                            selected = sortBy == SortOption.NAME,
                            onClick = { viewModel.setSortOption(SortOption.NAME) },
                            label = { Text("Name") }
                        )
                        FilterChip(
                            selected = sortBy == SortOption.SIZE,
                            onClick = { viewModel.setSortOption(SortOption.SIZE) },
                            label = { Text("Size") }
                        )
                        FilterChip(
                            selected = sortBy == SortOption.RECENTLY_ADDED,
                            onClick = { viewModel.setSortOption(SortOption.RECENTLY_ADDED) },
                            label = { Text("Recently Added") }
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = rDp(8.dp)),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    }
}

@Composable
private fun ModelTypeBadge(modelType: ModelType) {
    val (label, color) = when (modelType) {
        ModelType.GGUF -> "LLM" to MaterialTheme.colorScheme.primary
        ModelType.SD -> "Image" to MaterialTheme.colorScheme.tertiary
        ModelType.TTS -> "TTS" to MaterialTheme.colorScheme.secondary
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(rDp(4.dp)))
            .padding(horizontal = rDp(6.dp), vertical = rDp(2.dp))
    )
}

@Composable
fun ModelCard(
    model: HuggingFaceModel,
    isInstalled: Boolean,
    downloadState: ModelDownloadService.DownloadState?,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit
) {
    val isDownloading = remember(downloadState) {
        downloadState is ModelDownloadService.DownloadState.Downloading
    }
    val isExtracting = remember(downloadState) {
        downloadState is ModelDownloadService.DownloadState.Extracting
    }
    val isProcessing = remember(downloadState) {
        downloadState is ModelDownloadService.DownloadState.Processing
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = rDp(1.dp)),
        shape = RoundedCornerShape(rDp(10.dp))
    ) {
        Column(
            modifier = Modifier.padding(rDp(12.dp))
        ) {
            // Top: Type badge + Name + Action button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(rDp(6.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ModelTypeBadge(model.modelType)
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                when {
                    isInstalled -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Installed",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(rDp(24.dp))
                        )
                    }

                    isDownloading || isExtracting || isProcessing -> {
                        ActionProgressButton(
                            onClickListener = onCancelDownload,
                            icon = Icons.Default.Stop,
                            contentDescription = "Cancel Download"
                        )
                    }

                    else -> {
                        ActionButton(
                            onClickListener = onDownload,
                            icon = Icons.Default.Download,
                            contentDescription = "Download Model"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(rDp(4.dp)))

            // Size + repo source + key tags in a compact row
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(rDp(6.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Size chip
                Text(
                    text = model.approximateSize,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            RoundedCornerShape(rDp(4.dp))
                        )
                        .padding(horizontal = rDp(6.dp), vertical = rDp(2.dp))
                )

                // Repo source
                if (model.repositoryUrl.isNotEmpty()) {
                    val repoName = model.repositoryUrl.substringBefore("/")
                    Text(
                        text = repoName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(rDp(4.dp))
                            )
                            .padding(horizontal = rDp(6.dp), vertical = rDp(2.dp))
                    )
                }

                // Key tags (max 2)
                model.tags.take(2).forEach { tag ->
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(rDp(4.dp))
                            )
                            .padding(horizontal = rDp(5.dp), vertical = rDp(2.dp))
                    )
                }
            }

            // Download progress (animated)
            AnimatedVisibility(
                visible = isDownloading || isExtracting || isProcessing, enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) + fadeIn(), exit = shrinkVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = rDp(8.dp))) {
                    val progress =
                        if (downloadState is ModelDownloadService.DownloadState.Downloading) {
                            downloadState.progress
                        } else 0f

                    val statusText = when {
                        isProcessing -> "Processing..."
                        isExtracting -> "Extracting..."
                        isDownloading -> {
                            val ds = downloadState as ModelDownloadService.DownloadState.Downloading
                            val downloadedMB = ds.downloadedBytes / 1_000_000
                            val totalMB = ds.totalBytes / 1_000_000
                            val pct = (progress * 100).toInt()
                            val speedText = if (ds.speedBytesPerSec > 0) {
                                val speedMB = ds.speedBytesPerSec / 1_000_000.0
                                " · %.1f MB/s".format(speedMB)
                            } else ""
                            val etaText = if (ds.etaSeconds > 0) {
                                val mins = ds.etaSeconds / 60
                                val secs = ds.etaSeconds % 60
                                if (mins > 0) " · ${mins}m ${secs}s left"
                                else " · ${secs}s left"
                            } else ""
                            "${downloadedMB}/${totalMB}MB ($pct%)$speedText$etaText"
                        }
                        else -> ""
                    }

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(rDp(4.dp)))

                    if (isExtracting || isProcessing) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(rDp(4.dp))
                                .clip(RoundedCornerShape(rDp(2.dp))),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(rDp(4.dp))
                                .clip(RoundedCornerShape(rDp(2.dp))),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }
    }
}