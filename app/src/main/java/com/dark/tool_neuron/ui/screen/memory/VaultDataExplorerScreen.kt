package com.dark.tool_neuron.ui.screen.memory

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.tool_neuron.R
import com.dark.tool_neuron.global.Standards
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.BodyLabel
import com.dark.tool_neuron.ui.components.CaptionText
import com.dark.tool_neuron.ui.components.SectionDivider
import com.dark.tool_neuron.ui.components.StandardCard
import com.dark.tool_neuron.ui.theme.rDp
import com.dark.tool_neuron.viewmodel.memory.VaultDataExplorerViewModel
import com.memoryvault.core.BlockType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DataFilter(val label: String, val icon: ImageVector, val type: BlockType?) {
    ALL("All", Icons.Outlined.Layers, null), MESSAGES(
        "Messages", Icons.Outlined.ChatBubbleOutline, BlockType.MESSAGE
    ),
    FILES("Files", Icons.Outlined.InsertDriveFile, BlockType.FILE), EMBEDDINGS(
        "Vectors", Icons.Outlined.Hub, BlockType.EMBEDDING
    ),
    CUSTOM("Custom", Icons.Outlined.DataObject, BlockType.CUSTOM_DATA)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultDataExplorerScreen(
    onDrawerOpen: () -> Unit, viewModel: VaultDataExplorerViewModel = viewModel()
) {
    var selectedFilter by remember { mutableStateOf(DataFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var showDetailSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadAllData()
    }

    LaunchedEffect(selectedFilter) {
        viewModel.filterByType(selectedFilter.type)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = {
                Text(
                    "Data Explorer",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }, navigationIcon = {
                ActionButton(onClickListener = {
                    onDrawerOpen()
                }, icon = R.drawable.menu, contentDescription = "Menu")
            }, actions = {
                ActionButton(
                    onClickListener = { viewModel.loadAllData() },
                    icon = Icons.Default.Refresh,
                    contentDescription = "Refresh"
                )
            })
        }) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Filter chips row
            FilterChipsRow(
                selectedFilter = selectedFilter,
                onFilterSelected = { selectedFilter = it },
                counts = viewModel.typeCounts
            )

            // Search bar
            SearchBar(
                query = searchQuery,
                onQueryChange = {
                    searchQuery = it
                    viewModel.search(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rDp(Standards.SpacingLg), vertical = rDp(Standards.SpacingSm))
            )

            // Loading indicator
            AnimatedVisibility(visible = viewModel.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Data list
            if (viewModel.filteredItems.isEmpty() && !viewModel.isLoading) {
                EmptyDataState(filter = selectedFilter)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(rDp(Standards.SpacingLg)),
                    verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingSm))
                ) {
                    items(viewModel.filteredItems, key = { it.blockId.toString() }) { metadata ->
                        DataItemCard(
                            metadata = metadata, onClick = {
                                viewModel.selectItem(metadata)
                                showDetailSheet = true
                            })
                    }
                }
            }
        }

        // Detail bottom sheet
        if (showDetailSheet && viewModel.selectedItem != null) {
            MemoryItemDetailSheet(item = viewModel.selectedItem!!, onDismiss = {
                showDetailSheet = false
                viewModel.clearSelection()
            }, onDelete = {
                viewModel.deleteItem(viewModel.selectedItem!!.blockId.toString())
                showDetailSheet = false
            })
        }
    }
}

@Composable
fun FilterChipsRow(
    selectedFilter: DataFilter, onFilterSelected: (DataFilter) -> Unit, counts: Map<BlockType?, Int>
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = rDp(Standards.SpacingLg)),
        horizontalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingSm))
    ) {
        items(DataFilter.entries) { filter ->
            val count = if (filter == DataFilter.ALL) {
                counts.values.sum()
            } else {
                counts[filter.type] ?: 0
            }

            EnhancedFilterChip(
                filter = filter,
                count = count,
                isSelected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) })
        }
    }
}

