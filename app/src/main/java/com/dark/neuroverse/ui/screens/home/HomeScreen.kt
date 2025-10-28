package com.dark.neuroverse.ui.screens.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.ai_module.workers.ModelManager
import com.dark.neuroverse.activity.ModelPropEditorActivity
import com.dark.neuroverse.model.ChatUiState
import com.dark.neuroverse.ui.components.ModelLoadProgressBar
import com.dark.neuroverse.ui.components.TTSPlaybackBarCompact
import com.dark.neuroverse.ui.drawer.SettingsDrawerContent
import com.dark.neuroverse.ui.theme.rDP
import com.dark.neuroverse.viewModel.chatViewModel.ChatScreenViewModel
import com.dark.neuroverse.viewModel.chatViewModel.ChattingViewModelFactory
import com.dark.neuroverse.viewModel.chatViewModel.TTSViewModel
import com.dark.neuroverse.worker.ToolCallingManager
import com.dark.neuroverse.worker.UIStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onRequestSettingsChange: () -> Unit,
    chatScreenViewModel: ChatScreenViewModel = viewModel(
        factory = ChattingViewModelFactory(LocalContext.current)
    ),
    ttsViewModel: TTSViewModel = viewModel(),
    onDataHubClick: () -> Unit,
    onPluginStoreClick: () -> Unit,
    onModelsClick: () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val modelState by chatScreenViewModel.modelLoadingState.collectAsStateWithLifecycle()
    val uiState by UIStateManager.uiState.collectAsStateWithLifecycle()
    val isDialog by chatScreenViewModel.isDialogSelected.collectAsStateWithLifecycle()

    // Token rate state (throttled updates)
    var tokenCount by remember { mutableIntStateOf(0) }
    var lastTokenUpdate by remember { mutableLongStateOf(0L) }
    var tkPerSecond by remember { mutableIntStateOf(0) }
    var lastDisplayUpdate by remember { mutableLongStateOf(0L) }

    // Snackbar host for errors - positioned at TOP
    val snackbarHostState = remember { SnackbarHostState() }

    // Handle error states with top snackbar
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is ChatUiState.Error -> {
                // Show error at top with action
                val message = state.message.ifBlank { "Unknown error" }
                val actionLabel = if (state.isRetryable) "Retry" else null

                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = actionLabel,
                        duration = if (state.isRetryable) SnackbarDuration.Long
                        else SnackbarDuration.Short
                    )

                    if (result == SnackbarResult.ActionPerformed && state.isRetryable) {
                        // Dismiss error and allow retry
                        UIStateManager.setStateIdle()
                    }
                }
            }

            is ChatUiState.DecodingStream -> {
                // Token counting occurs only while decoding stream
                tokenCount++
                val currentTime = System.currentTimeMillis()

                // initialize lastTokenUpdate when first token arrives
                if (lastTokenUpdate == 0L) lastTokenUpdate = currentTime

                // Throttle display updates to every 100ms
                if (currentTime - lastDisplayUpdate > 100) {
                    val elapsedTime = currentTime - lastTokenUpdate
                    if (elapsedTime > 0L) {
                        tkPerSecond = (tokenCount * 1000L / elapsedTime).toInt()
                    }
                    lastDisplayUpdate = currentTime
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

    LaunchedEffect(Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                chatScreenViewModel.refreshModelList()
                ttsViewModel.initTTS()
            } catch (e: Exception) {
                Log.e("ChatScreen", "Failed to initialize TTS", e)
            }
        }
    }

    // System back -> close app
    BackHandler {
        if (context is Activity) {
            Log.d("HomeScreen", "Closing the app and removing the task...")
            context.finishAffinity()
        }
    }

    // Hide keyboard when drawer opens
    val imm =
        LocalContext.current.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    val token = LocalView.current.windowToken
    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) imm.hideSoftInputFromWindow(token, 0)
    }

    ModalNavigationDrawer(
        drawerState = drawerState, drawerContent = {
            ModalDrawerSheet(drawerState) {
                SettingsDrawerContent(
                    modifier = Modifier,
                    viewModel = chatScreenViewModel,
                    onSettingsClick = onRequestSettingsChange,
                    onChatSelected = { scope.launch { drawerState.close() } },
                    onNewChatClick = { chatScreenViewModel.newChat() },
                    onDataHubClick = { onDataHubClick() },
                    onPluginStoreClick = { onPluginStoreClick() },
                    onModelsClick = { onModelsClick() })
            }
        }) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .blur(if (drawerState.isOpen || isDialog) 10.dp else 0.dp), topBar = {
            Column {
                TopBar(
                    chatScreenViewModel,
                    onMenu = { scope.launch { drawerState.open() } },
                    onLeftMenu = {
                        if (ModelManager.currentModel.value.modelName.isBlank()) {
                            Toast.makeText(context, "Load a Model First!..", Toast.LENGTH_LONG)
                                .show()
                        } else {
                            context.startActivity(
                                Intent(context, ModelPropEditorActivity::class.java).apply {
                                    putExtra(
                                        "modelName", ModelManager.currentModel.value.modelName
                                    )
                                })
                        }
                    })

                ModelLoadProgressBar(loadState = modelState)
                TTSPlaybackBarCompact(ttsViewModel = ttsViewModel)

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

                // Token rate display
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

                // TOP ERROR SNACKBAR - Positioned right below TopBar
                TopErrorSnackbar(snackbarHostState = snackbarHostState)
            }
        }, bottomBar = {
            BottomBar(viewModel = chatScreenViewModel, uiState = uiState)
        }) { innerPadding ->
            BodyContent(innerPadding, chatScreenViewModel, ttsViewModel)
        }
    }
}

/**
 * Top-positioned error snackbar with dismiss action
 */
@Composable
fun TopErrorSnackbar(snackbarHostState: SnackbarHostState) {
    SnackbarHost(
        hostState = snackbarHostState, modifier = Modifier.fillMaxWidth()
    ) { data ->
        // Custom snackbar design for errors
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = rDP(12.dp)),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            ),
            elevation = CardDefaults.cardElevation(rDP(4.dp)),
            shape = RoundedCornerShape(rDP(6.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rDP(8.dp), vertical = rDP(12.dp)),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Error icon and message
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(rDP(12.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(rDP(24.dp))
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(rDP(4.dp))) {
                        Text(
                            text = "Error",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = data.visuals.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                IconButton(
                    onClick = { data.dismiss() }, modifier = Modifier.size(rDP(32.dp))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

// Keep the rest of your code unchanged
@Composable
fun BodyContent(
    innerPadding: PaddingValues, viewModel: ChatScreenViewModel, ttsViewModel: TTSViewModel
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
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
        if (listState.isScrollInProgress && !isAtBottom) userScrolled = true
    }

    LaunchedEffect(messages.isEmpty()) {
        if (messages.isEmpty()) userScrolled = false
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
            val scope = rememberCoroutineScope()
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
    ToolCallingManager.refreshToolList()
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

    ChatInputWithDataHubDialog(
        value = input,
        onValueChange = { input = it },
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

