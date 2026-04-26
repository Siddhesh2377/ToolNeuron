package com.dark.tool_neuron.ui.screens.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes

@Composable
fun GuideVoiceScreen(innerPadding: PaddingValues) {
    GuideDetailLayout(
        innerPadding = innerPadding,
        icon = TnIcons.Mic,
        lede = "Speak your message with on-device STT, and have replies read back via streaming on-device TTS. Both run inside the inference service — no network traffic.",
        steps = listOf(
            GuideStep(
                title = "Install voice models from the Store",
                body = "Voice models are Store-only — no SAF / directory import. Filter by the tts or stt chips and tap Download. Archives ship as .tar.bz2; the app extracts them with per-file progress and auto-builds the sherpa config. The first install of each kind becomes the active voice automatically.",
                visual = { ExtractProgressVisual() },
            ),
            GuideStep(
                title = "STT options",
                body = "Speech-to-text uses Whisper-compatible models. Whisper Tiny works fine on phones; Tiny multilingual covers most languages. STT auto-unloads after each transcription so it doesn't sit on ~75 MB of RAM.",
                visual = { SttModelCardVisual() },
            ),
            GuideStep(
                title = "TTS options",
                body = "Streaming TTS uses VITS / Kokoro voices. The app chunks your text at sentence boundaries so the first chunk plays while the rest synthesize in the background. Sample rate is auto-detected per model.",
                visual = { TtsModelCardVisual() },
            ),
            GuideStep(
                title = "Tap the mic to dictate",
                body = "The mic button is always visible in the bottom bar. If no STT is installed, tapping it routes to the Store. On first tap it asks for mic permission and starts recording immediately on grant. The input bar morphs into a recording equalizer with cancel and stop.",
                visual = { MicVisual() },
            ),
            GuideStep(
                title = "Hear replies stream in",
                body = "On any assistant bubble, tap the speak icon. A spinner shows while the active TTS model loads (on demand), then audio starts almost immediately. Tap again to stop mid-sentence.",
                visual = { SpeakerBubbleVisual() },
            ),
        ),
        tips = listOf(
            "Whisper models are multi-lingual — you don't need a separate one per language.",
            "TTS quality depends heavily on the model; bigger isn't always better.",
            "Mic permission is requested on first tap and never asked for foreground-mic — the app only records while the UI is in the foreground.",
            "Delete a voice model from the Installed tab; the per-archive folder is wiped recursively.",
        ),
    )
}

@Composable
private fun ExtractProgressVisual() {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingXs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Extracting whisper-tiny.tar.bz2",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "12 / 18 files",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Box(modifier = Modifier.fillMaxWidth()) {
            Surface(
                shape = shapes.full,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
            ) {}
            Surface(
                shape = shapes.full,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth(0.66f)
                    .height(6.dp),
            ) {}
        }
    }
}

@Composable
private fun SttModelCardVisual() {
    ModelTagCard(title = "whisper-small", tag = "stt", icon = TnIcons.Mic)
}

@Composable
private fun TtsModelCardVisual() {
    ModelTagCard(title = "vits-en-ljspeech", tag = "tts", icon = TnIcons.Volume)
}

@Composable
private fun ModelTagCard(title: String, tag: String, icon: ImageVector) {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current
    Surface(
        shape = shapes.card,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier.padding(dimens.spacingMd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingMd),
        ) {
            Surface(
                shape = shapes.full,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Surface(
                shape = shapes.full,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            ) {
                Text(
                    text = tag,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(
                        horizontal = dimens.spacingSm,
                        vertical = dimens.spacingXxs,
                    ),
                )
            }
        }
    }
}

@Composable
private fun MicVisual() {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current
    val waveColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = shapes.full,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            modifier = Modifier.size(56.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .drawBehind {
                            val cx = size.width / 2
                            val cy = size.height / 2
                            val r1 = size.width / 2 + dimens.spacingSm.toPx()
                            drawArc(
                                color = waveColor,
                                startAngle = -60f,
                                sweepAngle = 120f,
                                useCenter = false,
                                topLeft = Offset(cx - r1, cy - r1),
                                size = Size(r1 * 2, r1 * 2),
                                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round),
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = TnIcons.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeakerBubbleVisual() {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = shapes.cardSmall,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        ) {
            Text(
                text = "Hi there, how can I help?",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    horizontal = dimens.spacingSm,
                    vertical = dimens.spacingXs,
                ),
            )
        }
        Surface(
            shape = shapes.full,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            modifier = Modifier.size(32.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = TnIcons.Volume,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
