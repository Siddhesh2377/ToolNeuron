package com.dark.tool_neuron.ui.screen.home_screen

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.R
import com.dark.tool_neuron.models.state.AppState
import com.dark.tool_neuron.models.state.getBackgroundColor
import com.dark.tool_neuron.models.state.getColor
import com.dark.tool_neuron.models.state.getContentColor
import com.dark.tool_neuron.models.table_schema.Model
import com.dark.tool_neuron.state.AppStateManager
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.ActionTextButton
import com.dark.tool_neuron.ui.theme.rDp
import com.dark.tool_neuron.viewmodel.ChatViewModel
import com.dark.tool_neuron.viewmodel.LLMModelViewModel

enum class DynamicWindowTab {
    STATUS,
    MODELS,
    SYSTEM
}

@Composable
fun DynamicActionWindow(
    chatViewModel: ChatViewModel,
    modelViewModel: LLMModelViewModel,
    loadedRagCount: Int = 0,
    enabledToolCount: Int = 0,
    isMemoryEnabled: Boolean = false,
    ttsModelLoaded: Boolean = false
) {
    val appState by AppStateManager.appState.collectAsState()
    var selectedTab by remember { mutableStateOf(DynamicWindowTab.STATUS) }
    val installedModels by modelViewModel.installedModels.collectAsState(initial = emptyList())
    val currentModelID by modelViewModel.currentModelID.collectAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ),
        elevation = CardDefaults.cardElevation(rDp(4.dp)),
        colors = CardDefaults.cardColors(
            containerColor = appState.getBackgroundColor()
        ),
        shape = RoundedCornerShape(rDp(16.dp))
    ) {
        Column {
            // Tab Row
            SecondaryTabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = appState.getBackgroundColor(),
                contentColor = appState.getContentColor(),
                indicator = @Composable {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(selectedTab.ordinal),
                        height = rDp(3.dp),
                        color = appState.getColor()
                    )
                }
            ){
                Tab(
                    selected = selectedTab == DynamicWindowTab.STATUS,
                    onClick = { selectedTab = DynamicWindowTab.STATUS },
                    text = {
                        Text(
                            "Status",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selectedTab == DynamicWindowTab.STATUS) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = selectedTab == DynamicWindowTab.MODELS,
                    onClick = { selectedTab = DynamicWindowTab.MODELS },
                    text = {
                        Text(
                            "Models",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selectedTab == DynamicWindowTab.MODELS) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = selectedTab == DynamicWindowTab.SYSTEM,
                    onClick = { selectedTab = DynamicWindowTab.SYSTEM },
                    text = {
                        Text(
                            "System",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selectedTab == DynamicWindowTab.SYSTEM) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                )
            }

            // Content
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith
                            fadeOut(animationSpec = tween(300))
                },
                label = "tab_content"
            ) { tab ->
                when (tab) {
                    DynamicWindowTab.STATUS -> StatusTabContent(
                        appState = appState,
                        chatViewModel = chatViewModel,
                        loadedRagCount = loadedRagCount,
                        enabledToolCount = enabledToolCount,
                        isMemoryEnabled = isMemoryEnabled,
                        ttsModelLoaded = ttsModelLoaded
                    )
                    DynamicWindowTab.MODELS -> ModelsTabContent(
                        installedModels,
                        currentModelID,
                        modelViewModel,
                        chatViewModel
                    )
                    DynamicWindowTab.SYSTEM -> SystemTabContent(appState, modelViewModel)
                }
            }
        }
    }
}