@Composable
private fun EnhancedFilterChip(
    filter: DataFilter, count: Int, isSelected: Boolean, onClick: () -> Unit
) {
    val animatedElevation by animateDpAsState(
        targetValue = if (isSelected) rDp(4.dp) else rDp(1.dp), label = "chipElevation"
    )

    val animatedScale by animateFloatAsState(
        targetValue = if (isSelected) 1.02f else 1f, animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow
        ), label = "chipScale"
    )

    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rDp(Standards.ChipSpacing))
            ) {
                Text(
                    text = filter.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                )

                if (count > 0) {
                    CountBadge(
                        count = count, isSelected = isSelected
                    )
                }
            }
        },
        leadingIcon = {
            Icon(
                imageVector = filter.icon,
                contentDescription = null,
                modifier = Modifier.size(rDp(Standards.IconMd))
            )
        },
        shape = RoundedCornerShape(rDp(Standards.SpacingSm)),
        modifier = Modifier
            .scale(animatedScale)
            .animateContentSize(),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        border = if (isSelected) {
            FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = true,
                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                borderWidth = rDp(1.5.dp)
            )
        } else {
            FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = false,
                borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                borderWidth = rDp(1.dp)
            )
        },
        elevation = FilterChipDefaults.filterChipElevation(
            elevation = animatedElevation
        )
    )
}

@Composable
private fun CountBadge(
    count: Int, isSelected: Boolean
) {
    val animatedBackgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }, animationSpec = tween(durationMillis = 200), label = "badgeBackground"
    )

    val animatedTextColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }, animationSpec = tween(durationMillis = 200), label = "badgeText"
    )

    Surface(
        shape = RoundedCornerShape(rDp(Standards.ChipSpacing)),
        color = animatedBackgroundColor,
        modifier = Modifier
            .animateContentSize()
            .padding(vertical = rDp(2.dp))
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = rDp(Standards.ChipSpacing), vertical = rDp(3.dp))
                .defaultMinSize(minWidth = rDp(20.dp)), contentAlignment = Alignment.Center
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = animatedTextColor
            )
        }
    }
}

@Composable
fun SearchBar(
    query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = {
            Text(
                "Search by content, category, tags...",
                style = MaterialTheme.typography.bodySmall
            )
        },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(rDp(Standards.IconMd))
            )
        },
        trailingIcon = {
            AnimatedVisibility(visible = query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Clear",
                        modifier = Modifier.size(rDp(Standards.IconMd))
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(rDp(Standards.CardCornerRadius)),
        textStyle = MaterialTheme.typography.bodyMedium
    )
}

@Composable
fun DataItemCard(
    metadata: com.memoryvault.core.BlockMetadata, onClick: () -> Unit
) {
    val typeInfo = getTypeInfo(metadata.blockType)

    StandardCard(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingMd))
        ) {
            // Header: Icon + Type + Timestamp + Arrow
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingMd)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Type icon with gradient background
                Box(
                    modifier = Modifier
                        .size(rDp(44.dp))
                        .clip(RoundedCornerShape(rDp(Standards.CardCornerRadius)))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    typeInfo.color.copy(alpha = 0.15f),
                                    typeInfo.color.copy(alpha = 0.08f)
                                )
                            )
                        ), contentAlignment = Alignment.Center
                ) {
                    Icon(
                        typeInfo.icon,
                        contentDescription = null,
                        modifier = Modifier.size(rDp(22.dp)),
                        tint = typeInfo.color
                    )
                }

                // Type label and timestamp
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(rDp(2.dp))
                ) {
                    Text(
                        typeInfo.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    CaptionText(
                        text = formatTimestamp(metadata.timestamp)
                    )
                }

                // Arrow indicator
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "View details",
                    modifier = Modifier.size(rDp(Standards.IconMd)),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }

            // Content preview
            metadata.searchableText?.let { text ->
                Text(
                    text = text.take(100) + if (text.length > 100) "..." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Metadata chips row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingSm)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetadataChip(
                    icon = Icons.Outlined.Storage, text = formatSize(metadata.compressedSize)
                )

                metadata.category?.let { cat ->
                    MetadataChip(
                        icon = Icons.Outlined.Folder, text = cat
                    )
                }

                if (metadata.tags.isNotEmpty()) {
                    MetadataChip(
                        icon = Icons.Outlined.LocalOffer,
                        text = "${metadata.tags.size} ${if (metadata.tags.size == 1) "tag" else "tags"}"
                    )
                }
            }
        }
    }
}


