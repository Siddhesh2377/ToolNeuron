package com.dark.tool_neuron.ui.screen.model_store

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import com.dark.tool_neuron.ui.components.ExpandCollapseIcon
import com.dark.tool_neuron.ui.theme.Motion
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
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
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.dark.tool_neuron.global.formatBytes
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
import com.dark.tool_neuron.utils.SizeCategory
import com.dark.tool_neuron.viewmodel.ModelStoreViewModel
import com.dark.tool_neuron.viewmodel.RepoGroupInfo
import com.dark.tool_neuron.viewmodel.SortOption
import java.io.File
import java.util.Locale
import com.dark.tool_neuron.ui.icons.TnIcons

enum class StoreTab {
    MODELS, INSTALLED, SETTINGS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelStoreScreen(
    onNavigateBack: () -> Unit, viewModel: ModelStoreViewModel = viewModel()
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val models by viewModel.filteredModels.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val downloadStates by viewModel.downloadStates.collectAsStateWithLifecycle()
    val installedModels by viewModel.installedModels.collectAsStateWithLifecycle()
    val deviceInfo by viewModel.deviceInfo.collectAsStateWithLifecycle()
    val deleteInProgress by viewModel.deleteInProgress.collectAsStateWithLifecycle()

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
                        icon = TnIcons.ArrowLeft,
                        contentDescription = "Back"
                    )
                }, actions = {
                    if (selectedTab == StoreTab.MODELS) {
                        ActionButton(
                            onClickListener = { viewModel.refreshModels() },
                            icon = TnIcons.Refresh,
                            contentDescription = "Refresh"
                        )
                        ActionButton(
                            onClickListener = { showSearch = true },
                            icon = TnIcons.Search,
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
                    fadeIn(Motion.state()) togetherWith fadeOut(Motion.state())
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
                        onDelete = { viewModel.deleteModel(it) },
                        viewModel = viewModel
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
    val selectedRepo by viewModel.selectedRepository.collectAsStateWithLifecycle()

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
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = TnIcons.AlertTriangle,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
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
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = TnIcons.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
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
                AnimatedContent(
                    targetState = selectedRepo,
                    transitionSpec = {
                        fadeIn(Motion.state()) togetherWith
                                fadeOut(Motion.state())
                    },
                    label = "repo_nav"
                ) { repoKey ->
                    if (repoKey == null) {
                        // Repo card list view
                        RepoCardListView(
                            viewModel = viewModel,
                            isLoading = isLoading,
                            downloadStates = downloadStates
                        )
                    } else {
                        // Model detail view inside a repo
                        RepoDetailView(
                            repoKey = repoKey,
                            viewModel = viewModel,
                            isLoading = isLoading,
                            downloadStates = downloadStates,
                            installedModelNames = installedModelNames,
                            onDownload = onDownload,
                            onCancelDownload = onCancelDownload
                        )
                    }
                }
            }
        }
    }

    // Handle back press to return from detail to repo list
    if (selectedRepo != null) {
        androidx.activity.compose.BackHandler {
            viewModel.selectRepository(null)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RepoCardListView(
    viewModel: ModelStoreViewModel,
    isLoading: Boolean,
    downloadStates: Map<String, ModelDownloadService.DownloadState>
) {
    val groupedRepos = remember(viewModel.filteredModels.collectAsStateWithLifecycle().value) {
        viewModel.getGroupedRepos()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(if (isLoading) Modifier.blur(4.dp) else Modifier),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            flingBehavior = ScrollableDefaults.flingBehavior()
        ) {
            items(
                items = groupedRepos.entries.toList(),
                key = { it.key }
            ) { (repoKey, info) ->
                val repoModels = remember(groupedRepos, repoKey) { viewModel.getModelsForRepo(repoKey) }
                val hasActiveDownload = repoModels.any { model ->
                    val state = downloadStates[model.id]
                    state is ModelDownloadService.DownloadState.Downloading ||
                            state is ModelDownloadService.DownloadState.Extracting ||
                            state is ModelDownloadService.DownloadState.Processing
                }

                StoreRepoCard(
                    info = info,
                    hasActiveDownload = hasActiveDownload,
                    onClick = { viewModel.selectRepository(repoKey) }
                )
            }
        }

        if (isLoading) {
            LoadingIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StoreRepoCard(
    info: RepoGroupInfo,
    hasActiveDownload: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(Standards.CardSmallCornerRadius),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(Standards.CardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
        ) {
            ModelTypeBadge(info.modelType)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = info.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (info.author.isNotEmpty()) {
                        CaptionText(text = info.author)
                        CaptionText(text = "·")
                    }
                    CaptionText(
                        text = "${info.modelCount} ${if (info.modelCount == 1) "model" else "models"}"
                    )
                    if (hasActiveDownload) {
                        CaptionText(text = "·")
                        LoadingIndicator(
                            modifier = Modifier.size(10.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Icon(
                imageVector = TnIcons.ArrowRight,
                contentDescription = "View models",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RepoDetailView(
    repoKey: String,
    viewModel: ModelStoreViewModel,
    isLoading: Boolean,
    downloadStates: Map<String, ModelDownloadService.DownloadState>,
    installedModelNames: Set<String>,
    onDownload: (HuggingFaceModel) -> Unit,
    onCancelDownload: (String) -> Unit
) {
    val repoModels = remember(viewModel.filteredModels.collectAsStateWithLifecycle().value, repoKey) {
        viewModel.getModelsForRepo(repoKey)
    }
    val groupedRepos = remember(viewModel.filteredModels.collectAsStateWithLifecycle().value) {
        viewModel.getGroupedRepos()
    }
    val repoInfo = groupedRepos[repoKey]

    Column(modifier = Modifier.fillMaxSize()) {
        // Back header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ActionButton(
                onClickListener = { viewModel.selectRepository(null) },
                icon = TnIcons.ArrowLeft,
                contentDescription = "Back to repos"
            )
            repoInfo?.let { info ->
                ModelTypeBadge(info.modelType)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = info.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (info.author.isNotEmpty()) {
                        CaptionText(text = info.author)
                    }
                }
                CaptionText(text = "${info.modelCount} models")
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (isLoading) Modifier.blur(4.dp) else Modifier),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                flingBehavior = ScrollableDefaults.flingBehavior()
            ) {
                items(
                    items = repoModels,
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

            if (isLoading) {
                LoadingIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun InstalledModelsTab(
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
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
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
                val modelFile = File(model.modelPath)
                val sizeText = if (modelFile.exists()) {
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

@Composable
private fun ModelDetailsDialog(
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                val typeLabel = when (model.providerType) {
                    ProviderType.DIFFUSION -> "Stable Diffusion"
                    ProviderType.GGUF -> "GGUF (LLM)"
                    ProviderType.TTS -> "Text-to-Speech"
                }
                DetailRow("Type", typeLabel)
                DetailRow("Status", if (model.isActive) "Active" else "Inactive")

                val modelFile = File(model.modelPath)
                if (modelFile.exists()) {
                    if (modelFile.isDirectory) {
                        DetailRow("Storage", "Folder")
                        val folderSize = modelFile.walkTopDown().sumOf { it.length() }
                        DetailRow("Size", formatBytes(folderSize))
                        val fileCount = modelFile.walkTopDown().count { it.isFile }
                        DetailRow("Files", "$fileCount")
                    } else {
                        DetailRow("Storage", "File")
                        DetailRow("Size", formatBytes(modelFile.length()))
                    }
                }

                DetailRow("Path", model.modelPath)

                if (configLoaded && config != null) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
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

                            Spacer(modifier = Modifier.height(4.dp))
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
                                Spacer(modifier = Modifier.height(4.dp))
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
        contentPadding = PaddingValues(horizontal = Standards.SpacingLg, vertical = Standards.SpacingSm),
        verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
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
                        icon = TnIcons.Plus,
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
        icon = TnIcons.Prompt,
        trailing = {
            if (remainingEntries.isNotEmpty()) {
                ActionButton(
                    onClickListener = { expanded = !expanded },
                    icon = if (expanded) TnIcons.ChevronUp else TnIcons.ChevronDown,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Standards.SpacingXs)) {
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
                Column(verticalArrangement = Arrangement.spacedBy(Standards.SpacingXs)) {
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(Standards.CardSmallCornerRadius),
        onClick = onValidate
    ) {
        Column(
            modifier = Modifier.padding(Standards.CardPadding),
            verticalArrangement = Arrangement.spacedBy(Standards.SpacingXs)
        ) {
            // Row 1: validation dot + name + actions + switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
            ) {
                // Validation status dot
                val dotColor = when (validationResult) {
                    is ValidationResult.Valid -> MaterialTheme.colorScheme.primary
                    is ValidationResult.Invalid -> MaterialTheme.colorScheme.error
                    is ValidationResult.Checking -> MaterialTheme.colorScheme.tertiary
                    null -> MaterialTheme.colorScheme.outlineVariant
                }
                if (validationResult is ValidationResult.Checking) {
                    LoadingIndicator(
                        modifier = Modifier.size(10.dp),
                        color = dotColor
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
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
                            icon = ActionIcon.Vector(TnIcons.Edit),
                            onClick = onEdit,
                            contentDescription = "Edit"
                        ),
                        ActionItem(
                            icon = ActionIcon.Vector(TnIcons.Trash),
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
                horizontalArrangement = Arrangement.spacedBy(Standards.SpacingXs),
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
                        text = "${validationResult.ggufFileCount} ${validationResult.label}",
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
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.scrollable(rememberScrollState(), orientation = Orientation.Horizontal)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
            Icon(TnIcons.ArrowLeft, "Close search")
        }
    })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelFiltersSection(
    viewModel: ModelStoreViewModel
) {
    val selectedModelType by viewModel.selectedModelType.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val selectedParameters by viewModel.selectedParameters.collectAsStateWithLifecycle()
    val selectedQuantizations by viewModel.selectedQuantizations.collectAsStateWithLifecycle()
    val selectedSizeCategory by viewModel.selectedSizeCategory.collectAsStateWithLifecycle()
    val selectedTags by viewModel.selectedTags.collectAsStateWithLifecycle()
    val showNsfw by viewModel.showNsfw.collectAsStateWithLifecycle()
    val executionTarget by viewModel.executionTarget.collectAsStateWithLifecycle()
    val sortBy by viewModel.sortBy.collectAsStateWithLifecycle()

    var showAdvancedFilters by remember { mutableStateOf(false) }

    val activeFilterCount = listOf(
        selectedModelType != null,
        selectedCategory != null,
        selectedParameters.isNotEmpty(),
        selectedQuantizations.isNotEmpty(),
        selectedSizeCategory != null,
        selectedTags.isNotEmpty(),
        !showNsfw,
        executionTarget != null
    ).count { it }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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

        Spacer(modifier = Modifier.height(6.dp))

        if (selectedModelType == null || selectedModelType == ModelType.GGUF) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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

            Spacer(modifier = Modifier.height(6.dp))
        }

        // Tag chips
        val availableTags = remember(viewModel.models.collectAsStateWithLifecycle().value) {
            viewModel.getAvailableTags()
        }
        if (availableTags.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableTags.forEach { tag ->
                    FilterChip(
                        selected = tag in selectedTags,
                        onClick = { viewModel.toggleTagFilter(tag) },
                        label = { Text(tag) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { showAdvancedFilters = !showAdvancedFilters }
            ) {
                ExpandCollapseIcon(isExpanded = showAdvancedFilters, size = 20.dp)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Advanced Filters")
                if (activeFilterCount > 0) {
                    Spacer(modifier = Modifier.width(4.dp))
                    AssistChip(
                        onClick = {},
                        label = { Text(activeFilterCount.toString()) },
                        modifier = Modifier.height(24.dp)
                    )
                }
            }

            if (activeFilterCount > 0) {
                TextButton(onClick = { viewModel.clearAllFilters() }) {
                    Text("Clear All")
                }
            }
        }

        AnimatedVisibility(
            visible = showAdvancedFilters,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // NSFW toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Show NSFW Content",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    ActionSwitch(
                        checked = showNsfw,
                        onCheckedChange = { viewModel.setShowNsfw(it) }
                    )
                }

                // Execution target filter
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Execution",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = executionTarget == null,
                            onClick = { viewModel.setExecutionTarget(null) },
                            label = { Text("All") }
                        )
                        FilterChip(
                            selected = executionTarget == "CPU",
                            onClick = { viewModel.setExecutionTarget(if (executionTarget == "CPU") null else "CPU") },
                            label = { Text("CPU") }
                        )
                        FilterChip(
                            selected = executionTarget == "NPU",
                            onClick = { viewModel.setExecutionTarget(if (executionTarget == "NPU") null else "NPU") },
                            label = { Text("NPU") }
                        )
                    }
                }

                if (selectedModelType == null || selectedModelType == ModelType.GGUF) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Parameters",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
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

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Quantization",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
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

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Size",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Sort By",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
            modifier = Modifier.padding(top = 8.dp),
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
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
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
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Top: Type badge + Name + Action button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                            imageVector = TnIcons.CircleCheck,
                            contentDescription = "Installed",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    isDownloading || isExtracting || isProcessing -> {
                        ActionProgressButton(
                            onClickListener = onCancelDownload,
                            icon = TnIcons.PlayerStop,
                            contentDescription = "Cancel Download"
                        )
                    }

                    else -> {
                        ActionButton(
                            onClickListener = onDownload,
                            icon = TnIcons.Download,
                            contentDescription = "Download Model"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Size + repo source + key tags in a compact row
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
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
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
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
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }

            // Download progress (animated)
            AnimatedVisibility(
                visible = isDownloading || isExtracting || isProcessing,
                enter = Motion.Enter,
                exit = Motion.Exit
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    val progress =
                        if (downloadState is ModelDownloadService.DownloadState.Downloading) {
                            downloadState.progress
                        } else 0f

                    val statusText = when {
                        isProcessing -> "Processing..."
                        isExtracting -> {
                            val es = downloadState as ModelDownloadService.DownloadState.Extracting
                            if (es.currentFile.isNotEmpty()) {
                                "Unzipping ${es.currentFile} (${es.extractedCount + 1}/${es.totalFiles})"
                            } else {
                                "Extracting..."
                            }
                        }
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

                    Spacer(modifier = Modifier.height(4.dp))

                    if (isExtracting) {
                        val es = downloadState as ModelDownloadService.DownloadState.Extracting
                        if (es.totalFiles > 0) {
                            LinearProgressIndicator(
                                progress = { es.extractedCount.toFloat() / es.totalFiles },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    } else if (isProcessing) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }
    }
}