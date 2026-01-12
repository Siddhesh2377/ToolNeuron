package com.dark.tool_neuron.ui.screen.home_screen

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.R
import com.dark.tool_neuron.models.state.AppState
import com.dark.tool_neuron.models.state.getBackgroundColor
import com.dark.tool_neuron.models.state.getColor
import com.dark.tool_neuron.models.state.getContentColor
import com.dark.tool_neuron.state.AppStateManager
import com.dark.tool_neuron.ui.theme.rDp
import com.dark.tool_neuron.viewmodel.ChatViewModel
import kotlinx.coroutines.delay

enum class InfoTab {
    STATE, SYSTEM
}

@Composable
fun DynamicActionWindow(chatViewModel: ChatViewModel) {
    val appState by AppStateManager.appState.collectAsState()
    var selectedTab by remember { mutableStateOf(InfoTab.STATE) }
    var isExpanded by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
        elevation = CardDefaults.cardElevation(rDp(8.dp)),
        colors = CardDefaults.cardColors(
            containerColor = appState.getBackgroundColor()
        )
    ) {
        Column {
            // Header with tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rDp(16.dp), vertical = rDp(12.dp)),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Status Monitor",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = appState.getContentColor()
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(rDp(8.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mini tab toggle
                    Surface(
                        shape = RoundedCornerShape(rDp(20.dp)),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(modifier = Modifier.padding(rDp(4.dp))) {
                            MiniTab(
                                selected = selectedTab == InfoTab.STATE,
                                onClick = { selectedTab = InfoTab.STATE },
                                icon = Icons.Default.Info,
                                label = "State"
                            )
                            MiniTab(
                                selected = selectedTab == InfoTab.SYSTEM,
                                onClick = { selectedTab = InfoTab.SYSTEM },
                                icon = Icons.Default.Memory,
                                label = "System"
                            )
                        }
                    }

                    // Expand/Collapse button
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(rDp(32.dp))
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = appState.getContentColor()
                        )
                    }
                }
            }

            // Divider
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = rDp(16.dp)),
                color = appState.getContentColor().copy(alpha = 0.1f)
            )

            // Content
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Crossfade(
                    targetState = selectedTab,
                    animationSpec = tween(300),
                    label = "tab_transition"
                ) { tab ->
                    when (tab) {
                        InfoTab.STATE -> StateContent(appState, chatViewModel)
                        InfoTab.SYSTEM -> SystemContent(appState)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniTab(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        else
            MaterialTheme.colorScheme.surface.copy(alpha = 0.0f),
        label = "tab_bg"
    )

    val contentColor by animateColorAsState(
        targetValue = if (selected)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.onSurfaceVariant,
        label = "tab_content"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(rDp(16.dp)),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = rDp(12.dp), vertical = rDp(6.dp)),
            horizontalArrangement = Arrangement.spacedBy(rDp(6.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(rDp(16.dp)),
                tint = contentColor
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun StateContent(appState: AppState, chatViewModel: ChatViewModel) {
    Crossfade(
        targetState = appState,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "state_transition"
    ) { state ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDp(16.dp))
        ) {
            when (state) {
                is AppState.Welcome -> WelcomeContent(state)
                is AppState.NoModelLoaded -> NoModelLoadedContent(state, chatViewModel)
                is AppState.ModelLoaded -> ModelLoadedContent(state)
                is AppState.LoadingModel -> LoadingModelContent(state)
                is AppState.GeneratingText -> GeneratingTextContent(state, chatViewModel)
                is AppState.GeneratingImage -> GeneratingImageContent(state, chatViewModel)
                is AppState.GeneratingAudio -> GeneratingAudioContent(state)
                is AppState.Error -> ErrorContent(state)
            }
        }
    }
}

@Composable
private fun SystemContent(appState: AppState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(rDp(16.dp))
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
    ) {
        // System metrics
        SystemMetricRow(
            icon = Icons.Default.Memory,
            label = "Memory",
            value = getMemoryUsage(),
            color = appState.getColor()
        )

        SystemMetricRow(
            icon = Icons.Default.Storage,
            label = "CPU",
            value = getCpuUsage(),
            color = appState.getColor()
        )

        SystemMetricRow(
            icon = Icons.Default.Speed,
            label = "GPU",
            value = getGpuInfo(),
            color = appState.getColor()
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = rDp(4.dp)))

        // Runtime info
        InfoRow("Model Backend", getBackendInfo())
        InfoRow("Threads", getThreadCount())
        InfoRow("Inference Mode", getInferenceMode())
    }
}

