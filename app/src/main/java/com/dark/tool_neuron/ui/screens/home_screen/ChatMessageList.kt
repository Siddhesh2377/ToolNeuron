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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.model.ChatMessage
import com.dark.tool_neuron.model.ResearchUiState
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.screens.research.ResearchCard
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.ui.theme.Motion
import com.dark.tool_neuron.viewmodel.home_vm.GenerationStatus
import com.dark.tool_neuron.viewmodel.home_vm.StreamingFragment
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val ROLE_ASSISTANT = "assistant"
private const val STREAMING_ITEM_KEY = "__streaming__"
private const val STATUS_ITEM_KEY = "__status__"

@Composable
fun ChatMessageList(
    messages: List<ChatMessage>,
    streaming: StreamingFragment?,
    isGenerating: Boolean,
    generationStatus: GenerationStatus,
    speakingMessageId: String?,
    loadingSpeakId: String?,
    canSpeak: Boolean,
    onSpeakToggle: (messageId: String, text: String) -> Unit,
    onRegenerate: () -> Unit,
    onDelete: (String) -> Unit,
    onEditUserMessage: (messageId: String, newContent: String) -> Unit,
    onForkFromMessage: (String) -> Unit,
    onOpenResearchDocument: (String) -> Unit = {},
    onCancelResearch: (String) -> Unit = {},
    retrievalLabel: String? = null,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val showStatus = when (generationStatus) {
        GenerationStatus.Hidden,
        is GenerationStatus.GeneratingText,
        is GenerationStatus.Thinking -> false
        else -> true
    }
    val totalItems = messages.size +
            (if (streaming != null) 1 else 0) +
            (if (showStatus) 1 else 0)
    val lastAssistantId = messages.lastOrNull { it.role == ROLE_ASSISTANT }?.id

    val canScrollForward by remember(listState) {
        derivedStateOf { listState.canScrollForward }
    }

    var stickToBottom by remember { mutableStateOf(true) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (!scrolling) stickToBottom = !listState.canScrollForward
            }
    }

    LaunchedEffect(messages.size, totalItems) {
        if (totalItems > 0) stickToBottom = true
    }

    val streamingSignature = streaming?.let { it.content.length + it.thinkingContent.length } ?: 0

    LaunchedEffect(streamingSignature, totalItems) {
        if (stickToBottom && totalItems > 0 && !listState.isScrollInProgress) {
            listState.scrollToItem(totalItems - 1, scrollOffset = Int.MAX_VALUE)
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
                val runId = message.researchRunId
                if (runId != null) {
                    ResearchCard(
                        question = message.content,
                        state = ResearchUiState.fromJson(message.researchState),
                        onOpenDocument = onOpenResearchDocument,
                        onCancel = { onCancelResearch(runId) },
                    )
                } else {
                    MessageBubble(
                        message = message,
                        canRegenerate = !isGenerating && message.id == lastAssistantId,
                        canDelete = !isGenerating,
                        canEdit = !isGenerating && message.role == "user",
                        canFork = !isGenerating,
                        onRegenerate = onRegenerate,
                        onDelete = onDelete,
                        onEdit = onEditUserMessage,
                        onFork = onForkFromMessage,
                        isSpeaking = speakingMessageId == message.id,
                        isSpeakLoading = loadingSpeakId == message.id && speakingMessageId != message.id,
                        canSpeak = canSpeak,
                        onSpeakToggle = { onSpeakToggle(message.id, message.content) },
                    )
                }
            }
            if (streaming != null) {
                item(key = STREAMING_ITEM_KEY) {
                    if (retrievalLabel != null && streaming.content.isBlank() && streaming.thinkingContent.isBlank()) {
                        StreamingAssistantBubble(
                            content = "",
                            thinkingContent = "",
                            retrievalLabel = retrievalLabel,
                        )
                    } else if (streaming.content.isBlank() && streaming.thinkingContent.isBlank()) {
                        TypingIndicator()
                    } else {
                        StreamingAssistantBubble(
                            content = streaming.content,
                            thinkingContent = streaming.thinkingContent,
                            retrievalLabel = retrievalLabel,
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = canScrollForward && totalItems > 1,
            enter = fadeIn(Motion.state()) + scaleIn(Motion.state(), initialScale = 0.8f),
            exit = fadeOut(Motion.state()) + scaleOut(Motion.state(), targetScale = 0.8f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = dimens.spacingSm),
        ) {
            ActionButton(
                onClickListener = {
                    scope.launch {
                        if (totalItems > 0) {
                            listState.animateScrollToItem(totalItems - 1, scrollOffset = Int.MAX_VALUE)
                        }
                        stickToBottom = true
                    }
                },
                icon = TnIcons.ChevronDown,
                contentDescription = "Jump to bottom",
                shape = tnShapes.actionIcon,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}
