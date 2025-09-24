package com.dark.neuroverse.activity

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.neuroverse.BuildConfig
import com.dark.neuroverse.ui.theme.NeuroVerseTheme
import com.dark.userdata.*
import com.dark.userdata.ntds.*
import com.dark.userdata.ntds.neuron_tree.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.crypto.SecretKey

// Clean, minimal ViewModel
class UserDataViewModel(private val context: android.content.Context) : ViewModel() {

    private val _tree = MutableStateFlow<NeuronTree?>(null)
    val tree: StateFlow<NeuronTree?> = _tree.asStateFlow()

    private val _selectedNodeId = MutableStateFlow<String?>("root")
    val selectedNodeId: StateFlow<String?> = _selectedNodeId.asStateFlow()

    private val _expandedNodes = MutableStateFlow<Set<String>>(setOf("root"))
    val expandedNodes: StateFlow<Set<String>> = _expandedNodes.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val encryptionKey: SecretKey = getOrCreateHardwareBackedAesKey(BuildConfig.ALIAS)

    init {
        loadTree()
    }

    fun loadTree() {
        _isLoading.value = true
        try {
            val loadedTree = readBrainFile(encryptionKey, context)
            _tree.value = loadedTree
        } catch (e: Exception) {
            // Handle error silently for clean UX
        } finally {
            _isLoading.value = false
        }
    }

    fun saveTree() {
        _tree.value?.let { tree ->
            try {
                saveTree(tree, context, BuildConfig.ALIAS)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun selectNode(nodeId: String) {
        _selectedNodeId.value = nodeId
    }

    fun toggleNodeExpansion(nodeId: String) {
        _expandedNodes.update { expanded ->
            if (nodeId in expanded) {
                expanded - nodeId
            } else {
                expanded + nodeId
            }
        }
    }

    fun updateNodeContent(nodeId: String, content: String) {
        _tree.value?.let { tree ->
            val node = tree.getNodeDirect(nodeId)
            if (node.id.isNotEmpty()) {
                node.data.content = content
                saveTree() // Auto-save
            }
        }
    }

    fun addChildNode(parentId: String, nodeType: NodeType) {
        _tree.value?.let { tree ->
            val newNode = NeuronNode(data = NodeData("", nodeType))
            tree.addChild(parentId, newNode)
            _selectedNodeId.value = newNode.id
            _expandedNodes.update { it + parentId }
            saveTree()
        }
    }

    fun deleteNode(nodeId: String) {
        if (nodeId == "root") return
        _tree.value?.let { tree ->
            tree.deleteNodeById(nodeId)
            _selectedNodeId.value = "root"
            saveTree()
        }
    }

    fun getSelectedNode(): NeuronNode? {
        val nodeId = _selectedNodeId.value ?: return null
        return _tree.value?.getNodeDirect(nodeId)?.takeIf { it.id.isNotEmpty() }
    }
}

class UserDataViewModelFactory(private val context: android.content.Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return UserDataViewModel(context) as T
    }
}

class UserDataActivity : ComponentActivity() {

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NeuroVerseTheme {
                UserDataScreen()
            }
        }
    }
}

@Composable
fun UserDataScreen(
    viewModel: UserDataViewModel = viewModel(
        factory = UserDataViewModelFactory(LocalContext.current)
    )
) {
    val tree by viewModel.tree.collectAsStateWithLifecycle()
    val selectedNodeId by viewModel.selectedNodeId.collectAsStateWithLifecycle()
    val expandedNodes by viewModel.expandedNodes.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val selectedNode = viewModel.getSelectedNode()

    if (isLoading) {
        LoadingScreen()
        return
    }

    if (tree == null) {
        EmptyStateScreen { viewModel.loadTree() }
        return
    }

    Scaffold {
        Row(
            modifier = Modifier
                .padding(it)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Left sidebar - Tree view
            TreeSidebar(
                tree = tree!!,
                selectedNodeId = selectedNodeId,
                expandedNodes = expandedNodes,
                onNodeSelect = viewModel::selectNode,
                onToggleExpansion = viewModel::toggleNodeExpansion,
                onAddChild = { parentId, type -> viewModel.addChildNode(parentId, type) },
                onDelete = viewModel::deleteNode,
                modifier = Modifier.weight(0.3f)
            )

            // Divider
            VerticalDivider()

            // Main content area
            MainContentArea(
                selectedNode = selectedNode,
                onContentChange = { nodeId, content ->
                    viewModel.updateNodeContent(nodeId, content)
                },
                modifier = Modifier.weight(0.7f)
            )
        }
    }

}