@Composable
private fun SystemMetricRow(
    icon: ImageVector,
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color
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
                modifier = Modifier.size(rDp(20.dp)),
                tint = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
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
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

// State-specific content composables

@Composable
private fun WelcomeContent(state: AppState.Welcome) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
    ) {
        Icon(
            painter = painterResource(R.drawable.user),
            contentDescription = null,
            modifier = Modifier.size(rDp(40.dp)),
            tint = AppStateManager.appState.collectAsState().value.getColor()
        )
        Text(
            text = "Welcome to Tool Neuron",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = AppStateManager.appState.collectAsState().value.getContentColor()
        )
        Text(
            text = "Load a model to begin",
            style = MaterialTheme.typography.bodyMedium,
            color = AppStateManager.appState.collectAsState().value.getContentColor().copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun NoModelLoadedContent(state: AppState.NoModelLoaded, chatViewModel: ChatViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
    ) {
        Icon(
            painter = painterResource(R.drawable.vl_models),
            contentDescription = null,
            modifier = Modifier.size(rDp(40.dp)),
            tint = AppStateManager.appState.collectAsState().value.getColor()
        )
        Text(
            text = "No Model Loaded",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = AppStateManager.appState.collectAsState().value.getContentColor()
        )
        Text(
            text = "Select a model to continue your conversation",
            style = MaterialTheme.typography.bodySmall,
            color = AppStateManager.appState.collectAsState().value.getContentColor().copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        FilledTonalButton(
            onClick = { chatViewModel.showModelList() },
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = AppStateManager.appState.collectAsState().value.getColor().copy(alpha = 0.15f),
                contentColor = AppStateManager.appState.collectAsState().value.getColor()
            )
        ) {
            Icon(Icons.Default.ModelTraining, contentDescription = null)
            Spacer(modifier = Modifier.width(rDp(8.dp)))
            Text("Select Model")
        }
    }
}

@Composable
private fun ModelLoadedContent(state: AppState.ModelLoaded) {
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
                horizontalArrangement = Arrangement.spacedBy(rDp(8.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.smart_temp_message),
                    contentDescription = null,
                    modifier = Modifier.size(rDp(28.dp)),
                    tint = AppStateManager.appState.collectAsState().value.getColor()
                )
                Column {
                    Text(
                        text = "Model Ready",
                        style = MaterialTheme.typography.labelMedium,
                        color = AppStateManager.appState.collectAsState().value.getContentColor().copy(alpha = 0.7f)
                    )
                    Text(
                        text = state.modelName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = AppStateManager.appState.collectAsState().value.getColor()
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Ready",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(rDp(24.dp))
            )
        }
    }
}

