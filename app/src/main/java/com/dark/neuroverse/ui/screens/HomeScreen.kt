package com.dark.neuroverse.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Web
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.ai_module.workers.ModelManager
import com.dark.neuroverse.R
import com.dark.neuroverse.activity.ModelPropEditorActivity
import com.dark.neuroverse.model.ChatUiState
import com.dark.neuroverse.model.Message
import com.dark.neuroverse.model.Role
import com.dark.neuroverse.model.ToolOutput
import com.dark.neuroverse.ui.components.MarkdownText
import com.dark.neuroverse.ui.components.ModelLoadProgressBar
import com.dark.neuroverse.ui.components.RegenerateModelPickerDialog
import com.dark.neuroverse.ui.components.RobotDecodePlaceholder
import com.dark.neuroverse.ui.drawer.SettingsDrawerContent
import com.dark.neuroverse.ui.theme.Coral
import com.dark.neuroverse.ui.theme.CyberViolet
import com.dark.neuroverse.ui.theme.Mint
import com.dark.neuroverse.ui.theme.SkyBlue
import com.dark.neuroverse.ui.theme.SlateGrey
import com.dark.neuroverse.ui.theme.rDP
import com.dark.neuroverse.ui.theme.rSp
import com.dark.neuroverse.viewModel.chatViewModel.ChatScreenViewModel
import com.dark.neuroverse.viewModel.chatViewModel.ChattingViewModelFactory
import com.dark.neuroverse.viewModel.chatViewModel.TTSViewModel
import com.dark.neuroverse.viewModel.chatViewModel.TTSViewModelFactory
import com.dark.neuroverse.worker.ToolCallingManager
import com.dark.neuroverse.worker.UIStateManager
import com.dark.plugins.manager.PluginManager
import com.dark.plugins.model.Tools
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onRequestSettingsChange: () -> Unit,
    chatScreenViewModel: ChatScreenViewModel = viewModel(
        factory = ChattingViewModelFactory(LocalContext.current)
    ),
    ttsViewModel: TTSViewModel = viewModel(factory = TTSViewModelFactory(LocalContext.current)),
    onDataHubClick: () -> Unit,
    onPluginStoreClick: () -> Unit,
    onModelsClick: () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val modelState by chatScreenViewModel.modelLoadingState.collectAsStateWithLifecycle()
    val uiState by UIStateManager.uiState.collectAsStateWithLifecycle()

    // Optimized token tracking with throttling
    var tokenCount by remember { mutableIntStateOf(0) }
    var lastTokenUpdate by remember { mutableLongStateOf(0L) }
    var tkPerSecond by remember { mutableIntStateOf(0) }
    var lastDisplayUpdate by remember { mutableLongStateOf(0L) }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is ChatUiState.Error -> {
                Toast.makeText(
                    context, "Error: ${state.message}", Toast.LENGTH_LONG
                ).show()
            }

            is ChatUiState.DecodingStream -> {
                tokenCount++
                val currentTime = System.currentTimeMillis()

                // Throttle display updates to every 100ms
                if (currentTime - lastDisplayUpdate > 100) {
                    val elapsedTime = currentTime - lastTokenUpdate
                    if (elapsedTime > 0 && lastTokenUpdate > 0) {
                        tkPerSecond = (tokenCount * 1000 / elapsedTime).toInt()
                    }
                    lastDisplayUpdate = currentTime
                }

                if (lastTokenUpdate == 0L) {
                    lastTokenUpdate = currentTime
                }
            }

            else -> {
                // Reset counters when generation stops
                if (tokenCount > 0) {
                    tokenCount = 0
                    lastTokenUpdate = 0L
                    tkPerSecond = 0
                    lastDisplayUpdate = 0L
                }
            }
        }
    }

    BackHandler {
        if (context is Activity) {
            Log.d("HomeScreen", "Closing the app and removing the task...")
            context.finishAffinity()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState, drawerContent = {
            SettingsDrawerContent(
                modifier = Modifier,
                viewModel = chatScreenViewModel,
                onSettingsClick = onRequestSettingsChange,
                onChatSelected = { scope.launch { drawerState.close() } },
                onNewChatClick = {
                    chatScreenViewModel.newChat()
                },
                onDataHubClick = {
                    onDataHubClick()
                },
                onPluginStoreClick = {
                    onPluginStoreClick()
                },
                onModelsClick = {
                    onModelsClick()
                },
            )
        }) {
        Scaffold(modifier = Modifier
            .fillMaxSize()
            .imePadding(), topBar = {
            Column {
                TopBar(
                    chatScreenViewModel,
                    onMenu = { scope.launch { drawerState.open() } },
                    onLeftMenu = {
                        if (ModelManager.currentModel.value.modelName == "") {
                            Toast.makeText(context, "Load a Model First!..", Toast.LENGTH_LONG)
                                .show()
                        } else {
                            context.startActivity(
                                Intent(
                                    context, ModelPropEditorActivity::class.java
                                ).apply {
                                    putExtra(
                                        "modelName", ModelManager.currentModel.value.modelName
                                    )
                                })
                        }
                    })
                ModelLoadProgressBar(loadState = modelState)

                // Global loading indicator for UI state
                AnimatedVisibility(visible = uiState is ChatUiState.Loading) {
                    Column {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (uiState is ChatUiState.Loading) {
                            Text(
                                text = (uiState as ChatUiState.Loading).message,
                                modifier = Modifier.padding(
                                    horizontal = 16.dp, vertical = 4.dp
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = uiState is ChatUiState.GeneratingTitle) {
                    Column {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "Generating title…",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Optimized token rate display - only show when actually decoding
                AnimatedVisibility(visible = uiState is ChatUiState.DecodingStream && tkPerSecond > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Tokens/s: $tkPerSecond",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }, bottomBar = {
            BottomBar(viewModel = chatScreenViewModel, uiState = uiState)
        }) { innerPadding ->
            BodyContent(innerPadding, chatScreenViewModel, uiState, ttsViewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(
    viewModel: ChatScreenViewModel, onMenu: () -> Unit = {}, onLeftMenu: () -> Unit = {}
) {
    val title by viewModel.chatTitle.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()

    CenterAlignedTopAppBar(
        title = {
            if (messages.isEmpty()) {
                ModelSelection(viewModel, false)
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    ModelSelection(viewModel, true)
                }
            }
        }, navigationIcon = {
            IconButton(
                onClick = onMenu,
                shape = RoundedCornerShape(rDP(8.dp)),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(0.1f),
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(painter = painterResource(R.drawable.menu), contentDescription = "Menu")
            }
        }, actions = {
            IconButton(
                onClick = onLeftMenu,
                shape = RoundedCornerShape(rDP(8.dp)),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(0.1f),
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    painter = painterResource(R.drawable.settings), contentDescription = "New Chat"
                )
            }
        }, colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

@Composable
fun BodyContent(
    innerPadding: PaddingValues,
    viewModel: ChatScreenViewModel,
    uiState: ChatUiState,
    ttsViewModel: TTSViewModel
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Optimized auto-scroll management
    var userScrolled by remember { mutableStateOf(false) }

    val isAtBottom by remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            if (totalItems == 0) return@derivedStateOf true
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleIndex >= totalItems - 1
        }
    }

    // Only scroll when new messages arrive AND user hasn't manually scrolled
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && !userScrolled) {
            // Debounce scroll during streaming
            delay(50)
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    // Detect manual user scroll
    LaunchedEffect(listState.isScrollInProgress, isAtBottom) {
        if (listState.isScrollInProgress && !isAtBottom) {
            userScrolled = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        if (messages.isEmpty()) {
            EmptyStateContent(uiState)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(
                    bottom = rDP(96.dp),
                    top = rDP(8.dp),
                    start = rDP(24.dp),
                    end = rDP(24.dp)
                )
            ) {
                items(
                    items = messages,
                    key = { it.id },
                    contentType = { if (it.role == Role.User) "user" else "assistant" }
                ) { message ->
                    ChatBubble(
                        message = message,
                        viewModel = viewModel,
                        ttsViewModel = ttsViewModel,
                        uiState = uiState,
                    )
                    Spacer(Modifier.height(rDP(12.dp)))
                }
            }
        }

        // Jump to bottom FAB
        AnimatedVisibility(
            visible = !isAtBottom && messages.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = rDP(12.dp), bottom = rDP(20.dp))
        ) {
            SmallFloatingActionButton(
                onClick = {
                    userScrolled = false
                    scope.launch {
                        listState.animateScrollToItem(messages.lastIndex)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowDownward, contentDescription = "Jump to bottom"
                )
            }
        }
    }
}

@Composable
private fun EmptyStateContent(uiState: ChatUiState) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = rDP(24.dp)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (uiState) {
            is ChatUiState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(rDP(48.dp))
                )
                Spacer(Modifier.height(rDP(16.dp)))
                Text(
                    text = uiState.message,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = rSp(16.sp),
                    textAlign = TextAlign.Center
                )
            }

            is ChatUiState.Error -> {
                Icon(
                    painter = painterResource(R.drawable.menu),
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(rDP(48.dp))
                )
                Spacer(Modifier.height(rDP(16.dp)))
                Text(
                    text = "Something went wrong",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = rSp(18.sp),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = uiState.message,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = rSp(14.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = rDP(32.dp))
                )
            }

            else -> {
                Text(
                    text = "Ready to chat! Ask me anything. \uD83D\uDE0A \nToolNeuron",
                    color = SlateGrey,
                    fontSize = rSp(16.sp),
                    fontFamily = FontFamily.Serif,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun BottomBar(
    viewModel: ChatScreenViewModel, uiState: ChatUiState
) {
    var input by remember { mutableStateOf("") }
    val tools by ToolCallingManager.toolList.collectAsStateWithLifecycle()
    val selectedTools by ToolCallingManager.selectedTool.collectAsStateWithLifecycle()

    // Derive generation state from unified UI state
    val isGenerating = when (uiState) {
        is ChatUiState.Generating, is ChatUiState.DecodingStream, is ChatUiState.ExecutingTool -> true
        else -> false
    }

    // Determine if input should be disabled
    val inputEnabled = when (uiState) {
        is ChatUiState.Loading, is ChatUiState.Generating, is ChatUiState.DecodingStream, is ChatUiState.ExecutingTool -> false
        is ChatUiState.Error -> uiState.isRetryable
        else -> true
    }

    ChatInputBar(
        value = input,
        onValueChange = { if (inputEnabled) input = it },
        tools = tools,
        isGenerating = isGenerating,
        inputEnabled = inputEnabled,
        onRag = viewModel::setRag,
        onToolSelected = viewModel::selectTool,
        selectedTools = if (selectedTools.first.isEmpty()) emptyList() else listOf(selectedTools.second),
        onToolRemoved = { viewModel.unselectTool() },
        onSend = {
            when {
                isGenerating -> viewModel.stopGenerating()
                input.isNotBlank() -> {
                    viewModel.sendMessage(input)
                    input = ""
                }
            }
        })
}

@SuppressLint("StateFlowValueCalledInComposition")
@Composable
private fun ChatInputBar(
    value: String,
    tools: List<Pair<String, List<Tools>>>,
    selectedTools: List<Tools>,
    onToolSelected: (Pair<String, Tools>) -> Unit,
    onValueChange: (String) -> Unit,
    onRag: (Boolean) -> Unit,
    onSend: () -> Unit,
    onToolRemoved: (Tools) -> Unit,
    isGenerating: Boolean,
    inputEnabled: Boolean
) {
    var showToolsList by remember { mutableStateOf(false) }
    var isRag by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .background(MaterialTheme.colorScheme.surface),
        verticalArrangement = Arrangement.spacedBy(rDP(8.dp))
    ) {
        // Tools list
        AnimatedVisibility(visible = showToolsList) {
            ToolsList(
                tools = tools, onToolSelected = {
                    onToolSelected(it)
                    showToolsList = false
                })
        }

        // Tool selection and model button row
        Row(
            modifier = Modifier
                .padding(top = rDP(16.dp))
                .padding(horizontal = rDP(16.dp))
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(rDP(8.dp))
        ) {
            Button(
                onClick = {
                    if (inputEnabled) {
                        showToolsList = !showToolsList
                    }
                }, enabled = inputEnabled, colors = ButtonDefaults.buttonColors(
                    containerColor = if (showToolsList) SkyBlue else MaterialTheme.colorScheme.background,
                    contentColor = if (showToolsList) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary,
                ), shape = RoundedCornerShape(rDP(8.dp))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(rDP(8.dp))
                ) {
                    Icon(painterResource(R.drawable.tools), contentDescription = "Tools")
                    Text(text = "Tools", fontSize = rSp(14.sp))
                }
            }

            // Selected tools chips
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(rDP(4.dp))
            ) {
                items(selectedTools, key = { it.toolName }) { tool ->
                    ToolChip(
                        tool = tool,
                        onRemove = { onToolRemoved(tool) },
                        modifier = Modifier.animateItem()
                    )
                }
            }

            // RAG toggle button
            IconButton(
                onClick = {
                    if (inputEnabled) {
                        isRag = !isRag
                        onRag(isRag)
                    }
                },
                enabled = inputEnabled,
                modifier = Modifier.size(rDP(36.dp)),
                shape = RoundedCornerShape(rDP(8.dp)),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isRag) CyberViolet.copy(0.2f) else MaterialTheme.colorScheme.background,
                    contentColor = if (isRag) CyberViolet else MaterialTheme.colorScheme.primary,
                )
            ) {
                Icon(painterResource(R.drawable.database_zap), contentDescription = "Toggle RAG")
            }
        }

        // Input row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = rDP(8.dp))
                .padding(bottom = rDP(4.dp))
                .padding(end = rDP(18.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                enabled = inputEnabled,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = rDP(6.dp)),
                placeholder = {
                    Text(
                        text = if (inputEnabled) "Say Anything…" else "Processing...",
                        color = SlateGrey,
                        fontSize = rSp(14.sp)
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                textStyle = LocalTextStyle.current.copy(
                    color = MaterialTheme.colorScheme.primary, fontSize = rSp(15.sp)
                )
            )

            Spacer(Modifier.width(rDP(8.dp)))

            // Send/Stop button
            Box(
                modifier = Modifier
                    .size(rDP(36.dp))
                    .clip(CircleShape)
                    .background(
                        if (inputEnabled || isGenerating) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    )
                    .clickable(enabled = inputEnabled || isGenerating) {
                        if (ModelManager.isModelLoaded()) {
                            onSend()
                        } else {
                            Toast
                                .makeText(
                                    context,
                                    "Model is not loaded..! \nPlease Load Model..!",
                                    Toast.LENGTH_LONG
                                )
                                .show()
                        }
                    }, contentAlignment = Alignment.Center
            ) {
                when {
                    isGenerating -> {
                        Icon(
                            Icons.Rounded.Stop,
                            modifier = Modifier.padding(rDP(8.dp)),
                            contentDescription = "Stop",
                            tint = MaterialTheme.colorScheme.background
                        )
                        CircularProgressIndicator(
                            modifier = Modifier.size(rDP(28.dp)),
                            trackColor = MaterialTheme.colorScheme.background.copy(alpha = 0.3f),
                            color = MaterialTheme.colorScheme.background
                        )
                    }

                    else -> {
                        Icon(
                            painterResource(R.drawable.send_chat),
                            modifier = Modifier.padding(rDP(8.dp)),
                            contentDescription = "Send",
                            tint = if (inputEnabled) MaterialTheme.colorScheme.background
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolChip(
    tool: Tools, onRemove: () -> Unit, modifier: Modifier = Modifier
) {
    val accentColor = Color(0xFF0066FF)
    val backgroundColor = accentColor.copy(alpha = 0.2f)

    Box(
        modifier = modifier
            .size(ButtonDefaults.MinHeight)
            .background(color = backgroundColor, shape = RoundedCornerShape(rDP(8.dp)))
            .clickable { onRemove() }, contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Web, contentDescription = "Remove ${tool.toolName}", tint = accentColor
        )
    }
}

@Composable
fun ToolsList(
    modifier: Modifier = Modifier,
    tools: List<Pair<String, List<Tools>>>,
    onToolSelected: (Pair<String, Tools>) -> Unit
) {
    LazyColumn(
        modifier = modifier.heightIn(min = rDP(100.dp), max = rDP(300.dp)),
        contentPadding = PaddingValues(vertical = rDP(8.dp))
    ) {
        tools.forEach { (pluginName, toolList) ->
            item {
                Text(
                    text = pluginName,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = rSp(18.sp)),
                    modifier = Modifier.padding(horizontal = rDP(16.dp), vertical = rDP(8.dp))
                )
            }

            items(toolList) { tool ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = rDP(32.dp), vertical = rDP(4.dp))
                        .clickable { onToolSelected(Pair(pluginName, tool)) },
                    elevation = CardDefaults.cardElevation(rDP(0.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                ) {
                    Column(modifier = Modifier.padding(rDP(12.dp))) {
                        Text(
                            text = tool.toolName,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = rSp(16.sp))
                        )
                        if (tool.description.isNotBlank()) {
                            Text(
                                text = tool.description,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = rSp(13.sp)),
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(
    message: Message,
    viewModel: ChatScreenViewModel,
    ttsViewModel: TTSViewModel,
    uiState: ChatUiState,
) {
    val isUser = message.role == Role.User

    var debouncedIsStreaming by remember { mutableStateOf(false) }

    val isWaitingForFirstToken = message.text.isEmpty() && when (uiState) {
        is ChatUiState.Generating -> uiState.messageId == message.id
        else -> false
    }

    val isThisMessageStreaming = when (uiState) {
        is ChatUiState.DecodingStream -> uiState.messageId == message.id
        is ChatUiState.Generating -> uiState.messageId == message.id && uiState.isFirstToken
        else -> false
    }

    val isThisMessageExecutingTool = when (uiState) {
        is ChatUiState.ExecutingTool -> uiState.messageId == message.id
        else -> false
    }

    LaunchedEffect(isThisMessageStreaming) {
        if (isThisMessageStreaming) {
            // Immediately set to streaming mode (use plain Text)
            debouncedIsStreaming = true
        } else {
            // Wait 300ms before switching to markdown mode
            delay(500)
            debouncedIsStreaming = false
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column {
            // Show thinking section for assistant messages
            val showThinking = !isUser && !message.thought.isNullOrBlank()
            if (showThinking) {
                ThinkingChatUI(message)
                Spacer(Modifier.height(rDP(8.dp)))
            }

            // Main message content based on role
            when (message.role) {
                Role.User -> UserChatUI(message) {
                    viewModel.deleteMessage(it)
                }

                Role.Assistant -> if (isWaitingForFirstToken) {
                    DecodingPlaceholder()
                } else {
                    RegularChatUI(
                        message = message,
                        viewModel = viewModel,
                        ttsViewModel = ttsViewModel,
                        isStreaming = debouncedIsStreaming
                    )
                }

                Role.Tool -> ToolChatUI(
                    message = message,
                    isDecoding = isThisMessageExecutingTool,
                )
            }
        }
    }
}

@Composable
private fun DecodingPlaceholder() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = rDP(8.dp)),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Animated dots
        val infiniteTransition = rememberInfiniteTransition(label = "decoding")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alpha"
        )

        Text(
            text = "Decoding",
            color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic
        )

        Text(
            text = "...",
            color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = rDP(4.dp))
        )
    }
}

@Composable
private fun UserChatUI(
    message: Message, onMessageDelete: (String) -> Unit = {}
) {
    val radius = with(LocalDensity.current) { rDP(12.dp) }
    val corner = RoundedCornerShape(radius)
    val actionIconSize = rDP(14.dp)

    Column(
        modifier = Modifier.widthIn(max = rDP(240.dp)),
        horizontalAlignment = Alignment.End
    ) {
        // Message text
        Text(
            modifier = Modifier
                .clip(corner)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                .padding(horizontal = rDP(14.dp), vertical = rDP(8.dp)),
            text = message.text,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(rDP(8.dp)))

        Icon(
            Icons.Rounded.DeleteOutline,
            contentDescription = "Delete",
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier
                .padding(end = rDP(8.dp))
                .size(actionIconSize)
                .clickable { onMessageDelete(message.id) })
    }
}


@Composable
private fun RegularChatUI(
    message: Message,
    viewModel: ChatScreenViewModel,
    ttsViewModel: TTSViewModel,
    isStreaming: Boolean
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val isPlayingAudio by ttsViewModel.isPlaying.collectAsStateWithLifecycle()
    var showRegenerateDialog by remember { mutableStateOf(false) }
    val actionIconSize = rDP(14.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = rDP(4.dp))
    ) {
        Crossfade(isStreaming) {
            when(it){
                true -> {
                    Text(
                        text = message.text,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                false -> {
                    MarkdownText(
                        text = message.text,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Spacer(Modifier.height(rDP(10.dp)))

        // Action buttons
        Row(horizontalArrangement = Arrangement.spacedBy(rDP(12.dp))) {
            // Copy button
            Icon(
                painter = painterResource(R.drawable.copy),
                contentDescription = "Copy text",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier
                    .size(actionIconSize)
                    .clickable {
                        clipboardManager.setText(AnnotatedString(message.text))
                        Toast
                            .makeText(
                                context, "Copied to clipboard!", Toast.LENGTH_SHORT
                            )
                            .show()
                    })

            Icon(
                painter = painterResource(if (isPlayingAudio) R.drawable.stop else R.drawable.speaker),
                contentDescription = "Play/Stop audio",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier
                    .size(actionIconSize)
                    .clickable {
                        CoroutineScope(Dispatchers.IO).launch {
                            if (isPlayingAudio) {
                                ttsViewModel.onClickStop()
                            } else {
                                ttsViewModel.initTTS(context)
                                ttsViewModel.onGenerate(message.text, 3)
                            }
                        }
                    })

            // Regenerate button
            Icon(
                painter = painterResource(R.drawable.regen),
                contentDescription = "Regenerate response",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier
                    .size(actionIconSize)
                    .clickable { showRegenerateDialog = true })

            // Share button
            Icon(
                imageVector = Icons.Rounded.Share,
                contentDescription = "Share",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier
                    .size(actionIconSize)
                    .clickable {
                        val shareIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, message.text)
                            type = "text/plain"
                        }
                        context.startActivity(
                            Intent.createChooser(
                                shareIntent, "Share message"
                            )
                        )
                    })

            Icon(
                painterResource(R.drawable.resource_continue),
                contentDescription = "Continue",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier
                    .size(actionIconSize)
                    .clickable {
                        CoroutineScope(Dispatchers.IO).launch {
                            viewModel.continueGenerating(message)
                        }
                    })

            // Delete button
            Icon(
                Icons.Rounded.DeleteOutline,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier
                    .size(actionIconSize)
                    .clickable {
                        viewModel.deleteMessage(message.id)
                    })
        }
    }

    // Regenerate dialog
    if (showRegenerateDialog) {
        RegenerateModelPickerDialog(
            viewModel = viewModel, messageId = message.id
        ) {
            showRegenerateDialog = false
        }
    }
}

@Composable
private fun ToolChatUI(
    message: Message,
    isDecoding: Boolean,
) {
    if (isDecoding) {
        RobotDecodePlaceholder(
            active = true, modifier = Modifier.fillMaxWidth()
        )
    } else {
        Column {
            // Tool identifier tag
            AssistTag(message.tool?.toolName ?: "Unknown Tool")
            Spacer(Modifier.height(rDP(6.dp)))
            val toolOutput = message.tool?.toolOutput
            val out = remember(toolOutput) {
                try {
                    val outputString = toolOutput?.output ?: ""

                    when {
                        outputString.isBlank() -> {
                            JSONObject().apply {
                                put("err", "Tool not executed yet")
                            }
                        }

                        else -> {
                            JSONObject(outputString)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ToolChatUI", "Parse error: ${e.message}", e)
                    JSONObject().apply {
                        put("err", "Failed to parse: ${e.message}")
                    }
                }
            }

            when (message.tool?.toolOutput == null) {
                true -> {
                    Card(elevation = CardDefaults.cardElevation(rDP(0.dp))) {
                        PluginManager.currentPlugin.collectAsState().value?.api?.ToolPreviewContent(
                            out.toString()
                        )
                    }
                }

                false -> {
                    // Tool output available
                    ToolOutputToggle(toolOutput = message.tool.toolOutput, out = out)
                }
            }
        }
    }
}

@Composable
private fun AssistTag(name: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(rDP(10.dp)))
            .background(Color(0x1A3B82F6))
            .padding(horizontal = rDP(8.dp), vertical = rDP(4.dp))
    ) {
        Text(
            text = "via $name",
            fontSize = rSp(12.sp),
            color = Color(0xFF2563EB),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ThinkingChatUI(message: Message) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(120)) + expandVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow
            )
        ) + slideInVertically(initialOffsetY = { -it / 6 }),
        exit = fadeOut(animationSpec = tween(120)) + shrinkVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium
            )
        ) + slideOutVertically(targetOffsetY = { -it / 6 })
    ) {
        var showThinkingText by remember { mutableStateOf(false) }

        Spacer(Modifier.height(rDP(6.dp)))
        Box(modifier = Modifier
            .fillMaxWidth()
            .clickable { showThinkingText = !showThinkingText }
            .clip(RoundedCornerShape(rDP(8.dp)))
            .background(Color(0xFF0F172A))
            .border(rDP(1.dp), Color(0xFF334155), RoundedCornerShape(rDP(8.dp)))
            .animateContentSize(
                animationSpec = tween(
                    durationMillis = 180, easing = FastOutSlowInEasing
                )
            )) {
            Text(
                text = if (showThinkingText) "Thought:\n${message.thought}" else "Thinking... (tap to expand)",
                modifier = Modifier.padding(rDP(8.dp)),
                color = Color(0xFFCBD5E1),
                fontSize = rSp(12.sp),
                lineHeight = rSp(18.sp),
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun ToolOutputToggle(toolOutput: ToolOutput, out: JSONObject) {
    var expanded by remember { mutableStateOf(false) }

    val shimmerX by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0f, targetValue = 1f, animationSpec = infiniteRepeatable(
            tween(1200, easing = LinearEasing), RepeatMode.Restart
        ), label = "shimmerFloat"
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            Coral.copy(alpha = 0.25f), Coral, Coral.copy(alpha = 0.25f)
        ), start = Offset.Zero, end = Offset(1000f * shimmerX + 1f, 0f)
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(rDP(8.dp))
    ) {
        // Toggle Button
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(rDP(5.dp)))
                .border(
                    1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(rDP(5.dp))
                )
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = shimmerBrush, alpha = 0.25f, blendMode = BlendMode.SrcOver
                    )
                }
                .clickable { expanded = !expanded }
                .padding(horizontal = rDP(12.dp), vertical = rDP(6.dp))) {
            Text(
                text = "Show Tool Output",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Animated expansion for Tool Output
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            ToolOutputContent(toolOutput = toolOutput, out = out)
        }
    }
}

@Composable
fun ToolOutputContent(
    modifier: Modifier = Modifier, toolOutput: ToolOutput, out: JSONObject
) {
    val context = LocalContext.current
    val runningPlugin = PluginManager.runPlugin(context, toolOutput.toolName, toolOutput.output)

    if (out.has("err")) {
        Card(
            elevation = CardDefaults.cardElevation(rDP(0.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Box(modifier = Modifier.padding(vertical = rDP(16.dp), horizontal = rDP(34.dp))) {
                Text(
                    text = out.getString("err"),
                    color = Color(0xFFEF4444),
                    fontSize = rSp(12.sp),
                    lineHeight = rSp(18.sp),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    } else {
        Card(modifier = modifier) {
            runningPlugin.api?.ToolPreviewContent(toolOutput.output)
        }
    }
}

@Composable
fun ModelSelection(viewModel: ChatScreenViewModel, isCompact: Boolean) {
    var showDialog by remember { mutableStateOf(false) }
    val modelList by viewModel.modelList.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val uiState by UIStateManager.uiState.collectAsStateWithLifecycle()
    val isGeneratingTitle = uiState is ChatUiState.GeneratingTitle

    val selectedModelName = remember(selectedModel) {
        if (selectedModel.modelName == "") "Select Model"
        else selectedModel.modelName
    }

    Column {
        if (isCompact) {
            IconButton(
                onClick = { if (!isGeneratingTitle) showDialog = true },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(0.1f),
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(rDP(8.dp)),
            ) {
                Icon(Icons.Outlined.SmartToy, "Model")
            }
        } else {
            Button(
                onClick = { if (!isGeneratingTitle) showDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(0.1f),
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(rDP(8.dp)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.SmartToy, "Model")
                    Spacer(modifier = Modifier.width(rDP(8.dp)))
                    Text(
                        text = selectedModelName, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(rDP(8.dp)))
                    Icon(Icons.Default.KeyboardArrowDown, "Expand")
                }
            }
        }

        if (showDialog) {
            Dialog(onDismissRequest = { showDialog = false }) {
                Card(
                    shape = RoundedCornerShape(rDP(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(rDP(16.dp))
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = "Select Model",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = rDP(12.dp))
                        )

                        LazyColumn(
                            modifier = Modifier.heightIn(min = rDP(150.dp), max = rDP(320.dp)),
                            contentPadding = PaddingValues(vertical = rDP(8.dp))
                        ) {
                            items(modelList) { model ->
                                val isSelected = model.modelName == selectedModel.modelName

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = rDP(6.dp))
                                        .clickable {
                                            Log.d("ModelSelection", "Selected model: $model")
                                            viewModel.selectModel(model)
                                        }, colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) Mint.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.background
                                    ), elevation = CardDefaults.cardElevation(rDP(0.dp))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(rDP(14.dp))
                                            .fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = model.modelName,
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontSize = rSp(16.sp)
                                                )
                                            )
                                            Text(
                                                text = if (isSelected) "Currently Loaded" else "Tap to Load",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = if (isSelected) Mint else Color.Gray
                                                )
                                            )
                                        }
                                        Spacer(Modifier.weight(1f))
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Loaded",
                                                tint = Mint,
                                                modifier = Modifier.size(rDP(20.dp))
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(rDP(12.dp)))
                        Button(
                            onClick = { showDialog = false },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(rDP(8.dp))
                        ) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
}