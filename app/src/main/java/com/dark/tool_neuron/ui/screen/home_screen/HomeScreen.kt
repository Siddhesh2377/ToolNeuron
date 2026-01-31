package com.dark.tool_neuron.ui.screen.home_screen

import android.content.Intent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.toShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.R
import com.dark.tool_neuron.global.Standards
import com.dark.tool_neuron.activity.ModelLoadingActivity
import com.dark.tool_neuron.activity.RagActivity
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.ActionProgressButton
import com.dark.tool_neuron.ui.components.ActionToggleButton
import com.dark.tool_neuron.ui.components.AnimatedTitle
import com.dark.tool_neuron.ui.components.ModeToggleSwitch
import com.dark.tool_neuron.ui.components.ModelListItem
import com.dark.tool_neuron.ui.components.MemoryOverlayBottomSheet
import com.dark.tool_neuron.ui.components.PluginOverlayBottomSheet
import com.dark.tool_neuron.ui.components.RagOverlayBottomSheet
import com.dark.tool_neuron.ui.theme.rDp
import com.dark.tool_neuron.viewmodel.ChatViewModel
import com.dark.tool_neuron.viewmodel.LLMModelViewModel
import com.dark.tool_neuron.viewmodel.MemoryViewModel
import com.dark.tool_neuron.viewmodel.PluginViewModel
import com.dark.tool_neuron.viewmodel.RagViewModel
import com.dark.tool_neuron.worker.GenerationManager
import kotlinx.coroutines.launch

// Update HomeScreen to wrap with SharedTransitionLayout
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun HomeScreen(
    onStoreButtonClicked: () -> Unit,
    onSettingsClick: () -> Unit,
    onVaultManagerClick: () -> Unit,
    chatViewModel: ChatViewModel,
    llmModelViewModel: LLMModelViewModel
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState, drawerContent = {
            ModalDrawerSheet {
                HomeDrawerScreen(
                    onVaultManagerClick = {
                        onVaultManagerClick()
                    },
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
                TopBar( onStoreButtonClicked = {
                    onStoreButtonClicked()
                }, onMenuClick = {
                    scope.launch {
                        drawerState.open()
                    }
                }, showDynamicWindow = {
                    chatViewModel.showDynamicWindow()
                })
            },
            bottomBar = {
                BottomBar(
                    onSettingsClick = {
                        onSettingsClick()
                    },
                    chatViewModel = chatViewModel,
                    llmModelViewModel = llmModelViewModel
                )
            }) { paddingValues ->
            BodyContent(paddingValues, chatViewModel, llmModelViewModel = llmModelViewModel)
        }
    }
}

