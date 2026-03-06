package com.dark.tool_neuron.ui.screen.home

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.toShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.R
import com.dark.tool_neuron.global.Standards
import com.dark.tool_neuron.activity.ModelLoadingActivity
import com.dark.tool_neuron.activity.RagActivity
import com.dark.tool_neuron.models.plugins.PluginInfo
import com.dark.tool_neuron.data.AppSettingsDataStore
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.ActionProgressButton
import com.dark.tool_neuron.ui.components.ActionSwitch
import com.dark.tool_neuron.ui.components.ActionTextButton
import com.dark.tool_neuron.ui.components.ActionToggleButton
import com.dark.tool_neuron.ui.components.AnimatedTitle
import com.dark.tool_neuron.ui.components.LocalCodeHighlightEnabled
import com.dark.tool_neuron.ui.components.ModeToggleSwitch
import com.dark.tool_neuron.ui.components.ModelListItem
import com.dark.tool_neuron.ui.components.MemoryOverlayBottomSheet
import com.dark.tool_neuron.ui.components.PluginOverlayBottomSheet
import com.dark.tool_neuron.ui.components.SwitchRow
import com.dark.tool_neuron.ui.icons.TnIcons
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.material3.AlertDialog
import com.dark.tool_neuron.models.enums.ProviderType
import com.dark.tool_neuron.viewmodel.ChatViewModel
import com.dark.tool_neuron.viewmodel.LLMModelViewModel
import com.dark.tool_neuron.viewmodel.MemoryViewModel
import com.dark.tool_neuron.viewmodel.PluginViewModel
import com.dark.tool_neuron.viewmodel.RagViewModel
import com.dark.tool_neuron.models.ModelType
import kotlinx.coroutines.launch

// Update HomeScreen to wrap with SharedTransitionLayout
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun HomeScreen(
    onStoreButtonClicked: () -> Unit,
    onSettingsClick: () -> Unit,
    onVaultManagerClick: () -> Unit,
    onImageGenSetupNeeded: () -> Unit,
    chatViewModel: ChatViewModel,
    llmModelViewModel: LLMModelViewModel
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val appSettings = remember { AppSettingsDataStore(context) }
    val codeHighlightEnabled by appSettings.codeHighlightEnabled
        .collectAsStateWithLifecycle(initialValue = true)
    val toolCallingEnabled by appSettings.toolCallingEnabled
        .collectAsStateWithLifecycle(initialValue = true)

    // Navigate to QNN setup when a diffusion model needs it
    val needsQnnSetup by llmModelViewModel.needsQnnSetup.collectAsStateWithLifecycle()
    LaunchedEffect(needsQnnSetup) {
        if (needsQnnSetup) onImageGenSetupNeeded()
    }

    // Offer to reload the last loaded model on startup
    val lastModelOffer by llmModelViewModel.lastModelOffer.collectAsStateWithLifecycle()
    lastModelOffer?.let { model ->
        ReloadModelDialog(
            modelName = model.modelName,
            modelType = model.providerType,
            onConfirm = { llmModelViewModel.acceptLastModelOffer() },
            onDismiss = { llmModelViewModel.dismissLastModelOffer() }
        )
    }

    CompositionLocalProvider(LocalCodeHighlightEnabled provides codeHighlightEnabled) {
    ModalNavigationDrawer(
        drawerState = drawerState, drawerContent = {
            ModalDrawerSheet {
                HomeDrawerScreen(
                    onVaultManagerClick = onVaultManagerClick,
                    onChatSelected = {
                        chatViewModel.loadChat(it)
                        scope.launch {
                            drawerState.close()
                        }
                    },
                    chatViewModel = chatViewModel
                )
            }
        }) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopBar(
                    onStoreButtonClicked = onStoreButtonClicked,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onSettingsClick = onSettingsClick,
                    showDynamicWindow = { chatViewModel.showDynamicWindow() }
                )
            },
            bottomBar = {
                BottomBar(
                    chatViewModel = chatViewModel,
                    llmModelViewModel = llmModelViewModel,
                    toolCallingEnabled = toolCallingEnabled
                )
            }) { paddingValues ->
            BodyContent(paddingValues, chatViewModel, llmModelViewModel = llmModelViewModel)
        }
    }
    } // CompositionLocalProvider
}

