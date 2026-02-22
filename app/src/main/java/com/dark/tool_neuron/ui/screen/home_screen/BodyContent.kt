package com.dark.tool_neuron.ui.screen.home_screen

import android.content.ClipData
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.random.Random
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
import com.dark.tool_neuron.ui.components.ToolChainDisplay
import com.dark.tool_neuron.models.messages.ContentType
import com.dark.tool_neuron.models.messages.ImageGenerationMetrics
import com.dark.tool_neuron.models.messages.MemoryMetrics
import com.dark.tool_neuron.models.messages.MessageContent
import com.dark.tool_neuron.models.messages.Messages
import com.dark.tool_neuron.models.messages.RagResultItem
import com.dark.tool_neuron.models.messages.Role
import com.dark.tool_neuron.ui.components.AgentExecutionView
import com.dark.tool_neuron.ui.components.MarkdownText
import com.dark.tool_neuron.ui.components.lazyMarkdownItems
import com.dark.tool_neuron.ui.components.PluginResultCard
import com.dark.tool_neuron.ui.theme.maple
import com.dark.tool_neuron.ui.theme.rDp
import com.dark.tool_neuron.viewmodel.AgentPhase
import com.dark.tool_neuron.viewmodel.ChatViewModel
import com.dark.tool_neuron.viewmodel.LLMModelViewModel
import com.dark.tool_neuron.worker.GenerationManager
import com.mp.ai_gguf.models.DecodingMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext
import java.util.Base64

data class ParsedMessage(
    val thinkingContent: String?,
    val actualContent: String
)

suspend fun parseThinkingTags(content: String): ParsedMessage = withContext(Dispatchers.IO) {
    // Fast path: skip regex if no think tags present
    if (!content.contains("<think>")) {
        return@withContext ParsedMessage(null, content.trim())
    }

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
    val toolChainSteps by chatViewModel.toolChainSteps.collectAsState()
    val currentToolChainRound by chatViewModel.currentToolChainRound.collectAsState()
    val agentPhase by chatViewModel.agentPhase.collectAsState()
    val agentPlan by chatViewModel.agentPlan.collectAsState()
    val agentSummary by chatViewModel.agentSummary.collectAsState()
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
                    toolChainSteps = toolChainSteps,
                    currentToolChainRound = currentToolChainRound,
                    agentPhase = agentPhase,
                    agentPlan = agentPlan,
                    agentSummary = agentSummary
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = rDp(8.dp)),
                    verticalArrangement = Arrangement.spacedBy(rDp(4.dp))
                ) {
                    val lastAssistantIndex = messages.indexOfLast { it.role == Role.Assistant }

                    messages.forEachIndexed { index, message ->
                        when (message.role) {
                            Role.User -> {
                                item(key = "${message.msgId}-user") {
                                    UserMessageBubble(message)
                                }
                            }
                            else -> {
                                val isLastAssistant = index == lastAssistantIndex
                                // Header: RAG, tool chain, thinking, image/plugin
                                item(key = "${message.msgId}-header") {
                                    AssistantMessageHeader(message)
                                }
                                // Markdown content — each element is a lazy item
                                if (message.content.contentType == ContentType.Text) {
                                    val parsedText = message.content.content
                                        .let { raw ->
                                            // Strip <think>...</think> for the markdown items
                                            if (raw.contains("<think>")) {
                                                raw.replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "").trim()
                                            } else raw
                                        }
                                    if (parsedText.isNotEmpty()) {
                                        lazyMarkdownItems(
                                            text = parsedText,
                                            keyPrefix = message.msgId,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 4.dp)
                                        )
                                    }
                                }
                                // Footer: metrics + action row
                                item(key = "${message.msgId}-footer") {
                                    AssistantMessageFooter(
                                        message = message,
                                        ttsPlayingMsgId = ttsPlayingMsgId,
                                        ttsIsPlaying = ttsIsPlaying,
                                        ttsSynthesizing = ttsSynthesizing,
                                        ttsModelLoaded = ttsModelLoaded,
                                        onSpeak = { chatViewModel.speakMessage(it) },
                                        onStopTTS = { chatViewModel.stopTTS() },
                                        onRegenerate = if (isLastAssistant) {
                                            { chatViewModel.regenerateLastMessage() }
                                        } else null,
                                        isRegenerateEnabled = !isGenerating
                                    )
                                }
                            }
                        }
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
    toolChainSteps: List<com.dark.tool_neuron.models.messages.ToolChainStepData> = emptyList(),
    currentToolChainRound: Int = 0,
    agentPhase: AgentPhase = AgentPhase.Idle,
    agentPlan: String? = null,
    agentSummary: String? = null
) {
    val scrollState = rememberScrollState()

    // Track whether user has manually scrolled up (disables auto-scroll)
    var userScrolledUp by remember { mutableStateOf(false) }
    val isAtBottom = remember {
        derivedStateOf {
            val maxScroll = scrollState.maxValue
            maxScroll == 0 || scrollState.value >= maxScroll - 100
        }
    }

    // Detect user scroll gestures - if user scrolls away from bottom, pause auto-scroll
    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress && !isAtBottom.value) {
            userScrolledUp = true
        }
    }

    // Reset userScrolledUp when user scrolls back to bottom
    LaunchedEffect(isAtBottom.value) {
        if (isAtBottom.value) {
            userScrolledUp = false
        }
    }

    @OptIn(FlowPreview::class)
    LaunchedEffect(Unit) {
        snapshotFlow {
            // Combine all scroll-triggering values
            Triple(assistantMessage.length, messages.size, toolChainSteps.size)
        }
        .debounce(150)
        .collect {
            if (!userScrolledUp) {
                scrollState.animateScrollTo(scrollState.maxValue)
            }
        }
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

        // Agent execution view (Plan → Execute → Summarize)
        if (agentPhase != AgentPhase.Idle) {
            AgentExecutionView(
                plan = agentPlan,
                steps = toolChainSteps,
                summary = agentSummary,
                phase = agentPhase,
                currentStep = currentToolChainRound
            )
        }

        // Show tool results from plugin execution (only when NOT in agent mode,
        // since AgentExecutionView already displays step results)
        if (agentPhase == AgentPhase.Idle) {
            messages.filter { it.content.contentType == ContentType.PluginResult }.forEach { msg ->
                PluginResultCard(message = msg)
            }
        }

        when {
            isImageGeneration -> {
                ImageGenerationStreamingBubble(
                    streamingImage = streamingImage,
                    progress = imageProgress,
                    step = imageStep
                )
            }
            // Show streaming text when in simple flow or during plan/summary generation
            agentPhase == AgentPhase.Idle || agentPhase == AgentPhase.Complete -> {
                if (assistantMessage.isNotEmpty()) {
                    AssistantStreamingBubble(text = assistantMessage)
                }
            }
        }

        Spacer(modifier = Modifier.height(rDp(16.dp)))
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1))
                    .clip(RoundedCornerShape(rDp(12.dp)))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                MorphingImagePreview(
                    bitmap = bitmap,
                    progress = progress,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}


