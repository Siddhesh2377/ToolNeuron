package com.dark.tool_neuron.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.data.ThemeController
import com.dark.tool_neuron.ui.components.ActionToggleGroup
import com.dark.tool_neuron.ui.components.BodyLabel
import com.dark.tool_neuron.ui.components.CaptionText
import com.dark.tool_neuron.ui.components.StandardCard
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.ColorPalette
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.ui.theme.Motion
import com.dark.tool_neuron.ui.theme.colorSchemeFor
import com.dark.tool_neuron.viewmodel.ThemingViewModel
import kotlinx.coroutines.delay

@Composable
fun SettingsThemingScreen(
    innerPadding: PaddingValues,
    viewModel: ThemingViewModel,
) {
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val palette by viewModel.palette.collectAsStateWithLifecycle()
    val dimens = LocalDimens.current

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(60); visible = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = dimens.screenPadding,
                vertical = dimens.spacingSm,
            ),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingMd),
    ) {
        AnimatedSection(
            visible = visible,
            stagger = 0,
        ) {
            ThemingPreviewCard(palette = palette)
        }

        AnimatedSection(
            visible = visible,
            stagger = 60,
        ) {
            ModeSection(
                current = mode,
                onSelect = viewModel::setMode,
            )
        }

        AnimatedSection(
            visible = visible,
            stagger = 120,
        ) {
            PaletteSection(
                current = palette,
                onSelect = viewModel::setPalette,
            )
        }

        Spacer(Modifier.height(dimens.spacingLg))
    }
}

@Composable
private fun AnimatedSection(
    visible: Boolean,
    stagger: Int,
    content: @Composable () -> Unit,
) {
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            if (stagger > 0) delay(stagger.toLong())
            ready = true
        } else {
            ready = false
        }
    }
    AnimatedVisibility(
        visible = ready,
        enter = fadeIn(Motion.entrance()) +
            slideInVertically(Motion.entrance()) { it / 6 },
    ) {
        content()
    }
}

@Composable
private fun ThemingPreviewCard(palette: ColorPalette) {
    val dimens = LocalDimens.current
    StandardCard(
        title = "Preview",
        icon = TnIcons.Sparkles,
        description = "How chats look with this palette",
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            PreviewHeaderPill(paletteName = palette.displayName)
            PreviewMessage(isUser = false, text = "This is how an assistant message looks.")
            PreviewMessage(isUser = true, text = "And this is your reply.")
            PreviewInputRow()
        }
    }
}

@Composable
private fun PreviewHeaderPill(paletteName: String) {
    val tnShapes = LocalTnShapes.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = tnShapes.full,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = TnIcons.Sparkles,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = paletteName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun PreviewMessage(isUser: Boolean, text: String) {
    val tnShapes = LocalTnShapes.current
    val bubble by animateColorAsState(
        targetValue = if (isUser) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = Motion.state(),
        label = "previewBubble",
    )
    val onBubble by animateColorAsState(
        targetValue = if (isUser) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = Motion.state(),
        label = "previewBubbleFg",
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 280.dp),
            color = bubble,
            shape = tnShapes.cardSmall,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = onBubble,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun PreviewInputRow() {
    val tnShapes = LocalTnShapes.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            shape = tnShapes.full,
        ) {
            Text(
                text = "Type a message...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        Surface(
            color = MaterialTheme.colorScheme.primary,
            shape = CircleShape,
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = TnIcons.ArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun ModeSection(
    current: ThemeController.Mode,
    onSelect: (ThemeController.Mode) -> Unit,
) {
    val dimens = LocalDimens.current
    StandardCard(
        title = "Mode",
        icon = TnIcons.Star,
        description = "Pick light, dark, or follow the system.",
    ) {
        ActionToggleGroup(
            items = listOf(
                ThemeController.Mode.SYSTEM,
                ThemeController.Mode.LIGHT,
                ThemeController.Mode.DARK,
            ),
            selectedItem = current,
            onItemSelected = onSelect,
            itemLabel = { mode ->
                when (mode) {
                    ThemeController.Mode.SYSTEM -> "System"
                    ThemeController.Mode.LIGHT -> "Light"
                    ThemeController.Mode.DARK -> "Dark"
                }
            },
            modifier = Modifier.fillMaxWidth(),
            height = dimens.toggleGroupHeight + 8.dp,
        )
    }
}

@Composable
private fun PaletteSection(
    current: ColorPalette,
    onSelect: (ColorPalette) -> Unit,
) {
    val dimens = LocalDimens.current
    StandardCard(
        title = "Palette",
        icon = TnIcons.StarOutline,
        description = "Tap a palette to apply it instantly.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingSm)) {
            ColorPalette.entries.chunked(2).forEach { rowOfPalettes ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
                ) {
                    rowOfPalettes.forEach { p ->
                        PaletteTile(
                            palette = p,
                            selected = p == current,
                            onClick = { onSelect(p) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowOfPalettes.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PaletteTile(
    palette: ColorPalette,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val scheme = remember(palette, isDark) {
        colorSchemeFor(palette, isDark, context)
    }
    val tnShapes = LocalTnShapes.current

    val borderColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = Motion.state(),
        label = "tileBorder-${palette.name}",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (selected) 2.dp else 1.dp,
        animationSpec = Motion.state(),
        label = "tileBorderWidth-${palette.name}",
    )
    val tileScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.98f,
        animationSpec = Motion.interactive(),
        label = "tileScale-${palette.name}",
    )

    Surface(
        modifier = modifier
            .scale(tileScale)
            .clickable(onClick = onClick),
        color = scheme.surface,
        shape = tnShapes.card,
        border = BorderStroke(borderWidth, borderColor),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Tiny "header pill" using primaryContainer
            Surface(
                color = scheme.primaryContainer,
                shape = tnShapes.full,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp),
            ) { Box {} }

            // Two stacked rows showing primary + secondary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(22.dp)
                        .clip(tnShapes.cardSmall)
                        .background(scheme.primary),
                )
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(scheme.tertiary),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = palette.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = scheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                AnimatedVisibility(
                    visible = selected,
                    enter = fadeIn(Motion.state()),
                ) {
                    Icon(
                        imageVector = TnIcons.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

