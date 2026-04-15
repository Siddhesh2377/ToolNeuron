package com.dark.tool_neuron.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.tool_neuron.global.Standards
import com.dark.tool_neuron.plugins.PluginManager
import com.dark.tool_neuron.service.ModelDownloadService
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.SectionDivider
import com.dark.tool_neuron.ui.components.SectionHeader
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.viewmodel.SettingsViewModel

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
            // ── General ──
            generalSettingsSection(
                toolCallingEnabled = toolCallingEnabled,
                toolCallingBypassEnabled = toolCallingBypassEnabled,
                hasToolCallingModel = hasToolCallingModel,
                toolCallingDownloadState = toolCallingDownloadState,
                viewModel = viewModel
            )

            // ── LLM ──
            llmSettingsSection(
                streamingEnabled = streamingEnabled,
                chatMemoryEnabled = chatMemoryEnabled,
                askModelReloadDialog = askModelReloadDialog,
                viewModel = viewModel
            )

            // ── Chat ──
            chatSettingsSection(
                codeHighlightEnabled = codeHighlightEnabled,
                viewModel = viewModel
            )

            // ── Hardware Tuning ──
            hardwareTuningSection(
                hardwareTuningEnabled = hardwareTuningEnabled,
                performanceMode = performanceMode,
                hardwareProfile = hardwareProfile,
                viewModel = viewModel
            )

            // ── Model Configuration ──
            modelConfigurationSection(
                hardwareTuningEnabled = hardwareTuningEnabled,
                installedModels = installedModels,
                onModelEditor = onModelEditor
            )

            // ── AI Memory ──
            aiMemorySection(
                aiMemoryEnabled = aiMemoryEnabled,
                onAiMemoryClick = onAiMemoryClick,
                viewModel = viewModel
            )

            // ── TTS ──
            ttsSettingsSection(
                hasTtsModel = hasTtsModel,
                ttsDownloadState = ttsDownloadState,
                ttsModelLoaded = ttsModelLoaded,
                loadTTSOnStart = loadTTSOnStart,
                ttsSettings = ttsSettings,
                voices = voices,
                viewModel = viewModel
            )

            // ── Image Generation ──
            imageGenerationSection(
                imageBlurEnabled = imageBlurEnabled,
                viewModel = viewModel
            )

            // ── Data Management ──
            item { Spacer(Modifier.height(Standards.SpacingSm)) }
            item { SectionDivider() }
            item { SectionHeader(title = "Data Management") }

            item {
                DataManagementSection(viewModel = viewModel)
            }

            // ── About ──
            aboutSection(appVersion = viewModel.appVersion)

            item { Spacer(Modifier.height(Standards.SpacingXl)) }
        }
    }
}
