package com.dark.tool_neuron.ui.screens.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.view.ViewGroup
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.terminal.BootstrapState
import com.dark.tool_neuron.terminal.ExtraKeysState
import com.dark.tool_neuron.terminal.TerminalViewClientImpl
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.viewmodel.terminal_vm.TerminalViewModel
import com.termux.view.TerminalView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    onClose: () -> Unit,
    viewModel: TerminalViewModel = hiltViewModel(),
) {
    val dimens = LocalDimens.current
    val bootstrap by viewModel.bootstrapState.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val activeIndex by viewModel.activeSessionIndex.collectAsStateWithLifecycle()
    var installed by remember { mutableStateOf(viewModel.installer.isInstalled()) }

    LaunchedEffect(bootstrap) {
        if (bootstrap is BootstrapState.Done) {
            installed = viewModel.installer.isInstalled()
        }
    }

    LaunchedEffect(installed) {
        if (installed) viewModel.ensureFirstSession()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Terminal",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    ActionButton(
                        onClickListener = onClose,
                        icon = TnIcons.ArrowLeft,
                        contentDescription = "Back",
                        modifier = Modifier.padding(start = dimens.screenPadding),
                    )
                },
                actions = {
                    if (installed) {
                        ActionButton(
                            onClickListener = viewModel::newSession,
                            icon = TnIcons.Plus,
                            contentDescription = "New session",
                            modifier = Modifier.padding(end = dimens.screenPadding),
                        )
                    }
                },
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .consumeWindowInsets(inner),
        ) {
            when {
                !installed || bootstrap is BootstrapState.Running ->
                    TerminalSetupBody(
                        state = bootstrap,
                        onInstall = viewModel::install,
                        modifier = Modifier.fillMaxSize(),
                    )
                sessions.isEmpty() ->
                    EmptyBody(
                        onNewSession = viewModel::newSession,
                        modifier = Modifier.fillMaxSize(),
                    )
                else ->
                    TerminalBody(
                        viewModel = viewModel,
                        sessions = sessions,
                        activeIndex = activeIndex,
                        modifier = Modifier.fillMaxSize(),
                    )
            }
        }
    }
}

@Composable
private fun TerminalSetupBody(
    state: BootstrapState,
    onInstall: () -> Unit,
    modifier: Modifier,
) {
    val dimens = LocalDimens.current
    LaunchedEffect(Unit) {
        if (state is BootstrapState.Idle) onInstall()
    }
    Column(
        modifier = modifier.padding(dimens.screenPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = TnIcons.Download,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "First-time setup",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = dimens.spacingLg),
        )
        Text(
            text = "Extracting the shell environment into your device. Runs once.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = dimens.spacingSm),
        )
        when (state) {
            is BootstrapState.Idle -> {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = dimens.spacingLg),
                )
                Text(
                    text = "starting…",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = dimens.spacingSm),
                )
            }
            is BootstrapState.Running -> {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = dimens.spacingLg),
                )
                Text(
                    text = state.step,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = dimens.spacingSm),
                )
            }
            is BootstrapState.Done -> {
                Text(
                    text = "ready.",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = dimens.spacingLg),
                )
            }
            is BootstrapState.Failed -> {
                Text(
                    text = "failed: ${state.message}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = dimens.spacingLg),
                )
                Text(
                    text = "tap retry to try again",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clickable(onClick = onInstall)
                        .padding(top = dimens.spacingSm),
                )
            }
        }
    }
}

@Composable
private fun EmptyBody(onNewSession: () -> Unit, modifier: Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = "tap + to start a session",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable(onClick = onNewSession),
        )
    }
}

@Composable
private fun TerminalBody(
    viewModel: TerminalViewModel,
    sessions: List<com.termux.terminal.TerminalSession>,
    activeIndex: Int?,
    modifier: Modifier,
) {
    val index = activeIndex ?: 0
    val session = sessions.getOrNull(index) ?: return
    val extraKeys = remember { ExtraKeysState() }
    val titlesTick by viewModel.titlesTick.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(modifier = modifier.imePadding()) {
        if (sessions.size > 1) {
            SessionTabs(
                sessions = sessions,
                activeIndex = index,
                onSelect = viewModel::selectSession,
                onClose = viewModel::closeSession,
                titlesTick = titlesTick,
            )
        }

        TerminalCanvas(
            session = session,
            extraKeys = extraKeys,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        ExtraKeysRow(
            state = extraKeys,
            onKeyTap = { s -> viewModel.writeToActive(s) },
            onToggleCtrl = extraKeys::toggleCtrl,
            onToggleAlt = extraKeys::toggleAlt,
            onToggleShift = extraKeys::toggleShift,
            onToggleFn = extraKeys::toggleFn,
            onPaste = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val text = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                if (!text.isNullOrEmpty()) viewModel.writeToActive(text)
            },
            onCopyLastOutput = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val emu = session.emulator ?: return@ExtraKeysRow
                val text = emu.screen.transcriptText
                cm?.setPrimaryClip(ClipData.newPlainText("terminal", text))
            },
        )
    }
}

