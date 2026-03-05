package com.dark.tool_neuron.ui.screen.home

import android.content.ClipData
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import com.dark.tool_neuron.ui.components.ExpandCollapseIcon
import com.dark.tool_neuron.ui.theme.Motion
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
import androidx.compose.ui.graphics.vector.ImageVector
import com.dark.tool_neuron.R
import com.dark.tool_neuron.models.ui.ActionIcon
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.models.ui.ActionItem
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
import com.dark.tool_neuron.viewmodel.AgentPhase
import com.dark.tool_neuron.viewmodel.ChatViewModel
import com.dark.tool_neuron.viewmodel.LLMModelViewModel
import com.dark.tool_neuron.models.ModelType
import com.dark.tool_neuron.models.engine_schema.DecodingMetrics
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.debounce
import java.util.Base64
import com.dark.tool_neuron.ui.icons.TnIcons

// ── Pre-compiled regex (avoid allocation in composition) ──

private val THINK_TAG_REGEX = Regex("<think>(.*?)</think>", RegexOption.DOT_MATCHES_ALL)
private val THINK_OPEN_TAG = "<think>"
private val THINK_CLOSE_TAG = "</think>"

data class ParsedMessage(
    val thinkingContent: String?,
    val actualContent: String,
    val isThinkingInProgress: Boolean = false
)

