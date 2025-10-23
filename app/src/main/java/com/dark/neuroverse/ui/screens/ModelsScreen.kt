package com.dark.neuroverse.ui.screens

import android.content.Intent
import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.twotone.CheckCircle
import androidx.compose.material.icons.twotone.Cloud
import androidx.compose.material.icons.twotone.CloudOff
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.FileOpen
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.Inventory
import androidx.compose.material.icons.twotone.Key
import androidx.compose.material.icons.twotone.Link
import androidx.compose.material.icons.twotone.Search
import androidx.compose.material.icons.twotone.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.ai_module.data.ModelsList.getModelList
import com.dark.ai_module.model.ModelData
import com.dark.ai_module.model.ModelProvider
import com.dark.ai_module.model.OpenRouterModel
import com.dark.ai_module.model.toModelData
import com.dark.neuroverse.R
import com.dark.neuroverse.activity.GgufPickerActivity
import com.dark.neuroverse.model.DownloadState
import com.dark.neuroverse.ui.components.CollapsableButton
import com.dark.neuroverse.ui.components.StandardBottomBar
import com.dark.neuroverse.ui.theme.Mint
import com.dark.neuroverse.ui.theme.SkyBlue
import com.dark.neuroverse.ui.theme.Success
import com.dark.neuroverse.ui.theme.rDP
import com.dark.neuroverse.ui.theme.rSp
import com.dark.neuroverse.viewModel.ModelScreenViewModel

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(onNext: () -> Unit) {
    val context = LocalContext.current
    val viewModel: ModelScreenViewModel = viewModel()

    val installedModels by viewModel.models.collectAsState()
    val openRouterInstalled by viewModel.openRouterInstalledModels.collectAsState()

    // Enable finish button if ANY model is configured (GGUF or OpenRouter)
    val isEnabled by remember {
        derivedStateOf {
            installedModels.isNotEmpty() || openRouterInstalled.isNotEmpty()
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
                    "Models", style = MaterialTheme.typography.headlineLarge.copy(
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
                selectedTabIndex = selectedTab, modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, label ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = {
                        Text(
                            label, fontSize = rSp(14.sp), maxLines = 1
                        )
                    })
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
        modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = rDP(8.dp))
    ) {
        items(models) { modelData ->
            val state = downloadStates[modelData.modelUrl.toString()] ?: DownloadState()
            ModelCard(
                modelsData = modelData,
                isDownloading = state.isDownloading,
                progress = state.progress,
                onDownloadComplete = state.isComplete,
                viewModel = viewModel,
                onDownload = { viewModel.startDownload(modelData, context) })
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
    val openRouterInstalled by viewModel.openRouterInstalledModels.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()

    var showModelPicker by remember { mutableStateOf(false) }
    var isLoadingModels by remember { mutableStateOf(false) }
    var showKey by remember { mutableStateOf(false) }

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
                    containerColor = MaterialTheme.colorScheme.secondary.copy(0.1f)
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
                        trailingIcon = {
                            Icon(
                                painterResource(if (showKey) R.drawable.show else R.drawable.hide),
                                "Show-Hide",
                                modifier = Modifier.clickable {
                                    showKey = !showKey
                                })
                        },
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
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
                            }, modifier = Modifier.fillMaxWidth(), enabled = !isLoadingModels
                        ) {
                            if (isLoadingModels) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(rDP(18.dp)), strokeWidth = 2.dp
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
                    containerColor = MaterialTheme.colorScheme.secondary.copy(0.1f)
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

                        if (openRouterInstalled.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(rDP(12.dp)),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    "${openRouterInstalled.size}",
                                    modifier = Modifier.padding(
                                        horizontal = rDP(10.dp), vertical = rDP(4.dp)
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    if (openRouterInstalled.isEmpty()) {
                        EmptyStateCard(
                            icon = Icons.TwoTone.CloudOff,
                            title = "No models selected",
                            subtitle = "Add models from the available list below"
                        )
                    } else {
                        openRouterInstalled.forEach { modelId ->
                            OpenRouterModelItem(
                                modelId = modelId,
                                onDelete = { viewModel.removeOpenRouterModel(modelId.id) })
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
            selectedModels = openRouterInstalled,
            onDismiss = { showModelPicker = false },
            onModelSelected = { routerModel ->
                viewModel.addOpenRouterModel(routerModel)
                // Save to Room DB
                viewModel.addModel(
                    routerModel.toModelData()
                )
                showModelPicker = false
            })
    }
}

@Composable
private fun OpenRouterModelItem(
    modelId: OpenRouterModel, onDelete: () -> Unit
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
                modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.TwoTone.Cloud,
                    contentDescription = null,
                    tint = SkyBlue,
                    modifier = Modifier.size(rDP(20.dp))
                )
                Spacer(Modifier.width(rDP(10.dp)))
                Text(
                    modelId.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onDelete, colors = IconButtonDefaults.iconButtonColors(
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
    availableModels: List<OpenRouterModel>,
    selectedModels: List<OpenRouterModel>,
    onDismiss: () -> Unit,
    onModelSelected: (OpenRouterModel) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredModels = remember(searchQuery, availableModels, selectedModels) {
        availableModels.filter { it.name.contains(searchQuery, ignoreCase = true) }
            .filter { it !in selectedModels } // Hide already selected
    }

    Log.d("ModelPickerDialog", "Filtered models: $filteredModels")

    AlertDialog(onDismissRequest = onDismiss, title = {
        Text(
            "Select Model",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }, text = {
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
                                .clickable {
                                    onModelSelected(modelId)
                                    onDismiss() // Close the dialog after selecting the model
                                },
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
                                    modelId.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                // Add small tool icon if model supports tool calling
                                if (modelId.supportsTools) {
                                    Icon(
                                        painterResource(R.drawable.hammer), // or any tool icon you like
                                        contentDescription = "Supports tool calling",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(rDP(16.dp))
                                    )
                                }
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = "Add",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(rDP(16.dp))
                                )
                            }

                        }
                    }
                }
            }
        }
    }, confirmButton = {}, dismissButton = {
        TextButton(onClick = onDismiss) {
            Text("Close")
        }
    })
}

