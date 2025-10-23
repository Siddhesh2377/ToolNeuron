package com.dark.neuroverse.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProgressIndicatorDefaults
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
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
        val isDialogOpen by viewModel.isDialogOpened.collectAsStateWithLifecycle()
        Box {
            Column(
                Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .blur(
                        if (isDialogOpen) rDP(10.dp) else rDP(0.dp), BlurredEdgeTreatment.Unbounded
                    ), horizontalAlignment = Alignment.CenterHorizontally
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
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
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
            }

//            StandardBottomBar(Modifier
//                .align(Alignment.BottomCenter)
//                .padding(bottom = rDP(14.dp))) {
//                CollapsableButton(
//                    text = "Finish",
//                    icon = Icons.AutoMirrored.Filled.ArrowForward,
//                    enabled = isEnabled
//                ) { onNext() }
//            }
        }
    }
}

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
                modelData = modelData,
                isDownloading = state.isDownloading,
                progress = state.progress,
                onDownloadComplete = state.isComplete,
                viewModel = viewModel,
                onDownload = { viewModel.startDownload(modelData, context) })
        }
    }
}

@Composable
private fun OpenRouterTab(viewModel: ModelScreenViewModel) {
    val context = LocalContext.current
    val openRouterApiKey by viewModel.openRouterApiKey.collectAsStateWithLifecycle()
    val openRouterBaseUrl by viewModel.openRouterBaseUrl.collectAsStateWithLifecycle()
    val openRouterInstalled by viewModel.openRouterInstalledModels.collectAsStateWithLifecycle()
    val availableModels by viewModel.availableModels.collectAsStateWithLifecycle()

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

    LaunchedEffect(showModelPicker) {
        viewModel.setIsDialogOpen(showModelPicker)
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
        color = MaterialTheme.colorScheme.surfaceVariant
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

            OutlinedIconButton(
                onClick = onDelete,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                colors = IconButtonDefaults.outlinedIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
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
            .filter { it !in selectedModels }
    }

    AlertDialog(onDismissRequest = onDismiss, confirmButton = {}, dismissButton = {
        TextButton(onClick = onDismiss) { Text("Close") }
    }, title = {
        Text(
            text = "Select Model",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        )
    }, text = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = rDP(520.dp)),
            verticalArrangement = Arrangement.spacedBy(rDP(8.dp))
        ) {
            // Search Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search models...") },
                leadingIcon = {
                    Icon(Icons.TwoTone.Search, contentDescription = "Search")
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(rDP(12.dp))
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = rDP(6.dp)))

            // Empty State
            if (filteredModels.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.TwoTone.SearchOff,
                    title = "No models found",
                    subtitle = if (searchQuery.isBlank()) "Try fetching models first" else "Try a different keyword"
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(rDP(6.dp))
                ) {
                    items(filteredModels) { model ->
                        ModelListItem(
                            model = model, onClick = {
                                onModelSelected(model)
                                onDismiss()
                            })
                    }
                }
            }
        }
    })
}

@Composable
private fun ModelListItem(model: OpenRouterModel, onClick: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }

    val backgroundColor by animateColorAsState(
        if (isPressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else MaterialTheme.colorScheme.surfaceVariant
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(rDP(10.dp)))
            .clickable(
                interactionSource = remember { MutableInteractionSource() }) {
                isPressed = true
                onClick()
            }, color = backgroundColor, tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDP(12.dp)),
            verticalArrangement = Arrangement.spacedBy(rDP(6.dp))
        ) {
            // --- Model Name Row ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    maxLines = 2, // allow wrapping instead of truncating
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (model.supportsTools) {
                        Icon(
                            painter = painterResource(R.drawable.hammer),
                            contentDescription = "Supports Tools",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier
                                .padding(end = rDP(4.dp))
                                .size(rDP(18.dp))
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add Model",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(rDP(20.dp))
                    )
                }
            }

            // --- Details Line ---
            Column(verticalArrangement = Arrangement.spacedBy(rDP(2.dp))) {
                Text(
                    text = "Context Size: ${model.ctxSize}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Text(
                    text = "Temperature: ${model.temperature} | Top-P: ${model.topP}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
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

    val showModelDialog by viewModel.isDialogOpened.collectAsState()
    var selectedModel by remember { mutableStateOf<ModelData?>(null) }

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
            modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = rDP(4.dp))
        ) {
            items(installed, key = { it.id }) { model ->
                InstalledModelCard(
                    model = model,
                    onDelete = { viewModel.removeModel(model.modelName) },
                    onInfo = {
                        selectedModel = model
                        viewModel.setIsDialogOpen(true)
                    })
            }
        }

        AnimatedVisibility(visible = showModelDialog) {
            FileDetailDialog(model = selectedModel ?: ModelData(), onDismiss = {
                viewModel.setIsDialogOpen(false)
            }, onSelect = {

            })
        }
    }
}


