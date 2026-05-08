package com.dark.tool_neuron.ui.screens.dev_notes

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.BuildConfig
import com.dark.tool_neuron.ui.components.InfoBadge
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes

private data class DevNotesSection(
    val title: String,
    val body: String,
    val icon: ImageVector,
    val tone: SectionTone = SectionTone.Neutral,
)

private enum class SectionTone { Neutral, Caution }

private const val WELCOME_TITLE = "Welcome"

private const val WELCOME_BODY =
    "This is a small AI assistant that runs on your phone. The model lives on " +
            "your device, your chats stay on your device, and nothing gets sent off to a " +
            "server somewhere. You pick what you want to use, and the rest stays out of " +
            "the way."

private val SECTIONS = listOf(
    DevNotesSection(
        title = "Your data stays here",
        icon = TnIcons.Lock,
        body = "Chats, attached files, voice models, and settings all sit on your phone. " +
                "There is no analytics, no cloud sync, no telemetry. The master key that " +
                "unlocks your data is held in your phone's secure chip, so a copy of the " +
                "files on their own is just noise."
    ),
    DevNotesSection(
        title = "Chat with a model on your phone",
        icon = TnIcons.Cpu,
        body = "Pick a model from the store and download it once. After that it runs " +
                "offline. Smaller models are quick and friendly to your battery. Bigger " +
                "ones are slower but write better answers, so try a couple and see what " +
                "fits."
    ),
    DevNotesSection(
        title = "Talk to it, hear it back",
        icon = TnIcons.Mic,
        body = "Tap the mic to speak instead of type. Tap the speaker on a reply to hear " +
                "it read aloud. The voice models also live on your device, so this works " +
                "with no signal."
    ),
    DevNotesSection(
        title = "Ask questions about your documents",
        icon = TnIcons.FileText,
        body = "Attach a PDF, a text file, or one of the common document formats from the " +
                "Attach tab. The app reads through it locally and pulls the relevant bits " +
                "into the conversation. Replies come with little chips you can tap to see " +
                "the exact passage the answer drew from."
    ),
    DevNotesSection(
        title = "Show it pictures",
        icon = TnIcons.Eye,
        body = "If you have loaded a vision model, the image button on the input bar wakes " +
                "up. Attach a photo and ask about it. Without a vision model loaded the " +
                "button stays dim, which is the hint to grab one from the store."
    ),
    DevNotesSection(
        title = "Look things up with Research",
        icon = TnIcons.Compass,
        body = "Type /research followed by your question, or tap the compass on the input " +
                "bar. The app runs a search, reads the top hits, summarises each one, then " +
                "asks the model to write a structured document with citations. The search " +
                "and fetch need a network. The reading and writing happen on your phone."
    ),
    DevNotesSection(
        title = "Share it with your other devices",
        icon = TnIcons.Server,
        body = "Flip on the local server and the loaded model is reachable from another " +
                "browser on your home Wi-Fi. There is a small bundled web UI so you can " +
                "chat from a laptop without installing anything. A bearer token gates " +
                "access, and you can rotate it any time."
    ),
    DevNotesSection(
        title = "Rough edges",
        icon = TnIcons.AlertTriangle,
        tone = SectionTone.Caution,
        body = "Downloads run in the background and show progress in the notification " +
                "shade. There is no dedicated downloads screen yet, that one is on the " +
                "list. The local server speaks plain HTTP, so only run it on a network " +
                "you trust."
    ),
)

@Composable
fun DevNotesScreen(innerPadding: PaddingValues) {
    val dimens = LocalDimens.current

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
            verticalArrangement = Arrangement.spacedBy(dimens.spacingMd),
        ) {
            item(key = "spacer_top") {
                Spacer(Modifier.height(dimens.spacingSm))
            }
            item(key = "welcome") {
                WelcomeBlock()
            }
            items(items = SECTIONS, key = { it.title }) { section ->
                SectionCard(section)
            }
            item(key = "spacer_bottom") {
                Spacer(Modifier.height(dimens.spacingLg))
            }
        }
    }
}

@Composable
private fun WelcomeBlock() {
    val dimens = LocalDimens.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingXs),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            Text(
                text = WELCOME_TITLE,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            InfoBadge(text = "v${BuildConfig.VERSION_NAME}")
        }
        Text(
            text = WELCOME_BODY,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        )
    }
}

@Composable
private fun SectionCard(section: DevNotesSection) {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current
    val containerColor = when (section.tone) {
        SectionTone.Neutral -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        SectionTone.Caution -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
    }
    val iconTint = when (section.tone) {
        SectionTone.Neutral -> MaterialTheme.colorScheme.primary
        SectionTone.Caution -> MaterialTheme.colorScheme.error
    }
    val iconBg = when (section.tone) {
        SectionTone.Neutral -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        SectionTone.Caution -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
    }

    Surface(
        shape = shapes.card,
        color = containerColor,
    ) {
        Column(modifier = Modifier.padding(dimens.cardPadding)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
            ) {
                Surface(
                    shape = shapes.full,
                    color = iconBg,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = section.icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = iconTint,
                        )
                    }
                }
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(dimens.spacingXs))
            Text(
                text = section.body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
            )
        }
    }
}
