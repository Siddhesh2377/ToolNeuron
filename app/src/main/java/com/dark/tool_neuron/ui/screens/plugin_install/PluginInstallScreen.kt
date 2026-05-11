package com.dark.tool_neuron.ui.screens.plugin_install

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.plugin_api.PluginCapability
import com.dark.plugin_exc.InstalledPlugin
import com.dark.plugin_exc.catalog.CatalogEntry
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.ActionTextButton
import com.dark.tool_neuron.ui.components.BodyLabel
import com.dark.tool_neuron.ui.components.CaptionText
import com.dark.tool_neuron.ui.components.InfoBadge
import com.dark.tool_neuron.ui.components.StandardCard
import com.dark.tool_neuron.ui.components.StatusBadge
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.viewmodel.PluginInstallViewModel
import kotlinx.coroutines.launch

@Composable
fun PluginInstallScreen(
    innerPadding: PaddingValues,
    vm: PluginInstallViewModel = hiltViewModel(),
) {
    val installed by vm.installed.collectAsStateWithLifecycle()
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val storeRows by vm.storeRows.collectAsStateWithLifecycle()
    val activePlugin by vm.activePlugin.collectAsStateWithLifecycle()
    val openPlugins by vm.openPlugins.collectAsStateWithLifecycle()

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { vm.refreshCatalog() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        PrimaryTabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Tab(
                selected = pagerState.currentPage == 0,
                onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                text = {
                    Text(
                        text = "Store",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
            )
            Tab(
                selected = pagerState.currentPage == 1,
                onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                text = {
                    Text(
                        text = "Installed (${installed.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.Top,
        ) { page ->
            when (page) {
                0 -> StoreTab(
                    catalog = catalog,
                    rows = storeRows,
                    onInstall = vm::installFromCatalog,
                    onUpdate = vm::installFromCatalog,
                    onOpen = vm::openPlugin,
                    onRetry = vm::refreshCatalog,
                    onDismissError = vm::dismissError,
                )
                1 -> InstalledTab(
                    installed = installed,
                    activeId = activePlugin,
                    runningIds = openPlugins.toSet(),
                    onOpen = vm::openPlugin,
                    onStop = vm::stopPlugin,
                    onUninstall = vm::uninstall,
                )
            }
        }
    }
}

@Composable
private fun StoreTab(
    catalog: PluginInstallViewModel.CatalogState,
    rows: List<PluginInstallViewModel.StoreRow>,
    onInstall: (CatalogEntry) -> Unit,
    onUpdate: (CatalogEntry) -> Unit,
    onOpen: (String) -> Unit,
    onRetry: () -> Unit,
    onDismissError: (String) -> Unit,
) {
    val dimens = LocalDimens.current
    when (val c = catalog) {
        is PluginInstallViewModel.CatalogState.Loading -> CatalogLoadingState()
        is PluginInstallViewModel.CatalogState.Failed -> CatalogErrorState(c.reason, onRetry)
        is PluginInstallViewModel.CatalogState.Ready -> {
            if (rows.isEmpty()) {
                EmptyState(
                    icon = TnIcons.Puzzle,
                    title = "No plugins yet",
                    body = "The catalog returned no entries.",
                )
                return
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = dimens.screenPadding,
                    vertical = dimens.spacingMd,
                ),
                verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
            ) {
                items(rows, key = { it.entry.id }) { row ->
                    StoreCard(
                        row = row,
                        onInstall = { onInstall(row.entry) },
                        onUpdate = { onUpdate(row.entry) },
                        onOpen = { onOpen(row.entry.id) },
                        onDismissError = { onDismissError(row.entry.id) },
                    )
                }
                item {
                    StoreFootnote(updatedAt = c.catalog.updatedAt, count = c.catalog.plugins.size)
                }
                item { Spacer(Modifier.height(dimens.spacingXxl)) }
            }
        }
    }
}

@Composable
private fun StoreCard(
    row: PluginInstallViewModel.StoreRow,
    onInstall: () -> Unit,
    onUpdate: () -> Unit,
    onOpen: () -> Unit,
    onDismissError: () -> Unit,
) {
    val dimens = LocalDimens.current
    val e = row.entry
    StandardCard(
        containerColor = when (row.phase) {
            is PluginInstallViewModel.Phase.Installed ->
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.18f)
            is PluginInstallViewModel.Phase.UpdateAvailable ->
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            is PluginInstallViewModel.Phase.Failed ->
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else ->
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        },
        content = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
            ) {
                PluginInitialAvatar(initial = e.initial, active = false)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = e.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    CaptionText(text = "v${e.version} · ${e.author} · ${humanSize(e.size)}")
                }
                PhaseBadge(row.phase)
            }

            if (e.description.isNotBlank()) {
                BodyLabel(
                    text = e.description,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    maxLines = 3,
                )
            }

            CapabilityChips(capabilities = e.capabilities, hasNative = e.hasNativeCode)

            PhaseActionRow(
                phase = row.phase,
                onInstall = onInstall,
                onUpdate = onUpdate,
                onOpen = onOpen,
                onDismissError = onDismissError,
            )
        },
    )
}

