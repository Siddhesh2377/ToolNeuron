package com.dark.tool_neuron.ui.screen.home

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.models.ModelType
import com.dark.tool_neuron.models.messages.ContentType
import com.dark.tool_neuron.models.messages.Role
import com.dark.tool_neuron.ui.components.lazyMarkdownItems
import com.dark.tool_neuron.ui.theme.Motion
import com.dark.tool_neuron.viewmodel.ChatViewModel
import com.dark.tool_neuron.viewmodel.LLMModelViewModel
import com.dark.tool_neuron.global.Standards
import com.dark.tool_neuron.viewmodel.StreamingState
import com.dark.tool_neuron.viewmodel.ChatUiState
import com.dark.tool_neuron.viewmodel.AgentState
import com.dark.tool_neuron.viewmodel.RagState
import com.dark.tool_neuron.viewmodel.ChatConfigState

// ── Pre-compiled regex (avoid allocation in composition) ──

internal val THINK_TAG_REGEX = Regex(
    "<think>(.*?)</think>|\\[THINK](.*?)\\[/THINK]|<reasoning>(.*?)</reasoning>",
    RegexOption.DOT_MATCHES_ALL
)
private val THINK_OPEN_TAGS = listOf("<think>", "[THINK]", "<reasoning>")

data class ParsedMessage(
    val thinkingContent: String?,
    val actualContent: String,
    val isThinkingInProgress: Boolean = false
)

