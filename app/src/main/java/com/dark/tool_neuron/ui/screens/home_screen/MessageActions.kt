package com.dark.tool_neuron.ui.screens.home_screen

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.dark.tool_neuron.model.ChatMessage
import com.dark.tool_neuron.model.ui.ActionIcon
import com.dark.tool_neuron.model.ui.ActionItem
import com.dark.tool_neuron.ui.components.MultiActionButton
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.util.SecureClipboard
import kotlinx.coroutines.delay

private const val ROLE_ASSISTANT = "assistant"
private const val COPY_FLASH_DURATION_MS = 1500L

@Composable
fun MessageActions(
    message: ChatMessage,
    canRegenerate: Boolean,
    canDelete: Boolean,
    canEdit: Boolean,
    canFork: Boolean,
    onRegenerate: () -> Unit,
    onDelete: (String) -> Unit,
    onEdit: () -> Unit,
    onFork: (String) -> Unit,
    isSpeaking: Boolean = false,
    isSpeakLoading: Boolean = false,
    canSpeak: Boolean = false,
    onSpeakToggle: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var copied by remember(message.id) { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(COPY_FLASH_DURATION_MS)
            copied = false
        }
    }

    val actions = buildList {
        add(
            ActionItem(
                icon = ActionIcon.Vector(if (copied) TnIcons.CircleCheck else TnIcons.Copy),
                onClick = {
                    copyToClipboard(context, message.content)
                    copied = true
                },
                contentDescription = "Copy",
            )
        )
        if (message.role == ROLE_ASSISTANT && canSpeak && message.content.isNotBlank()) {
            val active = isSpeaking || isSpeakLoading
            add(
                ActionItem(
                    icon = ActionIcon.Vector(if (active) TnIcons.PlayerStop else TnIcons.Volume),
                    onClick = onSpeakToggle,
                    contentDescription = when {
                        isSpeakLoading -> "Loading voice"
                        isSpeaking -> "Stop speaking"
                        else -> "Speak"
                    },
                    isLoading = isSpeakLoading,
                )
            )
        }
        if (canEdit) {
            add(
                ActionItem(
                    icon = ActionIcon.Vector(TnIcons.Edit),
                    onClick = onEdit,
                    contentDescription = "Edit",
                )
            )
        }
        if (message.role == ROLE_ASSISTANT && canRegenerate) {
            add(
                ActionItem(
                    icon = ActionIcon.Vector(TnIcons.Refresh),
                    onClick = onRegenerate,
                    contentDescription = "Regenerate",
                )
            )
        }
        if (canFork) {
            add(
                ActionItem(
                    icon = ActionIcon.Vector(TnIcons.Fork),
                    onClick = { onFork(message.id) },
                    contentDescription = "Fork into new chat",
                )
            )
        }
        if (canDelete) {
            add(
                ActionItem(
                    icon = ActionIcon.Vector(TnIcons.Trash),
                    onClick = { onDelete(message.id) },
                    contentDescription = "Delete",
                )
            )
        }
    }

    MultiActionButton(actions = actions, modifier = modifier)
}

private fun copyToClipboard(context: Context, text: String) {
    SecureClipboard.copy(context, "message", text)
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}
