package com.dark.neuroverse.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Spring.DampingRatioMediumBouncy
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.QuestionMark
import androidx.compose.material.icons.twotone.CheckCircle
import androidx.compose.material.icons.twotone.CloudOff
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.FileOpen
import androidx.compose.material.icons.twotone.GraphicEq
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.dark.neuroverse.ui.theme.rDP
import com.dark.neuroverse.ui.theme.rSp
import com.dark.neuroverse.viewModel.ModelScreenViewModel

// Navigation Rail Categories
private enum class ModelCategory(
    val label: String, val shortLabel: String, val icon: Int
) {
    TEXT("Text Models", "TXT", R.drawable.text_ai_models), VLM(
        "Vision Models", "VLM", R.drawable.vl_models
    ),
    OPENROUTER("OpenRouter", "OPR", R.drawable.open_router), STT(
        "Speech-to-Text", "STT", R.drawable.stt_models
    ),
    TTS("Text-to-Speech", "TTS", R.drawable.tts_models), INSTALLED(
        "Installed", "INM", R.drawable.installed_models
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen() {
    val context = LocalContext.current
    val viewModel: ModelScreenViewModel = viewModel()

    val radius = rDP(18.dp)

    var selectedCategory by remember { mutableIntStateOf(0) }
    val haptic = LocalHapticFeedback.current

    Scaffold(
        floatingActionButton = {
            if (selectedCategory == ModelCategory.TEXT.ordinal) {
                FloatingActionButton(
                    onClick = {
                        context.startActivity(Intent(context, GgufPickerActivity::class.java))
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    elevation = FloatingActionButtonDefaults.elevation(8.dp)
                ) {
                    Icon(
                        Icons.TwoTone.FileOpen, contentDescription = "Import Model"
                    )
                }
            }
        }) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Navigation Rail
            EnhancedNavigationRail(
                selectedCategory = selectedCategory,
                onCategorySelected = {
                    selectedCategory = it
                    haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                },
            )

            // Main Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        MaterialTheme.colorScheme.surfaceContainer,
                        RoundedCornerShape(topStart = radius, bottomStart = radius)
                    )
            ) {
                Crossfade(selectedCategory, label = "category_transition") {
                    when (it) {
                        ModelCategory.TEXT.ordinal -> MarketplaceList(viewModel, "Text")
                        ModelCategory.VLM.ordinal -> ComingSoonScreen("VL-Models")
                        ModelCategory.OPENROUTER.ordinal -> OpenRouterTab(viewModel)
                        ModelCategory.STT.ordinal -> ComingSoonScreen("Speech-to-Text")
                        ModelCategory.TTS.ordinal -> ComingSoonScreen("Text-to-Speech")
                        ModelCategory.INSTALLED.ordinal -> InstalledList(viewModel)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EnhancedNavigationRail(
    selectedCategory: Int, onCategorySelected: (Int) -> Unit
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.background, header = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = rDP(16.dp))
            ) {
                Icon(
                    painter = painterResource(R.drawable.ai_model),
                    contentDescription = null,
                    modifier = Modifier.size(rDP(32.dp)),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(rDP(8.dp)))
                Text(
                    "Models",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(rDP(12.dp)))
            ModelCategory.entries.forEachIndexed { index, category ->
                val selected = selectedCategory == index
                val scale by animateFloatAsState(
                    targetValue = if (selected) 1.05f else 1f, animationSpec = spring(
                        DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium
                    )
                )

                NavigationRailItem(
                    selected = selected,
                    onClick = { onCategorySelected(index) },
                    icon = {
                        Box(
                            contentAlignment = Alignment.Center, modifier = Modifier
                                .background(
                                    color = if (selected) MaterialTheme.colorScheme.primary.copy(
                                        alpha = 0.15f
                                    )
                                    else Color.Transparent,
                                    shape = MaterialShapes.Cookie7Sided.toShape()
                                )
                                .scale(scale)
                                .padding(rDP(8.dp))
                        ) {
                            Icon(
                                painter = painterResource(category.icon),
                                contentDescription = category.label,
                                modifier = Modifier.size(rDP(24.dp)),
                                tint = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    label = {
                        Text(
                            category.shortLabel, style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    },
                    modifier = Modifier.padding(vertical = rDP(4.dp)),
                    colors = NavigationRailItemDefaults.colors(
                        indicatorColor = Color.Transparent
                    )
                )
            }
        }
    }

}

@Composable
private fun ComingSoonScreen(featureName: String) {
    Box(
        modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(rDP(16.dp))
        ) {
            Icon(
                Icons.TwoTone.GraphicEq,
                contentDescription = null,
                modifier = Modifier.size(rDP(64.dp)),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
            Text(
                "$featureName Models",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Coming Soon!",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun MarketplaceList(viewModel: ModelScreenViewModel, filterType: String) {
    val context = LocalContext.current
    val models = remember { getModelList(context) }
    val downloadStates by viewModel.downloadStates.collectAsState()

    // Filter models based on type (you'll need to add type field to ModelData)
    // For now, showing all models

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(rDP(20.dp)),
        verticalArrangement = Arrangement.spacedBy(rDP(16.dp))
    ) {
        item {
            Text(
                "$filterType Models", style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif
                ), modifier = Modifier.padding(bottom = rDP(8.dp))
            )
        }

        items(models, key = { it.modelUrl.toString() }) { modelData ->
            val state = downloadStates[modelData.modelUrl.toString()] ?: DownloadState()
            EnhancedModelCard(
                modelData = modelData,
                isDownloading = state.isDownloading,
                progress = state.progress,
                onDownloadComplete = state.isComplete,
                viewModel = viewModel,
                onDownload = { viewModel.startDownload(modelData, context) })
        }
    }
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EnhancedModelCard(
    modelData: ModelData,
    isDownloading: Boolean = false,
    onDownloadComplete: Boolean = false,
    progress: Float = 0f,
    viewModel: ModelScreenViewModel,
    onDownload: () -> Unit = {}
) {
    var isInstalled by remember { mutableStateOf(false) } // Track installation status
    var isExpanded by remember { mutableStateOf(false) } // Track details visibility

    // Check installation when model changes
    LaunchedEffect(modelData.modelName) {
        viewModel.checkIfInstalled(modelData.modelName) {
            isInstalled = it
        }
    }
    // Update flag when download finishes
    LaunchedEffect(onDownloadComplete) { if (onDownloadComplete) isInstalled = true }

    val cardElevation by animateDpAsState(
        targetValue = if (isExpanded) rDP(8.dp) else rDP(2.dp),
        animationSpec = spring(dampingRatio = DampingRatioMediumBouncy)
    )

    // --------------------------------------------------------------

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(rDP(20.dp))),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = cardElevation)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDP(20.dp)),
            verticalArrangement = Arrangement.spacedBy(rDP(16.dp))
        ) {
            // Header ------------------------------------------------
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        modelData.modelName, style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold, fontSize = rSp(20.sp)
                        ), maxLines = 2, overflow = TextOverflow.Ellipsis
                    )

                    if (isInstalled) {
                        IconButton(
                            onClick = {
                                isExpanded = !isExpanded
                            },
                            shape = MaterialShapes.Square.toShape(),
                            colors = IconButtonDefaults.outlinedIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondary.copy(0.2f),
                                contentColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Icon(
                                Icons.Outlined.QuestionMark,
                                contentDescription = "Installed",
                                modifier = Modifier.size(rDP(24.dp))
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = isExpanded) {
                    Text(
                        "Tap to collapse details",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f),
                        modifier = Modifier.padding(top = rDP(4.dp))
                    )
                }
            }


            // Specs pills -------------------------------------------

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(rDP(8.dp)),
                verticalArrangement = Arrangement.spacedBy(rDP(8.dp))
            ) {
                AnimatedPill("Context: ${modelData.ctxSize}")
                AnimatedPill(
                    if (modelData.isToolCalling) "Tools ✓" else "No Tools",
                    isHighlighted = modelData.isToolCalling
                )
            }

            // Detail section -----------------------------------------

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(rDP(8.dp))) {
                    HorizontalDivider(modifier = Modifier.alpha(.3f))
                    DetailRow("Temperature", modelData.temp.toString())
                    DetailRow("Top‑P", modelData.topP.toString())
                    DetailRow("Max Tokens", modelData.maxTokens.toString())
                }
            }

            // Progress bar -------------------------------------------

            if (isDownloading) {
                Column(verticalArrangement = Arrangement.spacedBy(rDP(8.dp))) {
                    val pct = (progress * 100).toInt()
                    Text("Downloading…", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "$pct %",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    LinearProgressIndicator(
                        progress = { progress },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rDP(8.dp))
                            .clip(RoundedCornerShape(rDP(8.dp)))
                    )
                }
            }

            // Action buttons -----------------------------------------

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(rDP(12.dp))
            ) {
                Button(
                    onClick = {
                        when {
                            !isInstalled && isDownloading -> viewModel.cancelDownload(
                                modelData.modelName, modelData.modelUrl.toString()
                            )

                            !isInstalled && !isDownloading -> onDownload()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = if (!isInstalled) ButtonDefaults.buttonColors()
                    else ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(0.1f),
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(rDP(16.dp))
                ) {
                    AnimatedContent(
                        targetState = when {
                            isInstalled -> "Installed"
                            isDownloading -> "Cancel"
                            else -> "Download"
                        }
                    ) { label ->
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = isInstalled,
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    OutlinedIconButton(
                        onClick = {
                            viewModel.removeModel(modelData.modelName)
                            isInstalled = false
                        },
                        shape = MaterialShapes.Square.toShape(),
                        border = BorderStroke(0.dp, Color.Unspecified),
                        colors = IconButtonDefaults.outlinedIconButtonColors(
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
    }
}

@Composable
private fun AnimatedPill(
    text: String, isHighlighted: Boolean = false
) {
    // Background – highlighted pill uses the primary colour with a light alpha,
    // normal pill uses the surface variant colour.
    val backgroundColor by animateColorAsState(
        targetValue = if (isHighlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.surfaceVariant, animationSpec = tween(300)
    )

    // Text – highlighted pill uses the primary colour,
    // normal pill uses the on‑surface‑variant colour.
    val textColor by animateColorAsState(
        targetValue = if (isHighlighted) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant, animationSpec = tween(300)
    )

    Surface(
        shape = RoundedCornerShape(rDP(16.dp)), color = backgroundColor
    ) {
        Text(
            text = text, style = MaterialTheme.typography.labelMedium.copy(
                color = textColor, fontWeight = FontWeight.SemiBold
            ), modifier = Modifier.padding(horizontal = rDP(12.dp), vertical = rDP(6.dp))
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label, style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        )
        Text(
            value, style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold
            )
        )
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
        contentPadding = PaddingValues(rDP(20.dp)),
        verticalArrangement = Arrangement.spacedBy(rDP(20.dp))
    ) {
        item {
            Text(
                "OpenRouter Configuration", style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif
                ), modifier = Modifier.padding(bottom = rDP(8.dp))
            )
        }

        // API Configuration Card
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = RoundedCornerShape(rDP(20.dp)),
                elevation = CardDefaults.cardElevation(defaultElevation = rDP(2.dp))
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(rDP(20.dp)),
                    verticalArrangement = Arrangement.spacedBy(rDP(16.dp))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.TwoTone.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(rDP(24.dp))
                        )
                        Spacer(Modifier.width(rDP(12.dp)))
                        Text(
                            "API Settings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    HorizontalDivider(modifier = Modifier.alpha(0.3f))

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
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(
                                    painterResource(
                                        if (showKey) R.drawable.show else R.drawable.hide
                                    ), "Toggle visibility"
                                )
                            }
                        },
                        visualTransformation = if (showKey) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(rDP(16.dp))
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
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(rDP(16.dp))
                    )

                    if (openRouterApiKey.isNotBlank()) {
                        Button(
                            onClick = {
                                isLoadingModels = true
                                viewModel.fetchAvailableModels()
                                isLoadingModels = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoadingModels,
                            shape = RoundedCornerShape(rDP(16.dp)),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = rDP(2.dp), pressedElevation = rDP(6.dp)
                            )
                        ) {
                            if (isLoadingModels) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(rDP(20.dp)), strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(rDP(12.dp)))
                            }
                            Text(
                                if (isLoadingModels) "Fetching..." else "Fetch Available Models",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }
        }

        // Selected Models Card
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = RoundedCornerShape(rDP(20.dp)),
                elevation = CardDefaults.cardElevation(defaultElevation = rDP(2.dp))
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(rDP(20.dp)),
                    verticalArrangement = Arrangement.spacedBy(rDP(16.dp))
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
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(rDP(24.dp))
                            )
                            Spacer(Modifier.width(rDP(12.dp)))
                            Text(
                                "Selected Models",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                overflow = TextOverflow.Clip,
                                maxLines = 1
                            )
                        }

                        AnimatedVisibility(visible = openRouterInstalled.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(rDP(20.dp)),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    "${openRouterInstalled.size}",
                                    modifier = Modifier.padding(
                                        horizontal = rDP(12.dp), vertical = rDP(6.dp)
                                    ),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.alpha(0.3f))

                    if (openRouterInstalled.isEmpty()) {
                        EmptyStateCard(
                            icon = Icons.TwoTone.CloudOff,
                            title = "No models selected",
                            subtitle = "Add models from the available list"
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(rDP(12.dp))) {
                            openRouterInstalled.forEach { modelId ->
                                OpenRouterModelItem(
                                    modelId = modelId,
                                    onDelete = { viewModel.removeModel(modelId.name) })
                            }
                        }
                    }

                    Button(
                        onClick = { showModelPicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = availableModels.isNotEmpty(),
                        shape = RoundedCornerShape(rDP(16.dp)),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = rDP(2.dp), pressedElevation = rDP(6.dp)
                        )
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(rDP(20.dp))
                        )
                        Spacer(Modifier.width(rDP(8.dp)))
                        Text(
                            "Add Model", style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
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
                viewModel.addModel(routerModel.toModelData())
                showModelPicker = false
            })
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OpenRouterModelItem(
    modelId: OpenRouterModel, onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(rDP(16.dp)))
            .padding(horizontal = rDP(16.dp), vertical = rDP(10.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            modelId.name, style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.SemiBold
            ), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )

        IconButton(
            onClick = {
                onDelete()
            },
            shape = MaterialShapes.Square.toShape(),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
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

    AlertDialog(
        onDismissRequest = onDismiss, confirmButton = {}, dismissButton = {
        TextButton(onClick = onDismiss) {
            Text("Close", fontWeight = FontWeight.Bold)
        }
    }, title = {
        Text(
            text = "Select Model", style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            )
        )
    }, text = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = rDP(520.dp)),
            verticalArrangement = Arrangement.spacedBy(rDP(12.dp))
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
                shape = RoundedCornerShape(rDP(16.dp))
            )

            HorizontalDivider(modifier = Modifier.alpha(0.3f))

            // Empty State
            if (filteredModels.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.TwoTone.SearchOff,
                    title = "No models found",
                    subtitle = if (searchQuery.isBlank()) "Try fetching models first"
                    else "Try a different keyword"
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(rDP(10.dp))
                ) {
                    items(filteredModels, key = { it.id }) { model ->
                        ModelListItem(
                            model = model, onClick = {
                                onModelSelected(model)
                                onDismiss()
                            })
                    }
                }
            }
        }
    }, shape = RoundedCornerShape(rDP(24.dp))
    )
}

