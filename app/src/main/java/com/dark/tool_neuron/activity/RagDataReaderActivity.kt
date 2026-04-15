package com.dark.tool_neuron.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import com.dark.tool_neuron.ui.components.PasswordTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.R
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.engine.EmbeddingEngine
import com.dark.tool_neuron.neuron_example.GraphSettings
import com.dark.tool_neuron.neuron_example.NeuronGraph
import com.dark.tool_neuron.neuron_example.NeuronNode
import com.dark.tool_neuron.ui.theme.NeuroVerseTheme
import com.neuronpacket.NeuronPacketManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import com.dark.tool_neuron.ui.icons.TnIcons

@AndroidEntryPoint
class RagDataReaderActivity : ComponentActivity() {

    @Inject
    lateinit var embeddingEngine: EmbeddingEngine

    companion object {
        const val EXTRA_RAG_FILE_PATH = "rag_file_path"
        const val EXTRA_RAG_NAME = "rag_name"
        const val EXTRA_IS_ENCRYPTED = "is_encrypted"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val filePath = intent.getStringExtra(EXTRA_RAG_FILE_PATH)
        val password = intent.getStringExtra("rag_password")
        val ragName = intent.getStringExtra(EXTRA_RAG_NAME) ?: "Unknown RAG"
        val isEncrypted = intent.getBooleanExtra(EXTRA_IS_ENCRYPTED, false)

        setContent {
            NeuroVerseTheme {
                if (filePath != null) {
                    RagDataReaderScreen(
                        filePath = filePath,
                        password = password,
                        ragName = ragName,
                        isEncrypted = isEncrypted,
                        embeddingEngine = embeddingEngine,
                        onBackClick = { finish() }
                    )
                } else {
                    ErrorScreen(
                        message = "No RAG file path provided",
                        onBackClick = { finish() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RagDataReaderScreen(
    filePath: String,
    password: String?,
    ragName: String,
    isEncrypted: Boolean,
    embeddingEngine: EmbeddingEngine,
    onBackClick: () -> Unit
) {
    var loadingState by remember { mutableStateOf<RagLoadingState>(RagLoadingState.Loading) }
    var graph by remember { mutableStateOf<NeuronGraph?>(null) }
    var nodes by remember { mutableStateOf<List<NeuronNode>>(emptyList()) }
    var selectedNode by remember { mutableStateOf<NeuronNode?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var enteredPassword by remember { mutableStateOf(password) }

    val scope = rememberCoroutineScope()

    // Show password dialog if encrypted and no password provided
    LaunchedEffect(isEncrypted, enteredPassword) {
        if (isEncrypted && enteredPassword == null) {
            showPasswordDialog = true
            loadingState = RagLoadingState.Loading
        } else {
            scope.launch {
                loadingState = RagLoadingState.Loading
                try {
                    val loadedGraph = withContext(Dispatchers.IO) {
                        loadRagFile(filePath, enteredPassword, isEncrypted, embeddingEngine)
                    }

                    if (loadedGraph != null) {
                        graph = loadedGraph
                        nodes = loadedGraph.getAllNodes()
                        loadingState = RagLoadingState.Success
                    } else {
                        loadingState = RagLoadingState.Error("Failed to load RAG file. ${if (isEncrypted) "Check your password." else ""}")
                    }
                } catch (e: Exception) {
                    loadingState = RagLoadingState.Error(e.message ?: "Unknown error")
                }
            }
        }
    }

    // Password Dialog
    if (showPasswordDialog) {
        PasswordInputDialog(
            onDismiss = {
                showPasswordDialog = false
                onBackClick()
            },
            onConfirm = { pwd ->
                enteredPassword = pwd
                showPasswordDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(ragName)
                    }
                },
                navigationIcon = {
                    ActionButton(
                        onClickListener = onBackClick,
                        icon = TnIcons.ArrowLeft,
                        contentDescription = "Back",
                        shape = RoundedCornerShape(12.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        when (val state = loadingState) {
            is RagLoadingState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        LoadingIndicator()
                        Text("Loading RAG data...")
                    }
                }
            }

            is RagLoadingState.Error -> {
                ErrorScreen(
                    message = state.message,
                    onBackClick = onBackClick
                )
            }

            is RagLoadingState.Success -> {
                val filteredNodes = if (searchQuery.isBlank()) {
                    nodes
                } else {
                    nodes.filter {
                        it.content.contains(searchQuery, ignoreCase = true) ||
                        it.id.contains(searchQuery, ignoreCase = true) ||
                        it.metadata.sourceName.contains(searchQuery, ignoreCase = true) ||
                        it.metadata.sourceId.contains(searchQuery, ignoreCase = true)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    // Node List
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        // Search Bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search nodes...") },
                            leadingIcon = { Icon(TnIcons.Search, null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(TnIcons.XCircle, null)
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Graph Stats Card
                        graph?.let { g ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        "Graph Statistics",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )

                                    StatRow("Model", g.getEmbeddingModelName())
                                    StatRow("Dimension", "${g.getEmbeddingDimension()}D")
                                    StatRow("Nodes", "${g.nodeCount}")
                                    StatRow("Connections", "${g.getAllNodes().sumOf { it.edges.size }}")
                                }
                            }
                        }

                        HorizontalDivider()

                        // Node List
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (filteredNodes.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "No nodes found",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                items(filteredNodes) { node ->
                                    NodeCard(
                                        node = node,
                                        isSelected = selectedNode?.id == node.id,
                                        onClick = { selectedNode = node }
                                    )
                                }
                            }
                        }
                    }

                    // Node Detail View
                    val node = selectedNode
                    val g = graph
                    if (node != null && g != null) {
                        VerticalDivider()

                        NodeDetailView(
                            node = node,
                            graph = g,
                            onClose = { selectedNode = null }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NodeCard(
    node: NeuronNode,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Node ${node.id.take(8)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (node.edges.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            "${node.edges.size} connections",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Text(
                node.content,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = if (isSelected)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (node.metadata.sourceName.isNotEmpty()) {
                Text(
                    node.metadata.sourceName,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isSelected)
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun NodeDetailView(
    node: NeuronNode,
    graph: NeuronGraph,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(400.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Node Details",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            ActionButton(
                onClickListener = onClose,
                icon = TnIcons.X,
                contentDescription = "Close",
                shape = RoundedCornerShape(12.dp)
            )
        }

        HorizontalDivider()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Node ID
            item {
                DetailSection(title = "Node ID") {
                    Text(
                        node.id,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Content
            item {
                DetailSection(title = "Content") {
                    Text(
                        node.content,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Metadata
            item {
                DetailSection(title = "Metadata") {
                    if (node.metadata.sourceName.isNotEmpty()) {
                        Text(
                            "Source: ${node.metadata.sourceName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (node.metadata.sourceId.isNotEmpty()) {
                        Text(
                            "Source ID: ${node.metadata.sourceId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                    Text(
                        "Position: ${node.metadata.position}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (node.metadata.extras.isNotEmpty()) {
                        Text(
                            "Extras: ${node.metadata.extras}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Embedding Info
            if (node.embedding != null) {
                item {
                    DetailSection(title = "Embedding") {
                        Text(
                            "Dimension: ${node.embedding!!.size}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "First 5 values: ${node.embedding!!.take(5).joinToString(", ") { "%.4f".format(it) }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
            }

            // Connected Nodes
            if (node.edges.isNotEmpty()) {
                item {
                    DetailSection(title = "Connected Nodes (${node.edges.size})") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            node.edges.forEach { edge ->
                                val connectedNode = graph.getNode(edge.targetId)
                                if (connectedNode != null) {
                                    ConnectedNodeCard(
                                        edge = edge,
                                        node = connectedNode
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
fun ConnectedNodeCard(
    edge: com.dark.tool_neuron.neuron_example.NeuronEdge,
    node: NeuronNode
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "→ ${edge.targetId.take(8)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        "${edge.type.name} • ${(edge.weight * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                node.content,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
fun DetailSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                content = content
            )
        }
    }
}

@Composable
fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErrorScreen(
    message: String,
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Error") },
                navigationIcon = {
                    ActionButton(
                        onClickListener = onBackClick,
                        icon = TnIcons.ArrowLeft,
                        contentDescription = "Back",
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    TnIcons.AlertTriangle,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
                FilledTonalButton(
                    onClick = onBackClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(0.1f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Go Back")
                }
            }
        }
    }
}

sealed class RagLoadingState {
    object Loading : RagLoadingState()
    object Success : RagLoadingState()
    data class Error(val message: String) : RagLoadingState()
}

@Composable
fun PasswordInputDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter Password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "This RAG is encrypted. Please enter the password to view its contents.",
                    style = MaterialTheme.typography.bodyMedium
                )
                PasswordTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    showPasswordState = showPassword,
                    onToggleVisibility = { showPassword = !showPassword }
                )
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = { if (password.isNotBlank()) onConfirm(password) },
                enabled = password.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(0.1f),
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Unlock")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}

private const val MAX_RAG_FILE_SIZE = 512L * 1024 * 1024 // 512 MB

suspend fun loadRagFile(
    filePath: String,
    password: String?,
    isEncrypted: Boolean,
    embeddingEngine: EmbeddingEngine
): NeuronGraph? = withContext(Dispatchers.IO) {
    try {
        val file = File(filePath)
        if (!file.exists()) {
            return@withContext null
        }

        // Guard against OOM — RAG files are loaded entirely into memory
        if (file.length() > MAX_RAG_FILE_SIZE) {
            android.util.Log.e("RagDataReader", "RAG file too large: ${file.length()} bytes (max $MAX_RAG_FILE_SIZE)")
            return@withContext null
        }

        val graph = NeuronGraph(embeddingEngine, GraphSettings.DEFAULT)

        if (isEncrypted && password != null) {
            // Load encrypted RAG
            val packetManager = NeuronPacketManager()
            packetManager.open(file)
            val authResult = packetManager.authenticate(password)
            if (authResult.isFailure) {
                return@withContext null
            }
            val payloadResult = packetManager.decryptPayload(authResult.getOrThrow())
            if (payloadResult.isFailure) {
                return@withContext null
            }
            graph.deserialize(payloadResult.getOrThrow())
            packetManager.close()
        } else {
            // Load unencrypted RAG
            val payload = file.readBytes()
            graph.deserialize(payload)
        }

        graph
    } catch (e: OutOfMemoryError) {
        android.util.Log.e("RagDataReader", "OOM loading RAG file (${File(filePath).length()} bytes)", e)
        null
    } catch (e: Exception) {
        android.util.Log.e("RagDataReader", "Failed to load RAG: ${e.message}", e)
        null
    }
}