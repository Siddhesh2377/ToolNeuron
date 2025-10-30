package com.dark.neuroverse.ui.screens.home

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Token
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.neuroverse.R
import com.dark.neuroverse.model.ChatUiState
import com.dark.neuroverse.model.DecodingMetrics
import com.dark.neuroverse.model.Message
import com.dark.neuroverse.model.Role
import com.dark.neuroverse.model.ToolOutput
import com.dark.neuroverse.ui.components.MarkdownText
import com.dark.neuroverse.ui.components.RegenerateModelPickerDialog
import com.dark.neuroverse.ui.theme.Coral
import com.dark.neuroverse.ui.theme.SlateGrey
import com.dark.neuroverse.ui.theme.rDP
import com.dark.neuroverse.ui.theme.rSp
import com.dark.neuroverse.viewModel.chatViewModel.ChatScreenViewModel
import com.dark.neuroverse.viewModel.chatViewModel.TTSViewModel
import com.dark.neuroverse.worker.UIStateManager
import com.dark.neuroverse.worker.UIStateManager.isGenerating
import com.dark.plugins.manager.PluginManager
import com.mp.data_hub_lib.model.RagResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun ChatBubble(
    message: Message,
    viewModel: ChatScreenViewModel,
    ttsViewModel: TTSViewModel,
) {
    val isUser = message.role == Role.User
    val isWaitingForFirstToken = viewModel.isMessageWaitingForFirstToken(message.id, message.text)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column {
            val showThinking = !isUser && !message.thought.isNullOrBlank()
            if (showThinking) {
                ThinkingChatUI(message)
                Spacer(Modifier.height(rDP(8.dp)))
            }

            when (message.role) {
                Role.User -> UserChatUI(
                    message = message,
                ) {
                    viewModel.deleteMessage(it)
                }

                Role.Assistant -> if (isWaitingForFirstToken) {
                    DecodingPlaceholder()
                } else {
                    RegularChatUI(
                        message = message,
                        viewModel = viewModel,
                        ttsViewModel = ttsViewModel,
                    )
                }

                Role.Tool -> ToolChatUI(
                    message = message,
                    viewModel = viewModel,
                    ttsViewModel = ttsViewModel,
                    onMessageDelete = {
                        viewModel.deleteMessage(message.id)
                    })
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
        // Animated dots
        val infiniteTransition = rememberInfiniteTransition(label = "decoding")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.3f, targetValue = 1f, animationSpec = infiniteRepeatable(
                animation = tween(800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ), label = "alpha"
        )

        Text(
            text = "Decoding",
            color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic
        )

        Text(
            text = "...",
            color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = rDP(4.dp))
        )
    }
}

