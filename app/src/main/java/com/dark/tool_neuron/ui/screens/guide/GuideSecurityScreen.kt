package com.dark.tool_neuron.ui.screens.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes

@Composable
fun GuideSecurityScreen(innerPadding: PaddingValues) {
    GuideDetailLayout(
        innerPadding = innerPadding,
        icon = TnIcons.ShieldCheck,
        lede = "Everything in this app is designed for on-device, private use. Nothing is uploaded, tracked, or telemetered. Here's how the protections are layered.",
        steps = listOf(
            GuideStep(
                title = "On-device only",
                body = "No Google Play Services, no analytics, no crash reporting over the network. Chat history, documents, images — all stay in app storage on this phone.",
                visual = { ShieldPill() },
            ),
            GuideStep(
                title = "App PIN lock",
                body = "From Settings or first-run setup, pick a 6-digit PIN. Auto-lock kicks in the moment the app goes to background. Unlock needs the real PIN each time.",
                visual = { PinDotsVisual(filled = 4) },
            ),
            GuideStep(
                title = "Panic PIN (nuclear option)",
                body = "Set a second \"panic\" PIN from Settings. Entering it instead of your real PIN wipes the app's vault and signs you out — UX looks identical to a wrong PIN.",
                visual = { PanicIndicator() },
            ),
            GuideStep(
                title = "Backoff + wipe",
                body = "After 3 wrong PINs the app locks you out for 1 min, then 5, 15, 1 h, 4 h, 12 h, 24 h. Ten wrong in a row triggers a full wipe.",
                visual = { BackoffChipsVisual() },
            ),
            GuideStep(
                title = "Hardware-backed keys",
                body = "The master key that encrypts every record lives in Android Keystore — StrongBox if the phone has a dedicated security chip, otherwise TEE. The app classifies your device at setup.",
                visual = { StrongBoxBadge() },
            ),
            GuideStep(
                title = "Integrity checks at launch",
                body = "The app hashes its own native libraries on first launch (TOFU) and compares on every subsequent launch within the same install identity. Debugger, Frida and Xposed scans run automatically. Hook detection baselines the first 32 bytes of critical functions and re-checks them after init.",
            ),
            GuideStep(
                title = "Root warning, not refusal",
                body = "If the device is rooted, the app shows a one-time warning that other root-capable apps could read its on-disk state. After you acknowledge, it never asks again. No hard-fail — your device, your call.",
                visual = { RootWarningVisual() },
            ),
            GuideStep(
                title = "Secure clipboard",
                body = "Copied chat text is marked sensitive on Android 13+ (hidden from clipboard history) and auto-cleared after 30 seconds — unless you've since copied something else.",
                visual = { ClipboardNoticeVisual() },
            ),
        ),
        tips = listOf(
            "Screen-off clears the in-memory session — unlocked state does not persist through app-switch.",
            "FLAG_SECURE is set on PIN screens; they can't be screenshotted or shown on external displays.",
            "If StrongBox is available, you'll see a \"StrongBox\" badge in Settings → Privacy.",
        ),
    )
}

@Composable
private fun ShieldPill() {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = shapes.full,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = dimens.spacingMd,
                    vertical = dimens.spacingSm,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
            ) {
                Icon(
                    imageVector = TnIcons.ShieldCheck,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "On-device only",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun PinDotsVisual(filled: Int) {
    val dimens = LocalDimens.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val filledColor = MaterialTheme.colorScheme.primary
        val emptyColor = MaterialTheme.colorScheme.surfaceVariant
        repeat(6) { i ->
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .drawBehind {
                        drawCircle(if (i < filled) filledColor else emptyColor)
                    },
            )
        }
    }
}

@Composable
private fun PanicIndicator() {
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
                    imageVector = TnIcons.AlertTriangle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "Panic PIN → wipe",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun BackoffChipsVisual() {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf("1 m", "5 m", "15 m", "1 h", "4 h", "12 h", "24 h").forEach { label ->
            Surface(
                shape = shapes.full,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            ) {
                Text(
                    text = label,
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
    }
}

@Composable
private fun StrongBoxBadge() {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = shapes.full,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
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
                    imageVector = TnIcons.Cpu,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "StrongBox",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            text = "or TEE fallback",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RootWarningVisual() {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current
    Surface(
        shape = shapes.cardSmall,
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(dimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            Icon(
                imageVector = TnIcons.AlertTriangle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "Device looks rooted — shown once, then dismissed forever",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ClipboardNoticeVisual() {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current
    Column(
        verticalArrangement = Arrangement.spacedBy(dimens.spacingXs),
    ) {
        Surface(
            shape = shapes.cardSmall,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        ) {
            Row(
                modifier = Modifier.padding(dimens.spacingSm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
            ) {
                Icon(
                    imageVector = TnIcons.Copy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "Copied — auto-clears in 30 s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