fun parseThinkingTags(content: String): ParsedMessage {
    // Fast path: no think tags at all
    if (!content.contains(THINK_OPEN_TAG)) {
        return ParsedMessage(null, content.trim())
    }

    // Completed thinking: <think>...</think> present
    val thinkingMatch = THINK_TAG_REGEX.find(content)
    if (thinkingMatch != null) {
        val thinkingContent = thinkingMatch.groupValues[1].trim()
        val actualContent = content.replace(THINK_TAG_REGEX, "").trim()
        return ParsedMessage(
            thinkingContent = thinkingContent.ifEmpty { null },
            actualContent = actualContent
        )
    }

    // In-progress thinking: <think> opened but no </think> yet (streaming)
    val openIdx = content.indexOf(THINK_OPEN_TAG)
    val thinkingContent = content.substring(openIdx + THINK_OPEN_TAG.length).trim()
    val beforeThink = content.substring(0, openIdx).trim()
    return ParsedMessage(
        thinkingContent = thinkingContent.ifEmpty { null },
        actualContent = beforeThink,
        isThinkingInProgress = true
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun BodyContent(
    paddingValues: PaddingValues,
    chatViewModel: ChatViewModel,
    llmModelViewModel: LLMModelViewModel
) {
    val messages = chatViewModel.messages
    val isGenerating by chatViewModel.isGenerating.collectAsStateWithLifecycle()
    val streamingUserMessage by chatViewModel.streamingUserMessage.collectAsStateWithLifecycle()
    val streamingAssistantMessage by chatViewModel.streamingAssistantMessage.collectAsStateWithLifecycle()
    val streamingImage by chatViewModel.streamingImage.collectAsStateWithLifecycle()
    val imageProgress by chatViewModel.imageGenerationProgress.collectAsStateWithLifecycle()
    val imageStep by chatViewModel.imageGenerationStep.collectAsStateWithLifecycle()
    val showDynamicWindow by chatViewModel.showDynamicWindow.collectAsStateWithLifecycle()
    val currentGenerationType by chatViewModel.currentGenerationType.collectAsStateWithLifecycle()
    val currentRagResults by chatViewModel.currentRagResults.collectAsStateWithLifecycle()
    val appState by com.dark.tool_neuron.state.AppStateManager.appState.collectAsStateWithLifecycle()
    val toolChainSteps by chatViewModel.toolChainSteps.collectAsStateWithLifecycle()
    val currentToolChainRound by chatViewModel.currentToolChainRound.collectAsStateWithLifecycle()
    val agentPhase by chatViewModel.agentPhase.collectAsStateWithLifecycle()
    val agentPlan by chatViewModel.agentPlan.collectAsStateWithLifecycle()
    val agentSummary by chatViewModel.agentSummary.collectAsStateWithLifecycle()
    val ttsPlayingMsgId by chatViewModel.ttsPlayingMsgId.collectAsStateWithLifecycle()
    val ttsIsPlaying by chatViewModel.ttsIsPlaying.collectAsStateWithLifecycle()
    val ttsSynthesizing by chatViewModel.ttsSynthesizing.collectAsStateWithLifecycle()
    val ttsModelLoaded by chatViewModel.ttsModelLoaded.collectAsStateWithLifecycle()

    // Image blur setting — collected once, passed down to avoid per-message DataStore creation
    val context = androidx.compose.ui.platform.LocalContext.current
    val imageBlurEnabled by remember { com.dark.tool_neuron.data.AppSettingsDataStore(context).imageBlurEnabled }
        .collectAsStateWithLifecycle(initialValue = true)

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
                    isImageGeneration = currentGenerationType == ModelType.IMAGE_GENERATION,
                    ragResults = currentRagResults,
                    appState = appState,
                    messages = messages,
                    toolChainSteps = toolChainSteps,
                    currentToolChainRound = currentToolChainRound,
                    agentPhase = agentPhase,
                    agentPlan = agentPlan,
                    agentSummary = agentSummary
                )
            } else {
                val deduped = remember(messages.size) { messages.distinctBy { it.msgId } }
                val lastAssistantIndex = remember(deduped.size) { deduped.indexOfLast { it.role == Role.Assistant } }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {

                    deduped.forEachIndexed { index, message ->
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
                                    AssistantMessageHeader(message, imageBlurEnabled)
                                }
                                // Markdown content — each element is a lazy item
                                if (message.content.contentType == ContentType.Text) {
                                    val raw = message.content.content
                                    val parsedText = if (raw.contains("<think>")) {
                                        raw.replace(THINK_TAG_REGEX, "").trim()
                                    } else raw
                                    if (parsedText.isNotEmpty()) {
                                        lazyMarkdownItems(
                                            text = parsedText,
                                            keyPrefix = message.msgId,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp)
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
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }

        // Scrim + Dynamic Action Window — single AnimatedVisibility to avoid double state reads
        AnimatedVisibility(
            visible = showDynamicWindow,
            enter = fadeIn(Motion.entrance()),
            exit = fadeOut(Motion.exit())
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Scrim background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            chatViewModel.hideDynamicWindow()
                        }
                )

                // Window content with spring animation
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    val ragCount by com.dark.tool_neuron.plugins.PluginManager.enabledPluginNames.collectAsStateWithLifecycle()
                    val ttsLoaded by com.dark.tool_neuron.tts.TTSManager.isModelLoaded.collectAsStateWithLifecycle()

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
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
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

        Spacer(modifier = Modifier.height(16.dp))
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
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
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
                    .clip(RoundedCornerShape(12.dp))
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


private data class ChunkAnimParams(
    val ampX: Float, val ampY: Float,
    val durX: Int, val durY: Int,
    val delayX: Int, val delayY: Int,
    val scatterX: Float, val scatterY: Float
)

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
                val params = remember {
                    val rng = Random(index * 37 + 7)
                    ChunkAnimParams(
                        ampX = 25f + rng.nextFloat() * 35f,
                        ampY = 20f + rng.nextFloat() * 30f,
                        durX = 3500 + rng.nextInt(2000),
                        durY = 3000 + rng.nextInt(2000),
                        delayX = rng.nextInt(700),
                        delayY = rng.nextInt(700),
                        scatterX = (rng.nextFloat() - 0.5f) * 50f,
                        scatterY = (rng.nextFloat() - 0.5f) * 40f
                    )
                }

                val driftX by infiniteTransition.animateFloat(
                    initialValue = -params.ampX, targetValue = params.ampX,
                    animationSpec = infiniteRepeatable(
                        tween(params.durX, delayMillis = params.delayX, easing = LinearOutSlowInEasing),
                        RepeatMode.Reverse
                    ), label = "dx$index"
                )
                val driftY by infiniteTransition.animateFloat(
                    initialValue = -params.ampY, targetValue = params.ampY,
                    animationSpec = infiniteRepeatable(
                        tween(params.durY, delayMillis = params.delayY, easing = LinearOutSlowInEasing),
                        RepeatMode.Reverse
                    ), label = "dy$index"
                )

                val col = index % cols
                val row = index / cols
                val ox = cellW * col + ((driftX + params.scatterX) * drift).dp
                val oy = cellH * row + ((driftY + params.scatterY) * drift).dp
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
    val parsedMessage = remember(text) { parseThinkingTags(text) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (parsedMessage.thinkingContent != null) {
            ThinkingBlock(
                thinkingText = parsedMessage.thinkingContent,
                isStreaming = parsedMessage.isThinkingInProgress
            )
        }

        if (parsedMessage.actualContent.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
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
private fun ThinkingBlock(
    thinkingText: String,
    isStreaming: Boolean = false
) {
    // Auto-expand while streaming, auto-collapse when done
    var userToggled by remember { mutableStateOf(false) }
    var userExpandState by remember { mutableStateOf(false) }

    val isExpanded = if (userToggled) userExpandState else isStreaming

    // Pulsing dot animation for streaming state
    val infiniteTransition = rememberInfiniteTransition(label = "thinkPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "thinkPulseAlpha"
    )

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 2.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        userToggled = true
                        userExpandState = !isExpanded
                    }
                    .padding(vertical = 8.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = TnIcons.BulbFilled,
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer { alpha = if (isStreaming) pulseAlpha else 1f },
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = if (isStreaming) "Thinking…" else "Thought",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                ExpandCollapseIcon(isExpanded = isExpanded, size = 20.dp)
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = Motion.Enter,
                exit = Motion.Exit
            ) {
                Column {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                    Text(
                        text = thinkingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
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
                imageVector = TnIcons.User,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(0.4f)
            )
            Spacer(Modifier.height(16.dp))
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
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 5.dp)
                .widthIn(max = 280.dp)
        ) {
            SelectionContainer {
                MarkdownText(
                    text = message.content.content,
                    modifier = Modifier.padding(
                        horizontal = 12.dp,
                        vertical = 6.dp
                    )
                )
            }
        }
    }
}

/** Header part of assistant message: RAG results, tool chain, thinking block, non-text content. */
@Composable
private fun AssistantMessageHeader(message: Messages, imageBlurEnabled: Boolean = true) {
    val hasRagResults = remember(message.ragResults) {
        message.ragResults?.isNotEmpty() == true
    }
    val hasToolChainSteps = remember(message.toolChainSteps) {
        message.toolChainSteps?.isNotEmpty() == true
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
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
            ContentType.Image -> ImageMessageBubble(message, imageBlurEnabled)
            ContentType.PluginResult -> PluginResultCard(message = message)
            else -> {
                // Thinking block (markdown body is handled by lazyMarkdownItems)
                val parsed = remember(message.content.content) {
                    if (message.content.content.contains("<think>")) {
                        parseThinkingTags(message.content.content)
                    } else null
                }
                parsed?.thinkingContent?.let { ThinkingBlock(it) }
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
        verticalArrangement = Arrangement.spacedBy(6.dp)
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
                    message.content.content.replace(THINK_TAG_REGEX, "").trim()
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
                icon = ActionIcon.Vector(TnIcons.PlayerStop),
                onClick = { onStopTTS() },
                contentDescription = "Stop"
            ))
            isSynthesizing -> add(ActionItem(
                icon = ActionIcon.Vector(TnIcons.Volume),
                onClick = { onStopTTS() },
                contentDescription = "Synthesizing",
                isLoading = true
            ))
            else -> add(ActionItem(
                icon = ActionIcon.Vector(TnIcons.Volume),
                onClick = { onSpeak(message) },
                contentDescription = "Speak"
            ))
        }

        // Copy action
        if (showCopied) {
            add(ActionItem(
                icon = ActionIcon.Vector(TnIcons.CircleCheck),
                onClick = {},
                contentDescription = "Copied"
            ))
        } else {
            add(ActionItem(
                icon = ActionIcon.Vector(TnIcons.Copy),
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
                icon = ActionIcon.Vector(TnIcons.Refresh),
                onClick = { if (isRegenerateEnabled) onRegenerate() },
                contentDescription = "Regenerate",
                enabled = isRegenerateEnabled
            ))
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
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
private fun ImageMessageBubble(message: Messages, imageBlurEnabled: Boolean = true) {
    var isImageRevealed by remember(imageBlurEnabled) { mutableStateOf(!imageBlurEnabled) }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(12.dp)
    ) {
        message.content.imagePrompt?.let { prompt ->
            Text(
                text = "Prompt: $prompt",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
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
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { isImageRevealed = !isImageRevealed },
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(12.dp),
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
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = TnIcons.Sparkles,
                                    contentDescription = "Reveal image",
                                    modifier = Modifier.size(32.dp),
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
                modifier = Modifier.padding(horizontal = 4.dp)
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
        if (metrics.totalTimeMs > 0f) "%.1f".format(metrics.totalTimeMs / 1000f) else null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        // Summary row
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = TnIcons.Gauge,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
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
                        text = "${metrics.tokensEvaluated + metrics.tokensPredicted} tokens",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                ExpandCollapseIcon(isExpanded = isExpanded, size = 18.dp)
            }
        }

        // Detailed metrics
        AnimatedVisibility(
            visible = isExpanded,
            enter = Motion.Enter,
            exit = Motion.Exit
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricRow(
                        icon = TnIcons.Coins,
                        label = "Total Tokens",
                        value = (metrics.tokensEvaluated + metrics.tokensPredicted).toString()
                    )

                    if (metrics.tokensEvaluated > 0) {
                        MetricRow(
                            icon = TnIcons.Prompt,
                            label = "Prompt Tokens",
                            value = metrics.tokensEvaluated.toString()
                        )
                    }

                    if (metrics.tokensPredicted > 0) {
                        MetricRow(
                            icon = TnIcons.Wand,
                            label = "Generated Tokens",
                            value = metrics.tokensPredicted.toString()
                        )
                    }

                    MetricRow(
                        icon = TnIcons.Gauge,
                        label = "Speed",
                        value = "$formattedSpeed t/s"
                    )

                    if (metrics.timeToFirstTokenMs > 0f) {
                        MetricRow(
                            icon = TnIcons.Clock,
                            label = "Time to First Token",
                            value = "${"%.0f".format(metrics.timeToFirstTokenMs)} ms"
                        )
                    }

                    formattedTime?.let { time ->
                        MetricRow(
                            icon = TnIcons.Clock,
                            label = "Total Duration",
                            value = "$time s"
                        )
                    }

                    // Memory metrics section
                    memoryMetrics?.let { mem ->
                        if (mem.modelSizeMB > 0 || mem.peakMemoryMB > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )

                            Text(
                                text = "Memory",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )

                            if (mem.modelSizeMB > 0) {
                                MetricRow(
                                    icon = TnIcons.Coins,
                                    label = "Model Size",
                                    value = "${mem.modelSizeMB} MB"
                                )
                            }

                            if (mem.contextSizeMB > 0) {
                                MetricRow(
                                    icon = TnIcons.Coins,
                                    label = "Context Size",
                                    value = "${mem.contextSizeMB} MB"
                                )
                            }

                            if (mem.peakMemoryMB > 0) {
                                MetricRow(
                                    icon = TnIcons.Coins,
                                    label = "Peak Memory",
                                    value = "${mem.peakMemoryMB} MB"
                                )
                            }

                            if (mem.memoryUsagePercent > 0) {
                                MetricRow(
                                    icon = TnIcons.Coins,
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
            .padding(horizontal = 12.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = TnIcons.Coins,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
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

                ExpandCollapseIcon(isExpanded = isExpanded, size = 18.dp)
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = Motion.Enter,
            exit = Motion.Exit
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (metrics.modelSizeMB > 0) {
                        MetricRow(
                            icon = TnIcons.Coins,
                            label = "Model Size",
                            value = "${metrics.modelSizeMB} MB"
                        )
                    }

                    if (metrics.contextSizeMB > 0) {
                        MetricRow(
                            icon = TnIcons.Coins,
                            label = "Context Size",
                            value = "${metrics.contextSizeMB} MB"
                        )
                    }

                    if (metrics.peakMemoryMB > 0) {
                        MetricRow(
                            icon = TnIcons.Coins,
                            label = "Peak Memory",
                            value = "${metrics.peakMemoryMB} MB"
                        )
                    }

                    if (metrics.memoryUsagePercent > 0) {
                        MetricRow(
                            icon = TnIcons.Coins,
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
            .padding(horizontal = 12.dp)
    ) {
        // Summary row
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = TnIcons.Photo,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
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

                ExpandCollapseIcon(isExpanded = isExpanded, size = 18.dp)
            }
        }

        // Detailed metrics
        AnimatedVisibility(
            visible = isExpanded,
            enter = Motion.Enter,
            exit = Motion.Exit
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricRow(
                        icon = TnIcons.Photo,
                        label = "Dimensions",
                        value = "${metrics.width} × ${metrics.height}"
                    )

                    MetricRow(
                        icon = TnIcons.SortAscending,
                        label = "Steps",
                        value = metrics.steps.toString()
                    )

                    MetricRow(
                        icon = TnIcons.Adjustments,
                        label = "CFG Scale",
                        value = "%.1f".format(metrics.cfgScale)
                    )

                    MetricRow(
                        icon = TnIcons.Coins,
                        label = "Seed",
                        value = metrics.seed.toString()
                    )

                    MetricRow(
                        icon = TnIcons.CalendarTime,
                        label = "Scheduler",
                        value = metrics.scheduler.uppercase()
                    )

                    MetricRow(
                        icon = TnIcons.Clock,
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
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
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
            .padding(horizontal = 12.dp)
    ) {
        // Summary row
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = TnIcons.Database,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
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

                ExpandCollapseIcon(isExpanded = isExpanded, size = 18.dp)
            }
        }

        // Detailed results
        AnimatedVisibility(
            visible = isExpanded,
            enter = Motion.Enter,
            exit = Motion.Exit
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                            modifier = Modifier.padding(top = 4.dp)
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
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "$scorePercent%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = scoreColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
            .padding(horizontal = 12.dp)
    ) {
        // Summary row
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = TnIcons.Database,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
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

                ExpandCollapseIcon(isExpanded = isExpanded, size = 18.dp)
            }
        }

        // Detailed results
        AnimatedVisibility(
            visible = isExpanded,
            enter = Motion.Enter,
            exit = Motion.Exit
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                            modifier = Modifier.padding(top = 4.dp)
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
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "$scorePercent%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = scoreColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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