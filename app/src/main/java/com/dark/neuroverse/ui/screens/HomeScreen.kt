package com.dark.neuroverse.ui.screens

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.neuroverse.R
import com.dark.neuroverse.activity.PluginStoreActivity
import com.dark.neuroverse.ui.drawer.SettingsDrawerContent
import com.dark.neuroverse.ui.theme.rDP
import com.dark.neuroverse.util.isZipByHeader
import com.dark.neuroverse.util.isZipByMime
import com.dark.neuroverse.util.isZipByName
import com.dark.neuroverse.util.queryDisplayName
import com.dark.neuroverse.util.uriToFile
import com.dark.neuroverse.viewModel.PluginHostViewModel
import com.dark.plugins.manager.PluginManager
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onRequestModelChange: () -> Unit, // For navigating to model screen
    onRequestSettingsChange: () -> Unit, // Optional, if needed outside,
    pluginName: String? = null
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val viewModel: PluginHostViewModel = viewModel()

    LaunchedEffect(pluginName) {
        if (pluginName != null) {
            viewModel.setCurrentByName(pluginName)
        }
    }

    LaunchedEffect(Unit) {
        Log.d("HomeScreen", "LaunchedEffect triggered")
        Log.d("HomeScreen", "Chat list updated")
    }

    ModalNavigationDrawer(
        drawerState = drawerState, drawerContent = {
            SettingsDrawerContent(
                viewModel,
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = rDP(12.dp))
                .padding(top = rDP(8.dp))
                .imePadding()
        ) {
            TopBar(
                viewModel = viewModel,
                onDrawerOpen = { scope.launch { drawerState.open() } },
            )

            // This is the unified plugin host UI
            PluginHostScreen(viewModel = viewModel)

            // Conversations + BottomBar are managed by plugins now; keep them commented
            // Conversations(Modifier.weight(1f), viewModel)
            // BottomBar(viewModel)
        }
    }
}