/**
 * ChatGPT-style morphing color preview.
 *
 * Each chunk bitmap is pre-processed with a radial alpha mask so its edges
 * fade from opaque (center) to fully transparent (border). When overlapping
 * blurred chunks are drawn together, they blend seamlessly with no hard edges.
 */
@Composable
private fun MorphingImagePreview(
    bitmap: Bitmap,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val cols = 2
    val rows = 3

    // Create feathered chunks: opaque center → transparent edges
    val chunks = remember(bitmap) {
        val cw = bitmap.width / cols
        val ch = bitmap.height / rows
        List(cols * rows) { i ->
            val src = Bitmap.createBitmap(bitmap, (i % cols) * cw, (i / cols) * ch, cw, ch)
            createFeatheredChunk(src, cw, ch)
        }
    }

    // Eased progress curve: stays abstract longer, resolves in last 30%
    val easedProgress = if (progress < 0.7f) {
        progress / 0.7f * 0.4f
    } else {
        0.4f + (progress - 0.7f) / 0.3f * 0.6f
    }
    val drift = (1f - easedProgress).coerceIn(0f, 1f)
    val blurAmount = (55f * drift).coerceAtLeast(0f)

    val infiniteTransition = rememberInfiniteTransition(label = "morph")

    BoxWithConstraints(modifier = modifier) {
        val cellW = maxWidth / cols
        val cellH = maxHeight / rows

        chunks.forEachIndexed { index, chunk ->
            key(index) {
                val rng = remember { Random(index * 37 + 7) }
                val ampX = remember { 25f + rng.nextFloat() * 35f }
                val ampY = remember { 20f + rng.nextFloat() * 30f }
                val durX = remember { 3500 + rng.nextInt(2000) }
                val durY = remember { 3000 + rng.nextInt(2000) }
                val delayX = remember { rng.nextInt(700) }
                val delayY = remember { rng.nextInt(700) }
                val scatterX = remember { (rng.nextFloat() - 0.5f) * 50f }
                val scatterY = remember { (rng.nextFloat() - 0.5f) * 40f }

                val driftX by infiniteTransition.animateFloat(
                    initialValue = -ampX, targetValue = ampX,
                    animationSpec = infiniteRepeatable(
                        tween(durX, delayMillis = delayX, easing = LinearOutSlowInEasing),
                        RepeatMode.Reverse
                    ), label = "dx$index"
                )
                val driftY by infiniteTransition.animateFloat(
                    initialValue = -ampY, targetValue = ampY,
                    animationSpec = infiniteRepeatable(
                        tween(durY, delayMillis = delayY, easing = LinearOutSlowInEasing),
                        RepeatMode.Reverse
                    ), label = "dy$index"
                )

                val col = index % cols
                val row = index / cols
                val ox = cellW * col + ((driftX + scatterX) * drift).dp
                val oy = cellH * row + ((driftY + scatterY) * drift).dp
                val blobScale = 1f + 0.6f * drift

                Image(
                    bitmap = chunk.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .size(cellW, cellH)
                        .offset(x = ox, y = oy)
                        .graphicsLayer {
                            scaleX = blobScale
                            scaleY = blobScale
                        }
                        .blur(blurAmount.dp, BlurredEdgeTreatment.Unbounded)
                )
            }
        }
    }
}

