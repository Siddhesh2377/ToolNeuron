package com.dark.tool_neuron.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.models.table_schema.InstalledRag
import com.dark.tool_neuron.models.table_schema.RagSourceType
import com.dark.tool_neuron.models.table_schema.RagStatus
import com.dark.tool_neuron.ui.theme.rDp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RagOverlayBottomSheet(
    show: Boolean,
    installedRags: List<InstalledRag>,
    loadedRags: List<InstalledRag>,
    installedCount: Int,
    loadedCount: Int,
    onDismiss: () -> Unit,
    onRagSelected: (InstalledRag) -> Unit,
    onRagToggleEnabled: (String, Boolean) -> Unit,
    onRagLoad: (String) -> Unit,
    onRagUnload: (String) -> Unit,
    onRagDelete: (String) -> Unit,
    onOpenRagActivity: () -> Unit,
    onInstallRag: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(0) }

    if (show) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = {
                Box(
                    Modifier
                        .padding(vertical = rDp(12.dp))
                        .width(rDp(40.dp))
                        .height(rDp(4.dp))
                        .clip(RoundedCornerShape(rDp(2.dp)))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = rDp(500.dp))
                    .padding(bottom = rDp(16.dp))
            ) {
                // Header
                RagOverlayHeader(
                    installedCount = installedCount,
                    loadedCount = loadedCount,
                    onOpenRagActivity = onOpenRagActivity,
                    onInstallRag = onInstallRag
                )

                Spacer(modifier = Modifier.height(rDp(8.dp)))

                // Tabs
                SecondaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Loaded ($loadedCount)") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Installed ($installedCount)") }
                    )
                }

                Spacer(modifier = Modifier.height(rDp(8.dp)))

                // Content
                when (selectedTab) {
                    0 -> {
                        if (loadedRags.isEmpty()) {
                            EmptyRagState(
                                message = "No RAGs loaded",
                                suggestion = "Load a RAG from the Installed tab to enable context-aware responses"
                            )
                        } else {
                            RagList(
                                rags = loadedRags,
                                onRagSelected = onRagSelected,
                                onRagToggleEnabled = onRagToggleEnabled,
                                onRagLoad = onRagLoad,
                                onRagUnload = onRagUnload,
                                onRagDelete = onRagDelete,
                                showLoadButton = false
                            )
                        }
                    }
                    1 -> {
                        if (installedRags.isEmpty()) {
                            EmptyRagState(
                                message = "No RAGs installed",
                                suggestion = "Create a new RAG or install one from a file"
                            )
                        } else {
                            RagList(
                                rags = installedRags,
                                onRagSelected = onRagSelected,
                                onRagToggleEnabled = onRagToggleEnabled,
                                onRagLoad = onRagLoad,
                                onRagUnload = onRagUnload,
                                onRagDelete = onRagDelete,
                                showLoadButton = true
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RagOverlayHeader(
    installedCount: Int,
    loadedCount: Int,
    onOpenRagActivity: () -> Unit,
    onInstallRag: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = rDp(16.dp)),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            SectionHeader(title = "RAG Management") {
                Row(horizontalArrangement = Arrangement.spacedBy(rDp(4.dp))) {
                    InfoBadge(
                        text = "$loadedCount active",
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                    InfoBadge(
                        text = "$installedCount installed",
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))) {
            ActionTextButton(
                onClickListener = onOpenRagActivity,
                icon = Icons.Default.Add,
                text = "Create",
                shape = RoundedCornerShape(rDp(12.dp))
            )
        }
    }
}

@Composable
private fun EmptyRagState(
    message: String,
    suggestion: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(rDp(32.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Memory,
            contentDescription = null,
            modifier = Modifier.size(rDp(48.dp)),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(rDp(16.dp)))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(rDp(8.dp)))
        Text(
            text = suggestion,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun RagList(
    rags: List<InstalledRag>,
    onRagSelected: (InstalledRag) -> Unit,
    onRagToggleEnabled: (String, Boolean) -> Unit,
    onRagLoad: (String) -> Unit,
    onRagUnload: (String) -> Unit,
    onRagDelete: (String) -> Unit,
    showLoadButton: Boolean
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = rDp(16.dp), vertical = rDp(8.dp)),
        verticalArrangement = Arrangement.spacedBy(rDp(8.dp))
    ) {
        items(rags, key = { it.id }) { rag ->
            RagListItem(
                rag = rag,
                onRagSelected = onRagSelected,
                onToggleEnabled = { onRagToggleEnabled(rag.id, it) },
                onLoad = { onRagLoad(rag.id) },
                onUnload = { onRagUnload(rag.id) },
                onDelete = { onRagDelete(rag.id) },
                showLoadButton = showLoadButton
            )
        }
    }
}

@Composable
private fun RagListItem(
    rag: InstalledRag,
    onRagSelected: (InstalledRag) -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
    onDelete: () -> Unit,
    showLoadButton: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onRagSelected(rag) },
        colors = CardDefaults.cardColors(
            containerColor = when (rag.status) {
                RagStatus.LOADED -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                RagStatus.LOADING -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                RagStatus.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        shape = RoundedCornerShape(rDp(12.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDp(12.dp))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = getRagSourceIcon(rag.sourceType),
                        contentDescription = null,
                        modifier = Modifier.size(rDp(24.dp)),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(rDp(8.dp)))
                    Column {
                        Text(
                            text = rag.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${rag.nodeCount} nodes | ${rag.getFormattedSize()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Status indicator and controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(rDp(4.dp))
                ) {
                    when (rag.status) {
                        RagStatus.LOADED -> {
                            StatusBadge(
                                text = "Loaded",
                                isActive = true,
                                activeColor = MaterialTheme.colorScheme.primary
                            )
                        }
                        RagStatus.LOADING -> {
                            StatusBadge(
                                text = "Loading",
                                isActive = true,
                                activeColor = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        RagStatus.ERROR -> {
                            StatusBadge(
                                text = "Error",
                                isActive = true,
                                activeColor = MaterialTheme.colorScheme.error
                            )
                        }
                        else -> {}
                    }

                    Switch(
                        checked = rag.isEnabled,
                        onCheckedChange = onToggleEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }

            if (rag.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(rDp(4.dp)))
                Text(
                    text = rag.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (rag.getTagsList().isNotEmpty()) {
                Spacer(modifier = Modifier.height(rDp(4.dp)))
                Row(horizontalArrangement = Arrangement.spacedBy(rDp(4.dp))) {
                    rag.getTagsList().take(3).forEach { tag ->
                        RagTag(tag = tag)
                    }
                    if (rag.getTagsList().size > 3) {
                        Text(
                            text = "+${rag.getTagsList().size - 3}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Action row
            Spacer(modifier = Modifier.height(rDp(8.dp)))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(rDp(8.dp)))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDate(rag.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))) {
                    if (showLoadButton) {
                        when (rag.status) {
                            RagStatus.LOADED -> {
                                ActionTextButton(
                                    onClickListener = onUnload,
                                    icon = Icons.Default.Close,
                                    text = "Unload",
                                    shape = RoundedCornerShape(rDp(12.dp))
                                )
                            }
                            RagStatus.LOADING -> {
                                Box(
                                    modifier = Modifier
                                        .size(rDp(32.dp))
                                        .padding(rDp(4.dp))
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.fillMaxSize(),
                                        strokeWidth = rDp(2.dp)
                                    )
                                }
                            }
                            else -> {
                                ActionTextButton(
                                    onClickListener = onLoad,
                                    icon = Icons.Default.Download,
                                    text = "Load",
                                    shape = RoundedCornerShape(rDp(12.dp))
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(rDp(32.dp))
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            modifier = Modifier.size(rDp(18.dp)),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RagTag(tag: String) {
    InfoBadge(
        text = tag,
        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        contentColor = MaterialTheme.colorScheme.primary
    )
}


private fun getRagSourceIcon(sourceType: RagSourceType) = when (sourceType) {
    RagSourceType.TEXT -> Icons.Default.Book
    RagSourceType.CHAT -> Icons.Default.Memory
    RagSourceType.FILE -> Icons.Default.Storage
    RagSourceType.MEDICAL_TEXT -> Icons.Default.Book
    RagSourceType.NEURON_PACKET -> Icons.Default.Memory
    RagSourceType.MEMORY_VAULT -> Icons.Default.Storage
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}