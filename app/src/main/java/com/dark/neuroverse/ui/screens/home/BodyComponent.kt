package com.dark.neuroverse.ui.screens.home

import android.content.ClipData
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
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.dark.neuroverse.model.Message
import com.dark.neuroverse.model.Role
import com.dark.neuroverse.model.ToolOutput
import com.dark.neuroverse.ui.components.MarkdownText
import com.dark.neuroverse.ui.components.RegenerateModelPickerDialog
import com.dark.neuroverse.ui.components.RobotDecodePlaceholder
import com.dark.neuroverse.ui.theme.Coral
import com.dark.neuroverse.ui.theme.SlateGrey
import com.dark.neuroverse.ui.theme.rDP
import com.dark.neuroverse.ui.theme.rSp
import com.dark.neuroverse.viewModel.chatViewModel.ChatScreenViewModel
import com.dark.neuroverse.viewModel.chatViewModel.TTSViewModel
import com.dark.plugins.manager.PluginManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun ChatBubble(
    message: Message,
    viewModel: ChatScreenViewModel,
    ttsViewModel: TTSViewModel,
) {
    val isUser = message.role == Role.User
    val isWaitingForFirstToken = viewModel.isMessageWaitingForFirstToken(message.id, message.text)
    val isThisMessageExecutingTool = viewModel.isMessageExecutingTool(message.id)

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
                    isDecoding = isThisMessageExecutingTool,
                )
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

@Composable
private fun RegularChatUI(
    message: Message,
    viewModel: ChatScreenViewModel,
    ttsViewModel: TTSViewModel,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboard.current

    // ✅ Collect state properly
    val isPlayingAudio by ttsViewModel.isPlaying.collectAsStateWithLifecycle()
    val audioProgress by ttsViewModel.audioProgress.collectAsStateWithLifecycle()
    val isInitialized by ttsViewModel.isInitialized.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var showRegenerateDialog by remember { mutableStateOf(false) }
    val actionIconSize = rDP(14.dp)

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
        // Content with smooth transition
        Crossfade(isStreaming, label = "content-transition") { streaming ->
            when (streaming) {
                true -> {
                    Text(
                        text = message.text,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                false -> {
                    MarkdownText(
                        text = message.text,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Spacer(Modifier.height(rDP(10.dp)))

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

            // ✅ TTS button - Fixed implementation
            Box(contentAlignment = Alignment.Center) {
                // Show progress indicator if playing
                if (isPlayingAudio && audioProgress > 0f) {
                    CircularProgressIndicator(
                        progress = { audioProgress },
                        modifier = Modifier.size(actionIconSize + 4.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        strokeWidth = 2.dp,
                        trackColor = ProgressIndicatorDefaults.circularIndeterminateTrackColor,
                        strokeCap = ProgressIndicatorDefaults.CircularDeterminateStrokeCap,
                    )
                }

                Icon(
                    painter = painterResource(
                        if (isPlayingAudio) R.drawable.stop else R.drawable.speaker
                    ),
                    contentDescription = if (isPlayingAudio) "Stop audio" else "Play audio",
                    tint = MaterialTheme.colorScheme.primary.copy(
                        alpha = if (isInitialized) 0.7f else 0.3f
                    ),
                    modifier = Modifier
                        .size(actionIconSize)
                        .clickable(enabled = isInitialized) {
                            if (isPlayingAudio) {
                                ttsViewModel.stopPlayback()
                            } else {
                                scope.launch(Dispatchers.IO) {
                                    val normalizer = ttsViewModel.normalizeText(message.text)
                                    ttsViewModel.generateAndPlayAudio(normalizer)
                                }
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
                    .clickable { showRegenerateDialog = true })

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
                    .clickable { viewModel.deleteMessage(message.id) })
        }
    }

    // Regenerate dialog
    if (showRegenerateDialog) {
        RegenerateModelPickerDialog(
            viewModel = viewModel, messageId = message.id
        ) {
            showRegenerateDialog = false
        }
    }
}

@Composable
private fun ToolChatUI(
    message: Message,
    isDecoding: Boolean,
) {
    if (isDecoding) {
        RobotDecodePlaceholder(
            active = true, modifier = Modifier.fillMaxWidth()
        )
    } else {
        Column {
            // Tool identifier tag
            AssistTag(message.tool?.toolName ?: "Unknown Tool")
            Spacer(Modifier.height(rDP(6.dp)))
            val toolOutput = message.tool?.toolOutput
            val out = remember(toolOutput) {
                try {
                    val outputString = toolOutput?.output ?: ""

                    when {
                        outputString.isBlank() -> {
                            JSONObject().apply {
                                put("err", "Tool not executed yet")
                            }
                        }

                        else -> {
                            JSONObject(outputString)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ToolChatUI", "Parse error: ${e.message}", e)
                    JSONObject().apply {
                        put("err", "Failed to parse: ${e.message}")
                    }
                }
            }

            when (message.tool?.toolOutput == null) {
                true -> {
                    Card(elevation = CardDefaults.cardElevation(rDP(0.dp))) {
                        PluginManager.currentPlugin.collectAsState().value?.api?.ToolPreviewContent(
                            out.toString()
                        )
                    }
                }

                false -> {
                    // Tool output available
                    ToolOutputToggle(toolOutput = message.tool.toolOutput, out = out)
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
fun ToolOutputToggle(toolOutput: ToolOutput, out: JSONObject) {
    var expanded by remember { mutableStateOf(false) }

    val shimmerX by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0f, targetValue = 1f, animationSpec = infiniteRepeatable(
            tween(1200, easing = LinearEasing), RepeatMode.Restart
        ), label = "shimmerFloat"
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            Coral.copy(alpha = 0.25f), Coral, Coral.copy(alpha = 0.25f)
        ), start = Offset.Zero, end = Offset(1000f * shimmerX + 1f, 0f)
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(rDP(8.dp))
    ) {
        // Toggle Button
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(rDP(5.dp)))
                .border(
                    1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(rDP(5.dp))
                )
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = shimmerBrush, alpha = 0.25f, blendMode = BlendMode.SrcOver
                    )
                }
                .clickable { expanded = !expanded }
                .padding(horizontal = rDP(12.dp), vertical = rDP(6.dp))) {
            Text(
                text = "Show Tool Output",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Animated expansion for Tool Output
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            ToolOutputContent(toolOutput = toolOutput, out = out)
        }
    }
}

@Composable
fun ToolOutputContent(
    modifier: Modifier = Modifier, toolOutput: ToolOutput, out: JSONObject
) {
    val context = LocalContext.current
    val runningPlugin = PluginManager.runPlugin(context, toolOutput.toolName, toolOutput.output)

    if (out.has("err")) {
        Card(
            elevation = CardDefaults.cardElevation(rDP(0.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Box(modifier = Modifier.padding(vertical = rDP(16.dp), horizontal = rDP(34.dp))) {
                Text(
                    text = out.getString("err"),
                    color = Color(0xFFEF4444),
                    fontSize = rSp(12.sp),
                    lineHeight = rSp(18.sp),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    } else {
        Card(modifier = modifier) {
            runningPlugin.api?.ToolPreviewContent(toolOutput.output)
        }
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