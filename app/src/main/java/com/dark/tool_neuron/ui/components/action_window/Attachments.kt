package com.dark.tool_neuron.ui.components.action_window

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dark.download_manager.formatBytes
import com.dark.tool_neuron.model.ChatDocument
import com.dark.tool_neuron.model.DocExtension
import com.dark.tool_neuron.ui.components.ActionTextButton
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes

@Composable
internal fun AttachmentsTab(
    documents: List<ChatDocument>,
    onAddAttachment: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    deepIndexing: Set<String> = emptySet(),
    onDeepIndex: (String) -> Unit = {},
    raptorBuilding: Set<String> = emptySet(),
    onBuildRaptor: (String) -> Unit = {},
) {
    val dimens = LocalDimens.current

    Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingSm)) {
        if (documents.isEmpty()) {
            EmptyAttachments()
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingXs)) {
                documents.forEach { doc ->
                    AttachmentRow(
                        doc = doc,
                        isDeepIndexing = doc.id in deepIndexing,
                        isRaptorBuilding = doc.id in raptorBuilding,
                        onRemove = { onRemoveAttachment(doc.id) },
                        onDeepIndex = { onDeepIndex(doc.id) },
                        onBuildRaptor = { onBuildRaptor(doc.id) },
                    )
                }
            }
        }
        ActionTextButton(
            onClickListener = onAddAttachment,
            icon = TnIcons.BookOpen,
            text = "Add attachment",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun EmptyAttachments() {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    Surface(
        shape = tnShapes.cardSmall,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.spacingMd),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingXxs),
        ) {
            Text(
                text = "No attachments yet",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Pick a document from a previous chat or import one from storage.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun AttachmentRow(
    doc: ChatDocument,
    onRemove: () -> Unit,
    isDeepIndexing: Boolean = false,
    onDeepIndex: () -> Unit = {},
    isRaptorBuilding: Boolean = false,
    onBuildRaptor: () -> Unit = {},
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    val ext = DocExtension.resolve(doc.mimeType, doc.name)
    Surface(
        shape = tnShapes.cardSmall,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = dimens.spacingSm,
                vertical = dimens.spacingXs,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExtensionBadge(extension = ext)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = dimens.spacingSm),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = doc.name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (doc.isDeepIndexed) {
                        DeepIndexedBadge()
                    }
                    if (doc.isRaptorIndexed) {
                        RaptorIndexedBadge()
                    }
                }
                val sizeText = if (doc.sizeBytes > 0) formatBytes(doc.sizeBytes) else null
                val chunksText = "${doc.chunkCount} chunk${if (doc.chunkCount == 1) "" else "s"}"
                val subtitle = listOfNotNull(sizeText, chunksText).joinToString(" • ")
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DeepIndexButton(
                isIndexing = isDeepIndexing,
                isAlreadyIndexed = doc.isDeepIndexed,
                onClick = onDeepIndex,
            )
            Box(modifier = Modifier.width(8.dp))
            RaptorActionIcon(
                building = isRaptorBuilding,
                indexed = doc.isRaptorIndexed,
                onClick = onBuildRaptor,
            )
            Box(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = TnIcons.X,
                contentDescription = "Remove attachment",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(18.dp)
                    .clickable(onClick = onRemove),
            )
        }
    }
}

@Composable
private fun DeepIndexButton(
    isIndexing: Boolean,
    isAlreadyIndexed: Boolean,
    onClick: () -> Unit,
) {
    if (isAlreadyIndexed) return
    if (isIndexing) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 1.5.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        return
    }
    Icon(
        imageVector = TnIcons.Sparkles,
        contentDescription = "Deep Index (generate doc context)",
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .size(18.dp)
            .clickable(onClick = onClick),
    )
}

@Composable
private fun RaptorActionIcon(
    building: Boolean,
    indexed: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (indexed) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier.size(22.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (building) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 1.5.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Icon(
                imageVector = TnIcons.Database,
                contentDescription = if (indexed) "RAPTOR indexed" else "Build RAPTOR tree",
                tint = tint,
                modifier = Modifier
                    .size(18.dp)
                    .clickable(enabled = !indexed, onClick = onClick),
            )
        }
    }
}

@Composable
private fun RaptorIndexedBadge() {
    val tnShapes = LocalTnShapes.current
    Surface(
        shape = tnShapes.cardSmall,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
        modifier = Modifier.padding(start = 4.dp),
    ) {
        Text(
            text = "RAPTOR",
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun DeepIndexedBadge() {
    val tnShapes = LocalTnShapes.current
    Surface(
        shape = tnShapes.cardSmall,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
        modifier = Modifier.padding(start = 4.dp),
    ) {
        Text(
            text = "Deep",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

@Composable
internal fun ExtensionBadge(
    extension: DocExtension,
    sizeDp: Int = 40,
) {
    val tnShapes = LocalTnShapes.current
    val tint = extension.tint
    Surface(
        shape = tnShapes.cardSmall,
        color = tint.copy(alpha = 0.18f).compositeOver(MaterialTheme.colorScheme.surface),
    ) {
        Box(
            modifier = Modifier.size(sizeDp.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = extension.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = readableOn(tint),
            )
        }
    }
}

private fun readableOn(c: Color): Color {
    val luminance = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
    return if (luminance < 0.55f) Color.White else Color(0xFF1A1A1A)
}

@Composable
internal fun ExtensionBadgeSpacer(width: Int) {
    Box(modifier = Modifier.width(width.dp))
}
