package com.dark.tool_neuron.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.twotone.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.ai_module.data.ModelsList.getModelList
import com.dark.ai_module.model.*
import com.dark.tool_neuron.activity.GgufPickerActivity
import com.dark.tool_neuron.model.DownloadState
import com.dark.tool_neuron.ui.theme.rDP
import com.dark.tool_neuron.viewModel.ModelScreenViewModel
import kotlinx.coroutines.delay
import java.io.File
import com.dark.tool_neuron.R

// ============================================================================
// PROVIDER CONFIGURATION - Easy to extend!
// ============================================================================

private sealed class ModelProviderTab(
    val id: String,
    val label: String,
    val icon: Int,
    val description: String,
    val isOnline: Boolean
) {
    object LocalGGUF : ModelProviderTab(
        "local_gguf", "Local Models", R.drawable.text_ai_models,
        "On-device GGUF models", false
    )

    object OpenRouter : ModelProviderTab(
        "openrouter", "OpenRouter", R.drawable.open_router,
        "Cloud-based models", true
    )

    object SherpaONNX : ModelProviderTab(
        "sherpa", "Sherpa ONNX", R.drawable.stt_models,
        "Speech & Audio models", false
    )

    object Installed : ModelProviderTab(
        "installed", "Installed", R.drawable.installed_models,
        "All installed models", false
    )

    companion object {
        fun all() = listOf(LocalGGUF, OpenRouter, SherpaONNX, Installed)
    }
}

// ============================================================================
// MAIN SCREEN
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val viewModel: ModelScreenViewModel = viewModel()
    val haptic = LocalHapticFeedback.current

    var selectedProvider by remember { mutableStateOf<ModelProviderTab>(ModelProviderTab.LocalGGUF) }
    var showSearch by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            ModernTopBar(
                title = selectedProvider.label,
                subtitle = selectedProvider.description,
                onBack = onBack,
                onSearchToggle = { showSearch = !showSearch }
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = selectedProvider == ModelProviderTab.LocalGGUF,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                FloatingActionButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        context.startActivity(Intent(context, GgufPickerActivity::class.java))
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.TwoTone.FileOpen, "Import Model")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Provider Tabs
            ProviderTabRow(
                providers = ModelProviderTab.all(),
                selected = selectedProvider,
                onSelect = {
                    selectedProvider = it
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                }
            )

            // Content
            AnimatedContent(
                targetState = selectedProvider,
                transitionSpec = {
                    fadeIn(animationSpec = spring()) + slideInHorizontally(animationSpec = spring()) { it / 4 } togetherWith
                            fadeOut(animationSpec = spring()) + slideOutHorizontally(animationSpec = spring()) { -it / 4 }
                },
                label = "provider_content"
            ) { provider ->
                when (provider) {
                    ModelProviderTab.LocalGGUF -> LocalModelsTab(viewModel)
                    ModelProviderTab.OpenRouter -> OpenRouterTab(viewModel)
                    ModelProviderTab.SherpaONNX -> SherpaONNXTab(viewModel)
                    ModelProviderTab.Installed -> InstalledModelsTab(viewModel)
                }
            }
        }
    }
}

// ============================================================================
// TOP BAR
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModernTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onSearchToggle: () -> Unit
) {
    CenterAlignedTopAppBar(
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.next),
                    contentDescription = "Back",
                    modifier = Modifier.rotate(-180f)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    )
}

// ============================================================================
// PROVIDER TAB ROW
// ============================================================================

@Composable
private fun ProviderTabRow(
    providers: List<ModelProviderTab>,
    selected: ModelProviderTab,
    onSelect: (ModelProviderTab) -> Unit
) {
    // Use LazyRow instead of ScrollableTabRow for better performance
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(vertical = rDP(8.dp)),
        contentPadding = PaddingValues(horizontal = rDP(16.dp)),
        horizontalArrangement = Arrangement.spacedBy(rDP(8.dp))
    ) {
        items(providers, key = { it.id }) { provider ->
            ProviderTab(
                provider = provider,
                isSelected = provider == selected,
                onClick = { onSelect(provider) }
            )
        }
    }
}

