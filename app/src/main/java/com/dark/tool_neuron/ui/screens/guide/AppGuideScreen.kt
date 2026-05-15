package com.dark.tool_neuron.ui.screens.guide

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import kotlinx.coroutines.delay

internal data class GuideEntry(
    val key: String,
    val title: String,
    val blurb: String,
    val icon: ImageVector,
)

internal data class GuideCategory(
    val title: String,
    val entries: List<GuideEntry>,
)

@Composable
fun AppGuideScreen(
    innerPadding: PaddingValues,
    onOpenEntry: (String) -> Unit,
) {
    val dimens = LocalDimens.current

    val categories = remember { guideCategories() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .navigationBarsPadding()
            .padding(horizontal = dimens.screenPadding),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingLg),
        ) {
            item { Spacer(Modifier.height(dimens.spacingSm)) }
            item { HeroIntro() }

            items(categories, key = { it.title }) { cat ->
                var visible by remember(cat.title) { mutableStateOf(false) }
                LaunchedEffect(cat.title) { delay(80); visible = true }
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(300)) +
                        slideInVertically(tween(350)) { it / 4 },
                ) {
                    CategoryBlock(category = cat, onOpenEntry = onOpenEntry)
                }
            }

            item { Spacer(Modifier.height(dimens.spacingMd)) }
        }
    }
}

@Composable
private fun HeroIntro() {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    Surface(
        shape = tnShapes.card,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(dimens.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingMd),
        ) {
            Icon(
                imageVector = TnIcons.BookOpen,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column {
                Text(
                    text = "Everything the app can do",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(dimens.spacingXxs))
                Text(
                    text = "Pick a feature to see a step-by-step walkthrough with visuals.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                )
            }
        }
    }
}

@Composable
private fun CategoryBlock(
    category: GuideCategory,
    onOpenEntry: (String) -> Unit,
) {
    val dimens = LocalDimens.current
    Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingSm)) {
        Text(
            text = category.title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = dimens.spacingSm),
        )
        category.entries.forEach { entry ->
            GuideOptionCard(entry = entry, onClick = { onOpenEntry(entry.key) })
        }
    }
}

@Composable
private fun GuideOptionCard(
    entry: GuideEntry,
    onClick: () -> Unit,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current

    Surface(
        onClick = onClick,
        shape = tnShapes.card,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(dimens.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingMd),
        ) {
            Surface(
                shape = tnShapes.full,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = entry.icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = dimens.spacingSm),
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = entry.blurb,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        }
    }
}

private fun guideCategories(): List<GuideCategory> = listOf(
    GuideCategory(
        title = "Getting started",
        entries = listOf(
            GuideEntry(
                key = GuideEntryKeys.CHAT,
                title = "Chat with a model",
                blurb = "Send messages and watch them stream. Edit, regenerate, speak, dictate.",
                icon = TnIcons.MessageCircle,
            ),
            GuideEntry(
                key = GuideEntryKeys.MODELS,
                title = "Download and manage models",
                blurb = "Browse the Store, search HuggingFace, import GGUF files, tune samplers.",
                icon = TnIcons.Package,
            ),
            GuideEntry(
                key = GuideEntryKeys.VOICE,
                title = "Voice in and out",
                blurb = "Tap the mic for on-device speech, or have replies spoken back.",
                icon = TnIcons.Mic,
            ),
        ),
    ),
    GuideCategory(
        title = "Advanced AI",
        entries = listOf(
            GuideEntry(
                key = GuideEntryKeys.RAG,
                title = "Documents and RAG",
                blurb = "Attach PDFs and docs. The app indexes them locally and grounds answers.",
                icon = TnIcons.BookOpen,
            ),
            GuideEntry(
                key = GuideEntryKeys.VLM,
                title = "Vision (VLM)",
                blurb = "Drop a VLM repo into the vlm folder; the projector auto-loads on use.",
                icon = TnIcons.Eye,
            ),
            GuideEntry(
                key = GuideEntryKeys.SERVER,
                title = "Remote Server",
                blurb = "Expose the loaded model on your LAN over an OpenAI-compatible API + Web UI.",
                icon = TnIcons.Server,
            ),
        ),
    ),
    GuideCategory(
        title = "Beyond chat",
        entries = listOf(
            GuideEntry(
                key = GuideEntryKeys.IMAGES,
                title = "Image generation",
                blurb = "Run Stable Diffusion on the device — QNN on NPU-capable phones, MNN as fallback.",
                icon = TnIcons.Photo,
            ),
            GuideEntry(
                key = GuideEntryKeys.PLUGINS,
                title = "Plugins",
                blurb = "Install mini-apps from the public HF catalog. Sandboxed Compose UIs with their own ONNX, storage, and dock.",
                icon = TnIcons.Puzzle,
            ),
        ),
    ),
    GuideCategory(
        title = "Your phone, your data",
        entries = listOf(
            GuideEntry(
                key = GuideEntryKeys.SECURITY,
                title = "Privacy and lock",
                blurb = "PIN lock, auto-lock, panic PIN, hardware-backed keys, on-device only.",
                icon = TnIcons.ShieldCheck,
            ),
            GuideEntry(
                key = GuideEntryKeys.THEMES,
                title = "Themes and palettes",
                blurb = "Follow system or pick a fixed mode. Seven accent palettes.",
                icon = TnIcons.Sparkles,
            ),
        ),
    ),
)

internal object GuideEntryKeys {
    const val CHAT = "chat"
    const val MODELS = "models"
    const val VOICE = "voice"
    const val RAG = "rag"
    const val VLM = "vlm"
    const val SECURITY = "security"
    const val THEMES = "themes"
    const val SERVER = "server"
    const val PLUGINS = "plugins"
    const val IMAGES = "images"
}
