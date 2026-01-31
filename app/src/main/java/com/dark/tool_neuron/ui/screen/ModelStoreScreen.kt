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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
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
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.ActionProgressButton
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
                        icon = Icons.Default.ArrowBack,
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
            TabRow(
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
                        installedModels = installedModels.map { it.modelName }.toSet(),
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
    installedModels: Set<String>,
    viewModel: ModelStoreViewModel,
    onDownload: (HuggingFaceModel) -> Unit,
    onCancelDownload: (String) -> Unit,
    onRetry: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ModelFiltersSection(viewModel = viewModel)

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            }

            error != null -> {
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
                            modifier = Modifier.size(rDp(64.dp)),
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
                        verticalArrangement = Arrangement.spacedBy(rDp(16.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(rDp(64.dp)),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "No models found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            else -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (isLoading) Modifier.blur(rDp(6.dp)) else Modifier),
                        contentPadding = PaddingValues(rDp(16.dp)),
                        verticalArrangement = Arrangement.spacedBy(rDp(12.dp)),
                        flingBehavior = ScrollableDefaults.flingBehavior()
                    ) {
                        items(
                            items = models, key = { model -> model.id }) { model ->
                            ModelCard(
                                model = model,
                                isInstalled = installedModels.contains(model.name),
                                downloadState = downloadStates[model.id],
                                onDownload = { onDownload(model) },
                                onCancelDownload = { onCancelDownload(model.id) }
                            )
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
            contentPadding = PaddingValues(rDp(16.dp)),
            verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InstalledModelCard(
    model: Model,
    isDeleting: Boolean,
    onShowDetails: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = rDp(2.dp)),
        shape = RoundedCornerShape(rDp(12.dp))
    ) {
        Column(
            modifier = Modifier.padding(rDp(16.dp))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.modelName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(rDp(4.dp)))
                    Text(
                        text = model.providerType.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
                ) {
                    IconButton(
                        onClick = onShowDetails,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Show Details"
                        )
                    }

                    if (isDeleting) {
                        Box(
                            modifier = Modifier.size(rDp(40.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            LoadingIndicator(
                                modifier = Modifier.size(rDp(24.dp))
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onDelete,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Model"
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(rDp(12.dp)))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(rDp(6.dp))
            ) {
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            text = if (model.isActive) "Active" else "Inactive",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(rDp(16.dp))
                        )
                    }
                )

                val modelFile = File(model.modelPath)
                if (modelFile.exists()) {
                    val sizeInMB = modelFile.length() / (1024 * 1024)
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                text = "${sizeInMB}MB",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = null,
                                modifier = Modifier.size(rDp(16.dp))
                            )
                        }
                    )
                }
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
                    DetailRow("Size", "${sizeInMB}MB")
                    DetailRow("Exists", "Yes")
                } else {
                    DetailRow("Exists", "No")
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
        contentPadding = PaddingValues(rDp(16.dp)),
        verticalArrangement = Arrangement.spacedBy(rDp(16.dp))
    ) {
        // Device Info Section
        item {
            DeviceInfoCard(deviceInfo)
        }

        // Repositories Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Model Repositories",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, "Add Repository")
                }
            }
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
    Card(
        modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ), shape = RoundedCornerShape(rDp(12.dp))
    ) {
        Column(
            modifier = Modifier.padding(rDp(16.dp)),
            verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
            ) {
                Icon(
                    painter = painterResource(R.drawable.prompt),
                    contentDescription = null,
                    modifier = Modifier.size(rDp(20.dp)),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Device Information",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            deviceInfo.forEach { (key, value) ->
                DeviceInfoRow(label = key.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(
                        Locale.ROOT
                    ) else it.toString()
                }, value = value)
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RepositoryCard(
    repository: HFModelRepository,
    validationResult: ValidationResult?,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onValidate: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(rDp(10.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDp(12.dp)),
            verticalArrangement = Arrangement.spacedBy(rDp(8.dp))
        ) {
            // Top row: Name, validation status, edit, toggle, delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = repository.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(rDp(4.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Validation status icon
                    when (validationResult) {
                        is ValidationResult.Valid -> {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Valid",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(rDp(18.dp))
                            )
                        }
                        is ValidationResult.Invalid -> {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = "Invalid",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(rDp(18.dp))
                            )
                        }
                        is ValidationResult.Checking -> {
                            LoadingIndicator(
                                modifier = Modifier.size(rDp(18.dp))
                            )
                        }
                        null -> {}
                    }

                    // Edit button
                    ActionButton(
                        onClickListener = onEdit,
                        icon = Icons.Default.Edit,
                        contentDescription = "Edit"
                    )

                    // Toggle switch
                    Switch(
                        checked = repository.isEnabled,
                        onCheckedChange = { onToggle() }
                    )

                    // Delete button
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Repository path
            Text(
                text = repository.repoPath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = maple
            )

            // Bottom row: Category chip, GGUF count, validate button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(rDp(8.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Category chip
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                text = repository.category.displayName,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.height(rDp(28.dp))
                    )

                    // GGUF file count if validated
                    if (validationResult is ValidationResult.Valid) {
                        Text(
                            text = "${validationResult.ggufFileCount} GGUF files",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (validationResult is ValidationResult.Invalid) {
                        Text(
                            text = validationResult.reason,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                // Validate button
                TextButton(
                    onClick = onValidate,
                    enabled = validationResult !is ValidationResult.Checking
                ) {
                    Text(
                        text = "Validate",
                        style = MaterialTheme.typography.labelSmall
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
            Icon(Icons.Default.ArrowBack, "Close search")
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
        elevation = CardDefaults.cardElevation(defaultElevation = rDp(2.dp)),
        shape = RoundedCornerShape(rDp(12.dp))
    ) {
        Column(
            modifier = Modifier.padding(rDp(16.dp))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(rDp(8.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ModelTypeBadge(model.modelType)
                        Text(
                            text = model.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    Spacer(modifier = Modifier.height(rDp(4.dp)))
                    Text(
                        text = model.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(rDp(12.dp)))

                when {
                    isInstalled -> {
                        ActionButton(
                            onClickListener = {},
                            icon = Icons.Default.CheckCircle,
                            contentDescription = "Installed",
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
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

            Spacer(modifier = Modifier.height(rDp(10.dp)))

            // Tags row with size chip
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(rDp(6.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(onClick = {}, label = {
                    Text(
                        text = model.approximateSize,
                        style = MaterialTheme.typography.labelSmall
                    )
                }, leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        modifier = Modifier.size(rDp(16.dp))
                    )
                })

                model.tags.take(3).forEach { tag ->
                    AssistChip(onClick = {}, label = {
                        Text(
                            text = tag, style = MaterialTheme.typography.labelSmall
                        )
                    })
                }
            }

            // Download progress
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
                Column(modifier = Modifier.padding(top = rDp(12.dp))) {
                    val progress =
                        if (downloadState is ModelDownloadService.DownloadState.Downloading) {
                            downloadState.progress
                        } else 0f

                    val statusText = when {
                        isProcessing -> "Processing model..."
                        isExtracting -> "Extracting files..."
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
                            "${downloadedMB}MB / ${totalMB}MB ($pct%)$speedText$etaText"
                        }
                        else -> ""
                    }

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(rDp(8.dp)))

                    when (isExtracting || isProcessing) {
                        true -> {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(rDp(6.dp))
                                    .clip(RoundedCornerShape(rDp(3.dp))),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }

                        false -> {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(rDp(6.dp))
                                    .clip(RoundedCornerShape(rDp(3.dp))),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}