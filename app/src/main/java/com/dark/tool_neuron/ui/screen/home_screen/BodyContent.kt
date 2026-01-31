package com.dark.tool_neuron.ui.screen.home_screen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dark.tool_neuron.R
import com.dark.tool_neuron.models.ui.ActionIcon
import com.dark.tool_neuron.models.ui.ActionItem
import com.dark.tool_neuron.ui.components.MemoryResultsDisplay
import com.dark.tool_neuron.ui.components.MultiActionButton
import com.dark.tool_neuron.models.messages.ContentType
import com.dark.tool_neuron.models.messages.ImageGenerationMetrics
import com.dark.tool_neuron.models.messages.MemoryMetrics
import com.dark.tool_neuron.models.messages.MessageContent
import com.dark.tool_neuron.models.messages.Messages
import com.dark.tool_neuron.models.messages.RagResultItem
import com.dark.tool_neuron.models.messages.Role
import com.dark.tool_neuron.ui.components.MarkdownText
import com.dark.tool_neuron.ui.components.PluginResultCard
import com.dark.tool_neuron.ui.theme.maple
import com.dark.tool_neuron.ui.theme.rDp
import com.dark.tool_neuron.viewmodel.ChatViewModel
import com.dark.tool_neuron.viewmodel.LLMModelViewModel
import com.dark.tool_neuron.worker.GenerationManager
import com.mp.ai_gguf.models.DecodingMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Base64

data class ParsedMessage(
    val thinkingContent: String?,
    val actualContent: String
)

suspend fun parseThinkingTags(content: String): ParsedMessage = withContext(Dispatchers.IO) {
    val thinkingRegex = Regex("<think>(.*?)</think>", RegexOption.DOT_MATCHES_ALL)
    val thinkingMatch = thinkingRegex.find(content)

    val thinkingContent = thinkingMatch?.groupValues?.getOrNull(1)?.trim()
    val actualContent = content.replace(thinkingRegex, "").trim()

    ParsedMessage(thinkingContent, actualContent)
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun BodyContent(
    paddingValues: PaddingValues,
    chatViewModel: ChatViewModel,
    llmModelViewModel: LLMModelViewModel
) {
    val messages = chatViewModel.messages
    val isGenerating by chatViewModel.isGenerating.collectAsState()
    val streamingUserMessage by chatViewModel.streamingUserMessage.collectAsState()
    val streamingAssistantMessage by chatViewModel.streamingAssistantMessage.collectAsState()
    val streamingImage by chatViewModel.streamingImage.collectAsState()
    val imageProgress by chatViewModel.imageGenerationProgress.collectAsState()
    val imageStep by chatViewModel.imageGenerationStep.collectAsState()
    val showDynamicWindow by chatViewModel.showDynamicWindow.collectAsState()
    val currentGenerationType by chatViewModel.currentGenerationType.collectAsState()
    val currentRagResults by chatViewModel.currentRagResults.collectAsState()
    val currentMemoryResults by chatViewModel.currentMemoryResults.collectAsState()
    val appState by com.dark.tool_neuron.state.AppStateManager.appState.collectAsState()
    val currentToolName by chatViewModel.currentToolName.collectAsState()
    val currentProcessingPhase by chatViewModel.currentProcessingPhase.collectAsState()
    val ttsPlayingMsgId by chatViewModel.ttsPlayingMsgId.collectAsState()
    val ttsIsPlaying by chatViewModel.ttsIsPlaying.collectAsState()
    val ttsSynthesizing by chatViewModel.ttsSynthesizing.collectAsState()
    val ttsModelLoaded by chatViewModel.ttsModelLoaded.collectAsState()

    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && !isGenerating) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(paddingValues)
    ) {
        if (messages.isEmpty() && !isGenerating) {
            EmptyMessagesState()
        } else {
            if (isGenerating && streamingUserMessage != null) {
                StreamingView(
                    userMessage = streamingUserMessage!!,
                    assistantMessage = streamingAssistantMessage,
                    streamingImage = streamingImage,
                    imageProgress = imageProgress,
                    imageStep = imageStep,
                    isImageGeneration = currentGenerationType == GenerationManager.ModelType.IMAGE_GENERATION,
                    ragResults = currentRagResults,
                    memoryResults = currentMemoryResults,
                    appState = appState,
                    messages = messages,
                    currentToolName = currentToolName,
                    processingPhase = currentProcessingPhase
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = rDp(8.dp)),
                    verticalArrangement = Arrangement.spacedBy(rDp(8.dp))
                ) {
                    items(
                        count = messages.size,
                        key = { index ->
                            val msg = messages[index]
                            "${msg.msgId}-${index}"
                        },
                        contentType = { index -> messages[index].role }
                    ) { index ->
                        MessageBubble(
                            message = messages[index],
                            ttsPlayingMsgId = ttsPlayingMsgId,
                            ttsIsPlaying = ttsIsPlaying,
                            ttsSynthesizing = ttsSynthesizing,
                            ttsModelLoaded = ttsModelLoaded,
                            onSpeak = { chatViewModel.speakMessage(it) },
                            onStopTTS = { chatViewModel.stopTTS() }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(rDp(16.dp)))
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showDynamicWindow,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        chatViewModel.hideDynamicWindow()
                    })
        }

        AnimatedVisibility(
            visible = showDynamicWindow,
            enter = fadeIn(animationSpec = tween(300)) + slideInVertically(
                initialOffsetY = { -it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ),
            exit = fadeOut(animationSpec = tween(300)) + slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = tween(300)
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rDp(16.dp), vertical = rDp(16.dp)),
                contentAlignment = Alignment.TopCenter
            ) {
                val ragCount by com.dark.tool_neuron.plugins.PluginManager.enabledPluginNames.collectAsState()
                val ttsLoaded by com.dark.tool_neuron.tts.TTSManager.isModelLoaded.collectAsState()

                DynamicActionWindow(
                    chatViewModel = chatViewModel,
                    modelViewModel = llmModelViewModel,
                    enabledToolCount = ragCount.size,
                    ttsModelLoaded = ttsLoaded
                )
            }
        }
    }
}