@Composable
private fun LoadingModelContent(state: AppState.LoadingModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(rDp(12.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.size(rDp(40.dp)),
                color = AppStateManager.appState.collectAsState().value.getColor(),
                strokeWidth = rDp(4.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Loading Model",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = AppStateManager.appState.collectAsState().value.getContentColor()
                )
                Text(
                    text = state.modelName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppStateManager.appState.collectAsState().value.getContentColor().copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = "${(state.progress * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = AppStateManager.appState.collectAsState().value.getColor()
            )
        }

        LinearProgressIndicator(
            progress = { state.progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(rDp(8.dp))
                .clip(RoundedCornerShape(rDp(4.dp))),
            color = AppStateManager.appState.collectAsState().value.getColor(),
            trackColor = AppStateManager.appState.collectAsState().value.getColor().copy(alpha = 0.2f)
        )

        // Loading stages
        LoadingStageIndicator(state.progress)
    }
}

@Composable
private fun LoadingStageIndicator(progress: Float) {
    val stages = listOf(
        "Initializing" to 0.0f..0.2f,
        "Loading weights" to 0.2f..0.6f,
        "Optimizing" to 0.6f..0.9f,
        "Finalizing" to 0.9f..1.0f
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        stages.forEach { (stage, range) ->
            val isActive = progress in range
            val isComplete = progress > range.endInclusive

            Row(
                horizontalArrangement = Arrangement.spacedBy(rDp(4.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when {
                        isComplete -> Icons.Default.CheckCircle
                        isActive -> Icons.Default.Schedule
                        else -> Icons.Default.Circle
                    },
                    contentDescription = null,
                    modifier = Modifier.size(rDp(12.dp)),
                    tint = when {
                        isComplete -> MaterialTheme.colorScheme.primary
                        isActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    }
                )
                Text(
                    text = stage,
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        isActive || isComplete -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    }
                )
            }
        }
    }
}

@Composable
private fun GeneratingTextContent(state: AppState.GeneratingText, chatViewModel: ChatViewModel) {
    val streamingText by chatViewModel.streamingAssistantMessage.collectAsState()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(rDp(12.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "generating")
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "rotation"
            )

            Icon(
                painter = painterResource(R.drawable.tool),
                contentDescription = null,
                modifier = Modifier
                    .size(rDp(40.dp))
                    .rotate(rotation),
                tint = AppStateManager.appState.collectAsState().value.getColor()
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Generating Text",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = AppStateManager.appState.collectAsState().value.getContentColor()
                )
                Text(
                    text = state.modelName,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppStateManager.appState.collectAsState().value.getContentColor().copy(alpha = 0.7f)
                )
            }
        }

        // Text preview with smooth animation
        if (streamingText.isNotEmpty()) {
            AnimatedContent(
                targetState = streamingText,
                transitionSpec = {
                    fadeIn(animationSpec = tween(200)) togetherWith
                            fadeOut(animationSpec = tween(200))
                },
                label = "text_preview"
            ) { text ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(rDp(8.dp)),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(modifier = Modifier.padding(rDp(12.dp))) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Preview",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Token count
                            val tokenCount = text.split("\\s+".toRegex()).size
                            Text(
                                text = "$tokenCount tokens",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppStateManager.appState.collectAsState().value.getColor()
                            )
                        }

                        Spacer(modifier = Modifier.height(rDp(8.dp)))

                        // Scrollable text preview
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = rDp(120.dp))
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = MaterialTheme.typography.bodySmall.lineHeight
                            )
                        }
                    }
                }
            }
        }

        // Animated progress indicator
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(rDp(4.dp))
                .clip(RoundedCornerShape(rDp(2.dp))),
            color = AppStateManager.appState.collectAsState().value.getColor()
        )
    }
}

