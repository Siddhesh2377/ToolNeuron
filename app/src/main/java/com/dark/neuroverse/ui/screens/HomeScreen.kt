package com.dark.neuroverse.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Web
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.ai_module.model.ModelsData
import com.dark.neuroverse.R
import com.dark.neuroverse.activity.PluginStoreActivity
import com.dark.neuroverse.model.Message
import com.dark.neuroverse.model.Role
import com.dark.neuroverse.ui.components.MarkdownText
import com.dark.neuroverse.ui.components.ModelLoadProgressBar
import com.dark.neuroverse.ui.components.ProjectedCapturable
import com.dark.neuroverse.ui.drawer.SettingsDrawerContent
import com.dark.neuroverse.ui.theme.Mint
import com.dark.neuroverse.ui.theme.SkyBlue
import com.dark.neuroverse.ui.theme.SlateGrey
import com.dark.neuroverse.ui.theme.rDP
import com.dark.neuroverse.ui.theme.rSp
import com.dark.neuroverse.viewModel.ChatScreenViewModel
import com.dark.neuroverse.viewModel.ChattingViewModelFactory
import com.dark.neuroverse.viewModel.GenerationState
import com.dark.plugins.manager.PluginManager
import com.dark.plugins.model.Tools
import com.dark.userdata.readBitmapImage
import com.dark.userdata.writeBitmapImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onRequestModelChange: () -> Unit,
    onRequestSettingsChange: () -> Unit,
    viewModel: ChatScreenViewModel = viewModel(
        factory = ChattingViewModelFactory(LocalContext.current)
    ),
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val chatTitle by viewModel.chatTitle.collectAsStateWithLifecycle()
    val modelState by viewModel.modelLoadingState.collectAsStateWithLifecycle()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SettingsDrawerContent(
                modifier = Modifier,
                viewModel = viewModel,
                onSettingsClick = onRequestSettingsChange,
                onModelsClick = onRequestModelChange,
                onPluginClick = { scope.launch { drawerState.close() } },
                onPluginStoreClick = {
                    context.startActivity(Intent(context, PluginStoreActivity::class.java))
                }
            )
        }
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            topBar = {
                Column {
                    TopBar(
                        title = chatTitle,
                        onMenu = { scope.launch { drawerState.open() } },
                        onLeftMenu = { viewModel.newChat() }
                    )

                    ModelLoadProgressBar(loadState = modelState)
                }
            },
            bottomBar = { BottomBar(viewModel) }
        ) { inner ->
            BodyContent(inner, viewModel)
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(
    title: String = "NeuroV Chat",
    onMenu: () -> Unit = {},
    onLeftMenu: () -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                fontSize = rSp(22.sp),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        },
        navigationIcon = {
            IconButton(onClick = onMenu) {
                Icon(painter = painterResource(R.drawable.menu), contentDescription = "Menu")
            }
        },
        actions = {
            // circular spark button
            Box(
                modifier = Modifier
                    .padding(end = rDP(8.dp))
                    .size(rDP(32.dp))
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.onPrimary, Color(0xFF0089FF)) // SkyBlue
                        )
                    )
                    .clickable { onLeftMenu() },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
            IconButton(onClick = onLeftMenu) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = "More",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

@Composable
private fun BodyContent(inner: PaddingValues, viewModel: ChatScreenViewModel) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val generationState by viewModel.generationState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(inner)
    ) {
        if (messages.isEmpty()) {
            EmptyHint()
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = rDP(16.dp)),
                reverseLayout = false,
                contentPadding = PaddingValues(
                    bottom = rDP(96.dp),
                    top = rDP(8.dp),
                    start = rDP(8.dp),
                    end = rDP(8.dp)
                )
            ) {
                items(
                    items = messages,
                    key = { it.id },                    // <-- stable!
                    contentType = { if (it.role == Role.User) "user" else "assistant" }
                ) { msg ->
                    ChatBubble(msg, generationState) {
                        val preview = writeBitmapImage(it)
                        viewModel.writeToolPreviewByID(msg.id, preview)
                    }
                    Spacer(Modifier.height(rDP(18.dp)))
                }
            }
        }
    }
}


