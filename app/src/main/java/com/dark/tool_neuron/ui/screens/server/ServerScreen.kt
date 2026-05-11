package com.dark.tool_neuron.ui.screens.server

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.native_server.BindMode
import com.dark.tool_neuron.model.ModelInfo
import com.dark.tool_neuron.service.server.ServerRequestEvent
import com.dark.tool_neuron.service.server.ServerState
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.ActionTextButton
import com.dark.tool_neuron.ui.components.BodyLabel
import com.dark.tool_neuron.ui.components.CaptionText
import com.dark.tool_neuron.ui.components.InfoBadge
import com.dark.tool_neuron.ui.components.SectionHeader
import com.dark.tool_neuron.ui.components.StandardCard
import com.dark.tool_neuron.ui.components.StatusBadge
import com.dark.tool_neuron.ui.components.TnTextField
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.util.SecureClipboard
import com.dark.tool_neuron.viewmodel.ServerViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ServerScreen(
    innerPadding: PaddingValues,
    vm: ServerViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val port by vm.port.collectAsStateWithLifecycle()
    val bindMode by vm.bindMode.collectAsStateWithLifecycle()
    val events by vm.requestEvents.collectAsStateWithLifecycle()
    val tokenVisible by vm.tokenVisible.collectAsStateWithLifecycle()
    val installedModels by vm.installedChatModels.collectAsStateWithLifecycle()
    val selectedModelId by vm.selectedModelId.collectAsStateWithLifecycle()

    val dimens = LocalDimens.current
    val busy = state is ServerState.Running ||
            state is ServerState.Starting ||
            state is ServerState.LoadingModel
    BackHandler(enabled = busy) {}

    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(innerPadding)
            .verticalScroll(scroll)
            .padding(horizontal = dimens.screenPadding, vertical = dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
    ) {
        StatusCard(state)

        if (state is ServerState.Running) {
            EndpointsCard(state as ServerState.Running)
        }

        ModelPickerCard(
            models = installedModels,
            selectedId = selectedModelId,
            disabled = busy,
            onSelect = vm::selectModel,
        )

        ConfigCard(
            port = port,
            bindMode = bindMode,
            disabled = busy,
            onPortChange = vm::setPort,
            onBindChange = vm::setBindMode,
        )

        TokenCard(
            visible = tokenVisible,
            maskedToken = vm.maskedToken(),
            realToken = vm.currentToken(),
            onReveal = vm::revealToken,
            onHide = vm::hideToken,
            onRotate = vm::rotateToken,
        )

        StartStopBar(
            state = state,
            startEnabled = !busy && (vm.anyEngineInstalled.collectAsStateWithLifecycle().value || !selectedModelId.isNullOrBlank()),
            onStart = vm::start,
            onStop = vm::stop,
        )

        if (events.isNotEmpty()) RequestLogCard(events)
    }
}

