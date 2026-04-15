package com.dark.tool_neuron.ui.screen

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.tool_neuron.global.Standards
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
import com.dark.tool_neuron.ui.theme.rDp
import com.dark.tool_neuron.viewmodel.SettingsViewModel
import com.dark.tool_neuron.worker.SystemBackupManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onModelEditor: () -> Unit = {},
    onPersonasClick: () -> Unit = {},
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
    
    // Remote API settings
    val remoteApiEnabled by viewModel.remoteApiEnabled.collectAsStateWithLifecycle()
    val remoteApiPort by viewModel.remoteApiPort.collectAsStateWithLifecycle()
    val remoteApiNsdEnabled by viewModel.remoteApiNsdEnabled.collectAsStateWithLifecycle()
    
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

            // Download recommended tool calling model card — only visible when no GGUF model is installed
            if (!hasToolCallingModel) {
                item {
                    StandardCard(title = "Recommended Tool Calling Model") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize(spring()),
                            verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingSm))
                        ) {
                            CaptionText(text = "Ruvltra Claude Code 0.5B · ~400 MB")
                            CaptionText(text = "Compact model optimized for tool calling")

                            when (toolCallingDownloadState) {
                                is ModelDownloadService.DownloadState.Downloading -> {
                                    val progress = toolCallingDownloadState.progress
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
                                            color = MaterialTheme.colorScheme.tertiary,
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
                                        color = MaterialTheme.colorScheme.tertiary,
                                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                    )
                                }

                                is ModelDownloadService.DownloadState.Success -> {
                                    CaptionText(text = "Downloaded successfully")
                                }

                                is ModelDownloadService.DownloadState.Error -> {
                                    Text(
                                        text = toolCallingDownloadState.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    FilledTonalButton(onClick = { viewModel.downloadToolCallingModel() }) {
                                        Text("Retry")
                                    }
                                }

                                else -> {
                                    FilledTonalButton(onClick = { viewModel.downloadToolCallingModel() }) {
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

            // Bypass tool calling model check — red warning card
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(rDp(Standards.CardCornerRadius)),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                    border = androidx.compose.foundation.BorderStroke(
                        width = rDp(1.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(rDp(Standards.SpacingMd)),
                        verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingSm))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingSm))
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(rDp(20.dp))
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

            // ==================== Remote API ====================
            item { Spacer(Modifier.height(rDp(Standards.SpacingSm))) }
            item { SectionDivider() }
            item { SectionHeader(title = "Remote API Access") }
            
            item {
                SwitchRow(
                    title = "Enable Remote API",
                    description = "Expose ToolNeuron over local network (OpenAI-compatible)",
                    checked = remoteApiEnabled,
                    onCheckedChange = { viewModel.setRemoteApiEnabled(it) }
                )
            }
            
            item {
                SwitchRow(
                    title = "Network Discovery (NSD)",
                    description = "Broadcast as ToolNeuron-API.local",
                    checked = remoteApiNsdEnabled,
                    onCheckedChange = { viewModel.setRemoteApiNsdEnabled(it) },
                    enabled = remoteApiEnabled
                )
            }
            
            if (remoteApiEnabled) {
                item {
                    StandardCard(title = "API Configuration") {
                        Column(verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingXs))) {
                            val ip = viewModel.localIpAddress ?: "Unknown IP"
                            val baseUrl = "http://$ip:$remoteApiPort"
                            
                            CaptionText(text = "Base URL: $baseUrl")
                            CaptionText(text = "Chat URL: $baseUrl/v1/chat/completions")
                            CaptionText(text = "Image URL: $baseUrl/v1/images/generations")
                        }
                    }
                }
            }

            // ==================== AI Personality & Memory ====================
            item { Spacer(Modifier.height(rDp(Standards.SpacingSm))) }
            item { SectionDivider() }
            item { SectionHeader(title = "AI Personality & Memory") }

            item {
                Surface(
                    onClick = onPersonasClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(rDp(8.dp)),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(modifier = Modifier.padding(rDp(16.dp))) {
                        Text(
                            "Personas",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Choose or create AI personalities with custom behavior",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

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
                    shape = RoundedCornerShape(rDp(8.dp)),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(modifier = Modifier.padding(rDp(16.dp))) {
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

            // ==================== Chat ====================
            item { Spacer(Modifier.height(rDp(Standards.SpacingSm))) }
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
                    description = "Privacy-First Local AI Assistant"
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(rDp(4.dp))) {
                        Text(
                            text = "Version ${viewModel.appVersion}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        CaptionText(text = "Complete on-device intelligence: LLM, Image Gen, TTS, RAG, and OpenAI-compatible Remote API.")
                    }
                }
            }

            // ==================== Data Management ====================
            item { Spacer(Modifier.height(rDp(Standards.SpacingSm))) }
            item { SectionDivider() }
            item { SectionHeader(title = "Data Management") }

            item {
                DataManagementSection(viewModel = viewModel)
            }

            item { Spacer(Modifier.height(rDp(Standards.SpacingXl))) }
        }
    }
}