@Composable
private fun BottomBar(
    viewModel: ChatScreenViewModel
) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("Hi Bro") }
    val tools by viewModel.toolList.collectAsStateWithLifecycle()
    val selectedTools by viewModel.selectedTools.collectAsStateWithLifecycle()
    val modelList by viewModel.modelList.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()

    ChatInputBar(
        value = input,
        onValueChange = {
            input = it
        },
        tools = tools,
        isGenerating = isGenerating,
        modelList = modelList,
        onAttach = {},
        onToolSelected = {
            viewModel.selectTool(it)
        },
        selectedTools = if (selectedTools.first.isEmpty()) emptyList() else listOf(selectedTools.second),
        onModelSelected = {
            viewModel.selectModel(it)
        },
        selectedModel = selectedModel,
        onToolRemoved = {
            viewModel.unSelectTool()
        },
        onSend = {
            when (isGenerating) {
                true -> {
                    viewModel.stopGenerating()
                }

                false -> {
                    if (input.isNotBlank()) {
                        viewModel.sendMessage(input, context)
                        input = ""
                    }
                }
            }
        })
}

@Composable
private fun EmptyHint() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = rDP(24.dp)),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(Modifier.height(rDP(24.dp)))
        Text(
            text = "Here is Your PDF Document About General Science",
            color = SlateGrey,
            fontSize = rSp(16.sp)
        )
    }
}



@Composable
private fun AssistTag(name: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(rDP(10.dp)))
            .background(Color(0x1A3B82F6)) // faint blue chip
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
fun ToolsList(
    modifier: Modifier = Modifier,
    tools: List<Pair<String, List<Tools>>>, // Pair(pluginName, tools)
    onToolSelected: (Pair<String, Tools>) -> Unit
) {
    LazyColumn(
        modifier = modifier.heightIn(min = rDP(100.dp), max = rDP(300.dp)),
        contentPadding = PaddingValues(vertical = rDP(8.dp))
    ) {
        tools.forEach { (pluginName, toolList) ->
            item {
                // Plugin header
                Text(
                    text = pluginName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = rSp(18.sp) // scaled font
                    ),
                    modifier = Modifier.padding(
                        horizontal = rDP(16.dp),
                        vertical = rDP(8.dp)
                    )
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
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = rSp(16.sp)
                            )
                        )
                        if (tool.path.isNotBlank()) {
                            Text(
                                text = tool.path,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = rSp(13.sp)
                                ),
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
fun ModelList(
    modifier: Modifier = Modifier,
    modelList: List<ModelsData>, // list of models
    onModelSelected: (ModelsData) -> Unit,
    selectedModel: ModelsData
) {
    LazyColumn(
        modifier = modifier.heightIn(min = rDP(100.dp), max = rDP(300.dp)),
        contentPadding = PaddingValues(vertical = rDP(8.dp))
    ) {
        item {
            // Section header
            Text(
                text = "Local Models",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = rSp(18.sp)
                ),
                modifier = Modifier.padding(
                    horizontal = rDP(16.dp),
                    vertical = rDP(8.dp)
                )
            )
        }

        items(modelList) { modelsData ->
            val isSelected = modelsData.modeName == selectedModel.modeName
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rDP(32.dp), vertical = rDP(4.dp))
                    .clickable { onModelSelected(modelsData) },
                elevation = CardDefaults.cardElevation(rDP(0.dp)),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected)
                        Mint.copy(alpha = 0.5f)
                    else
                        MaterialTheme.colorScheme.background
                )
            ) {
                Row(modifier = Modifier.padding(rDP(12.dp))) {
                    Text(
                        text = modelsData.modeName,
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = rSp(16.sp)
                        )
                    )
                    Spacer(Modifier.weight(1f))
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Check",
                            tint = Mint,
                            modifier = Modifier.size(rDP(20.dp))
                        )
                    }
                }
            }
        }
    }
}



