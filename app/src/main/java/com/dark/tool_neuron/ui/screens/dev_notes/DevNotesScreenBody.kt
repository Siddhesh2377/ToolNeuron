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
    "ToolNeuron runs the AI on your phone. The model, your chats, your documents, your " +
            "voice models — everything stays on the device. You pick what to use, the rest " +
            "stays out of the way. This page is the short tour of what's here."

private val SECTIONS = listOf(
    DevNotesSection(
        title = "Nothing leaves the phone",
        icon = TnIcons.Lock,
        body = "Chats, files, voice models, plugins, and settings all live on your device. " +
                "No analytics, no cloud sync, no telemetry. The master key that unlocks your " +
                "data sits in your phone's secure chip, so the files on their own are just noise."
    ),
    DevNotesSection(
        title = "Chat with a model on your phone",
        icon = TnIcons.Cpu,
        body = "Pick a model from the Store and download it once. After that it runs offline. " +
                "Smaller models are quick and easy on the battery. Bigger ones write better " +
                "answers but take longer to think. Try a couple and see what fits."
    ),
    DevNotesSection(
        title = "Talk to it, hear it back",
        icon = TnIcons.Mic,
        body = "Tap the mic to speak instead of type. Tap the speaker on a reply to hear it " +
                "read aloud. The voice models are also local, so this still works on a plane."
    ),
    DevNotesSection(
        title = "Ask questions about your documents",
        icon = TnIcons.FileText,
        body = "Attach a PDF, a text file, or one of the common doc formats. The app reads " +
                "through it locally and pulls the relevant bits into the conversation. Replies " +
                "come with chips you can tap to see exactly which passage the answer drew from."
    ),
    DevNotesSection(
        title = "Show it pictures",
        icon = TnIcons.Eye,
        body = "Load a vision model and the image button on the input bar wakes up. Attach a " +
                "photo and ask about it. Without a vision model loaded the button stays dim — " +
                "that's the hint to grab one from the Store."
    ),
    DevNotesSection(
        title = "Look things up with Research",
        icon = TnIcons.Compass,
        body = "Type /research followed by your question, or tap the compass on the input bar. " +
                "The app searches, reads the top hits, summarises each one, then asks the " +
                "model to write a structured document with citations. Search and fetch need a " +
                "network. Reading and writing happen on your phone."
    ),
    DevNotesSection(
        title = "Generate images on the device",
        icon = TnIcons.Photo,
        body = "The Images screen runs Stable Diffusion locally. On a Snapdragon NPU you'll get " +
                "real speed; on other phones it falls back to MNN on CPU/GPU. First use does a " +
                "one-time runtime extract; after that it's prompt-in, image-out, no cloud calls."
    ),
    DevNotesSection(
        title = "Plugins — mini-apps inside the app",
        icon = TnIcons.Puzzle,
        body = "Open Plugins from the drawer to browse the public catalog on HuggingFace. Tap " +
                "Install, the zip downloads and runs inside ToolNeuron with its own screen and " +
                "encrypted storage. First-party plugins so far: Notes, Counter, Expense Tracker " +
                "(uses a tiny ONNX model to auto-categorise spends). Each plugin's permissions " +
                "are listed up front."
    ),
    DevNotesSection(
        title = "Share a chat",
        icon = TnIcons.Download,
        body = "Long-press a chat in the drawer and pick Share / Export. You choose between " +
                "Markdown (keeps the formatting) and Plain text (strips code fences, LaTeX, " +
                "and styling for a clean copy). The exported file goes through Android's share " +
                "sheet — pick anywhere."
    ),
    DevNotesSection(
        title = "Hand it to your other devices",
        icon = TnIcons.Server,
        body = "Flip on the local server and the loaded model is reachable from a browser on " +
                "your home Wi-Fi. A small offline web UI is bundled, so you can chat from a " +
                "laptop with nothing to install. A bearer token gates access; rotate it any time."
    ),
    DevNotesSection(
        title = "Rough edges",
        icon = TnIcons.AlertTriangle,
        tone = SectionTone.Caution,
        body = "Downloads run in the background and show progress in the notification shade. " +
                "The local server speaks plain HTTP — only run it on networks you trust. " +
                "Plugins run inside the host process; the capability badges on each plugin are " +
                "the boundary, not an OS sandbox."
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