@Composable
fun TreeSidebar(
    tree: NeuronTree,
    selectedNodeId: String?,
    expandedNodes: Set<String>,
    onNodeSelect: (String) -> Unit,
    onToggleExpansion: (String) -> Unit,
    onAddChild: (String, NodeType) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Brain Tree",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Tree structure
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                item {
                    TreeNodeItem(
                        node = tree.root,
                        level = 0,
                        isSelected = selectedNodeId == tree.root.id,
                        isExpanded = tree.root.id in expandedNodes,
                        onSelect = { onNodeSelect(tree.root.id) },
                        onToggleExpansion = { onToggleExpansion(tree.root.id) },
                        onAddChild = onAddChild,
                        onDelete = onDelete
                    )
                }

                if (tree.root.id in expandedNodes) {
                    items(tree.root.children) { child ->
                        TreeNodeSubtree(
                            node = child,
                            level = 1,
                            selectedNodeId = selectedNodeId,
                            expandedNodes = expandedNodes,
                            onNodeSelect = onNodeSelect,
                            onToggleExpansion = onToggleExpansion,
                            onAddChild = onAddChild,
                            onDelete = onDelete
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TreeNodeSubtree(
    node: NeuronNode,
    level: Int,
    selectedNodeId: String?,
    expandedNodes: Set<String>,
    onNodeSelect: (String) -> Unit,
    onToggleExpansion: (String) -> Unit,
    onAddChild: (String, NodeType) -> Unit,
    onDelete: (String) -> Unit
) {
    Column {
        TreeNodeItem(
            node = node,
            level = level,
            isSelected = selectedNodeId == node.id,
            isExpanded = node.id in expandedNodes,
            onSelect = { onNodeSelect(node.id) },
            onToggleExpansion = { onToggleExpansion(node.id) },
            onAddChild = onAddChild,
            onDelete = onDelete
        )

        if (node.id in expandedNodes) {
            node.children.forEach { child ->
                TreeNodeSubtree(
                    node = child,
                    level = level + 1,
                    selectedNodeId = selectedNodeId,
                    expandedNodes = expandedNodes,
                    onNodeSelect = onNodeSelect,
                    onToggleExpansion = onToggleExpansion,
                    onAddChild = onAddChild,
                    onDelete = onDelete
                )
            }
        }
    }
}

@Composable
fun TreeNodeItem(
    node: NeuronNode,
    level: Int,
    isSelected: Boolean,
    isExpanded: Boolean,
    onSelect: () -> Unit,
    onToggleExpansion: () -> Unit,
    onAddChild: (String, NodeType) -> Unit,
    onDelete: (String) -> Unit
) {
    var showDropdown by remember { mutableStateOf(false) }
    val hasChildren = node.children.isNotEmpty()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                else Color.Transparent
            )
            .clickable { onSelect() }
            .padding(
                start = (level * 16).dp + 8.dp,
                top = 4.dp,
                bottom = 4.dp,
                end = 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Expand/collapse icon
        if (hasChildren) {
            IconButton(
                onClick = onToggleExpansion,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        } else {
            Spacer(modifier = Modifier.width(20.dp))
        }

        // Node type icon
        Icon(
            imageVector = getNodeTypeIcon(node.data.type),
            contentDescription = node.data.type.name,
            tint = getNodeTypeColor(node.data.type),
            modifier = Modifier.size(16.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Node label
        Text(
            text = getNodeDisplayName(node),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        // Actions menu
        Box {
            IconButton(
                onClick = { showDropdown = true },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Outlined.MoreVert,
                    contentDescription = "Actions",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp)
                )
            }

            DropdownMenu(
                expanded = showDropdown,
                onDismissRequest = { showDropdown = false }
            ) {
                NodeType.values().forEach { type ->
                    DropdownMenuItem(
                        text = { Text("Add $type", fontSize = 13.sp) },
                        onClick = {
                            onAddChild(node.id, type)
                            showDropdown = false
                        },
                        leadingIcon = {
                            Icon(
                                getNodeTypeIcon(type),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = getNodeTypeColor(type)
                            )
                        }
                    )
                }

                if (node.id != "root") {
                    Divider()
                    DropdownMenuItem(
                        text = { Text("Delete", fontSize = 13.sp, color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            onDelete(node.id)
                            showDropdown = false
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MainContentArea(
    selectedNode: NeuronNode?,
    onContentChange: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (selectedNode != null) {
            NodeEditor(
                node = selectedNode,
                onContentChange = { content ->
                    onContentChange(selectedNode.id, content)
                }
            )
        } else {
            EmptySelection()
        }
    }
}

@Composable
fun NodeEditor(
    node: NeuronNode,
    onContentChange: (String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var content by remember(node.id) { mutableStateOf(node.data.content) }

    LaunchedEffect(node.data.content) {
        content = node.data.content
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = getNodeTypeIcon(node.data.type),
                contentDescription = node.data.type.name,
                tint = getNodeTypeColor(node.data.type),
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = getNodeDisplayName(node),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = node.data.type.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Content editor
        BasicTextField(
            value = content,
            onValueChange = { newContent ->
                content = newContent
                onContentChange(newContent)
            },
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester),
            textStyle = TextStyle(
                fontSize = 16.sp,
                lineHeight = 24.sp,
                color = MaterialTheme.colorScheme.onBackground
            ),
            decorationBox = { innerTextField ->
                if (content.isEmpty()) {
                    Text(
                        text = "Start writing...",
                        style = TextStyle(
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                        )
                    )
                }
                innerTextField()
            }
        )
    }
}

@Composable
fun EmptySelection() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Outlined.Topic,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Select a node to edit",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Loading brain data...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun EmptyStateScreen(onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Outlined.Error,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Could not load brain data",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

// Helper functions
fun getNodeDisplayName(node: NeuronNode): String {
    return when {
        node.id == "root" -> "Brain Root"
        node.id == "chatHistory" -> "Chat History"
        node.data.content.isNotBlank() -> {
            val firstLine = node.data.content.lines().firstOrNull()?.trim() ?: ""
            if (firstLine.length > 20) firstLine.take(20) + "..." else firstLine
        }
        else -> node.id.take(8) + "..."
    }
}

fun getNodeTypeIcon(type: NodeType): ImageVector {
    return when (type) {
        NodeType.ROOT -> Icons.Outlined.AccountTree
        NodeType.OPERATOR -> Icons.Outlined.Settings
        NodeType.HOLDER -> Icons.Outlined.Folder
        NodeType.STEAM -> Icons.Outlined.Stream
        NodeType.LEAF -> Icons.Outlined.Description
    }
}

fun getNodeTypeColor(type: NodeType): Color {
    return when (type) {
        NodeType.ROOT -> Color(0xFF4CAF50)
        NodeType.OPERATOR -> Color(0xFF2196F3)
        NodeType.HOLDER -> Color(0xFFFF9800)
        NodeType.STEAM -> Color(0xFF9C27B0)
        NodeType.LEAF -> Color(0xFF607D8B)
    }
}