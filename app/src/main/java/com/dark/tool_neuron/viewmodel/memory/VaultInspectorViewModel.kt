package com.dark.tool_neuron.viewmodel.memory

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.tool_neuron.R
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.screen.memory.EmptyStateCard
import com.dark.tool_neuron.ui.screen.memory.VaultLogger
import com.dark.tool_neuron.ui.screen.memory.formatBytes
import com.dark.tool_neuron.ui.screen.memory.formatDate
import com.dark.tool_neuron.ui.theme.rDp
import com.dark.tool_neuron.ui.theme.rSp
import com.dark.tool_neuron.vault.VaultHelper
import com.memoryvault.core.BlockMetadata
import com.memoryvault.core.BlockType
import kotlinx.coroutines.launch

class VaultInspectorViewModel : ViewModel() {
    var blockMetadata by mutableStateOf<List<BlockMetadata>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var selectedBlock by mutableStateOf<BlockMetadata?>(null)
        private set

    private var _filterType by mutableStateOf<BlockType?>(null)
    val filterType: BlockType? get() = _filterType

    private var _sortBy by mutableStateOf(SortOption.TIMESTAMP_DESC)
    val sortBy: SortOption get() = _sortBy

    private var _searchQuery by mutableStateOf("")
    val searchQuery: String get() = _searchQuery

    var statusMessage by mutableStateOf("")
        private set

    var showError by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf("")
        private set

    init {
        loadBlockMetadata()
    }

    fun loadBlockMetadata() {
        viewModelScope.launch {
            try {
                isLoading = true
                blockMetadata = VaultHelper.getVault().getAllMetadata()
                statusMessage = "Loaded ${blockMetadata.size} blocks"
                VaultLogger.info("Inspector", "Loaded ${blockMetadata.size} block metadata entries")
            } catch (e: Exception) {
                showError = true
                errorMessage = e.message ?: "Failed to load block metadata"
                VaultLogger.error("Inspector", "Failed to load metadata", e)
            } finally {
                isLoading = false
            }
        }
    }

    fun selectBlock(block: BlockMetadata?) {
        selectedBlock = block
        if (block != null) {
            VaultLogger.debug("Inspector", "Selected block: ${block.blockId}")
        }
    }

    fun setFilterType(type: BlockType?) {
        _filterType = type
        VaultLogger.debug("Inspector", "Filter changed to: ${type?.name ?: "ALL"}")
    }

    fun setSortOption(option: SortOption) {
        _sortBy = option
        VaultLogger.debug("Inspector", "Sort changed to: ${option.name}")
    }

    fun setSearchQuery(query: String) {
        _searchQuery = query
    }

    fun getFilteredAndSortedBlocks(): List<BlockMetadata> {
        var filtered = blockMetadata

        if (_filterType != null) {
            filtered = filtered.filter { it.blockType == _filterType }
        }

        if (_searchQuery.isNotBlank()) {
            filtered = filtered.filter {
                it.blockId.toString()
                    .contains(_searchQuery, ignoreCase = true) || it.category?.contains(
                    _searchQuery,
                    ignoreCase = true
                ) == true || it.tags.any { tag ->
                    tag.contains(
                        _searchQuery,
                        ignoreCase = true
                    )
                } || it.searchableText?.contains(_searchQuery, ignoreCase = true) == true
            }
        }

        filtered = when (_sortBy) {
            SortOption.TIMESTAMP_ASC -> filtered.sortedBy { it.timestamp }
            SortOption.TIMESTAMP_DESC -> filtered.sortedByDescending { it.timestamp }
            SortOption.SIZE_ASC -> filtered.sortedBy { it.compressedSize }
            SortOption.SIZE_DESC -> filtered.sortedByDescending { it.compressedSize }
            SortOption.TYPE -> filtered.sortedBy { it.blockType.name }
        }

        return filtered
    }

    fun dismissError() {
        showError = false
        errorMessage = ""
    }
}

