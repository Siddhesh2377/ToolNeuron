package com.dark.tool_neuron.plugins.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.models.plugins.ScrapedContent
import com.dark.tool_neuron.plugins.viewmodel.WebSearchViewModel
import com.dark.tool_neuron.ui.theme.ManropeFontFamily
import com.dark.tool_neuron.ui.theme.maple
import com.dark.tool_neuron.ui.theme.rDp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * UI component for Web Scraping Tool
 */
@Composable
fun WebScrapingUI(
    viewModel: WebSearchViewModel,
    modifier: Modifier = Modifier
) {
    val state = viewModel.webScrapingState.value
    val scrapedContent = viewModel.scrapedContent.value

    ToolCard(
        title = "Web Scraping",
        icon = Icons.Default.Language,
        state = state,
        modifier = modifier
    ) {
        scrapedContent?.let { content ->
            ScrapedContentDisplay(content = content)
        }
    }
}

@Composable
private fun ScrapedContentDisplay(content: ScrapedContent) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
        verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
    ) {
        // Metadata
        ContentMetadata(content = content)

        // Divider
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(rDp(1.dp))
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        )

        // Content preview/full
        ContentPreview(
            content = content,
            isExpanded = isExpanded,
            onExpandToggle = { isExpanded = !isExpanded }
        )
    }
}

@Composable
private fun ContentMetadata(content: ScrapedContent) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(rDp(8.dp))
    ) {
        // URL
        MetadataRow(
            icon = Icons.Default.Language,
            label = "URL",
            value = content.url,
            valueStyle = MetadataValueStyle.Link
        )

        // Title
        if (content.title.isNotEmpty()) {
            MetadataRow(
                icon = Icons.Default.TextFields,
                label = "Title",
                value = content.title,
                valueStyle = MetadataValueStyle.Normal
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(rDp(16.dp))
        ) {
            // Content length
            MetadataChip(
                icon = Icons.Default.TextFields,
                label = "${content.contentLength} chars"
            )

            // Fetch time
            val timeFormatted = remember(content.fetchTime) {
                SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    .format(Date(content.fetchTime))
            }
            MetadataChip(
                icon = Icons.Default.Schedule,
                label = timeFormatted
            )
        }

        // Selector metadata if present
        content.metadata["selector"]?.let { selector ->
            if (selector != "none") {
                MetadataRow(
                    icon = Icons.Default.ContentCopy,
                    label = "Selector",
                    value = selector,
                    valueStyle = MetadataValueStyle.Code
                )
            }
        }
    }
}

@Composable
private fun MetadataRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    valueStyle: MetadataValueStyle
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(rDp(8.dp)),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(rDp(16.dp))
                .padding(top = rDp(2.dp))
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(rDp(2.dp))
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ManropeFontFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = when (valueStyle) {
                    MetadataValueStyle.Code, MetadataValueStyle.Link -> maple
                    MetadataValueStyle.Normal -> ManropeFontFamily
                },
                color = when (valueStyle) {
                    MetadataValueStyle.Link -> MaterialTheme.colorScheme.tertiary
                    MetadataValueStyle.Code -> MaterialTheme.colorScheme.secondary
                    MetadataValueStyle.Normal -> MaterialTheme.colorScheme.onSurface
                },
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MetadataChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(rDp(6.dp)))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
            .padding(horizontal = rDp(8.dp), vertical = rDp(4.dp)),
        horizontalArrangement = Arrangement.spacedBy(rDp(4.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(rDp(14.dp))
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = maple,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ContentPreview(
    content: ScrapedContent,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "rotation"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(rDp(8.dp))
    ) {
        // Header with expand button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(rDp(8.dp)))
                .clickable { onExpandToggle() }
                .padding(rDp(8.dp)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isExpanded) "Content" else "Content Preview",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ManropeFontFamily,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )

            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(rDp(20.dp))
                    .rotate(rotation)
            )
        }

        // Content text
        AnimatedVisibility(
            visible = true,
            enter = slideInVertically(
                initialOffsetY = { -it / 2 },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(),
            exit = fadeOut()
        ) {
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(rDp(8.dp)))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                    .then(
                        if (isExpanded) {
                            Modifier
                                .height(rDp(200.dp))
                                .verticalScroll(scrollState)
                        } else {
                            Modifier
                        }
                    )
                    .padding(rDp(12.dp))
            ) {
                Text(
                    text = if (isExpanded) content.content else content.content.take(200) + "...",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = maple,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = MaterialTheme.typography.bodySmall.fontSize * 1.5,
                    maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private enum class MetadataValueStyle {
    Normal,
    Link,
    Code
}
