package com.dark.tool_neuron.ui.screen.memory

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.theme.ManropeFontFamily
import com.dark.tool_neuron.ui.theme.rDp
import com.dark.tool_neuron.ui.theme.rSp
import com.dark.tool_neuron.viewmodel.memory.VaultDataExplorerViewModel
import com.memoryvault.core.BlockType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DataFilter(val label: String, val icon: ImageVector, val type: BlockType?) {
    ALL("All", Icons.Outlined.Layers, null),
    MESSAGES("Messages", Icons.Outlined.ChatBubbleOutline, BlockType.MESSAGE),
    FILES("Files", Icons.AutoMirrored.Outlined.InsertDriveFile, BlockType.FILE),
    EMBEDDINGS("Vectors", Icons.Outlined.Hub, BlockType.EMBEDDING),
    CUSTOM("Custom", Icons.Outlined.DataObject, BlockType.CUSTOM_DATA)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultDataExplorerScreen(
    onDrawerOpen: () -> Unit,
    viewModel: VaultDataExplorerViewModel = viewModel()
) {
    var selectedFilter by remember { mutableStateOf(DataFilter.ALL) }
    var showDetailSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadAllData()
    }

    LaunchedEffect(selectedFilter) {
        viewModel.filterByType(selectedFilter.type)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = rDp(16.dp)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${viewModel.filteredItems.size} items",
                fontFamily = ManropeFontFamily,
                fontSize = rSp(13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ActionButton(
                onClickListener = { viewModel.loadAllData() },
                icon = Icons.Outlined.Refresh,
                contentDescription = "Refresh"
            )
        }

        // Filter chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = rDp(16.dp), vertical = rDp(8.dp)),
            horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
        ) {
            items(DataFilter.entries) { filter ->
                val count = if (filter == DataFilter.ALL) viewModel.typeCounts.values.sum()
                           else viewModel.typeCounts[filter.type] ?: 0

                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = {
                        Text(
                            "${filter.label} $count",
                            fontFamily = ManropeFontFamily,
                            fontSize = rSp(12.sp)
                        )
                    },
                    leadingIcon = {
                        Icon(filter.icon, null, Modifier.size(rDp(16.dp)))
                    },
                    shape = RoundedCornerShape(rDp(8.dp)),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                    ),
                    border = null
                )
            }
        }

        // Loading
        AnimatedVisibility(
            visible = viewModel.isLoading,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rDp(16.dp))
            )
        }

        // Content
        if (viewModel.filteredItems.isEmpty() && !viewModel.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        selectedFilter.icon, null,
                        modifier = Modifier.size(rDp(40.dp)),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Text(
                        "No ${selectedFilter.label.lowercase()}",
                        fontFamily = ManropeFontFamily,
                        fontSize = rSp(14.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = rDp(8.dp))
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = rDp(16.dp), vertical = rDp(8.dp)),
                verticalArrangement = Arrangement.spacedBy(rDp(8.dp))
            ) {
                items(viewModel.filteredItems, key = { it.blockId.toString() }) { metadata ->
                    DataCard(
                        metadata = metadata,
                        onClick = {
                            viewModel.selectItem(metadata)
                            showDetailSheet = true
                        }
                    )
                }
            }
        }
    }

    // Detail sheet
    if (showDetailSheet && viewModel.selectedItem != null) {
        DetailSheet(
            item = viewModel.selectedItem!!,
            onDismiss = {
                showDetailSheet = false
                viewModel.clearSelection()
            },
            onDelete = {
                viewModel.deleteItem(viewModel.selectedItem!!.blockId.toString())
                showDetailSheet = false
            }
        )
    }
}