enum class SortOption(val displayName: String) {
    TIMESTAMP_DESC("Newest First"),
    TIMESTAMP_ASC("Oldest First"),
    SIZE_DESC("Largest First"),
    SIZE_ASC("Smallest First"),
    TYPE("By Type")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultInspectorScreen(
    viewModel: VaultInspectorViewModel = viewModel()
) {
    var showSortMenu by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val filteredBlocks = remember(
        viewModel.blockMetadata, viewModel.filterType, viewModel.sortBy, viewModel.searchQuery
    ) {
        viewModel.getFilteredAndSortedBlocks()
    }

    val showScrollToTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 3
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Vault Inspector",
                            fontSize = rSp(20.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "${filteredBlocks.size} blocks",
                            fontSize = rSp(12.sp),
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // Sort Button
                    Box {
                        ActionButton(
                            onClickListener = { showSortMenu = true },
                            icon = Icons.AutoMirrored.Outlined.Sort,
                            contentDescription = "Sort",
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (showSortMenu) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (showSortMenu) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            shape = RoundedCornerShape(rDp(12.dp))
                        ) {
                            SortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            option.displayName,
                                            fontSize = rSp(14.sp),
                                            fontWeight = if (viewModel.sortBy == option) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        viewModel.setSortOption(option)
                                        showSortMenu = false
                                    },
                                    leadingIcon = {
                                        if (viewModel.sortBy == option) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(rDp(4.dp)))

                    // Filter Button
                    Box {
                        ActionButton(
                            onClickListener = { showFilterMenu = true },
                            icon = Icons.Outlined.FilterList,
                            contentDescription = "Filter",
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (viewModel.filterType != null || showFilterMenu)
                                    MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (viewModel.filterType != null || showFilterMenu)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )

                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false },
                            shape = RoundedCornerShape(rDp(12.dp))
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "All Types",
                                        fontSize = rSp(14.sp),
                                        fontWeight = if (viewModel.filterType == null) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    viewModel.setFilterType(null)
                                    showFilterMenu = false
                                },
                                leadingIcon = {
                                    if (viewModel.filterType == null) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = rDp(4.dp)))

                            BlockType.entries.forEach { type ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            type.name,
                                            fontSize = rSp(14.sp),
                                            fontWeight = if (viewModel.filterType == type) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        viewModel.setFilterType(type)
                                        showFilterMenu = false
                                    },
                                    leadingIcon = {
                                        if (viewModel.filterType == type) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(rDp(4.dp)))

                    // Refresh Button
                    ActionButton(
                        onClickListener = { viewModel.loadBlockMetadata() },
                        icon = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )

                    Spacer(Modifier.width(rDp(8.dp)))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Search Bar
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = if (viewModel.searchQuery.isNotEmpty()) rDp(2.dp) else rDp(0.dp)
                ) {
                    Column {
                        OutlinedTextField(
                            value = viewModel.searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = rDp(16.dp), vertical = rDp(12.dp)),
                            placeholder = {
                                Text(
                                    "Search blocks by ID, category, tags...",
                                    fontSize = rSp(14.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingIcon = {
                                if (viewModel.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(rDp(28.dp)),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )

                        // Active Filters Display
                        AnimatedVisibility(
                            visible = viewModel.filterType != null || viewModel.searchQuery.isNotEmpty(),
                            enter = fadeIn() + slideInVertically(),
                            exit = fadeOut() + slideOutVertically()
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = rDp(16.dp), vertical = rDp(10.dp)),
                                    horizontalArrangement = Arrangement.spacedBy(rDp(8.dp)),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        modifier = Modifier.size(rDp(18.dp)),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )

                                    Text(
                                        buildString {
                                            append("Active filters: ")
                                            if (viewModel.filterType != null) {
                                                append(viewModel.filterType!!.name)
                                            }
                                            if (viewModel.searchQuery.isNotEmpty()) {
                                                if (viewModel.filterType != null) append(" • ")
                                                append("\"${viewModel.searchQuery}\"")
                                            }
                                        },
                                        fontSize = rSp(13.sp),
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }

                        // Loading Indicator
                        if (viewModel.isLoading) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Block List
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(rDp(16.dp)),
                    verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
                ) {
                    if (filteredBlocks.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Default.Face,
                                message = if (viewModel.searchQuery.isNotEmpty() || viewModel.filterType != null)
                                    "No blocks match your filters"
                                else "No blocks in vault"
                            )
                        }
                    } else {
                        items(
                            items = filteredBlocks,
                            key = { it.blockId.toString() }
                        ) { block ->
                            BlockMetadataCard(
                                metadata = block,
                                isSelected = viewModel.selectedBlock?.blockId == block.blockId,
                                onClick = { viewModel.selectBlock(block) }
                            )
                        }
                    }

                    // Bottom spacer
                    item {
                        Spacer(modifier = Modifier.height(rDp(16.dp)))
                    }
                }
            }

            // Scroll to top button
            AnimatedVisibility(
                visible = showScrollToTop,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(rDp(16.dp)),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                androidx.compose.material3.FloatingActionButton(
                    onClick = {
                        kotlinx.coroutines.MainScope().launch {
                            listState.animateScrollToItem(0)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.Face,
                        contentDescription = "Scroll to top"
                    )
                }
            }
        }

        // Block Detail Sheet
        viewModel.selectedBlock?.let { block ->
            BlockDetailSheet(
                metadata = block,
                onDismiss = { viewModel.selectBlock(null) }
            )
        }

        // Error Dialog
        if (viewModel.showError) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissError() },
                icon = {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                title = {
                    Text(
                        "Error",
                        fontSize = rSp(20.sp),
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        viewModel.errorMessage,
                        fontSize = rSp(14.sp)
                    )
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissError() }) {
                        Text("OK", fontWeight = FontWeight.SemiBold)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(rDp(24.dp))
            )
        }
    }
}