/** Create a bitmap with radial alpha fade: opaque center → transparent edges. */
private fun createFeatheredChunk(src: Bitmap, w: Int, h: Int): Bitmap {
    val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(result)

    // Draw original chunk
    canvas.drawBitmap(src, 0f, 0f, null)

    // Punch out edges with radial gradient alpha mask (DST_IN keeps center, fades edges)
    val maskPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    maskPaint.xfermode = android.graphics.PorterDuffXfermode(
        android.graphics.PorterDuff.Mode.DST_IN
    )
    val radius = maxOf(w, h) * 0.75f
    maskPaint.shader = android.graphics.RadialGradient(
        w / 2f, h / 2f, radius,
        intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0x00FFFFFF),
        floatArrayOf(0f, 0.45f, 1f),
        android.graphics.Shader.TileMode.CLAMP
    )
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), maskPaint)

    src.recycle()
    return result
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
private fun UserMessageBubble(message: Messages) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(rDp(12.dp)),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
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

/** Header part of assistant message: RAG results, tool chain, thinking block, non-text content. */
@Composable
private fun AssistantMessageHeader(message: Messages) {
    val hasRagResults = remember(message.ragResults) {
        message.ragResults?.isNotEmpty() == true
    }
    val hasToolChainSteps = remember(message.toolChainSteps) {
        message.toolChainSteps?.isNotEmpty() == true
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(rDp(6.dp))
    ) {
        if (hasRagResults) {
            message.ragResults?.let { results ->
                SavedRagResultsDisplay(results = results)
            }
        }

        if (message.agentPlan != null) {
            AgentExecutionView(
                plan = message.agentPlan,
                steps = message.toolChainSteps ?: emptyList(),
                summary = message.agentSummary,
                phase = AgentPhase.Complete
            )
        } else if (hasToolChainSteps) {
            message.toolChainSteps?.let { steps ->
                ToolChainDisplay(steps = steps, isLive = false)
            }
        }

        // Non-text content types
        when (message.content.contentType) {
            ContentType.Image -> ImageMessageBubble(message)
            ContentType.PluginResult -> PluginResultCard(message = message)
            else -> {
                // Thinking block (markdown body is handled by lazyMarkdownItems)
                val thinkingContent by produceState<String?>(
                    initialValue = null,
                    key1 = message.content.content
                ) {
                    if (message.content.content.contains("<think>")) {
                        value = parseThinkingTags(message.content.content).thinkingContent
                    }
                }
                thinkingContent?.let { ThinkingBlock(it) }
            }
        }
    }
}

/** Footer part of assistant message: metrics + action row. */
@Composable
private fun AssistantMessageFooter(
    message: Messages,
    ttsPlayingMsgId: String?,
    ttsIsPlaying: Boolean,
    ttsSynthesizing: Boolean,
    ttsModelLoaded: Boolean,
    onSpeak: (Messages) -> Unit,
    onStopTTS: () -> Unit,
    onRegenerate: (() -> Unit)?,
    isRegenerateEnabled: Boolean
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
    val isTextContent = message.content.contentType == ContentType.Text
    val isThisMessagePlaying = ttsPlayingMsgId == message.msgId && ttsIsPlaying
    val isThisMessageSynthesizing = ttsPlayingMsgId == message.msgId && ttsSynthesizing

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(rDp(6.dp))
    ) {
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
        if (isTextContent && message.content.content.isNotEmpty()) {
            // Strip thinking tags for the text content passed to action row
            val textContent = remember(message.content.content) {
                if (message.content.content.contains("<think>")) {
                    message.content.content.replace(
                        Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), ""
                    ).trim()
                } else message.content.content
            }
            if (textContent.isNotEmpty()) {
                MessageActionRow(
                    message = message,
                    textContent = textContent,
                    isPlaying = isThisMessagePlaying,
                    isSynthesizing = isThisMessageSynthesizing,
                    ttsModelLoaded = ttsModelLoaded,
                    onSpeak = onSpeak,
                    onStopTTS = onStopTTS,
                    onRegenerate = onRegenerate,
                    isRegenerateEnabled = isRegenerateEnabled
                )
            }
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
    onStopTTS: () -> Unit,
    onRegenerate: (() -> Unit)? = null,
    isRegenerateEnabled: Boolean = true
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
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
                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("message", textContent))) }
                    showCopied = true
                },
                contentDescription = "Copy"
            ))
        }

        // Regenerate action (always visible, disabled during generation)
        if (onRegenerate != null) {
            add(ActionItem(
                icon = ActionIcon.Vector(Icons.Default.Refresh),
                onClick = { if (isRegenerateEnabled) onRegenerate() },
                contentDescription = "Regenerate",
                enabled = isRegenerateEnabled
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