@Composable
private fun TerminalCanvas(
    session: com.termux.terminal.TerminalSession,
    extraKeys: ExtraKeysState,
    modifier: Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val tv = TerminalView(ctx, null)
            tv.setTextSize(20)
            tv.setTypeface(Typeface.MONOSPACE)
            tv.setBackgroundColor(android.graphics.Color.BLACK)
            tv.isFocusable = true
            tv.isFocusableInTouchMode = true
            tv.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            val client = TerminalViewClientImpl(tv, extraKeys, 20)
            tv.setTerminalViewClient(client)
            tv.attachSession(session)
            tv.post {
                tv.requestFocus()
                val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                imm?.showSoftInput(tv, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
            tv
        },
        update = { tv ->
            if (tv.currentSession !== session) {
                tv.attachSession(session)
            }
            tv.onScreenUpdated()
        },
    )
}

@Composable
private fun SessionTabs(
    sessions: List<com.termux.terminal.TerminalSession>,
    activeIndex: Int,
    onSelect: (Int) -> Unit,
    onClose: (com.termux.terminal.TerminalSession) -> Unit,
    titlesTick: Int,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(
                horizontal = dimens.spacingSm,
                vertical = dimens.spacingXs,
            ),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
    ) {
        sessions.forEachIndexed { idx, s ->
            val active = idx == activeIndex
            val label = s.mSessionName?.takeIf { it.isNotBlank() } ?: "pid ${s.pid}"
            Surface(
                shape = tnShapes.full,
                color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.clickable { onSelect(idx) },
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = dimens.spacingMd,
                        vertical = dimens.spacingXs,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        imageVector = TnIcons.X,
                        contentDescription = "Close",
                        modifier = Modifier
                            .size(12.dp)
                            .clickable { onClose(s) },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ExtraKeysRow(
    state: ExtraKeysState,
    onKeyTap: (String) -> Unit,
    onToggleCtrl: () -> Unit,
    onToggleAlt: () -> Unit,
    onToggleShift: () -> Unit,
    onToggleFn: () -> Unit,
    onPaste: () -> Unit,
    onCopyLastOutput: () -> Unit,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    .compositeOver(MaterialTheme.colorScheme.surface)
            )
            .horizontalScroll(scroll)
            .padding(horizontal = dimens.spacingSm, vertical = dimens.spacingXs),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
    ) {
        KeyChip(label = "ESC", onClick = { onKeyTap("\u001b") }, tnShapes = tnShapes, dimens = dimens)
        KeyChip(label = "CTRL", active = state.ctrl, onClick = onToggleCtrl, tnShapes = tnShapes, dimens = dimens)
        KeyChip(label = "ALT", active = state.alt, onClick = onToggleAlt, tnShapes = tnShapes, dimens = dimens)
        KeyChip(label = "SHIFT", active = state.shift, onClick = onToggleShift, tnShapes = tnShapes, dimens = dimens)
        KeyChip(label = "TAB", onClick = { onKeyTap("\t") }, tnShapes = tnShapes, dimens = dimens)
        KeyChip(label = "|", onClick = { onKeyTap("|") }, tnShapes = tnShapes, dimens = dimens)
        KeyChip(label = "-", onClick = { onKeyTap("-") }, tnShapes = tnShapes, dimens = dimens)
        KeyChip(label = "/", onClick = { onKeyTap("/") }, tnShapes = tnShapes, dimens = dimens)
        KeyChip(label = "←", onClick = { onKeyTap("\u001b[D") }, tnShapes = tnShapes, dimens = dimens)
        KeyChip(label = "↑", onClick = { onKeyTap("\u001b[A") }, tnShapes = tnShapes, dimens = dimens)
        KeyChip(label = "↓", onClick = { onKeyTap("\u001b[B") }, tnShapes = tnShapes, dimens = dimens)
        KeyChip(label = "→", onClick = { onKeyTap("\u001b[C") }, tnShapes = tnShapes, dimens = dimens)
        KeyChip(label = "HOME", onClick = { onKeyTap("\u001b[H") }, tnShapes = tnShapes, dimens = dimens)
        KeyChip(label = "END", onClick = { onKeyTap("\u001b[F") }, tnShapes = tnShapes, dimens = dimens)
        KeyChip(label = "PgUp", onClick = { onKeyTap("\u001b[5~") }, tnShapes = tnShapes, dimens = dimens)
        KeyChip(label = "PgDn", onClick = { onKeyTap("\u001b[6~") }, tnShapes = tnShapes, dimens = dimens)
        KeyChip(label = "PASTE", onClick = onPaste, tnShapes = tnShapes, dimens = dimens)
        KeyChip(label = "COPY", onClick = onCopyLastOutput, tnShapes = tnShapes, dimens = dimens)
    }
}

@Composable
private fun KeyChip(
    label: String,
    active: Boolean = false,
    onClick: () -> Unit,
    tnShapes: com.dark.tool_neuron.ui.theme.TnShapes,
    dimens: com.dark.tool_neuron.ui.theme.Dimens,
) {
    Surface(
        shape = tnShapes.md,
        color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
            else MaterialTheme.colorScheme.surface,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(
                horizontal = dimens.spacingMd,
                vertical = dimens.spacingXs,
            ),
        )
    }
}
