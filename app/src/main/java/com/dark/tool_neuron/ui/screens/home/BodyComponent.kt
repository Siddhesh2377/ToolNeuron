package com.dark.tool_neuron.ui.screens.home

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Token
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.R
import com.dark.tool_neuron.model.ChatUiState
import com.dark.tool_neuron.model.DecodingStage
import com.dark.tool_neuron.model.Message
import com.dark.tool_neuron.model.Role
import com.dark.tool_neuron.ui.components.MarkdownText
import com.dark.tool_neuron.ui.components.RegenerateModelPickerDialog
import com.dark.tool_neuron.ui.theme.Coral
import com.dark.tool_neuron.ui.theme.SlateGrey
import com.dark.tool_neuron.ui.theme.rDP
import com.dark.tool_neuron.ui.theme.rSp
import com.dark.tool_neuron.viewModel.chatViewModel.ChatScreenViewModel
import com.dark.tool_neuron.viewModel.chatViewModel.TTSViewModel
import com.dark.tool_neuron.worker.UIStateManager
import com.dark.tool_neuron.worker.UIStateManager.isGenerating
import com.dark.plugins.manager.PluginManager
import com.dark.plugins.model.LoadedPlugin
import com.mp.data_hub_lib.model.Doc
import com.mp.data_hub_lib.model.RagResult
import com.mp.plugin_api.api.PluginApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

// OPTIMIZATION 1: Extract stable message state to prevent unnecessary recompositions
private data class MessageDisplayState(
    val isUser: Boolean,
    val isWaitingForFirstToken: Boolean,
    val isCurrentlyStreaming: Boolean,
    val hasThought: Boolean
)

@Composable
fun ChatBubble(
    message: Message,
    viewModel: ChatScreenViewModel,
    ttsViewModel: TTSViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // OPTIMIZATION: Compute display state once and make it stable
    val displayState = remember(message.id, message.role, message.text, message.thought, uiState) {
        val isStreaming = when (uiState) {
            is ChatUiState.DecodingStream -> (uiState as ChatUiState.DecodingStream).messageId == message.id
            is ChatUiState.Generating -> (uiState as ChatUiState.Generating).messageId == message.id
            else -> false
        }

        MessageDisplayState(
            isUser = message.role == Role.User,
            isWaitingForFirstToken = message.text.isEmpty() && isStreaming,
            isCurrentlyStreaming = isStreaming,
            hasThought = !message.thought.isNullOrBlank()
        )
    }

    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn() + scaleIn(
            initialScale = 0.8f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ),
        exit = fadeOut() + scaleOut()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (displayState.isUser) Arrangement.End else Arrangement.Start
        ) {
            Column {
                if (displayState.hasThought && !displayState.isUser) {
                    ThinkingChatUI(message)
                    Spacer(Modifier.height(rDP(8.dp)))
                }

                // OPTIMIZATION: Removed nested AnimatedContent - directly switch based on role
                when (message.role) {
                    Role.User -> UserChatUI(
                        message = message,
                        onMessageDelete = { viewModel.deleteMessage(it) }
                    )

                    Role.Assistant -> {
                        if (displayState.isWaitingForFirstToken) {
                            DecodingPlaceholder()
                        } else {
                            RegularChatUI(
                                message = message,
                                viewModel = viewModel,
                                ttsViewModel = ttsViewModel,
                                isStreaming = displayState.isCurrentlyStreaming
                            )
                        }
                    }

                    Role.Tool -> ToolChatUI(
                        message = message,
                        viewModel = viewModel,
                        ttsViewModel = ttsViewModel,
                        onMessageDelete = { viewModel.deleteMessage(message.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DecodingPlaceholder() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = rDP(8.dp)),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "decoding")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alpha"
        )

        Text(
            text = "Decoding...",
            color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic
        )
    }
}

@Composable
private fun UserChatUI(
    message: Message,
    onMessageDelete: (String) -> Unit
) {
    val radius = with(LocalDensity.current) { rDP(12.dp) }
    val corner = RoundedCornerShape(radius)
    val clipboardManager = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.widthIn(max = rDP(240.dp)),
        horizontalAlignment = Alignment.End
    ) {
        Text(
            modifier = Modifier
                .clip(corner)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                .padding(horizontal = rDP(14.dp), vertical = rDP(8.dp)),
            text = message.text,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(rDP(10.dp)))

        MessageActionRow(
            onCopy = {
                scope.launch {
                    clipboardManager.setClipEntry(
                        ClipEntry(ClipData.newPlainText("message", message.text))
                    )
                    Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                }
            },
            onShare = { shareText(context, message.text) },
            onDelete = { onMessageDelete(message.id) }
        )
    }
}