@Composable
private fun UserChatUI(
    message: Message, onMessageDelete: (String) -> Unit = {}
) {
    val radius = with(LocalDensity.current) { rDP(12.dp) }
    val corner = RoundedCornerShape(radius)
    val actionIconSize = rDP(14.dp)
    val clipboardManager = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.widthIn(max = rDP(240.dp)), horizontalAlignment = Alignment.End
    ) {
        // Message text
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

        // Action buttons
        Row(horizontalArrangement = Arrangement.spacedBy(rDP(12.dp))) {
            // Copy button
            Icon(
                painter = painterResource(R.drawable.copy),
                contentDescription = "Copy text",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier
                    .size(actionIconSize)
                    .clickable {
                        scope.launch {
                            clipboardManager.setClipEntry(
                                ClipEntry(ClipData.newPlainText("message", message.text))
                            )
                        }
                        Toast.makeText(
                            context, "Copied to clipboard!", Toast.LENGTH_SHORT
                        ).show()
                    })

            // Share button
            Icon(
                imageVector = Icons.Rounded.Share,
                contentDescription = "Share",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier
                    .size(actionIconSize)
                    .clickable {
                        val shareIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, message.text)
                            type = "text/plain"
                        }
                        context.startActivity(
                            Intent.createChooser(shareIntent, "Share message")
                        )
                    })

            // Delete button
            Icon(
                Icons.Rounded.DeleteOutline,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier
                    .size(actionIconSize)
                    .clickable { onMessageDelete(message.id) })
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
private fun RegularChatUI(
    message: Message,
    viewModel: ChatScreenViewModel,
    ttsViewModel: TTSViewModel,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val decodingMetrics = message.decodingMetrics

    // TTS states
    val isPlayingAudio by ttsViewModel.isPlaying.collectAsStateWithLifecycle()
    val audioProgress by ttsViewModel.audioProgress.collectAsStateWithLifecycle()
    val isInitialized by ttsViewModel.isInitialized.collectAsStateWithLifecycle()

    var showRegenerateDialog by remember { mutableStateOf(false) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isStreaming = when (uiState) {
        is ChatUiState.DecodingStream -> (uiState as ChatUiState.DecodingStream).messageId == message.id
        is ChatUiState.Generating -> (uiState as ChatUiState.Generating).messageId == message.id
        else -> false
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = rDP(4.dp))
    ) {


        // Main message content
        Crossfade(isStreaming, label = "content-transition") { streaming ->
            if (streaming) {
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

        if (decodingMetrics.durationMs.toInt() != 0){
            Spacer(Modifier.height(rDP(3.dp)))

            decodingMetrics.let { metrics ->
                Spacer(Modifier.height(rDP(6.dp)))
                Text(
                    text = "Decoded with \n${metrics.modelName} in ${metrics.durationMs} ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f)
                )
            }
        }

        Spacer(Modifier.height(rDP(8.dp)))

        // Expandable RAG section
        message.ragResult?.let {
            RagResultCard(rag = it)
        }

        Spacer(Modifier.height(rDP(8.dp)))

        // Action buttons row (copy, TTS, regen, share, delete)
        ChatMessageActions(
            message = message,
            scope = scope,
            context = context,
            ttsViewModel = ttsViewModel,
            isPlayingAudio = isPlayingAudio,
            audioProgress = audioProgress,
            isInitialized = isInitialized,
            onRegenerateClick = { showRegenerateDialog = true },
            onDeleteClick = { viewModel.deleteMessage(message.id) })
    }

    // Regenerate dialog
    if (showRegenerateDialog) {
        RegenerateModelPickerDialog(
            viewModel = viewModel, messageId = message.id
        ) { showRegenerateDialog = false }
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun RagResultCard(rag: RagResult) {
    var ragExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            // Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { ragExpanded = !ragExpanded }) {
                Text(
                    text = "RAG Result (${rag.docs.size} docs)",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = if (ragExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Expand RAG",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            AnimatedVisibility(visible = ragExpanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    // Stats
                    val stats = rag.stats
                    Text(
                        text = "Stats → Docs: ${stats.tokenCount}, Time: ${stats.totalTime}ms, TPS: ${
                            String.format(
                                "%.2f", stats.tokensPerSecond
                            )
                        }",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Docs List
                    rag.docs.forEach { doc ->
                        Spacer(modifier = Modifier.height(4.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp)
                                )
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
                }
            }
        }
    }
}

@Composable
private fun ThinkingChatUI(message: Message) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(120)) + expandVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow
            )
        ) + slideInVertically(initialOffsetY = { -it / 6 }),
        exit = fadeOut(animationSpec = tween(120)) + shrinkVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium
            )
        ) + slideOutVertically(targetOffsetY = { -it / 6 })
    ) {
        var showThinkingText by remember { mutableStateOf(false) }

        Spacer(Modifier.height(rDP(6.dp)))
        Box(modifier = Modifier
            .fillMaxWidth()
            .clickable { showThinkingText = !showThinkingText }
            .clip(RoundedCornerShape(rDP(8.dp)))
            .background(Color(0xFF0F172A))
            .border(rDP(1.dp), Color(0xFF334155), RoundedCornerShape(rDP(8.dp)))
            .animateContentSize(
                animationSpec = tween(
                    durationMillis = 180, easing = FastOutSlowInEasing
                )
            )) {
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
private fun ChatMessageActions(
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
    val actionIconSize = rDP(14.dp)

    val clipboardManager = LocalClipboard.current

    Row(
        horizontalArrangement = Arrangement.spacedBy(rDP(12.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Copy button
        Icon(
            painter = painterResource(R.drawable.copy),
            contentDescription = "Copy text",
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier
                .size(actionIconSize)
                .clickable {
                    scope.launch {
                        clipboardManager.setClipEntry(
                            ClipEntry(ClipData.newPlainText("message", message.text))
                        )
                    }
                    Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                })

        // TTS button
        Box(contentAlignment = Alignment.Center) {
            if (isPlayingAudio && audioProgress > 0f) {
                CircularProgressIndicator(
                    progress = { audioProgress },
                    modifier = Modifier.size(actionIconSize + 4.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    strokeWidth = 2.dp,
                    trackColor = ProgressIndicatorDefaults.circularIndeterminateTrackColor
                )
            }

            val context = LocalContext.current

            Icon(
                painter = painterResource(if (isPlayingAudio) R.drawable.stop else R.drawable.speaker),
                contentDescription = if (isPlayingAudio) "Stop audio" else "Play audio",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = if (isInitialized) 0.7f else 0.3f),
                modifier = Modifier
                    .size(actionIconSize)
                    .clickable(enabled = isInitialized) {
                        if (isPlayingAudio) ttsViewModel.stopPlayback()
                        else scope.launch(Dispatchers.IO) {
                            val normalized = ttsViewModel.normalizeText(message.text)
                            ttsViewModel.generateAndPlayAudio(normalized, context)
                        }
                    })
        }

        // Regenerate button
        Icon(
            painter = painterResource(R.drawable.regen),
            contentDescription = "Regenerate response",
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier
                .size(actionIconSize)
                .clickable { onRegenerateClick() })

        // Share button
        Icon(
            imageVector = Icons.Rounded.Share,
            contentDescription = "Share",
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier
                .size(actionIconSize)
                .clickable {
                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, message.text)
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share message"))
                })

        // Delete button
        Icon(
            Icons.Rounded.DeleteOutline,
            contentDescription = "Delete",
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier
                .size(actionIconSize)
                .clickable { onDeleteClick() })
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolChatUI(
    message: Message,
    viewModel: ChatScreenViewModel,
    ttsViewModel: TTSViewModel,
    onMessageDelete: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val decodingMetrics = message.decodingMetrics
    val uiState = UIStateManager.uiState.collectAsStateWithLifecycle()
    val isPlaying by ttsViewModel.isPlaying.collectAsStateWithLifecycle()
    val progress by ttsViewModel.audioProgress.collectAsStateWithLifecycle()
    val initialized by ttsViewModel.isInitialized.collectAsStateWithLifecycle()

    Column {
        AssistTag(message.tool?.toolName ?: "Unknown Tool")
        Spacer(Modifier.height(rDP(6.dp)))

        // Handles both decoding + output
        ToolUIContent(
            uiState = uiState.value,
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

        if (decodingMetrics.durationMs.toInt() != 0){
            Spacer(Modifier.height(rDP(3.dp)))

            decodingMetrics.let { metrics ->
                Spacer(Modifier.height(rDP(6.dp)))
                Text(
                    text = "Decoded with \n${metrics.modelName} in ${metrics.durationMs} ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f)
                )
            }
        }

        Spacer(Modifier.height(rDP(12.dp)))

        Row(
            horizontalArrangement = Arrangement.spacedBy(rDP(12.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val iconSize = rDP(14.dp)

            // Copy
            Icon(
                painter = painterResource(R.drawable.copy),
                contentDescription = "Copy",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier
                    .size(iconSize)
                    .clickable {
                        scope.launch {
                            clipboardManager.setClipEntry(
                                ClipEntry(ClipData.newPlainText("message", message.text))
                            )
                        }
                        Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                    }
            )

            // TTS button with progress
            Box(contentAlignment = Alignment.Center) {
                if (isPlaying && progress > 0f) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(iconSize + 4.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        strokeWidth = 2.dp
                    )
                }
                Icon(
                    painter = painterResource(
                        if (isPlaying) R.drawable.stop else R.drawable.speaker
                    ),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(
                        alpha = if (initialized) 0.7f else 0.3f
                    ),
                    modifier = Modifier
                        .size(iconSize)
                        .clickable(enabled = initialized) {
                            if (isPlaying) ttsViewModel.stopPlayback()
                            else scope.launch(Dispatchers.IO) {
                                val normalized = ttsViewModel.normalizeText(message.text)
                                ttsViewModel.generateAndPlayAudio(normalized, context)
                            }
                        }
                )
            }

            // Share
            Icon(
                imageVector = Icons.Rounded.Share,
                contentDescription = "Share",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier
                    .size(iconSize)
                    .clickable {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            putExtra(Intent.EXTRA_TEXT, message.text)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share message"))
                    }
            )

            // Delete
            Icon(
                Icons.Rounded.DeleteOutline,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier
                    .size(iconSize)
                    .clickable { onMessageDelete(message.id) }
            )
        }
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
    val runningPlugin = PluginManager.activePlugin.collectAsState().value

    val showActualTool = message.tool?.toolOutput?.output?.isNotBlank()

    Crossfade(targetState = uiState) { state ->
        when (state) {
            is ChatUiState.DecodingTool -> {
                // Loading UI
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
                        Modifier.fillMaxWidth().height(rDP(4.dp))
                    )
                }
            }

            else -> {
                val output = remember(message.tool?.toolOutput) {
                    runCatching {
                        val text = message.tool?.toolOutput?.output ?: ""
                        when {
                            text.isBlank() -> JSONObject().put("err", "Tool not executed yet")
                            else -> JSONObject(text)
                        }
                    }.getOrElse {
                        JSONObject().put("err", "Failed to parse: ${it.message}")
                    }
                }

                var expanded by remember { mutableStateOf(false) }

                Column(verticalArrangement = Arrangement.spacedBy(rDP(8.dp))) {
                    // Toggle + Summarize Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(rDP(8.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Toggle
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(rDP(5.dp)))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(rDP(5.dp))
                                )
                                .clickable { expanded = !expanded }
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

                        // Summarize Button
                        if (!output.has("err")) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(rDP(5.dp)))
                                    .background(
                                        if (isGenerating)
                                            MaterialTheme.colorScheme.surfaceVariant
                                        else Coral.copy(alpha = 0.1f)
                                    )
                                    .clickable(enabled = !isGenerating) {
                                        viewModel.summarizeToolOutput(message.id)
                                    }
                                    .padding(horizontal = rDP(12.dp), vertical = rDP(6.dp))
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(rDP(6.dp)),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isGenerating)
                                        CircularProgressIndicator(Modifier.size(rDP(16.dp)))
                                    else
                                        Icon(
                                            Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = Coral,
                                            modifier = Modifier.size(rDP(16.dp))
                                        )

                                    Text(
                                        if (isGenerating) "Summarizing..." else "Summarize",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isGenerating)
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        else Coral,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    // Expand Output Section
                    AnimatedVisibility(
                        visible = expanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
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
                                Card { runningPlugin?.api?.AppContent() }
                            }

                            uiState is ChatUiState.Error -> {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    )
                                ) {
                                    Text(
                                        (uiState as ChatUiState.Error).message,
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
                                            try {
                                                withContext(Dispatchers.IO) {
                                                    PluginManager.runPlugin(
                                                        context,
                                                        message.tool?.toolOutput?.pluginName ?: ""
                                                    )
                                                }
                                            } catch (e: Exception) {
                                                Log.e("ToolOutput", "Plugin launch failed", e)
                                            }
                                        }
                                    } else runningPlugin.api?.ToolPreviewContent(output.toString())
                                }
                            }
                        }
                    }
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
                CircularProgressIndicator(
                    modifier = Modifier.size(rDP(48.dp))
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
                    text = "Ready to chat! Ask me anything. \uD83D\uDE0A \nToolNeuron",
                    color = SlateGrey,
                    fontSize = rSp(16.sp),
                    fontFamily = FontFamily.Serif,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}