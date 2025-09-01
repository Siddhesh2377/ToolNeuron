package com.dark.neuroverse.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.ai_module.model.ModelsData
import com.dark.neuroverse.R
import com.dark.neuroverse.activity.PluginStoreActivity
import com.dark.neuroverse.model.Message
import com.dark.neuroverse.model.Role
import com.dark.neuroverse.ui.drawer.SettingsDrawerContent
import com.dark.neuroverse.ui.theme.SkyBlue
import com.dark.neuroverse.ui.theme.SlateGrey
import com.dark.neuroverse.ui.theme.rDP
import com.dark.neuroverse.viewModel.ChatScreenViewModel
import com.dark.neuroverse.viewModel.DrawerViewModel
import com.dark.plugins.manager.PluginManager
import com.dark.plugins.model.Tools
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onRequestModelChange: () -> Unit, // For navigating to model screen
    onRequestSettingsChange: () -> Unit, // Optional, if needed outside,
    pluginName: String? = null,
    chatScreenViewModel: ChatScreenViewModel = viewModel(),
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val drawerViewModel: DrawerViewModel = viewModel()
    PluginManager.init(context)

    LaunchedEffect(pluginName) {
        if (pluginName != null) {
            drawerViewModel.setCurrentByName(pluginName)
        }
    }

    LaunchedEffect(Unit) {
        Log.d("HomeScreen", "LaunchedEffect triggered")
        Log.d("HomeScreen", "Chat list updated")
    }



    ModalNavigationDrawer(
        drawerState = drawerState, drawerContent = {
            SettingsDrawerContent(
                modifier = Modifier,
                drawerViewModel,
                onSettingsClick = onRequestSettingsChange,
                onModelsClick = onRequestModelChange,
                onPluginClick = {
                    scope.launch {
                        drawerState.close()
                    }
                },
                onPluginStoreClick = {
                    context.startActivity(Intent(context, PluginStoreActivity::class.java))
                })
        }) {
        Scaffold(modifier = Modifier
            .fillMaxSize()
            .imePadding(), topBar = {
            TopBar(onMenu = {
                scope.launch {
                    drawerState.open()
                }
            }, onLeftMenu = {

            })
        }, bottomBar = {
            BottomBar(chatScreenViewModel)
        }) { inner ->
            BodyContent(inner, chatScreenViewModel)
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    onMenu: () -> Unit = {}, onLeftMenu: () -> Unit = {}
) {
    val title = "NeuroV Chat"
    TopAppBar(
        title = {
        Text(
            text = title,
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }, navigationIcon = {
        IconButton(onClick = onLeftMenu) {
            Icon(painter = painterResource(R.drawable.menu), contentDescription = "Menu")
        }
    }, actions = {
        // Circular “spark” button (for future quick actions / mic)
        Box(
            modifier = Modifier
                .padding(end = 8.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(MaterialTheme.colorScheme.onPrimary, SkyBlue))
                )
                .clickable { /* TODO: quick action */ }, contentAlignment = Alignment.Center
        ) {
            // tiny white dot “spark”
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
        IconButton(onClick = onMenu) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = "More",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }, colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.background
    )
    )
}

@Composable
private fun BodyContent(inner: PaddingValues, viewModel: ChatScreenViewModel) {
    val messages by viewModel.messages.collectAsState()

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
                    .padding(horizontal = 16.dp),
                reverseLayout = false,
                contentPadding = PaddingValues(
                    bottom = 96.dp, top = 8.dp, start = 8.dp, end = 8.dp
                )
            ) {
                items(messages) { msg ->
                    ChatBubble(msg)
                    Spacer(Modifier.height(18.dp))
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
    var input by remember { mutableStateOf("Search On Web About General Science") }
    val tools = viewModel.toolList.collectAsState().value
    val selectedTools = viewModel.selectedTools.collectAsState().value
    val modelList = viewModel.modelList.collectAsState().value


    selectedTools.forEach {
        Log.v("Selected Tool", it.toolName)
    }

    ChatInputBar(value = input, onValueChange = {
        input = it
    }, tools = tools, modelList = modelList, onAttach = {}, onToolSelected = {
        viewModel.selectTool(it)
    }, selectedTools = selectedTools, onModelSelected = {
        viewModel.selectModel(it)
    }, onSend = {
        if (input.isNotBlank()) {
            viewModel.sendMessage(input, context)
            input = ""
        }
    })
}

@Composable
private fun EmptyHint() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            "Here is Your PDF Document About General Science", color = SlateGrey, fontSize = 16.sp
        )
    }
}

