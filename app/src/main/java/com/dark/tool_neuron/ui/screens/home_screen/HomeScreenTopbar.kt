package com.dark.tool_neuron.ui.screens.home_screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.model.DownloadProgress
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.action_window.ActionWindowPill
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.ui.util.LocalIsExpandedLayout
import com.dark.tool_neuron.viewmodel.home_vm.PillState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenTopbar(
    pillState: PillState,
    expanded: Boolean,
    downloadProgress: DownloadProgress? = null,
    onToggle: () -> Unit,
    onMenuClick: () -> Unit = {},
    onStoreClick: () -> Unit = {},
) {
    val dimens = LocalDimens.current

    CenterAlignedTopAppBar(
        title = {
            ActionWindowPill(
                state = pillState,
                expanded = expanded,
                onToggle = onToggle,
            )
        },
        navigationIcon = {
            if (!LocalIsExpandedLayout.current) {
                ActionButton(
                    onClickListener = onMenuClick,
                    icon = TnIcons.Menu,
                    contentDescription = "Menu",
                    modifier = Modifier.padding(start = dimens.screenPadding)
                )
            }
        },
        actions = {
            StorePill(
                downloadProgress = downloadProgress,
                onClick = onStoreClick,
                modifier = Modifier.padding(end = dimens.screenPadding),
            )
        }
    )
}

@Composable
private fun StorePill(
    downloadProgress: DownloadProgress?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    val downloading = downloadProgress != null

    var lastShown by remember { mutableStateOf<DownloadProgress>(DownloadProgress.Indeterminate) }
    LaunchedEffect(downloadProgress) {
        if (downloadProgress != null) lastShown = downloadProgress
    }

    val targetFraction = (lastShown as? DownloadProgress.Determinate)?.fraction ?: 0f
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(durationMillis = 300),
        label = "downloadFraction",
    )

    Surface(
        shape = tnShapes.full,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        contentColor = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .height(dimens.actionIconSize)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Row(
            modifier = Modifier.padding(
                start = dimens.spacingSm,
                end = dimens.spacingMd,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
        ) {
            Box(
                modifier = Modifier.size(dimens.iconMd),
                contentAlignment = Alignment.Center,
            ) {
                if (downloading) {
                    when (lastShown) {
                        is DownloadProgress.Determinate -> CircularProgressIndicator(
                            progress = { animatedFraction },
                            modifier = Modifier.size(dimens.iconMd),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        )
                        DownloadProgress.Indeterminate -> CircularProgressIndicator(
                            modifier = Modifier.size(dimens.iconMd),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        )
                    }
                }else{
                    Icon(
                        imageVector = TnIcons.Download,
                        contentDescription = "Store",
                        modifier = Modifier.size(dimens.iconMd),
                    )
                }
            }
            Text(
                text = "Store",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
