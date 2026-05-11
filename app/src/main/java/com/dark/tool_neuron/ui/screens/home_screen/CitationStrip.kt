package com.dark.tool_neuron.ui.screens.home_screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.model.Citation
import com.dark.tool_neuron.model.DocExtension
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes

@Composable
fun CitationStrip(citations: List<Citation>) {
    if (citations.isEmpty()) return

    val grouped = remember(citations) {
        citations.groupBy { it.docId }.entries
            .map { (docId, list) -> DocCitationGroup(docId = docId, citations = list) }
            .sortedByDescending { group -> group.citations.maxOf { it.score } }
    }
    var expanded by remember(citations) { mutableStateOf(false) }
    var openCitation by remember(citations) { mutableStateOf<Citation?>(null) }

    val anyCited = remember(citations) { citations.any { it.cited } }
    val title = if (anyCited) "Sources" else "Possible sources"
    val subtitle = buildString {
        append(grouped.size)
        append(if (grouped.size == 1) " doc" else " docs")
        append(" · ")
        append(citations.size)
        append(if (citations.size == 1) " chunk" else " chunks")
    }

    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")

    Surface(
        shape = tnShapes.cardSmall,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = dimens.spacingSm, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
            ) {
                Icon(
                    imageVector = TnIcons.BookOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                }
                Icon(
                    imageVector = TnIcons.ChevronDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(rotation),
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = dimens.spacingSm,
                            end = dimens.spacingSm,
                            top = 0.dp,
                            bottom = dimens.spacingSm,
                        ),
                    verticalArrangement = Arrangement.spacedBy(dimens.spacingXs),
                ) {
                    grouped.forEach { group ->
                        DocGroupRow(
                            group = group,
                            onChunkTap = { openCitation = it },
                        )
                    }
                }
            }
        }
    }

    openCitation?.let { current ->
        CitationDialog(citation = current, onDismiss = { openCitation = null })
    }
}

private data class DocCitationGroup(
    val docId: String,
    val citations: List<Citation>,
)

@Composable
private fun DocGroupRow(group: DocCitationGroup, onChunkTap: (Citation) -> Unit) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    val first = group.citations.first()
    val ext = remember(first.mimeType, first.name) {
        DocExtension.resolve(first.mimeType, first.name)
    }
    val cited = group.citations.any { it.cited }
    val tint = ext.tint

    Surface(
        shape = tnShapes.cardSmall,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(dimens.spacingXs)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Surface(
                    shape = tnShapes.cardSmall,
                    color = tint.copy(alpha = 0.18f).compositeOver(MaterialTheme.colorScheme.surface),
                    modifier = Modifier.size(28.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = ext.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = readableOn(tint),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = first.name.ifBlank { "Document" },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Text(
                        text = "${group.citations.size} chunk${if (group.citations.size == 1) "" else "s"} · " +
                            (if (cited) "cited" else "possibly used"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                group.citations.sortedBy { it.chunkIndex }.forEach { c ->
                    ChunkRow(citation = c, onClick = { onChunkTap(c) })
                }
            }
        }
    }
}

@Composable
private fun ChunkRow(citation: Citation, onClick: () -> Unit) {
    val dimens = LocalDimens.current
    val color = if (citation.cited) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
    ) {
        Text(
            text = "[${citation.chunkIndex}]",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.width(36.dp),
        )
        Text(
            text = citation.snippet.ifBlank { "(no preview)" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "%.2f".format(citation.score),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
        )
    }
}

@Composable
private fun CitationDialog(citation: Citation, onDismiss: () -> Unit) {
    val dimens = LocalDimens.current
    val ext = remember(citation.mimeType, citation.name) {
        DocExtension.resolve(citation.mimeType, citation.name)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
            ) {
                Surface(
                    color = ext.tint.copy(alpha = 0.18f).compositeOver(MaterialTheme.colorScheme.surface),
                    modifier = Modifier.size(28.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = ext.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = readableOn(ext.tint),
                        )
                    }
                }
                Text(
                    text = citation.name.ifBlank { "Document" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingXs)) {
                Text(
                    text = citation.snippet.ifBlank { "(no preview available)" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm)) {
                    Text(
                        text = "Chunk ${citation.chunkIndex}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                    Text(
                        text = "Score %.2f".format(citation.score),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                    Text(
                        text = if (citation.cited) "Cited" else "Possibly used",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = if (citation.cited) 1f else 0.5f),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private fun readableOn(c: Color): Color {
    val luminance = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
    return if (luminance < 0.55f) Color.White else Color(0xFF1A1A1A)
}
