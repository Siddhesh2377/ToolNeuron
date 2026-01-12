package com.dark.tool_neuron.ui.screen.home_screen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.R
import com.dark.tool_neuron.models.messages.ContentType
import com.dark.tool_neuron.models.messages.ImageGenerationMetrics
import com.dark.tool_neuron.models.messages.MessageContent
import com.dark.tool_neuron.models.messages.Messages
import com.dark.tool_neuron.models.messages.Role
import com.dark.tool_neuron.ui.components.MarkdownText
import com.dark.tool_neuron.ui.theme.rDp
import com.dark.tool_neuron.viewmodel.ChatViewModel
import com.dark.tool_neuron.viewmodel.LLMModelViewModel
import com.dark.tool_neuron.worker.GenerationManager
import com.mp.ai_gguf.models.DecodingMetrics
import java.util.Base64

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
                    isImageGeneration = currentGenerationType == GenerationManager.ModelType.IMAGE_GENERATION
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
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        chatViewModel.hideDynamicWindow()
                    })
        }

        // Dynamic window overlay
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
                DynamicActionWindow(chatViewModel, llmModelViewModel)
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
    isImageGeneration: Boolean
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(assistantMessage, streamingImage) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(rDp(8.dp)),
        verticalArrangement = Arrangement.spacedBy(rDp(8.dp))
    ) {
        // User message bubble
        UserMessageBubble(
            message = Messages(
                role = Role.User,
                content = MessageContent(
                    contentType = ContentType.Text,
                    content = userMessage
                )
            )
        )

        // Assistant streaming bubble (text or image)
        if (isImageGeneration) {
            ImageGenerationStreamingBubble(
                streamingImage = streamingImage,
                progress = imageProgress,
                step = imageStep
            )
        } else {
            AssistantStreamingBubble(text = assistantMessage)
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
        // Progress indicator
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

        // Preview image if available
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
            Box(
                modifier = Modifier
                    .size(rDp(8.dp))
                    .background(
                        MaterialTheme.colorScheme.primary,
                        shape = CircleShape
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
private fun MessageBubble(message: Messages) {
    when (message.role) {
        Role.User -> UserMessageBubble(message)
        else -> AssistantMessageBubble(message)
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
            MarkdownText(
                text = message.content.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(
                    horizontal = rDp(12.dp),
                    vertical = rDp(10.dp)
                )
            )
        }
    }
}

@Composable
private fun AssistantMessageBubble(message: Messages) {
    val showMetrics = remember(message.decodingMetrics) {
        message.decodingMetrics?.tokensPerSecond?.let { it > 0 } ?: false
    }

    val showImageMetrics = remember(message.imageMetrics) {
        message.imageMetrics != null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(rDp(8.dp)),
        verticalArrangement = Arrangement.spacedBy(rDp(6.dp))
    ) {
        // Check if it's an image message
        when (message.content.contentType) {
            ContentType.Image -> {
                ImageMessageBubble(message)
            }
            else -> {
                MarkdownText(
                    text = message.content.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = rDp(4.dp))
                )
            }
        }
        //TODO: Add Delete Icon

        // Show metrics based on message type
        if (showMetrics) {
            message.decodingMetrics?.let { metrics ->
                MetricsDisplay(metrics)
            }
        }

        if (showImageMetrics) {
            message.imageMetrics?.let { metrics ->
                ImageMetricsDisplay(metrics)
            }
        }
    }
}

@Composable
private fun ImageMessageBubble(message: Messages) {
    Column(
        verticalArrangement = Arrangement.spacedBy(rDp(8.dp))
    ) {
        // Show prompt if available
        message.content.imagePrompt?.let { prompt ->
            Text(
                text = "Prompt: $prompt",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = rDp(4.dp))
            )
        }

        // Display image
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
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    shape = RoundedCornerShape(rDp(12.dp)),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = message.content.content,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(rDp(12.dp))),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }

        // Show seed if available
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
private fun MetricsDisplay(metrics: DecodingMetrics) {
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
private fun ImageMetricsDisplay(metrics: ImageGenerationMetrics) {
    val formattedTime = remember(metrics.generationTimeMs) {
        "%.1f".format(metrics.generationTimeMs / 1000f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = rDp(4.dp)),
        horizontalArrangement = Arrangement.spacedBy(rDp(12.dp))
    ) {
        MetricItem(icon = "🎨", value = metrics.steps.toString(), unit = "steps")
        MetricItem(icon = "📐", value = "${metrics.width}×${metrics.height}", unit = "")
        MetricItem(icon = "⚙️", value = "%.1f".format(metrics.cfgScale), unit = "cfg")
        MetricItem(icon = "⏰", value = formattedTime, unit = "s")
    }
}

@Composable
private fun MetricItem(
    icon: String,
    value: String,
    unit: String
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
        if (unit.isNotEmpty()) {
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}