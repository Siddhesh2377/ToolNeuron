package com.dark.tool_neuron.ui.screen.home

import android.content.ClipData
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.models.messages.ContentType
import com.dark.tool_neuron.models.messages.Messages
import com.dark.tool_neuron.models.ui.ActionIcon
import com.dark.tool_neuron.models.ui.ActionItem
import com.dark.tool_neuron.ui.components.AgentExecutionView
import com.dark.tool_neuron.ui.components.MultiActionButton
import com.dark.tool_neuron.ui.components.PluginResultCard
import com.dark.tool_neuron.ui.components.ToolChainDisplay
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.viewmodel.AgentPhase
import kotlinx.coroutines.launch
import com.dark.tool_neuron.global.Standards

// ── AssistantMessageHeader ──

/** Header part of assistant message: RAG results, tool chain, thinking block, non-text content. */
@Composable
internal fun AssistantMessageHeader(message: Messages, imageBlurEnabled: Boolean = true) {
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
                    if (THINK_TAG_REGEX.containsMatchIn(message.content.content)) {
                        parseThinkingTags(message.content.content)
                    } else null
                }
                parsed?.thinkingContent?.let { ThinkingBlock(it) }
            }
        }
    }
}

// ── AssistantMessageFooter ──

/** Footer part of assistant message: metrics + action row. */
@Composable
internal fun AssistantMessageFooter(
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
                if (THINK_TAG_REGEX.containsMatchIn(message.content.content)) {
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

// ── MessageActionRow ──

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MessageActionRow(
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
            .padding(horizontal = Standards.SpacingMd),
        horizontalArrangement = Arrangement.spacedBy(Standards.SpacingXs),
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