@Composable
private fun DataCard(
    metadata: com.memoryvault.core.BlockMetadata,
    onClick: () -> Unit
) {
    val typeInfo = getTypeInfo(metadata.blockType)

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(rDp(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDp(12.dp)),
            horizontalArrangement = Arrangement.spacedBy(rDp(12.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Type icon
            Box(
                modifier = Modifier
                    .size(rDp(36.dp))
                    .clip(RoundedCornerShape(rDp(10.dp)))
                    .background(typeInfo.color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    typeInfo.icon, null,
                    modifier = Modifier.size(rDp(18.dp)),
                    tint = typeInfo.color
                )
            }

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    typeInfo.label,
                    fontFamily = ManropeFontFamily,
                    fontSize = rSp(14.sp),
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(rDp(12.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MetaLabel(Icons.Outlined.Storage, formatSize(metadata.compressedSize))
                    metadata.category?.let {
                        MetaLabel(Icons.Outlined.Folder, it)
                    }
                }
            }

            Icon(
                Icons.Outlined.ChevronRight, null,
                modifier = Modifier.size(rDp(20.dp)),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun MetaLabel(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(rDp(4.dp))
    ) {
        Icon(
            icon, null,
            modifier = Modifier.size(rDp(12.dp)),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Text(
            text,
            fontFamily = ManropeFontFamily,
            fontSize = rSp(11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailSheet(
    item: com.memoryvault.core.BlockMetadata,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    val typeInfo = getTypeInfo(item.blockType)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = rDp(20.dp), topEnd = rDp(20.dp)),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = rDp(20.dp))
                .padding(bottom = rDp(32.dp)),
            verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(rDp(12.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(rDp(40.dp))
                            .clip(RoundedCornerShape(rDp(10.dp)))
                            .background(typeInfo.color.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(typeInfo.icon, null, Modifier.size(rDp(20.dp)), tint = typeInfo.color)
                    }
                    Column {
                        Text(
                            typeInfo.label,
                            fontFamily = ManropeFontFamily,
                            fontSize = rSp(16.sp),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            item.blockId.toString().take(12) + "...",
                            fontFamily = ManropeFontFamily,
                            fontSize = rSp(11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                ActionButton(
                    onClickListener = onDelete,
                    icon = Icons.Outlined.Delete,
                    contentDescription = "Delete"
                )
            }

            // Divider
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            ) {
                Box(Modifier.size(1.dp))
            }

            // Details
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DetailItem("Created", formatTimestampFull(item.timestamp))
                DetailItem("Size", formatSize(item.compressedSize))
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DetailItem("Original", formatSize(item.uncompressedSize))
                val savings = ((1 - item.compressedSize.toFloat() / item.uncompressedSize.toFloat()) * 100).toInt()
                DetailItem("Savings", "$savings%")
            }

            item.category?.let { DetailItem("Category", it) }

            // Tags
            if (item.tags.isNotEmpty()) {
                Column {
                    Text(
                        "Tags",
                        fontFamily = ManropeFontFamily,
                        fontSize = rSp(11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(rDp(6.dp))) {
                        items(item.tags.toList()) { tag ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(rDp(6.dp))
                            ) {
                                Text(
                                    tag,
                                    fontFamily = ManropeFontFamily,
                                    fontSize = rSp(11.sp),
                                    modifier = Modifier.padding(horizontal = rDp(8.dp), vertical = rDp(4.dp))
                                )
                            }
                        }
                    }
                }
            }

            // Preview
            item.searchableText?.let { text ->
                Column {
                    Text(
                        "Preview",
                        fontFamily = ManropeFontFamily,
                        fontSize = rSp(11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(rDp(10.dp))
                    ) {
                        Text(
                            text,
                            fontFamily = ManropeFontFamily,
                            fontSize = rSp(12.sp),
                            modifier = Modifier.padding(rDp(12.dp)),
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailItem(label: String, value: String) {
    Column {
        Text(
            label,
            fontFamily = ManropeFontFamily,
            fontSize = rSp(11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            fontFamily = ManropeFontFamily,
            fontSize = rSp(13.sp),
            fontWeight = FontWeight.Medium
        )
    }
}

data class TypeInfo(
    val label: String,
    val icon: ImageVector,
    val color: Color
)

@Composable
fun getTypeInfo(type: BlockType): TypeInfo {
    return when (type) {
        BlockType.MESSAGE -> TypeInfo("Message", Icons.Outlined.ChatBubbleOutline, MaterialTheme.colorScheme.primary)
        BlockType.FILE -> TypeInfo("File", Icons.AutoMirrored.Outlined.InsertDriveFile, MaterialTheme.colorScheme.tertiary)
        BlockType.EMBEDDING -> TypeInfo("Embedding", Icons.Outlined.Hub, MaterialTheme.colorScheme.secondary)
        BlockType.CUSTOM_DATA -> TypeInfo("Custom", Icons.Outlined.DataObject, MaterialTheme.colorScheme.error)
        BlockType.REFERENCE -> TypeInfo("Reference", Icons.Outlined.Link, MaterialTheme.colorScheme.outline)
        BlockType.METADATA -> TypeInfo("Metadata", Icons.Outlined.Info, MaterialTheme.colorScheme.outline)
    }
}

fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "Now"
        diff < 3600_000 -> "${diff / 60_000}m"
        diff < 86400_000 -> "${diff / 3600_000}h"
        else -> SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(timestamp))
    }
}

fun formatTimestampFull(timestamp: Long): String {
    return SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(timestamp))
}

fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = rDp(40.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon, null,
                modifier = Modifier.size(rDp(40.dp)),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Text(
                message,
                fontFamily = ManropeFontFamily,
                fontSize = rSp(14.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = rDp(8.dp))
            )
        }
    }
}
