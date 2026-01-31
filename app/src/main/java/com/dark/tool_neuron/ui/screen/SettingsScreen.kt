package com.dark.tool_neuron.ui.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.tool_neuron.global.Standards
import com.dark.tool_neuron.service.ModelDownloadService
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.ActionTextButton
import com.dark.tool_neuron.ui.components.ActionToggleGroup
import com.dark.tool_neuron.ui.components.BodyLabel
import com.dark.tool_neuron.ui.components.CaptionText
import com.dark.tool_neuron.ui.components.SectionDivider
import com.dark.tool_neuron.ui.components.SectionHeader
import com.dark.tool_neuron.ui.components.StandardCard
import com.dark.tool_neuron.ui.components.SwitchRow
import com.dark.tool_neuron.ui.theme.rDp
import com.dark.tool_neuron.viewmodel.SettingsViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onModelEditor: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel()
) {
    // App settings
    val streamingEnabled by viewModel.streamingEnabled.collectAsStateWithLifecycle()
    val chatMemoryEnabled by viewModel.chatMemoryEnabled.collectAsStateWithLifecycle()
    val toolCallingEnabled by viewModel.toolCallingEnabled.collectAsStateWithLifecycle()
    val imageBlurEnabled by viewModel.imageBlurEnabled.collectAsStateWithLifecycle()
    val loadTTSOnStart by viewModel.loadTTSOnStart.collectAsStateWithLifecycle()

    // Installed models
    val installedModels by viewModel.installedModels.collectAsStateWithLifecycle(initialValue = emptyList())

    // TTS settings
    val ttsSettings by viewModel.ttsSettings.collectAsStateWithLifecycle()
    val ttsModelLoaded by viewModel.ttsModelLoaded.collectAsStateWithLifecycle()
    val ttsVoices by viewModel.ttsAvailableVoices.collectAsStateWithLifecycle()
    val hasTtsModel by viewModel.hasTtsModel.collectAsStateWithLifecycle()
    val ttsDownloadStates by viewModel.ttsDownloadStates.collectAsStateWithLifecycle()
    val ttsDownloadState = ttsDownloadStates["supertonic-v2-tts"]

    // Auto-load TTS after download succeeds
    LaunchedEffect(ttsDownloadState) {
        if (ttsDownloadState is ModelDownloadService.DownloadState.Success) {
            viewModel.loadTtsAfterDownload()
        }
    }

    val voices = ttsVoices.ifEmpty {
        listOf("F1", "F2", "F3", "F4", "F5", "M1", "M2", "M3", "M4", "M5")
    }

    val languages = listOf("en" to "EN", "ko" to "KO", "es" to "ES", "pt" to "PT", "fr" to "FR")

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    ActionButton(
                        onClickListener = onNavigateBack,
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = rDp(Standards.SpacingLg)),
            verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingSm))
        ) {
            // ==================== General ====================
            item { SectionHeader(title = "General") }

            item {
                SwitchRow(
                    title = "Tool Calling",
                    description = "Enable tool/plugin calling for supported models",
                    checked = toolCallingEnabled,
                    onCheckedChange = { viewModel.setToolCallingEnabled(it) }
                )
            }

            // ==================== LLM ====================
            item { Spacer(Modifier.height(rDp(Standards.SpacingSm))) }
            item { SectionDivider() }
            item { SectionHeader(title = "LLM") }

            item {
                SwitchRow(
                    title = "Streaming Response",
                    description = "Stream tokens as they generate in real-time",
                    checked = streamingEnabled,
                    onCheckedChange = { viewModel.setStreamingEnabled(it) }
                )
            }

            item {
                SwitchRow(
                    title = "Chat Memory",
                    description = "Remember previous messages in conversation (faster without)",
                    checked = chatMemoryEnabled,
                    onCheckedChange = { viewModel.setChatMemoryEnabled(it) }
                )
            }

            // ==================== Model Configuration ====================
            item { Spacer(Modifier.height(rDp(Standards.SpacingSm))) }
            item { SectionDivider() }
            item {
                SectionHeader(title = "Model Configuration") {
                    ActionTextButton(
                        onClickListener = onModelEditor,
                        icon = Icons.Default.Psychology,
                        text = "Configure",
                        shape = RoundedCornerShape(rDp(Standards.CardSmallCornerRadius))
                    )
                }
            }

            if (installedModels.isEmpty()) {
                item {
                    StandardCard(
                        description = "No models installed. Download models from the store."
                    )
                }
            } else {
                items(installedModels.size) { index ->
                    val model = installedModels[index]
                    StandardCard(
                        title = model.modelName,
                        description = model.providerType.name,
                        icon = Icons.Default.Psychology,
                        onClick = onModelEditor
                    )
                }
            }

            // ==================== TTS ====================
            item { Spacer(Modifier.height(rDp(Standards.SpacingSm))) }
            item { SectionDivider() }
            item { SectionHeader(title = "Text-to-Speech") }

            // Download TTS card — only visible when no TTS model is installed
            if (!hasTtsModel) {
                item {
                    StandardCard(title = "Download TTS") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize(spring()),
                            verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingSm))
                        ) {
                            CaptionText(text = "Supertonic v2 · ~263 MB")

                            when (ttsDownloadState) {
                                is ModelDownloadService.DownloadState.Downloading -> {
                                    val progress = ttsDownloadState.progress
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        LinearProgressIndicator(
                                            progress = { progress },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(rDp(6.dp))
                                                .clip(RoundedCornerShape(rDp(3.dp))),
                                            color = MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                        )
                                        Spacer(Modifier.width(rDp(12.dp)))
                                        Text(
                                            "${(progress * 100).toInt()}%",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                is ModelDownloadService.DownloadState.Extracting,
                                is ModelDownloadService.DownloadState.Processing -> {
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(rDp(6.dp))
                                            .clip(RoundedCornerShape(rDp(3.dp))),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                    )
                                }

                                is ModelDownloadService.DownloadState.Success -> {
                                    CaptionText(text = "Downloaded — loading model...")
                                }

                                is ModelDownloadService.DownloadState.Error -> {
                                    Text(
                                        text = ttsDownloadState.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    FilledTonalButton(onClick = { viewModel.downloadTts() }) {
                                        Text("Retry")
                                    }
                                }

                                else -> {
                                    FilledTonalButton(onClick = { viewModel.downloadTts() }) {
                                        Icon(
                                            Icons.Default.Download,
                                            contentDescription = null,
                                            modifier = Modifier.size(rDp(18.dp))
                                        )
                                        Spacer(Modifier.width(rDp(8.dp)))
                                        Text("Download")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                SwitchRow(
                    title = "Load TTS on App Start",
                    description = "Auto-load TTS model when app launches",
                    checked = loadTTSOnStart,
                    onCheckedChange = { viewModel.setLoadTTSOnStart(it) }
                )
            }

            // Voice picker
            item {
                StandardCard(title = "Voice") {
                    Column(verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingSm))) {
                        val femaleVoices = voices.filter { it.startsWith("F") }
                        val maleVoices = voices.filter { it.startsWith("M") }

                        if (femaleVoices.isNotEmpty()) {
                            CaptionText(text = "Female")
                            ActionToggleGroup(
                                items = femaleVoices,
                                selectedItem = ttsSettings.voice,
                                onItemSelected = { viewModel.updateVoice(it) },
                                itemLabel = { it },
                                enabled = ttsModelLoaded
                            )
                        }
                        if (maleVoices.isNotEmpty()) {
                            CaptionText(text = "Male")
                            ActionToggleGroup(
                                items = maleVoices,
                                selectedItem = ttsSettings.voice,
                                onItemSelected = { viewModel.updateVoice(it) },
                                itemLabel = { it },
                                enabled = ttsModelLoaded
                            )
                        }
                    }
                }
            }

            // Language selector
            item {
                StandardCard(title = "Language") {
                    ActionToggleGroup(
                        items = languages.map { it.first },
                        selectedItem = ttsSettings.language,
                        onItemSelected = { viewModel.updateLanguage(it) },
                        itemLabel = { code -> languages.first { it.first == code }.second },
                        enabled = ttsModelLoaded
                    )
                }
            }

            // Speed slider
            item {
                StandardCard(title = "Speed") {
                    Column(verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingXs))) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CaptionText(text = "Playback speed")
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(rDp(4.dp))
                            ) {
                                Text(
                                    text = "${"%.2f".format(ttsSettings.speed)}x",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = rDp(8.dp), vertical = rDp(2.dp))
                                )
                            }
                        }

                        Slider(
                            value = ttsSettings.speed,
                            onValueChange = { viewModel.updateSpeed((it * 20).roundToInt() / 20f) },
                            valueRange = 0.5f..2.0f,
                            enabled = ttsModelLoaded,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            CaptionText(text = "0.5x")
                            CaptionText(text = "1.0x")
                            CaptionText(text = "2.0x")
                        }
                    }
                }
            }

            // Steps slider
            item {
                StandardCard(title = "Denoising Steps") {
                    Column(verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingXs))) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CaptionText(text = "Higher = better quality, slower")
                            Surface(
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(rDp(4.dp))
                            ) {
                                Text(
                                    text = "${ttsSettings.steps}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.padding(horizontal = rDp(8.dp), vertical = rDp(2.dp))
                                )
                            }
                        }

                        Slider(
                            value = ttsSettings.steps.toFloat(),
                            onValueChange = { viewModel.updateSteps(it.roundToInt()) },
                            valueRange = 1f..8f,
                            steps = 6,
                            enabled = ttsModelLoaded,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.tertiary,
                                activeTrackColor = MaterialTheme.colorScheme.tertiary
                            )
                        )
                    }
                }
            }

            // Auto-speak
            item {
                SwitchRow(
                    title = "Auto-speak",
                    description = "Automatically speak assistant responses",
                    checked = ttsSettings.autoSpeak,
                    onCheckedChange = { viewModel.updateAutoSpeak(it) },
                    enabled = ttsModelLoaded
                )
            }

            // NNAPI
            item {
                SwitchRow(
                    title = "Use NNAPI",
                    description = "Hardware acceleration (may not work on all devices)",
                    checked = ttsSettings.useNNAPI,
                    onCheckedChange = { viewModel.updateUseNNAPI(it) },
                    enabled = ttsModelLoaded
                )
            }

            // ==================== Image Generation ====================
            item { Spacer(Modifier.height(rDp(Standards.SpacingSm))) }
            item { SectionDivider() }
            item { SectionHeader(title = "Image Generation") }

            item {
                SwitchRow(
                    title = "Blur Generated Images",
                    description = "Blur images by default, tap to reveal",
                    checked = imageBlurEnabled,
                    onCheckedChange = { viewModel.setImageBlurEnabled(it) }
                )
            }

            // ==================== About ====================
            item { Spacer(Modifier.height(rDp(Standards.SpacingSm))) }
            item { SectionDivider() }
            item { SectionHeader(title = "About") }

            item {
                StandardCard(
                    title = "ToolNeuron",
                    description = "On-device AI — LLM, Image Generation, TTS"
                ) {
                    BodyLabel(
                        text = "Version ${viewModel.appVersion}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item { Spacer(Modifier.height(rDp(Standards.SpacingXl))) }
        }
    }
}