@Composable
private fun PhaseBadge(phase: PluginInstallViewModel.Phase) {
    when (phase) {
        is PluginInstallViewModel.Phase.Installed -> StatusBadge(text = "Installed", isActive = true)
        is PluginInstallViewModel.Phase.UpdateAvailable -> InfoBadge(
            text = "Update",
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        )
        else -> Unit
    }
}

@Composable
private fun PhaseActionRow(
    phase: PluginInstallViewModel.Phase,
    onInstall: () -> Unit,
    onUpdate: () -> Unit,
    onOpen: () -> Unit,
    onDismissError: () -> Unit,
) {
    val dimens = LocalDimens.current
    AnimatedContent(
        targetState = phase,
        transitionSpec = {
            (fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.97f))
                .togetherWith(fadeOut(tween(140)))
        },
        label = "phase-action",
    ) { current ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (current) {
                is PluginInstallViewModel.Phase.NotInstalled -> {
                    ActionTextButton(
                        onClickListener = onInstall,
                        icon = TnIcons.Download,
                        text = "Install",
                    )
                }
                is PluginInstallViewModel.Phase.Installed -> {
                    ActionTextButton(
                        onClickListener = onOpen,
                        icon = TnIcons.Rocket,
                        text = "Open",
                    )
                }
                is PluginInstallViewModel.Phase.UpdateAvailable -> {
                    ActionTextButton(
                        onClickListener = onUpdate,
                        icon = TnIcons.Download,
                        text = "Update from v${current.fromVersion}",
                    )
                    Spacer(Modifier.weight(1f))
                    ActionTextButton(
                        onClickListener = onOpen,
                        icon = TnIcons.Rocket,
                        text = "Open",
                    )
                }
                is PluginInstallViewModel.Phase.Downloading -> {
                    val frac = if (current.total > 0)
                        (current.bytes.toFloat() / current.total.toFloat()).coerceIn(0f, 1f)
                    else 0f
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(dimens.spacingXxs),
                    ) {
                        LinearProgressIndicator(
                            progress = { frac },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        CaptionText(
                            text = "Downloading · ${humanSize(current.bytes)} / ${humanSize(current.total)}",
                        )
                    }
                }
                is PluginInstallViewModel.Phase.Installing -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(dimens.iconSm),
                        strokeWidth = 2.dp,
                    )
                    CaptionText(text = "Verifying and extracting bundle")
                }
                is PluginInstallViewModel.Phase.Failed -> {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Install failed",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold,
                        )
                        CaptionText(text = current.reason)
                    }
                    ActionTextButton(
                        onClickListener = onInstall,
                        icon = TnIcons.Download,
                        text = "Retry",
                    )
                    ActionTextButton(
                        onClickListener = onDismissError,
                        icon = TnIcons.Check,
                        text = "Dismiss",
                    )
                }
            }
        }
    }
}

@Composable
private fun StoreFootnote(updatedAt: String, count: Int) {
    val dimens = LocalDimens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
    ) {
        Icon(
            imageVector = TnIcons.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(dimens.iconSm),
        )
        CaptionText(text = "$count plugin(s) in catalog · catalog updated $updatedAt")
    }
}

@Composable
private fun CatalogLoadingState() {
    val dimens = LocalDimens.current
    Box(
        modifier = Modifier.fillMaxSize().padding(dimens.spacingXxl),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            CircularProgressIndicator()
            CaptionText(text = "Fetching plugin catalog…")
        }
    }
}