fun parseThinkingTags(content: String): ParsedMessage {
    // Fast path: no think tags at all
    val openTag = THINK_OPEN_TAGS.firstOrNull { content.contains(it, ignoreCase = true) }
        ?: return ParsedMessage(null, content.trim())

    // Completed thinking: matched pair present
    val thinkingMatch = THINK_TAG_REGEX.find(content)
    if (thinkingMatch != null) {
        // Group 1 = <think>, Group 2 = [THINK], Group 3 = <reasoning>
        val thinkingContent = thinkingMatch.groupValues.drop(1).firstOrNull { it.isNotEmpty() }?.trim() ?: ""
        val actualContent = content.replace(THINK_TAG_REGEX, "").trim()
        return ParsedMessage(
            thinkingContent = thinkingContent.ifEmpty { null },
            actualContent = actualContent
        )
    }

    // In-progress thinking: open tag without close tag (streaming)
    val openIdx = content.indexOf(openTag, ignoreCase = true)
    val thinkingContent = content.substring(openIdx + openTag.length).trim()
    val beforeThink = content.substring(0, openIdx).trim()
    return ParsedMessage(
        thinkingContent = thinkingContent.ifEmpty { null },
        actualContent = beforeThink,
        isThinkingInProgress = true
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun BodyContent(
    paddingValues: PaddingValues,
    chatViewModel: ChatViewModel,
    llmModelViewModel: LLMModelViewModel
) {
    val messages = chatViewModel.messages
    val streaming by chatViewModel.streamingState.collectAsStateWithLifecycle()
    val chatState by chatViewModel.chatUiState.collectAsStateWithLifecycle()
    val agent by chatViewModel.agentState.collectAsStateWithLifecycle()
    val rag by chatViewModel.ragState.collectAsStateWithLifecycle()
    val config by chatViewModel.chatConfigState.collectAsStateWithLifecycle()
    val appState by com.dark.tool_neuron.state.AppStateManager.appState.collectAsStateWithLifecycle()
    val ttsPlayingMsgId by chatViewModel.ttsPlayingMsgId.collectAsStateWithLifecycle()
    val ttsIsPlaying by chatViewModel.ttsIsPlaying.collectAsStateWithLifecycle()
    val ttsSynthesizing by chatViewModel.ttsSynthesizing.collectAsStateWithLifecycle()
    val ttsModelLoaded by chatViewModel.ttsModelLoaded.collectAsStateWithLifecycle()

    // Image blur setting — collected once, passed down to avoid per-message DataStore creation
    val context = androidx.compose.ui.platform.LocalContext.current
    val imageBlurEnabled by remember { com.dark.tool_neuron.data.AppSettingsDataStore(context).imageBlurEnabled }
        .collectAsStateWithLifecycle(initialValue = true)

    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && !chatState.isGenerating) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(paddingValues)
    ) {
        if (messages.isEmpty() && !chatState.isGenerating) {
            EmptyMessagesState()
        } else {
            if (chatState.isGenerating && streaming.userMessage != null) {
                StreamingView(
                    userMessage = streaming.userMessage!!,
                    assistantMessage = streaming.assistantMessage,
                    streamingImage = streaming.image,
                    imageProgress = streaming.imageProgress,
                    imageStep = streaming.imageStep,
                    isImageGeneration = chatState.generationType == ModelType.IMAGE_GENERATION,
                    ragResults = rag.results,
                    appState = appState,
                    messages = messages,
                    toolChainSteps = agent.toolChainSteps,
                    currentToolChainRound = agent.currentRound,
                    agentPhase = agent.phase,
                    agentPlan = agent.plan,
                    agentSummary = agent.summary,
                    thinkingEnabled = chatState.thinkingEnabled
                )
            } else {
                val deduped = remember(messages.size) { messages.distinctBy { it.msgId } }
                val lastAssistantIndex = remember(deduped.size) { deduped.indexOfLast { it.role == Role.Assistant } }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = Standards.SpacingSm),
                    verticalArrangement = Arrangement.spacedBy(Standards.SpacingXs)
                ) {

                    deduped.forEachIndexed { index, message ->
                        when (message.role) {
                            Role.User -> {
                                item(key = "${message.msgId}-user") {
                                    UserMessageBubble(message)
                                }
                            }
                            else -> {
                                val isLastAssistant = index == lastAssistantIndex
                                // Header: RAG, tool chain, thinking, image/plugin
                                item(key = "${message.msgId}-header") {
                                    AssistantMessageHeader(message, imageBlurEnabled)
                                }
                                // Markdown content — each element is a lazy item
                                if (message.content.contentType == ContentType.Text) {
                                    val raw = message.content.content
                                    val parsedText = if (THINK_TAG_REGEX.containsMatchIn(raw)) {
                                        raw.replace(THINK_TAG_REGEX, "").trim()
                                    } else raw
                                    if (parsedText.isNotEmpty()) {
                                        lazyMarkdownItems(
                                            text = parsedText,
                                            keyPrefix = message.msgId,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = Standards.SpacingMd)
                                        )
                                    }
                                }
                                // Footer: metrics + action row
                                item(key = "${message.msgId}-footer") {
                                    AssistantMessageFooter(
                                        message = message,
                                        ttsPlayingMsgId = ttsPlayingMsgId,
                                        ttsIsPlaying = ttsIsPlaying,
                                        ttsSynthesizing = ttsSynthesizing,
                                        ttsModelLoaded = ttsModelLoaded,
                                        onSpeak = { chatViewModel.speakMessage(it) },
                                        onStopTTS = { chatViewModel.stopTTS() },
                                        onRegenerate = if (isLastAssistant) {
                                            { chatViewModel.regenerateLastMessage() }
                                        } else null,
                                        isRegenerateEnabled = !chatState.isGenerating
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.height(Standards.SpacingLg))
                    }
                }
            }
        }

        // Scrim + Dynamic Action Window — single AnimatedVisibility to avoid double state reads
        AnimatedVisibility(
            visible = config.showDynamicWindow,
            enter = fadeIn(Motion.entrance()),
            exit = fadeOut(Motion.exit())
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Scrim background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            chatViewModel.hideDynamicWindow()
                        }
                )

                // Window content with spring animation
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Standards.SpacingLg, vertical = Standards.SpacingLg),
                    contentAlignment = Alignment.TopCenter
                ) {
                    val ragCount by com.dark.tool_neuron.plugins.PluginManager.enabledPluginNames.collectAsStateWithLifecycle()
                    val ttsLoaded by com.dark.tool_neuron.tts.TTSManager.isModelLoaded.collectAsStateWithLifecycle()

                    DynamicActionWindow(
                        chatViewModel = chatViewModel,
                        modelViewModel = llmModelViewModel,
                        enabledToolCount = ragCount.size,
                        ttsModelLoaded = ttsLoaded
                    )
                }
            }
        }
    }
}
