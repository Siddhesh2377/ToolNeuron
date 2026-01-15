package com.dark.tool_neuron.ui.screen.home_screen

import android.content.Intent
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.R
import com.dark.tool_neuron.activity.ModelLoadingActivity
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.ActionProgressButton
import com.dark.tool_neuron.ui.components.ActionToggleButton
import com.dark.tool_neuron.ui.components.AnimatedTitle
import com.dark.tool_neuron.ui.components.ModeToggleSwitch
import com.dark.tool_neuron.ui.components.ModelListItem
import com.dark.tool_neuron.ui.theme.rDp
import com.dark.tool_neuron.viewmodel.ChatViewModel
import com.dark.tool_neuron.viewmodel.LLMModelViewModel
import com.dark.tool_neuron.worker.GenerationManager
import kotlinx.coroutines.launch

// Update HomeScreen to wrap with SharedTransitionLayout
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun HomeScreen(
    onStoreButtonClicked: () -> Unit,
    onModelEditor: () -> Unit,
    onVaultManagerClick: () -> Unit,
    chatViewModel: ChatViewModel,
    llmModelViewModel: LLMModelViewModel
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Start with a new conversation instead of loading a chat
    LaunchedEffect(Unit) {
        chatViewModel.startNewConversation()
    }

    ModalNavigationDrawer(
        drawerState = drawerState, drawerContent = {
            ModalDrawerSheet {
                HomeDrawerScreen(onVaultManagerClick = {
                    onVaultManagerClick()
                }, onChatSelected = {
                    chatViewModel.loadChat(it)
                    scope.launch {
                        drawerState.close()
                    }
                })
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
                    onModelEditor = {
                        onModelEditor()
                    }, chatViewModel = chatViewModel, llmModelViewModel = llmModelViewModel
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
    onModelEditor: () -> Unit,
    chatViewModel: ChatViewModel = hiltViewModel(),
    llmModelViewModel: LLMModelViewModel = hiltViewModel()
) {
    var value by remember { mutableStateOf("") }
    val installedModels by llmModelViewModel.installedModels.collectAsStateWithLifecycle(emptyList())
    val currentModelID by llmModelViewModel.currentModelID.collectAsStateWithLifecycle()
    val showModelList by chatViewModel.showModelList.collectAsStateWithLifecycle()
    val isGenerating by chatViewModel.isGenerating.collectAsStateWithLifecycle()
    val currentGenerationType by chatViewModel.currentGenerationType.collectAsStateWithLifecycle()
    val isTextModelLoaded by chatViewModel.isTextModelLoaded.collectAsStateWithLifecycle()
    val isImageModelLoaded by chatViewModel.isImageModelLoaded.collectAsStateWithLifecycle()

    // Track if any model is loaded
    val isModelLoaded = currentModelID.isNotEmpty()

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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(rDp(6.dp)),
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

                    ActionButton(
                        onClickListener = {
                            onModelEditor()
                        },
                        icon = R.drawable.settings,
                        modifier = Modifier.padding(start = rDp(6.dp))
                    )

                    ActionToggleButton(
                        onCheckedChange = {
                            if (showModelList) {
                                chatViewModel.hideModelList()
                            } else {
                                chatViewModel.showModelList()
                            }
                        }, checked = showModelList, icon = R.drawable.ai_model
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
                                                chatViewModel.sendTextMessage(value)
                                            }

                                            GenerationManager.ModelType.IMAGE_GENERATION -> {
                                                chatViewModel.sendImageRequest(value)
                                            }
                                        }
                                        value = ""
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
