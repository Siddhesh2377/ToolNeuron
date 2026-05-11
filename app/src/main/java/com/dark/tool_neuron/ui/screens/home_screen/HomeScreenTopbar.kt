package com.dark.tool_neuron.ui.screens.home_screen

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    onGuideClick: () -> Unit = {},
    onModelManagerClick: () -> Unit = {},
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
            ActionButton(
                onClickListener = onModelManagerClick,
                icon = TnIcons.Sliders,
                contentDescription = "Model settings",
                modifier = Modifier.padding(end = dimens.spacingSm)
            )
            ActionButton(
                onClickListener = onGuideClick,
                icon = TnIcons.BookOpen,
                contentDescription = "Guide",
                modifier = Modifier.padding(end = dimens.spacingSm)
            )
            StoreActionButton(
                downloadProgress = downloadProgress,
                onClick = onStoreClick,
                modifier = Modifier.padding(end = dimens.screenPadding),
            )
        }
    )
}

@Composable
private fun StoreActionButton(
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

    Box(modifier = modifier.size(dimens.actionIconSize), contentAlignment = Alignment.Center) {
        Crossfade(
            targetState = downloading,
            animationSpec = tween(durationMillis = 220),
            label = "storeButtonState",
        ) { isDownloading ->
            if (isDownloading) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(dimens.actionIconSize)) {
                    when (lastShown) {
                        is DownloadProgress.Determinate -> CircularProgressIndicator(
                            progress = { animatedFraction },
                            modifier = Modifier.size(dimens.actionIconSize),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        )
                        DownloadProgress.Indeterminate -> CircularProgressIndicator(
                            modifier = Modifier.size(dimens.actionIconSize),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        )
                    }
                    FilledIconButton(
                        onClick = onClick,
                        shape = tnShapes.full,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier.size(dimens.actionIconSize - 8.dp),
                    ) {
                        Icon(
                            imageVector = TnIcons.Download,
                            contentDescription = "Store",
                            modifier = Modifier.padding(dimens.actionIconPadding - 2.dp),
                        )
                    }
                }
            } else {
                ActionButton(
                    onClickListener = onClick,
                    icon = TnIcons.Download,
                    contentDescription = "Store",
                )
            }
        }
    }
}
