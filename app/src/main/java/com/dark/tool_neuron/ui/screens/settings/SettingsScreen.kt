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
import androidx.compose.material3.Text
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
        SETTING_GROUPS.forEachIndexed { groupIndex, group ->
            item(key = "group-${group.title}") {
                Text(
                    text = group.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            itemsIndexed(group.cards, key = { _, card -> "${group.title}-${card.route}" }) { index, card ->
                AnimatedSettingsCard(
                    visible = visible,
                    stagger = (groupIndex * 80) + (index * 35),
                ) {
                    SettingsLandingCard(card = card, onClick = { onNavigate(card.route) })
                }
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

private data class SettingsGroup(
    val title: String,
    val cards: List<LandingCard>,
)

private val SETTING_GROUPS = listOf(
    SettingsGroup(
        title = "Models",
        cards = listOf(
            LandingCard(
                route = NavScreens.SettingsChatRag.route,
                title = "Chat & RAG",
                description = "Embeddings, rerank, retrieval debugging",
                icon = TnIcons.MessageCircle,
            ),
            LandingCard(
                route = NavScreens.SettingsVision.route,
                title = "Vision",
                description = "Image quality for VLM models",
                icon = TnIcons.Photo,
            ),
            LandingCard(
                route = NavScreens.SettingsModel.route,
                title = "Model performance",
                description = "Performance and per-model configuration",
                icon = TnIcons.Sliders,
            ),
        ),
    ),
    SettingsGroup(
        title = "Storage",
        cards = listOf(
            LandingCard(
                route = NavScreens.Storage.route,
                title = "Storage & maintenance",
                description = "Usage, cleanup, and model health checks",
                icon = TnIcons.HardDrive,
            ),
        ),
    ),
    SettingsGroup(
        title = "Downloads",
        cards = listOf(
            LandingCard(
                route = NavScreens.Downloads.route,
                title = "Download history",
                description = "Active downloads, retries, and failures",
                icon = TnIcons.Download,
            ),
        ),
    ),
    SettingsGroup(
        title = "Remote Server",
        cards = listOf(
            LandingCard(
                route = NavScreens.SettingsServerRoles.route,
                title = "Server model roles",
                description = "Remote API model identities",
                icon = TnIcons.Server,
            ),
        ),
    ),
    SettingsGroup(
        title = "Web Search",
        cards = listOf(
            LandingCard(
                route = NavScreens.SettingsWebSearch.route,
                title = "Web search",
                description = "Default search depth for web queries",
                icon = TnIcons.Globe,
            ),
        ),
    ),
    SettingsGroup(
        title = "Privacy & Security",
        cards = listOf(
            LandingCard(
                route = NavScreens.SettingsPrivacy.route,
                title = "Privacy",
                description = "App lock, panic PIN, vault",
                icon = TnIcons.Shield,
            ),
        ),
    ),
    SettingsGroup(
        title = "Appearance",
        cards = listOf(
            LandingCard(
                route = NavScreens.SettingsTheming.route,
                title = "Theming",
                description = "Mode, palette, and live preview",
                icon = TnIcons.Sparkles,
            ),
        ),
    ),
    SettingsGroup(
        title = "Advanced",
        cards = listOf(
            LandingCard(
                route = NavScreens.SettingsVoice.route,
                title = "Voice",
                description = "Default text-to-speech and speech-to-text",
                icon = TnIcons.Volume,
            ),
            LandingCard(
                route = NavScreens.SettingsPlugins.route,
                title = "Plugins",
                description = "ONNX execution provider, installed plugins",
                icon = TnIcons.Puzzle,
            ),
            LandingCard(
                route = NavScreens.SettingsDiagnostics.route,
                title = "Diagnostics",
                description = "Recent errors, crashes, exportable bundle",
                icon = TnIcons.AlertTriangle,
            ),
        ),
    ),
    SettingsGroup(
        title = "About",
        cards = listOf(
            LandingCard(
                route = NavScreens.SettingsAbout.route,
                title = "About",
                description = "Version, license, terms, credits",
                icon = TnIcons.Info,
            ),
        ),
    ),
)