@Composable
private fun StreamingView(
    userMessage: String,
    assistantMessage: String,
    streamingImage: Bitmap?,
    imageProgress: Float,
    imageStep: String,
    isImageGeneration: Boolean,
    ragResults: List<com.dark.tool_neuron.viewmodel.RagQueryDisplayResult> = emptyList(),
    memoryResults: List<com.dark.tool_neuron.worker.ScoredVaultContent> = emptyList(),
    appState: com.dark.tool_neuron.models.state.AppState,
    messages: List<Messages> = emptyList(),
    currentToolName: String? = null,
    processingPhase: String? = null
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(assistantMessage, streamingImage, messages.size, appState) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(rDp(8.dp)),
        verticalArrangement = Arrangement.spacedBy(rDp(8.dp))
    ) {
        UserMessageBubble(
            message = Messages(
                role = Role.User,
                content = MessageContent(
                    contentType = ContentType.Text,
                    content = userMessage
                )
            )
        )

        // Show RAG context if available
        if (ragResults.isNotEmpty()) {
            RagResultsDisplay(results = ragResults)
        }

        // Show Memory results if available
        if (memoryResults.isNotEmpty()) {
            MemoryResultsDisplay(results = memoryResults)
        }

        // Show tool results
        messages.filter { it.content.contentType == ContentType.PluginResult }.forEach { msg ->
            PluginResultCard(message = msg)
        }

        // Processing phase indicator
        if (processingPhase != null) {
            ProcessingPhaseIndicator(phase = processingPhase)
        }

        when {
            appState is com.dark.tool_neuron.models.state.AppState.ExecutingPlugin -> {
                PluginExecutionStreamingBubble(
                    pluginName = appState.pluginName,
                    toolName = appState.toolName,
                    isExecuting = true
                )
            }
            appState is com.dark.tool_neuron.models.state.AppState.PluginExecutionComplete -> {
                PluginExecutionCompleteStreamingBubble(
                    pluginName = appState.pluginName,
                    toolName = appState.toolName,
                    success = appState.success,
                    executionTimeMs = appState.executionTimeMs,
                    errorMessage = appState.errorMessage
                )
            }
            isImageGeneration -> {
                ImageGenerationStreamingBubble(
                    streamingImage = streamingImage,
                    progress = imageProgress,
                    step = imageStep
                )
            }
            else -> {
                AssistantStreamingBubble(text = assistantMessage)
            }
        }

        Spacer(modifier = Modifier.height(rDp(16.dp)))
    }
}

