package com.dark.tool_neuron.ui.screen.home

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import com.dark.tool_neuron.ui.theme.Motion
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.dark.tool_neuron.ui.components.ModelListItem
import com.dark.tool_neuron.viewmodel.ChatViewModel
import com.dark.tool_neuron.viewmodel.LLMModelViewModel
import com.dark.tool_neuron.ui.icons.TnIcons

enum class DynamicWindowTab {
    STATUS,
    MODELS,
    SYSTEM
}

private val WHITESPACE_REGEX = "\\s+".toRegex()

@Composable
fun DynamicActionWindow(
    chatViewModel: ChatViewModel,
    modelViewModel: LLMModelViewModel,
    loadedRagCount: Int = 0,
    enabledToolCount: Int = 0,
    isMemoryEnabled: Boolean = false,
    ttsModelLoaded: Boolean = false
) {
    val appState by AppStateManager.appState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(DynamicWindowTab.STATUS) }
    val installedModels by modelViewModel.installedModels.collectAsStateWithLifecycle(initialValue = emptyList())
    val currentModelID by modelViewModel.currentModelID.collectAsStateWithLifecycle()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = Motion.content()),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            SecondaryTabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
                indicator = @Composable {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(selectedTab.ordinal),
                        height = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                DynamicWindowTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = {
                            Text(
                                tab.name.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selectedTab == tab) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn(Motion.state()) togetherWith
                            fadeOut(Motion.state())
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
                    DynamicWindowTab.SYSTEM -> SystemTabContent(appState, modelViewModel, chatViewModel)
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
            .heightIn(max = 280.dp)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
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

        val hasActiveSubsystems = loadedRagCount > 0 || enabledToolCount > 0 || isMemoryEnabled || ttsModelLoaded
        if (hasActiveSubsystems) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                thickness = 0.5.dp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (loadedRagCount > 0) {
                    CompactBadge("RAG ($loadedRagCount)", MaterialTheme.colorScheme.primary)
                }
                if (enabledToolCount > 0) {
                    CompactBadge("Tools ($enabledToolCount)", MaterialTheme.colorScheme.tertiary)
                }
                if (isMemoryEnabled) {
                    CompactBadge("Memory", MaterialTheme.colorScheme.secondary)
                }
                if (ttsModelLoaded) {
                    CompactBadge("TTS", MaterialTheme.colorScheme.tertiary)
                }
            }
        }
    }
}