@Composable
private fun ChatBubble(msg: Message) {

    LocalContext.current

    val isUser = msg.role == Role.User

    val bubbleColor = if (isUser) MaterialTheme.colorScheme.primary
    else Color.Transparent

    val textColor =
        if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
    val align = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val radius = with(LocalDensity.current) { 18.dp }

    val corner = RoundedCornerShape(
        radius
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .then(if (isUser) Modifier.widthIn(max = 300.dp) else Modifier.fillMaxWidth())
                .clip(
                    corner
                )
                .background(bubbleColor)
                .padding(14.dp), contentAlignment = align
        ) {
            Column {
                if (msg.viaPlugin != null) {
                    AssistTag(msg.viaPlugin)
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    msg.text, color = textColor, fontSize = 15.sp, lineHeight = 20.sp
                )

                if (!isUser) {
                    if (msg.viaPlugin != null) {
                        val lp = PluginManager.currentPlugin.collectAsState().value

                        AnimatedContent(lp == null) {
                            when (it) {
                                true -> {
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surface
                                        ),
                                        elevation = CardDefaults.cardElevation(0.dp),
                                        modifier = Modifier.size(200.dp),
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(24.dp),
                                            verticalArrangement = Arrangement.spacedBy(
                                                16.dp, Alignment.CenterVertically
                                            ),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(32.dp), // a bit larger than before
                                                strokeWidth = 3.dp
                                            )
                                            Text(
                                                text = "Loading...Plugin \n ${msg.viaPlugin}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                textAlign = TextAlign.Center,
                                                fontFamily = FontFamily.Serif
                                            )
                                        }
                                    }

                                }

                                false -> {
                                    Card(elevation = CardDefaults.cardElevation(0.dp)) {
                                        lp?.api?.content()?.invoke()
                                    }
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
private fun AssistTag(name: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x1A3B82F6)) // faint blue chip
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = "via $name",
            fontSize = 12.sp,
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
    onToolSelected: (Tools) -> Unit
) {
    LazyColumn(
        modifier = modifier.heightIn(min = 100.dp, max = 300.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        tools.forEach { (pluginName, toolList) ->
            item {
                // Plugin header
                Text(
                    text = pluginName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            items(toolList) { tool ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 4.dp)
                        .clickable { onToolSelected(tool) },
                    elevation = CardDefaults.cardElevation(0.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = tool.toolName, style = MaterialTheme.typography.bodyLarge
                        )
                        if (tool.path.isNotBlank()) {
                            Text(
                                text = tool.path,
                                style = MaterialTheme.typography.bodySmall,
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
    modifier: Modifier = Modifier, modelList: List<ModelsData>, // Pair(pluginName, tools)
    onModelSelected: (ModelsData) -> Unit
) {
    LazyColumn(
        modifier = modifier.heightIn(min = 100.dp, max = 300.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        item {
            // Plugin header
            Text(
                text = "Local Models",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        items(modelList) { modelsData ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 4.dp)
                    .clickable { onModelSelected(modelsData) },
                elevation = CardDefaults.cardElevation(0.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = modelsData.modeName, style = MaterialTheme.typography.bodyLarge
                    )
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
    onToolSelected: (Tools) -> Unit,
    onModelSelected: (ModelsData) -> Unit,
    onValueChange: (String) -> Unit,
    onAttach: () -> Unit,
    onSend: () -> Unit
) {
    var showToolsList by remember { mutableStateOf(false) }
    var showModelList by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AnimatedVisibility(showToolsList) {

            ToolsList(
                modifier = Modifier, tools = tools, onToolSelected = {
                    onToolSelected(it)
                    showToolsList = false
                })
        }
        AnimatedVisibility(showModelList) {

            ModelList(
                modifier = Modifier, modelList = modelList, onModelSelected = {
                    onModelSelected(it)
                    showModelList = false
                })
        }

        Row(
            modifier = Modifier
                .padding(top = 16.dp)
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    showToolsList = !showToolsList
                    showModelList = false
                }, colors = ButtonDefaults.textButtonColors(
                    containerColor = MaterialTheme.colorScheme.background
                ), shape = RoundedCornerShape(rDP(8.dp))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(painterResource(R.drawable.tools), contentDescription = "Add")
                    Text(text = "Tools")
                }
            }

            LazyRow(Modifier.weight(1f)) {
                items(selectedTools, key = { it.toolName } // stable key for animation
                ) { tool ->
                    ToolCard(
                        modifier = Modifier
                            .animateItem() // smooth slide when inserted/removed
                            .padding(8.dp), tool = tool
                    )
                }
            }

            IconButton(
                onClick = {
                    showModelList = !showModelList
                    showToolsList = false
                },
                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.background),
                shape = RoundedCornerShape(rDP(8.dp))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(painterResource(R.drawable.tools), contentDescription = "Add")
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .padding(bottom = 4.dp)
                .padding(end = 18.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp),
                placeholder = { Text("Say Anything…", color = SlateGrey) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.primary)
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
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { onSend() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(R.drawable.send_chat),
                    modifier = Modifier.padding(8.dp),
                    contentDescription = "Send",
                    tint = MaterialTheme.colorScheme.background
                )
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