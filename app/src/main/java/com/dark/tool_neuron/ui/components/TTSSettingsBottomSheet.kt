package com.dark.tool_neuron.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.tts.TTSSettings
import kotlin.math.roundToInt
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.global.Standards

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TTSSettingsBottomSheet(
    show: Boolean,
    settings: TTSSettings,
    isModelLoaded: Boolean,
    availableVoices: List<String>,
    onDismiss: () -> Unit,
    onVoiceChange: (String) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onStepsChange: (Int) -> Unit,
    onLanguageChange: (String) -> Unit,
    onAutoSpeakToggle: (Boolean) -> Unit,
    onUseNNAPIToggle: (Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (show) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = {
                Box(
                    Modifier
                        .padding(vertical = Standards.SpacingMd)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 600.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = Standards.SpacingLg)
            ) {
                // Header
                TTSSettingsHeader(isModelLoaded = isModelLoaded)

                Spacer(modifier = Modifier.height(Standards.SpacingMd))

                // Model Status
                TTSModelStatus(isModelLoaded = isModelLoaded)

                if (isModelLoaded) {
                    Spacer(modifier = Modifier.height(Standards.SpacingMd))

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = Standards.SpacingLg),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    Spacer(modifier = Modifier.height(Standards.SpacingMd))

                    // Voice Selection
                    VoiceSelectionSection(
                        selectedVoice = settings.voice,
                        availableVoices = availableVoices,
                        onVoiceChange = onVoiceChange
                    )

                    Spacer(modifier = Modifier.height(Standards.SpacingLg))

                    // Language Selection
                    LanguageSelectionSection(
                        selectedLanguage = settings.language,
                        onLanguageChange = onLanguageChange
                    )

                    Spacer(modifier = Modifier.height(Standards.SpacingLg))

                    // Speed Slider
                    SpeedSection(
                        speed = settings.speed,
                        onSpeedChange = onSpeedChange
                    )

                    Spacer(modifier = Modifier.height(Standards.SpacingLg))

                    // Steps Slider
                    StepsSection(
                        steps = settings.steps,
                        onStepsChange = onStepsChange
                    )

                    Spacer(modifier = Modifier.height(Standards.SpacingLg))

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = Standards.SpacingLg),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    Spacer(modifier = Modifier.height(Standards.SpacingMd))

                    // Toggles
                    ToggleSection(
                        autoSpeak = settings.autoSpeak,
                        useNNAPI = settings.useNNAPI,
                        onAutoSpeakToggle = onAutoSpeakToggle,
                        onUseNNAPIToggle = onUseNNAPIToggle
                    )
                }

                Spacer(modifier = Modifier.height(Standards.SpacingLg))
            }
        }
    }
}

@Composable
private fun TTSSettingsHeader(isModelLoaded: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Standards.SpacingLg),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "TTS Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (isModelLoaded) "Supertonic v2 loaded" else "No TTS model loaded",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            imageVector = TnIcons.Volume,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = if (isModelLoaded) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun TTSModelStatus(isModelLoaded: Boolean) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Standards.SpacingLg),
        shape = RoundedCornerShape(10.dp),
        color = if (isModelLoaded) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Standards.SpacingMd),
            horizontalArrangement = Arrangement.spacedBy(Standards.SpacingMd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = TnIcons.CircleCheck,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (isModelLoaded) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.error
            )
            Column {
                Text(
                    text = if (isModelLoaded) "Model Ready" else "Model Not Loaded",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isModelLoaded) "5 languages, 10 voices available"
                           else "Download TTS model from the Model Store",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoiceSelectionSection(
    selectedVoice: String,
    availableVoices: List<String>,
    onVoiceChange: (String) -> Unit
) {
    val voices = availableVoices.ifEmpty {
        listOf("F1", "F2", "F3", "F4", "F5", "M1", "M2", "M3", "M4", "M5")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Standards.SpacingLg),
        verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
    ) {
        Text(
            text = "Voice",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm),
            verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
        ) {
            voices.forEach { voice ->
                FilterChip(
                    selected = selectedVoice == voice,
                    onClick = { onVoiceChange(voice) },
                    label = {
                        Text(
                            text = voice,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selectedVoice == voice) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        Text(
            text = when {
                selectedVoice.startsWith("F") -> "Female voice ${selectedVoice.last()}"
                selectedVoice.startsWith("M") -> "Male voice ${selectedVoice.last()}"
                else -> selectedVoice
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageSelectionSection(
    selectedLanguage: String,
    onLanguageChange: (String) -> Unit
) {
    val languages = listOf(
        "en" to "English",
        "ko" to "Korean",
        "es" to "Spanish",
        "pt" to "Portuguese",
        "fr" to "French"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Standards.SpacingLg),
        verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
    ) {
        Text(
            text = "Language",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm),
            verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
        ) {
            languages.forEach { (code, name) ->
                FilterChip(
                    selected = selectedLanguage == code,
                    onClick = { onLanguageChange(code) },
                    label = {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selectedLanguage == code) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )
            }
        }
    }
}

@Composable
private fun SpeedSection(
    speed: Float,
    onSpeedChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Standards.SpacingLg),
        verticalArrangement = Arrangement.spacedBy(Standards.SpacingXs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Speed",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                shape = RoundedCornerShape(Standards.SpacingXs)
            ) {
                Text(
                    text = "${"%.2f".format(speed)}x",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = Standards.SpacingSm, vertical = Standards.SpacingXxs)
                )
            }
        }

        Slider(
            value = speed,
            onValueChange = { onSpeedChange((it * 20).roundToInt() / 20f) },
            valueRange = 0.5f..2.0f,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("0.5x", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("1.0x", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("2.0x", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StepsSection(
    steps: Int,
    onStepsChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Standards.SpacingLg),
        verticalArrangement = Arrangement.spacedBy(Standards.SpacingXs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Denoising Steps",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Higher = better quality, slower",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                shape = RoundedCornerShape(Standards.SpacingXs)
            ) {
                Text(
                    text = "$steps",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(horizontal = Standards.SpacingSm, vertical = Standards.SpacingXxs)
                )
            }
        }

        Slider(
            value = steps.toFloat(),
            onValueChange = { onStepsChange(it.roundToInt()) },
            valueRange = 1f..8f,
            steps = 6,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.tertiary,
                activeTrackColor = MaterialTheme.colorScheme.tertiary
            )
        )
    }
}

@Composable
private fun ToggleSection(
    autoSpeak: Boolean,
    useNNAPI: Boolean,
    onAutoSpeakToggle: (Boolean) -> Unit,
    onUseNNAPIToggle: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Standards.SpacingLg),
        verticalArrangement = Arrangement.spacedBy(Standards.SpacingMd)
    ) {
        // Auto-speak toggle
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Standards.SpacingMd),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-speak",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Automatically speak assistant responses",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = autoSpeak,
                    onCheckedChange = onAutoSpeakToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }

        // NNAPI toggle
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Standards.SpacingMd),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Use NNAPI",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Hardware acceleration (may not work on all devices)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = useNNAPI,
                    onCheckedChange = onUseNNAPIToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.tertiary,
                        checkedTrackColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                )
            }
        }
    }
}