@Composable
private fun CompactBadge(text: String, color: androidx.compose.ui.graphics.Color) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .background(color, CircleShape)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Medium
            )
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
            .heightIn(max = 240.dp)
    ) {
        if (installedModels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = TnIcons.Photo,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        "No models installed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
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
                        },
                        onDeleteListener = { modelToDelete ->
                            modelViewModel.deleteModel(modelToDelete)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SystemTabContent(
    appState: AppState,
    modelViewModel: LLMModelViewModel,
    chatViewModel: ChatViewModel
) {
    val context = LocalContext.current
    val isTextModelLoaded by modelViewModel.isGgufModelLoaded.collectAsStateWithLifecycle()
    val isImageModelLoaded by modelViewModel.isDiffusionModelLoaded.collectAsStateWithLifecycle()
    val thinkingEnabled by chatViewModel.thinkingModeEnabled.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp)
            .padding(10.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Thinking Mode
        if (isTextModelLoaded) {
            SectionHeader("Generation")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = TnIcons.Brain,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (thinkingEnabled) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Thinking Mode",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Switch(
                    checked = thinkingEnabled,
                    onCheckedChange = { chatViewModel.setThinkingMode(it) },
                    modifier = Modifier.scale(0.75f)
                )
            }
        }

        // Active Models
        if (isTextModelLoaded || isImageModelLoaded) {
            SectionHeader("Active Models")
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isTextModelLoaded) {
                    CompactBadge("Text", MaterialTheme.colorScheme.primary)
                }
                if (isImageModelLoaded) {
                    CompactBadge("Image", MaterialTheme.colorScheme.tertiary)
                }
            }
        }

        // System Resources
        SectionHeader("Resources")
        SystemMetricRow(TnIcons.Cpu, "RAM", getMemoryUsage(context))
        SystemMetricRow(TnIcons.Database, "CPU Cores", getCpuCores())
        SystemMetricRow(TnIcons.Gauge, "Threads", getActiveThreads())

        // Device Info
        SectionHeader("Device")
        InfoRow("Model", Build.MODEL)
        InfoRow("Android", Build.VERSION.RELEASE)
        InfoRow("SDK", Build.VERSION.SDK_INT.toString())
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
fun ModelListItem(
    modifier: Modifier,
    model: Model,
    isLoaded: Boolean,
    onClickListener: (Model) -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = if (isLoaded) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        },
        tonalElevation = if (isLoaded) 1.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isLoaded) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Icon(
                        imageVector = if (model.providerType == com.dark.tool_neuron.models.enums.ProviderType.GGUF) TnIcons.Sparkles
                            else TnIcons.Photo,
                        contentDescription = null,
                        modifier = Modifier
                            .size(28.dp)
                            .padding(6.dp),
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
                        style = MaterialTheme.typography.bodySmall,
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
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isLoaded) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
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
                        icon = TnIcons.CornerDownLeft,
                        text = "Unload",
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(0.1f),
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(6.dp)
                    )
                } else {
                    ActionButton(
                        onClickListener = { onClickListener(model) },
                        icon = TnIcons.ExternalLink,
                        contentDescription = "Load",
                        shape = RoundedCornerShape(6.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(0.1f),
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun SystemMetricRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
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
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// State-specific content composables

@Composable
private fun WelcomeContent() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = TnIcons.User,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Column {
            Text(
                text = "Welcome",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Load a model to begin",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NoModelLoadedContent() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = TnIcons.Photo,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column {
            Text(
                text = "No Model Loaded",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Switch to Models tab to load one",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ModelLoadedContent(state: AppState.ModelLoaded) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f),
                RoundedCornerShape(8.dp)
            )
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = TnIcons.CircleCheck,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.tertiary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Model Ready",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = state.modelName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun LoadingModelContent(state: AppState.LoadingModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f),
                RoundedCornerShape(8.dp)
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                CircularProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    strokeWidth = 2.dp,
                    trackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Loading Model",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = state.modelName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = "${(state.progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        LinearProgressIndicator(
            progress = { state.progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
        )
    }
}

@Composable
private fun GeneratingTextContent(state: AppState.GeneratingText, chatViewModel: ChatViewModel) {
    val streamingText by chatViewModel.streamingAssistantMessage.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                    imageVector = TnIcons.Wrench,
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(rotation),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = "Generating",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = state.modelName,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (streamingText.isNotEmpty()) {
                Text(
                    text = "${streamingText.split(WHITESPACE_REGEX).size}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp)),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun GeneratingImageContent(state: AppState.GeneratingImage, chatViewModel: ChatViewModel) {
    val streamingImage by chatViewModel.streamingImage.collectAsStateWithLifecycle()
    val progress by chatViewModel.imageGenerationProgress.collectAsStateWithLifecycle()
    val step by chatViewModel.imageGenerationStep.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                    imageVector = TnIcons.Wrench,
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .scale(scale),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = step.ifEmpty { "Creating" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = state.modelName,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )
        streamingImage?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Preview",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
            )
        }
    }
}

@Composable
private fun GeneratingAudioContent() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AudioWaveAnimation(MaterialTheme.colorScheme.primary)
        Text(
            text = "Generating Audio",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp)),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ExecutingPluginContent(state: AppState.ExecutingPlugin) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f),
                RoundedCornerShape(8.dp)
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                    imageVector = TnIcons.Wrench,
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(rotation),
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Executing Tool",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = state.toolName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            CompactBadge(state.pluginName, MaterialTheme.colorScheme.tertiary)
        }
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp)),
            color = MaterialTheme.colorScheme.tertiary,
            trackColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
        )
    }
}

@Composable
private fun AudioWaveAnimation(color: androidx.compose.ui.graphics.Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "audio_wave")
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(20.dp)
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
                    .width(2.dp)
                    .fillMaxHeight(height)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun PluginExecutionCompleteContent(state: AppState.PluginExecutionComplete) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (state.success) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)
                else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                RoundedCornerShape(8.dp)
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (state.success) TnIcons.CircleCheck else TnIcons.AlertTriangle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (state.success) MaterialTheme.colorScheme.tertiary
                           else MaterialTheme.colorScheme.error
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (state.success) "Tool Completed" else "Tool Failed",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (state.success) MaterialTheme.colorScheme.tertiary
                               else MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = state.toolName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = "${state.executionTimeMs}ms",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (state.success) MaterialTheme.colorScheme.tertiary
                       else MaterialTheme.colorScheme.error
            )
        }

        if (!state.success && state.errorMessage != null) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                thickness = 0.5.dp
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = TnIcons.AlertTriangle,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
                Text(
                    text = state.errorMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (state.success) {
            CompactBadge(state.pluginName, MaterialTheme.colorScheme.tertiary)
        }
    }
}

@Composable
private fun ErrorContent(state: AppState.Error) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                RoundedCornerShape(8.dp)
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = TnIcons.AlertTriangle,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Error",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
                state.modelName?.let { model ->
                    Text(
                        text = model,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
            thickness = 0.5.dp
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = TnIcons.AlertTriangle,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
            )
            Text(
                text = state.message,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
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
private fun getCpuCores(): String = remember {
    try {
        val text = java.io.File("/sys/devices/system/cpu/present").readText().trim()
        val parts = text.split("-")
        if (parts.size == 2) "${parts[1].toInt() + 1}" else "${Runtime.getRuntime().availableProcessors()}"
    } catch (_: Exception) {
        "${Runtime.getRuntime().availableProcessors()}"
    }
}

@Composable
private fun getActiveThreads(): String = "${Thread.activeCount()}"
