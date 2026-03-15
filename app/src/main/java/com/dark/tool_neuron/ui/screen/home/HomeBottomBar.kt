package com.dark.tool_neuron.ui.screen.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.activity.RagActivity
import com.dark.tool_neuron.audio.ChatAudioRecorder
import com.dark.tool_neuron.audio.RecordedAudioClip
import com.dark.tool_neuron.global.Standards
import com.dark.tool_neuron.models.ModelType
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.ActionProgressButton
import com.dark.tool_neuron.ui.components.ActionToggleButton
import com.dark.tool_neuron.ui.components.MemoryOverlayBottomSheet
import com.dark.tool_neuron.ui.components.ModeToggleSwitch
import com.dark.tool_neuron.ui.components.ModelListItem
import com.dark.tool_neuron.ui.components.PluginOverlayBottomSheet
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.viewmodel.ChatViewModel
import com.dark.tool_neuron.viewmodel.ChatUiState
import com.dark.tool_neuron.viewmodel.ChatConfigState
import com.dark.tool_neuron.viewmodel.LLMModelViewModel
import com.dark.tool_neuron.viewmodel.MemoryViewModel
import com.dark.tool_neuron.viewmodel.PluginViewModel
import com.dark.tool_neuron.viewmodel.RagViewModel
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── BottomBar ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BottomBar(
    chatViewModel: ChatViewModel = hiltViewModel(),
    llmModelViewModel: LLMModelViewModel = hiltViewModel(),
    ragViewModel: RagViewModel = hiltViewModel(),
    pluginViewModel: PluginViewModel = hiltViewModel(),
    memoryViewModel: MemoryViewModel = hiltViewModel(),
    toolCallingEnabled: Boolean = true
) {
    val context = LocalContext.current
    var value by remember { mutableStateOf("") }
    val installedModels by llmModelViewModel.installedModels.collectAsStateWithLifecycle(emptyList())
    val currentModelID by llmModelViewModel.currentModelID.collectAsStateWithLifecycle()
    val chatState by chatViewModel.chatUiState.collectAsStateWithLifecycle()
    val config by chatViewModel.chatConfigState.collectAsStateWithLifecycle()
    val isTextModelLoaded by chatViewModel.isTextModelLoaded.collectAsStateWithLifecycle()
    val isImageModelLoaded by chatViewModel.isImageModelLoaded.collectAsStateWithLifecycle()

    // RAG State
    val loadedRags by ragViewModel.loadedRags.collectAsStateWithLifecycle()
    val isRagEnabledForChat by ragViewModel.isRagEnabledForChat.collectAsStateWithLifecycle()
    val lastRagResults by ragViewModel.lastRagResults.collectAsStateWithLifecycle()

    // Plugin State
    val showPluginOverlay by pluginViewModel.showPluginOverlay.collectAsStateWithLifecycle()
    val registeredPlugins by pluginViewModel.registeredPlugins.collectAsStateWithLifecycle()
    val enabledPluginNames by pluginViewModel.enabledPluginNames.collectAsStateWithLifecycle()
    val expandedPluginIds by pluginViewModel.expandedPluginIds.collectAsStateWithLifecycle()
    val multiTurnEnabled by pluginViewModel.multiTurnEnabled.collectAsStateWithLifecycle()
    val toolCallingConfig by pluginViewModel.toolCallingConfig.collectAsStateWithLifecycle()
    val isToolCallingModelLoaded by pluginViewModel.isToolCallingModelLoaded.collectAsStateWithLifecycle()

    // Memory State
    val showMemoryOverlay by memoryViewModel.showMemoryOverlay.collectAsStateWithLifecycle()
    val isMemoryEnabled by memoryViewModel.isMemoryEnabled.collectAsStateWithLifecycle()
    val memoryResults by memoryViewModel.memoryResults.collectAsStateWithLifecycle()
    val vaultStats by memoryViewModel.vaultStats.collectAsStateWithLifecycle()
    val memoryEntryCount by memoryViewModel.memoryEntryCount.collectAsStateWithLifecycle()

    // Web Search & non-WebSearch plugins
    val isWebSearchEnabled by pluginViewModel.isWebSearchEnabled.collectAsStateWithLifecycle()
    val nonWebSearchPlugins by pluginViewModel.nonWebSearchPlugins.collectAsStateWithLifecycle()

    // Coroutine scope for RAG queries
    val scope = rememberCoroutineScope()
    val audioRecorder = remember(context.applicationContext) {
        ChatAudioRecorder(context.applicationContext)
    }
    var stagedRecording by remember { mutableStateOf<RecordedAudioClip?>(null) }
    var isMicRecording by remember { mutableStateOf(false) }
    var recordingStartedAtMs by remember { mutableStateOf<Long?>(null) }
    var recordingElapsedMs by remember { mutableStateOf(0L) }
    var micErrorMessage by remember { mutableStateOf<String?>(null) }

    val discardStagedRecording = {
        stagedRecording?.let { audioRecorder.deleteClip(it.file) }
        stagedRecording = null
    }
    val clearAudioCaptureState = {
        if (isMicRecording) {
            audioRecorder.cancelRecording()
        }
        isMicRecording = false
        recordingStartedAtMs = null
        recordingElapsedMs = 0L
        discardStagedRecording()
        micErrorMessage = null
    }
    val startMicrophoneRecording = {
        discardStagedRecording()
        micErrorMessage = null
        try {
            audioRecorder.startRecording()
            isMicRecording = true
            recordingStartedAtMs = SystemClock.elapsedRealtime()
            recordingElapsedMs = 0L
        } catch (e: IOException) {
            micErrorMessage = e.message ?: "Unable to start microphone recording"
        } catch (e: IllegalStateException) {
            micErrorMessage = e.message ?: "Unable to start microphone recording"
        } catch (e: SecurityException) {
            micErrorMessage = e.message ?: "Microphone permission denied"
        }
    }
    val stopMicrophoneRecording = {
        try {
            val clip = audioRecorder.stopRecording()
            discardStagedRecording()
            stagedRecording = clip
            isMicRecording = false
            recordingStartedAtMs = null
            recordingElapsedMs = clip.durationMillis
            micErrorMessage = null
        } catch (e: IllegalStateException) {
            isMicRecording = false
            recordingStartedAtMs = null
            recordingElapsedMs = 0L
            micErrorMessage = e.message ?: "Unable to finalize microphone recording"
        }
    }

    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startMicrophoneRecording()
        } else {
            micErrorMessage = "Microphone permission is required to record audio."
        }
    }

    // File imports remain the fallback path, while mic capture stages a temporary clip for review
    // before both routes converge on ChatViewModel.sendChatWithAudio(...).
    val audioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            discardStagedRecording()
            micErrorMessage = null
            val audioPrompt = value.ifBlank { "Transcribe this audio." }
            chatViewModel.sendChatWithAudio(audioPrompt, context, uri)
            value = ""
        }
    }

    DisposableEffect(audioRecorder) {
        onDispose {
            audioRecorder.release()
            stagedRecording?.let { audioRecorder.deleteClip(it.file) }
        }
    }

    LaunchedEffect(isMicRecording, recordingStartedAtMs) {
        if (!isMicRecording || recordingStartedAtMs == null) {
            return@LaunchedEffect
        }

        while (isMicRecording) {
            val startedAt = recordingStartedAtMs ?: break
            recordingElapsedMs = SystemClock.elapsedRealtime() - startedAt
            delay(250L)
        }
    }

    LaunchedEffect(chatState.generationType) {
        if (chatState.generationType != ModelType.AUDIO_GENERATION) {
            clearAudioCaptureState()
        }
    }

    val audioStatusMessage = when {
        micErrorMessage != null -> micErrorMessage
        isMicRecording -> "Recording microphone - ${formatAudioDuration(recordingElapsedMs)}"
        stagedRecording != null -> "Recorded clip ready to send - ${formatAudioDuration(stagedRecording!!.durationMillis)}"
        else -> null
    }
    val canSendCurrentInput = when (chatState.generationType) {
        ModelType.TEXT_GENERATION -> value.isNotBlank()
        ModelType.IMAGE_GENERATION -> value.isNotBlank()
        ModelType.AUDIO_GENERATION -> stagedRecording != null && !isMicRecording
    }
    // More Options overlay state
    var showMoreOptions by remember { mutableStateOf(false) }

    // Track if any model is loaded
    val isModelLoaded = currentModelID.isNotEmpty()

    // Plugin Overlay (excludes Web Search — it has its own toggle)
    PluginOverlayBottomSheet(
        show = showPluginOverlay,
        plugins = nonWebSearchPlugins,
        enabledPluginNames = enabledPluginNames,
        expandedPluginIds = expandedPluginIds,
        multiTurnEnabled = multiTurnEnabled,
        toolCallingConfig = toolCallingConfig,
        onDismiss = { pluginViewModel.hidePluginOverlay() },
        onPluginToggle = { name, enabled ->
            pluginViewModel.togglePluginEnabled(name, enabled)
        },
        onPluginExpand = { name ->
            pluginViewModel.togglePluginExpanded(name)
        },
        onMultiTurnToggle = { pluginViewModel.setMultiTurnEnabled(it) },
        onMaxRoundsChange = { pluginViewModel.setMaxRounds(it) }
    )

    // Memory Overlay
    MemoryOverlayBottomSheet(
        show = showMemoryOverlay,
        isMemoryEnabled = isMemoryEnabled,
        vaultStats = vaultStats,
        memoryResults = memoryResults,
        memoryEntryCount = memoryEntryCount,
        onDismiss = { memoryViewModel.dismissMemoryOverlay() },
        onMemoryEnabledChange = { memoryViewModel.setMemoryEnabled(it) },
        onRefreshStats = { memoryViewModel.refreshStats() }
    )

    Column {
        AnimatedVisibility(config.showModelList) {
            if (installedModels.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Standards.SpacingSm)
                        .background(
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(Standards.RadiusMd)
                        )
                        .padding(horizontal = Standards.SpacingMd, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
                ) {
                    Icon(
                        imageVector = TnIcons.AlertTriangle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        "No models installed. Download one from the store or load a local GGUF file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            } else {
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .padding(Standards.SpacingSm)
                        .heightIn(max = 200.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(0.04f)
                                .compositeOver(MaterialTheme.colorScheme.background),
                            shape = RoundedCornerShape(Standards.RadiusMd)
                        ), contentPadding = PaddingValues(bottom = Standards.SpacingSm)
                ) {
                    items(installedModels) { modelConfig ->
                        ModelListItem(
                            modifier = Modifier
                                .padding(top = Standards.SpacingSm)
                                .padding(horizontal = Standards.SpacingSm),
                            model = modelConfig,
                            isLoaded = currentModelID == modelConfig.id,
                            onClickListener = { selectedModel ->
                                if (isModelLoaded) {
                                    llmModelViewModel.unloadModel()
                                    chatViewModel.hideModelList()
                                } else {
                                    llmModelViewModel.loadModel(selectedModel)
                                    chatViewModel.hideModelList()
                                }
                            }
                        )
                    }
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.primary.copy(0.04f)
                        .compositeOver(MaterialTheme.colorScheme.background)
                )
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = Standards.SpacingSm)
                    .padding(top = Standards.SpacingSm, bottom = 10.dp)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                ) {
                    TextField(
                        value = value,
                        onValueChange = { value = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                text = when (chatState.generationType) {
                                    ModelType.TEXT_GENERATION -> "Say Anything…"
                                    ModelType.IMAGE_GENERATION -> "Describe the image you want…"
                                    ModelType.AUDIO_GENERATION -> "Say Anything…"
                                }
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                // Quick-look chips showing active subsystems
                QuickLookChipRow(
                    loadedRagCount = loadedRags.size,
                    isMemoryEnabled = isMemoryEnabled,
                    isWebSearchEnabled = isWebSearchEnabled,
                    isRagEnabled = isRagEnabledForChat,
                    activePluginName = enabledPluginNames.firstOrNull { it != "Web Search" },
                    onRagChipClick = { context.startActivity(Intent(context, RagActivity::class.java)) },
                    onToolChipClick = { pluginViewModel.showPluginOverlay() },
                    onMemoryChipClick = { memoryViewModel.toggleMemoryOverlay() },
                    onWebSearchChipClick = { pluginViewModel.toggleWebSearch(false) }
                )

                AnimatedVisibility(
                    visible = chatState.generationType == ModelType.AUDIO_GENERATION &&
                        audioStatusMessage != null
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Standards.SpacingXs, start = Standards.SpacingMd),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = audioStatusMessage.orEmpty(),
                            color = if (micErrorMessage != null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // More Options overlay (above action row, like model list)
                MoreOptionsOverlay(
                    show = showMoreOptions,
                    loadedRagCount = loadedRags.size,
                    isRagEnabled = isRagEnabledForChat,
                    onRagToggle = { ragViewModel.toggleRagForChat(it) },
                    onRagManage = {
                        showMoreOptions = false
                        context.startActivity(Intent(context, RagActivity::class.java))
                    },
                    nonWebSearchPlugins = nonWebSearchPlugins,
                    enabledPluginNames = enabledPluginNames,
                    isToolCallingModelLoaded = isToolCallingModelLoaded,
                    toolCallingEnabled = toolCallingEnabled,
                    onPluginToggle = { name, enabled ->
                        pluginViewModel.togglePluginEnabled(name, enabled)
                    },
                    onManagePlugins = {
                        showMoreOptions = false
                        pluginViewModel.showPluginOverlay()
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Standards.ActionIconSpace),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Mode toggle switch (Text/Image)
                    ModeToggleSwitch(
                        isImageMode = chatState.generationType == ModelType.IMAGE_GENERATION,
                        onModeChange = { isImageMode ->
                            if (isImageMode) {
                                chatViewModel.switchToImageGeneration()
                            } else {
                                chatViewModel.switchToTextGeneration()
                            }
                        },
                        textModelLoaded = isTextModelLoaded,
                        imageModelLoaded = isImageModelLoaded,
                        modifier = Modifier.padding(start = Standards.SpacingMd)
                    )

                    // 2. More Options
                    ActionToggleButton(
                        onCheckedChange = { showMoreOptions = !showMoreOptions },
                        checked = showMoreOptions,
                        icon = TnIcons.Adjustments
                    )

                    // 3. Model selector
                    ActionToggleButton(
                        onCheckedChange = {
                            if (config.showModelList) {
                                chatViewModel.hideModelList()
                            } else {
                                chatViewModel.showModelList()
                            }
                        }, checked = config.showModelList, icon = TnIcons.Stack2
                    )

                    // 4. Web Search Toggle
                    if (toolCallingEnabled) {
                        ActionToggleButton(
                            onCheckedChange = { pluginViewModel.toggleWebSearch(!isWebSearchEnabled) },
                            checked = isWebSearchEnabled,
                            enabled = isToolCallingModelLoaded,
                            icon = TnIcons.World
                        )
                    }

                    // 5. Thinking Toggle
                    if (isTextModelLoaded) {
                        ActionToggleButton(
                            onCheckedChange = { chatViewModel.toggleThinkingMode() },
                            checked = chatState.thinkingEnabled,
                            enabled = isTextModelLoaded,
                            icon = TnIcons.Brain
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    if (chatState.generationType == ModelType.AUDIO_GENERATION) {
                        ActionButton(
                            onClickListener = {
                                micErrorMessage = null
                                audioLauncher.launch("audio/*")
                            },
                            enabled = !chatState.isGenerating && !isMicRecording,
                            icon = TnIcons.FileUpload,
                            modifier = Modifier.padding(end = Standards.SpacingXs)
                        )

                        ActionButton(
                            onClickListener = {
                                if (isMicRecording) {
                                    stopMicrophoneRecording()
                                } else if (
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    startMicrophoneRecording()
                                } else {
                                    microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            enabled = !chatState.isGenerating,
                            icon = if (isMicRecording) TnIcons.PlayerStop else TnIcons.Microphone,
                            modifier = Modifier.padding(end = Standards.SpacingXs),
                            colors = if (isMicRecording) {
                                IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.error.copy(0.18f),
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            } else {
                                IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(0.06f),
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            }
                        )

                        if (isMicRecording || stagedRecording != null) {
                            ActionButton(
                                onClickListener = { clearAudioCaptureState() },
                                enabled = !chatState.isGenerating,
                                icon = TnIcons.X,
                                modifier = Modifier.padding(end = Standards.SpacingXs)
                            )
                        }
                    }

                    // 6. Send/Stop
                    when (chatState.isGenerating) {
                        true -> {
                            ActionProgressButton(
                                onClickListener = {
                                    chatViewModel.stop()
                                },
                                modifier = Modifier.padding(end = Standards.SpacingMd),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(0.3f),
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }

                        false -> {
                            ActionButton(
                                onClickListener = {
                                    // Close overlays on send
                                    showMoreOptions = false
                                    when (chatState.generationType) {
                                        ModelType.TEXT_GENERATION -> {
                                            val hasRags = loadedRags.isNotEmpty() && isRagEnabledForChat

                                            if (hasRags) {
                                                val userQuery = value
                                                value = ""
                                                scope.launch {
                                                    val ragContext = ragViewModel.queryAndStoreResults(userQuery)
                                                    chatViewModel.setRagContext(
                                                        ragContext.ifBlank { null },
                                                        ragViewModel.lastRagResults.value
                                                    )
                                                    chatViewModel.sendTextMessage(userQuery)
                                                }
                                            } else {
                                                chatViewModel.clearRagContext()
                                                chatViewModel.sendTextMessage(value)
                                                value = ""
                                            }
                                        }

                                        ModelType.IMAGE_GENERATION -> {
                                            chatViewModel.sendImageRequest(value)
                                            value = ""
                                        }

                                        ModelType.AUDIO_GENERATION -> {
                                            val clip = stagedRecording ?: return@ActionButton
                                            val readinessError = chatViewModel.getAudioGenerationReadinessError()
                                            if (readinessError != null) {
                                                micErrorMessage = readinessError
                                                return@ActionButton
                                            }

                                            val audioPrompt = value.ifBlank { "Transcribe this audio." }
                                            chatViewModel.sendChatWithAudio(audioPrompt, clip.file)
                                            stagedRecording = null
                                            recordingStartedAtMs = null
                                            recordingElapsedMs = 0L
                                            micErrorMessage = null
                                            value = ""
                                        }
                                    }
                                },
                                enabled = canSendCurrentInput,
                                icon = TnIcons.Send,
                                shape = MaterialShapes.Ghostish.toShape(),
                                modifier = Modifier.padding(end = Standards.SpacingMd),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(0.3f),
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatAudioDuration(durationMillis: Long): String {
    val totalSeconds = (durationMillis / 1000L).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
