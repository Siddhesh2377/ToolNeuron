package com.dark.tool_neuron.ui.screens.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes

@Composable
fun GuideResearchScreen(innerPadding: PaddingValues) {
    GuideDetailLayout(
        innerPadding = innerPadding,
        icon = TnIcons.Compass,
        lede = "Research runs a multi-iteration loop: it searches the web, fetches pages, compresses each into facts, generates follow-up questions, and finally writes a structured document. Two ways to start one — both produce the same run.",
        steps = listOf(
            GuideStep(
                title = "Method 1 — slash command",
                body = "Type \"/research <your question>\" as a normal message and send. The slash is detected before the message goes to the chat model; instead it spawns a research run for that question.",
                visual = { SlashCommandVisual() },
            ),
            GuideStep(
                title = "Method 2 — toggle button",
                body = "Tap the compass icon in the input bar (next to the mic). It glows when armed. Type your question normally and send — the toggle routes it as a research run, then resets.",
                visual = { ToggleButtonVisual() },
            ),
            GuideStep(
                title = "Watch each iteration",
                body = "A research card appears in the chat. It shows the current phase — Search, Fetch, Compress, Generating questions, Writing — with a per-iteration progress bar and a Stop button.",
                visual = { ResearchCardVisual() },
            ),
            GuideStep(
                title = "Inspect what each loop did",
                body = "Tap \"Show iterations\" on the card to expand a timeline. Every iteration has its own block: the search query, fetched URLs (✓ ok / ✗ failed), compressed byte ratio, and the follow-up questions the model generated.",
                visual = { TimelineVisual() },
            ),
            GuideStep(
                title = "Open the final document",
                body = "When the run completes, the card flips to Done with the document title, summary, and an Open button. Tapping it opens a read-only viewer with sections and source citations.",
            ),
            GuideStep(
                title = "Stop or pause",
                body = "Stop ends the run immediately, persisting partial progress as Cancelled. Backgrounding the app also auto-cancels by default — toggle this in Settings → Research.",
                visual = { StopButtonVisual() },
            ),
        ),
        tips = listOf(
            "Only one research run at a time. Chat send, model load/unload, and starting another research are all blocked while a run is in flight.",
            "The chat LLM is borrowed for the run — bigger models give better summaries but slower iterations.",
            "Sources are real — every fact in the final document maps to a URL in the citations list.",
            "Settings → Research lets you tune max iterations (1–10), follow-up questions per iter (1–6), and DDG results per search (3–10).",
        ),
    )
}

@Composable
private fun SlashCommandVisual() {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current
    Surface(
        shape = shapes.full,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = dimens.spacingMd,
                vertical = dimens.spacingSm,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            Surface(
                shape = shapes.full,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            ) {
                Text(
                    text = "/research",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(
                        horizontal = dimens.spacingSm,
                        vertical = dimens.spacingXxs,
                    ),
                )
            }
            Text(
                text = "what's new in Linux this week",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ToggleButtonVisual() {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
    ) {
        Surface(
            shape = shapes.full,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(36.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = TnIcons.Compass,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Column {
            Text(
                text = "Research",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "armed — next message starts a run",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            )
        }
    }
}

@Composable
private fun ResearchCardVisual() {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current
    Surface(
        shape = shapes.lg,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(dimens.spacingMd)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = shapes.md,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    modifier = Modifier.size(24.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = TnIcons.Sparkles,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Spacer4(dimens.spacingSm)
                Column {
                    Text(
                        text = "Research",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Searching DuckDuckGo…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }
            Spacer4(dimens.spacingSm)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Iter 2/5",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer4(dimens.spacingSm)
                Surface(
                    shape = shapes.full,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    modifier = Modifier
                        .weight(1f)
                        .size(width = 0.dp, height = 4.dp),
                ) {}
                Spacer4(dimens.spacingSm)
                Surface(
                    shape = shapes.full,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = dimens.spacingSm,
                            vertical = dimens.spacingXxs,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(dimens.spacingXxs),
                    ) {
                        Icon(
                            imageVector = TnIcons.PlayerStop,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = "Stop",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineVisual() {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingXs),
    ) {
        listOf(
            Triple("Iteration 1", "linux news", 5),
            Triple("Iteration 2", "linux kernel 6.13", 4),
        ).forEach { (label, q, n) ->
            Surface(
                shape = shapes.md,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(dimens.spacingMd)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer4(dimens.spacingXxs)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = TnIcons.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(11.dp),
                        )
                        Spacer4(dimens.spacingXs)
                        Text(
                            text = "“$q”",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = TnIcons.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(11.dp),
                        )
                        Spacer4(dimens.spacingXs)
                        Text(
                            text = "$n/$n fetched · 410KB → 2.1KB",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StopButtonVisual() {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current
    Surface(
        shape = shapes.md,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth(),
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
                imageVector = TnIcons.PlayerStop,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = "Stopping research…",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun Spacer4(width: androidx.compose.ui.unit.Dp) {
    androidx.compose.foundation.layout.Spacer(Modifier.width(width))
}