@Composable
private fun StatusTabContent(
    appState: AppState,
    chatViewModel: ChatViewModel,
    loadedRagCount: Int = 0,
    enabledToolCount: Int = 0,
    isMemoryEnabled: Boolean = false,
    ttsModelLoaded: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = rDp(120.dp), max = rDp(350.dp))
            .padding(rDp(16.dp)),
        verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
    ) {
        when (appState) {
            is AppState.Welcome -> WelcomeContent()
            is AppState.NoModelLoaded -> NoModelLoadedContent()
            is AppState.ModelLoaded -> ModelLoadedContent(appState)
            is AppState.LoadingModel -> LoadingModelContent(appState)
            is AppState.GeneratingText -> GeneratingTextContent(appState, chatViewModel)
            is AppState.GeneratingImage -> GeneratingImageContent(appState, chatViewModel)
            is AppState.GeneratingAudio -> GeneratingAudioContent()
            is AppState.ExecutingPlugin -> ExecutingPluginContent(appState)
            is AppState.PluginExecutionComplete -> PluginExecutionCompleteContent(appState)
            is AppState.Error -> ErrorContent(appState)
        }

        // Active Subsystems section
        val hasActiveSubsystems = loadedRagCount > 0 || enabledToolCount > 0 || isMemoryEnabled || ttsModelLoaded
        if (hasActiveSubsystems) {
            com.dark.tool_neuron.ui.components.SectionDivider(label = "Active Subsystems")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
            ) {
                if (loadedRagCount > 0) {
                    com.dark.tool_neuron.ui.components.StatusBadge(
                        text = "RAG ($loadedRagCount)",
                        isActive = true,
                        activeColor = MaterialTheme.colorScheme.primary
                    )
                }
                if (enabledToolCount > 0) {
                    com.dark.tool_neuron.ui.components.StatusBadge(
                        text = "Tools ($enabledToolCount)",
                        isActive = true,
                        activeColor = MaterialTheme.colorScheme.tertiary
                    )
                }
                if (isMemoryEnabled) {
                    com.dark.tool_neuron.ui.components.StatusBadge(
                        text = "Memory",
                        isActive = true,
                        activeColor = MaterialTheme.colorScheme.secondary
                    )
                }
                if (ttsModelLoaded) {
                    com.dark.tool_neuron.ui.components.StatusBadge(
                        text = "TTS",
                        isActive = true,
                        activeColor = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelsTabContent(
    installedModels: List<Model>,
    currentModelID: String,
    modelViewModel: LLMModelViewModel,
    chatViewModel: ChatViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = rDp(120.dp), max = rDp(300.dp))
    ) {
        if (installedModels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(rDp(32.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(rDp(8.dp))
                ) {
                    Icon(
                        painter = painterResource(R.drawable.vl_models),
                        contentDescription = null,
                        modifier = Modifier.size(rDp(32.dp)),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        "No models installed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(rDp(12.dp)),
                verticalArrangement = Arrangement.spacedBy(rDp(8.dp))
            ) {
                items(installedModels, key = { it.id }) { model ->
                    ModelListItem(
                        modifier = Modifier.fillMaxWidth(),
                        model = model,
                        isLoaded = currentModelID == model.id,
                        onClickListener = { selectedModel ->
                            if (currentModelID == model.id) {
                                modelViewModel.unloadModel()
                            } else {
                                modelViewModel.loadModel(selectedModel)
                            }
                            chatViewModel.hideDynamicWindow()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SystemTabContent(appState: AppState, modelViewModel: LLMModelViewModel) {
    val context = LocalContext.current
    val isTextModelLoaded by modelViewModel.isGgufModelLoaded.collectAsState()
    val isImageModelLoaded by modelViewModel.isDiffusionModelLoaded.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = rDp(120.dp), max = rDp(300.dp))
            .padding(rDp(16.dp))
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(rDp(16.dp))
    ) {
        // Loaded Models
        if (isTextModelLoaded || isImageModelLoaded) {
            Column(verticalArrangement = Arrangement.spacedBy(rDp(8.dp))) {
                Text(
                    text = "Active Models",
                    style = MaterialTheme.typography.labelMedium,
                    color = appState.getColor(),
                    fontWeight = FontWeight.SemiBold
                )

                if (isTextModelLoaded) {
                    ModelStatusChip(label = "Text", color = MaterialTheme.colorScheme.primary)
                }
                if (isImageModelLoaded) {
                    ModelStatusChip(label = "Image", color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }

        // System Resources
        Column(verticalArrangement = Arrangement.spacedBy(rDp(8.dp))) {
            Text(
                text = "Resources",
                style = MaterialTheme.typography.labelMedium,
                color = appState.getColor(),
                fontWeight = FontWeight.SemiBold
            )

            SystemMetricRow(icon = Icons.Default.Memory, label = "RAM", value = getMemoryUsage(context))
            SystemMetricRow(icon = Icons.Default.Storage, label = "CPU Cores", value = getCpuCores())
            SystemMetricRow(icon = Icons.Default.Speed, label = "Threads", value = getActiveThreads())
        }

        // Device Info
        Column(verticalArrangement = Arrangement.spacedBy(rDp(8.dp))) {
            Text(
                text = "Device",
                style = MaterialTheme.typography.labelMedium,
                color = appState.getColor(),
                fontWeight = FontWeight.SemiBold
            )

            InfoRow("Model", Build.MODEL)
            InfoRow("Android", Build.VERSION.RELEASE)
            InfoRow("SDK", Build.VERSION.SDK_INT.toString())
        }
    }
}

@Composable
private fun ModelListItem(
    modifier: Modifier,
    model: Model,
    isLoaded: Boolean,
    onClickListener: (Model) -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(rDp(10.dp)),
        color = if (isLoaded) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        },
        tonalElevation = if (isLoaded) rDp(2.dp) else rDp(1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDp(12.dp)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(rDp(12.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(rDp(8.dp)),
                    color = if (isLoaded) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Icon(
                        painter = painterResource(
                            if (model.providerType.name == "GGUF") R.drawable.smart_temp_message
                            else R.drawable.vl_models
                        ),
                        contentDescription = null,
                        modifier = Modifier
                            .size(rDp(36.dp))
                            .padding(rDp(8.dp)),
                        tint = if (isLoaded) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.modelName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isLoaded) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isLoaded) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(rDp(6.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isLoaded) {
                            Box(
                                modifier = Modifier
                                    .size(rDp(6.dp))
                                    .background(
                                        MaterialTheme.colorScheme.primary,
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    )
                            )
                        }
                        Text(
                            text = model.providerType.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = if (isLoaded) 0.8f else 0.6f
                            )
                        )
                    }
                }
            }

            Crossfade(targetState = isLoaded, label = "button_state") { loaded ->
                if (loaded) {
                    ActionTextButton(
                        onClickListener = { onClickListener(model) },
                        icon = Icons.Default.SubdirectoryArrowLeft,
                        text = "Unload",
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(0.12f),
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(rDp(8.dp))
                    )
                } else {
                    ActionButton(
                        onClickListener = { onClickListener(model) },
                        icon = Icons.Default.ArrowOutward,
                        contentDescription = "Load",
                        shape = RoundedCornerShape(rDp(8.dp)),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(0.12f),
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelStatusChip(label: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(rDp(8.dp)))
            .padding(horizontal = rDp(12.dp), vertical = rDp(6.dp)),
        horizontalArrangement = Arrangement.spacedBy(rDp(6.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(rDp(6.dp))
                .background(color, shape = androidx.compose.foundation.shape.CircleShape)
        )
        Text(
            text = "$label Model",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

@Composable
private fun SystemMetricRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(rDp(8.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(rDp(16.dp)),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

// State-specific content composables (compact versions)

@Composable
private fun WelcomeContent() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(rDp(12.dp)),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        tonalElevation = rDp(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDp(24.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
        ) {
            Icon(
                painter = painterResource(R.drawable.user),
                contentDescription = null,
                modifier = Modifier.size(rDp(32.dp)),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Welcome",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Load a model to begin",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun NoModelLoadedContent() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(rDp(12.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = rDp(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDp(24.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
        ) {
            Icon(
                painter = painterResource(R.drawable.vl_models),
                contentDescription = null,
                modifier = Modifier.size(rDp(32.dp)),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "No Model Loaded",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Switch to Models tab to load a model",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}


@Composable
private fun ModelLoadedContent(state: AppState.ModelLoaded) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(rDp(12.dp)),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
        tonalElevation = rDp(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDp(16.dp)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(rDp(12.dp)),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    painter = painterResource(R.drawable.smart_temp_message),
                    contentDescription = null,
                    modifier = Modifier.size(rDp(24.dp)),
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Model Ready",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = state.modelName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Ready",
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(rDp(20.dp))
            )
        }
    }
}

@Composable
private fun LoadingModelContent(state: AppState.LoadingModel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(rDp(12.dp)),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
        tonalElevation = rDp(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(rDp(16.dp)),
            verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(rDp(12.dp)),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    CircularProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.size(rDp(24.dp)),
                        color = MaterialTheme.colorScheme.secondary,
                        strokeWidth = rDp(3.dp),
                        trackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Loading Model",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = state.modelName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(rDp(6.dp)),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "${(state.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(horizontal = rDp(8.dp), vertical = rDp(4.dp))
                    )
                }
            }

            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rDp(4.dp))
                    .clip(RoundedCornerShape(rDp(2.dp))),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
            )
        }
    }
}

@Composable
private fun GeneratingTextContent(state: AppState.GeneratingText, chatViewModel: ChatViewModel) {
    val appState by AppStateManager.appState.collectAsState()
    val streamingText by chatViewModel.streamingAssistantMessage.collectAsState()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(rDp(12.dp)),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "generating")
                val rotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "rotation"
                )
                Icon(
                    painter = painterResource(R.drawable.tool),
                    contentDescription = null,
                    modifier = Modifier
                        .size(rDp(28.dp))
                        .rotate(rotation),
                    tint = appState.getColor()
                )
                Column {
                    Text(
                        text = "Generating",
                        style = MaterialTheme.typography.labelSmall,
                        color = appState.getContentColor().copy(alpha = 0.7f)
                    )
                    Text(
                        text = state.modelName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = appState.getContentColor(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (streamingText.isNotEmpty()) {
                Text(
                    text = "${streamingText.split("\\s+".toRegex()).size}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = appState.getColor()
                )
            }
        }
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(rDp(3.dp))
                .clip(RoundedCornerShape(rDp(2.dp))),
            color = appState.getColor()
        )
    }
}

@Composable
private fun GeneratingImageContent(state: AppState.GeneratingImage, chatViewModel: ChatViewModel) {
    val appState by AppStateManager.appState.collectAsState()
    val streamingImage by chatViewModel.streamingImage.collectAsState()
    val progress by chatViewModel.imageGenerationProgress.collectAsState()
    val step by chatViewModel.imageGenerationStep.collectAsState()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(rDp(12.dp)),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "generating_image")
                val scale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "scale"
                )
                Icon(
                    painter = painterResource(R.drawable.tool),
                    contentDescription = null,
                    modifier = Modifier
                        .size(rDp(28.dp))
                        .scale(scale),
                    tint = appState.getColor()
                )
                Column {
                    Text(
                        text = step.ifEmpty { "Creating" },
                        style = MaterialTheme.typography.labelSmall,
                        color = appState.getContentColor().copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = state.modelName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = appState.getContentColor(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = appState.getColor()
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(rDp(4.dp))
                .clip(RoundedCornerShape(rDp(2.dp))),
            color = appState.getColor(),
            trackColor = appState.getColor().copy(alpha = 0.2f)
        )
        streamingImage?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Preview",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(rDp(10.dp)))
            )
        }
    }
}

@Composable
private fun GeneratingAudioContent() {
    val appState by AppStateManager.appState.collectAsState()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
    ) {
        AudioWaveAnimation(appState.getColor())
        Text(
            text = "Generating Audio",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = appState.getContentColor()
        )
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(rDp(3.dp))
                .clip(RoundedCornerShape(rDp(2.dp))),
            color = appState.getColor()
        )
    }
}

@Composable
private fun ExecutingPluginContent(state: AppState.ExecutingPlugin) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(rDp(12.dp)),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
        tonalElevation = rDp(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(rDp(16.dp)),
            verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(rDp(12.dp)),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "executing_plugin")
                    val rotation by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1500, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "rotation"
                    )
                    Icon(
                        painter = painterResource(R.drawable.tool),
                        contentDescription = null,
                        modifier = Modifier
                            .size(rDp(24.dp))
                            .rotate(rotation),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Executing Tool",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = state.toolName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(rDp(6.dp)),
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = state.pluginName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(horizontal = rDp(8.dp), vertical = rDp(4.dp))
                    )
                }
            }

            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rDp(4.dp))
                    .clip(RoundedCornerShape(rDp(2.dp))),
                color = MaterialTheme.colorScheme.tertiary,
                trackColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
            )
        }
    }
}

@Composable
private fun AudioWaveAnimation(color: androidx.compose.ui.graphics.Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "audio_wave")
    Row(
        horizontalArrangement = Arrangement.spacedBy(rDp(4.dp)),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(rDp(28.dp))
    ) {
        repeat(5) { index ->
            val height by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500, delayMillis = index * 80, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "wave_$index"
            )
            Box(
                modifier = Modifier
                    .width(rDp(3.dp))
                    .fillMaxHeight(height)
                    .clip(RoundedCornerShape(rDp(2.dp)))
                    .background(color)
            )
        }
    }
}

@Composable
private fun PluginExecutionCompleteContent(state: AppState.PluginExecutionComplete) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(rDp(12.dp)),
        color = if (state.success) {
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        },
        tonalElevation = rDp(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(rDp(16.dp)),
            verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(rDp(12.dp)),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (state.success) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        modifier = Modifier.size(rDp(24.dp)),
                        tint = if (state.success) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (state.success) "Tool Completed" else "Tool Failed",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (state.success) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                        Text(
                            text = state.toolName,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.success) {
                                MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(rDp(6.dp)),
                    color = if (state.success) {
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                    } else {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                    }
                ) {
                    Text(
                        text = "${state.executionTimeMs}ms",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (state.success) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.padding(horizontal = rDp(8.dp), vertical = rDp(4.dp))
                    )
                }
            }

            if (!state.success && state.errorMessage != null) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(rDp(8.dp)),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        painter = painterResource(R.drawable.error),
                        contentDescription = null,
                        modifier = Modifier.size(rDp(16.dp)),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                    Text(
                        text = state.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (state.success) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(rDp(6.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(rDp(6.dp))
                            .background(
                                MaterialTheme.colorScheme.tertiary,
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                    Text(
                        text = state.pluginName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorContent(state: AppState.Error) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(rDp(12.dp)),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
        tonalElevation = rDp(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(rDp(16.dp)),
            verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(rDp(12.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    modifier = Modifier.size(rDp(24.dp)),
                    tint = MaterialTheme.colorScheme.error
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Error",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                    state.modelName?.let { model ->
                        Text(
                            text = model,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(rDp(8.dp)),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    painter = painterResource(R.drawable.error),
                    contentDescription = null,
                    modifier = Modifier.size(rDp(16.dp)),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// System info helpers
@Composable
private fun getMemoryUsage(context: Context): String {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memoryInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memoryInfo)
    val usedMemory = (memoryInfo.totalMem - memoryInfo.availMem) / (1024 * 1024)
    val totalMemory = memoryInfo.totalMem / (1024 * 1024)
    return "${usedMemory}MB / ${totalMemory}MB"
}

@Composable
private fun getCpuCores(): String = "${Runtime.getRuntime().availableProcessors()}"

@Composable
private fun getActiveThreads(): String = "${Thread.activeCount()}"