@Composable
private fun EmptyStateCard(
    icon: ImageVector, title: String, subtitle: String
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
            modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
        ) {
            EmptyStateCard(
                icon = Icons.TwoTone.Inventory,
                title = "No models installed",
                subtitle = "Download from marketplace or import a GGUF file"
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = rDP(8.dp))
        ) {
            items(installed, key = { it.id }) { model ->
                InstalledModelCard(
                    model = model,
                    onDelete = { viewModel.removeModel(model.modelName) },
                    onInfo = {})
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// CARDS
// ═══════════════════════════════════════════════════════════════════════════
@Composable
private fun InstalledModelCard(
    model: ModelData, onDelete: () -> Unit, onInfo: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = rDP(16.dp), vertical = rDP(6.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondary.copy(0.1f)
        )
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(rDP(14.dp)),
            verticalArrangement = Arrangement.spacedBy(rDP(10.dp))
        ) {
            Row(
                Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        model.modelName, style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold, fontSize = rSp(18.sp)
                        ), maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(rDP(4.dp)))
                    Pill(
                        text = if (model.providerName == ModelProvider.LocalGGUF.toString()) "Local" else "OpenRouter",
                        isRemote = model.providerName != ModelProvider.LocalGGUF.toString()
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(rDP(8.dp))) {
                    IconButton(
                        onClick = onInfo, colors = IconButtonDefaults.iconButtonColors(
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
                        onClick = onDelete, colors = IconButtonDefaults.iconButtonColors(
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondary.copy(0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDP(14.dp)),
            verticalArrangement = Arrangement.spacedBy(rDP(12.dp))
        ) {
            Text(
                modelsData.modelName,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
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
                    modifier = Modifier.fillMaxWidth(), progress = { progress })
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
                                    modelsData.modelName, modelsData.modelUrl.toString()   // ← key
                                )
                            }

                            !isInstalled && !isDownloading -> onDownload()
                            // otherwise – do nothing; button is disabled
                        }
                    }, colors = if (!isInstalled) {
                        ButtonDefaults.buttonColors()
                    } else {
                        ButtonDefaults.buttonColors(
                            containerColor = Success.copy(alpha = 0.2f), contentColor = Success
                        )
                    }
                ) {
                    AnimatedContent(
                        targetState = isInstalled,
                        transitionSpec = { fadeIn() togetherWith fadeOut() }) { installed ->
                        if (installed) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                        } else {
                            AnimatedContent(
                                targetState = isDownloading,
                                transitionSpec = { fadeIn() togetherWith fadeOut() }) { downloading ->
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
                        }, colors = IconButtonDefaults.iconButtonColors(
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
            text, modifier = Modifier.padding(
                horizontal = rDP(10.dp), vertical = rDP(4.dp)
            ), style = MaterialTheme.typography.labelMedium.copy(
                color = if (!isRemote) Mint else SkyBlue, fontWeight = FontWeight.Bold
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
        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label, style = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.primary
            )
        )
        Text(
            value, style = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            ), maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}