@Composable
private fun ModelListItem(model: OpenRouterModel, onClick: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }

    val backgroundColor by animateColorAsState(
        if (isPressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = spring(dampingRatio = DampingRatioMediumBouncy)
    )

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f, animationSpec = spring(
            dampingRatio = DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium
        )
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(rDP(16.dp)))
            .clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null
            ) {
                isPressed = true
                onClick()
            },
        color = backgroundColor,
        tonalElevation = rDP(1.dp),
        shape = RoundedCornerShape(rDP(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDP(16.dp)),
            verticalArrangement = Arrangement.spacedBy(rDP(8.dp))
        ) {
            // Model Name Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(rDP(8.dp))
                ) {
                    if (model.supportsTools) {
                        Surface(
                            shape = RoundedCornerShape(rDP(8.dp)),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.hammer),
                                contentDescription = "Supports Tools",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier
                                    .padding(rDP(6.dp))
                                    .size(rDP(16.dp))
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add Model",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(rDP(22.dp))
                    )
                }
            }

            // Details
            Column(verticalArrangement = Arrangement.spacedBy(rDP(4.dp))) {
                Text(
                    text = "Context: ${model.ctxSize} tokens",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Text(
                    text = "Temp: ${model.temperature} • Top-P: ${model.topP}",
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
            .padding(rDP(32.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(rDP(12.dp))
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(rDP(80.dp))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(rDP(40.dp)),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Justify,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Justify,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
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
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(rDP(20.dp)),
            verticalArrangement = Arrangement.spacedBy(rDP(16.dp))
        ) {
            item {
                Text(
                    "Installed Models", style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif
                    ), modifier = Modifier.padding(bottom = rDP(8.dp))
                )
            }

            items(installed, key = { it.id }) { model ->
                InstalledModelCard(
                    model = model, onDelete = { viewModel.removeModel(model.modelName) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InstalledModelCard(
    model: ModelData, onDelete: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    val cardElevation by animateDpAsState(
        targetValue = if (isExpanded) rDP(8.dp) else rDP(2.dp),
        animationSpec = spring(dampingRatio = DampingRatioMediumBouncy)
    )

    val haptic = LocalHapticFeedback.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium
                )
            )
            .clip(RoundedCornerShape(rDP(20.dp))), colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ), elevation = CardDefaults.cardElevation(defaultElevation = cardElevation)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(rDP(20.dp)),
            verticalArrangement = Arrangement.spacedBy(rDP(16.dp))
        ) {
            // Header Row
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rDP(2.dp))
            ) {
                Text(
                    text = model.modelName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold, fontSize = rSp(20.sp)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(horizontalArrangement = Arrangement.spacedBy(rDP(8.dp))) {
                    IconButton(
                        onClick = {
                            isExpanded = !isExpanded
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        },
                        shape = MaterialShapes.Square.toShape(),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        )
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.Info,
                            contentDescription = "Model Info",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        shape = MaterialShapes.Square.toShape(),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
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


            AnimatedPill(
                text = if (model.providerName == ModelProvider.LocalGGUF.toString()) "Local Model"
                else "OpenRouter",
                isHighlighted = model.providerName == ModelProvider.LocalGGUF.toString()
            )


            // Expandable Details
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(rDP(12.dp))) {
                    HorizontalDivider(modifier = Modifier.alpha(0.3f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(rDP(8.dp))
                    ) {
                        AnimatedPill(text = "Context: ${model.ctxSize}")
                        AnimatedPill(text = "Temp: ${model.temp}")
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(rDP(8.dp))) {
                        DetailRow("Top-P", model.topP.toString())
                        DetailRow("Top-K", model.topK.toString())
                        DetailRow("Max Tokens", model.maxTokens.toString())
                        DetailRow("GPU Layers", model.gpuLayers.toString())
                        DetailRow("Threads", model.threads.toString())
                    }
                }
            }
        }
    }
}