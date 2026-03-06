package com.dark.tool_neuron.ui.screen.settings

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import com.dark.tool_neuron.ui.theme.Motion
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.tool_neuron.global.Standards
import com.dark.tool_neuron.global.formatBackupTimestamp
import com.dark.tool_neuron.global.formatBytes
import com.dark.tool_neuron.plugins.PluginManager
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
import com.dark.tool_neuron.viewmodel.SettingsViewModel
import com.dark.tool_neuron.worker.SystemBackupManager
import kotlin.math.roundToInt
import com.dark.tool_neuron.ui.components.PasswordTextField
import com.dark.tool_neuron.ui.icons.TnIcons

// ── Constants ──

private val DEFAULT_VOICES = listOf("F1", "F2", "F3", "F4", "F5", "M1", "M2", "M3", "M4", "M5")
private val SUPPORTED_LANGUAGES = listOf("en" to "EN", "ko" to "KO", "es" to "ES", "pt" to "PT", "fr" to "FR")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onModelEditor: () -> Unit = {},
    onAiMemoryClick: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel()
) {
    // App settings
    val streamingEnabled by viewModel.streamingEnabled.collectAsStateWithLifecycle()
    val chatMemoryEnabled by viewModel.chatMemoryEnabled.collectAsStateWithLifecycle()
    val toolCallingEnabled by viewModel.toolCallingEnabled.collectAsStateWithLifecycle()
    val toolCallingBypassEnabled by viewModel.toolCallingBypassEnabled.collectAsStateWithLifecycle()
    val imageBlurEnabled by viewModel.imageBlurEnabled.collectAsStateWithLifecycle()
    val loadTTSOnStart by viewModel.loadTTSOnStart.collectAsStateWithLifecycle()
    val codeHighlightEnabled by viewModel.codeHighlightEnabled.collectAsStateWithLifecycle()
    val aiMemoryEnabled by viewModel.aiMemoryEnabled.collectAsStateWithLifecycle()
    val askModelReloadDialog by viewModel.askModelReloadDialog.collectAsStateWithLifecycle()
    val hardwareTuningEnabled by viewModel.hardwareTuningEnabled.collectAsStateWithLifecycle()
    val hardwareProfile by viewModel.hardwareProfile.collectAsStateWithLifecycle()
    val performanceMode by viewModel.performanceMode.collectAsStateWithLifecycle()
    // Installed models
    val installedModels by viewModel.installedModels.collectAsStateWithLifecycle(initialValue = emptyList())

    // Tool calling model state
    val hasToolCallingModel by viewModel.hasToolCallingModel.collectAsStateWithLifecycle()
    val toolCallingDownloadStates by viewModel.toolCallingModelDownloadState.collectAsStateWithLifecycle()
    val toolCallingDownloadState = toolCallingDownloadStates[PluginManager.TOOL_CALLING_MODEL_ID]


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

    val voices = ttsVoices.ifEmpty { DEFAULT_VOICES }

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
                        icon = TnIcons.ArrowLeft,
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
            contentPadding = PaddingValues(horizontal = Standards.SpacingMd),
            verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
        ) {
            // ==================== General ====================
            item { SectionHeader(title = "General") }

            item {
                val canEnableToolCalling = hasToolCallingModel || toolCallingBypassEnabled
                SwitchRow(
                    title = "Tool Calling",
                    description = when {
                        toolCallingBypassEnabled -> "Bypass enabled — tool calling available for all models"
                        hasToolCallingModel -> "Any model with a chat template can use tools"
                        else -> "Install a GGUF model to enable tool calling"
                    },
                    checked = toolCallingEnabled && canEnableToolCalling,
                    onCheckedChange = { viewModel.setToolCallingEnabled(it) },
                    enabled = canEnableToolCalling
                )
            }

            // Download recommended tool calling model card
            if (!hasToolCallingModel) {
                item {
                    ModelDownloadCard(
                        title = "Recommended Tool Calling Model",
                        description = "Ruvltra Claude Code 0.5B · ~400 MB\nCompact model optimized for tool calling",
                        downloadState = toolCallingDownloadState,
                        onDownload = { viewModel.downloadToolCallingModel() }
                    )
                }
            }

            // Bypass tool calling model check — red warning card
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Standards.CardCornerRadius),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Standards.SpacingMd),
                        verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
                        ) {
                            Icon(
                                TnIcons.AlertTriangle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Bypass Model Check",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Text(
                            text = "Force tool calling on models without a chat template. May cause errors or unexpected output.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        SwitchRow(
                            title = "Enable Bypass",
                            description = if (toolCallingBypassEnabled) "Tool calling forced for all models" else "Only models with chat templates can use tools",
                            checked = toolCallingBypassEnabled,
                            onCheckedChange = { viewModel.setToolCallingBypassEnabled(it) },
                            titleColor = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // ==================== LLM ====================
            item { Spacer(Modifier.height(Standards.SpacingSm)) }
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

            item {
                SwitchRow(
                    title = "Ask to Reload Model",
                    description = "Show dialog on startup to reload last model. When off, auto-loads silently.",
                    checked = askModelReloadDialog,
                    onCheckedChange = { viewModel.setAskModelReloadDialog(it) }
                )
            }

            // ==================== Chat ====================
            item { Spacer(Modifier.height(Standards.SpacingSm)) }
            item { SectionDivider() }
            item { SectionHeader(title = "Chat") }

            item {
                SwitchRow(
                    title = "Code Syntax Highlighting",
                    description = "Colorize code blocks based on language (disable for faster scrolling)",
                    checked = codeHighlightEnabled,
                    onCheckedChange = { viewModel.setCodeHighlightEnabled(it) }
                )
            }

            // ==================== Hardware Tuning ====================
            item { Spacer(Modifier.height(Standards.SpacingSm)) }
            item { SectionDivider() }
            item { SectionHeader(title = "Hardware Tuning") }

            item {
                SwitchRow(
                    title = "Hardware-Based Tuning",
                    description = "Automatically optimize model parameters based on your device's hardware. Disable to set parameters manually.",
                    checked = hardwareTuningEnabled,
                    onCheckedChange = { viewModel.setHardwareTuningEnabled(it) }
                )
            }

            // ── Performance Mode ──
            item {
                Column {
                    Text(
                        text = "Performance Mode",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (hardwareTuningEnabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(Standards.SpacingSm))
                    ActionToggleGroup(
                        items = com.dark.tool_neuron.global.PerformanceMode.entries.toList(),
                        selectedItem = performanceMode,
                        onItemSelected = { viewModel.setPerformanceMode(it) },
                        itemLabel = { mode ->
                            when (mode) {
                                com.dark.tool_neuron.global.PerformanceMode.PERFORMANCE -> "Performance"
                                com.dark.tool_neuron.global.PerformanceMode.BALANCED -> "Balanced"
                                com.dark.tool_neuron.global.PerformanceMode.POWER_SAVING -> "Power Saver"
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = hardwareTuningEnabled
                    )
                    Spacer(Modifier.height(Standards.SpacingXs))
                    Text(
                        text = if (!hardwareTuningEnabled) {
                            "Enable hardware tuning to use performance presets"
                        } else when (performanceMode) {
                            com.dark.tool_neuron.global.PerformanceMode.PERFORMANCE -> "Uses all fast cores. Best speed, higher battery use."
                            com.dark.tool_neuron.global.PerformanceMode.BALANCED -> "Uses performance cores only. Good speed and battery balance."
                            com.dark.tool_neuron.global.PerformanceMode.POWER_SAVING -> "Minimal threads and memory. Best battery life."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            hardwareProfile?.let { profile ->
                item {
                    val topo = profile.cpuTopology
                    val coreInfo = if (topo.scanSucceeded) {
                        buildString {
                            if (topo.primeCoreCount > 0) append("${topo.primeCoreCount}P")
                            if (topo.performanceCoreCount > 0) {
                                if (isNotEmpty()) append("+")
                                append("${topo.performanceCoreCount}P")
                            }
                            if (topo.efficiencyCoreCount > 0) {
                                if (isNotEmpty()) append("+")
                                append("${topo.efficiencyCoreCount}E")
                            }
                            append(" cores")
                        }
                    } else {
                        "${profile.cpuCores} cores"
                    }

                    StandardCard(
                        title = "${profile.totalRamMB} MB RAM · $coreInfo · ${profile.cpuArch}",
                        description = profile.deviceModel
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            ActionTextButton(
                                onClickListener = { viewModel.rescanHardware() },
                                icon = TnIcons.Refresh,
                                text = "Rescan",
                                shape = RoundedCornerShape(Standards.CardSmallCornerRadius)
                            )
                        }
                    }
                }
            }

            // ==================== Model Configuration ====================
            item { Spacer(Modifier.height(Standards.SpacingSm)) }
            item { SectionDivider() }
            item {
                SectionHeader(title = "Model Configuration") {
                    ActionTextButton(
                        onClickListener = onModelEditor,
                        icon = TnIcons.Sparkles,
                        text = "Configure",
                        shape = RoundedCornerShape(Standards.CardSmallCornerRadius),
                        enabled = !hardwareTuningEnabled
                    )
                }
            }

            if (hardwareTuningEnabled) {
                item {
                    Text(
                        text = "Model parameters are managed by the performance engine. Disable hardware tuning to edit manually.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
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
                items(installedModels.size, key = { installedModels[it].id }) { index ->
                    val model = installedModels[index]
                    StandardCard(
                        title = model.modelName,
                        description = model.providerType.name,
                        icon = TnIcons.Sparkles,
                        onClick = if (!hardwareTuningEnabled) onModelEditor else ({})
                    )
                }
            }

            // ==================== AI Memory ====================
            item { Spacer(Modifier.height(Standards.SpacingSm)) }
            item { SectionDivider() }
            item { SectionHeader(title = "AI Memory") }

            item {
                SwitchRow(
                    title = "AI Memory",
                    description = "Remember facts about you across conversations",
                    checked = aiMemoryEnabled,
                    onCheckedChange = { viewModel.setAiMemoryEnabled(it) }
                )
            }

            item {
                Surface(
                    onClick = onAiMemoryClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "View Memories",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "See, search, and manage what the AI remembers about you",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ==================== TTS ====================
            item { Spacer(Modifier.height(Standards.SpacingSm)) }
            item { SectionDivider() }
            item { SectionHeader(title = "Text-to-Speech") }

            // Download TTS card — only visible when no TTS model is installed
            if (!hasTtsModel) {
                item {
                    ModelDownloadCard(
                        title = "Download TTS",
                        description = "Supertonic v2 · ~263 MB",
                        downloadState = ttsDownloadState,
                        onDownload = { viewModel.downloadTts() },
                        successText = "Downloaded — loading model..."
                    )
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
                    Column(verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)) {
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
                        items = SUPPORTED_LANGUAGES.map { it.first },
                        selectedItem = ttsSettings.language,
                        onItemSelected = { viewModel.updateLanguage(it) },
                        itemLabel = { code -> SUPPORTED_LANGUAGES.first { it.first == code }.second },
                        enabled = ttsModelLoaded
                    )
                }
            }

            // Speed slider
            item {
                StandardCard(title = "Speed") {
                    Column(verticalArrangement = Arrangement.spacedBy(Standards.SpacingXs)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CaptionText(text = "Playback speed")
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "${"%.2f".format(ttsSettings.speed)}x",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
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
                    Column(verticalArrangement = Arrangement.spacedBy(Standards.SpacingXs)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CaptionText(text = "Higher = better quality, slower")
                            Surface(
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "${ttsSettings.steps}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
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
            item { Spacer(Modifier.height(Standards.SpacingSm)) }
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

            // ==================== Data Management ====================
            item { Spacer(Modifier.height(Standards.SpacingSm)) }
            item { SectionDivider() }
            item { SectionHeader(title = "Data Management") }

            item {
                DataManagementSection(viewModel = viewModel)
            }

            // ==================== About ====================
            item { Spacer(Modifier.height(Standards.SpacingSm)) }
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

            item { Spacer(Modifier.height(Standards.SpacingXl)) }
        }
    }
}

// ==================== Reusable Download Card ====================

@Composable
private fun ModelDownloadCard(
    title: String,
    description: String,
    downloadState: ModelDownloadService.DownloadState?,
    onDownload: () -> Unit,
    successText: String = "Downloaded"
) {
    StandardCard(title = title) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(Motion.content()),
            verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
        ) {
            description.split("\n").forEach { line ->
                CaptionText(text = line)
            }

            when (downloadState) {
                is ModelDownloadService.DownloadState.Downloading -> {
                    val progress = downloadState.progress
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                        Spacer(Modifier.width(12.dp))
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
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                }

                is ModelDownloadService.DownloadState.Success -> {
                    CaptionText(text = successText)
                }

                is ModelDownloadService.DownloadState.Error -> {
                    Text(
                        text = downloadState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    FilledTonalButton(onClick = onDownload) {
                        Text("Retry")
                    }
                }

                else -> {
                    FilledTonalButton(onClick = onDownload) {
                        Icon(
                            TnIcons.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Download")
                    }
                }
            }
        }
    }
}

// ==================== Data Management Section ====================

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DataManagementSection(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val backupProgress by viewModel.backupProgress.collectAsStateWithLifecycle()
    val backupOptions by viewModel.backupOptions.collectAsStateWithLifecycle()
    val backupSizeEstimate by viewModel.backupSizeEstimate.collectAsStateWithLifecycle()

    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf("") }
    var backupPasswordConfirm by remember { mutableStateOf("") }
    var restorePassword by remember { mutableStateOf("") }
    var deleteConfirmText by remember { mutableStateOf("") }

    // SAF launchers
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null && backupPassword.isNotEmpty()) {
            viewModel.createBackup(uri, backupPassword)
            backupPassword = ""
            backupPasswordConfirm = ""
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && restorePassword.isNotEmpty()) {
            viewModel.restoreBackup(uri, restorePassword)
            restorePassword = ""
        }
    }

    // Auto-dismiss progress after completion, or restart after restore
    LaunchedEffect(backupProgress) {
        if (backupProgress is SystemBackupManager.BackupProgress.Complete) {
            if (showRestoreDialog) {
                // Restart process — Hilt singletons hold stale DB/DAO refs
                kotlinx.coroutines.delay(500)
                showRestoreDialog = false
                val activity = context as? Activity
                activity?.let {
                    val intent = it.packageManager.getLaunchIntentForPackage(it.packageName)
                        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    it.finishAffinity()
                    if (intent != null) it.startActivity(intent)
                    Runtime.getRuntime().exit(0)
                }
            } else {
                kotlinx.coroutines.delay(2000)
                viewModel.clearBackupProgress()
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)) {
        // Progress indicator
        AnimatedVisibility(
            visible = backupProgress != null && backupProgress !is SystemBackupManager.BackupProgress.Complete
                    && backupProgress !is SystemBackupManager.BackupProgress.Error,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                shape = RoundedCornerShape(Standards.CardCornerRadius)
            ) {
                Row(
                    modifier = Modifier.padding(Standards.CardPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
                ) {
                    LoadingIndicator(modifier = Modifier.size(20.dp))
                    Text(
                        text = when (val p = backupProgress) {
                            is SystemBackupManager.BackupProgress.Starting -> "Starting..."
                            is SystemBackupManager.BackupProgress.Collecting -> p.component
                            is SystemBackupManager.BackupProgress.Processing -> {
                                val stage = if (p.stage.isNotEmpty()) "${p.stage} " else ""
                                "${stage}${(p.progress * 100).toInt()}%"
                            }
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Status messages
        val progressStatus = backupProgress
        if (progressStatus is SystemBackupManager.BackupProgress.Complete) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(Standards.CardCornerRadius)
            ) {
                Text(
                    "Operation completed successfully",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(Standards.CardPadding)
                )
            }
        }
        if (progressStatus is SystemBackupManager.BackupProgress.Error) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                shape = RoundedCornerShape(Standards.CardCornerRadius)
            ) {
                Text(
                    progressStatus.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(Standards.CardPadding)
                )
            }
        }

        // --- Green Backup Card ---
        Surface(
            onClick = { showBackupDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Standards.CardCornerRadius),
            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier.padding(Standards.CardPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
            ) {
                Icon(
                    TnIcons.CloudUpload, null,
                    modifier = Modifier.size(Standards.IconLg),
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Backup",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        "Create encrypted backup of all app data",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // --- Restore Card ---
        Surface(
            onClick = { showRestoreDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Standards.CardCornerRadius),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        ) {
            Row(
                modifier = Modifier.padding(Standards.CardPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
            ) {
                Icon(
                    TnIcons.CloudDownload, null,
                    modifier = Modifier.size(Standards.IconLg),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Restore from Backup",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Restore from encrypted backup file",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // --- Red Delete All Card ---
        Surface(
            onClick = { showDeleteDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Standards.CardCornerRadius),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(Standards.CardPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
            ) {
                Icon(
                    TnIcons.TrashX, null,
                    modifier = Modifier.size(Standards.IconLg),
                    tint = MaterialTheme.colorScheme.error
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Delete All Data",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        "Permanently delete all app data",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }

    // ==================== Dialogs ====================

    if (showBackupDialog) {
        BackupDialog(
            backupPassword = backupPassword,
            onPasswordChange = { backupPassword = it },
            backupPasswordConfirm = backupPasswordConfirm,
            onPasswordConfirmChange = { backupPasswordConfirm = it },
            backupOptions = backupOptions,
            onOptionsChange = { viewModel.updateBackupOptions(it) },
            backupSizeEstimate = backupSizeEstimate,
            onEstimateSize = { viewModel.estimateBackupSize() },
            onConfirm = {
                showBackupDialog = false
                val timestamp = formatBackupTimestamp()
                backupLauncher.launch("toolneuron_backup_$timestamp.tnbackup")
            },
            onDismiss = {
                showBackupDialog = false
                backupPassword = ""
                backupPasswordConfirm = ""
            }
        )
    }

    if (showRestoreDialog) {
        RestoreDialog(
            restorePassword = restorePassword,
            onPasswordChange = { restorePassword = it },
            onConfirm = {
                restoreLauncher.launch(arrayOf("application/octet-stream", "*/*"))
            },
            onDismiss = {
                showRestoreDialog = false
                restorePassword = ""
            }
        )
    }

    if (showDeleteDialog) {
        DeleteAllDataDialog(
            deleteConfirmText = deleteConfirmText,
            onConfirmTextChange = { deleteConfirmText = it },
            onConfirm = {
                showDeleteDialog = false
                deleteConfirmText = ""
                viewModel.deleteAllData()
            },
            onDismiss = {
                showDeleteDialog = false
                deleteConfirmText = ""
            }
        )
    }
}

// ==================== Extracted Dialogs ====================

@Composable
private fun BackupDialog(
    backupPassword: String,
    onPasswordChange: (String) -> Unit,
    backupPasswordConfirm: String,
    onPasswordConfirmChange: (String) -> Unit,
    backupOptions: SystemBackupManager.BackupOptions,
    onOptionsChange: (SystemBackupManager.BackupOptions) -> Unit,
    backupSizeEstimate: SystemBackupManager.BackupSizeEstimate?,
    onEstimateSize: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    LaunchedEffect(Unit) { onEstimateSize() }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(TnIcons.CloudUpload, null, tint = MaterialTheme.colorScheme.tertiary) },
        title = {
            Text("Create Backup", fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)) {
                Text(
                    "Set a password to encrypt your backup. You'll need this password to restore.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PasswordTextField(
                    value = backupPassword,
                    onValueChange = onPasswordChange,
                    label = "Password",
                    modifier = Modifier.fillMaxWidth(),
                    showToggle = false
                )
                PasswordTextField(
                    value = backupPasswordConfirm,
                    onValueChange = onPasswordConfirmChange,
                    label = "Confirm Password",
                    modifier = Modifier.fillMaxWidth(),
                    showToggle = false,
                    isError = backupPasswordConfirm.isNotEmpty() && backupPassword != backupPasswordConfirm
                )

                Spacer(modifier = Modifier.height(4.dp))

                SwitchRow(
                    title = "Include RAG files",
                    checked = backupOptions.includeRagFiles,
                    onCheckedChange = { checked ->
                        onOptionsChange(backupOptions.copy(includeRagFiles = checked))
                    }
                )

                SwitchRow(
                    title = "Include AI Models",
                    checked = backupOptions.includeModelFiles,
                    onCheckedChange = { checked ->
                        onOptionsChange(backupOptions.copy(includeModelFiles = checked))
                    }
                )

                if (backupOptions.includeModelFiles && backupSizeEstimate != null) {
                    val models = backupSizeEstimate.modelBreakdown
                    if (models.isNotEmpty()) {
                        Column(
                            modifier = Modifier.padding(start = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            models.forEach { model ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        model.modelName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (model.canBackup)
                                            MaterialTheme.colorScheme.onSurface
                                        else
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        if (model.canBackup) formatBytes(model.sizeBytes)
                                        else model.reason,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (model.canBackup)
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        else
                                            MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }

                backupSizeEstimate?.let { estimate ->
                    Text(
                        "Estimated size: ${formatBytes(estimate.totalSize)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = backupPassword.length >= 4 && backupPassword == backupPasswordConfirm
            ) {
                Text("Create Backup", color = MaterialTheme.colorScheme.tertiary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun RestoreDialog(
    restorePassword: String,
    onPasswordChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(TnIcons.CloudDownload, null, tint = MaterialTheme.colorScheme.primary) },
        title = {
            Text("Restore from Backup", fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)) {
                Text(
                    "This will replace all current data with the backup. The app will restart after restore.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                PasswordTextField(
                    value = restorePassword,
                    onValueChange = onPasswordChange,
                    label = "Backup Password",
                    modifier = Modifier.fillMaxWidth(),
                    showToggle = false
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = restorePassword.length >= 4
            ) {
                Text("Select Backup File")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun DeleteAllDataDialog(
    deleteConfirmText: String,
    onConfirmTextChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(TnIcons.TrashX, null, tint = MaterialTheme.colorScheme.error) },
        title = {
            Text("Delete All Data", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)) {
                Text(
                    "This will permanently delete all chats, memories, personas, RAG data, and settings. This cannot be undone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Type DELETE to confirm",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
                OutlinedTextField(
                    value = deleteConfirmText,
                    onValueChange = onConfirmTextChange,
                    label = { Text("Type DELETE") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = deleteConfirmText.isNotEmpty() && deleteConfirmText != "DELETE"
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = deleteConfirmText == "DELETE"
            ) {
                Text("Delete Everything", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
