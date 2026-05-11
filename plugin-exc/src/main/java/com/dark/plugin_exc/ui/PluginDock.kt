package com.dark.plugin_exc.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dark.plugin_api.PluginManifest
import com.dark.plugin_exc.PluginExecutor

@Composable
fun PluginDock(
    executor: PluginExecutor,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val openIds by executor.openPlugins.collectAsState()
    val activeId by executor.activePlugin.collectAsState()
    val installed by executor.registry.installed.collectAsState()

    AnimatedVisibility(
        visible = openIds.isNotEmpty(),
        enter = fadeIn() + scaleIn(initialScale = 0.92f),
        exit = fadeOut() + scaleOut(targetScale = 0.92f),
        modifier = modifier
            .padding(WindowInsets.navigationBars.asPaddingValues())
            .padding(16.dp),
    ) {
        val openManifests = openIds.mapNotNull { id ->
            installed.firstOrNull { it.manifest.id == id }?.manifest
        }

        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.shadow(elevation = 12.dp, shape = RoundedCornerShape(32.dp)),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                BackChip(onClick = onClose)
                Spacer(Modifier.width(2.dp))
                openManifests.forEach { manifest ->
                    PluginDockChip(
                        manifest = manifest,
                        active = manifest.id == activeId,
                        onClick = { executor.switchTo(manifest.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BackChip(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Back to host" },
    ) {
        BackArrowGlyph(tint = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun BackArrowGlyph(tint: Color) {
    androidx.compose.material3.Icon(
        imageVector = backArrowVector,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(18.dp),
    )
}

private val backArrowVector: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "PluginDockBack",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
        .path(fill = androidx.compose.ui.graphics.SolidColor(Color.Black)) {
            moveTo(20f, 11f)
            horizontalLineTo(7.83f)
            lineTo(13.42f, 5.41f)
            lineTo(12f, 4f)
            lineTo(4f, 12f)
            lineTo(12f, 20f)
            lineTo(13.41f, 18.59f)
            lineTo(7.83f, 13f)
            horizontalLineTo(20f)
            verticalLineTo(11f)
            close()
        }
        .build()
}

@Composable
private fun PluginDockChip(
    manifest: PluginManifest,
    active: Boolean,
    onClick: () -> Unit,
) {
    val initial = manifest.initial.take(1).ifBlank { manifest.name.take(1) }.uppercase()
    val containerColor = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val contentColor = if (active) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(if (active) 48.dp else 44.dp)
            .clip(CircleShape)
            .background(containerColor, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = manifest.name },
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.titleMedium,
            color = contentColor,
        )
        if (manifest.hasNativeCode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary, CircleShape),
            )
        }
    }
}