@SuppressLint("DefaultLocale")
@Composable
private fun RegularChatUI(
    message: Message,
    viewModel: ChatScreenViewModel,
    ttsViewModel: TTSViewModel,
    isStreaming: Boolean // OPTIMIZATION: Pass computed state instead of recalculating
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isPlayingAudio by ttsViewModel.isPlaying.collectAsStateWithLifecycle()
    val audioProgress by ttsViewModel.audioProgress.collectAsStateWithLifecycle()
    val isInitialized by ttsViewModel.isInitialized.collectAsStateWithLifecycle()

    var showRegenerateDialog by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = rDP(4.dp))
    ) {
        // OPTIMIZATION: Remove Crossfade - directly render based on streaming state
        if (isStreaming && message.text.isEmpty()) {
            DecodingStageLayout(stage = DecodingStage.Decoding)
        } else {
            // OPTIMIZATION: Only use MarkdownText when needed, plain Text for streaming
            if (isStreaming) {
                Text(
                    text = message.text,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                MarkdownText(
                    text = message.text,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        DecodingMetricsDisplay(message.decodingMetrics)

        Spacer(Modifier.height(rDP(8.dp)))

        message.ragResult?.let {
            RagResultCard(rag = it)
            Spacer(Modifier.height(rDP(8.dp)))
        }

        AssistantMessageActions(
            message = message,
            scope = scope,
            context = context,
            ttsViewModel = ttsViewModel,
            isPlayingAudio = isPlayingAudio,
            audioProgress = audioProgress,
            isInitialized = isInitialized,
            onRegenerateClick = { showRegenerateDialog = true },
            onDeleteClick = { viewModel.deleteMessage(message.id) }
        )
    }

    if (showRegenerateDialog) {
        RegenerateModelPickerDialog(
            viewModel = viewModel,
            messageId = message.id
        ) { showRegenerateDialog = false }
    }
}

@Composable
private fun DecodingMetricsDisplay(metrics: com.dark.tool_neuron.model.DecodingMetrics) {
    // OPTIMIZATION: Only render if metrics exist
    if (metrics.durationMs.toInt() == 0) return

    Spacer(Modifier.height(rDP(9.dp)))
    Text(
        text = "Decoded with\n${metrics.modelName} in ${metrics.durationMs} ms",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f)
    )
}

@SuppressLint("DefaultLocale")
@Composable
fun RagResultCard(rag: RagResult) {
    var ragExpanded by rememberSaveable { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { ragExpanded = !ragExpanded }
            ) {
                Text(
                    text = "RAG Result (${rag.docs.size} docs)",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = if (ragExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (ragExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            AnimatedVisibility(
                visible = ragExpanded,
                enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    val stats = rag.stats
                    Text(
                        text = "Stats → Docs: ${stats.tokenCount}, Time: ${stats.totalTime}ms, TPS: ${
                            String.format("%.2f", stats.tokensPerSecond)
                        }",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    rag.docs.forEach { doc ->
                        Spacer(modifier = Modifier.height(4.dp))
                        RagDocItem(doc)
                    }
                }
            }
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
private fun RagDocItem(doc: Doc) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
            .padding(8.dp)
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = doc.text.take(120) + if (doc.text.length > 120) "..." else "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Similarity: ${String.format("%.3f", doc.similarity)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
private fun ThinkingChatUI(message: Message) {
    var showThinkingText by rememberSaveable { mutableStateOf(false) }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(120)) + expandVertically(
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
        ) + slideInVertically(initialOffsetY = { -it / 6 }),
        exit = fadeOut(tween(120)) + shrinkVertically(
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
        ) + slideOutVertically(targetOffsetY = { -it / 6 })
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showThinkingText = !showThinkingText }
                .clip(RoundedCornerShape(rDP(8.dp)))
                .background(Color(0xFF0F172A))
                .border(rDP(1.dp), Color(0xFF334155), RoundedCornerShape(rDP(8.dp)))
                .animateContentSize(tween(180, easing = FastOutSlowInEasing))
        ) {
            Text(
                text = if (showThinkingText) "Thought:\n${message.thought}" else "Thinking... (tap to expand)",
                modifier = Modifier.padding(rDP(8.dp)),
                color = Color(0xFFCBD5E1),
                fontSize = rSp(12.sp),
                lineHeight = rSp(18.sp),
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun MessageActionRow(
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val iconSize = rDP(14.dp)

    Row(horizontalArrangement = Arrangement.spacedBy(rDP(12.dp))) {
        ActionIcon(
            painter = painterResource(R.drawable.copy),
            contentDescription = "Copy",
            onClick = onCopy,
            iconSize = iconSize
        )
        ActionIcon(
            imageVector = Icons.Rounded.Share,
            contentDescription = "Share",
            onClick = onShare,
            iconSize = iconSize
        )
        ActionIcon(
            imageVector = Icons.Rounded.DeleteOutline,
            contentDescription = "Delete",
            onClick = onDelete,
            iconSize = iconSize
        )
    }
}

@Composable
private fun AssistantMessageActions(
    message: Message,
    scope: CoroutineScope,
    context: Context,
    ttsViewModel: TTSViewModel,
    isPlayingAudio: Boolean,
    audioProgress: Float,
    isInitialized: Boolean,
    onRegenerateClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val clipboardManager = LocalClipboard.current
    val iconSize = rDP(14.dp)

    Row(
        horizontalArrangement = Arrangement.spacedBy(rDP(12.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionIcon(
            painter = painterResource(R.drawable.copy),
            contentDescription = "Copy",
            onClick = {
                scope.launch {
                    clipboardManager.setClipEntry(
                        ClipEntry(ClipData.newPlainText("message", message.text))
                    )
                    Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                }
            },
            iconSize = iconSize
        )

// In your AssistantMessageActions composable, modify the TTS button:
        TTSActionButton(
            isPlaying = isPlayingAudio,
            progress = audioProgress,
            isInitialized = isInitialized,
            iconSize = iconSize,
            onClick = {
                if (isPlayingAudio) {
                    ttsViewModel.stopPlayback()
                } else {
                    scope.launch(Dispatchers.IO) {
                        // Try to replay saved audio first
                        val replayed = ttsViewModel.playAgainFromMessage(
                            context = context,
                            messageId = message.id,
                            currentText = message.text
                        )

                        // If no saved audio or speaker changed, generate new
                        if (!replayed) {
                            val normalized = ttsViewModel.normalizeText(message.text)
                            ttsViewModel.generateAndPlayAudio(
                                text = normalized,
                                context = context,
                                autoSave = true,
                                messageId = message.id // Pass message ID for future saves
                            )
                        }
                    }
                }
            }
        )

        ActionIcon(
            painter = painterResource(R.drawable.regen),
            contentDescription = "Regenerate",
            onClick = onRegenerateClick,
            iconSize = iconSize
        )

        ActionIcon(
            imageVector = Icons.Rounded.Share,
            contentDescription = "Share",
            onClick = { shareText(context, message.text) },
            iconSize = iconSize
        )

        ActionIcon(
            imageVector = Icons.Rounded.DeleteOutline,
            contentDescription = "Delete",
            onClick = onDeleteClick,
            iconSize = iconSize
        )
    }
}

@Composable
private fun ActionIcon(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    iconSize: Dp,
    enabled: Boolean = true
) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.7f else 0.3f),
        modifier = Modifier
            .size(iconSize)
            .clickable(enabled = enabled) { onClick() }
    )
}

@Composable
private fun ActionIcon(
    painter: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    iconSize: Dp,
    enabled: Boolean = true
) {
    Icon(
        painter = painter,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.7f else 0.3f),
        modifier = Modifier
            .size(iconSize)
            .clickable(enabled = enabled) { onClick() }
    )
}

@Composable
private fun TTSActionButton(
    isPlaying: Boolean,
    progress: Float,
    isInitialized: Boolean,
    iconSize: Dp,
    onClick: () -> Unit
) {
    Box(contentAlignment = Alignment.Center) {
        if (isPlaying && progress > 0f) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(iconSize + 4.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                strokeWidth = 2.dp,
                trackColor = ProgressIndicatorDefaults.circularIndeterminateTrackColor
            )
        }

        Icon(
            painter = painterResource(if (isPlaying) R.drawable.stop else R.drawable.speaker),
            contentDescription = if (isPlaying) "Stop audio" else "Play audio",
            tint = MaterialTheme.colorScheme.primary.copy(alpha = if (isInitialized) 0.7f else 0.3f),
            modifier = Modifier
                .size(iconSize)
                .clickable(enabled = isInitialized) { onClick() }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolChatUI(
    message: Message,
    viewModel: ChatScreenViewModel,
    ttsViewModel: TTSViewModel,
    onMessageDelete: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val uiState by UIStateManager.uiState.collectAsStateWithLifecycle()
    val isPlaying by ttsViewModel.isPlaying.collectAsStateWithLifecycle()
    val progress by ttsViewModel.audioProgress.collectAsStateWithLifecycle()
    val initialized by ttsViewModel.isInitialized.collectAsStateWithLifecycle()

    Column {
        AssistTag(message.tool?.toolName ?: "Unknown Tool")
        Spacer(Modifier.height(rDP(6.dp)))

        ToolUIContent(
            uiState = uiState,
            message = message,
            viewModel = viewModel,
            isGenerating = isGenerating()
        )

        Spacer(Modifier.height(rDP(12.dp)))

        MarkdownText(
            text = message.text,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium
        )

        DecodingMetricsDisplay(message.decodingMetrics)

        Spacer(Modifier.height(rDP(12.dp)))

        ToolMessageActions(
            message = message,
            scope = scope,
            context = context,
            ttsViewModel = ttsViewModel,
            isPlaying = isPlaying,
            progress = progress,
            initialized = initialized,
            onDeleteClick = { onMessageDelete(message.id) }
        )
    }
}

@Composable
private fun ToolMessageActions(
    message: Message,
    scope: CoroutineScope,
    context: Context,
    ttsViewModel: TTSViewModel,
    isPlaying: Boolean,
    progress: Float,
    initialized: Boolean,
    onDeleteClick: () -> Unit
) {
    val iconSize = rDP(14.dp)
    val clipboardManager = LocalClipboard.current

    Row(
        horizontalArrangement = Arrangement.spacedBy(rDP(12.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionIcon(
            painter = painterResource(R.drawable.copy),
            contentDescription = "Copy",
            onClick = {
                scope.launch {
                    clipboardManager.setClipEntry(
                        ClipEntry(ClipData.newPlainText("message", message.text))
                    )
                    Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                }
            },
            iconSize = iconSize
        )

        // In your AssistantMessageActions composable, modify the TTS button:
        TTSActionButton(
            isPlaying = isPlaying,
            progress = progress,
            isInitialized = initialized,
            iconSize = iconSize,
            onClick = {
                if (isPlaying) {
                    ttsViewModel.stopPlayback()
                } else {
                    scope.launch(Dispatchers.IO) {
                        // Try to replay saved audio first
                        val replayed = ttsViewModel.playAgainFromMessage(
                            context = context,
                            messageId = message.id,
                            currentText = message.text
                        )

                        // If no saved audio or speaker changed, generate new
                        if (!replayed) {
                            val normalized = ttsViewModel.normalizeText(message.text)
                            ttsViewModel.generateAndPlayAudio(
                                text = normalized,
                                context = context,
                                autoSave = true,
                                messageId = message.id // Pass message ID for future saves
                            )
                        }
                    }
                }
            }
        )

        ActionIcon(
            imageVector = Icons.Rounded.Share,
            contentDescription = "Share",
            onClick = { shareText(context, message.text) },
            iconSize = iconSize
        )

        ActionIcon(
            imageVector = Icons.Rounded.DeleteOutline,
            contentDescription = "Delete",
            onClick = onDeleteClick,
            iconSize = iconSize
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DecodingStageLayout(stage: DecodingStage) {
    val (icon, text) = when (stage) {
        DecodingStage.PreparingPrompt -> Icons.Rounded.Build to "Preparing your prompt..."
        DecodingStage.EncodingInput -> Icons.Rounded.Code to "Encoding the input..."
        DecodingStage.LoadingModel -> Icons.Rounded.Memory to "Loading the model..."
        DecodingStage.Decoding -> Icons.Rounded.Token to "Decoding the response..."
        DecodingStage.Rendering -> Icons.Rounded.Draw to "Rendering text..."
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(rDP(10.dp)))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.03f)
                    )
                )
            )
            .border(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                RoundedCornerShape(rDP(10.dp))
            )
            .padding(horizontal = rDP(14.dp), vertical = rDP(10.dp)),
        verticalArrangement = Arrangement.spacedBy(rDP(10.dp))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(rDP(8.dp))
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(rDP(16.dp))
            )
            Text(
                text,
                color = MaterialTheme.colorScheme.primary,
                fontSize = rSp(13.sp),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            )
        }
        LinearWavyProgressIndicator(
            Modifier
                .fillMaxWidth()
                .height(rDP(4.dp))
        )
    }
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ToolUIContent(
    uiState: ChatUiState,
    message: Message,
    viewModel: ChatScreenViewModel,
    isGenerating: Boolean
) {
    val context = LocalContext.current
    val runningPlugin by PluginManager.activePlugin.collectAsState()

    // OPTIMIZATION: Remove Crossfade - direct conditional rendering
    when (uiState) {
        is ChatUiState.DecodingTool -> DecodingToolUI()
        else -> ToolOutputUI(
            message = message,
            uiState = uiState,
            viewModel = viewModel,
            isGenerating = isGenerating,
            runningPlugin = runningPlugin,
            context = context
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DecodingToolUI() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(rDP(10.dp)))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.03f)
                    )
                )
            )
            .border(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                RoundedCornerShape(rDP(10.dp))
            )
            .padding(horizontal = rDP(14.dp), vertical = rDP(10.dp)),
        verticalArrangement = Arrangement.spacedBy(rDP(10.dp))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(rDP(8.dp))
        ) {
            Icon(
                imageVector = Icons.Rounded.Token,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(rDP(16.dp))
            )
            Text(
                "Decoding the input...",
                color = MaterialTheme.colorScheme.primary,
                fontSize = rSp(13.sp),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            )
        }
        LinearWavyProgressIndicator(
            Modifier
                .fillMaxWidth()
                .height(rDP(4.dp))
        )
    }
}