@Composable
private fun GeneratingImageContent(state: AppState.GeneratingImage, chatViewModel: ChatViewModel) {
    val streamingImage by chatViewModel.streamingImage.collectAsState()
    val progress by chatViewModel.imageGenerationProgress.collectAsState()
    val step by chatViewModel.imageGenerationStep.collectAsState()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(rDp(12.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "generating_image")
            val scale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scale"
            )

            Icon(
                painter = painterResource(R.drawable.tool),
                contentDescription = null,
                modifier = Modifier
                    .size(rDp(40.dp))
                    .scale(scale),
                tint = AppStateManager.appState.collectAsState().value.getColor()
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Creating Image",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = AppStateManager.appState.collectAsState().value.getContentColor()
                )
                Text(
                    text = state.modelName,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppStateManager.appState.collectAsState().value.getContentColor().copy(alpha = 0.7f)
                )
            }

            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = AppStateManager.appState.collectAsState().value.getColor()
            )
        }

        // Step info
        if (step.isNotEmpty()) {
            Text(
                text = step,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Progress bar
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(rDp(8.dp))
                .clip(RoundedCornerShape(rDp(4.dp))),
            color = AppStateManager.appState.collectAsState().value.getColor(),
            trackColor = AppStateManager.appState.collectAsState().value.getColor().copy(alpha = 0.2f)
        )

        // Image preview
        streamingImage?.let { bitmap ->
            AnimatedContent(
                targetState = bitmap,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith
                            fadeOut(animationSpec = tween(300))
                },
                label = "image_preview"
            ) { image ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(rDp(12.dp)),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ) {
                    Column(modifier = Modifier.padding(rDp(12.dp))) {
                        Text(
                            text = "Preview",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = rDp(8.dp))
                        )

                        Image(
                            bitmap = image.asImageBitmap(),
                            contentDescription = "Generated image preview",
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(rDp(8.dp)))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GeneratingAudioContent(state: AppState.GeneratingAudio) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
    ) {
        // Audio wave animation
        AudioWaveAnimation()

        Text(
            text = "Generating Audio",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = AppStateManager.appState.collectAsState().value.getContentColor()
        )
        Text(
            text = state.modelName,
            style = MaterialTheme.typography.bodyMedium,
            color = AppStateManager.appState.collectAsState().value.getContentColor().copy(alpha = 0.7f)
        )

        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(rDp(4.dp))
                .clip(RoundedCornerShape(rDp(2.dp))),
            color = AppStateManager.appState.collectAsState().value.getColor()
        )
    }
}

@Composable
private fun AudioWaveAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "audio_wave")

    Row(
        horizontalArrangement = Arrangement.spacedBy(rDp(4.dp)),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(rDp(40.dp))
    ) {
        repeat(5) { index ->
            val height by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 600,
                        delayMillis = index * 100,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "wave_$index"
            )

            Box(
                modifier = Modifier
                    .width(rDp(6.dp))
                    .fillMaxHeight(height)
                    .clip(RoundedCornerShape(rDp(3.dp)))
                    .background(AppStateManager.appState.collectAsState().value.getColor())
            )
        }
    }
}

@Composable
private fun ErrorContent(state: AppState.Error) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(rDp(12.dp)),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                painter = painterResource(R.drawable.error),
                contentDescription = null,
                modifier = Modifier.size(rDp(40.dp)),
                tint = MaterialTheme.colorScheme.error
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Error Occurred",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )

                Spacer(modifier = Modifier.height(rDp(4.dp)))

                Surface(
                    shape = RoundedCornerShape(rDp(8.dp)),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(rDp(12.dp))
                    )
                }

                state.modelName?.let { modelName ->
                    Spacer(modifier = Modifier.height(rDp(8.dp)))
                    Text(
                        text = "Model: $modelName",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// System info helper functions
@Composable
private fun getMemoryUsage(): String {
    val runtime = Runtime.getRuntime()
    val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1048576
    val maxMemory = runtime.maxMemory() / 1048576
    return "$usedMemory / ${maxMemory}MB"
}

@Composable
private fun getCpuUsage(): String {
    return "${Runtime.getRuntime().availableProcessors()} cores"
}

@Composable
private fun getGpuInfo(): String {
    return "Available" // Replace with actual GPU detection
}

@Composable
private fun getBackendInfo(): String {
    return "GGUF + Diffusion"
}

@Composable
private fun getThreadCount(): String {
    return "${Thread.activeCount()}"
}

@Composable
private fun getInferenceMode(): String {
    return "CPU/GPU Hybrid"
}