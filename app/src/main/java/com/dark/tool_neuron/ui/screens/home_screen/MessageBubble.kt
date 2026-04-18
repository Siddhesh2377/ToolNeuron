package com.dark.tool_neuron.ui.screens.home_screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.model.ChatMessage
import com.dark.tool_neuron.ui.components.markdown.MarkdownText
import com.dark.tool_neuron.ui.components.markdown.ThinkingBlock
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes

private const val ROLE_USER = "user"
private val UserBubbleMaxWidth = 280.dp

@Composable
fun MessageBubble(
    message: ChatMessage,
    canRegenerate: Boolean,
    canDelete: Boolean,
    onRegenerate: () -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (message.role == ROLE_USER) {
        UserBubble(
            message = message,
            canDelete = canDelete,
            onDelete = onDelete,
            modifier = modifier,
        )
    } else {
        AssistantBubble(
            message = message,
            canRegenerate = canRegenerate,
            canDelete = canDelete,
            onRegenerate = onRegenerate,
            onDelete = onDelete,
            modifier = modifier,
        )
    }
}

@Composable
fun StreamingAssistantBubble(
    content: String,
    thinkingContent: String,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalDimens.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        verticalAlignment = Alignment.Top,
    ) {
        AssistantDot()
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingXs),
        ) {
            if (thinkingContent.isNotBlank()) {
                StreamingThinkingPreview(text = thinkingContent)
            }
            if (content.isNotBlank()) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.87f),
                    modifier = Modifier.padding(horizontal = dimens.spacingXs),
                )
            }
        }
    }
}

@Composable
private fun StreamingThinkingPreview(text: String) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    Surface(
        shape = tnShapes.md,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(dimens.spacingSm)) {
            Text(
                text = "Thinking…",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = dimens.spacingXxs),
            )
        }
    }
}

@Composable
private fun UserBubble(
    message: ChatMessage,
    canDelete: Boolean,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(dimens.spacingXs),
    ) {
        Surface(
            shape = tnShapes.lg,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.widthIn(max = UserBubbleMaxWidth),
        ) {
            SelectionContainer {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(
                        horizontal = dimens.spacingMd,
                        vertical = dimens.spacingSm,
                    ),
                )
            }
        }
        MessageActions(
            message = message,
            canRegenerate = false,
            canDelete = canDelete,
            onRegenerate = {},
            onDelete = onDelete,
        )
    }
}

@Composable
private fun AssistantBubble(
    message: ChatMessage,
    canRegenerate: Boolean,
    canDelete: Boolean,
    onRegenerate: () -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalDimens.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        verticalAlignment = Alignment.Top,
    ) {
        AssistantDot()
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingXs),
        ) {
            if (message.thinkingContent.isNotBlank()) {
                ThinkingBlock(
                    text = message.thinkingContent,
                    isStreaming = false,
                )
            }
            if (message.content.isNotBlank()) {
                SelectionContainer {
                    MarkdownText(text = message.content)
                }
            }
            MessageActions(
                message = message,
                canRegenerate = canRegenerate,
                canDelete = canDelete,
                onRegenerate = onRegenerate,
                onDelete = onDelete,
            )
        }
    }
}

@Composable
private fun AssistantDot() {
    Box(
        modifier = Modifier
            .padding(top = 6.dp)
            .size(8.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape)
    )
}