@Composable
private fun ToolOutputUI(
    message: Message,
    uiState: ChatUiState,
    viewModel: ChatScreenViewModel,
    isGenerating: Boolean,
    runningPlugin: LoadedPlugin?,
    context: Context
) {
    val output = remember(message.tool?.toolOutput) {
        parseToolOutput(message.tool?.toolOutput?.output)
    }

    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(rDP(8.dp)),
        modifier = Modifier.animateContentSize(spring(stiffness = Spring.StiffnessLow))
    ) {
        ToolOutputHeader(
            expanded = expanded,
            hasError = output.has("err"),
            isGenerating = isGenerating,
            onToggleExpand = { expanded = !expanded },
            onSummarize = { viewModel.summarizeToolOutput(message.id) }
        )

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
            exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut()
        ) {
            ToolOutputContent(
                output = output,
                uiState = uiState,
                message = message,
                runningPlugin = runningPlugin?.api,
                context = context
            )
        }
    }
}

@Composable
private fun ToolOutputHeader(
    expanded: Boolean,
    hasError: Boolean,
    isGenerating: Boolean,
    onToggleExpand: () -> Unit,
    onSummarize: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(rDP(8.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(rDP(5.dp)))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(rDP(5.dp))
                )
                .clickable { onToggleExpand() }
                .padding(horizontal = rDP(12.dp), vertical = rDP(6.dp))
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (expanded) "Hide Tool Output" else "Show Tool Output",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(rDP(20.dp))
                )
            }
        }

        if (!hasError) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(rDP(5.dp)))
                    .background(
                        if (isGenerating) MaterialTheme.colorScheme.surfaceVariant
                        else Coral.copy(alpha = 0.1f)
                    )
                    .clickable(enabled = !isGenerating) { onSummarize() }
                    .padding(horizontal = rDP(12.dp), vertical = rDP(6.dp))
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(rDP(6.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(Modifier.size(rDP(16.dp)))
                    } else {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Coral,
                            modifier = Modifier.size(rDP(16.dp))
                        )
                    }

                    Text(
                        if (isGenerating) "Summarizing..." else "Summarize",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isGenerating) MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = 0.5f
                        )
                        else Coral,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolOutputContent(
    output: JSONObject,
    uiState: ChatUiState,
    message: Message,
    runningPlugin: PluginApi?,
    context: Context
) {
    when {
        output.has("err") -> {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    output.getString("err"),
                    color = Color(0xFFEF4444),
                    fontSize = rSp(12.sp),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(rDP(16.dp))
                )
            }
        }

        uiState is ChatUiState.ExecutingTool -> {
            Card { runningPlugin?.AppContent() }
        }

        uiState is ChatUiState.Error -> {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    uiState.message,
                    color = Color(0xFFEF4444),
                    fontSize = rSp(12.sp),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(rDP(16.dp))
                )
            }
        }

        else -> {
            Card {
                if (runningPlugin == null) {
                    LaunchedEffect(message.tool?.toolOutput?.pluginName) {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                PluginManager.runPlugin(
                                    context,
                                    message.tool?.toolOutput?.pluginName ?: ""
                                )
                            }
                        }.onFailure { e ->
                            Log.e("ToolOutput", "Plugin launch failed", e)
                        }
                    }
                } else {
                    runningPlugin.ToolPreviewContent(output.toString())
                }
            }
        }
    }
}

