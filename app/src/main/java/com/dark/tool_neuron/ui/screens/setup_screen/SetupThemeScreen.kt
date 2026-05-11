package com.dark.tool_neuron.ui.screens.setup_screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.data.ThemeController
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.ColorPalette
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.ui.theme.Motion
import com.dark.tool_neuron.viewmodel.SetupThemeViewModel
import kotlinx.coroutines.delay

@Composable
fun SetupThemeScreen(
    innerPadding: PaddingValues,
    viewModel: SetupThemeViewModel = hiltViewModel(),
) {
    val dimens = LocalDimens.current
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val palette by viewModel.palette.collectAsStateWithLifecycle()

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(80); visible = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = dimens.screenPadding),
    ) {
        Spacer(Modifier.height(dimens.spacingXl))

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(Motion.entrance()) + slideInVertically(Motion.entrance()) { it / 4 },
        ) {
            Column {
                Icon(
                    imageVector = TnIcons.Sparkles,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(dimens.spacingLg))
                Text(
                    text = "Make it yours",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(dimens.spacingXs))
                Text(
                    text = "Pick a theme mode and accent palette. You can change this anytime from Settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(dimens.spacingXl))

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(Motion.entrance()) + slideInVertically(Motion.entrance()) { it / 3 },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingSm)) {
                SectionLabel(text = "Theme mode")
                ThemeModeRow(
                    mode = ThemeController.Mode.SYSTEM,
                    icon = TnIcons.Sparkles,
                    title = "Follow system",
                    subtitle = "Match device light/dark setting",
                    selected = mode == ThemeController.Mode.SYSTEM,
                    onClick = { viewModel.selectMode(ThemeController.Mode.SYSTEM) },
                )
                ThemeModeRow(
                    mode = ThemeController.Mode.LIGHT,
                    icon = TnIcons.StarOutline,
                    title = "Light",
                    subtitle = "Always use light colours",
                    selected = mode == ThemeController.Mode.LIGHT,
                    onClick = { viewModel.selectMode(ThemeController.Mode.LIGHT) },
                )
                ThemeModeRow(
                    mode = ThemeController.Mode.DARK,
                    icon = TnIcons.Star,
                    title = "Dark",
                    subtitle = "Always use dark colours",
                    selected = mode == ThemeController.Mode.DARK,
                    onClick = { viewModel.selectMode(ThemeController.Mode.DARK) },
                )
            }
        }

        Spacer(Modifier.height(dimens.spacingLg))

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(Motion.entrance()) + slideInVertically(Motion.entrance()) { it / 3 },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingSm)) {
                SectionLabel(text = "Accent palette")
                ColorPalette.entries.forEach { p ->
                    PaletteRow(
                        palette = p,
                        selected = palette == p,
                        onClick = { viewModel.selectPalette(p) },
                    )
                }
            }
        }

        Spacer(Modifier.height(dimens.spacingLg))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun ThemeModeRow(
    mode: ThemeController.Mode,
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tnShapes = LocalTnShapes.current
    val dimens = LocalDimens.current

    val borderColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = Motion.state(),
        label = "modeBorder-${mode.name}",
    )
    val containerColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else MaterialTheme.colorScheme.surface,
        animationSpec = Motion.state(),
        label = "modeBg-${mode.name}",
    )

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = tnShapes.card,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(dimens.spacingMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(dimens.spacingMd))
            TextCol(
                title = title,
                subtitle = subtitle,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RowScope.TextCol(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PaletteRow(
    palette: ColorPalette,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tnShapes = LocalTnShapes.current
    val dimens = LocalDimens.current

    val borderColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = Motion.state(),
        label = "palBorder-${palette.name}",
    )

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = tnShapes.card,
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(dimens.spacingMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PaletteSwatch(palette = palette)
            Spacer(Modifier.width(dimens.spacingMd))
            Text(
                text = palette.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(
                    imageVector = TnIcons.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun PaletteSwatch(palette: ColorPalette) {
    val colors = paletteSwatchColors(palette)
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        colors.forEach { c ->
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .drawBehind { drawCircle(c) },
            )
        }
    }
}

private fun paletteSwatchColors(palette: ColorPalette): List<Color> = when (palette) {
    ColorPalette.DYNAMIC -> listOf(Color(0xFF8AB4F8), Color(0xFFB388FF), Color(0xFFFFAB91))
    ColorPalette.NEON_LIME -> listOf(Color(0xFF556500), Color(0xFFD6ED6C), Color(0xFFDFE6C5))
    ColorPalette.OCEAN_CYAN -> listOf(Color(0xFF006874), Color(0xFF82D3E0), Color(0xFFCAE6ED))
    ColorPalette.VIOLET_DUSK -> listOf(Color(0xFF5A5A96), Color(0xFFC7BCFF), Color(0xFFE2DEFF))
    ColorPalette.AMBER_RUST -> listOf(Color(0xFF8A5400), Color(0xFFFFD898), Color(0xFFFFE0B6))
    ColorPalette.ROSE_PINK -> listOf(Color(0xFF8F4953), Color(0xFFFFD9DE), Color(0xFFFFECEF))
    ColorPalette.MONO_SLATE -> listOf(Color(0xFF5F6063), Color(0xFFB9C6DA), Color(0xFFE2E6EC))
}
