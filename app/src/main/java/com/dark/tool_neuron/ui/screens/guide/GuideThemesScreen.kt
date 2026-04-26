package com.dark.tool_neuron.ui.screens.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes



@Composable
fun GuideThemesScreen(innerPadding: PaddingValues) {
    GuideDetailLayout(
        innerPadding = innerPadding,
        icon = TnIcons.Sparkles,
        lede = "Choose a theme mode and accent palette. First-run setup includes a dedicated theme step; Settings lets you change any time.",
        steps = listOf(
            GuideStep(
                title = "Picked once during setup",
                body = "After the lock-mode step, setup drops you into a Theme screen. Pick a mode and palette — picks apply live. Continue lives on the bottom bar. You can change everything later from Settings → Appearance.",
            ),
            GuideStep(
                title = "Mode: system, light, dark",
                body = "\"Follow system\" flips with your OS toggle. \"Light\" and \"Dark\" are fixed. No AMOLED-black mode yet — standard dark surfaces.",
                visual = { ModeSegmentVisual() },
            ),
            GuideStep(
                title = "Accent palettes",
                body = "Seven accent palettes: Dynamic (Material You on Android 12+), Neon Lime, Ocean Cyan, Violet Dusk, Amber Rust, Rose Pink, Mono Slate.",
                visual = { PaletteStrip() },
            ),
            GuideStep(
                title = "Dynamic fallback",
                body = "On pre-Android 12 devices, Dynamic quietly falls back to Neon Lime so the app still looks cohesive.",
            ),
            GuideStep(
                title = "Change later",
                body = "Settings → Appearance. Changes take effect instantly — no restart needed.",
                visual = { SettingsAppearanceVisual() },
            ),
        ),
        tips = listOf(
            "Material You pulls from your current wallpaper colours.",
            "All screens use the same palette — chat bubbles, cards, PIN pad, progress indicators.",
        ),
    )
}

@Composable
private fun ModeSegmentVisual() {
    val dimens = LocalDimens.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
    ) {
        ModeSegment(label = "System", icon = TnIcons.Sparkles, selected = true)
        ModeSegment(label = "Light", icon = TnIcons.StarOutline, selected = false)
        ModeSegment(label = "Dark", icon = TnIcons.Star, selected = false)
    }
}

@Composable
private fun ModeSegment(label: String, icon: ImageVector, selected: Boolean) {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current
    Surface(
        shape = shapes.full,
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
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
                tint = if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PaletteStrip() {
    val colors = listOf(
        Color(0xFF556500),
        Color(0xFF006874),
        Color(0xFF5A5A96),
        Color(0xFF8A5400),
        Color(0xFF8F4953),
        Color(0xFF5F6063),
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        colors.forEach { c ->
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .drawBehind { drawCircle(c) },
            )
        }
    }
}

@Composable
private fun SettingsAppearanceVisual() {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current
    Surface(
        shape = shapes.cardSmall,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier.padding(dimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            Icon(
                imageVector = TnIcons.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = "Settings → Appearance",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
