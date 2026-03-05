package com.dark.tool_neuron.neuron_example

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.engine.EmbeddingEngine
import com.dark.tool_neuron.models.vault.ChatInfo
import com.dark.tool_neuron.repo.ChatRepository
import com.neuronpacket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import com.dark.tool_neuron.ui.icons.TnIcons


// Main Screen


//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//fun NeuronExampleScreen() {
//    val context = LocalContext.current
//    val scope = rememberCoroutineScope()
//
//    // State
//    var status by remember { mutableStateOf("Not Initialized") }
//    var isLoading by remember { mutableStateOf(false) }
//    var errorMessage by remember { mutableStateOf("") }
//    var settings by remember { mutableStateOf(GraphSettings.DEFAULT) }
//
//    // Dialogs
//    var showSettingsSheet by remember { mutableStateOf(false) }
//    var showAddTextDialog by remember { mutableStateOf(false) }
//    var showChatPickerDialog by remember { mutableStateOf(false) }
//    var showExportDialog by remember { mutableStateOf(false) }
//    var showLoadDialog by remember { mutableStateOf(false) }
//
//    // Graph state
//    var selectedNodeId by remember { mutableStateOf<String?>(null) }
//    var searchQuery by remember { mutableStateOf("") }
//    var searchResults by remember { mutableStateOf<List<QueryResult>>(emptyList()) }
//
//    // Loaded packet info
//    var loadedPacketInfo by remember { mutableStateOf<LoadedPacketState?>(null) }
//
//    val embeddingEngine = remember { EmbeddingEngine() }
//    val graph = remember { NeuronGraph(embeddingEngine, settings) }
//    var graphNodes by remember { mutableStateOf<List<NeuronNode>>(emptyList()) }
//
//    // Update graph settings when changed
//    LaunchedEffect(settings) {
//        graph.settings = settings
//    }
//
//    // Refresh nodes display
//    fun refreshNodes() {
//        graphNodes = graph.getAllNodes()
//    }
//
//    Scaffold(
//        topBar = {
//            TopAppBar(
//                title = {
//                    Column {
//                        Text("Neuron Graph")
//                        if (loadedPacketInfo != null) {
//                            Text(
//                                text = loadedPacketInfo!!.metadata.name.ifEmpty { "Loaded Pack" },
//                                style = MaterialTheme.typography.labelSmall,
//                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
//                            )
//                        }
//                    }
//                },
//                actions = {
//                    // Load button
//                    IconButton(onClick = { showLoadDialog = true }) {
//                        Icon(TnIcons.FolderOpen, contentDescription = "Load Pack")
//                    }
//                    // Settings button
//                    IconButton(onClick = { showSettingsSheet = true }) {
//                        Icon(TnIcons.Settings, contentDescription = "Settings")
//                    }
//                },
//                colors = TopAppBarDefaults.topAppBarColors(
//                    containerColor = MaterialTheme.colorScheme.primaryContainer
//                )
//            )
//        }
//    ) { padding ->
//        Column(
//            modifier = Modifier
//                .fillMaxSize()
//                .padding(padding)
//                .padding(16.dp),
//            verticalArrangement = Arrangement.spacedBy(12.dp)
//        ) {
//            // Status & Error
//            if (status.isNotEmpty()) {
//                Text(
//                    text = status,
//                    style = MaterialTheme.typography.bodySmall,
//                    color = MaterialTheme.colorScheme.onSurfaceVariant
//                )
//            }
//
//            if (errorMessage.isNotEmpty()) {
//                Card(
//                    colors = CardDefaults.cardColors(
//                        containerColor = MaterialTheme.colorScheme.errorContainer
//                    )
//                ) {
//                    Row(
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .padding(12.dp),
//                        horizontalArrangement = Arrangement.SpaceBetween,
//                        verticalAlignment = Alignment.CenterVertically
//                    ) {
//                        Text(
//                            text = errorMessage,
//                            color = MaterialTheme.colorScheme.onErrorContainer,
//                            style = MaterialTheme.typography.bodySmall,
//                            modifier = Modifier.weight(1f)
//                        )
//                        IconButton(onClick = { errorMessage = "" }) {
//                            Icon(
//                                TnIcons.X,
//                                contentDescription = "Dismiss",
//                                tint = MaterialTheme.colorScheme.onErrorContainer
//                            )
//                        }
//                    }
//                }
//            }
//
//            // Initialize Button
//            if (!embeddingProvider.isInitialized()) {
//                Button(
//                    onClick = {
//                        scope.launch(Dispatchers.IO) {
//                            isLoading = true
//                            status = "Initializing embedding model..."
//                            errorMessage = ""
//
//                            val modelPath = EmbeddingEngine.getModelPath(context)
//                            val config = com.dark.tool_neuron.engine.EmbeddingConfig(
//                                modelPath = modelPath.absolutePath
//                            )
//
//                            val result = embeddingEngine.initialize(config)
//                            if (result.isSuccess) {
//                                status = "Ready (dim: ${embeddingEngine.getDimension()})"
//                            } else {
//                                status = "Initialization failed"
//                                errorMessage = result.exceptionOrNull()?.message ?: "Unknown error"
//                            }
//                            isLoading = false
//                        }
//                    },
//                    modifier = Modifier.fillMaxWidth(),
//                    enabled = !isLoading
//                ) {
//                    if (isLoading) {
//                        CircularProgressIndicator(
//                            modifier = Modifier.size(20.dp),
//                            strokeWidth = 2.dp
//                        )
//                        Spacer(modifier = Modifier.width(8.dp))
//                    }
//                    Text("Initialize Embedding Model")
//                }
//            }
//
//            // Add Content Buttons
//            if (embeddingProvider.isInitialized()) {
//                Row(
//                    modifier = Modifier.fillMaxWidth(),
//                    horizontalArrangement = Arrangement.spacedBy(8.dp)
//                ) {
//                    OutlinedButton(
//                        onClick = { showAddTextDialog = true },
//                        modifier = Modifier.weight(1f),
//                        enabled = !isLoading
//                    ) {
//                        Icon(TnIcons.Code, contentDescription = null, modifier = Modifier.size(18.dp))
//                        Spacer(modifier = Modifier.width(4.dp))
//                        Text("Text")
//                    }
//
//                    OutlinedButton(
//                        onClick = { showChatPickerDialog = true },
//                        modifier = Modifier.weight(1f),
//                        enabled = !isLoading
//                    ) {
//                        Icon(TnIcons.MessageCircle, contentDescription = null, modifier = Modifier.size(18.dp))
//                        Spacer(modifier = Modifier.width(4.dp))
//                        Text("Chat")
//                    }
//                }
//            }
//
//            // Stats Bar
//            if (graphNodes.isNotEmpty()) {
//                val stats = graph.getStats()
//                Card(
//                    modifier = Modifier.fillMaxWidth(),
//                    colors = CardDefaults.cardColors(
//                        containerColor = MaterialTheme.colorScheme.secondaryContainer
//                    )
//                ) {
//                    Row(
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .padding(12.dp),
//                        horizontalArrangement = Arrangement.SpaceEvenly
//                    ) {
//                        StatItem("Nodes", stats.nodeCount.toString())
//                        StatItem("Edges", stats.edgeCount.toString())
//                        StatItem("Sources", stats.sourceCount.toString())
//                    }
//                }
//            }
//
//            // Graph Visualization
//            if (embeddingProvider.isInitialized()) {
//                val selectedNode = graphNodes.find { it.id == selectedNodeId }
//                val connectedNodes = selectedNode?.edges?.mapNotNull { edge ->
//                    graphNodes.find { it.id == edge.targetId }
//                } ?: emptyList()
//
//                GraphVisualization(
//                    nodes = graphNodes,
//                    selectedNodeId = selectedNodeId,
//                    onNodeSelected = { selectedNodeId = it },
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .height(250.dp)
//                )
//
//                // Node Detail Card
//                AnimatedVisibility(
//                    visible = selectedNode != null,
//                    enter = slideInVertically { it } + fadeIn(),
//                    exit = slideOutVertically { it } + fadeOut()
//                ) {
//                    NodeDetailCard(
//                        node = selectedNode,
//                        connectedNodes = connectedNodes,
//                        onDismiss = { selectedNodeId = null },
//                        modifier = Modifier.fillMaxWidth()
//                    )
//                }
//            }
//
//            // Search Section
//            if (graphNodes.isNotEmpty()) {
//                HorizontalDivider()
//
//                OutlinedTextField(
//                    value = searchQuery,
//                    onValueChange = { searchQuery = it },
//                    label = { Text("Query the graph") },
//                    placeholder = { Text("Ask something...") },
//                    modifier = Modifier.fillMaxWidth(),
//                    trailingIcon = {
//                        if (searchQuery.isNotBlank()) {
//                            IconButton(
//                                onClick = {
//                                    scope.launch(Dispatchers.IO) {
//                                        isLoading = true
//                                        searchResults = graph.query(searchQuery, topK = 5)
//                                        isLoading = false
//                                    }
//                                }
//                            ) {
//                                Icon(TnIcons.Search, contentDescription = "Search")
//                            }
//                        }
//                    },
//                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
//                    keyboardActions = KeyboardActions(
//                        onSearch = {
//                            scope.launch(Dispatchers.IO) {
//                                isLoading = true
//                                searchResults = graph.query(searchQuery, topK = 5)
//                                isLoading = false
//                            }
//                        }
//                    ),
//                    singleLine = true
//                )
//
//                // Search Results
//                if (searchResults.isNotEmpty()) {
//                    Text(
//                        text = "Results (${searchResults.size})",
//                        style = MaterialTheme.typography.labelMedium,
//                        modifier = Modifier.padding(top = 8.dp)
//                    )
//
//                    LazyColumn(
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .weight(1f),
//                        verticalArrangement = Arrangement.spacedBy(8.dp)
//                    ) {
//                        items(searchResults) { result ->
//                            SearchResultCard(
//                                result = result,
//                                onClick = { selectedNodeId = result.node.id }
//                            )
//                        }
//                    }
//                }
//            }
//
//            Spacer(modifier = Modifier.weight(1f))
//
//            // Export Button
//            if (graphNodes.isNotEmpty()) {
//                Button(
//                    onClick = { showExportDialog = true },
//                    modifier = Modifier.fillMaxWidth(),
//                    enabled = !isLoading
//                ) {
//                    Icon(TnIcons.DeviceFloppy, contentDescription = null)
//                    Spacer(modifier = Modifier.width(8.dp))
//                    Text("Export as Neuron Pack")
//                }
//            }
//
//            // Clear Button
//            if (graphNodes.isNotEmpty()) {
//                OutlinedButton(
//                    onClick = {
//                        scope.launch {
//                            graph.clear()
//                            refreshNodes()
//                            searchResults = emptyList()
//                            selectedNodeId = null
//                            loadedPacketInfo = null
//                        }
//                    },
//                    modifier = Modifier.fillMaxWidth(),
//                    colors = ButtonDefaults.outlinedButtonColors(
//                        contentColor = MaterialTheme.colorScheme.error
//                    )
//                ) {
//                    Icon(TnIcons.Trash, contentDescription = null)
//                    Spacer(modifier = Modifier.width(8.dp))
//                    Text("Clear Graph")
//                }
//            }
//        }
//    }
//
//    // Settings Bottom Sheet
//    if (showSettingsSheet) {
//        SettingsBottomSheet(
//            settings = settings,
//            onSettingsChange = { settings = it },
//            onDismiss = { showSettingsSheet = false }
//        )
//    }
//
//    // Add Text Dialog
//    if (showAddTextDialog) {
//        AddTextDialog(
//            onAdd = { text, name ->
//                scope.launch(Dispatchers.IO) {
//                    isLoading = true
//                    status = "Processing text..."
//                    errorMessage = ""
//
//                    val result = graph.addText(text, name)
//                    if (result.isSuccess) {
//                        val added = result.getOrThrow()
//                        status = "Added ${added.size} nodes"
//                        refreshNodes()
//                    } else {
//                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to add text"
//                    }
//                    isLoading = false
//                }
//                showAddTextDialog = false
//            },
//            onDismiss = { showAddTextDialog = false }
//        )
//    }
//
//    // Chat Picker Dialog
//    if (showChatPickerDialog) {
//        ChatPickerDialog(
//            onChatSelected = { chatInfo, messages ->
//                scope.launch(Dispatchers.IO) {
//                    isLoading = true
//                    status = "Processing chat..."
//                    errorMessage = ""
//
//                    val result = graph.addChatMessages(
//                        messages = messages,
//                        chatId = chatInfo.chatId,
//                        chatName = "Chat ${chatInfo.chatId.take(8)}",
//                        asConversationWindows = false
//                    )
//
//                    if (result.isSuccess) {
//                        val added = result.getOrThrow()
//                        status = "Added ${added.size} nodes from chat"
//                        refreshNodes()
//                    } else {
//                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to add chat"
//                    }
//                    isLoading = false
//                }
//                showChatPickerDialog = false
//            },
//            onDismiss = { showChatPickerDialog = false }
//        )
//    }
//
//    // Export Dialog
//    if (showExportDialog) {
//        ExportDialog(
//            graph = graph,
//            cacheDir = context.cacheDir,
//            onExported = { path, recoveryKey ->
//                status = "Exported to: $path"
//                showExportDialog = false
//            },
//            onDismiss = { showExportDialog = false }
//        )
//    }
//
//    // Load Dialog
//    if (showLoadDialog) {
//        LoadPacketDialog(
//            cacheDir = context.cacheDir,
//            onLoaded = { packetState, graphData ->
//                scope.launch(Dispatchers.IO) {
//                    isLoading = true
//                    status = "Loading graph..."
//
//                    val result = graph.deserialize(graphData)
//                    if (result.isSuccess) {
//                        loadedPacketInfo = packetState
//                        refreshNodes()
//                        status = "Loaded: ${packetState.metadata.name}"
//                    } else {
//                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to load graph"
//                    }
//                    isLoading = false
//                }
//                showLoadDialog = false
//            },
//            onDismiss = { showLoadDialog = false }
//        )
//    }
//}


