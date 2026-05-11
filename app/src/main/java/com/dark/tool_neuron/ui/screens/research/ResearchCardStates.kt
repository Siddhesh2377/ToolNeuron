package com.dark.tool_neuron.ui.screens.research

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.model.ResearchUiState
import com.dark.tool_neuron.model.ResearchUrlEntry
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens

internal sealed class ResearchCardState {
    object Plan : ResearchCardState()
    data class Search(val query: String, val resultCount: Int) : ResearchCardState()
    data class Fetch(val urls: List<ResearchUrlEntry>) : ResearchCardState()
    data class Compress(val rawBytes: Long, val compressedBytes: Long) : ResearchCardState()
    data class QuestionGen(val questions: List<String>) : ResearchCardState()
    object Final : ResearchCardState()
    object Stopping : ResearchCardState()
    data class Done(val docId: String, val title: String, val summary: String) : ResearchCardState()
    data class Cancelled(val reason: String) : ResearchCardState()
    data class Failed(val message: String) : ResearchCardState()

    companion object {
        fun fromUiState(s: ResearchUiState): ResearchCardState = when (s.phase) {
            ResearchUiState.PHASE_SEARCH -> Search(s.query, s.resultCount)
            ResearchUiState.PHASE_FETCH -> Fetch(s.urls)
            ResearchUiState.PHASE_COMPRESS -> Compress(s.rawBytes, s.compressedBytes)
            ResearchUiState.PHASE_QUESTION_GEN -> QuestionGen(s.questions)
            ResearchUiState.PHASE_FINAL -> Final
            ResearchUiState.PHASE_STOPPING -> Stopping
            ResearchUiState.PHASE_DONE -> Done(s.docId, s.title, s.summary)
            ResearchUiState.PHASE_CANCELLED -> Cancelled(s.message.ifBlank { "Cancelled" })
            ResearchUiState.PHASE_FAILED -> Failed(s.message.ifBlank { "Research failed" })
            else -> Plan
        }
    }
}

@Composable
internal fun SearchRow(state: ResearchCardState.Search) {
    val dimens = LocalDimens.current
    Column {
        StatusLine(TnIcons.Search, "Searching DuckDuckGo")
        Spacer(Modifier.size(dimens.spacingXs))
        Text(
            "“${state.query}”",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (state.resultCount > 0) {
            Spacer(Modifier.size(dimens.spacingXxs))
            Text(
                "${state.resultCount} results",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun FetchRow(state: ResearchCardState.Fetch) {
    val dimens = LocalDimens.current
    Column {
        StatusLine(TnIcons.Globe, "Fetching pages")
        Spacer(Modifier.size(dimens.spacingXs))
        state.urls.take(MAX_URLS_VISIBLE).forEach { entry ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 1.dp),
            ) {
                val tint = when (entry.ok) {
                    true -> MaterialTheme.colorScheme.primary
                    false -> MaterialTheme.colorScheme.error
                    null -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Icon(
                    imageVector = when (entry.ok) {
                        true -> TnIcons.Check
                        false -> TnIcons.X
                        null -> TnIcons.Circle
                    },
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(dimens.spacingXs))
                Text(
                    text = entry.url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (state.urls.size > MAX_URLS_VISIBLE) {
            Text(
                "+${state.urls.size - MAX_URLS_VISIBLE} more",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun CompressRow(state: ResearchCardState.Compress) {
    Column {
        StatusLine(TnIcons.Zap, "Compressing fetched data")
        if (state.rawBytes > 0) {
            Text(
                text = "${formatBytes(state.rawBytes)} → ${formatBytes(state.compressedBytes)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun QuestionGenRow(state: ResearchCardState.QuestionGen) {
    val dimens = LocalDimens.current
    Column {
        StatusLine(TnIcons.MessageCircle, "Generating follow-up questions")
        if (state.questions.isNotEmpty()) {
            Spacer(Modifier.size(dimens.spacingXs))
            state.questions.forEach { q ->
                Text(
                    "• $q",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 1.dp),
                )
            }
        }
    }
}

@Composable
internal fun DoneRow(state: ResearchCardState.Done, onOpenDocument: (String) -> Unit) {
    val dimens = LocalDimens.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = TnIcons.CircleCheck,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(dimens.spacingXs))
            Text(
                text = state.title.ifBlank { "Research complete" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (state.summary.isNotBlank()) {
            Spacer(Modifier.size(dimens.spacingXs))
            Text(
                state.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.size(dimens.spacingSm))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier,
        ) {
            TextButton(onClick = { onOpenDocument(state.docId) }) {
                Icon(
                    imageVector = TnIcons.BookOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Open document",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
internal fun ErrorRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String,
    isError: Boolean,
) {
    val dimens = LocalDimens.current
    val color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(dimens.iconSm),
        )
        Spacer(Modifier.width(dimens.spacingXs))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatusLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
) {
    val dimens = LocalDimens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(dimens.iconSm),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun formatBytes(value: Long): String {
    if (value < 1024) return "${value}B"
    if (value < 1024 * 1024) return "${value / 1024}KB"
    return "${value / (1024 * 1024)}MB"
}

private const val MAX_URLS_VISIBLE = 5
