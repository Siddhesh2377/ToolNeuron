package com.dark.tool_neuron.ui.screens.home_screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.ui.components.SectionHeader
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToolsPickerWindow(
    thinkingEnabled: Boolean,
    thinkingSupported: Boolean,
    webSearchEnabled: Boolean,
    canAttachImage: Boolean,
    canAttachFiles: Boolean,
    canCompact: Boolean,
    onToggleThinking: () -> Unit,
    onToggleWebSearch: () -> Unit,
    onAttachImage: () -> Unit,
    onAttachFiles: () -> Unit,
    onCompactChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = dimens.spacingSm),
        shape = tnShapes.xl,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = dimens.spacingMd,
                vertical = dimens.spacingSm,
            ),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            SectionHeader(title = "Tools")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
            ) {
                ToggleToolCard(
                    Modifier.weight(1f),
                    icon = TnIcons.Sparkles,
                    label = "Thinking",
                    description = if (thinkingSupported) "Step-by-step reasoning" else "Not supported",
                    enabled = thinkingSupported,
                    active = thinkingEnabled,
                    onClick = onToggleThinking,
                )
                ToggleToolCard(
                    Modifier.weight(1f),
                    icon = TnIcons.Globe,
                    label = "Web search",
                    description = "3 queries, snippets, answer",
                    enabled = true,
                    active = webSearchEnabled,
                    onClick = onToggleWebSearch,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
            ) {
                ActionToolCard(
                    Modifier.weight(1f),
                    icon = TnIcons.Photo,
                    label = "Image",
                    description = if (canAttachImage) "Add a picture" else "Load a VLM",
                    enabled = canAttachImage,
                    onClick = onAttachImage,
                )
                ActionToolCard(
                    Modifier.weight(1f),
                    icon = TnIcons.FileText,
                    label = "Files",
                    description = if (canAttachFiles) "Add document for RAG" else "Download a embedding model",
                    enabled = canAttachFiles,
                    onClick = onAttachFiles,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
            ) {
                ActionToolCard(
                    Modifier.fillMaxWidth(),
                    icon = TnIcons.Sparkles,
                    label = "Compact chat",
                    description = if (canCompact) "Summarize and free context"
                                  else "Need a loaded model and a non-empty chat",
                    enabled = canCompact,
                    onClick = onCompactChat,
                )
            }
        }
    }
}

@Composable
private fun ToggleToolCard(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    description: String,
    enabled: Boolean,
    active: Boolean,
    onClick: () -> Unit,
) {
    val container = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
        active   -> MaterialTheme.colorScheme.primary
        else     -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    }
    val content = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        active   -> MaterialTheme.colorScheme.onPrimary
        else     -> MaterialTheme.colorScheme.primary
    }
    ToolCardScaffold(
        modifier = modifier,
        container = container,
        content = content,
        icon = icon,
        label = label,
        description = description,
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
private fun ActionToolCard(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val container = if (enabled)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    else
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val content = if (enabled)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    ToolCardScaffold(
        modifier = modifier,
        container = container,
        content = content,
        icon = icon,
        label = label,
        description = description,
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
private fun ToolCardScaffold(
    modifier: Modifier,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    icon: ImageVector,
    label: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current

    Surface(
        shape = tnShapes.lg,
        color = container,
        contentColor = content,
        modifier = modifier

            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = dimens.spacingMd,
                vertical = dimens.spacingSm,
            ),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(dimens.spacingXxs),
        ) {
            Box(
                modifier = Modifier.size(dimens.iconLg),
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(dimens.iconMd),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = content.copy(alpha = 0.75f),
                maxLines = 1,
            )
        }
    }
}