@Composable
internal fun TopBar(viewModel: PluginHostViewModel, onDrawerOpen: () -> Unit) {
    val context = LocalContext.current
    val resolver = context.contentResolver


    val zipMimeTypes = arrayOf("application/zip", "application/x-zip-compressed")

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            // Persist if you'll need it later (across restarts)
            try {
                resolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
            }

            val name = queryDisplayName(resolver, uri)
            val mime = resolver.getType(uri)

            if (isZipByName(name) || isZipByMime(mime) || isZipByHeader(resolver, uri)) {
                val file = uriToFile(context, uri)
                PluginManager.registerPlugin(path = arrayOf(file.absolutePath), context)
            } else {
                Toast.makeText(context, "Not a valid plugin", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(rDP(8.dp))
    ) {
        IconButton(onClick = onDrawerOpen) {
            Icon(painter = painterResource(R.drawable.menu), contentDescription = "Menu")
        }

        val chatTitle = viewModel.activePluginName.collectAsState().value
        Crossfade(chatTitle) { title ->
            Text(
                title ?: "Neuro-V",
                maxLines = 1,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = FontFamily.Serif, fontWeight = FontWeight.W100
                )
            )
        }

        Spacer(Modifier.weight(1f))

        IconButton(onClick = {
            launcher.launch(zipMimeTypes)
        }) {
            Icon(painter = painterResource(R.drawable.new_chat), contentDescription = "New Chat")
        }
    }
}
//
//@Composable
//internal fun Conversations(modifier: Modifier = Modifier, viewModel: ChattingViewModel) {
//    @Composable
//    fun ChatBubble(message: Message) {
//        val isThinkingMessage = remember(message.content) {
//            message.content.trimStart().startsWith("<think>")
//        }
//
//        // Detect and clean reasoning part
//        val raw = message.content.trim()
//        val cleanThinking = remember(raw) {
//            if (isThinkingMessage) {
//                val withoutOpen = raw.removePrefix("<think>").trimStart()
//                if (withoutOpen.endsWith("</think>")) {
//                    withoutOpen.removeSuffix("</think>").trimEnd()
//                } else {
//                    withoutOpen
//                }
//            } else ""
//        }
//
//        val actualResponse = remember(raw) {
//            if (isThinkingMessage && raw.contains("</think>")) {
//                // Extract actual response that comes after </think>
//                raw.substringAfter("</think>").trimStart()
//            } else if (!isThinkingMessage) {
//                // Normal system message
//                raw
//            } else {
//                "" // if still thinking and no closing tag yet
//            }
//        }
//
//        Column(
//            modifier = Modifier.fillMaxWidth(),
//            verticalArrangement = Arrangement.Center,
//            horizontalAlignment = if (message.role == ROLE.USER) Alignment.End else Alignment.Start
//        ) {
//            if (message.role == ROLE.USER) {
//                if (message.document.isNotEmpty()) {
//                    Row {
//                        repeat(message.document.size) {
//                            Box(
//                                Modifier.padding(start = rDP(12.dp)),
//                                contentAlignment = Alignment.Center
//                            ) {
//                                Text(
//                                    text = message.document[it].doc.name,
//                                    style = MaterialTheme.typography.bodyMedium,
//                                    color = MaterialTheme.colorScheme.primary
//                                )
//                            }
//                        }
//                    }
//                }
//            }
//
//            if (isThinkingMessage) {
//                ThinkingBubble(message.copy(content = cleanThinking))
//            }
//
//            if (actualResponse.isNotBlank()) {
//                MarkdownText(
//                    text = actualResponse,
//                    canCopy = message.role != ROLE.USER,
//                    style = MaterialTheme.typography.bodyLarge.copy(
//                        fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal
//                    ),
//                    modifier = Modifier
//                        .then(
//                            if (message.role == ROLE.USER) {
//                                Modifier.background(
//                                        color = MaterialTheme.colorScheme.surface,
//                                        shape = MaterialTheme.shapes.extraLarge
//                                    ).widthIn(max = rDP(300.dp))
//                            } else Modifier
//                        )
//                        .padding(vertical = rDP(8.dp), horizontal = rDP(18.dp))
//                )
//            }
//
//            if (message.role != ROLE.USER && actualResponse.isNotBlank()) {
//                Row(
//                    modifier = Modifier.padding(
//                        top = rDP(4.dp), start = rDP(18.dp)
//                    ), horizontalArrangement = Arrangement.spacedBy(rDP(14.dp))
//                ) {
//                    Icon(
//                        painter = painterResource(R.drawable.copy),
//                        contentDescription = "Copy",
//                        modifier = Modifier.size(rDP(14.dp))
//                    )
//
//                    Icon(
//                        painter = painterResource(R.drawable.new_action),
//                        contentDescription = "Layers",
//                        modifier = Modifier.size(rDP(14.dp))
//                    )
//                }
//            }
//        }
//    }
//
//    val messages = viewModel.messages.collectAsState().value.toMutableList()
//
//    LazyColumn(
//        modifier = modifier
//            .fillMaxWidth()
//            .padding(top = rDP(16.dp)),
//        verticalArrangement = Arrangement.spacedBy(rDP(12.dp)),
//        contentPadding = PaddingValues(bottom = rDP(26.dp))
//    ) {
//        items(messages.size) { index ->
//            ChatBubble(message = messages[index])
//        }
//    }
//}
//
//@OptIn(ExperimentalMaterial3ExpressiveApi::class)
//@Composable
//internal fun BottomBar(viewModel: ChattingViewModel) {
//    var userInput by remember { mutableStateOf("") }
//    val isGenerating by viewModel.isGenerating.collectAsState()
//    val attachedFiles by viewModel.attachedFiles.collectAsState()
//
//    val context = LocalContext.current
//    val filePickerLauncher = rememberLauncherForActivityResult(
//        contract = ActivityResultContracts.GetContent()
//    ) { uri: Uri? ->
//      //  viewModel.handleFileUri(context, uri ?: return@rememberLauncherForActivityResult)
//    }
//
//    Column(Modifier.padding(8.dp)) {
//
//        AnimatedVisibility(attachedFiles.isNotEmpty()) {
//            LazyRow {
//                itemsIndexed(attachedFiles) { index, item ->
//                    FileItem(item, onRemove = { viewModel.clearAttachment(index) })
//                }
//            }
//        }
//
//        Row(
//            modifier = Modifier
//                .fillMaxWidth()
//                .padding(horizontal = rDP(16.dp))
//                .clip(MaterialTheme.shapes.medium)
//                .background(color = MaterialTheme.colorScheme.primary)
//                .heightIn(max = 400.dp)
//                .padding(vertical = rDP(8.dp))
//                .padding(start = rDP(12.dp), end = rDP(10.dp)),
//            verticalAlignment = Alignment.Bottom,
//            horizontalArrangement = Arrangement.spacedBy(rDP(10.dp))
//        ) {
//            Box(
//                modifier = Modifier
//                    .weight(1f)
//                    .padding(8.dp)
//            ) {
//                BasicTextField(
//                    value = userInput,
//                    textStyle = MaterialTheme.typography.titleMedium.copy(
//                        color = MaterialTheme.colorScheme.onPrimary
//                    ),
//                    onValueChange = { userInput = it },
//                    singleLine = false,
//                    decorationBox = { innerTextField ->
//                        if (userInput.isEmpty()) {
//                            Text(
//                                "Say Anything...",
//                                style = MaterialTheme.typography.titleMedium,
//                                fontFamily = FontFamily.Serif,
//                                fontWeight = FontWeight.Bold,
//                                color = Color.Gray
//                            )
//                        }
//                        innerTextField()
//                    })
//            }
//
//            ActionButton(R.drawable.attachment, "Attachment") {
//                filePickerLauncher.launch("*/*")
//            }
//
//            ActionButtonWithCircleProgressIndicator(R.drawable.send_chat, "Send", isGenerating) {
//                if (isGenerating) {
//                    viewModel.stopGenerating()
//                    return@ActionButtonWithCircleProgressIndicator
//                }
//
//                if (userInput.isNotEmpty()) {
//                    viewModel.sendMessage(userInput)
//                    userInput = ""
//                }
//            }
//        }
//    }
//}
//
//@Composable
//fun FileItem(fileAttachment: FileAttachment, onRemove: () -> Unit) {
//    Row(
//        modifier = Modifier
//            .padding(horizontal = rDP(16.dp), vertical = rDP(8.dp))
//            .clip(MaterialTheme.shapes.medium)
//            .background(MaterialTheme.colorScheme.surfaceVariant)
//            .padding(horizontal = rDP(12.dp), vertical = rDP(8.dp)),
//        verticalAlignment = Alignment.CenterVertically
//    ) {
//        Icon(
//            painter = painterResource(id = R.drawable.attachment),
//            contentDescription = "Attached",
//            modifier = Modifier.size(rDP(16.dp)),
//            tint = MaterialTheme.colorScheme.onSurfaceVariant
//        )
//        Spacer(modifier = Modifier.width(rDP(6.dp)))
//
//        Text(
//            text = fileAttachment.doc.name,
//            style = MaterialTheme.typography.bodyMedium,
//            color = MaterialTheme.colorScheme.onSurfaceVariant
//        )
//
//        if (fileAttachment.isLoading) {
//            Spacer(Modifier.width(rDP(6.dp)))
//            CircularProgressIndicator(
//                modifier = Modifier.size(rDP(14.dp)),
//                strokeWidth = rDP(2.dp),
//                color = MaterialTheme.colorScheme.primary
//            )
//        } else {
//            Spacer(modifier = Modifier.width(rDP(6.dp)))
//            Icon(
//                Icons.Default.Close,
//                contentDescription = "Remove",
//                modifier = Modifier
//                    .size(rDP(12.dp))
//                    .clickable { onRemove() },
//                tint = MaterialTheme.colorScheme.error
//            )
//        }
//    }
//}
//
//
//internal object UIComponents {
//
//    @Composable
//    fun ActionButton(
//        @DrawableRes icon: Int, contentDescription: String, onClick: () -> Unit
//    ) {
//        IconButton(
//            modifier = Modifier.size(rDP(26.dp)), onClick = {
//                onClick()
//            }, colors = IconButtonDefaults.iconButtonColors(
//                containerColor = MaterialTheme.colorScheme.onPrimary,
//                contentColor = MaterialTheme.colorScheme.primary
//            )
//        ) {
//            Icon(
//                painter = painterResource(icon),
//                contentDescription = contentDescription,
//                modifier = Modifier.size(rDP(14.dp))
//            )
//        }
//    }
//
//    @Composable
//    fun ActionButtonWithCircleProgressIndicator(
//        @DrawableRes icon: Int, contentDescription: String, show: Boolean, onClick: () -> Unit
//    ) {
//        Box(contentAlignment = Alignment.Center) {
//            AnimatedVisibility(show) {
//                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
//            }
//
//            IconButton(
//                modifier = Modifier.size(rDP(26.dp)), onClick = {
//                    onClick()
//                }, colors = IconButtonDefaults.iconButtonColors(
//                    containerColor = MaterialTheme.colorScheme.onPrimary,
//                    contentColor = MaterialTheme.colorScheme.primary
//                )
//            ) {
//                AnimatedContent(show) { isGeneratingText ->
//                    when {
//                        isGeneratingText -> {
//                            Icon(
//                                imageVector = Icons.Default.Stop, contentDescription = "Mic"
//                            )
//                        }
//
//                        else -> {
//                            Icon(
//                                painter = painterResource(icon),
//                                contentDescription = contentDescription,
//                                modifier = Modifier.size(rDP(14.dp))
//                            )
//                        }
//                    }
//                }
//            }
//        }
//    }
//
//    @Composable
//    fun ThinkingBubble(message: Message) {
//        var expanded by remember { mutableStateOf(false) }
//
//        Column(
//            modifier = Modifier
//                .fillMaxWidth()
//                .padding(horizontal = rDP(16.dp))
//        ) {
//            Row(
//                verticalAlignment = Alignment.CenterVertically,
//                modifier = Modifier
//                    .clickable { expanded = !expanded }
//                    .background(
//                        color = MaterialTheme.colorScheme.surfaceVariant,
//                        shape = MaterialTheme.shapes.medium
//                    )
//                    .padding(rDP(10.dp))) {
//                Icon(
//                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
//                    contentDescription = "Expand",
//                    modifier = Modifier.size(rDP(16.dp))
//                )
//                Spacer(modifier = Modifier.width(rDP(8.dp)))
//                Text(
//                    text = if (expanded) "AI Reasoning (tap to hide)" else "AI is thinking...",
//                    style = MaterialTheme.typography.labelMedium
//                )
//            }
//
//            AnimatedVisibility(visible = expanded) {
//                MarkdownText(
//                    text = message.content,
//                    canCopy = message.role != ROLE.USER,
//                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .padding(rDP(12.dp))
//                )
//            }
//        }
//    }
//
//}