@SuppressLint("StateFlowValueCalledInComposition")
@Composable
private fun ChatInputBar(
    value: String,
    tools: List<Pair<String, List<Tools>>>,
    modelList: List<ModelsData>,
    selectedTools: List<Tools>,
    onToolSelected: (Pair<String, Tools>) -> Unit,
    onModelSelected: (ModelsData) -> Unit,
    onValueChange: (String) -> Unit,
    onAttach: () -> Unit,
    onSend: () -> Unit,
    onToolRemoved: (Tools) -> Unit,
    selectedModel: ModelsData,
    isGenerating: Boolean
) {
    var showToolsList by remember { mutableStateOf(false) }
    var showModelList by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
        verticalArrangement = Arrangement.spacedBy(rDP(8.dp))
    ) {
        AnimatedVisibility(showToolsList) {
            ToolsList(
                modifier = Modifier,
                tools = tools,
                onToolSelected = {
                    onToolSelected(it)
                    showToolsList = false
                }
            )
        }

        AnimatedVisibility(showModelList) {
            ModelList(
                modifier = Modifier,
                selectedModel = selectedModel,
                modelList = modelList,
                onModelSelected = {
                    onModelSelected(it)
                    showModelList = false
                }
            )
        }

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
                    showToolsList = !showToolsList
                    showModelList = false
                },
                colors = ButtonDefaults.textButtonColors(
                    containerColor = if (showToolsList) SkyBlue else MaterialTheme.colorScheme.background,
                    contentColor = if (showToolsList) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary,
                ),
                shape = RoundedCornerShape(rDP(8.dp))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(rDP(8.dp))
                ) {
                    Icon(painterResource(R.drawable.tools), contentDescription = "Add")
                    Text(text = "Tools", fontSize = rSp(14.sp))
                }
            }

            LazyRow(Modifier.weight(1f)) {
                items(selectedTools, key = { it.toolName }) { tool ->
                    ToolCard(
                        modifier = Modifier
                            .animateItem()
                            .clickable { onToolRemoved(tool) }
                            .padding(rDP(8.dp)),
                        tool = tool
                    )
                }
            }

            IconButton(
                onClick = {
                    showModelList = !showModelList
                    showToolsList = false
                },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (showModelList) Mint else MaterialTheme.colorScheme.background,
                    contentColor = if (showModelList) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary,
                ),
                shape = RoundedCornerShape(rDP(8.dp))
            ) {
                Icon(Icons.Default.SmartToy, contentDescription = "Add")
            }
        }

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
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = rDP(6.dp)),
                placeholder = {
                    Text("Say Anything…", color = SlateGrey, fontSize = rSp(14.sp))
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
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = rSp(15.sp)
                )
            )

            IconButton(onClick = onAttach) {
                Icon(
                    Icons.Outlined.AttachFile,
                    contentDescription = "Attach",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Send button with gradient pill
            Box(
                modifier = Modifier
                    .size(rDP(36.dp))
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { onSend() },
                contentAlignment = Alignment.Center
            ) {
                when (isGenerating) {
                    true -> {
                        Icon(
                            Icons.Default.Stop,
                            modifier = Modifier.padding(rDP(8.dp)),
                            contentDescription = "Send",
                            tint = MaterialTheme.colorScheme.background
                        )
                        CircularProgressIndicator(
                            trackColor = MaterialTheme.colorScheme.background,
                            modifier = Modifier.size(rDP(28.dp))
                        )
                    }

                    false -> {
                        Icon(
                            painterResource(R.drawable.send_chat),
                            modifier = Modifier.padding(rDP(8.dp)),
                            contentDescription = "Send",
                            tint = MaterialTheme.colorScheme.background
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun ToolCard(modifier: Modifier = Modifier, tool: Tools) {
    val accentColor = Color(0xFF0066FF)
    val backgroundColor = accentColor.copy(alpha = 0.2f)

    Box(
        modifier
            .size(ButtonDefaults.MinHeight)
            .background(color = backgroundColor, shape = RoundedCornerShape(rDP(8.dp))),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Web, contentDescription = "Open File", tint = accentColor)
    }
}


@Composable
private fun ChatBubble(
    msg: Message,
    generationState: GenerationState,
    onCapture: (Bitmap) -> Unit
) {
    // role check
    val isUser = msg.role == Role.User

    // trigger capture when generation is DONE
    var shouldCaptureNow by remember { mutableStateOf(false) }
    LaunchedEffect(generationState) {
        shouldCaptureNow = generationState == GenerationState.DONE
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
        ) {
            // optional "thoughts"
            val thoughtVisible = !isUser && !msg.thought.isNullOrBlank()
            if (thoughtVisible) {
                ThinkingChatUI(msg)
                Spacer(Modifier.height(rDP(8.dp)))
            }

            // role-specific bubble UI
            when (msg.role) {
                Role.User -> {
                    UserChatUI(msg)
                }

                Role.Assistant -> {
                    RegularChatUI(msg)
                }

                Role.Tool -> {
                    ToolChatUI(
                        message = msg,
                        shouldCaptureNow = shouldCaptureNow,
                        onCapture = {
                            shouldCaptureNow = false
                            onCapture(it)
                        }
                    )
                }
            }
        }
    }
}


@Composable
private fun ToolChatUI(
    message: Message,
    shouldCaptureNow: Boolean,
    onCapture: (Bitmap) -> Unit
) {
    Column {
        AssistTag(message.tool?.toolName ?: "Unknown Tool")
        Spacer(Modifier.height(rDP(6.dp)))

        val pluginLoading by PluginManager.currentPlugin.collectAsState(initial = null)
        val pluginPreviewLoading: Bitmap? = readBitmapImage(message.tool?.toolPreview ?: "")

        when (pluginPreviewLoading == null) {
            true -> {
                val isLoading = pluginLoading == null

                ProjectedCapturable(
                    captureKey = message.id,         // stable + flips when ready
                    captureWhen = shouldCaptureNow,
                    onCaptured = { bmp -> onCapture(bmp) },
                ) {
                    Crossfade(targetState = isLoading, label = "plugin") { loading ->
                        if (loading) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(rDP(0.dp)),
                                modifier = Modifier.size(rDP(200.dp)),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(rDP(24.dp)),
                                    verticalArrangement = Arrangement.spacedBy(
                                        rDP(16.dp), Alignment.CenterVertically
                                    ),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(rDP(32.dp)),
                                        strokeWidth = rDP(3.dp)
                                    )
                                    Text(
                                        text = "Loading...Plugin \n ${message.tool?.toolName}",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = rSp(14.sp),
                                            fontFamily = FontFamily.Serif
                                        ),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            Card(elevation = CardDefaults.cardElevation(rDP(0.dp))) {
                                PluginManager.currentPlugin.collectAsState()
                                    .value?.api?.content()
                                    ?.invoke()
                            }
                        }
                    }
                }
            }

            false -> {
                var showToolOutput by remember { mutableStateOf(false) }

                Box(
                    Modifier
                        .clickable { showToolOutput = !showToolOutput }
                        .padding(top = rDP(8.dp))
                        .clip(RoundedCornerShape(rDP(12.dp)))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            rDP(1.dp),
                            MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(rDP(12.dp))
                        )
                ) {
                    Crossfade(targetState = showToolOutput) { visible ->
                        when (visible) {
                            true -> {
                                Image(
                                    bitmap = pluginPreviewLoading.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            false -> {
                                Card(
                                    elevation = CardDefaults.cardElevation(rDP(0.dp)),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text(
                                        "Show Tool Output",
                                        Modifier.padding(
                                            horizontal = rDP(16.dp),
                                            vertical = rDP(12.dp)
                                        ),
                                        fontSize = rSp(14.sp),
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RegularChatUI(message: Message) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = rDP(14.dp))
    ) {
        MarkdownText(
            message.text,
            color = MaterialTheme.colorScheme.primary,
            style = TextStyle.Default.copy(
                fontSize = rSp(15.sp),
                lineHeight = rSp(20.sp)
            )
        )
    }
}


@Composable
private fun UserChatUI(message: Message) {
    val radius = with(LocalDensity.current) { rDP(12.dp) }

    // shape
    val corner = RoundedCornerShape(radius)

    Box(
        modifier = Modifier
            .widthIn(max = rDP(300.dp))
            .clip(corner)
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = rDP(14.dp), vertical = rDP(8.dp))
    ) {
        MarkdownText(
            message.text,
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}


@Composable
private fun ThinkingChatUI(message: Message) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(120)) + expandVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessLow
            )
        ) + slideInVertically(initialOffsetY = { -it / 6 }),
        exit = fadeOut(animationSpec = tween(120)) + shrinkVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + slideOutVertically(targetOffsetY = { -it / 6 })
    ) {
        var showThinkingText by remember { mutableStateOf(true) }

        Spacer(Modifier.height(rDP(6.dp)))
        Box(
            Modifier
                .fillMaxWidth()
                .clickable { showThinkingText = !showThinkingText }
                .clip(RoundedCornerShape(rDP(8.dp)))
                .background(Color(0xFF0F172A)) // slate-ish
                .border(rDP(1.dp), Color(0xFF334155), RoundedCornerShape(rDP(8.dp)))
                .padding(rDP(10.dp))
                .animateContentSize(animationSpec = tween(120))
        ) {

            Text(
                text = if (showThinkingText) "Thinking..." else "Thought: \n${message.thought}",
                color = Color(0xFFCBD5E1),
                fontSize = rSp(12.sp),
                lineHeight = rSp(18.sp),
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