@Composable
private fun ProcessingPhaseIndicator(phase: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = rDp(12.dp), vertical = rDp(4.dp)),
        horizontalArrangement = Arrangement.spacedBy(rDp(8.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(rDp(16.dp)),
            strokeWidth = rDp(2.dp),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = phase,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ImageGenerationStreamingBubble(
    streamingImage: Bitmap?,
    progress: Float,
    step: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(rDp(8.dp)),
        verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(rDp(12.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(rDp(24.dp)),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = rDp(3.dp)
            )

            Column {
                Text(
                    text = "Generating image...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "$step • ${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        streamingImage?.let { bitmap ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                shape = RoundedCornerShape(rDp(12.dp)),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Generating image preview",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
private fun PluginExecutionStreamingBubble(
    pluginName: String,
    toolName: String,
    isExecuting: Boolean = true
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(rDp(8.dp)),
        verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(rDp(12.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "plugin_execution")
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

            Column {
                Text(
                    text = "Executing tool...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "$pluginName • $toolName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(rDp(3.dp))
                .clip(RoundedCornerShape(rDp(2.dp))),
            color = MaterialTheme.colorScheme.tertiary
        )
    }
}

@Composable
private fun PluginExecutionCompleteStreamingBubble(
    pluginName: String,
    toolName: String,
    success: Boolean,
    executionTimeMs: Long,
    errorMessage: String?
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(rDp(8.dp)),
        shape = RoundedCornerShape(rDp(12.dp)),
        color = if (success) {
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        },
        tonalElevation = rDp(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(rDp(12.dp)),
            verticalArrangement = Arrangement.spacedBy(rDp(8.dp))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(rDp(12.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (success) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    modifier = Modifier.size(rDp(24.dp)),
                    tint = if (success) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (success) "Tool executed successfully" else "Tool execution failed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (success) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "$pluginName • $toolName",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (success) {
                            MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                        }
                    )
                }

                Text(
                    text = "${executionTimeMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (success) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }

            if (!success && errorMessage != null) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                )
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = rDp(36.dp))
                )
            }
        }
    }
}

@Composable
private fun AssistantStreamingBubble(text: String) {
    val parsedMessage by produceState(
        initialValue = ParsedMessage(null, text),
        key1 = text
    ) {
        value = parseThinkingTags(text)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = rDp(8.dp)),
        verticalArrangement = Arrangement.spacedBy(rDp(8.dp))
    ) {
        if (parsedMessage.thinkingContent != null) {
            ThinkingBlock(parsedMessage.thinkingContent!!)
        }

        if (parsedMessage.actualContent.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(rDp(8.dp)),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(rDp(8.dp))
                        .background(MaterialTheme.colorScheme.primary, shape = CircleShape)
                )

                Text(
                    text = parsedMessage.actualContent.ifEmpty { "..." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ThinkingBlock(thinkingText: String) {
    var isExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = rDp(12.dp)),
        shape = RoundedCornerShape(rDp(10.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = rDp(2.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = rDp(8.dp), horizontal = rDp(12.dp)),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(rDp(8.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.thinking),
                        contentDescription = null,
                        modifier = Modifier.size(rDp(16.dp)),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = "Thinking",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.size(rDp(20.dp)),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) + fadeIn(),
                exit = shrinkVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) + fadeOut()
            ) {
                Column {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                    Text(
                        text = thinkingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(rDp(12.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyMessagesState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(R.drawable.user),
                contentDescription = null,
                modifier = Modifier.size(rDp(64.dp)),
                tint = MaterialTheme.colorScheme.primary.copy(0.4f)
            )
            Spacer(Modifier.height(rDp(16.dp)))
            Text(
                "No Conversation Yet.!!",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Select a Model & Start a conversation",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: Messages,
    ttsPlayingMsgId: String? = null,
    ttsIsPlaying: Boolean = false,
    ttsSynthesizing: Boolean = false,
    ttsModelLoaded: Boolean = false,
    onSpeak: (Messages) -> Unit = {},
    onStopTTS: () -> Unit = {}
) {
    when (message.role) {
        Role.User -> UserMessageBubble(message)
        else -> AssistantMessageBubble(
            message = message,
            ttsPlayingMsgId = ttsPlayingMsgId,
            ttsIsPlaying = ttsIsPlaying,
            ttsSynthesizing = ttsSynthesizing,
            ttsModelLoaded = ttsModelLoaded,
            onSpeak = onSpeak,
            onStopTTS = onStopTTS
        )
    }
}

@Composable
private fun UserMessageBubble(message: Messages) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(rDp(12.dp)),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .padding(horizontal = rDp(8.dp), vertical = rDp(5.dp))
                .widthIn(max = rDp(280.dp))
        ) {
            SelectionContainer {
                MarkdownText(
                    text = message.content.content,
                    modifier = Modifier.padding(
                        horizontal = rDp(12.dp),
                        vertical = rDp(6.dp)
                    )
                )
            }
        }
    }
}

@Composable
private fun AssistantMessageBubble(
    message: Messages,
    ttsPlayingMsgId: String? = null,
    ttsIsPlaying: Boolean = false,
    ttsSynthesizing: Boolean = false,
    ttsModelLoaded: Boolean = false,
    onSpeak: (Messages) -> Unit = {},
    onStopTTS: () -> Unit = {}
) {
    val showMetrics = remember(message.decodingMetrics) {
        message.decodingMetrics?.tokensPerSecond?.let { it > 0 } ?: false
    }

    val showImageMetrics = remember(message.imageMetrics) {
        message.imageMetrics != null
    }

    val showMemoryMetrics = remember(message.memoryMetrics) {
        message.memoryMetrics?.let { it.modelSizeMB > 0 || it.peakMemoryMB > 0 } ?: false
    }

    val hasRagResults = remember(message.ragResults) {
        message.ragResults?.isNotEmpty() == true
    }

    val parsedMessage by produceState(
        initialValue = ParsedMessage(null, message.content.content),
        key1 = message.content.content
    ) {
        value = parseThinkingTags(message.content.content)
    }

    val isTextContent = message.content.contentType == ContentType.Text
    val isThisMessagePlaying = ttsPlayingMsgId == message.msgId && ttsIsPlaying
    val isThisMessageSynthesizing = ttsPlayingMsgId == message.msgId && ttsSynthesizing

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = rDp(8.dp)),
        verticalArrangement = Arrangement.spacedBy(rDp(6.dp))
    ) {
        // Show RAG results if available (before the response)
        if (hasRagResults) {
            message.ragResults?.let { results ->
                SavedRagResultsDisplay(results = results)
            }
        }

        when (message.content.contentType) {
            ContentType.Image -> {
                ImageMessageBubble(message)
            }
            ContentType.PluginResult -> {
                PluginResultCard(message = message)
            }
            else -> {
                if (parsedMessage.thinkingContent != null) {
                    ThinkingBlock(parsedMessage.thinkingContent!!)
                }

                if (parsedMessage.actualContent.isNotEmpty()) {
                    SelectionContainer{
                        MarkdownText(
                            text = parsedMessage.actualContent,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = rDp(4.dp))
                        )
                    }
                }
            }
        }

        if (showMetrics) {
            message.decodingMetrics?.let { metrics ->
                MetricsDisplay(metrics, message.memoryMetrics)
            }
        }

        if (showImageMetrics) {
            message.imageMetrics?.let { metrics ->
                ImageMetricsDisplay(metrics)
            }
        }

        if (showMemoryMetrics && !showMetrics) {
            message.memoryMetrics?.let { metrics ->
                MemoryMetricsDisplay(metrics)
            }
        }

        // Action row: TTS speak + Copy (only for text messages with content)
        if (isTextContent && parsedMessage.actualContent.isNotEmpty()) {
            MessageActionRow(
                message = message,
                textContent = parsedMessage.actualContent,
                isPlaying = isThisMessagePlaying,
                isSynthesizing = isThisMessageSynthesizing,
                ttsModelLoaded = ttsModelLoaded,
                onSpeak = onSpeak,
                onStopTTS = onStopTTS
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MessageActionRow(
    message: Messages,
    textContent: String,
    isPlaying: Boolean,
    isSynthesizing: Boolean,
    ttsModelLoaded: Boolean,
    onSpeak: (Messages) -> Unit,
    onStopTTS: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var showCopied by remember { mutableStateOf(false) }

    LaunchedEffect(showCopied) {
        if (showCopied) {
            kotlinx.coroutines.delay(1500)
            showCopied = false
        }
    }

    val actions = buildList {
        // TTS action: 3 states - playing (stop icon), synthesizing (loading spinner), idle (speak icon)
        when {
            isPlaying -> add(ActionItem(
                icon = ActionIcon.Vector(Icons.Default.Stop),
                onClick = { onStopTTS() },
                contentDescription = "Stop"
            ))
            isSynthesizing -> add(ActionItem(
                icon = ActionIcon.Resource(R.drawable.volume),
                onClick = { onStopTTS() },
                contentDescription = "Synthesizing",
                isLoading = true
            ))
            else -> add(ActionItem(
                icon = ActionIcon.Resource(R.drawable.volume),
                onClick = { onSpeak(message) },
                contentDescription = "Speak"
            ))
        }

        // Copy action
        if (showCopied) {
            add(ActionItem(
                icon = ActionIcon.Vector(Icons.Default.CheckCircle),
                onClick = {},
                contentDescription = "Copied"
            ))
        } else {
            add(ActionItem(
                icon = ActionIcon.Resource(R.drawable.copy),
                onClick = {
                    clipboardManager.setText(AnnotatedString(textContent))
                    showCopied = true
                },
                contentDescription = "Copy"
            ))
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = rDp(12.dp)),
        horizontalArrangement = Arrangement.spacedBy(rDp(4.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MultiActionButton(actions = actions)

        if (showCopied) {
            Text(
                text = "Copied",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ImageMessageBubble(message: Messages) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val appSettingsDataStore = remember { com.dark.tool_neuron.data.AppSettingsDataStore(context) }
    val imageBlurEnabled by appSettingsDataStore.imageBlurEnabled.collectAsState(initial = true)
    var isImageRevealed by remember(imageBlurEnabled) { mutableStateOf(!imageBlurEnabled) }

    Column(
        verticalArrangement = Arrangement.spacedBy(rDp(8.dp)),
        modifier = Modifier.padding(rDp(12.dp))
    ) {
        message.content.imagePrompt?.let { prompt ->
            Text(
                text = "Prompt: $prompt",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = rDp(4.dp))
            )
        }

        message.content.imageData?.let { base64Image ->
            val bitmap = remember(base64Image) {
                try {
                    val imageBytes = Base64.getDecoder().decode(base64Image)
                    BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                } catch (e: Exception) {
                    null
                }
            }

            bitmap?.let {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(rDp(12.dp)))
                        .clickable { isImageRevealed = !isImageRevealed },
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(rDp(12.dp)),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = message.content.content,
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (!isImageRevealed) Modifier.blur(radius = 46.dp)
                                    else Modifier
                                ),
                            contentScale = ContentScale.Crop
                        )
                    }

                    // Overlay when blurred
                    if (!isImageRevealed) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(rDp(8.dp))
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.smart_temp_message),
                                    contentDescription = "Reveal image",
                                    modifier = Modifier.size(rDp(32.dp)),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Tap to reveal",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        message.content.imageSeed?.let { seed ->
            Text(
                text = "Seed: $seed",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = rDp(4.dp))
            )
        }
    }
}

@Composable
private fun MetricsDisplay(metrics: DecodingMetrics, memoryMetrics: MemoryMetrics? = null) {
    var isExpanded by remember { mutableStateOf(false) }

    val formattedSpeed = remember(metrics.tokensPerSecond) {
        "%.1f".format(metrics.tokensPerSecond)
    }
    val formattedTime = remember(metrics.totalTimeMs) {
        if (metrics.totalTimeMs > 0) "%.1f".format(metrics.totalTimeMs / 1000f) else null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = rDp(12.dp))
    ) {
        // Summary row
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            shape = RoundedCornerShape(rDp(8.dp)),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            tonalElevation = rDp(1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rDp(10.dp), vertical = rDp(8.dp)),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(rDp(12.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.speed),
                        contentDescription = null,
                        modifier = Modifier.size(rDp(14.dp)),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "$formattedSpeed t/s",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )

                    Text(
                        text = "${metrics.totalTokens} tokens",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.size(rDp(18.dp)),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Detailed metrics
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeIn(),
            exit = shrinkVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = rDp(6.dp)),
                shape = RoundedCornerShape(rDp(8.dp)),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            ) {
                Column(
                    modifier = Modifier.padding(rDp(10.dp)),
                    verticalArrangement = Arrangement.spacedBy(rDp(8.dp))
                ) {
                    MetricRow(
                        icon = R.drawable.tokens,
                        label = "Total Tokens",
                        value = metrics.totalTokens.toString()
                    )

                    if (metrics.promptTokens > 0) {
                        MetricRow(
                            icon = R.drawable.prompt,
                            label = "Prompt Tokens",
                            value = metrics.promptTokens.toString()
                        )
                    }

                    if (metrics.generatedTokens > 0) {
                        MetricRow(
                            icon = R.drawable.generated,
                            label = "Generated Tokens",
                            value = metrics.generatedTokens.toString()
                        )
                    }

                    MetricRow(
                        icon = R.drawable.speed,
                        label = "Speed",
                        value = "$formattedSpeed t/s"
                    )

                    if (metrics.timeToFirstToken > 0) {
                        MetricRow(
                            icon = R.drawable.timer,
                            label = "Time to First Token",
                            value = "${metrics.timeToFirstToken} ms"
                        )
                    }

                    formattedTime?.let { time ->
                        MetricRow(
                            icon = R.drawable.clock,
                            label = "Total Duration",
                            value = "$time s"
                        )
                    }

                    // Memory metrics section
                    memoryMetrics?.let { mem ->
                        if (mem.modelSizeMB > 0 || mem.peakMemoryMB > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = rDp(4.dp)),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )

                            Text(
                                text = "Memory",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(bottom = rDp(4.dp))
                            )

                            if (mem.modelSizeMB > 0) {
                                MetricRow(
                                    icon = R.drawable.tokens,
                                    label = "Model Size",
                                    value = "${mem.modelSizeMB} MB"
                                )
                            }

                            if (mem.contextSizeMB > 0) {
                                MetricRow(
                                    icon = R.drawable.tokens,
                                    label = "Context Size",
                                    value = "${mem.contextSizeMB} MB"
                                )
                            }

                            if (mem.peakMemoryMB > 0) {
                                MetricRow(
                                    icon = R.drawable.tokens,
                                    label = "Peak Memory",
                                    value = "${mem.peakMemoryMB} MB"
                                )
                            }

                            if (mem.memoryUsagePercent > 0) {
                                MetricRow(
                                    icon = R.drawable.tokens,
                                    label = "Memory Usage",
                                    value = "${"%.1f".format(mem.memoryUsagePercent)}%"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryMetricsDisplay(metrics: MemoryMetrics) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = rDp(12.dp))
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            shape = RoundedCornerShape(rDp(8.dp)),
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
            tonalElevation = rDp(1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rDp(10.dp), vertical = rDp(8.dp)),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(rDp(12.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.tokens),
                        contentDescription = null,
                        modifier = Modifier.size(rDp(14.dp)),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = "Memory",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (metrics.peakMemoryMB > 0) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )

                        Text(
                            text = "${metrics.peakMemoryMB} MB peak",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.size(rDp(18.dp)),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeIn(),
            exit = shrinkVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = rDp(6.dp)),
                shape = RoundedCornerShape(rDp(8.dp)),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            ) {
                Column(
                    modifier = Modifier.padding(rDp(10.dp)),
                    verticalArrangement = Arrangement.spacedBy(rDp(8.dp))
                ) {
                    if (metrics.modelSizeMB > 0) {
                        MetricRow(
                            icon = R.drawable.tokens,
                            label = "Model Size",
                            value = "${metrics.modelSizeMB} MB"
                        )
                    }

                    if (metrics.contextSizeMB > 0) {
                        MetricRow(
                            icon = R.drawable.tokens,
                            label = "Context Size",
                            value = "${metrics.contextSizeMB} MB"
                        )
                    }

                    if (metrics.peakMemoryMB > 0) {
                        MetricRow(
                            icon = R.drawable.tokens,
                            label = "Peak Memory",
                            value = "${metrics.peakMemoryMB} MB"
                        )
                    }

                    if (metrics.memoryUsagePercent > 0) {
                        MetricRow(
                            icon = R.drawable.tokens,
                            label = "Memory Usage",
                            value = "${"%.1f".format(metrics.memoryUsagePercent)}%"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageMetricsDisplay(metrics: ImageGenerationMetrics) {
    var isExpanded by remember { mutableStateOf(false) }

    val formattedTime = remember(metrics.generationTimeMs) {
        "%.1f".format(metrics.generationTimeMs / 1000f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = rDp(12.dp))
    ) {
        // Summary row
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            shape = RoundedCornerShape(rDp(8.dp)),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            tonalElevation = rDp(1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rDp(10.dp), vertical = rDp(8.dp)),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(rDp(12.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.dimensions),
                        contentDescription = null,
                        modifier = Modifier.size(rDp(14.dp)),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = "${metrics.width}×${metrics.height}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )

                    Text(
                        text = "${metrics.steps} steps",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.size(rDp(18.dp)),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Detailed metrics
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeIn(),
            exit = shrinkVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = rDp(6.dp)),
                shape = RoundedCornerShape(rDp(8.dp)),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            ) {
                Column(
                    modifier = Modifier.padding(rDp(10.dp)),
                    verticalArrangement = Arrangement.spacedBy(rDp(8.dp))
                ) {
                    MetricRow(
                        icon = R.drawable.dimensions,
                        label = "Dimensions",
                        value = "${metrics.width} × ${metrics.height}"
                    )

                    MetricRow(
                        icon = R.drawable.steps,
                        label = "Steps",
                        value = metrics.steps.toString()
                    )

                    MetricRow(
                        icon = R.drawable.cgf,
                        label = "CFG Scale",
                        value = "%.1f".format(metrics.cfgScale)
                    )

                    MetricRow(
                        icon = R.drawable.tokens,
                        label = "Seed",
                        value = metrics.seed.toString()
                    )

                    MetricRow(
                        icon = R.drawable.scheduler,
                        label = "Scheduler",
                        value = metrics.scheduler.uppercase()
                    )

                    MetricRow(
                        icon = R.drawable.clock,
                        label = "Generation Time",
                        value = "$formattedTime s"
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricRow(
    icon: Int,
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
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(rDp(14.dp)),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
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
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = maple
        )
    }
}

// RAG Results UI Component
@Composable
fun RagResultsDisplay(
    results: List<com.dark.tool_neuron.viewmodel.RagQueryDisplayResult>,
    modifier: Modifier = Modifier
) {
    if (results.isEmpty()) return

    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = rDp(12.dp))
    ) {
        // Summary row
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            shape = RoundedCornerShape(rDp(8.dp)),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
            tonalElevation = rDp(1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rDp(10.dp), vertical = rDp(8.dp)),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(rDp(12.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.rag),
                        contentDescription = null,
                        modifier = Modifier.size(rDp(14.dp)),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "RAG Context",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )

                    Text(
                        text = "${results.size} matches",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.size(rDp(18.dp)),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Detailed results
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeIn(),
            exit = shrinkVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = rDp(6.dp)),
                shape = RoundedCornerShape(rDp(8.dp)),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            ) {
                Column(
                    modifier = Modifier.padding(rDp(10.dp)),
                    verticalArrangement = Arrangement.spacedBy(rDp(10.dp))
                ) {
                    results.take(5).forEachIndexed { index, result ->
                        RagResultItem(result = result, index = index)
                        if (index < results.size - 1 && index < 4) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        }
                    }

                    if (results.size > 5) {
                        Text(
                            text = "... and ${results.size - 5} more results",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = rDp(4.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RagResultItem(
    result: com.dark.tool_neuron.viewmodel.RagQueryDisplayResult,
    index: Int
) {
    val scorePercent = (result.score * 100).toInt()
    val scoreColor = when {
        scorePercent >= 80 -> MaterialTheme.colorScheme.primary
        scorePercent >= 60 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(rDp(4.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(rDp(6.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${index + 1}.",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = result.ragName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }

            Surface(
                color = scoreColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(rDp(4.dp))
            ) {
                Text(
                    text = "$scorePercent%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = scoreColor,
                    modifier = Modifier.padding(horizontal = rDp(6.dp), vertical = rDp(2.dp))
                )
            }
        }

        Text(
            text = result.content.take(200) + if (result.content.length > 200) "..." else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            lineHeight = 16.sp
        )
    }
}

// Saved RAG Results Display (for persisted messages)
@Composable
fun SavedRagResultsDisplay(
    results: List<RagResultItem>,
    modifier: Modifier = Modifier
) {
    if (results.isEmpty()) return

    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = rDp(12.dp))
    ) {
        // Summary row
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            shape = RoundedCornerShape(rDp(8.dp)),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
            tonalElevation = rDp(1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rDp(10.dp), vertical = rDp(8.dp)),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(rDp(12.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.rag),
                        contentDescription = null,
                        modifier = Modifier.size(rDp(14.dp)),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "RAG Context",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )

                    Text(
                        text = "${results.size} matches",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.size(rDp(18.dp)),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Detailed results
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeIn(),
            exit = shrinkVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = rDp(6.dp)),
                shape = RoundedCornerShape(rDp(8.dp)),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            ) {
                Column(
                    modifier = Modifier.padding(rDp(10.dp)),
                    verticalArrangement = Arrangement.spacedBy(rDp(10.dp))
                ) {
                    results.take(5).forEachIndexed { index, result ->
                        SavedRagResultItemRow(result = result, index = index)
                        if (index < results.size - 1 && index < 4) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        }
                    }

                    if (results.size > 5) {
                        Text(
                            text = "... and ${results.size - 5} more results",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = rDp(4.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedRagResultItemRow(
    result: RagResultItem,
    index: Int
) {
    val scorePercent = (result.score * 100).toInt()
    val scoreColor = when {
        scorePercent >= 80 -> MaterialTheme.colorScheme.primary
        scorePercent >= 60 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(rDp(4.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(rDp(6.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${index + 1}.",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = result.ragName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }

            Surface(
                color = scoreColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(rDp(4.dp))
            ) {
                Text(
                    text = "$scorePercent%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = scoreColor,
                    modifier = Modifier.padding(horizontal = rDp(6.dp), vertical = rDp(2.dp))
                )
            }
        }

        Text(
            text = result.content.take(200) + if (result.content.length > 200) "..." else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            lineHeight = 16.sp
        )
    }
}