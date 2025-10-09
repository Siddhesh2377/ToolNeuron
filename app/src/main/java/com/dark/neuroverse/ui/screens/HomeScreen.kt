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
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Web
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Share
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
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
import com.dark.neuroverse.viewModel.chatViewModel.ChatUiState
import com.dark.neuroverse.viewModel.chatViewModel.ChattingViewModelFactory
import com.dark.plugins.manager.PluginManager
import com.dark.plugins.model.Tools
import com.dark.userdata.helpers.MemoryDataTags
import com.mp.data_hub_lib.manager.DataHubManager
import com.mp.data_hub_lib.model.BrainDoc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onRequestSettingsChange: () -> Unit, viewModel: ChatScreenViewModel = viewModel(
        factory = ChattingViewModelFactory(LocalContext.current)
    ), onDataHubClick: () -> Unit, onPluginStoreClick: () -> Unit, onModelsClick: () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val modelState by viewModel.modelLoadingState.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Token tracking
    val tokenCount: MutableStateFlow<Int> = MutableStateFlow(0)
    val lastTokenUpdate: MutableStateFlow<Long> = MutableStateFlow(System.currentTimeMillis())
    val tkPerSecond by remember { mutableStateOf(MutableStateFlow(0)) }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is ChatUiState.Error -> {
                Toast.makeText(
                    context, "Error: ${state.message}", Toast.LENGTH_LONG
                ).show()
            }

            is ChatUiState.DecodingStream -> {
                tokenCount.value++
                lastTokenUpdate.value = System.currentTimeMillis()
            }

            else -> {
                if (lastTokenUpdate.value > 0) {
                    val currentTime = System.currentTimeMillis()
                    val elapsedTime = currentTime - lastTokenUpdate.value

                    if (elapsedTime > 0) { // Ensure elapsedTime is greater than zero to avoid division by zero
                        tkPerSecond.value = (tokenCount.value / (elapsedTime / 1000.0)).toInt()
                        tokenCount.value = 0
                        lastTokenUpdate.value = currentTime
                    }
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
                viewModel = viewModel,
                onSettingsClick = onRequestSettingsChange,
                onChatSelected = { scope.launch { drawerState.close() } },
                onNewChatClick = {
                    viewModel.newChat()
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
                    viewModel,
                    onMenu = { scope.launch { drawerState.open() } },
                    onLeftMenu = {
                        if(ModelManager.currentModel.value.modelName == ""){
                            Toast.makeText(context, "Load a Model First!..", Toast.LENGTH_LONG).show()
                        }else{
                            context.startActivity(Intent(context, ModelPropEditorActivity::class.java).apply {
                                putExtra("modelName", ModelManager.currentModel.value.modelName)
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
                                text = (uiState as ChatUiState.Loading).operation,
                                modifier = Modifier.padding(
                                    horizontal = 16.dp, vertical = 4.dp
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Token rate display
                AnimatedVisibility(visible = uiState is ChatUiState.DecodingStream) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Tokens/s: ${tkPerSecond.collectAsState().value}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }, bottomBar = {
            BottomBar(viewModel = viewModel, uiState = uiState)
        }) { innerPadding ->
            BodyContent(innerPadding, viewModel, uiState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(
    viewModel: ChatScreenViewModel, onMenu: () -> Unit = {}, onLeftMenu: () -> Unit = {}
) {
    val title by viewModel.chatTitle.collectAsStateWithLifecycle()

    CenterAlignedTopAppBar(
        title = {
        Crossfade(viewModel.messages.collectAsStateWithLifecycle().value.isEmpty()) {
            if (it) {
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
    innerPadding: PaddingValues, viewModel: ChatScreenViewModel, uiState: ChatUiState
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Auto-scroll management
    var autoScrollEnabled by remember { mutableStateOf(true) }

    val isAtBottom by remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            if (totalItems == 0) return@derivedStateOf true
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleIndex >= totalItems - 1
        }
    }

    // Disable auto-scroll when user scrolls up manually
    LaunchedEffect(Unit) {
        snapshotFlow { listState.isScrollInProgress to isAtBottom }.collect { (scrolling, atBottom) ->
            if (scrolling && !atBottom) {
                autoScrollEnabled = false
            }
        }
    }

    // Auto-scroll to new messages when enabled
    LaunchedEffect(messages.size, autoScrollEnabled) {
        if (messages.isNotEmpty() && autoScrollEnabled) {
            scope.launch {
                listState.animateScrollToItem(messages.lastIndex)
            }
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = rDP(16.dp)),
                state = listState,
                contentPadding = PaddingValues(
                    bottom = rDP(96.dp), top = rDP(8.dp), start = rDP(8.dp), end = rDP(8.dp)
                )
            ) {
                items(
                    items = messages,
                    key = { it.id },
                    contentType = { if (it.role == Role.User) "user" else "assistant" }) { message ->
                    ChatBubble(
                        message = message,
                        viewModel = viewModel,
                        uiState = uiState,
                    )
                    Spacer(Modifier.height(rDP(18.dp)))
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
                    autoScrollEnabled = true
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
                    text = uiState.operation,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = rSp(16.sp),
                    textAlign = TextAlign.Center
                )
            }

            is ChatUiState.Error -> {
                Icon(
                    painter = painterResource(R.drawable.menu), // Replace with error icon
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
    val tools by viewModel.toolList.collectAsStateWithLifecycle()
    val selectedTools by viewModel.selectedTools.collectAsStateWithLifecycle()

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
        onToolRemoved = { viewModel.unSelectTool() },
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
                            Toast.makeText(
                                context,
                                "Model is not loaded..! \nPlease Load Model..!",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }, contentAlignment = Alignment.Center
            ) {
                when {
                    isGenerating -> {
                        Icon(
                            Icons.Default.Stop,
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
    uiState: ChatUiState,
) {
    val isUser = message.role == Role.User

    // Check if this specific message is being processed
    val isThisMessageDecoding = when (uiState) {
        is ChatUiState.DecodingStream -> uiState.messageId == message.id
        is ChatUiState.Generating -> uiState.messageId == message.id && uiState.isFirstToken
        is ChatUiState.ExecutingTool -> uiState.messageId == message.id
        else -> false
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
                Role.User -> UserChatUI(message)
                Role.Assistant -> RegularChatUI(message, viewModel)
                Role.Tool -> ToolChatUI(
                    message = message,
                    isDecoding = isThisMessageDecoding,
                )
            }
        }
    }
}

@Composable
private fun UserChatUI(message: Message) {
    val radius = with(LocalDensity.current) { rDP(12.dp) }
    val corner = RoundedCornerShape(radius)

    Box(
        modifier = Modifier
            .widthIn(max = rDP(240.dp))
            .clip(corner)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
            .padding(horizontal = rDP(14.dp), vertical = rDP(8.dp)),
        contentAlignment = Alignment.CenterEnd
    ) {
        Text(
            text = message.text,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun RegularChatUI(
    message: Message, viewModel: ChatScreenViewModel
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val  uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentMsgId by viewModel.currentMsgId.collectAsStateWithLifecycle()

    Crossfade(targetState = message.text.isEmpty(), label = "assistant-content") { empty ->
        when (empty) {
            true -> {
                RobotDecodePlaceholder(
                    active = true, modifier = Modifier.fillMaxWidth()
                )
            }

            false -> {
                var showRegenerateDialog by remember { mutableStateOf(false) }
                val actionIconSize = rDP(16.dp)
                val isStreaming = uiState is ChatUiState.DecodingStream && currentMsgId == message.id

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = rDP(14.dp))
                ) {
                    MarkdownText(
                        text = message.text,
                        isStreaming = isStreaming,
                        color = MaterialTheme.colorScheme.primary,
                        style = TextStyle.Default.copy(
                            fontSize = rSp(13.sp), lineHeight = rSp(20.sp)
                        )
                    )

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
                                    Toast.makeText(
                                        context, "Copied to clipboard!", Toast.LENGTH_SHORT
                                    ).show()
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

                        // Save Memory button
                        Icon(
                            painter = painterResource(R.drawable.memory_stick),
                            contentDescription = "Save To Memory",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            modifier = Modifier
                                .size(actionIconSize)
                                .clickable {
                                    CoroutineScope(Dispatchers.IO).launch {
                                        Log.d("HomeScreen", "SaveMemory")
                                        DataHubManager.reinitializeEmbeddingModel()
                                        val data = DataHubManager.getEmbeddingManager()
                                            .getEmbedding(message.text)
                                        Log.d("HomeScreen", "SaveMemory: $data")
                                        val memory = listOf(
                                            BrainDoc(
                                                id = UUID.randomUUID().toString(),
                                                text = message.text,
                                                embedding = data.getOrNull()?.toList()
                                                    ?: emptyList()
                                            )
                                        )
                                        Log.d("HomeScreen", "SaveMemory: $memory")
                                        viewModel.addMessageInMemory(memory, MemoryDataTags.Other)
                                        Log.d("HomeScreen", "SaveMemory done")
                                    }
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
        }
    }
}

@Composable
private fun ToolChatUI(
    message: Message,
    isDecoding: Boolean,
) {
    Crossfade(targetState = isDecoding, label = "tool-content") { decoding ->
        when (decoding) {
            true -> {
                RobotDecodePlaceholder(
                    active = true, modifier = Modifier.fillMaxWidth()
                )
            }

            false -> {
                Column {
                    // Tool identifier tag
                    AssistTag(message.tool?.toolName ?: "Unknown Tool")
                    Spacer(Modifier.height(rDP(6.dp)))

                    val pluginLoading by PluginManager.currentPlugin.collectAsState(initial = null)

                    when (message.tool?.toolOutput == null) {
                        true -> {
                            val isLoading = pluginLoading == null
                            Crossfade(
                                targetState = isLoading, label = "plugin-loading"
                            ) { loading ->
                                if (loading) {
                                    // Loading state for plugin
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surface
                                        ),
                                        elevation = CardDefaults.cardElevation(rDP(0.dp)),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(rDP(24.dp)),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(rDP(12.dp))
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(rDP(32.dp)),
                                                strokeWidth = rDP(3.dp)
                                            )
                                            Text(
                                                text = "Loading Plugin\n${message.tool?.toolName ?: "Unknown"}",
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontSize = rSp(14.sp),
                                                    fontFamily = FontFamily.Serif
                                                ),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                } else {
                                    // Plugin content
                                    Card(elevation = CardDefaults.cardElevation(rDP(0.dp))) {
                                        PluginManager.currentPlugin.collectAsState().value?.api?.content()
                                            ?.invoke()
                                    }
                                }
                            }
                        }

                        false -> {
                            // Tool output available
                            ToolOutputToggle(toolOutput = message.tool.toolOutput)
                        }
                    }
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
fun ToolOutputToggle(toolOutput: ToolOutput) {
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
            ToolOutputContent(toolOutput = toolOutput)
        }
    }
}

@Composable
fun ToolOutputContent(
    modifier: Modifier = Modifier, toolOutput: ToolOutput
) {
    val context = LocalContext.current
    val runningPlugin = PluginManager.runPlugin(context, toolOutput.toolName, toolOutput.output)

    Card(modifier = modifier) {
        runningPlugin.api?.ToolPreviewContent(toolOutput.output)
    }
}

@Composable
fun ModelSelection(viewModel: ChatScreenViewModel, isCompact: Boolean) {
    var showDialog by remember { mutableStateOf(false) }
    val modelList by viewModel.modelList.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val isLoading by viewModel.isModelLoading.collectAsStateWithLifecycle()
    var selectedModelName by remember { mutableStateOf("") }

    LaunchedEffect(selectedModel) {
        selectedModelName = if (selectedModel.modelName == "") "Select Model"
        else selectedModel.modelName
    }

    Column {
        Crossfade(isCompact) {
            when (it) {
                true -> {
                    IconButton(
                        onClick = { showDialog = true },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(0.1f),
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(rDP(8.dp)),
                    ) {
                        Icon(Icons.Outlined.SmartToy, "Model")
                    }
                }

                false -> {
                    Button(
                        onClick = { showDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(0.1f),
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(rDP(8.dp)),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.SmartToy, "Model")
                            Spacer(modifier = Modifier.width(rDP(8.dp)))
                            Crossfade(selectedModelName) { modelName ->
                                Text(
                                    text = modelName, maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(rDP(8.dp)))
                            Icon(Icons.Default.KeyboardArrowDown, "Expand")
                        }
                    }
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
                                        .clickable(enabled = !isLoading) {
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
                                        when {
                                            isLoading && model.modelName == modelList.find { it.modelName == selectedModel.modelName }?.modelName -> {
                                                CircularProgressIndicator(
                                                    strokeWidth = 2.dp,
                                                    modifier = Modifier.size(rDP(20.dp)),
                                                    color = Mint
                                                )
                                            }

                                            isSelected -> {
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