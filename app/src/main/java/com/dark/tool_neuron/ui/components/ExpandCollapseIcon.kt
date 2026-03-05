package com.dark.tool_neuron.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.ui.icons.TnIcons

// ── Expand/Collapse Icon ──

@Composable
fun ExpandCollapseIcon(
    isExpanded: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    contentDescription: String? = if (isExpanded) "Collapse" else "Expand"
) {
    Icon(
        imageVector = if (isExpanded) TnIcons.ChevronUp else TnIcons.ChevronDown,
        contentDescription = contentDescription,
        modifier = modifier.then(Modifier.size(size)),
        tint = tint
    )
}
