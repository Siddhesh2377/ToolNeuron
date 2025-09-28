package com.dark.neuroverse.ui.screens

import android.util.Log
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Height
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stream
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.neuroverse.model.NodeStats
import com.dark.neuroverse.model.ViewMode
import com.dark.neuroverse.ui.components.MarkdownText
import com.dark.neuroverse.ui.theme.rDP
import com.dark.neuroverse.viewModel.userdata.UserDataViewerViewModel
import com.dark.neuroverse.viewModel.userdata.UserDataViewerViewModelFactory
import com.dark.userdata.helpers.MemoryDataTags
import com.dark.userdata.ntds.neuron_tree.NeuronNode
import com.dark.userdata.ntds.neuron_tree.NeuronTree
import com.dark.userdata.ntds.neuron_tree.NodeType
import kotlinx.coroutines.launch
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDataViewerScreen(
    viewModel: UserDataViewerViewModel = viewModel(
        factory = UserDataViewerViewModelFactory(LocalContext.current)
    )
) {
    val tree by viewModel.tree.collectAsStateWithLifecycle()
    val selectedNodeId by viewModel.selectedNodeId.collectAsStateWithLifecycle()
    val expandedNodes by viewModel.expandedNodes.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    var selectedNode = viewModel.getSelectedNode()

    if (isLoading) {
        LoadingScreen()
        return
    }

    if (tree == null) {
        EmptyStateScreen { viewModel.loadTree() }
        return
    }

    LaunchedEffect(selectedNodeId) {
        selectedNode = viewModel.getSelectedNode()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface, topBar = {
            TopAppBar(title = {
                Text(
                    text = "User Data Viewer", fontWeight = FontWeight.SemiBold
                )
            }, actions = {
                // View mode toggle
                Row {
                    IconButton(
                        onClick = { viewModel.setViewMode(ViewMode.TREE) }) {
                        Icon(
                            Icons.Outlined.AccountTree,
                            contentDescription = "Tree View",
                            tint = if (viewMode == ViewMode.TREE) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { viewModel.setViewMode(ViewMode.LIST) }) {
                        Icon(
                            Icons.Outlined.List,
                            contentDescription = "List View",
                            tint = if (viewMode == ViewMode.LIST) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { viewModel.setViewMode(ViewMode.STATS) }) {
                        Icon(
                            Icons.Outlined.Analytics,
                            contentDescription = "Statistics",
                            tint = if (viewMode == ViewMode.STATS) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { viewModel.loadTree() }) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = "Refresh",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            })
        }) { paddingValues ->
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        ModalNavigationDrawer(
            modifier = Modifier
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            drawerState = drawerState,
            drawerContent = {
                TreeNavigationPane(
                    tree = tree!!,
                    modifier = Modifier.widthIn(max = rDP(250.dp)),
                    selectedNodeId = selectedNodeId,
                    expandedNodes = expandedNodes,
                    searchQuery = searchQuery,
                    onNodeSelect = {
                        Log.d("DrawerState", "Node selected: $it")
                        viewModel.selectNode(it)
                        scope.launch {
                            drawerState.close()
                        }
                        Log.d("DrawerState", "Drawer state: ${drawerState.currentValue}")
                    },
                    onToggleExpansion = viewModel::toggleNodeExpansion,
                )
            }) {
            Column {

                // Search bar (except for stats view)
                if (viewMode != ViewMode.STATS) {
                    SearchBar(
                        query = searchQuery,
                        onQueryChange = viewModel::updateSearchQuery,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }

                // Main content based on view mode
                when (viewMode) {
                    ViewMode.TREE -> {
                        Row(modifier = Modifier.fillMaxSize()) {
                            Crossfade(selectedNode) {
                                NodeDetailsPane(
                                    selectedNode = it, modifier = Modifier.weight(0.6f)
                                ) { node ->
                                    viewModel.selectNode(node.id)
                                }
                            }
                        }
                    }

                    ViewMode.LIST -> {
                        NodeListView(
                            nodes = viewModel.getFilteredNodes(),
                            selectedNodeId = selectedNodeId,
                            onNodeSelect = viewModel::selectNode,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    ViewMode.STATS -> {
                        StatisticsView(
                            stats = viewModel.getNodeStats(),
                            tree = tree!!,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search nodes...") },
        leadingIcon = {
            Icon(
                Icons.Outlined.Search, contentDescription = "Search"
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Outlined.Clear, contentDescription = "Clear"
                    )
                }
            }
        },
        singleLine = true,
        modifier = modifier
    )
}

@Composable
fun TreeNavigationPane(
    tree: NeuronTree,
    selectedNodeId: String?,
    expandedNodes: Set<String>,
    searchQuery: String,
    onNodeSelect: (String) -> Unit,
    onToggleExpansion: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        LazyColumn(
            modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Text(
                    text = "Data Structure",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            item {
                TreeNodeViewItem(
                    node = tree.root,
                    level = 0,
                    isSelected = selectedNodeId == tree.root.id,
                    isExpanded = tree.root.id in expandedNodes,
                    searchQuery = searchQuery,
                    onSelect = { onNodeSelect(tree.root.id) },
                    onToggleExpansion = { onToggleExpansion(tree.root.id) })
            }

            if (tree.root.id in expandedNodes) {
                items(tree.root.children) { child ->
                    TreeNodeSubtreeView(
                        node = child,
                        level = 1,
                        selectedNodeId = selectedNodeId,
                        expandedNodes = expandedNodes,
                        searchQuery = searchQuery,
                        onNodeSelect = {
                            onNodeSelect(it)
                        },
                        onToggleExpansion = onToggleExpansion
                    )
                }
            }
        }
    }
}

@Composable
fun TreeNodeSubtreeView(
    node: NeuronNode,
    level: Int,
    selectedNodeId: String?,
    expandedNodes: Set<String>,
    searchQuery: String,
    onNodeSelect: (String) -> Unit,
    onToggleExpansion: (String) -> Unit
) {
    Column {
        TreeNodeViewItem(
            node = node,
            level = level,
            isSelected = selectedNodeId == node.id,
            isExpanded = node.id in expandedNodes,
            searchQuery = searchQuery,
            onSelect = { onNodeSelect(node.id) },
            onToggleExpansion = { onToggleExpansion(node.id) })

        if (node.id in expandedNodes) {
            node.children.forEach { child ->
                TreeNodeSubtreeView(
                    node = child,
                    level = level + 1,
                    selectedNodeId = selectedNodeId,
                    expandedNodes = expandedNodes,
                    searchQuery = searchQuery,
                    onNodeSelect = onNodeSelect,
                    onToggleExpansion = onToggleExpansion
                )
            }
        }
    }
}

@Composable
fun TreeNodeViewItem(
    node: NeuronNode,
    level: Int,
    isSelected: Boolean,
    isExpanded: Boolean,
    searchQuery: String,
    onSelect: () -> Unit,
    onToggleExpansion: () -> Unit
) {
    val hasChildren = node.children.isNotEmpty()
    val isHighlighted = searchQuery.isNotEmpty() && (node.id.lowercase()
        .contains(searchQuery.lowercase()) || node.data.content.lowercase()
        .contains(searchQuery.lowercase()) || node.data.type.name.lowercase()
        .contains(searchQuery.lowercase()))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                    isHighlighted -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    else -> Color.Transparent
                }
            )
            .clickable { onSelect() }
            .padding(
                start = (level * 20).dp + 12.dp, top = 8.dp, bottom = 8.dp, end = 12.dp
            ), verticalAlignment = Alignment.CenterVertically) {
        // Expand/collapse button
        if (hasChildren) {
            IconButton(
                onClick = onToggleExpansion, modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        } else {
            Spacer(modifier = Modifier.width(24.dp))
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Node type icon
        Icon(
            imageVector = getNodeTypeIcon(node.data.type),
            contentDescription = node.data.type.name,
            tint = getNodeTypeColor(node.data.type),
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Node title
            Text(
                text = getNodeDisplayName(node),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Node type and child count
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = node.data.type.name.lowercase(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                if (hasChildren) {
                    Text(
                        text = " • ${node.children.size} child${if (node.children.size == 1) "" else "ren"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(
                            alpha = 0.7f
                        )
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
fun NodeDetailsPane(
    selectedNode: NeuronNode?, modifier: Modifier = Modifier, onNodeSelect: (NeuronNode) -> Unit
) {
    Surface(
        modifier = modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.background
    ) {
        if (selectedNode != null) {
            NodeDetailView(node = selectedNode) {
                onNodeSelect(it)
            }
        } else {
            EmptySelectionView()
        }
    }
}

@Composable
fun NodeDetailView(
    node: NeuronNode, onNodeSelect: (NeuronNode) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            // Header with icon and title
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = getNodeTypeColor(node.data.type).copy(alpha = 0.1f),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = getNodeTypeIcon(node.data.type),
                            contentDescription = node.data.type.name,
                            tint = getNodeTypeColor(node.data.type),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = getNodeDisplayName(node),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = node.data.type.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }
        }

        item {
            HorizontalDivider()
        }

        // Node ID
        item {
            DetailSection(
                title = "Node ID", content = {
                    SelectionContainer {
                        MarkdownText(
                            text = node.id,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                })
        }

        // JSON Structure View
        item {
            DetailSection(
                title = "JSON Structure", content = {
                    JsonNodeView(node = node)
                })
        }

        // Children info
        if (node.children.isNotEmpty()) {
            item {
                DetailSection(
                    title = "Children (${node.children.size})", content = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            node.children.forEach { child ->
                                ChildNodeItem(child = child) {
                                    onNodeSelect(child)
                                }
                            }
                        }
                    })
            }
        }

        // Metadata
        item {
            DetailSection(
                title = "Metadata", content = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetadataRow("Type", node.data.type.name)
                        MetadataRow("Children Count", "${node.children.size}")
                        MetadataRow("Content Length", "${node.data.content.length} characters")
                        MetadataRow(
                            "Has Content",
                            if (node.data.content.isNotBlank()) "Yes" else "No"
                        )
                    }
                })
        }
    }
}

@Composable
fun DetailSection(
    title: String, content: @Composable () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        content()
    }
}

@Composable
fun ChildNodeItem(child: NeuronNode, onClick: (NeuronNode) -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onClick(child)
            }) {
        Row(
            modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = getNodeTypeIcon(child.data.type),
                contentDescription = child.data.type.name,
                tint = getNodeTypeColor(child.data.type),
                modifier = Modifier.size(16.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = getNodeDisplayName(child),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun NodeListView(
    nodes: List<NeuronNode>,
    selectedNodeId: String?,
    onNodeSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        state = rememberLazyListState()
    ) {
        items(nodes) { node ->
            NodeListItem(
                node = node,
                isSelected = selectedNodeId == node.id,
                onClick = { onNodeSelect(node.id) })
        }
    }
}

@Composable
fun NodeListItem(
    node: NeuronNode, isSelected: Boolean, onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = getNodeTypeColor(node.data.type).copy(alpha = 0.1f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = getNodeTypeIcon(node.data.type),
                        contentDescription = node.data.type.name,
                        tint = getNodeTypeColor(node.data.type),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = getNodeDisplayName(node),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "${node.data.type.name.lowercase()} • ${node.children.size} children",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                if (node.data.content.isNotBlank()) {
                    Text(
                        text = node.data.content.take(100) + if (node.data.content.length > 100) "..." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(
                            alpha = 0.8f
                        )
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (node.children.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "${node.children.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StatisticsView(
    stats: NodeStats, tree: NeuronTree, modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Data Statistics",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Overview stats
        item {
            StatsOverviewCard(stats = stats)
        }

        // Node type breakdown
        item {
            NodeTypeBreakdownCard(stats = stats)
        }

        // Content statistics
        item {
            ContentStatsCard(stats = stats)
        }
    }
}

@Composable
fun StatsOverviewCard(stats: NodeStats) {
    ElevatedCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "Overview",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    value = "${stats.total}",
                    label = "Total Nodes",
                    icon = Icons.Outlined.AccountTree
                )

                StatItem(
                    value = "${stats.deepestLevel}",
                    label = "Max Depth",
                    icon = Icons.Outlined.Height
                )

                StatItem(
                    value = "${stats.totalContent}",
                    label = "Characters",
                    icon = Icons.Outlined.Article
                )
            }
        }
    }
}

@Composable
fun StatItem(
    value: String, label: String, icon: ImageVector
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun NodeTypeBreakdownCard(stats: NodeStats) {
    ElevatedCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "Node Type Distribution",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            stats.byType.forEach { (type, count) ->
                if (count > 0) {
                    NodeTypeStatRow(
                        type = type, count = count, total = stats.total
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
fun NodeTypeStatRow(
    type: NodeType, count: Int, total: Int
) {
    val percentage = if (total > 0) (count.toFloat() / total * 100).toInt() else 0

    Row(
        modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = getNodeTypeIcon(type),
            contentDescription = type.name,
            tint = getNodeTypeColor(type),
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = type.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "$count ($percentage%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            LinearProgressIndicator(
                progress = { count.toFloat() / total.coerceAtLeast(1) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = getNodeTypeColor(type),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun ContentStatsCard(stats: NodeStats) {
    ElevatedCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "Content Analysis",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            ContentStatRow(
                label = "Total Characters",
                value = "${stats.totalContent}",
                icon = Icons.Outlined.TextFields
            )

            Spacer(modifier = Modifier.height(12.dp))

            ContentStatRow(
                label = "Average per Node",
                value = "${if (stats.total > 0) stats.totalContent / stats.total else 0}",
                icon = Icons.Outlined.Calculate
            )

            Spacer(modifier = Modifier.height(12.dp))

            ContentStatRow(
                label = "Tree Depth",
                value = "${stats.deepestLevel} levels",
                icon = Icons.Outlined.AccountTree
            )
        }
    }
}

@Composable
fun ContentStatRow(
    label: String, value: String, icon: ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun EmptySelectionView() {
    Box(
        modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Outlined.Visibility,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Select a node to view details",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )

            Text(
                text = "Choose a node from the tree or list to explore its data",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp), strokeWidth = 4.dp
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Loading user data...",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )

            Text(
                text = "Decrypting and parsing brain file",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
fun EmptyStateScreen(onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Outlined.FolderOff,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "No Data Available",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Could not load user data from the brain file",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(
                onClick = onRetry
            ) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Try Again")
            }
        }
    }
}

@Composable
fun JsonNodeView(
    node: NeuronNode, modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(true) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with expand/collapse
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.DataObject,
                        contentDescription = "JSON",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Node Structure",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = { isExpanded = !isExpanded }, modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))

                SelectionContainer {
                    Column {
                        // Opening brace
                        JsonBrace(text = "{", level = 0)

                        // ID field
                        JsonField(
                            key = "id", value = "\"${node.id}\"", level = 1, isLast = false
                        )

                        // Data object
                        JsonField(
                            key = "data",
                            value = "{",
                            level = 1,
                            isLast = node.children.isEmpty(),
                            isObject = true
                        )

                        // Data content
                        JsonField(
                            key = "content",
                            value = "\"${node.data.content.take(50)}${if (node.data.content.length > 50) "..." else ""}\"",
                            level = 2,
                            isLast = false
                        )

                        JsonField(
                            key = "type",
                            value = "\"${node.data.type.name}\"",
                            level = 2,
                            isLast = true
                        )

                        // Closing data brace
                        JsonBrace(text = "}", level = 1, hasComma = node.children.isNotEmpty())

                        // Children array (if exists)
                        if (node.children.isNotEmpty()) {
                            JsonField(
                                key = "children",
                                value = "[",
                                level = 1,
                                isLast = true,
                                isArray = true
                            )

                            node.children.forEachIndexed { index, child ->
                                JsonChildPreview(
                                    child = child,
                                    level = 2,
                                    isLast = index == node.children.size - 1
                                )
                            }

                            JsonBrace(text = "]", level = 1)
                        }

                        // Closing brace
                        JsonBrace(text = "}", level = 0)
                    }
                }
            }
        }
    }
}

@Composable
fun JsonField(
    key: String,
    value: String,
    level: Int,
    isLast: Boolean,
    isObject: Boolean = false,
    isArray: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top
    ) {
        // Indentation
        Spacer(modifier = Modifier.width((level * 16).dp))

        // Key
        Text(
            text = "\"$key\":",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Value
        Text(
            text = value + if (!isLast && !isObject && !isArray) "," else "",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = when {
                value.startsWith("\"") -> MaterialTheme.colorScheme.secondary // Strings
                value in listOf("{", "}", "[", "]") -> MaterialTheme.colorScheme.onSurface // Braces
                else -> MaterialTheme.colorScheme.tertiary // Other values
            },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun JsonBrace(
    text: String, level: Int, hasComma: Boolean = false
) {
    Row {
        Spacer(modifier = Modifier.width((level * 16).dp))
        Text(
            text = text + if (hasComma) "," else "",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun JsonChildPreview(
    child: NeuronNode, level: Int, isLast: Boolean
) {
    Column {
        // Opening brace for child
        JsonBrace(text = "{", level = level)

        // Child ID (preview only)
        JsonField(
            key = "id", value = "\"${child.id}\"", level = level + 1, isLast = false
        )

        // Child type
        JsonField(
            key = "type", value = "\"${child.data.type.name}\"", level = level + 1, isLast = false
        )

        // Children count if any
        if (child.children.isNotEmpty()) {
            JsonField(
                key = "children_count",
                value = "${child.children.size}",
                level = level + 1,
                isLast = true
            )
        } else {
            // Remove comma from type if no children
            JsonField(
                key = "content_length",
                value = "${child.data.content.length}",
                level = level + 1,
                isLast = true
            )
        }

        // Closing brace for child
        JsonBrace(text = "}", level = level, hasComma = !isLast)
    }
}

// Helper functions
fun getNodeDisplayName(node: NeuronNode): String {
    return when {
        node.id == "root" -> "Brain Root"
        node.id == "chatHistory" -> "Chat History"
        node.id == "memoryHistory" -> "Memory"
        node.id == MemoryDataTags.Family.toString().lowercase() -> "Family"
        node.id == MemoryDataTags.Friends.toString().lowercase() -> "Friends"
        node.id == MemoryDataTags.Work.toString().lowercase() -> "Work"

        node.id == MemoryDataTags.Health.toString().lowercase() -> "Health"
        node.id == MemoryDataTags.Entertainment.toString().lowercase() -> "Entertainment"
        node.id == MemoryDataTags.Education.toString().lowercase() -> "Education"

        node.id == MemoryDataTags.Other.toString().lowercase() -> "Other"

        node.data.content.isNotBlank() -> {
            val data = JSONObject(node.data.content).optString("title", "Unknown")
            "Title: $data"
        }

        else -> "Node ${node.id.take(8)}"
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
        NodeType.ROOT -> Color(0xFF4CAF50)      // Green
        NodeType.OPERATOR -> Color(0xFF2196F3)  // Blue
        NodeType.HOLDER -> Color(0xFFFF9800)    // Orange
        NodeType.STEAM -> Color(0xFF9C27B0)     // Purple
        NodeType.LEAF -> Color(0xFF607D8B)      // Blue Grey
    }
}