@Composable
fun ModelCard(
    modelData: ModelData,
    isDownloading: Boolean = false,
    onDownloadComplete: Boolean = false,
    progress: Float = 0f,
    viewModel: ModelScreenViewModel,
    onDownload: () -> Unit = {}
) {
    var isInstalled by remember { mutableStateOf(false) }

    LaunchedEffect(modelData.modelName) {
        viewModel.checkIfInstalled(modelData.modelName) { isInstalled = it }
    }

    LaunchedEffect(onDownloadComplete) {
        if (onDownloadComplete) isInstalled = true
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = rDP(16.dp), vertical = rDP(6.dp))
            .clip(RoundedCornerShape(rDP(8.dp))), colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceDim
        ), elevation = CardDefaults.cardElevation(defaultElevation = rDP(0.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDP(16.dp)),
            verticalArrangement = Arrangement.spacedBy(rDP(12.dp))
        ) {
            // --- Model Title ---
            Text(
                text = modelData.modelName, style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold, fontSize = rSp(18.sp)
                ), maxLines = 2, overflow = TextOverflow.Ellipsis
            )

            // --- Specs as Pills ---
            Row(horizontalArrangement = Arrangement.spacedBy(rDP(8.dp))) {
                Pill(text = "Context: ${modelData.ctxSize}")
                Pill(text = if (modelData.isToolCalling) "Tools: YES" else "Tools: NO")
            }

            // --- Progress Indicator ---
            AnimatedVisibility(visible = isDownloading) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rDP(6.dp))
                        .clip(RoundedCornerShape(rDP(6.dp))),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                    strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                )
            }

            // --- Actions ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(rDP(10.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Download / Cancel / Installed Button
                Button(
                    onClick = {
                        when {
                            !isInstalled && isDownloading -> {
                                viewModel.cancelDownload(
                                    modelData.modelName, modelData.modelUrl.toString()
                                )
                            }

                            !isInstalled && !isDownloading -> onDownload()
                        }
                    }, colors = if (!isInstalled) ButtonDefaults.buttonColors()
                    else ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                        contentColor = MaterialTheme.colorScheme.secondary
                    ), shape = RoundedCornerShape(rDP(12.dp)), modifier = Modifier.weight(1f)
                ) {
                    AnimatedContent(
                        targetState = when {
                            isInstalled -> "Installed"
                            isDownloading -> "Cancel"
                            else -> "Download"
                        }
                    ) { label ->
                        Text(label)
                    }
                }

                // Delete Button (visible only if installed)
                AnimatedVisibility(visible = isInstalled) {
                    OutlinedIconButton(
                        onClick = {
                            viewModel.removeModel(modelData.modelName)
                            isInstalled = false
                        },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        colors = IconButtonDefaults.outlinedIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
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
    }
}

@Composable
private fun Pill(
    modifier: Modifier = Modifier,
    text: String,
    isRemote: Boolean = false,
) {
    val bgColor = if (!isRemote) Mint.copy(alpha = 0.15f) else SkyBlue.copy(alpha = 0.15f)
    val textColor = if (!isRemote) Mint else SkyBlue

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(rDP(12.dp)),
        color = bgColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Text(
            text = text, style = MaterialTheme.typography.labelMedium.copy(
                color = textColor, fontWeight = FontWeight.SemiBold
            ), modifier = Modifier.padding(horizontal = rDP(10.dp), vertical = rDP(4.dp))
        )
    }
}

@Composable
private fun InstalledModelCard(
    model: ModelData, onDelete: () -> Unit, onInfo: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = rDP(16.dp), vertical = rDP(8.dp))
            .clip(RoundedCornerShape(rDP(8.dp))), colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ), elevation = CardDefaults.cardElevation(defaultElevation = rDP(0.dp))
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(rDP(14.dp)),
            verticalArrangement = Arrangement.spacedBy(rDP(12.dp))
        ) {
            // --- Header Row ---
            Row(
                Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(rDP(4.dp))
                ) {
                    Text(
                        text = model.modelName, style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold, fontSize = rSp(18.sp)
                        ), maxLines = 2, overflow = TextOverflow.Ellipsis
                    )

                    Pill(
                        text = if (model.providerName == ModelProvider.LocalGGUF.toString()) "Local Model"
                        else "OpenRouter",
                        isRemote = model.providerName != ModelProvider.LocalGGUF.toString()
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(rDP(6.dp))) {
                    OutlinedIconButton(
                        onClick = {
                            onInfo()
                        },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        colors = IconButtonDefaults.outlinedIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.onPrimary,
                        )
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.Info,
                            contentDescription = "Model Info",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }


                    OutlinedIconButton(
                        onClick = {
                            onDelete()
                        },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        colors = IconButtonDefaults.outlinedIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
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

            // --- Optional Model Stats Section ---
            HorizontalDivider(modifier = Modifier.alpha(0.5f))
            Column(
                verticalArrangement = Arrangement.spacedBy(rDP(4.dp))
            ) {
                Text(
                    text = "Context size: ${model.ctxSize} tokens",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Text(
                    text = "Temperature: ${model.temp}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
private fun FileDetailDialog(
    model: ModelData, onDismiss: () -> Unit, onSelect: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = rDP(16.dp)),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                Modifier.padding(rDP(20.dp)), verticalArrangement = Arrangement.spacedBy(rDP(10.dp))
            ) {
                Text(
                    text = "Model Details",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                )

                Spacer(Modifier.height(rDP(6.dp)))

                // --- Core Info ---
                InfoRow("Name", model.modelName)
                InfoRow("Provider", model.providerName)
                InfoRow("Type", model.modelType.name)
                InfoRow("Context Size", model.ctxSize.toString())
                InfoRow("Threads", model.threads.toString())
                InfoRow("GPU Layers", model.gpuLayers.toString())
                InfoRow("Temp / Top-P / Top-K", "${model.temp} / ${model.topP} / ${model.topK}")
                InfoRow("Max Tokens", model.maxTokens.toString())
                InfoRow("Tool Calling", if (model.isToolCalling) "Yes" else "No")
                InfoRow("Imported", if (model.isImported) "Yes" else "No")
                InfoRow("File Path", model.modelPath)

                HorizontalDivider(modifier = Modifier.padding(vertical = rDP(6.dp)))

                // --- Action Buttons ---
                Row(horizontalArrangement = Arrangement.spacedBy(rDP(8.dp))) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Close")
                    }
                    Button(
                        onClick = onSelect,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
        )
        Spacer(Modifier.height(3.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
        )
    }
}