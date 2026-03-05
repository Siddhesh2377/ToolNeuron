package com.dark.tool_neuron.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.global.Standards
import com.dark.tool_neuron.worker.ScoredVaultContent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MemoryTimelineView(
    entries: List<ScoredVaultContent>,
    modifier: Modifier = Modifier
) {
    if (entries.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(Standards.SpacingLg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No memories yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
        return
    }

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        itemsIndexed(entries) { index, entry ->
            val isLast = index == entries.lastIndex

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                // Timeline column: dot + vertical line
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(24.dp)
                ) {
                    // Dot
                    Box(
                        modifier = Modifier
                            .size(Standards.TimelineNodeSize)
                            .background(
                                MaterialTheme.colorScheme.secondary,
                                CircleShape
                            )
                    )

                    // Vertical line (not after last item)
                    if (!isLast) {
                        Box(
                            modifier = Modifier
                                .width(Standards.TimelineLineWidth)
                                .fillMaxHeight()
                                .background(
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(Standards.SpacingSm))

                // Content card
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = Standards.SpacingMd)
                ) {
                    // Timestamp
                    Text(
                        text = dateFormat.format(Date(entry.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )

                    Spacer(modifier = Modifier.height(Standards.SpacingXs))

                    // Content preview in a card
                    StandardCard(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = entry.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )

                        val category = entry.category
                        if (category != null) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Standards.SpacingXs)
                            ) {
                                InfoBadge(
                                    text = category,
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                                    contentColor = MaterialTheme.colorScheme.secondary
                                )
                                val scorePercent = (entry.score * 100).toInt()
                                InfoBadge(
                                    text = "$scorePercent%",
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                                    contentColor = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
