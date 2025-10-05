package com.dark.neuroverse.ui.screens

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.twotone.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.ai_module.data.ModelsList.getModelList
import com.dark.ai_module.model.ModelData
import com.dark.ai_module.model.ModelProvider
import com.dark.neuroverse.activity.GgufPickerActivity
import com.dark.neuroverse.model.DownloadState
import com.dark.neuroverse.ui.components.CollapsableButton
import com.dark.neuroverse.ui.components.StandardBottomBar
import com.dark.neuroverse.ui.theme.*
import com.dark.neuroverse.viewModel.ModelScreenViewModel

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(onNext: () -> Unit) {
    val context = LocalContext.current
    val viewModel: ModelScreenViewModel = viewModel()

    val installedModels by viewModel.models.collectAsState()
    val openRouterModels by viewModel.openRouterModels.collectAsState()

    // Enable finish button if ANY model is configured (GGUF or OpenRouter)
    val isEnabled by remember {
        derivedStateOf {
            installedModels.isNotEmpty() || openRouterModels.isNotEmpty()
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("GGUF", "OpenRouter", "Installed")

    Scaffold { innerPadding ->
        Column(
            Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = rDP(24.dp), bottom = rDP(12.dp))
                    .padding(horizontal = rDP(26.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.SmartToy,
                    modifier = Modifier.size(rDP(30.dp)),
                    contentDescription = null
                )
                Spacer(Modifier.width(rDP(12.dp)))
                Text(
                    "Models",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                        fontSize = rSp(28.sp)
                    )
                )

                Spacer(Modifier.weight(1f))

                Button(onClick = {
                    context.startActivity(Intent(context, GgufPickerActivity::class.java))
                }) {
                    Icon(
                        Icons.TwoTone.FileOpen,
                        modifier = Modifier.size(rDP(18.dp)),
                        contentDescription = null
                    )
                    Spacer(Modifier.width(rDP(8.dp)))
                    Text("Import", fontSize = rSp(15.sp))
                }
            }

            // Tabs
            SecondaryTabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                label,
                                fontSize = rSp(14.sp),
                                maxLines = 1
                            )
                        }
                    )
                }
            }

            // Content
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                modifier = Modifier.weight(1f)
            ) { tab ->
                when (tab) {
                    0 -> MarketplaceList(viewModel)
                    1 -> OpenRouterTab(viewModel)
                    else -> InstalledList(viewModel)
                }
            }

            StandardBottomBar(Modifier.padding(bottom = rDP(14.dp))) {
                CollapsableButton(
                    text = "Finish",
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    enabled = isEnabled
                ) { onNext() }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// GGUF MARKETPLACE TAB
// ═══════════════════════════════════════════════════════════════════════════
@Composable
private fun MarketplaceList(viewModel: ModelScreenViewModel) {
    val context = LocalContext.current
    val models = remember { getModelList(context) }
    val downloadStates by viewModel.downloadStates.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = rDP(8.dp))
    ) {
        items(models) { modelData ->
            val state = downloadStates[modelData.modelUrl.toString()] ?: DownloadState()
            ModelCard(
                modelsData = modelData,
                isDownloading = state.isDownloading,
                progress = state.progress,
                onDownloadComplete = state.isComplete,
                viewModel = viewModel,
                onDownload = { viewModel.startDownload(modelData, context) }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// OPENROUTER TAB - Complete Redesign
// ═══════════════════════════════════════════════════════════════════════════
@Composable
private fun OpenRouterTab(viewModel: ModelScreenViewModel) {
    val context = LocalContext.current
    val openRouterApiKey by viewModel.openRouterApiKey.collectAsState()
    val openRouterBaseUrl by viewModel.openRouterBaseUrl.collectAsState()
    val openRouterModels by viewModel.openRouterModels.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()

    var showModelPicker by remember { mutableStateOf(false) }
    var isLoadingModels by remember { mutableStateOf(false) }

    // Auto-load on init
    LaunchedEffect(Unit) {
        viewModel.initOpenRouter(context)
        if (openRouterApiKey.isNotBlank()) {
            isLoadingModels = true
            viewModel.fetchAvailableModels()
            isLoadingModels = false
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = rDP(16.dp), vertical = rDP(12.dp)),
        verticalArrangement = Arrangement.spacedBy(rDP(12.dp))
    ) {
        // API Configuration Card
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(rDP(16.dp)),
                    verticalArrangement = Arrangement.spacedBy(rDP(12.dp))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.TwoTone.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(rDP(8.dp)))
                        Text(
                            "API Configuration",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    HorizontalDivider()

                    OutlinedTextField(
                        value = openRouterApiKey,
                        onValueChange = {
                            viewModel.saveOpenRouterApiKey(context, it)
                        },
                        label = { Text("API Key") },
                        placeholder = { Text("sk-or-v1-...") },
                        leadingIcon = {
                            Icon(Icons.TwoTone.Key, contentDescription = null)
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = openRouterBaseUrl,
                        onValueChange = {
                            viewModel.saveOpenRouterBaseUrl(context, it)
                        },
                        label = { Text("Base URL") },
                        placeholder = { Text("https://openrouter.ai/api/v1") },
                        leadingIcon = {
                            Icon(Icons.TwoTone.Link, contentDescription = null)
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (openRouterApiKey.isNotBlank()) {
                        Button(
                            onClick = {
                                isLoadingModels = true
                                viewModel.fetchAvailableModels()
                                isLoadingModels = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoadingModels
                        ) {
                            if (isLoadingModels) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(rDP(18.dp)),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(rDP(8.dp)))
                            }
                            Text(if (isLoadingModels) "Fetching..." else "Fetch Available Models")
                        }
                    }
                }
            }
        }

        // Selected Models Card
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(rDP(16.dp)),
                    verticalArrangement = Arrangement.spacedBy(rDP(12.dp))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.TwoTone.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(rDP(8.dp)))
                            Text(
                                "Selected Models",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (openRouterModels.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(rDP(12.dp)),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    "${openRouterModels.size}",
                                    modifier = Modifier.padding(
                                        horizontal = rDP(10.dp),
                                        vertical = rDP(4.dp)
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    if (openRouterModels.isEmpty()) {
                        EmptyStateCard(
                            icon = Icons.TwoTone.CloudOff,
                            title = "No models selected",
                            subtitle = "Add models from the available list below"
                        )
                    } else {
                        openRouterModels.forEach { modelId ->
                            OpenRouterModelItem(
                                modelId = modelId,
                                onDelete = { viewModel.removeOpenRouterModel(modelId) }
                            )
                        }
                    }

                    Button(
                        onClick = { showModelPicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = availableModels.isNotEmpty()
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(rDP(8.dp)))
                        Text("Add Model")
                    }
                }
            }
        }
    }

    // Model Picker Dialog
    if (showModelPicker) {
        ModelPickerDialog(
            availableModels = availableModels,
            selectedModels = openRouterModels,
            onDismiss = { showModelPicker = false },
            onModelSelected = { modelId ->
                viewModel.addOpenRouterModel(modelId)
                // Save to Room DB
                viewModel.addModel(
                    ModelData(
                        modelName = modelId,
                        providerName = ModelProvider.OpenRouter.toString(),
                        modelPath = modelId, // For OpenRouter, path = model ID
                        ctxSize = 0, // Will be determined by API
                        isImported = false,
                        isToolCalling = true // Most OpenRouter models support tools
                    )
                )
                showModelPicker = false
            }
        )
    }
}

@Composable
private fun OpenRouterModelItem(
    modelId: String,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(rDP(8.dp)),
        color = MaterialTheme.colorScheme.primary.copy(0.1f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDP(12.dp)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.TwoTone.Cloud,
                    contentDescription = null,
                    tint = SkyBlue,
                    modifier = Modifier.size(rDP(20.dp))
                )
                Spacer(Modifier.width(rDP(10.dp)))
                Text(
                    modelId,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onDelete,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                )
            ) {
                Icon(
                    Icons.TwoTone.Delete,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(rDP(18.dp))
                )
            }
        }
    }
}