@Composable
private fun ProviderTab(
    provider: ModelProviderTab,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    // Animate only the background color, not size
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(200),
        label = "tab_bg"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(200),
        label = "tab_content"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(rDP(12.dp)),
        color = backgroundColor,
        modifier = Modifier.height(rDP(40.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = rDP(16.dp)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(rDP(8.dp))
        ) {
            Icon(
                painter = painterResource(provider.icon),
                contentDescription = null,
                modifier = Modifier.size(rDP(20.dp)),
                tint = contentColor
            )

            // Always show text, no AnimatedVisibility
            if (isSelected) {
                Text(
                    provider.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }
        }
    }
}

// ============================================================================
// LOCAL MODELS TAB
// ============================================================================

@Composable
private fun LocalModelsTab(viewModel: ModelScreenViewModel) {
    val context = LocalContext.current
    val downloadStates by viewModel.downloadStates.collectAsState()

    val textModels = remember { getModelList(context) }
    val vlmModels = remember { getVLMModelList(context) }
    val allModels = remember { textModels + vlmModels }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(rDP(16.dp)),
        verticalArrangement = Arrangement.spacedBy(rDP(12.dp))
    ) {
        if (textModels.isNotEmpty()) {
            item {
                CategoryHeader("Text Models", textModels.size)
            }
            items(textModels, key = { it.id }) { model ->
                ModernModelCard(
                    modelData = model,
                    downloadState = downloadStates[model.modelUrl.toString()],
                    viewModel = viewModel
                )
            }
        }

        if (vlmModels.isNotEmpty()) {
            item {
                Spacer(Modifier.height(rDP(8.dp)))
                CategoryHeader("Vision Models", vlmModels.size)
            }
            items(vlmModels, key = { it.id }) { model ->
                ModernModelCard(
                    modelData = model,
                    downloadState = downloadStates[model.modelUrl.toString()],
                    viewModel = viewModel
                )
            }
        }

        if (allModels.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.TwoTone.CloudOff,
                    title = "No models available",
                    subtitle = "Import a GGUF file to get started"
                )
            }
        }
    }
}

// ============================================================================
// SHERPA ONNX TAB
// ============================================================================