@Composable
fun BlockMetadataCard(
    metadata: BlockMetadata,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 0.98f else 1f,
        label = "scale"
    )

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(rDp(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) rDp(4.dp) else rDp(0.dp)
        )
    ) {
        Column(
            modifier = Modifier.padding(rDp(16.dp)),
            verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(rDp(12.dp)),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(rDp(44.dp))
                            .clip(RoundedCornerShape(rDp(12.dp)))
                            .background(getBlockTypeColor(metadata.blockType)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            getBlockTypeIcon(metadata.blockType),
                            contentDescription = null,
                            modifier = Modifier.size(rDp(22.dp)),
                            tint = Color.White
                        )
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(rDp(2.dp))
                    ) {
                        Text(
                            metadata.blockType.name,
                            fontSize = rSp(15.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            metadata.blockId.toString().take(16) + "...",
                            fontSize = rSp(11.sp),
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(rDp(2.dp))
                ) {
                    Text(
                        formatBytes(metadata.compressedSize),
                        fontSize = rSp(13.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        formatDate(metadata.timestamp),
                        fontSize = rSp(11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Tags and Category
            if (metadata.category != null || metadata.tags.isNotEmpty()) {
                Column (
                    verticalArrangement = Arrangement.spacedBy(rDp(6.dp)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    metadata.category?.let {
                        SuggestionChip(
                            onClick = { },
                            label = {
                                Text(
                                    it,
                                    fontSize = rSp(11.sp),
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            icon = {
                                Icon(
                                    Icons.Default.Face,
                                    contentDescription = null,
                                    modifier = Modifier.size(rDp(14.dp))
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            shape = RoundedCornerShape(rDp(8.dp))
                        )
                    }

                    metadata.tags.take(2).forEach { tag ->
                        SuggestionChip(
                            onClick = { },
                            label = {
                                Text(
                                    tag,
                                    fontSize = rSp(11.sp),
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                labelColor = MaterialTheme.colorScheme.onTertiaryContainer
                            ),
                            shape = RoundedCornerShape(rDp(8.dp))
                        )
                    }

                    if (metadata.tags.size > 2) {
                        SuggestionChip(
                            onClick = { },
                            label = {
                                Text(
                                    "+${metadata.tags.size - 2}",
                                    fontSize = rSp(11.sp),
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(rDp(8.dp))
                        )
                    }
                }
            }

            // Compression Info
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Column(
                verticalArrangement = Arrangement.spacedBy(rDp(6.dp))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Compressed",
                        fontSize = rSp(12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        formatBytes(metadata.compressedSize),
                        fontSize = rSp(12.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Uncompressed",
                        fontSize = rSp(12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        formatBytes(metadata.uncompressedSize),
                        fontSize = rSp(12.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                val compressionRatio = if (metadata.uncompressedSize > 0) {
                    (metadata.compressedSize.toFloat() / metadata.uncompressedSize.toFloat() * 100).toInt()
                } else 100

                Spacer(modifier = Modifier.height(rDp(4.dp)))

                LinearProgressIndicator(
                    progress = { compressionRatio / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rDp(8.dp))
                        .clip(RoundedCornerShape(rDp(4.dp))),
                    color = when {
                        compressionRatio < 30 -> Color(0xFF4CAF50)
                        compressionRatio < 60 -> Color(0xFFFFC107)
                        else -> Color(0xFFFF5722)
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )

                Text(
                    "Compression ratio: $compressionRatio%",
                    fontSize = rSp(11.sp),
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockDetailSheet(
    metadata: BlockMetadata,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = rDp(24.dp), topEnd = rDp(24.dp)),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = rDp(20.dp)),
            verticalArrangement = Arrangement.spacedBy(rDp(16.dp))
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Block Details",
                        fontSize = rSp(24.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            item {
                DetailRow(
                    label = "Block ID",
                    value = metadata.blockId.toString(),
                    onCopy = {
                        val clip = ClipData.newPlainText("Block ID", metadata.blockId.toString())
                        clipboardManager.setPrimaryClip(clip)
                        Toast.makeText(context, "Block ID copied", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            item {
                DetailRow(label = "Type", value = metadata.blockType.name)
            }

            item {
                DetailRow(label = "File Offset", value = "${metadata.fileOffset} bytes")
            }

            item {
                DetailRow(label = "Compressed Size", value = formatBytes(metadata.compressedSize))
            }

            item {
                DetailRow(label = "Uncompressed Size", value = formatBytes(metadata.uncompressedSize))
            }

            item {
                DetailRow(label = "Timestamp", value = formatDate(metadata.timestamp))
            }

            if (metadata.category != null) {
                item {
                    DetailRow(label = "Category", value = metadata.category!!)
                }
            }

            if (metadata.tags.isNotEmpty()) {
                item {
                    DetailRow(label = "Tags", value = metadata.tags.joinToString(", "))
                }
            }

            item {
                DetailRow(
                    label = "Content Hash",
                    value = metadata.contentHash,
                    onCopy = {
                        val clip = ClipData.newPlainText("Content Hash", metadata.contentHash)
                        clipboardManager.setPrimaryClip(clip)
                        Toast.makeText(context, "Content hash copied", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            if (metadata.searchableText != null) {
                item {
                    DetailRow(
                        label = "Searchable Text",
                        value = metadata.searchableText!!,
                        maxLines = 4
                    )
                }
            }

            item {
                Spacer(Modifier.height(rDp(32.dp)))
            }
        }
    }
}

@Composable
fun DetailRow(
    label: String,
    value: String,
    maxLines: Int = 1,
    onCopy: (() -> Unit)? = null
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(rDp(6.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                fontSize = rSp(13.sp),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            if (onCopy != null) {
                ActionButton(
                    onClickListener = onCopy,
                    icon = R.drawable.copy,
                    contentDescription = "Copy",
                    shape = RoundedCornerShape(rDp(8.dp)),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(0.08f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(rDp(12.dp)),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                value,
                fontSize = rSp(13.sp),
                fontFamily = if (maxLines == 1) FontFamily.Monospace else FontFamily.Default,
                modifier = Modifier.padding(rDp(14.dp)),
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun getBlockTypeIcon(type: BlockType): ImageVector {
    return when (type) {
        BlockType.MESSAGE -> Icons.Default.Email
        BlockType.FILE -> Icons.Default.Face
        BlockType.CUSTOM_DATA -> Icons.Default.Star
        BlockType.EMBEDDING -> Icons.Default.Face
        BlockType.REFERENCE -> Icons.Default.Face
        BlockType.METADATA -> Icons.Default.Info
    }
}

fun getBlockTypeColor(type: BlockType): Color {
    return when (type) {
        BlockType.MESSAGE -> Color(0xFF2196F3)
        BlockType.FILE -> Color(0xFF4CAF50)
        BlockType.CUSTOM_DATA -> Color(0xFFFF9800)
        BlockType.EMBEDDING -> Color(0xFF9C27B0)
        BlockType.REFERENCE -> Color(0xFF00BCD4)
        BlockType.METADATA -> Color(0xFF607D8B)
    }
}