@Composable
private fun StatusCard(state: ServerState) {
    val dimens = LocalDimens.current
    val (title, subtitle) = when (state) {
        ServerState.Stopped -> "Stopped" to "Pick a model and tap Start."
        is ServerState.LoadingModel -> "Loading model" to state.modelName
        ServerState.Starting -> "Starting" to "Binding port and attaching bridge."
        is ServerState.Running ->
            "Running" to "${state.modelName}  ·  ${state.info.bindMode.name.lowercase().replace('_', ' ')}"
        is ServerState.Failed -> "Failed" to state.reason
    }
    val isActive = state is ServerState.Running
    StandardCard(
        title = title,
        icon = TnIcons.Server,
        trailing = {
            StatusBadge(
                text = if (isActive) "ACTIVE" else "IDLE",
                isActive = isActive,
            )
            if (state is ServerState.LoadingModel || state is ServerState.Starting) {
                Spacer(Modifier.width(dimens.spacingXs))
                CircularProgressIndicator(
                    modifier = Modifier.size(dimens.iconSm),
                    strokeWidth = 2.dp,
                )
            }
        },
    ) {
        BodyLabel(
            text = subtitle,
            color = if (state is ServerState.Failed)
                MaterialTheme.colorScheme.error
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EndpointsCard(running: ServerState.Running) {
    val dimens = LocalDimens.current
    val ctx = LocalContext.current
    val info = running.info
    val port = info.port
    val deviceWebUrl = "http://${info.displayHost}:$port/"
    val deviceApiUrl = "http://${info.displayHost}:$port/v1"
    val lanHost = info.lanHost
    val lanWebUrl = lanHost?.let { "http://$it:$port/" }
    val lanApiUrl = lanHost?.let { "http://$it:$port/v1" }

    StandardCard(
        title = "Endpoints",
        icon = TnIcons.Globe,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingSm)) {
            SectionHeader(title = "From this device")
            UrlRow(
                label = "OpenAI base URL",
                url = deviceApiUrl,
                onCopy = { SecureClipboard.copy(ctx, "Tool-Neuron API URL", deviceApiUrl) },
            )
            UrlRow(
                label = "Web UI",
                url = deviceWebUrl,
                onCopy = { SecureClipboard.copy(ctx, "Tool-Neuron Web UI", deviceWebUrl) },
            )
            ActionTextButton(
                onClickListener = {
                    val intent = Intent(Intent.ACTION_VIEW, deviceWebUrl.toUri())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { ctx.startActivity(intent) }
                },
                icon = TnIcons.Globe,
                text = "Open Web UI",
                contentDescription = "Open the bundled Web UI in this device's browser",
                modifier = Modifier.fillMaxWidth(),
            )

            if (lanWebUrl != null && lanApiUrl != null) {
                Spacer(Modifier.size(dimens.spacingXs))
                SectionHeader(
                    title = "From LAN",
                    action = { CaptionText(text = "same Wi-Fi") },
                )
                UrlRow(
                    label = "OpenAI base URL",
                    url = lanApiUrl,
                    onCopy = { SecureClipboard.copy(ctx, "Tool-Neuron LAN API URL", lanApiUrl) },
                )
                UrlRow(
                    label = "Web UI",
                    url = lanWebUrl,
                    onCopy = { SecureClipboard.copy(ctx, "Tool-Neuron LAN Web UI", lanWebUrl) },
                )
            }

            CaptionText(
                text = "127.0.0.1 always works on this device, regardless of Wi-Fi or mobile-data state. Share the LAN URL when devices are on the same Wi-Fi.",
            )
        }
    }
}

@Composable
private fun UrlRow(label: String, url: String, onCopy: () -> Unit) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingXxs)) {
        CaptionText(text = label)
        Surface(
            shape = tnShapes.cardSmall,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = dimens.spacingSm,
                    vertical = dimens.spacingXs,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
            ) {
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ActionButton(
                    onClickListener = onCopy,
                    icon = TnIcons.Copy,
                    contentDescription = "Copy $label",
                )
            }
        }
    }
}

