package com.dark.tool_neuron.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.R
import com.dark.tool_neuron.global.Standards
import com.dark.tool_neuron.ui.theme.rDp
import com.dark.tool_neuron.worker.ScoredVaultContent

@Composable
fun MemoryResultsDisplay(
    results: List<ScoredVaultContent>,
    modifier: Modifier = Modifier
) {
    if (results.isEmpty()) return

    var isExpanded by remember { mutableStateOf(false) }

    StandardCard(
        modifier = modifier,
        iconRes = R.drawable.memory_vault,
        title = "Memory Vault Results",
        description = "${results.size} memories found",
        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
        onClick = { isExpanded = !isExpanded },
        trailing = {
            InfoBadge(
                text = "${results.size}",
                containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                contentColor = MaterialTheme.colorScheme.secondary
            )
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    ) {
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingSm))
            ) {
                results.forEach { result ->
                    MemoryEntryCard(result = result)
                }
            }
        }
    }
}

@Composable
private fun MemoryEntryCard(
    result: ScoredVaultContent,
    modifier: Modifier = Modifier
) {
    var isRevealed by remember { mutableStateOf(false) }
    val scorePercent = (result.score * 100).toInt()

    StandardCard(
        modifier = modifier.clickable { isRevealed = !isRevealed },
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        trailing = {
            InfoBadge(
                text = "$scorePercent%",
                containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                contentColor = MaterialTheme.colorScheme.tertiary
            )
        }
    ) {
        Text(
            text = result.content,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (isRevealed) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            modifier = if (!isRevealed) Modifier.blur(rDp(6.dp)) else Modifier
        )

        if (result.category != null || result.tags.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = rDp(Standards.SpacingXs)),
                horizontalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingXs))
            ) {
                result.category?.let {
                    InfoBadge(
                        text = it,
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        contentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