@Composable
private fun CatalogErrorState(reason: String, onRetry: () -> Unit) {
    val dimens = LocalDimens.current
    Box(
        modifier = Modifier.fillMaxSize().padding(dimens.spacingXxl),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            Icon(
                imageVector = TnIcons.AlertTriangle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(dimens.iconLg),
            )
            Text(
                text = "Could not load catalog",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            BodyLabel(text = reason, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ActionTextButton(
                onClickListener = onRetry,
                icon = TnIcons.Rocket,
                text = "Retry",
            )
        }
    }
}

@Composable
private fun InstalledTab(
    installed: List<InstalledPlugin>,
    activeId: String?,
    runningIds: Set<String>,
    onOpen: (String) -> Unit,
    onStop: (String) -> Unit,
    onUninstall: (String) -> Unit,
) {
    val dimens = LocalDimens.current
    if (installed.isEmpty()) {
        EmptyState(
            icon = TnIcons.Puzzle,
            title = "No plugins installed",
            body = "Browse the Store tab to add a plugin.",
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = dimens.screenPadding,
            vertical = dimens.spacingMd,
        ),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
    ) {
        items(installed, key = { it.manifest.id }) { plugin ->
            val id = plugin.manifest.id
            PluginCard(
                installed = plugin,
                isActive = id == activeId,
                isRunning = id in runningIds,
                onOpen = { onOpen(id) },
                onStop = { onStop(id) },
                onUninstall = { onUninstall(id) },
            )
        }
        item { Spacer(Modifier.height(dimens.spacingXxl)) }
    }
}

@Composable
private fun PluginCard(
    installed: InstalledPlugin,
    isActive: Boolean,
    isRunning: Boolean,
    onOpen: () -> Unit,
    onStop: () -> Unit,
    onUninstall: () -> Unit,
) {
    val dimens = LocalDimens.current
    val m = installed.manifest
    val containerColor = when {
        isActive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        isRunning -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.18f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }

    StandardCard(
        containerColor = containerColor,
        content = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
            ) {
                PluginInitialAvatar(initial = m.initial, active = isActive)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = m.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    CaptionText(text = "v${m.version} · ${m.author}")
                }
                if (isRunning) {
                    val label = if (isActive) "Active" else "Running"
                    StatusBadge(text = label, isActive = true)
                }
            }

            if (m.description.isNotBlank()) {
                BodyLabel(
                    text = m.description,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    maxLines = 3,
                )
            }

            CapabilityChips(capabilities = m.capabilities, hasNative = m.hasNativeCode)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionButton(
                    onClickListener = onOpen,
                    icon = TnIcons.Rocket,
                    contentDescription = if (isRunning) "Resume" else "Open",
                )
                if (isRunning) {
                    ActionButton(
                        onClickListener = onStop,
                        icon = TnIcons.PlayerStop,
                        contentDescription = "Stop",
                        colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    )
                }
                Spacer(Modifier.weight(1f))
                ActionTextButton(
                    onClickListener = onUninstall,
                    icon = TnIcons.Trash,
                    text = "Uninstall",
                )
            }
        },
    )
}

@Composable
private fun PluginInitialAvatar(initial: String, active: Boolean) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    val letter = initial.take(1).ifBlank { "·" }.uppercase()
    val bg = if (active) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (active) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .size(dimens.iconLg + 8.dp)
            .clip(tnShapes.cardSmall)
            .background(bg, tnShapes.cardSmall),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = fg,
        )
    }
}

@Composable
private fun CapabilityChips(capabilities: List<PluginCapability>, hasNative: Boolean) {
    if (capabilities.isEmpty() && !hasNative) return
    val dimens = LocalDimens.current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingXs),
    ) {
        if (hasNative) {
            InfoBadge(
                text = "native",
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
        capabilities.forEach { cap ->
            InfoBadge(
                text = capabilityLabel(cap),
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

private fun capabilityLabel(cap: PluginCapability): String = when (cap) {
    PluginCapability.HXS_READ -> "data·read"
    PluginCapability.HXS_WRITE -> "data·write"
    PluginCapability.INTERNET -> "internet"
    PluginCapability.AI_ONNX -> "ai"
    PluginCapability.CAMERA -> "camera"
    PluginCapability.MIC -> "mic"
    PluginCapability.FILESYSTEM_READ -> "fs·read"
    PluginCapability.FILESYSTEM_WRITE -> "fs·write"
    PluginCapability.NOTIFICATIONS -> "notif"
    PluginCapability.CLIPBOARD -> "clipboard"
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, body: String) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(dimens.spacingXxl),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            Box(
                modifier = Modifier
                    .size(dimens.iconLg + 40.dp)
                    .clip(tnShapes.card)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        tnShapes.card,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(dimens.iconLg),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            BodyLabel(
                text = body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun humanSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f kB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}
