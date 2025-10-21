package com.dark.neuroverse.ui.screens.home

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
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
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
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

    val imm =
        LocalContext.current.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    val token = LocalView.current.windowToken
    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) {
            imm.hideSoftInputFromWindow(token, 0)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState, drawerContent = {
            ModalDrawerSheet(drawerState) {
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
            }
        }) {
        Scaffold(modifier = Modifier.fillMaxSize(), topBar = {
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
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                BodyContent(innerPadding, chatScreenViewModel, ttsViewModel)
            }
        }
    }
}

@Composable
fun BodyContent(
    innerPadding: PaddingValues, viewModel: ChatScreenViewModel, ttsViewModel: TTSViewModel
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var userScrolled by remember { mutableStateOf(false) }

    val isAtBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            if (info.totalItemsCount == 0) return@derivedStateOf true
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= info.totalItemsCount - 1
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && !userScrolled) {
            listState.scrollToItem(messages.lastIndex)
        }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && !isAtBottom) {
            userScrolled = true
        }
    }

    LaunchedEffect(messages.isEmpty()) {
        if (messages.isEmpty()) {
            userScrolled = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        if (messages.isEmpty()) {
            EmptyStateContent(viewModel.uiState.collectAsStateWithLifecycle().value)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(
                    bottom = rDP(96.dp), top = rDP(8.dp), start = rDP(24.dp), end = rDP(24.dp)
                )
            ) {
                items(items = messages, key = { it.id }, contentType = { it.role }) { message ->
                    ChatBubble(
                        message = message, viewModel = viewModel, ttsViewModel = ttsViewModel
                    )
                    Spacer(Modifier.height(rDP(12.dp)))
                }
            }
        }

        AnimatedVisibility(
            visible = !isAtBottom && messages.isNotEmpty(),
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = rDP(12.dp), bottom = rDP(20.dp))
        ) {
            SmallFloatingActionButton(
                onClick = {
                    userScrolled = false
                    scope.launch {
                        listState.scrollToItem(messages.lastIndex)
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

