package com.dark.tool_neuron.ui.screens.home_screen

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.model.ChatMessage
import com.dark.tool_neuron.model.MessageKind
import com.dark.tool_neuron.ui.components.markdown.MarkdownText
import com.dark.tool_neuron.ui.components.markdown.ThinkingBlock
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes

private const val ROLE_USER = "user"
private const val ROLE_TOOL = "tool"
private val UserBubbleMaxWidth = 280.dp

@Composable
fun MessageBubble(
    message: ChatMessage,
    canRegenerate: Boolean,
    canDelete: Boolean,
    canEdit: Boolean,
    canFork: Boolean,
    onRegenerate: () -> Unit,
    onDelete: (String) -> Unit,
    onEdit: (messageId: String, newContent: String) -> Unit,
    onFork: (String) -> Unit,
    isSpeaking: Boolean = false,
    isSpeakLoading: Boolean = false,
    canSpeak: Boolean = false,
    onSpeakToggle: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    when {
        message.kind == MessageKind.ToolResult || message.role == ROLE_TOOL ->
            ToolResultBubble(message = message, modifier = modifier)
        message.role == ROLE_USER ->
            UserBubble(
                message = message,
                canDelete = canDelete,
                canEdit = canEdit,
                canFork = canFork,
                onDelete = onDelete,
                onEdit = { newContent -> onEdit(message.id, newContent) },
                onFork = onFork,
                modifier = modifier,
            )
        else ->
            AssistantBubble(
                message = message,
                canRegenerate = canRegenerate,
                canDelete = canDelete,
                canFork = canFork,
                onRegenerate = onRegenerate,
                onDelete = onDelete,
                onFork = onFork,
                isSpeaking = isSpeaking,
                isSpeakLoading = isSpeakLoading,
                canSpeak = canSpeak,
                onSpeakToggle = onSpeakToggle,
                modifier = modifier,
            )
    }
}

@Composable
fun StreamingAssistantBubble(
    content: String,
    thinkingContent: String,
    retrievalLabel: String? = null,
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
            if (retrievalLabel != null) {
                RetrievalStatusBanner(label = retrievalLabel)
            }
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
private fun RetrievalStatusBanner(label: String) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    Surface(
        shape = tnShapes.md,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = dimens.spacingSm, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
        ) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
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
    canEdit: Boolean,
    canFork: Boolean,
    onDelete: (String) -> Unit,
    onEdit: (String) -> Unit,
    onFork: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    var editing by remember(message.id) { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(dimens.spacingXs),
    ) {
        if (message.imageUris.isNotEmpty()) {
            UserImageThumbnails(uris = message.imageUris)
        }
        if (message.content.isNotBlank()) {
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
        }
        MessageActions(
            message = message,
            canRegenerate = false,
            canDelete = canDelete,
            canEdit = canEdit,
            canFork = canFork,
            onRegenerate = {},
            onDelete = onDelete,
            onEdit = { editing = true },
            onFork = onFork,
        )
    }

    if (editing) {
        EditMessageDialog(
            initialText = message.content,
            onConfirm = { newContent ->
                editing = false
                onEdit(newContent)
            },
            onDismiss = { editing = false },
        )
    }
}

@Composable
private fun EditMessageDialog(
    initialText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit message") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 10,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.trim().isNotEmpty() && text.trim() != initialText.trim(),
            ) { Text("Save & regenerate") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun AssistantBubble(
    message: ChatMessage,
    canRegenerate: Boolean,
    canDelete: Boolean,
    canFork: Boolean,
    onRegenerate: () -> Unit,
    onDelete: (String) -> Unit,
    onFork: (String) -> Unit,
    isSpeaking: Boolean,
    isSpeakLoading: Boolean,
    canSpeak: Boolean,
    onSpeakToggle: () -> Unit,
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
            if (message.citations.isNotEmpty()) {
                CitationStrip(citations = message.citations)
            }
            MessageFooter(message = message)
            MessageActions(
                message = message,
                canRegenerate = canRegenerate,
                canDelete = canDelete,
                canEdit = false,
                canFork = canFork,
                onRegenerate = onRegenerate,
                onDelete = onDelete,
                onEdit = {},
                onFork = onFork,
                isSpeaking = isSpeaking,
                isSpeakLoading = isSpeakLoading,
                canSpeak = canSpeak,
                onSpeakToggle = onSpeakToggle,
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

@Composable
private fun UserImageThumbnails(uris: List<String>) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .widthIn(max = UserBubbleMaxWidth)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
    ) {
        uris.forEach { uriString ->
            val bitmap = remember(uriString) {
                runCatching {
                    val uri = Uri.parse(uriString)
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                }.getOrNull()
            }
            Surface(
                shape = tnShapes.md,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                modifier = Modifier.size(96.dp),
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Attached image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
