package com.dark.tool_neuron.ui.screens.dev_notes

import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes

internal const val APP_VERSION = "1.0.0"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevNotesTopBar() {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current

    CenterAlignedTopAppBar(
        title = {
            Text(
                text = "Developer Notes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        navigationIcon = {
            Icon(TnIcons.Leaf, "Screen-Icon", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = dimens.screenPadding))
        },
        actions = {
            Surface(
                shape = tnShapes.chip,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier.padding(end = dimens.screenPadding)
            ) {
                Text(
                    text = "v$APP_VERSION",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = dimens.spacingSm, vertical = dimens.spacingXxs + 1.dp)
                )
            }
        }
    )
}
