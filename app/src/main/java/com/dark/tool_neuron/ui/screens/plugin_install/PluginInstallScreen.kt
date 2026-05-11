package com.dark.tool_neuron.ui.screens.plugin_install

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
    val installState by vm.installState.collectAsStateWithLifecycle()
    val activePlugin by vm.activePlugin.collectAsStateWithLifecycle()
    val openPlugins by vm.openPlugins.collectAsStateWithLifecycle()

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val dimens = LocalDimens.current

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
                        text = "Installed (${installed.size})",
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
                        text = "Install",
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
                0 -> InstalledTab(
                    installed = installed,
                    activeId = activePlugin,
                    runningIds = openPlugins.toSet(),
                    onOpen = vm::openPlugin,
                    onStop = vm::stopPlugin,
                    onUninstall = vm::uninstall,
                )
                1 -> InstallTab(
                    state = installState,
                    onPickFile = vm::installFromUri,
                    onOpen = vm::openPlugin,
                    onDismiss = vm::dismissInstallState,
                )
            }
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
            title = "No plugins yet",
            body = "Install a plugin from a .zip bundle to get started.",
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
private fun InstallTab(
    state: PluginInstallViewModel.InstallState,
    onPickFile: (android.net.Uri) -> Unit,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val dimens = LocalDimens.current
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) onPickFile(uri) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = dimens.screenPadding, vertical = dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingMd),
    ) {
        DropZoneCard(
            onPick = {
                picker.launch(
                    arrayOf("application/zip", "application/octet-stream", "*/*")
                )
            },
            enabled = state !is PluginInstallViewModel.InstallState.Working,
        )

        AnimatedContent(
            targetState = state,
            transitionSpec = {
                (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.95f))
                    .togetherWith(fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 1.02f))
            },
            label = "install-state",
        ) { current ->
            when (current) {
                is PluginInstallViewModel.InstallState.Idle -> Spacer(Modifier.height(0.dp))
                is PluginInstallViewModel.InstallState.Working -> WorkingCard()
                is PluginInstallViewModel.InstallState.Success -> SuccessCard(
                    name = current.name,
                    onOpen = { onOpen(current.pluginId); onDismiss() },
                    onDismiss = onDismiss,
                )
                is PluginInstallViewModel.InstallState.Failed -> FailedCard(
                    reason = current.reason,
                    onDismiss = onDismiss,
                )
            }
        }

        Spacer(Modifier.weight(1f))
        InfoFootnote()
    }
}

@Composable
private fun DropZoneCard(onPick: () -> Unit, enabled: Boolean) {
    val dimens = LocalDimens.current
    StandardCard(
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        onClick = if (enabled) onPick else null,
        content = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = dimens.spacingMd),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
            ) {
                Box(
                    modifier = Modifier
                        .size(dimens.iconLg + 24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = TnIcons.Package,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(dimens.iconMd + 4.dp),
                    )
                }
                Text(
                    text = "Install from local storage",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                CaptionText(text = "Tap to pick a plugin bundle (.zip).")
                Spacer(Modifier.height(dimens.spacingXs))
                ActionButton(
                    onClickListener = onPick,
                    icon = TnIcons.HardDrive,
                    contentDescription = "Choose file",
                    enabled = enabled,
                )
            }
        },
    )
}

@Composable
private fun WorkingCard() {
    val dimens = LocalDimens.current
    StandardCard(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        content = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(dimens.iconMd),
                    strokeWidth = 2.5.dp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Installing plugin…",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    CaptionText(text = "Extracting bundle and verifying manifest")
                }
            }
        },
    )
}

@Composable
private fun SuccessCard(name: String, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val dimens = LocalDimens.current
    StandardCard(
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        content = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
            ) {
                Icon(
                    imageVector = TnIcons.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Installed",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    BodyLabel(
                        text = name,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm)) {
                ActionTextButton(
                    onClickListener = onOpen,
                    icon = TnIcons.Rocket,
                    text = "Open plugin",
                )
                ActionTextButton(
                    onClickListener = onDismiss,
                    icon = TnIcons.Check,
                    text = "Dismiss",
                )
            }
        },
    )
}

@Composable
private fun FailedCard(reason: String, onDismiss: () -> Unit) {
    val dimens = LocalDimens.current
    StandardCard(
        containerColor = MaterialTheme.colorScheme.errorContainer,
        content = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
            ) {
                Icon(
                    imageVector = TnIcons.AlertTriangle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = "Install failed",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            BodyLabel(
                text = reason,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            ActionTextButton(
                onClickListener = onDismiss,
                icon = TnIcons.Check,
                text = "Dismiss",
            )
        },
    )
}

@Composable
private fun InfoFootnote() {
    val dimens = LocalDimens.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
    ) {
        Icon(
            imageVector = TnIcons.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(dimens.iconSm),
        )
        CaptionText(
            text = "Plugins run inside this app. Permissions you grant are limited by what each plugin declares.",
        )
    }
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
