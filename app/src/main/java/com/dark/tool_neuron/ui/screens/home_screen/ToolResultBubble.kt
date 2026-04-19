package com.dark.tool_neuron.ui.screens.home_screen

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.net.toUri
import com.dark.tool_neuron.model.ChatMessage
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.ui.theme.Motion
import org.json.JSONException
import org.json.JSONObject

@Composable
fun ToolResultBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    val parsed = remember(message.id, message.content) { parseToolPayload(message.content) }

    var expanded by rememberSaveable(message.id) { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = Motion.state(),
        label = "toolChevron",
    )

    val hasBody = parsed.results.isNotEmpty() || parsed.error != null

    Surface(
        shape = tnShapes.cardSmall,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(Motion.content()),
    ) {
        Column(
            modifier = Modifier.padding(dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (hasBody) Modifier.clickable { expanded = !expanded }
                        else Modifier
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
            ) {
                Icon(
                    imageVector = if (parsed.error != null) TnIcons.AlertTriangle else TnIcons.Globe,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(dimens.iconSm),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            parsed.error != null -> "${message.modelName.ifEmpty { "tool" }} · error"
                            parsed.query != null -> "${message.modelName.ifEmpty { "tool" }} · \"${parsed.query}\""
                            else -> message.modelName.ifEmpty { "tool" }
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = parsed.error ?: summarize(parsed.results),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (hasBody && parsed.error == null) {
                    Icon(
                        imageVector = TnIcons.ChevronDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(dimens.iconSm)
                            .rotate(chevronRotation),
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded && parsed.results.isNotEmpty(),
                enter = fadeIn(Motion.state()) + expandVertically(Motion.content()),
                exit = fadeOut(Motion.state()) + shrinkVertically(Motion.content()),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingSm)) {
                    parsed.results.forEach { r ->
                        CitationChip(title = r.title, url = r.url, snippet = r.snippet)
                    }
                    ResultsFootnote(results = parsed.results)
                }
            }
        }
    }
}

@Composable
private fun CitationChip(
    title: String,
    url: String,
    snippet: String,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    val context = LocalContext.current

    Surface(
        shape = tnShapes.cardSmall,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                runCatching {
                    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            },
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = dimens.spacingMd,
                vertical = dimens.spacingSm,
            ),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingXxs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = hostOf(url),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (snippet.isNotBlank()) {
                Text(
                    text = snippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ResultsFootnote(results: List<ToolResultEntry>) {
    val dimens = LocalDimens.current
    val domainCount = results.map { hostOf(it.url) }.distinct().size
    val text = buildString {
        append(results.size)
        append(if (results.size == 1) " result" else " results")
        append(" · ")
        append(domainCount)
        append(if (domainCount == 1) " source" else " sources")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingXxs),
    ) {
        Icon(
            imageVector = TnIcons.Sparkles,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(dimens.iconSm),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

private fun summarize(results: List<ToolResultEntry>): String {
    if (results.isEmpty()) return "no results"
    val domains = results.map { hostOf(it.url) }.distinct()
    val count = results.size
    val source = if (domains.size == 1) domains.first() else "${domains.size} sources"
    return "$count ${if (count == 1) "result" else "results"} · $source"
}

private data class ToolPayload(
    val query: String?,
    val results: List<ToolResultEntry>,
    val error: String?,
)

private data class ToolResultEntry(
    val title: String,
    val url: String,
    val snippet: String,
)

private fun parseToolPayload(raw: String): ToolPayload {
    if (raw.isBlank()) return ToolPayload(null, emptyList(), null)
    return try {
        val root = JSONObject(raw)
        val error = root.optString("error").takeIf { it.isNotEmpty() }
        val query = root.optString("query").takeIf { it.isNotEmpty() }
        val arr = root.optJSONArray("results")
        val results = if (arr != null) {
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        ToolResultEntry(
                            title = o.optString("title"),
                            url = o.optString("url"),
                            snippet = o.optString("snippet"),
                        )
                    )
                }
            }
        } else emptyList()
        ToolPayload(query = query, results = results, error = error)
    } catch (_: JSONException) {
        ToolPayload(null, emptyList(), raw.take(200))
    }
}

private fun hostOf(url: String): String = runCatching {
    url.toUri().host ?: url
}.getOrDefault(url)