// ==================== Data Management Section ====================

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

    // Auto-dismiss progress after completion/error
    LaunchedEffect(backupProgress) {
        if (backupProgress is SystemBackupManager.BackupProgress.Complete) {
            kotlinx.coroutines.delay(2000)
            viewModel.clearBackupProgress()
        }
    }

    // Restart process after successful restore — Hilt singletons hold stale DB/DAO refs
    LaunchedEffect(backupProgress) {
        if (backupProgress is SystemBackupManager.BackupProgress.Complete && showRestoreDialog) {
            kotlinx.coroutines.delay(500)
            showRestoreDialog = false
            val activity = context as? Activity
            activity?.let {
                val intent = it.packageManager.getLaunchIntentForPackage(it.packageName)
                    ?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                it.finishAffinity()
                if (intent != null) it.startActivity(intent)
                Runtime.getRuntime().exit(0)
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingSm))) {
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
                shape = RoundedCornerShape(rDp(Standards.CardCornerRadius))
            ) {
                Row(
                    modifier = Modifier.padding(rDp(Standards.CardPadding)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingSm))
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(rDp(20.dp)))
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
                shape = RoundedCornerShape(rDp(Standards.CardCornerRadius))
            ) {
                Text(
                    "Operation completed successfully",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(rDp(Standards.CardPadding))
                )
            }
        }
        if (progressStatus is SystemBackupManager.BackupProgress.Error) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                shape = RoundedCornerShape(rDp(Standards.CardCornerRadius))
            ) {
                Text(
                    progressStatus.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(rDp(Standards.CardPadding))
                )
            }
        }

        // --- Green Backup Card ---
        Surface(
            onClick = { showBackupDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(rDp(Standards.CardCornerRadius)),
            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
            border = BorderStroke(rDp(1.dp), MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier.padding(rDp(Standards.CardPadding)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingSm))
            ) {
                Icon(
                    Icons.Outlined.Backup, null,
                    modifier = Modifier.size(rDp(Standards.IconLg)),
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
            shape = RoundedCornerShape(rDp(Standards.CardCornerRadius)),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        ) {
            Row(
                modifier = Modifier.padding(rDp(Standards.CardPadding)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingSm))
            ) {
                Icon(
                    Icons.Outlined.Restore, null,
                    modifier = Modifier.size(rDp(Standards.IconLg)),
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
            shape = RoundedCornerShape(rDp(Standards.CardCornerRadius)),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
            border = BorderStroke(rDp(1.dp), MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(rDp(Standards.CardPadding)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingSm))
            ) {
                Icon(
                    Icons.Outlined.DeleteForever, null,
                    modifier = Modifier.size(rDp(Standards.IconLg)),
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

    // Backup Dialog
    if (showBackupDialog) {
        // Estimate size on dialog open
        LaunchedEffect(showBackupDialog) {
            viewModel.estimateBackupSize()
        }

        AlertDialog(
            onDismissRequest = {
                showBackupDialog = false
                backupPassword = ""
                backupPasswordConfirm = ""
            },
            icon = { Icon(Icons.Outlined.Backup, null, tint = MaterialTheme.colorScheme.tertiary) },
            title = {
                Text("Create Backup", fontWeight = FontWeight.SemiBold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingSm))) {
                    Text(
                        "Set a password to encrypt your backup. You'll need this password to restore.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = backupPassword,
                        onValueChange = { backupPassword = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = backupPasswordConfirm,
                        onValueChange = { backupPasswordConfirm = it },
                        label = { Text("Confirm Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = backupPasswordConfirm.isNotEmpty() && backupPassword != backupPasswordConfirm
                    )

                    Spacer(modifier = Modifier.height(rDp(4.dp)))

                    // Include RAG files checkbox
                    SwitchRow(
                        title = "Include RAG files",
                        checked = backupOptions.includeRagFiles,
                        onCheckedChange = { checked ->
                            viewModel.updateBackupOptions(backupOptions.copy(includeRagFiles = checked))
                        }
                    )

                    // Include AI Models checkbox
                    SwitchRow(
                        title = "Include AI Models",
                        checked = backupOptions.includeModelFiles,
                        onCheckedChange = { checked ->
                            viewModel.updateBackupOptions(backupOptions.copy(includeModelFiles = checked))
                        }
                    )

                    // Model list when models are included
                    if (backupOptions.includeModelFiles && backupSizeEstimate != null) {
                        val models = backupSizeEstimate!!.modelBreakdown
                        if (models.isNotEmpty()) {
                            Column(
                                modifier = Modifier.padding(start = rDp(8.dp)),
                                verticalArrangement = Arrangement.spacedBy(rDp(2.dp))
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
                                            if (model.canBackup) formatFileSize(model.sizeBytes)
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

                    // Size estimate
                    backupSizeEstimate?.let { estimate ->
                        Text(
                            "Estimated size: ${formatFileSize(estimate.totalSize)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBackupDialog = false
                        val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                        backupLauncher.launch("toolneuron_backup_$timestamp.tnbackup")
                    },
                    enabled = backupPassword.length >= 4 && backupPassword == backupPasswordConfirm
                ) {
                    Text("Create Backup", color = MaterialTheme.colorScheme.tertiary)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBackupDialog = false
                    backupPassword = ""
                    backupPasswordConfirm = ""
                }) { Text("Cancel") }
            },
            shape = RoundedCornerShape(rDp(16.dp))
        )
    }

    // Restore Dialog
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = {
                showRestoreDialog = false
                restorePassword = ""
            },
            icon = { Icon(Icons.Outlined.Restore, null, tint = MaterialTheme.colorScheme.primary) },
            title = {
                Text("Restore from Backup", fontWeight = FontWeight.SemiBold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingSm))) {
                    Text(
                        "This will replace all current data with the backup. The app will restart after restore.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    OutlinedTextField(
                        value = restorePassword,
                        onValueChange = { restorePassword = it },
                        label = { Text("Backup Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        restoreLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                    },
                    enabled = restorePassword.length >= 4
                ) {
                    Text("Select Backup File")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRestoreDialog = false
                    restorePassword = ""
                }) { Text("Cancel") }
            },
            shape = RoundedCornerShape(rDp(16.dp))
        )
    }

    // Delete All Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                deleteConfirmText = ""
            },
            icon = { Icon(Icons.Outlined.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = {
                Text("Delete All Data", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingSm)) ) {
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
                        onValueChange = { deleteConfirmText = it },
                        label = { Text("Type DELETE") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = deleteConfirmText.isNotEmpty() && deleteConfirmText != "DELETE"
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        deleteConfirmText = ""
                        viewModel.deleteAllData()
                    },
                    enabled = deleteConfirmText == "DELETE"
                ) {
                    Text("Delete Everything", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    deleteConfirmText = ""
                }) { Text("Cancel") }
            },
            shape = RoundedCornerShape(rDp(16.dp))
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "${bytes} B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024L * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