// Data Classes


data class LoadedPacketState(
    val packetId: String,
    val metadata: PacketMetadata,
    val session: PacketSession,
    val recoveryKey: String? = null
)


// Components


@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun SearchResultCard(
    result: QueryResult,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = result.node.sourceType.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${(result.score * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = result.node.content.take(150) + if (result.node.content.length > 150) "..." else "",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            if (result.connectedNodes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "+${result.connectedNodes.size} connected",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}


// Settings Bottom Sheet


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsBottomSheet(
    settings: GraphSettings,
    onSettingsChange: (GraphSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Graph Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            SettingSlider(
                label = "Edge Threshold",
                value = settings.edgeThreshold,
                valueRange = 0.5f..0.95f,
                valueDisplay = { "%.2f".format(it) },
                onValueChange = { onSettingsChange(settings.copy(edgeThreshold = it)) }
            )

            SettingSlider(
                label = "Max Edges Per Node",
                value = settings.maxEdgesPerNode.toFloat(),
                valueRange = 3f..30f,
                steps = 26,
                valueDisplay = { it.toInt().toString() },
                onValueChange = { onSettingsChange(settings.copy(maxEdgesPerNode = it.toInt())) }
            )

            SettingSlider(
                label = "Traversal Depth",
                value = settings.traversalDepth.toFloat(),
                valueRange = 1f..3f,
                steps = 1,
                valueDisplay = { "${it.toInt()}-hop" },
                onValueChange = { onSettingsChange(settings.copy(traversalDepth = it.toInt())) }
            )

            SettingSlider(
                label = "Chunk Size (tokens)",
                value = settings.chunkSizeTokens.toFloat(),
                valueRange = 128f..1024f,
                steps = 6,
                valueDisplay = { it.toInt().toString() },
                onValueChange = { onSettingsChange(settings.copy(chunkSizeTokens = it.toInt())) }
            )

            SettingSlider(
                label = "Chunk Overlap (tokens)",
                value = settings.chunkOverlapTokens.toFloat(),
                valueRange = 0f..100f,
                steps = 9,
                valueDisplay = { it.toInt().toString() },
                onValueChange = { onSettingsChange(settings.copy(chunkOverlapTokens = it.toInt())) }
            )

            HorizontalDivider()

            TextButton(
                onClick = { onSettingsChange(GraphSettings.DEFAULT) },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Reset to Defaults")
            }
        }
    }
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    valueDisplay: (Float) -> String,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = valueDisplay(value),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )
    }
}


