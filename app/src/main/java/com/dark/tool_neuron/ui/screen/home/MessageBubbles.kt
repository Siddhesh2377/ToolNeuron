package com.dark.tool_neuron.ui.screen.home

import android.graphics.BitmapFactory
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.models.messages.Messages
import com.dark.tool_neuron.ui.components.ExpandCollapseIcon
import com.dark.tool_neuron.ui.components.MarkdownText
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.Motion
import kotlinx.coroutines.delay
import java.util.Base64
import com.dark.tool_neuron.global.Standards

// ── UserMessageBubble ──

@Composable
internal fun UserMessageBubble(message: Messages) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(Standards.RadiusLg),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .padding(horizontal = Standards.SpacingSm, vertical = 5.dp)
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

// ── AssistantStreamingBubble ──

@Composable
internal fun AssistantStreamingBubble(text: String, thinkingEnabled: Boolean = false) {
    // ── Typewriter effect ──
    // Smoothly reveals text 2-4 chars per tick instead of chunky batch updates
    var revealedLen by remember { mutableIntStateOf(0) }
    val latestText by rememberUpdatedState(text)

    LaunchedEffect(Unit) {
        while (true) {
            val target = latestText.length
            if (revealedLen < target) {
                val behind = target - revealedLen
                val step = when {
                    behind > 20 -> 4   // far behind: catch up faster
                    behind > 8 -> 3
                    else -> 2          // normal: gentle reveal
                }
                revealedLen = minOf(revealedLen + step, target)
                delay(33) // ~30 FPS — actively revealing
            } else {
                delay(100) // idle — waiting for tokens, check less often
            }
        }
    }

    val displayed = if (revealedLen < text.length) text.substring(0, revealedLen) else text

    // Only parse thinking tags when thinking mode is enabled — skip regex overhead otherwise
    val parsedMessage = if (thinkingEnabled) {
        remember(displayed) { parseThinkingTags(displayed) }
    } else {
        ParsedMessage(thinkingContent = null, actualContent = displayed)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Standards.SpacingSm),
        verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
    ) {
        if (parsedMessage.thinkingContent != null) {
            ThinkingBlock(
                thinkingText = parsedMessage.thinkingContent,
                isStreaming = parsedMessage.isThinkingInProgress
            )
        }

        if (parsedMessage.actualContent.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm),
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

// ── ImageMessageBubble ──

@Composable
internal fun ImageMessageBubble(message: Messages, imageBlurEnabled: Boolean = true) {
    var isImageRevealed by remember(imageBlurEnabled) { mutableStateOf(!imageBlurEnabled) }

    Column(
        verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm),
        modifier = Modifier.padding(Standards.SpacingMd)
    ) {
        message.content.imagePrompt?.let { prompt ->
            Text(
                text = "Prompt: $prompt",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Standards.SpacingXs)
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
                        .clip(RoundedCornerShape(Standards.RadiusLg))
                        .clickable { isImageRevealed = !isImageRevealed },
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(Standards.RadiusLg),
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
                                verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
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
                modifier = Modifier.padding(horizontal = Standards.SpacingXs)
            )
        }
    }
}

// ── ThinkingBlock ──

@Composable
internal fun ThinkingBlock(
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = Standards.SpacingMd),
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
                    .padding(vertical = Standards.SpacingSm, horizontal = Standards.SpacingMd),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm),
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
                        modifier = Modifier.padding(Standards.SpacingMd)
                    )
                }
            }
        }
    }
}