@Composable
private fun AssistTag(name: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(rDP(10.dp)))
            .background(Color(0x1A3B82F6))
            .padding(horizontal = rDP(8.dp), vertical = rDP(4.dp))
    ) {
        Text(
            text = "via $name",
            fontSize = rSp(12.sp),
            color = Color(0xFF2563EB),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EmptyStateContent(uiState: ChatUiState) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = rDP(24.dp)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (uiState) {
            is ChatUiState.Loading -> {
                LoadingIndicator(
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(rDP(16.dp)))
                Text(
                    text = uiState.message,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = rSp(16.sp),
                    textAlign = TextAlign.Center
                )
            }

            is ChatUiState.Error -> {
                Icon(
                    painter = painterResource(R.drawable.menu),
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(rDP(48.dp))
                )
                Spacer(Modifier.height(rDP(16.dp)))
                Text(
                    text = "Something went wrong",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = rSp(18.sp),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = uiState.message,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = rSp(14.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = rDP(32.dp))
                )
            }

            else -> {
                Text(
                    text = "Ready to chat! Ask me anything. 😊 \nToolNeuron",
                    color = SlateGrey,
                    fontSize = rSp(16.sp),
                    fontFamily = FontFamily.Serif,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// Helper functions
private fun parseToolOutput(output: String?): JSONObject {
    return runCatching {
        val text = output ?: ""
        when {
            text.isBlank() -> JSONObject().put("err", "Tool not executed yet")
            else -> JSONObject(text)
        }
    }.getOrElse {
        JSONObject().put("err", "Failed to parse: ${it.message}")
    }
}

private fun shareText(context: Context, text: String) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_TEXT, text)
        type = "text/plain"
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share message"))
}