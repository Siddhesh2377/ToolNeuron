package com.dark.tool_neuron.ui.screen.home_screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.R
import com.dark.tool_neuron.models.messages.ContentType
import com.dark.tool_neuron.models.messages.MessageContent
import com.dark.tool_neuron.models.messages.Messages
import com.dark.tool_neuron.models.messages.Role
import com.dark.tool_neuron.ui.theme.rDp
import com.dark.tool_neuron.viewmodel.ChatViewModel
import com.mp.ai_gguf.models.DecodingMetrics

// Update BodyContent
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun BodyContent(
    paddingValues: PaddingValues, chatViewModel: ChatViewModel
) {
    val messages = chatViewModel.messages
    val isGenerating by chatViewModel.isGenerating.collectAsState()
    val streamingUserMessage by chatViewModel.streamingUserMessage.collectAsState()
    val streamingAssistantMessage by chatViewModel.streamingAssistantMessage.collectAsState()
    val showDynamicWindow by chatViewModel.showDynamicWindow.collectAsState()

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
                    assistantMessage = streamingAssistantMessage
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
                        key = { index -> messages[index].msgId },
                        contentType = { index -> messages[index].role }) { index ->
                        MessageBubble(message = messages[index])
                    }
                    item {
                        Spacer(modifier = Modifier.height(rDp(16.dp)))
                    }
                }
            }
        }

        // Shared transition dialog
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
                        interactionSource = remember { MutableInteractionSource() }) {
                        chatViewModel.hideDynamicWindow()
                    })
        }

        // Dynamic window overlay
        AnimatedVisibility(
            visible = showDynamicWindow,
            enter = fadeIn(animationSpec = tween(300)) + slideInVertically(
                initialOffsetY = { -it }, animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ),
            exit = fadeOut(animationSpec = tween(300)) + slideOutVertically(
                targetOffsetY = { -it }, animationSpec = tween(300)
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rDp(16.dp), vertical = rDp(16.dp)),
                contentAlignment = Alignment.TopCenter
            ) {
                DynamicActionWindow(chatViewModel)
            }
        }
    }
}

@Composable
private fun StreamingView(
    userMessage: String, assistantMessage: String
) {
    // Scrollable column for overflow
    val scrollState = rememberScrollState()

    // Auto-scroll to bottom when assistant message updates
    LaunchedEffect(assistantMessage) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(rDp(8.dp)),
        verticalArrangement = Arrangement.spacedBy(rDp(8.dp))
    ) {
        // User message bubble (static)
        UserMessageBubble(
            message = Messages(
                role = Role.User, content = MessageContent(
                    contentType = ContentType.Text, content = userMessage
                )
            )
        )

        // Assistant message bubble (streaming)
        AssistantStreamingBubble(text = assistantMessage)

        // Add some bottom padding
        Spacer(modifier = Modifier.height(rDp(16.dp)))
    }
}

@Composable
private fun AssistantStreamingBubble(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(rDp(8.dp)),
        verticalArrangement = Arrangement.spacedBy(rDp(6.dp))
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(rDp(8.dp)),
            verticalAlignment = Alignment.Top
        ) {
            // Optional: Add a pulsing indicator
            Box(
                modifier = Modifier
                    .size(rDp(8.dp))
                    .background(
                        MaterialTheme.colorScheme.primary, shape = CircleShape
                    )
            )

            Text(
                text = text.ifEmpty { "..." },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun EmptyMessagesState() {
    Box(
        modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
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
private fun MessageBubble(message: Messages) {
    when (message.role) {
        Role.User -> UserMessageBubble(message)
        else -> AssistantMessageBubble(message)
    }
}

@Composable
private fun UserMessageBubble(message: Messages) {
    Row(
        modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(rDp(12.dp)),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .padding(horizontal = rDp(8.dp), vertical = rDp(5.dp))
                .widthIn(max = rDp(280.dp))
        ) {
            Text(
                text = message.content.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(
                    horizontal = rDp(12.dp), vertical = rDp(10.dp)
                )
            )
        }
    }
}

@Composable
private fun AssistantMessageBubble(message: Messages) {
    // Memoize metrics display
    val showMetrics = remember(message.decodingMetrics) {
        message.decodingMetrics?.tokensPerSecond?.let { it > 0 } ?: false
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(rDp(8.dp)),
        verticalArrangement = Arrangement.spacedBy(rDp(6.dp))
    ) {
        Text(
            text = message.content.content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = rDp(4.dp))
        )

        if (showMetrics) {
            message.decodingMetrics?.let { metrics ->
                MetricsDisplay(metrics)
            }
        }
    }
}

@Composable
private fun MetricsDisplay(metrics: DecodingMetrics) {
    // Memoize formatted values
    val formattedSpeed = remember(metrics.tokensPerSecond) {
        "%.1f".format(metrics.tokensPerSecond)
    }
    val formattedTime = remember(metrics.totalTimeMs) {
        if (metrics.totalTimeMs > 0) "%.1f".format(metrics.totalTimeMs / 1000f) else null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = rDp(4.dp)),
        horizontalArrangement = Arrangement.spacedBy(rDp(12.dp))
    ) {
        MetricItem(icon = "⚡", value = formattedSpeed, unit = "t/s")

        if (metrics.totalTokens > 0) {
            MetricItem(icon = "📊", value = metrics.totalTokens.toString(), unit = "tokens")
        }

        if (metrics.timeToFirstToken > 0) {
            MetricItem(icon = "⏱️", value = metrics.timeToFirstToken.toString(), unit = "ms")
        }

        formattedTime?.let { time ->
            MetricItem(icon = "⏰", value = time, unit = "s")
        }
    }
}

@Composable
private fun MetricItem(
    icon: String, value: String, unit: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(rDp(4.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon,
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = unit,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}