// Add Text Dialog


@Composable
private fun AddTextDialog(
    onAdd: (text: String, name: String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Text Content") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Source Name") },
                    placeholder = { Text("e.g., My Notes") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Text Content") },
                    placeholder = { Text("Paste your text here...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    maxLines = 10
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(text, name.ifEmpty { "Text Document" }) },
                enabled = text.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}


// Chat Picker Dialog


@Composable
private fun ChatPickerDialog(
    onChatSelected: (ChatInfo, List<com.dark.tool_neuron.models.messages.Messages>) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val chatRepository = remember { ChatRepository() }

    var chats by remember { mutableStateOf<List<ChatInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val result = chatRepository.getAllChats()
        if (result.isSuccess) {
            chats = result.getOrThrow()
        } else {
            error = result.exceptionOrNull()?.message
        }
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Chat") },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                when {
                    isLoading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    error != null -> {
                        Text(
                            text = error ?: "Error loading chats",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    chats.isEmpty() -> {
                        Text(text = "No chats found", modifier = Modifier.align(Alignment.Center))
                    }
                    else -> {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(chats) { chat ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            scope.launch(Dispatchers.IO) {
                                                val messagesResult = chatRepository.getMessages(chat.chatId)
                                                if (messagesResult.isSuccess) {
                                                    onChatSelected(chat, messagesResult.getOrThrow())
                                                }
                                            }
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "Chat ${chat.chatId.take(8)}...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${chat.messageCount} messages",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}


// Export Dialog - Full Options


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportDialog(
    graph: NeuronGraph,
    cacheDir: File,
    onExported: (path: String, recoveryKey: String) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()

    // Metadata
    var packName by remember { mutableStateOf("knowledge_pack") }
    var description by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("knowledge") }
    var language by remember { mutableStateOf("en") }
    var version by remember { mutableStateOf("1.0") }
    var tagsText by remember { mutableStateOf("") }

    // Config
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var loadingMode by remember { mutableStateOf(LoadingMode.EMBEDDED) }
    var compress by remember { mutableStateOf(true) }

    // Read-only users
    var showAddUserDialog by remember { mutableStateOf(false) }
    var readOnlyUsers by remember { mutableStateOf<List<UserCredentials>>(emptyList()) }

    // State
    var isExporting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var exportedRecoveryKey by remember { mutableStateOf<String?>(null) }
    var currentTab by remember { mutableIntStateOf(0) }

    if (exportedRecoveryKey != null) {
        // Show recovery key
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Export Successful") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(
                        TnIcons.CircleCheck,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(48.dp)
                            .align(Alignment.CenterHorizontally)
                    )

                    Text(
                        text = "Your pack has been exported successfully!",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Recovery Key (SAVE THIS!)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = exportedRecoveryKey!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }

                    Text(
                        text = "If you forget your password, you'll need this key to recover access.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onExported("${cacheDir}/${packName}.neuron", exportedRecoveryKey!!)
                }) {
                    Text("Done")
                }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = { if (!isExporting) onDismiss() },
        title = { Text("Export Neuron Pack") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Tabs
                SecondaryTabRow(selectedTabIndex = currentTab) {
                    Tab(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        text = { Text("Basic") }
                    )
                    Tab(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        text = { Text("Metadata") }
                    )
                    Tab(
                        selected = currentTab == 2,
                        onClick = { currentTab = 2 },
                        text = { Text("Users") }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                when (currentTab) {
                    0 -> {
                        // Basic tab
                        OutlinedTextField(
                            value = packName,
                            onValueChange = { packName = it },
                            label = { Text("Pack Name *") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Admin Password *") },
                            singleLine = true,
                            visualTransformation = if (passwordVisible)
                                VisualTransformation.None
                            else
                                PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        if (passwordVisible) TnIcons.EyeOff
                                        else TnIcons.Eye,
                                        contentDescription = "Toggle password"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Loading Mode
                        Text(
                            text = "Loading Mode",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = loadingMode == LoadingMode.EMBEDDED,
                                onClick = { loadingMode = LoadingMode.EMBEDDED },
                                label = { Text("Embedded") }
                            )
                            FilterChip(
                                selected = loadingMode == LoadingMode.TRANSIENT,
                                onClick = { loadingMode = LoadingMode.TRANSIENT },
                                label = { Text("Transient") }
                            )
                        }

                        // Compress
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Enable Compression (LZ4)")
                            Switch(
                                checked = compress,
                                onCheckedChange = { compress = it }
                            )
                        }
                    }

                    1 -> {
                        // Metadata tab
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Description") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            maxLines = 4
                        )

                        OutlinedTextField(
                            value = domain,
                            onValueChange = { domain = it },
                            label = { Text("Domain") },
                            placeholder = { Text("e.g., medical, legal, tech") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = language,
                                onValueChange = { language = it },
                                label = { Text("Language") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = version,
                                onValueChange = { version = it },
                                label = { Text("Version") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        OutlinedTextField(
                            value = tagsText,
                            onValueChange = { tagsText = it },
                            label = { Text("Tags (comma separated)") },
                            placeholder = { Text("ai, knowledge, research") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    2 -> {
                        // Users tab
                        Text(
                            text = "Read-Only Users",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = "Add users who can read but not modify the pack",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (readOnlyUsers.isNotEmpty()) {
                            readOnlyUsers.forEachIndexed { index, user ->
                                Card(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = user.label,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = user.permissions.name,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                readOnlyUsers = readOnlyUsers.toMutableList().apply {
                                                    removeAt(index)
                                                }
                                            }
                                        ) {
                                            Icon(TnIcons.Trash, contentDescription = "Remove")
                                        }
                                    }
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { showAddUserDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(TnIcons.UserPlus, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add User")
                        }
                    }
                }

                if (error != null) {
                    Text(
                        text = error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        isExporting = true
                        error = null

                        try {
                            val packetManager = NeuronPacketManager()
                            val outputPath = "${cacheDir}/${packName}.neuron"
                            val payload = graph.serialize()

                            val tags = tagsText.split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }

                            val config = ExportConfig(
                                adminPassword = password,
                                readOnlyUsers = readOnlyUsers,
                                loadingMode = loadingMode,
                                compress = compress
                            )

                            val metadata = PacketMetadata(
                                name = packName,
                                description = description,
                                domain = domain,
                                language = language,
                                version = version,
                                tags = tags,
                                loadingMode = loadingMode
                            )

                            val result = packetManager.export(
                                File(outputPath),
                                metadata,
                                payload,
                                config
                            )

                            if (result.isSuccess) {
                                exportedRecoveryKey = result.getOrThrow().recoveryKey
                            } else {
                                error = result.exceptionOrNull()?.message
                            }
                        } catch (e: Exception) {
                            error = e.message
                        }

                        isExporting = false
                    }
                },
                enabled = !isExporting && packName.isNotBlank() && password.isNotBlank()
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Export")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isExporting) {
                Text("Cancel")
            }
        }
    )

    // Add User Dialog
    if (showAddUserDialog) {
        AddUserDialog(
            onAdd = { user ->
                readOnlyUsers = readOnlyUsers + user
                showAddUserDialog = false
            },
            onDismiss = { showAddUserDialog = false }
        )
    }
}

@Composable
private fun AddUserDialog(
    onAdd: (UserCredentials) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var permission by remember { mutableStateOf(Permission.READ) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add User") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    placeholder = { Text("e.g., Reader, Guest") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(text = "Permission", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = permission == Permission.READ,
                            onClick = { permission = Permission.READ },
                            label = { Text("Read") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = permission == Permission.WRITE,
                            onClick = { permission = Permission.WRITE },
                            label = { Text("Write") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = permission == Permission.READ_WRITE_ADMIN,
                            onClick = { permission = Permission.READ_WRITE_ADMIN },
                            label = { Text("Admin") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(UserCredentials(password, label, permission)) },
                enabled = label.isNotBlank() && password.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}


// Load Packet Dialog


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoadPacketDialog(
    cacheDir: File,
    onLoaded: (LoadedPacketState, ByteArray) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedFile by remember { mutableStateOf<File?>(null) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loadingStep by remember { mutableStateOf("") }
    var packetInfo by remember { mutableStateOf<PacketInfo?>(null) }

    // File picker
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                // Copy to cache for native access
                val inputStream = context.contentResolver.openInputStream(uri)
                val tempFile = File(cacheDir, "temp_load.neuron")
                inputStream?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                selectedFile = tempFile

                // Open and get info
                scope.launch(Dispatchers.IO) {
                    val manager = NeuronPacketManager()
                    val result = manager.open(tempFile)
                    if (result.isSuccess) {
                        packetInfo = manager.getPacketInfo()
                    } else {
                        error = result.exceptionOrNull()?.message
                    }
                }
            } catch (e: Exception) {
                error = e.message
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("Load Neuron Pack") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isLoading) {
                    // Loading state
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = loadingStep,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    // File selection
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { filePicker.launch("*/*") },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                TnIcons.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (selectedFile != null) "File Selected" else "Select .neuron file",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (selectedFile != null) {
                                    Text(
                                        text = selectedFile!!.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                TnIcons.ChevronRight,
                                contentDescription = null
                            )
                        }
                    }

                    // Packet info
                    if (packetInfo != null) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Packet Info",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "ID: ${packetInfo!!.packetId.take(16)}...",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "Users: ${packetInfo!!.userCount}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "Mode: ${packetInfo!!.loadingMode.name}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    // Password
                    if (selectedFile != null) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            singleLine = true,
                            visualTransformation = if (passwordVisible)
                                VisualTransformation.None
                            else
                                PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        if (passwordVisible) TnIcons.EyeOff
                                        else TnIcons.Eye,
                                        contentDescription = "Toggle password"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (error != null) {
                        Text(
                            text = error ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        isLoading = true
                        error = null

                        try {
                            val manager = NeuronPacketManager()

                            loadingStep = "Opening packet..."
                            val openResult = manager.open(selectedFile!!)
                            if (openResult.isFailure) {
                                error = openResult.exceptionOrNull()?.message
                                isLoading = false
                                return@launch
                            }

                            loadingStep = "Authenticating..."
                            val authResult = manager.authenticate(password)
                            if (authResult.isFailure) {
                                error = authResult.exceptionOrNull()?.message
                                isLoading = false
                                return@launch
                            }

                            val session = authResult.getOrThrow()

                            loadingStep = "Decrypting payload..."
                            val payloadResult = manager.decryptPayload(session)
                            if (payloadResult.isFailure) {
                                error = payloadResult.exceptionOrNull()?.message
                                isLoading = false
                                return@launch
                            }

                            val info = manager.getPacketInfo()
                            val metadata = PacketMetadata(
                                packetId = info?.packetId ?: "",
                                loadingMode = info?.loadingMode ?: LoadingMode.EMBEDDED
                            )

                            val state = LoadedPacketState(
                                packetId = session.packetId,
                                metadata = metadata,
                                session = session
                            )

                            onLoaded(state, payloadResult.getOrThrow())

                        } catch (e: Exception) {
                            error = e.message
                        }

                        isLoading = false
                    }
                },
                enabled = !isLoading && selectedFile != null && password.isNotBlank()
            ) {
                Text("Load")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Cancel")
            }
        }
    )
}