@Composable
fun MetadataChip(
    icon: ImageVector, text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingXs))
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(rDp(12.dp)),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun EmptyDataState(filter: DataFilter) {
    Box(
        modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingLg))
        ) {
            Icon(
                filter.icon,
                contentDescription = null,
                modifier = Modifier.size(rDp(64.dp)),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Text(
                "No ${filter.label.lowercase()} found",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Data will appear here once stored in the vault",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryItemDetailSheet(
    item: com.memoryvault.core.BlockMetadata, onDismiss: () -> Unit, onDelete: () -> Unit
) {
    val typeInfo = getTypeInfo(item.blockType)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = rDp(Standards.SpacingXl), topEnd = rDp(Standards.SpacingXl))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = rDp(Standards.SpacingXl))
                .padding(bottom = rDp(32.dp)),
            verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingLg))
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingMd)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(rDp(48.dp))
                            .clip(RoundedCornerShape(rDp(Standards.CardCornerRadius)))
                            .background(typeInfo.color.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            typeInfo.icon,
                            contentDescription = null,
                            modifier = Modifier.size(rDp(Standards.IconLg)),
                            tint = typeInfo.color
                        )
                    }
                    Column {
                        Text(
                            typeInfo.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        CaptionText(
                            text = "ID: ${item.blockId.toString().take(8)}..."
                        )
                    }
                }

                IconButton(
                    onClick = onDelete, colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(rDp(Standards.IconMd))
                    )
                }
            }

            SectionDivider()

            // Details grid
            DetailRow(label = "Created", value = formatTimestampFull(item.timestamp))
            DetailRow(label = "Compressed Size", value = formatSize(item.compressedSize))
            DetailRow(label = "Original Size", value = formatSize(item.uncompressedSize))
            DetailRow(
                label = "Compression",
                value = "${((1 - item.compressedSize.toFloat() / item.uncompressedSize.toFloat()) * 100).toInt()}% saved"
            )
            DetailRow(label = "File Offset", value = "${item.fileOffset} bytes")

            item.category?.let {
                DetailRow(label = "Category", value = it)
            }

            if (item.tags.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingSm))) {
                    Text(
                        "Tags",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingSm))
                    ) {
                        items(item.tags.toList()) { tag ->
                            SuggestionChip(
                                onClick = {}, label = {
                                    Text(
                                        tag,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }, shape = RoundedCornerShape(rDp(Standards.ChipSpacing))
                            )
                        }
                    }
                }
            }

            item.searchableText?.let { text ->
                Column(verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingSm))) {
                    Text(
                        "Content Preview",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(rDp(Standards.SpacingSm)),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(rDp(Standards.SpacingMd)),
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Content hash
            Column(verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingXs))) {
                Text(
                    "Content Hash",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CaptionText(text = item.contentHash)
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

// Helper data class for type styling
data class TypeInfo(
    val label: String, val icon: ImageVector, val color: androidx.compose.ui.graphics.Color
)

@Composable
fun getTypeInfo(type: BlockType): TypeInfo {
    return when (type) {
        BlockType.MESSAGE -> TypeInfo(
            "Message", Icons.Outlined.ChatBubbleOutline, MaterialTheme.colorScheme.primary
        )

        BlockType.FILE -> TypeInfo(
            "File", Icons.Outlined.InsertDriveFile, MaterialTheme.colorScheme.tertiary
        )

        BlockType.EMBEDDING -> TypeInfo(
            "Embedding", Icons.Outlined.Hub, MaterialTheme.colorScheme.secondary
        )

        BlockType.CUSTOM_DATA -> TypeInfo(
            "Custom Data", Icons.Outlined.DataObject, MaterialTheme.colorScheme.error
        )

        BlockType.REFERENCE -> TypeInfo(
            "Reference", Icons.Outlined.Link, MaterialTheme.colorScheme.outline
        )

        BlockType.METADATA -> TypeInfo(
            "Metadata", Icons.Outlined.Info, MaterialTheme.colorScheme.outline
        )
    }
}

// Utility functions
fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        diff < 604800_000 -> "${diff / 86400_000}d ago"
        else -> SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(timestamp))
    }
}

fun formatTimestampFull(timestamp: Long): String {
    return SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
