package com.dark.tool_neuron.ui.screens.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes

@Composable
fun GuideServerScreen(innerPadding: PaddingValues) {
    GuideDetailLayout(
        innerPadding = innerPadding,
        icon = TnIcons.Server,
        lede = "Turn the loaded model into an OpenAI-compatible HTTP endpoint other devices on your Wi-Fi can call. A bundled offline Web UI is served from the same port.",
        steps = listOf(
            GuideStep(
                title = "Pick a model first",
                body = "Open the Server Screen from the drawer. The Model card lists every installed chat model — pick one. The server loads it on Start and unloads on Stop, so the choice is server-owned.",
                visual = { ModelPickerVisual() },
            ),
            GuideStep(
                title = "Choose how to bind",
                body = "Default is All interfaces — works from this device's browser AND from same-Wi-Fi devices. Loopback-only stays inside this device. Wi-Fi-only fails to start if Wi-Fi is off, even from this device.",
                visual = { BindModeChipsVisual() },
            ),
            GuideStep(
                title = "Start the server",
                body = "Tap Start. The model loads, the port binds, and the screen flips to Running. You'll see two URL groups when applicable: From this device (127.0.0.1) and From LAN (Wi-Fi IP). Default port is 11434 for OpenWebUI auto-detect.",
                visual = { EndpointUrlsVisual() },
            ),
            GuideStep(
                title = "Open the bundled Web UI",
                body = "Tap Open Web UI to launch it in this device's browser. Fully offline, single-file chat client. Streaming markdown replies, sidebar history in localStorage, bearer-token settings, rename, dark/light follows the OS. The rebuild fixed the streaming jitter — token updates throttle to one frame, the bubble can't reflow ancestors, and auto-scroll only follows when you're actually at the bottom.",
                visual = { WebUiVisual() },
            ),
            GuideStep(
                title = "Read the API at /docs",
                body = "Pointing the same browser at /docs opens a multi-page documentation site, served straight from the server. Authentication, every endpoint, the streaming wire format, error codes, rate-limit defaults, plus copy-paste examples for the OpenAI Python SDK, OpenWebUI, plain fetch, and llama-index. The chat UI links to it from the sidebar footer. Like /, it's a public route — read the docs without a token.",
                visual = { DocsLinkVisual() },
            ),
            GuideStep(
                title = "Reveal the bearer token",
                body = "Every request needs a tn_sk_… bearer token. It's generated on first start and lives in the encrypted vault. Tap Reveal to see it — gating requires an active app session. Rotate to invalidate the old one.",
                visual = { TokenCardVisual() },
            ),
            GuideStep(
                title = "While running, the app is locked down",
                body = "The drawer hides, back is absorbed, and you stay on the Server Screen until you tap Stop. This keeps you from accidentally swapping models or backgrounding the inference engine while clients are connected.",
                visual = { LockdownPillVisual() },
            ),
            GuideStep(
                title = "Stop releases everything",
                body = "Tap Stop server — the bound port closes, the bearer token is wiped from native memory, the model unloads, and the lockdown lifts.",
            ),
        ),
        tips = listOf(
            "/v1/models returns only the loaded model on purpose. Pick what you want before starting.",
            "127.0.0.1 always works regardless of Wi-Fi state. Share the LAN URL only when devices are on the same Wi-Fi.",
            "No TLS in this build — keep the server inside trusted networks.",
            "Public routes are /, /webui, /docs, and /health. Everything else needs a Bearer token.",
        ),
    )
}

@Composable
private fun ModelPickerVisual() {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingXs),
    ) {
        ModelRowMock(name = "Llama 3.2 3B Instruct", selected = true)
        ModelRowMock(name = "Qwen 2.5 1.5B Instruct", selected = false)
    }
}

@Composable
private fun ModelRowMock(name: String, selected: Boolean) {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current
    val bg = if (selected)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    else
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    Surface(
        shape = shapes.cardSmall,
        color = bg,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = dimens.spacingSm,
                vertical = dimens.spacingSm,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            Surface(
                shape = shapes.full,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(16.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (selected) {
                        Icon(
                            imageVector = TnIcons.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(10.dp),
                        )
                    }
                }
            }
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun BindModeChipsVisual() {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
            "loopback only" to false,
            "wifi only" to false,
            "all interfaces" to true,
        ).forEach { (label, selected) ->
            Surface(
                shape = shapes.full,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(
                        horizontal = dimens.spacingSm,
                        vertical = dimens.spacingXxs,
                    ),
                )
            }
        }
    }
}

@Composable
private fun EndpointUrlsVisual() {
    val dimens = LocalDimens.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
    ) {
        UrlRowMock(label = "From this device", url = "http://127.0.0.1:11434/v1")
        UrlRowMock(label = "From LAN", url = "http://192.168.1.42:11434/v1")
    }
}

@Composable
private fun UrlRowMock(label: String, url: String) {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current
    Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingXxs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            shape = shapes.cardSmall,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
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
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = TnIcons.Copy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun WebUiVisual() {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current
    Surface(
        shape = shapes.cardSmall,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(dimens.spacingSm),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingXs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
            ) {
                Icon(
                    imageVector = TnIcons.Globe,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "Web UI",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = shapes.full,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                ) {
                    Text(
                        text = "offline",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(
                            horizontal = dimens.spacingSm,
                            vertical = dimens.spacingXxs,
                        ),
                    )
                }
            }
            Surface(
                shape = shapes.cardSmall,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            ) {
                Text(
                    text = "What's the capital of France?",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(
                        horizontal = dimens.spacingSm,
                        vertical = dimens.spacingXs,
                    ),
                )
            }
            Surface(
                shape = shapes.cardSmall,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Text(
                    text = "Paris — streaming…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(
                        horizontal = dimens.spacingSm,
                        vertical = dimens.spacingXs,
                    ),
                )
            }
        }
    }
}

@Composable
private fun TokenCardVisual() {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingXs),
    ) {
        Surface(
            shape = shapes.cardSmall,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "tn_sk_••••••••••••••••••••",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(
                    horizontal = dimens.spacingSm,
                    vertical = dimens.spacingXs,
                ),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs)) {
            TokenActionChip(label = "Reveal", icon = TnIcons.Eye)
            TokenActionChip(label = "Rotate", icon = TnIcons.Refresh)
        }
    }
}

@Composable
private fun TokenActionChip(label: String, icon: ImageVector) {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current
    Surface(
        shape = shapes.full,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = dimens.spacingSm,
                vertical = dimens.spacingXs,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun DocsLinkVisual() {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingXs),
    ) {
        Surface(
            shape = shapes.cardSmall,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
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
                    text = "http://127.0.0.1:11434/docs",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = TnIcons.Copy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingXxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf("Overview", "Auth", "Chat", "Streaming", "Errors", "Examples").forEach { label ->
                Surface(
                    shape = shapes.full,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = dimens.spacingSm,
                            vertical = dimens.spacingXxs,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun LockdownPillVisual() {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = shapes.full,
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = dimens.spacingSm,
                    vertical = dimens.spacingXs,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
            ) {
                Icon(
                    imageVector = TnIcons.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "App locked to Server Screen",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