@Composable
private fun SherpaONNXTab(viewModel: ModelScreenViewModel) {
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

// ============================================================================
// MODERN MODEL CARD
// ============================================================================

@Composable
private fun ModernModelCard(
    modelData: ModelData,
    downloadState: DownloadState?,
    viewModel: ModelScreenViewModel
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
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(rDP(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDP(16.dp)),
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
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(rDP(4.dp)))

                    Row(horizontalArrangement = Arrangement.spacedBy(rDP(8.dp))) {
                        Chip("${modelData.ctxSize} ctx")
                        if (modelData.isToolCalling) {
                            Chip("Tools", isHighlighted = true)
                        }
                    }
                }

                if (isInstalled && !isDownloading) {
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(rDP(36.dp))
                    ) {
                        Icon(
                            Icons.TwoTone.Info,
                            contentDescription = "Info",
                            tint = MaterialTheme.colorScheme.primary
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
                Column(verticalArrangement = Arrangement.spacedBy(rDP(8.dp))) {
                    HorizontalDivider(Modifier.alpha(0.3f))
                    DetailRow("Temperature", modelData.temp.toString())
                    DetailRow("Top-P", modelData.topP.toString())
                    DetailRow("Max Tokens", modelData.maxTokens.toString())
                    DetailRow("GPU Layers", modelData.gpuLayers.toString())
                }
            }

            // Download Progress
            if (isDownloading) {
                Column(verticalArrangement = Arrangement.spacedBy(rDP(8.dp))) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Downloading...",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rDP(6.dp))
                            .clip(RoundedCornerShape(rDP(3.dp)))
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
                                modelData.modelName,
                                modelData.modelUrl.toString()
                            )
                            !isInstalled -> viewModel.startDownload(modelData, context)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isInstalled || isDownloading,
                    colors = if (isInstalled && !isDownloading) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else ButtonDefaults.buttonColors(),
                    shape = RoundedCornerShape(rDP(12.dp))
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
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Icon(
                            Icons.TwoTone.Delete,
                            contentDescription = "Remove",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// OPENROUTER TAB (Simplified for readability)
// ============================================================================

@Composable
private fun OpenRouterTab(viewModel: ModelScreenViewModel) {
    val context = LocalContext.current
    val apiKey by viewModel.openRouterApiKey.collectAsStateWithLifecycle()
    val baseUrl by viewModel.openRouterBaseUrl.collectAsStateWithLifecycle()
    val installed by viewModel.openRouterInstalledModels.collectAsStateWithLifecycle()
    val available by viewModel.availableModels.collectAsStateWithLifecycle()

    var showPicker by remember { mutableStateOf(false) }
    var showKey by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.initOpenRouter(context)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(rDP(16.dp)),
        verticalArrangement = Arrangement.spacedBy(rDP(16.dp))
    ) {
        // API Settings Card
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = RoundedCornerShape(rDP(16.dp))
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(rDP(16.dp)),
                    verticalArrangement = Arrangement.spacedBy(rDP(12.dp))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.TwoTone.Key, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(rDP(8.dp)))
                        Text("API Configuration", fontWeight = FontWeight.Bold)
                    }

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { viewModel.saveOpenRouterApiKey(context, it) },
                        label = { Text("API Key") },
                        trailingIcon = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(
                                    painterResource(if (showKey) R.drawable.show else R.drawable.hide),
                                    "Toggle"
                                )
                            }
                        },
                        visualTransformation = if (showKey)
                            VisualTransformation.None
                        else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(rDP(12.dp))
                    )

                    Button(
                        onClick = { viewModel.fetchAvailableModels() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = apiKey.isNotBlank(),
                        shape = RoundedCornerShape(rDP(12.dp))
                    ) {
                        Text("Fetch Models", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Selected Models
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = RoundedCornerShape(rDP(16.dp))
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(rDP(16.dp)),
                    verticalArrangement = Arrangement.spacedBy(rDP(12.dp))
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Selected Models", fontWeight = FontWeight.Bold)
                        if (installed.isNotEmpty()) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    "${installed.size}",
                                    modifier = Modifier.padding(
                                        horizontal = rDP(10.dp),
                                        vertical = rDP(4.dp)
                                    ),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }

                    if (installed.isEmpty()) {
                        EmptyState(
                            icon = Icons.TwoTone.CloudOff,
                            title = "No models selected",
                            subtitle = "Add from available models",
                            compact = true
                        )
                    } else {
                        installed.forEach { model ->
                            OpenRouterModelItem(
                                model = model,
                                onRemove = { viewModel.removeModel(model.name) }
                            )
                        }
                    }

                    Button(
                        onClick = { showPicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = available.isNotEmpty(),
                        shape = RoundedCornerShape(rDP(12.dp))
                    ) {
                        Icon(Icons.Filled.Add, null)
                        Spacer(Modifier.width(rDP(8.dp)))
                        Text("Add Model", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showPicker) {
        ModelPickerDialog(
            available = available,
            selected = installed,
            onDismiss = { showPicker = false },
            onSelect = { model ->
                viewModel.addOpenRouterModel(model)
                viewModel.addModel(model.toModelData())
                showPicker = false
            }
        )
    }
}

// ============================================================================
// INSTALLED MODELS TAB
// ============================================================================

@Composable
private fun InstalledModelsTab(viewModel: ModelScreenViewModel) {
    val models by viewModel.models.collectAsState()

    if (models.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.TwoTone.Inventory,
                title = "No installed models",
                subtitle = "Download or import models to get started"
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(rDP(16.dp)),
            verticalArrangement = Arrangement.spacedBy(rDP(12.dp))
        ) {
            items(models, key = { it.id }) { model ->
                InstalledModelCard(
                    model = model,
                    onRemove = { viewModel.removeModel(model.modelName) }
                )
            }
        }
    }
}

// ============================================================================
// HELPER COMPONENTS
// ============================================================================

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
                fontWeight = FontWeight.Bold
            )
        )
        Surface(
            shape = RoundedCornerShape(rDP(8.dp)),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Text(
                "$count",
                modifier = Modifier.padding(horizontal = rDP(10.dp), vertical = rDP(4.dp)),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun Chip(text: String, isHighlighted: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(rDP(8.dp)),
        color = if (isHighlighted)
            MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = rDP(10.dp), vertical = rDP(4.dp)),
            style = MaterialTheme.typography.labelSmall,
            color = if (isHighlighted)
                MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

@Composable
private fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    compact: Boolean = false
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
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(rDP(if (compact) 56.dp else 80.dp))
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
            style = if (compact) MaterialTheme.typography.titleMedium
            else MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun OpenRouterModelItem(model: OpenRouterModel, onRemove: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(rDP(12.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDP(12.dp)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    model.name,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${model.ctxSize} ctx • Temp ${model.temperature}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onRemove,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
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

@Composable
private fun InstalledModelCard(model: ModelData, onRemove: () -> Unit) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(rDP(16.dp))
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(rDP(16.dp)),
            verticalArrangement = Arrangement.spacedBy(rDP(12.dp))
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        model.modelName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(rDP(4.dp)))

                    Chip(
                        text = if (model.providerName == ModelProvider.LocalGGUF.toString())
                            "Local Model" else model.providerName,
                        isHighlighted = model.providerName == ModelProvider.LocalGGUF.toString()
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(rDP(4.dp))) {
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Icon(
                            Icons.TwoTone.Info,
                            contentDescription = "Info",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    IconButton(
                        onClick = onRemove,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Icon(
                            Icons.TwoTone.Delete,
                            contentDescription = "Remove",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(rDP(8.dp))) {
                    HorizontalDivider(Modifier.alpha(0.3f))

                    Row(horizontalArrangement = Arrangement.spacedBy(rDP(8.dp))) {
                        Chip("${model.ctxSize} ctx")
                        Chip("Temp ${model.temp}")
                        Chip("Top-P ${model.topP}")
                    }

                    DetailRow("Top-K", model.topK.toString())
                    DetailRow("Max Tokens", model.maxTokens.toString())
                    DetailRow("GPU Layers", model.gpuLayers.toString())
                    DetailRow("Threads", model.threads.toString())
                }
            }
        }
    }
}

@Composable
private fun ModelPickerDialog(
    available: List<OpenRouterModel>,
    selected: List<OpenRouterModel>,
    onDismiss: () -> Unit,
    onSelect: (OpenRouterModel) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filtered = remember(searchQuery, available, selected) {
        available
            .filter { it.name.contains(searchQuery, ignoreCase = true) }
            .filter { it !in selected }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", fontWeight = FontWeight.Bold)
            }
        },
        title = {
            Text(
                "Select Model",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                )
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = rDP(500.dp)),
                verticalArrangement = Arrangement.spacedBy(rDP(12.dp))
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search models...") },
                    leadingIcon = { Icon(Icons.TwoTone.Search, null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(rDP(12.dp)),
                    singleLine = true
                )

                if (filtered.isEmpty()) {
                    EmptyState(
                        icon = Icons.TwoTone.SearchOff,
                        title = "No models found",
                        subtitle = if (searchQuery.isBlank())
                            "Fetch models first"
                        else "Try different keywords",
                        compact = true
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(rDP(8.dp))
                    ) {
                        items(filtered, key = { it.id }) { model ->
                            ModelPickerItem(
                                model = model,
                                onClick = { onSelect(model) }
                            )
                        }
                    }
                }
            }
        },
        shape = RoundedCornerShape(rDP(20.dp))
    )
}

@Composable
private fun ModelPickerItem(model: OpenRouterModel, onClick: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(rDP(12.dp)))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isPressed = true
                onClick()
            },
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(rDP(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDP(12.dp)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    model.name,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(rDP(4.dp)))

                Row(horizontalArrangement = Arrangement.spacedBy(rDP(8.dp))) {
                    Text(
                        "${model.ctxSize} ctx",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (model.supportsTools) {
                        Text(
                            "• Tools ✓",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Icon(
                Icons.Filled.Add,
                contentDescription = "Add",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(rDP(24.dp))
            )
        }
    }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(100)
            isPressed = false
        }
    }
}

// ============================================================================
// HELPER FUNCTIONS
// ============================================================================

private fun getVLMModelList(context: Context): List<ModelData> {
    return emptyList()
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