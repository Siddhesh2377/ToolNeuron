package com.dark.tool_neuron.ui.screens.web_search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.model.ChatMessage
import com.dark.tool_neuron.model.WebSearchHit
import com.dark.tool_neuron.model.WebSearchUiState
import com.dark.tool_neuron.ui.components.markdown.MarkdownText
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.screens.home_screen.MessageActions
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.ui.theme.Motion

@Composable
fun WebSearchCard(
    message: ChatMessage,
    state: WebSearchUiState,
    onCancel: () -> Unit,
    canRegenerate: Boolean = false,
    canDelete: Boolean = false,
    canFork: Boolean = false,
    onRegenerate: () -> Unit = {},
    onDelete: (String) -> Unit = {},
    onFork: (String) -> Unit = {},
    isSpeaking: Boolean = false,
    isSpeakLoading: Boolean = false,
    canSpeak: Boolean = false,
    onSpeakToggle: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current

    Surface(
        shape = tnShapes.lg,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            .compositeOver(MaterialTheme.colorScheme.surface),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = dimens.spacingXs),
    ) {
        Column(modifier = Modifier.padding(dimens.spacingMd)) {
            Header(userQuery = message.content)
            Spacer(Modifier.size(dimens.spacingSm))

            QueriesStrip(state)

            AnimatedContent(
                targetState = state.phase,
                transitionSpec = { fadeIn(Motion.state()) togetherWith fadeOut(Motion.state()) },
                label = "web_search_phase",
            ) { phase ->
                when (phase) {
                    WebSearchUiState.PHASE_PLAN -> StatusRow(TnIcons.Sparkles, "Planning search…")
                    WebSearchUiState.PHASE_QUERIES -> StatusRow(TnIcons.Search, "Generated 3 queries")
                    WebSearchUiState.PHASE_SEARCH -> SearchProgressRow(state)
                    WebSearchUiState.PHASE_SYNTHESIZE -> StatusRow(TnIcons.Sparkles, "Writing answer from sources…")
                    WebSearchUiState.PHASE_STOPPING -> StatusRow(TnIcons.PlayerStop, "Stopping…")
                    WebSearchUiState.PHASE_DONE -> DoneSection(
                        state = state,
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
                    )
                    WebSearchUiState.PHASE_CANCELLED -> ErrorRow(TnIcons.X, state.message.ifBlank { "Cancelled" }, isError = false)
                    WebSearchUiState.PHASE_FAILED -> ErrorRow(TnIcons.AlertTriangle, state.message.ifBlank { "Failed" }, isError = true)
                    else -> StatusRow(TnIcons.Search, phase)
                }
            }

            // Footer for non-Done phases: Stop while in-flight, plus a
            // Delete button that's always available so users can clear a
            // stuck / failed / cancelled card without waiting for it to
            // resolve. PHASE_DONE keeps its own MessageActions row inside
            // DoneSection, which already includes Delete.
            AnimatedVisibility(visible = state.phase != WebSearchUiState.PHASE_DONE) {
                Column {
                    Spacer(Modifier.size(dimens.spacingSm))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (state.isInFlight()) {
                            TextButton(onClick = onCancel) {
                                Icon(
                                    imageVector = TnIcons.PlayerStop,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.size(dimens.spacingXs))
                                Text("Stop")
                            }
                        }
                        if (canDelete) {
                            TextButton(onClick = {
                                // Cancel first if still running so the
                                // coordinator releases the chat LLM and
                                // doesn't keep writing to a now-dead msg.
                                if (state.isInFlight()) onCancel()
                                onDelete(message.id)
                            }) {
                                Icon(
                                    imageVector = TnIcons.Trash,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.size(dimens.spacingXs))
                                Text("Delete")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(userQuery: String) {
    val dimens = LocalDimens.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = TnIcons.Globe,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.size(dimens.spacingXs))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Web search",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            if (userQuery.isNotBlank()) {
                Text(
                    text = userQuery,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun QueriesStrip(state: WebSearchUiState) {
    if (state.queries.isEmpty()) return
    val dimens = LocalDimens.current
    Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingXs)) {
        state.queries.forEachIndexed { idx, q ->
            val hitCount = state.perQueryHits[idx]?.size ?: 0
            val isCurrent = state.currentQueryIndex == idx && state.isInFlight()
            QueryRow(
                index = idx + 1,
                query = q,
                hitCount = hitCount,
                isCurrent = isCurrent,
                hasRun = state.perQueryHits.containsKey(idx),
            )
        }
        Spacer(Modifier.size(dimens.spacingXs))
    }
}

@Composable
private fun QueryRow(
    index: Int,
    query: String,
    hitCount: Int,
    isCurrent: Boolean,
    hasRun: Boolean,
) {
    val dimens = LocalDimens.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        when {
            isCurrent -> CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 1.5.dp,
            )
            hasRun -> Icon(
                imageVector = TnIcons.Check,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            else -> Icon(
                imageVector = TnIcons.Circle,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
        }
        Spacer(Modifier.size(dimens.spacingSm))
        Text(
            text = "$index. $query",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (hasRun) {
            Text(
                text = "$hitCount",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    val dimens = LocalDimens.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.size(dimens.spacingXs))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SearchProgressRow(state: WebSearchUiState) {
    val idx = state.currentQueryIndex.coerceAtLeast(0)
    val current = state.queries.getOrNull(idx).orEmpty()
    val total = state.queries.size
    val label = if (current.isBlank()) "Searching…" else "Searching ${idx + 1}/$total: $current"
    StatusRow(TnIcons.Search, label)
}

@Composable
private fun ErrorRow(icon: androidx.compose.ui.graphics.vector.ImageVector, message: String, isError: Boolean) {
    val dimens = LocalDimens.current
    val tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = tint,
        )
        Spacer(Modifier.size(dimens.spacingXs))
        Text(text = message, style = MaterialTheme.typography.bodyMedium, color = tint)
    }
}

@Composable
private fun DoneSection(
    state: WebSearchUiState,
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
) {
    val dimens = LocalDimens.current
    var showSources by remember { mutableStateOf(false) }
    // MessageActions reads message.content for Copy/Speak. The card stores
    // the user's query there; for the answer-side actions we want the
    // synthesized answer instead, so we swap content on a copy.
    val actionMessage = remember(message.id, state.answer) {
        message.copy(content = state.answer)
    }
    val trimmedAnswer = remember(state.answer) { state.answer.trim() }
    Column {
        if (trimmedAnswer.isBlank()) {
            Text(
                text = "The model didn't produce a summary. Open the sources below for raw results.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            MarkdownText(text = trimmedAnswer)
        }

        if (state.sources.isNotEmpty()) {
            Spacer(Modifier.size(dimens.spacingSm))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showSources = !showSources },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (showSources) TnIcons.ChevronUp else TnIcons.ChevronDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(dimens.spacingXs))
                Text(
                    text = "${state.sources.size} sources",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = showSources) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(dimens.spacingXs),
                    modifier = Modifier.padding(top = dimens.spacingXs),
                ) {
                    state.sources.forEachIndexed { idx, hit ->
                        SourceRow(index = idx + 1, hit = hit)
                    }
                }
            }
        }

        Spacer(Modifier.size(dimens.spacingSm))
        MessageActions(
            message = actionMessage,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SourceRow(index: Int, hit: WebSearchHit) {
    val dimens = LocalDimens.current
    val uriHandler = LocalUriHandler.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {
                if (hit.url.isNotBlank()) runCatching { uriHandler.openUri(hit.url) }
            }),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "[$index]",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.size(dimens.spacingXs))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = hit.title.ifBlank { hit.url },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (hit.url.isNotBlank()) {
                Text(
                    text = hit.url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