@Composable
private fun ModelPickerDialog(
    availableModels: List<String>,
    selectedModels: List<String>,
    onDismiss: () -> Unit,
    onModelSelected: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredModels = remember(searchQuery, availableModels, selectedModels) {
        availableModels
            .filter { it.contains(searchQuery, ignoreCase = true) }
            .filter { it !in selectedModels } // Hide already selected
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Select Model",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = rDP(500.dp)),
                verticalArrangement = Arrangement.spacedBy(rDP(8.dp))
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search models...") },
                    leadingIcon = {
                        Icon(Icons.TwoTone.Search, contentDescription = null)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider()

                if (filteredModels.isEmpty()) {
                    EmptyStateCard(
                        icon = Icons.TwoTone.SearchOff,
                        title = "No models found",
                        subtitle = if (searchQuery.isBlank()) "Try fetching models first" else "Try a different search"
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(rDP(4.dp))
                    ) {
                        items(filteredModels) { modelId ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onModelSelected(modelId) },
                                shape = RoundedCornerShape(rDP(8.dp)),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(rDP(12.dp)),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        modelId,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Icon(
                                        Icons.Filled.Add,
                                        contentDescription = "Add",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun EmptyStateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(rDP(24.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(rDP(8.dp))
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(rDP(48.dp)),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// INSTALLED MODELS TAB
// ═══════════════════════════════════════════════════════════════════════════
@Composable
private fun InstalledList(viewModel: ModelScreenViewModel) {
    val installed by viewModel.models.collectAsState()

    if (installed.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            EmptyStateCard(
                icon = Icons.TwoTone.Inventory,
                title = "No models installed",
                subtitle = "Download from marketplace or import a GGUF file"
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = rDP(8.dp))
        ) {
            items(installed, key = { it.id }) { model ->
                InstalledModelCard(
                    model = model,
                    onDelete = { viewModel.removeModel(model.modelName) },
                    onInfo = {}
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// CARDS
// ═══════════════════════════════════════════════════════════════════════════
@Composable
private fun InstalledModelCard(
    model: ModelData,
    onDelete: () -> Unit,
    onInfo: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = rDP(16.dp), vertical = rDP(6.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(rDP(14.dp)),
            verticalArrangement = Arrangement.spacedBy(rDP(10.dp))
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        model.modelName,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = rSp(18.sp)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(rDP(4.dp)))
                    Pill(
                        text = if (model.providerName == ModelProvider.LocalGGUF.toString()) "Local" else "OpenRouter",
                        isRemote = model.providerName != ModelProvider.LocalGGUF.toString()
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(rDP(8.dp))) {
                    IconButton(
                        onClick = onInfo,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Icon(
                            Icons.TwoTone.Info,
                            contentDescription = "Info",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Icon(
                            Icons.TwoTone.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

// com.dark.neuroverse.ui.screens.ModelsScreen.kt

@Composable
fun ModelCard(
    modelsData: ModelData,
    isDownloading: Boolean = false,
    onDownloadComplete: Boolean = false,
    progress: Float = 0f,
    viewModel: ModelScreenViewModel,
    onDownload: () -> Unit = {}
) {
    // One‑shot query for “is this model on disk?”
    var isInstalled by remember { mutableStateOf(false) }

    LaunchedEffect(modelsData.modelName) {
        viewModel.checkIfInstalled(modelsData.modelName) { isInstalled = it }
    }

    // Cease the flag once a download finishes
    LaunchedEffect(onDownloadComplete) {
        if (onDownloadComplete) isInstalled = true
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = rDP(16.dp), vertical = rDP(6.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDP(14.dp)),
            verticalArrangement = Arrangement.spacedBy(rDP(12.dp))
        ) {
            Text(
                modelsData.modelName,
                style = MaterialTheme.typography.titleLarge
                    .copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            SpecGrid(
                "Context" to modelsData.ctxSize.toString(),
                "Tools" to if (modelsData.isToolCalling) "YES" else "NO"
            )

            // Progress bar – visible only while downloading
            AnimatedVisibility(visible = isDownloading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    progress = { progress }
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(rDP(8.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        when {
                            !isInstalled && isDownloading -> {
                                viewModel.cancelDownload(
                                    modelsData.modelName,
                                    modelsData.modelUrl.toString()   // ← key
                                )
                            }

                            !isInstalled && !isDownloading -> onDownload()
                            // otherwise – do nothing; button is disabled
                        }
                    },
                    colors = if (!isInstalled) {
                        ButtonDefaults.buttonColors()
                    } else {
                        ButtonDefaults.buttonColors(
                            containerColor = Success.copy(alpha = 0.2f),
                            contentColor = Success
                        )
                    }
                ) {
                    AnimatedContent(
                        targetState = isInstalled,
                        transitionSpec = { fadeIn() togetherWith fadeOut() }
                    ) { installed ->
                        if (installed) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                        } else {
                            AnimatedContent(
                                targetState = isDownloading,
                                transitionSpec = { fadeIn() togetherWith fadeOut() }
                            ) { downloading ->
                                if (downloading) Icon(Icons.Filled.Stop, contentDescription = null)
                                else Icon(Icons.Filled.ArrowCircleDown, contentDescription = null)
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = isInstalled) {
                    IconButton(
                        onClick = {
                            viewModel.removeModel(modelsData.modelName)
                            isInstalled = false      // immediately reflect the change
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Icon(Icons.TwoTone.Delete, contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// UI HELPERS
// ═══════════════════════════════════════════════════════════════════════════
@Composable
private fun Pill(text: String, isRemote: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(rDP(12.dp)),
        color = if (!isRemote) Mint.copy(alpha = 0.2f) else SkyBlue.copy(alpha = 0.2f)
    ) {
        Text(
            text,
            modifier = Modifier.padding(
                horizontal = rDP(10.dp),
                vertical = rDP(4.dp)
            ),
            style = MaterialTheme.typography.labelMedium.copy(
                color = if (!isRemote) Mint else SkyBlue,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
private fun SpecGrid(vararg pairs: Pair<String, String>) {
    Column(verticalArrangement = Arrangement.spacedBy(rDP(6.dp))) {
        pairs.forEach { (k, v) -> SpecRow(k, v) }
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.primary
            )
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}