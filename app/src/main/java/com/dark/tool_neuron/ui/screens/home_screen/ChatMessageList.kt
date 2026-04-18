package com.dark.tool_neuron.ui.screens.home_screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.model.ChatMessage
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.ui.theme.Motion
import com.dark.tool_neuron.viewmodel.StreamingFragment
import kotlinx.coroutines.launch

private const val ROLE_ASSISTANT = "assistant"
private const val STREAMING_ITEM_KEY = "__streaming__"

@Composable
fun ChatMessageList(
    messages: List<ChatMessage>,
    streaming: StreamingFragment?,
    isGenerating: Boolean,
    onRegenerate: () -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val totalItems = messages.size + if (streaming != null) 1 else 0
    val lastAssistantId = messages.lastOrNull { it.role == ROLE_ASSISTANT }?.id

    val isAtBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            last.index >= info.totalItemsCount - 1
        }
    }

    val streamingSignature = streaming?.let { it.content.length + it.thinkingContent.length } ?: 0

    LaunchedEffect(streamingSignature, messages.size) {
        if (isAtBottom && totalItems > 0) {
            listState.scrollToItem(totalItems - 1)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(dimens.spacingMd),
        ) {
            items(items = messages, key = { it.id }) { message ->
                MessageBubble(
                    message = message,
                    canRegenerate = !isGenerating && message.id == lastAssistantId,
                    canDelete = !isGenerating,
                    onRegenerate = onRegenerate,
                    onDelete = onDelete,
                )
            }
            if (streaming != null) {
                item(key = STREAMING_ITEM_KEY) {
                    if (streaming.content.isBlank() && streaming.thinkingContent.isBlank()) {
                        TypingIndicator()
                    } else {
                        StreamingAssistantBubble(
                            content = streaming.content,
                            thinkingContent = streaming.thinkingContent,
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = !isAtBottom && totalItems > 1,
            enter = fadeIn(Motion.state()) + scaleIn(Motion.state(), initialScale = 0.8f),
            exit = fadeOut(Motion.state()) + scaleOut(Motion.state(), targetScale = 0.8f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = dimens.spacingSm),
        ) {
            ActionButton(
                onClickListener = {
                    scope.launch {
                        if (totalItems > 0) listState.animateScrollToItem(totalItems - 1)
                    }
                },
                icon = TnIcons.ChevronDown,
                contentDescription = "Jump to bottom",
                shape = tnShapes.full,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}