// First, update your TopBar to use a shared transition key
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun TopBar(
    onMenuClick: () -> Unit,
    onSettingsClick: () -> Unit,
    showDynamicWindow: () -> Unit,
    onStoreButtonClicked: () -> Unit
) {
    val context = LocalContext.current

    CenterAlignedTopAppBar(title = {
        AnimatedTitle(
            modifier = Modifier, onShowDynamicWindow = {
                showDynamicWindow()
            })
    }, navigationIcon = {
        ActionButton(
            onClickListener = onMenuClick,
            icon = TnIcons.Menu,
            modifier = Modifier.padding(start = 6.dp)
        )
    }, actions = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActionButton(
                onClickListener = onSettingsClick,
                icon = TnIcons.Settings,
                modifier = Modifier.padding(end = 6.dp)
            )
            ActionButton(
                onClickListener = {
                    onStoreButtonClicked()
                }, icon = TnIcons.Download, modifier = Modifier.padding(end = 6.dp)
            )
            ActionButton(
                onClickListener = {
                    // Open ModelLoadingActivity which will launch SAF picker automatically
                    context.startActivity(Intent(context, ModelLoadingActivity::class.java))
                }, icon = TnIcons.Upload, modifier = Modifier.padding(end = 6.dp)
            )
        }
    })
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BottomBar(
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
    val showModelList by chatViewModel.showModelList.collectAsStateWithLifecycle()
    val isGenerating by chatViewModel.isGenerating.collectAsStateWithLifecycle()
    val currentGenerationType by chatViewModel.currentGenerationType.collectAsStateWithLifecycle()
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

    // Thinking mode
    val thinkingEnabled by chatViewModel.thinkingModeEnabled.collectAsStateWithLifecycle()
    val modelSupportsThinking by chatViewModel.modelSupportsThinking.collectAsStateWithLifecycle()

    // Coroutine scope for RAG queries
    val scope = rememberCoroutineScope()

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
        AnimatedVisibility(showModelList) {
            if (installedModels.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .background(
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                        .padding(8.dp)
                        .heightIn(max = 200.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(0.04f)
                                .compositeOver(MaterialTheme.colorScheme.background),
                            shape = RoundedCornerShape(8.dp)
                        ), contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(installedModels) { modelConfig ->
                        ModelListItem(
                            Modifier
                                .padding(top = 8.dp)
                                .padding(horizontal = 8.dp),
                            isLoaded = currentModelID == modelConfig.id,
                            model = modelConfig
                        ) { selectedModel ->
                            if (isModelLoaded) {
                                llmModelViewModel.unloadModel()
                                chatViewModel.hideModelList()
                            } else {
                                llmModelViewModel.loadModel(selectedModel)
                                chatViewModel.hideModelList()
                            }
                        }
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
                    .padding(horizontal = 8.dp)
                    .padding(top = 8.dp, bottom = 10.dp)
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
                                text = when (currentGenerationType) {
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
                        isImageMode = currentGenerationType == ModelType.IMAGE_GENERATION,
                        onModeChange = { isImageMode ->
                            if (isImageMode) {
                                chatViewModel.switchToImageGeneration()
                            } else {
                                chatViewModel.switchToTextGeneration()
                            }
                        },
                        textModelLoaded = isTextModelLoaded,
                        imageModelLoaded = isImageModelLoaded,
                        modifier = Modifier.padding(start = 12.dp)
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
                            if (showModelList) {
                                chatViewModel.hideModelList()
                            } else {
                                chatViewModel.showModelList()
                            }
                        }, checked = showModelList, icon = TnIcons.Brain
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
                            checked = thinkingEnabled,
                            enabled = isTextModelLoaded,
                            icon = TnIcons.Brain
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    // 6. Send/Stop
                    when (isGenerating) {
                        true -> {
                            ActionProgressButton(
                                onClickListener = {
                                    chatViewModel.stop()
                                },
                                modifier = Modifier.padding(end = 12.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(0.3f),
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }

                        false -> {
                            ActionButton(
                                onClickListener = {
                                    if (value.isNotBlank()) {
                                        // Close overlays on send
                                        showMoreOptions = false
                                        when (currentGenerationType) {
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
                                            ModelType.AUDIO_GENERATION -> {}
                                        }
                                    }
                                },
                                icon = TnIcons.Send,
                                shape = MaterialShapes.Ghostish.toShape(),
                                modifier = Modifier.padding(end = 12.dp),
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

@Composable
private fun QuickLookChipRow(
    loadedRagCount: Int,
    isMemoryEnabled: Boolean,
    isWebSearchEnabled: Boolean = false,
    isRagEnabled: Boolean = true,
    activePluginName: String? = null,
    onRagChipClick: () -> Unit,
    onToolChipClick: () -> Unit,
    onMemoryChipClick: () -> Unit,
    onWebSearchChipClick: () -> Unit = {}
) {
    val hasAnyActive = (loadedRagCount > 0 && isRagEnabled) || isMemoryEnabled || isWebSearchEnabled || activePluginName != null

    AnimatedVisibility(visible = hasAnyActive) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(Standards.ChipSpacing)
        ) {
            if (loadedRagCount > 0 && isRagEnabled) {
                StatusChip(
                    label = "$loadedRagCount RAG",
                    color = MaterialTheme.colorScheme.primary,
                    onClick = onRagChipClick
                )
            }
            if (isWebSearchEnabled) {
                StatusChip(
                    label = "Web Search",
                    color = MaterialTheme.colorScheme.tertiary,
                    onClick = onWebSearchChipClick
                )
            }
            if (activePluginName != null) {
                StatusChip(
                    label = activePluginName,
                    color = MaterialTheme.colorScheme.tertiary,
                    onClick = onToolChipClick
                )
            }
            if (isMemoryEnabled) {
                StatusChip(
                    label = "Memory",
                    color = MaterialTheme.colorScheme.secondary,
                    onClick = onMemoryChipClick
                )
            }
        }
    }
}

@Composable
private fun StatusChip(
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(Standards.ChipCornerRadius),
        modifier = Modifier.height(Standards.ChipHeight)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Standards.ChipHorizontalPadding)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(color, RoundedCornerShape(50))
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun MoreOptionsOverlay(
    show: Boolean,
    // RAG
    loadedRagCount: Int,
    isRagEnabled: Boolean,
    onRagToggle: (Boolean) -> Unit,
    onRagManage: () -> Unit,
    // Plugin
    nonWebSearchPlugins: List<PluginInfo>,
    enabledPluginNames: Set<String>,
    isToolCallingModelLoaded: Boolean,
    toolCallingEnabled: Boolean,
    onPluginToggle: (String, Boolean) -> Unit,
    onManagePlugins: () -> Unit
) {
    val context = LocalContext.current

    AnimatedVisibility(visible = show) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .padding(bottom = 8.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(0.04f)
                        .compositeOver(MaterialTheme.colorScheme.background),
                    shape = RoundedCornerShape(10.dp)
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // RAG section
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(Standards.CardSmallCornerRadius)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Standards.CardPadding, vertical = Standards.SpacingSm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
                ) {
                    Icon(
                        imageVector = TnIcons.Database,
                        contentDescription = null,
                        modifier = Modifier.size(Standards.IconMd),
                        tint = if (loadedRagCount > 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "RAG",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (loadedRagCount > 0) "$loadedRagCount loaded" else "None loaded",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    ActionTextButton(
                        onClickListener = onRagManage,
                        icon = TnIcons.Database,
                        text = "Manage"
                    )
                    ActionSwitch(
                        checked = isRagEnabled,
                        onCheckedChange = onRagToggle,
                        enabled = loadedRagCount > 0
                    )
                }
            }

            // Plugin section (only if tool calling enabled)
            if (toolCallingEnabled && nonWebSearchPlugins.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(Standards.CardSmallCornerRadius)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Standards.CardPadding),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Plugins",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        nonWebSearchPlugins.forEach { plugin ->
                            val isEnabled = enabledPluginNames.contains(plugin.name)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = plugin.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isToolCallingModelLoaded) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    )
                                    Text(
                                        text = "${plugin.toolDefinitionBuilder.size} tools",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                                ActionSwitch(
                                    checked = isEnabled,
                                    onCheckedChange = { onPluginToggle(plugin.name, !isEnabled) },
                                    enabled = isToolCallingModelLoaded
                                )
                            }
                        }

                        ActionTextButton(
                            onClickListener = onManagePlugins,
                            icon = TnIcons.Wrench,
                            text = "Configure"
                        )
                    }
                }
            }
        }
    }
}

// ==================== Reload Model Dialog ====================

@Composable
private fun ReloadModelDialog(
    modelName: String,
    modelType: ProviderType,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val typeLabel = when (modelType) {
        ProviderType.GGUF -> "Text"
        ProviderType.DIFFUSION -> "Image"
        ProviderType.TTS -> "TTS"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                TnIcons.Cpu, null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text("Load Previous Model?", fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)) {
                Text(
                    "You previously had a model loaded. Would you like to load it again?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    modelName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    typeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Load")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Skip")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
