package com.dark.neuroverse.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.neuroverse.R
import com.dark.neuroverse.ui.theme.rDP
import com.dark.neuroverse.viewModel.chatViewModel.ChatScreenViewModel

/**
 * Micro dialog for choosing a model to regenerate a single assistant message.
 * Clean, compact, and unobtrusive. One‑tap picks model & triggers regeneration.
 */
@Composable
fun RegenerateModelPickerDialog(
    viewModel: ChatScreenViewModel,
    messageId: String,
    onDismiss: () -> Unit,
) {
    val models by viewModel.modelList.collectAsStateWithLifecycle()
    val selected by viewModel.selectedModel.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {}, // one‑tap rows; no explicit confirm needed
        title = {
            Text(
                text = "Regenerate with",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
        },
        text = {
            Surface(tonalElevation = 0.dp, color = MaterialTheme.colorScheme.surface) {
                LazyColumn(
                    modifier = Modifier
                        .heightIn(min = rDP(80.dp), max = rDP(360.dp))
                ) {
                    // Current model first
                    val sorted = listOf(selected) + models.filter { it.modeName != selected.modeName }
                    items(sorted, key = { it.modeName }) { model ->
                        ModelRow(
                            name = shortModelLabel(model.modeName),
                            isCurrent = model.modeName == selected.modeName,
                            onClick = {
                                // Fire & close
                                viewModel.regenerateResponse(model, messageId)
                                onDismiss()
                            }
                        )
                        HorizontalDivider(
                            Modifier,
                            DividerDefaults.Thickness,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        },
        shape = RoundedCornerShape(rDP(16.dp)),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    )
}

@Composable
private fun ModelRow(name: String, isCurrent: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = rDP(8.dp), vertical = rDP(10.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(rDP(12.dp))
    ) {
        // Tiny avatar dot for visual anchor
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(
                    if (isCurrent) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.secondary
                )
                .align(Alignment.CenterVertically)
                .padding(rDP(6.dp))
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (isCurrent) {
                Text(
                    text = "current",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Icon(
            painter = painterResource(R.drawable.regen),
            contentDescription = null,
            tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun shortModelLabel(raw: String): String {
    // Squash file-y suffixes & quant tags, keep punchy ID
    var s = raw
    s = s.replace(".gguf", "", ignoreCase = true)
        .replace(".bin", "", ignoreCase = true)
        .replace(Regex("-Q\n?\n?\n?\\d.*$"), "") // defensive
        .replace(Regex("-Q\\d_[A-Z_]+$"), "")
        .replace(Regex("(?i)(fp16|int\n?8|int\n?4|Q\\d_[A-Z_]+)"), "")
        .replace(Regex("[_-]{2,}"), "-")
        .trim('-','_',' ')
    // keep it readable; collapse extra version chunks
    if (s.length > 28) s = s.take(24) + "…"
    return s
}