@Composable
private fun ModelPickerCard(
    models: List<ModelInfo>,
    selectedId: String?,
    disabled: Boolean,
    onSelect: (String) -> Unit,
) {
    val dimens = LocalDimens.current
    StandardCard(
        title = "Model",
        description = "Server loads this model on start, unloads on stop.",
        icon = TnIcons.Cpu,
    ) {
        if (models.isEmpty()) {
            CaptionText("No chat models installed. Download one from the Model Store first.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingXs)) {
                models.forEach { model ->
                    ModelRow(
                        model = model,
                        selected = model.id == selectedId,
                        enabled = !disabled,
                        onClick = { onSelect(model.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelRow(
    model: ModelInfo,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    val bg = if (selected)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    else
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    Surface(
        shape = tnShapes.cardSmall,
        color = bg,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = dimens.spacingSm,
                vertical = dimens.spacingSm,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(
                        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = RoundedCornerShape(50),
                    )
                    .background(
                        color = Color.Transparent,
                        shape = RoundedCornerShape(50),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        TnIcons.Check,
                        contentDescription = "Selected",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    model.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (enabled)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                )
                CaptionText(
                    text = model.id,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                        .copy(alpha = if (enabled) 0.7f else 0.3f),
                )
            }
        }
    }
}

@Composable
private fun ConfigCard(
    port: Int,
    bindMode: BindMode,
    disabled: Boolean,
    onPortChange: (Int) -> Unit,
    onBindChange: (BindMode) -> Unit,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    var draft by remember(port, disabled) { mutableStateOf(port.toString()) }
    val draftInt = draft.toIntOrNull()
    val draftValid = draftInt != null && draftInt in 1024..65535

    StandardCard(
        title = "Configuration",
        icon = TnIcons.Wrench,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingSm)) {
            CaptionText(text = "Port")
            Surface(
                shape = tnShapes.cardSmall,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                TnTextField(
                    value = draft,
                    onValueChange = { input ->
                        val cleaned = input.filter { it.isDigit() }.take(5)
                        draft = cleaned
                        cleaned.toIntOrNull()
                            ?.takeIf { it in 1024..65535 }
                            ?.let(onPortChange)
                    },
                    placeholder = "11434",
                    enabled = !disabled,
                    maxLines = 1,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            if (!draftValid) {
                CaptionText(
                    text = "Enter a port between 1024 and 65535",
                    color = MaterialTheme.colorScheme.error,
                )
            }

            SectionHeader(title = "Bind mode")
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs)) {
                BindMode.entries.forEach { mode ->
                    BindModeChip(
                        mode = mode,
                        selected = bindMode == mode,
                        enabled = !disabled,
                        onClick = { onBindChange(mode) },
                    )
                }
            }
            CaptionText(text = bindModeHelp(bindMode))
        }
    }
}

@Composable
private fun BindModeChip(
    mode: BindMode,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    val bg = when {
        selected -> MaterialTheme.colorScheme.primary
        enabled -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }
    val fg = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        enabled -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    }
    Surface(
        shape = tnShapes.full,
        color = bg,
        modifier = Modifier.then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Text(
            text = mode.name.lowercase().replace('_', ' '),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = fg,
            modifier = Modifier.padding(
                horizontal = dimens.spacingMd,
                vertical = dimens.spacingXs,
            ),
        )
    }
}

private fun bindModeHelp(mode: BindMode): String = when (mode) {
    BindMode.LOOPBACK_ONLY ->
        "Device only — 127.0.0.1 in this device's browser. Safest, no LAN exposure."
    BindMode.WIFI_ONLY ->
        "LAN only — same-Wi-Fi devices. Server fails to start if Wi-Fi is off and the device's own browser cannot reach it."
    BindMode.ALL_INTERFACES ->
        "Recommended — works from this device (127.0.0.1) AND from same-Wi-Fi devices. Loopback always works regardless of Wi-Fi or mobile-data state."
}

@Composable
private fun TokenCard(
    visible: Boolean,
    maskedToken: String,
    realToken: String,
    onReveal: () -> Boolean,
    onHide: () -> Unit,
    onRotate: () -> Unit,
) {
    val dimens = LocalDimens.current
    val ctx = LocalContext.current
    var revealError by remember { mutableStateOf<String?>(null) }
    StandardCard(
        title = "Bearer token",
        icon = TnIcons.Lock,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingSm)) {
            Surface(
                shape = LocalTnShapes.current.cardSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (visible) realToken.ifBlank { "—" } else maskedToken.ifBlank { "—" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(
                        horizontal = dimens.spacingSm,
                        vertical = dimens.spacingXs,
                    ),
                )
            }
            revealError?.let {
                CaptionText(text = it, color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs)) {
                if (visible) {
                    ActionTextButton(
                        onClickListener = onHide,
                        icon = TnIcons.EyeOff,
                        text = "Hide",
                    )
                    ActionTextButton(
                        onClickListener = {
                            SecureClipboard.copy(ctx, "Tool-Neuron token", realToken)
                        },
                        icon = TnIcons.Copy,
                        text = "Copy",
                    )
                } else {
                    ActionTextButton(
                        onClickListener = {
                            val ok = onReveal()
                            revealError = if (ok) null else "Unlock the app to reveal the token"
                        },
                        icon = TnIcons.Eye,
                        text = "Reveal",
                    )
                }
                ActionTextButton(
                    onClickListener = onRotate,
                    icon = TnIcons.Refresh,
                    text = "Rotate",
                )
            }
        }
    }
}

@Composable
private fun StartStopBar(
    state: ServerState,
    startEnabled: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val dimens = LocalDimens.current
    when (state) {
        is ServerState.Running -> {
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = dimens.spacingXs),
            ) { Text("Stop server") }
        }
        is ServerState.LoadingModel, is ServerState.Starting -> {
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = dimens.spacingXs),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(dimens.iconSm),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(Modifier.width(dimens.spacingSm))
                Text(
                    if (state is ServerState.LoadingModel) "Loading model — Cancel" else "Starting — Cancel",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
        else -> {
            Button(
                onClick = onStart,
                enabled = startEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = dimens.spacingXs),
            ) { Text(if (startEnabled) "Start server" else "Pick a model first") }
        }
    }
}

@Composable
private fun RequestLogCard(events: List<ServerRequestEvent>) {
    val dimens = LocalDimens.current
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    StandardCard(
        title = "Recent requests",
        icon = TnIcons.Database,
        trailing = {
            InfoBadge(text = "${events.size}")
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingXs)) {
            events.take(20).forEach { e ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
                ) {
                    Text(
                        fmt.format(Date(e.timestampMs)),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${e.method} ${e.path}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        e.status.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor(e.status),
                    )
                    CaptionText(text = "${e.durationMs}ms")
                }
            }
        }
    }
}

@Composable
private fun statusColor(status: Int): Color = when {
    status in 200..299 -> Color(0xFF22C55E)
    status in 400..499 -> Color(0xFFEAB308)
    status >= 500 -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
