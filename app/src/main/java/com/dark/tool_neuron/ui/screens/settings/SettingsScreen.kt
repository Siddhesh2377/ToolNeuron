package com.dark.tool_neuron.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.dark.tool_neuron.model.NavScreens
import com.dark.tool_neuron.ui.components.StandardCard
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.Motion
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(
    innerPadding: PaddingValues,
    onNavigate: (route: String) -> Unit,
) {
    val dimens = LocalDimens.current

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(40); visible = true }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = dimens.screenPadding,
            end = dimens.screenPadding,
            top = innerPadding.calculateTopPadding() + dimens.spacingSm,
            bottom = innerPadding.calculateBottomPadding() + dimens.spacingSm,
        ),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingXs),
    ) {
        itemsIndexed(SETTINGS_LANDING_CARDS, key = { _, card -> card.route }) { index, card ->
            AnimatedSettingsCard(
                visible = visible,
                stagger = index * 40,
            ) {
                SettingsLandingCard(card = card, onClick = { onNavigate(card.route) })
            }
        }
    }
}

@Composable
private fun AnimatedSettingsCard(
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
            slideInVertically(Motion.entrance()) { it / 8 },
    ) {
        content()
    }
}

@Composable
private fun SettingsLandingCard(card: LandingCard, onClick: () -> Unit) {
    val dimens = LocalDimens.current
    StandardCard(
        title = card.title,
        description = card.description,
        icon = card.icon,
        onClick = onClick,
        trailing = {
            Icon(
                imageVector = TnIcons.ArrowRight,
                contentDescription = null,
                modifier = Modifier.size(dimens.iconMd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

private data class LandingCard(
    val route: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
)

private val SETTINGS_LANDING_CARDS = listOf(
    LandingCard(
        route = NavScreens.SettingsChatRag.route,
        title = "Chat & RAG",
        description = "Embeddings, rerank, retrieval debugging",
        icon = TnIcons.MessageCircle,
    ),
    LandingCard(
        route = NavScreens.SettingsVoice.route,
        title = "Voice",
        description = "Default text-to-speech and speech-to-text",
        icon = TnIcons.Volume,
    ),
    LandingCard(
        route = NavScreens.SettingsTheming.route,
        title = "Theming",
        description = "Mode, palette, and live preview",
        icon = TnIcons.Sparkles,
    ),
    LandingCard(
        route = NavScreens.SettingsVision.route,
        title = "Vision",
        description = "Image quality for VLM (multimodal) models",
        icon = TnIcons.Photo,
    ),
    LandingCard(
        route = NavScreens.SettingsModel.route,
        title = "Model",
        description = "Performance and per-model configuration",
        icon = TnIcons.Sliders,
    ),
    LandingCard(
        route = NavScreens.SettingsPlugins.route,
        title = "Plugins",
        description = "ONNX execution provider, installed plugins",
        icon = TnIcons.Puzzle,
    ),
    LandingCard(
        route = NavScreens.SettingsPrivacy.route,
        title = "Privacy",
        description = "App lock, panic PIN, vault",
        icon = TnIcons.Shield,
    ),
    LandingCard(
        route = NavScreens.Storage.route,
        title = "Storage",
        description = "Disk usage by category",
        icon = TnIcons.HardDrive,
    ),
    LandingCard(
        route = NavScreens.SettingsAbout.route,
        title = "About",
        description = "Version, license, terms, credits",
        icon = TnIcons.Info,
    ),
)