// First, update your TopBar to use a shared transition key
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun TopBar(
    onMenuClick: () -> Unit,
    showDynamicWindow: () -> Unit,
    onStoreButtonClicked: () -> Unit
) {
    val context = LocalContext.current

    // SAF file picker launcher - opens ModelLoadingActivity with selected URI
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            // Persist permission for future access
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            // Open ModelLoadingActivity which will automatically use the SAF picker
            context.startActivity(Intent(context, ModelLoadingActivity::class.java))
        }
    }

    CenterAlignedTopAppBar(title = {
        AnimatedTitle(
            modifier = Modifier, onShowDynamicWindow = {
                showDynamicWindow()
            })
    }, navigationIcon = {
        ActionButton(
            onClickListener = onMenuClick,
            icon = Icons.Default.Menu,
            modifier = Modifier.padding(start = rDp(6.dp))
        )
    }, actions = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActionButton(
                onClickListener = {
                    onStoreButtonClicked()
                }, icon = R.drawable.download, modifier = Modifier.padding(end = rDp(6.dp))
            )
            ActionButton(
                onClickListener = {
                    // Open ModelLoadingActivity which will launch SAF picker automatically
                    context.startActivity(Intent(context, ModelLoadingActivity::class.java))
                }, icon = R.drawable.load_model, modifier = Modifier.padding(end = rDp(6.dp))
            )
        }
    })
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BottomBar(
    onSettingsClick: () -> Unit,
    chatViewModel: ChatViewModel = hiltViewModel(),
    llmModelViewModel: LLMModelViewModel = hiltViewModel(),
    ragViewModel: RagViewModel = hiltViewModel(),
    pluginViewModel: PluginViewModel = hiltViewModel(),
    memoryViewModel: MemoryViewModel = hiltViewModel()
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
    val showRagOverlay by ragViewModel.showRagOverlay.collectAsStateWithLifecycle()
    val installedRags by ragViewModel.installedRags.collectAsStateWithLifecycle()
    val loadedRags by ragViewModel.loadedRags.collectAsStateWithLifecycle()
    val installedCount by ragViewModel.installedCount.collectAsStateWithLifecycle()
    val loadedCount by ragViewModel.loadedCount.collectAsStateWithLifecycle()
    val isRagEnabledForChat by ragViewModel.isRagEnabledForChat.collectAsStateWithLifecycle()
    val lastRagResults by ragViewModel.lastRagResults.collectAsStateWithLifecycle()

    // Plugin State
    val showPluginOverlay by pluginViewModel.showPluginOverlay.collectAsStateWithLifecycle()
    val registeredPlugins by pluginViewModel.registeredPlugins.collectAsStateWithLifecycle()
    val enabledPluginNames by pluginViewModel.enabledPluginNames.collectAsStateWithLifecycle()
    val expandedPluginIds by pluginViewModel.expandedPluginIds.collectAsStateWithLifecycle()
    val grammarMode by pluginViewModel.grammarMode.collectAsStateWithLifecycle()
    val multiTurnEnabled by pluginViewModel.multiTurnEnabled.collectAsStateWithLifecycle()
    val toolCallingConfig by pluginViewModel.toolCallingConfig.collectAsStateWithLifecycle()
    val isToolCallingModelLoaded by pluginViewModel.isToolCallingModelLoaded.collectAsStateWithLifecycle()

    // Memory State
    val showMemoryOverlay by memoryViewModel.showMemoryOverlay.collectAsStateWithLifecycle()
    val isMemoryEnabled by memoryViewModel.isMemoryEnabled.collectAsStateWithLifecycle()
    val memoryResults by memoryViewModel.memoryResults.collectAsStateWithLifecycle()
    val vaultStats by memoryViewModel.vaultStats.collectAsStateWithLifecycle()
    val memoryEntryCount by memoryViewModel.memoryEntryCount.collectAsStateWithLifecycle()

    // App settings
    val appSettingsDataStore = remember { com.dark.tool_neuron.data.AppSettingsDataStore(context) }
    val toolCallingEnabled by appSettingsDataStore.toolCallingEnabled.collectAsStateWithLifecycle(initialValue = true)

    // Coroutine scope for RAG queries
    val scope = rememberCoroutineScope()

    // Track if any model is loaded
    val isModelLoaded = currentModelID.isNotEmpty()

    // Password dialog state for bottom bar
    var showPasswordDialogBottomBar by remember { mutableStateOf(false) }
    var ragToLoadBottomBar by remember { mutableStateOf<String?>(null) }

    // SAF file picker for RAG installation
    val ragFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            ragViewModel.installRagFromUri(uri)
        }
    }

    // Password Dialog for encrypted RAGs in bottom bar
    if (showPasswordDialogBottomBar && ragToLoadBottomBar != null) {
        PasswordDialogBottomBar(
            onDismiss = {
                showPasswordDialogBottomBar = false
                ragToLoadBottomBar = null
            },
            onConfirm = { password ->
                ragViewModel.loadRag(ragToLoadBottomBar!!, password)
                showPasswordDialogBottomBar = false
                ragToLoadBottomBar = null
            }
        )
    }

    // RAG Overlay
    RagOverlayBottomSheet(
        show = showRagOverlay,
        installedRags = installedRags,
        loadedRags = loadedRags,
        installedCount = installedCount,
        loadedCount = loadedCount,
        onDismiss = { ragViewModel.hideRagOverlay() },
        onRagSelected = { ragViewModel.selectRag(it) },
        onRagToggleEnabled = { id, enabled -> ragViewModel.toggleRagEnabled(id, enabled) },
        onRagLoad = { ragId ->
            // Check if RAG is encrypted
            val rag = installedRags.find { it.id == ragId }
            if (rag?.isEncrypted == true) {
                ragToLoadBottomBar = ragId
                showPasswordDialogBottomBar = true
            } else {
                ragViewModel.loadRag(ragId)
            }
        },
        onRagUnload = { ragViewModel.unloadRag(it) },
        onRagDelete = { ragViewModel.deleteRag(it) },
        onOpenRagActivity = {
            ragViewModel.hideRagOverlay()
            context.startActivity(Intent(context, RagActivity::class.java))
        },
        onInstallRag = {
            ragFilePicker.launch(arrayOf("*/*"))
        }
    )

    // Plugin Overlay
    PluginOverlayBottomSheet(
        show = showPluginOverlay,
        plugins = registeredPlugins,
        enabledPluginNames = enabledPluginNames,
        expandedPluginIds = expandedPluginIds,
        grammarMode = grammarMode,
        multiTurnEnabled = multiTurnEnabled,
        toolCallingConfig = toolCallingConfig,
        onDismiss = { pluginViewModel.hidePluginOverlay() },
        onPluginToggle = { name, enabled ->
            pluginViewModel.togglePluginEnabled(name, enabled)
        },
        onPluginExpand = { name ->
            pluginViewModel.togglePluginExpanded(name)
        },
        onGrammarModeChange = { pluginViewModel.setGrammarMode(it) },
        onMultiTurnToggle = { pluginViewModel.setMultiTurnEnabled(it) },
        onMaxRoundsChange = { pluginViewModel.setMaxRounds(it) },
        onMaxTokensPerTurnChange = { pluginViewModel.setMaxTokensPerTurn(it) }
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
            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .padding(rDp(8.dp))
                    .heightIn(max = rDp(200.dp))
                    .background(
                        MaterialTheme.colorScheme.primary.copy(0.04f)
                            .compositeOver(MaterialTheme.colorScheme.background),
                        shape = RoundedCornerShape(rDp(8.dp))
                    ), contentPadding = PaddingValues(bottom = rDp(8.dp))
            ) {
                items(installedModels) { modelConfig ->
                    ModelListItem(
                        Modifier
                            .padding(top = rDp(8.dp))
                            .padding(horizontal = rDp(8.dp)),
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
                    .padding(horizontal = rDp(8.dp))
                    .padding(top = rDp(8.dp), bottom = rDp(10.dp))
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = rDp(200.dp))
                ) {
                    TextField(
                        value = value,
                        onValueChange = { value = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                text = when (currentGenerationType) {
                                    GenerationManager.ModelType.TEXT_GENERATION -> "Say Anything…"
                                    GenerationManager.ModelType.IMAGE_GENERATION -> "Describe the image you want…"
                                    GenerationManager.ModelType.AUDIO_GENERATION -> "Say Anything…"
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
                    enabledToolCount = enabledPluginNames.size,
                    isMemoryEnabled = isMemoryEnabled,
                    onRagChipClick = { ragViewModel.showRagOverlay() },
                    onToolChipClick = { pluginViewModel.showPluginOverlay() },
                    onMemoryChipClick = { memoryViewModel.toggleMemoryOverlay() }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(rDp(Standards.ActionIconSpace)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mode toggle switch (Text/Image)
                    ModeToggleSwitch(
                        isImageMode = currentGenerationType == GenerationManager.ModelType.IMAGE_GENERATION,
                        onModeChange = { isImageMode ->
                            if (isImageMode) {
                                chatViewModel.switchToImageGeneration()
                            } else {
                                chatViewModel.switchToTextGeneration()
                            }
                        },
                        textModelLoaded = isTextModelLoaded,
                        imageModelLoaded = isImageModelLoaded,
                        modifier = Modifier.padding(start = rDp(12.dp))
                    )

                    // Settings
                    ActionButton(
                        onClickListener = { onSettingsClick() },
                        icon = Icons.Outlined.Settings,
                        modifier = Modifier.padding(start = rDp(6.dp))
                    )

                    // Model selector
                    ActionToggleButton(
                        onCheckedChange = {
                            if (showModelList) {
                                chatViewModel.hideModelList()
                            } else {
                                chatViewModel.showModelList()
                            }
                        }, checked = showModelList, icon = R.drawable.ai_model
                    )

                    // RAG Button
                    ActionToggleButton(
                        onCheckedChange = {
                            if (showRagOverlay) {
                                ragViewModel.hideRagOverlay()
                            } else {
                                ragViewModel.showRagOverlay()
                            }
                        },
                        checked = showRagOverlay,
                        icon = R.drawable.rag
                    )

                    // Plugin Button (hidden when tool calling disabled in settings)
                    if (toolCallingEnabled) {
                        ActionToggleButton(
                            onCheckedChange = {
                                if (showPluginOverlay) {
                                    pluginViewModel.hidePluginOverlay()
                                } else {
                                    pluginViewModel.showPluginOverlay()
                                }
                            },
                            checked = showPluginOverlay,
                            enabled = isToolCallingModelLoaded,
                            icon = R.drawable.tools
                        )
                    }

                    // Memory Button
                    ActionToggleButton(
                        onCheckedChange = {
                            if (showMemoryOverlay) {
                                memoryViewModel.dismissMemoryOverlay()
                            } else {
                                memoryViewModel.toggleMemoryOverlay()
                            }
                        },
                        checked = showMemoryOverlay,
                        icon = R.drawable.memory_vault
                    )

                    Spacer(Modifier.weight(1f))

                    when (isGenerating) {
                        true -> {
                            ActionProgressButton(
                                onClickListener = {
                                    chatViewModel.stop()
                                },
                                modifier = Modifier.padding(end = rDp(12.dp)),
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
                                        when (currentGenerationType) {
                                            GenerationManager.ModelType.TEXT_GENERATION -> {
                                                // Check if RAG is enabled and there are loaded RAGs
                                                Log.d("HomeScreen", "Loaded RAGs count: ${loadedRags.size}")
                                                loadedRags.forEach { rag ->
                                                    Log.d("HomeScreen", "RAG: ${rag.name}, enabled: ${rag.isEnabled}, status: ${rag.status}")
                                                }

                                                val hasRags = loadedRags.isNotEmpty()
                                                val hasMemory = isMemoryEnabled

                                                if (hasRags || hasMemory) {
                                                    val userQuery = value
                                                    value = ""
                                                    scope.launch {
                                                        var combinedContext = ""

                                                        // Query Memory Vault if enabled
                                                        if (hasMemory) {
                                                            chatViewModel.setProcessingPhase("Querying Memory Vault...")
                                                            Log.d("HomeScreen", "Querying Memory Vault for: $userQuery")
                                                            val memoryContext = memoryViewModel.queryMemory(userQuery)
                                                            if (memoryContext.isNotBlank()) {
                                                                combinedContext += memoryContext
                                                                chatViewModel.setMemoryContext(
                                                                    memoryContext,
                                                                    memoryViewModel.memoryResults.value
                                                                )
                                                            }
                                                        }

                                                        // Query RAG if loaded
                                                        if (hasRags) {
                                                            chatViewModel.setProcessingPhase("Querying RAG...")
                                                            Log.d("HomeScreen", "Querying RAGs for: $userQuery")
                                                            val ragContext = ragViewModel.queryAndStoreResults(userQuery)
                                                            if (combinedContext.isNotBlank()) {
                                                                combinedContext += "\n$ragContext"
                                                            } else {
                                                                combinedContext += ragContext
                                                            }
                                                            chatViewModel.setRagContext(
                                                                ragContext.ifBlank { null },
                                                                ragViewModel.lastRagResults.value
                                                            )
                                                        }

                                                        // Update combined RAG context if memory added
                                                        if (hasRags && hasMemory && combinedContext.isNotBlank()) {
                                                            chatViewModel.setRagContext(
                                                                combinedContext.ifBlank { null },
                                                                ragViewModel.lastRagResults.value
                                                            )
                                                        } else if (!hasRags && hasMemory && combinedContext.isNotBlank()) {
                                                            chatViewModel.setRagContext(
                                                                combinedContext.ifBlank { null },
                                                                emptyList()
                                                            )
                                                        }

                                                        chatViewModel.setProcessingPhase("Generating Response...")
                                                        chatViewModel.sendTextMessage(userQuery)
                                                    }
                                                } else {
                                                    Log.d("HomeScreen", "No RAGs or Memory, sending message directly")
                                                    chatViewModel.clearRagContext()
                                                    chatViewModel.clearMemoryContext()
                                                    chatViewModel.sendTextMessage(value)
                                                    value = ""
                                                }
                                            }

                                            GenerationManager.ModelType.IMAGE_GENERATION -> {
                                                chatViewModel.sendImageRequest(value)
                                                value = ""
                                            }
                                            GenerationManager.ModelType.AUDIO_GENERATION -> {
                                                // TTS handled via settings overlay, not text input
                                            }
                                        }
                                    }
                                },
                                icon = R.drawable.send_chat,
                                shape = MaterialShapes.Ghostish.toShape(),
                                modifier = Modifier.padding(end = rDp(12.dp)),
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
    enabledToolCount: Int,
    isMemoryEnabled: Boolean,
    onRagChipClick: () -> Unit,
    onToolChipClick: () -> Unit,
    onMemoryChipClick: () -> Unit
) {
    val hasAnyActive = loadedRagCount > 0 || enabledToolCount > 0 || isMemoryEnabled

    AnimatedVisibility(visible = hasAnyActive) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = rDp(4.dp), vertical = rDp(2.dp)),
            horizontalArrangement = Arrangement.spacedBy(rDp(Standards.ChipSpacing))
        ) {
            if (loadedRagCount > 0) {
                StatusChip(
                    label = "$loadedRagCount RAG",
                    color = MaterialTheme.colorScheme.primary,
                    onClick = onRagChipClick
                )
            }
            if (enabledToolCount > 0) {
                StatusChip(
                    label = "$enabledToolCount Tools",
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
        shape = RoundedCornerShape(rDp(Standards.ChipCornerRadius)),
        modifier = Modifier.height(rDp(Standards.ChipHeight))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = rDp(Standards.ChipHorizontalPadding))
        ) {
            Box(
                modifier = Modifier
                    .size(rDp(6.dp))
                    .background(color, RoundedCornerShape(50))
            )
            Spacer(Modifier.width(rDp(4.dp)))
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
private fun PasswordDialogBottomBar(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter Password") },
        text = {
            Column {
                Text(
                    "This RAG is encrypted. Please enter the password to load it.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = if (showPassword) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = if (showPassword) "Hide password" else "Show password"
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (password.isNotBlank()) {
                        onConfirm(password)
                    }
                },
                enabled = password.isNotBlank()
            ) {
                Text("Load")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
