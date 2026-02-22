package com.dark.tool_neuron.ui.screen.memory

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.tool_neuron.models.messages.Role
import com.dark.tool_neuron.models.vault.ChatInfo
import com.dark.tool_neuron.models.vault.MessageSearchResult
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.ActionToggleGroup
import com.dark.tool_neuron.ui.theme.ManropeFontFamily
import com.dark.tool_neuron.ui.theme.rDp
import com.dark.tool_neuron.ui.theme.rSp
import com.dark.tool_neuron.viewmodel.memory.VaultManagementViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class ManageTab(val label: String) {
    STATS("Stats"),
    CHATS("Chats"),
    TOOLS("Tools")
}

@Composable
fun VaultManagementScreen(
    viewModel: VaultManagementViewModel = viewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = ManageTab.entries

    LaunchedEffect(Unit) {
        viewModel.loadVaultStats()
        viewModel.loadChatList()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Tab selector
        ActionToggleGroup(
            items = tabs.toList(),
            selectedItem = tabs[selectedTab],
            onItemSelected = { selectedTab = tabs.indexOf(it) },
            itemLabel = { it.label },
            modifier = Modifier.padding(horizontal = rDp(16.dp))
        )

        Spacer(Modifier.height(rDp(8.dp)))

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
        when (selectedTab) {
            0 -> VaultStatsOverview(
                stats = viewModel.vaultStats,
                onRefresh = { viewModel.loadVaultStats() }
            )
            1 -> ChatsContent(viewModel)
            2 -> ToolsContent(viewModel)
        }

        // Error Dialog
        if (viewModel.showError) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissError() },
                icon = { Icon(Icons.Outlined.Warning, null) },
                title = {
                    Text(
                        "Error",
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                text = {
                    Text(
                        viewModel.errorMessage,
                        fontFamily = ManropeFontFamily
                    )
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissError() }) {
                        Text("OK", fontFamily = ManropeFontFamily)
                    }
                },
                shape = RoundedCornerShape(rDp(16.dp))
            )
        }
    }
}

@Composable
private fun ChatsContent(viewModel: VaultManagementViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = rDp(16.dp), vertical = rDp(8.dp)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${viewModel.chatList.size} conversations",
                fontFamily = ManropeFontFamily,
                fontSize = rSp(13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ActionButton(
                onClickListener = { viewModel.loadChatList() },
                icon = Icons.Outlined.Refresh,
                contentDescription = "Refresh"
            )
        }

        if (viewModel.chatList.isEmpty() && !viewModel.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.ChatBubbleOutline, null,
                        modifier = Modifier.size(rDp(40.dp)),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Text(
                        "No conversations",
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
                contentPadding = PaddingValues(horizontal = rDp(16.dp)),
                verticalArrangement = Arrangement.spacedBy(rDp(8.dp))
            ) {
                items(viewModel.chatList, key = { it.chatId }) { chat ->
                    ChatCard(
                        chat = chat,
                        isSelected = viewModel.selectedChatId == chat.chatId,
                        onClick = { viewModel.loadChatMessages(chat.chatId) },
                        onDelete = { viewModel.deleteChat(chat.chatId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatCard(
    chat: ChatInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
               else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(rDp(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDp(12.dp)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Chat ${chat.chatId.take(8)}",
                    fontFamily = ManropeFontFamily,
                    fontSize = rSp(14.sp),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "${chat.messageCount} messages · ${formatDate(chat.createdAt)}",
                    fontFamily = ManropeFontFamily,
                    fontSize = rSp(12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            ActionButton(
                onClickListener = onDelete,
                icon = Icons.Outlined.Delete,
                contentDescription = "Delete"
            )
        }
    }
}

@Composable
private fun ToolsContent(viewModel: VaultManagementViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = rDp(16.dp), vertical = rDp(8.dp)),
        verticalArrangement = Arrangement.spacedBy(rDp(8.dp))
    ) {
        item {
            Text(
                "MAINTENANCE",
                fontFamily = ManropeFontFamily,
                fontSize = rSp(11.sp),
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
        }

        item {
            ToolCard(
                title = "Defragment",
                description = "Reclaim unused space and optimize storage",
                icon = Icons.Outlined.CleaningServices,
                isProcessing = viewModel.isDefragging,
                progress = viewModel.defragProgress,
                onClick = { viewModel.performDefragmentation() }
            )
        }

        item {
            ToolCard(
                title = "Create Backup",
                description = "Save encrypted backup to Downloads",
                icon = Icons.Outlined.Backup,
                onClick = {
                    val path = "/storage/emulated/0/Download/vault_backup_${System.currentTimeMillis()}.mvlt.gz"
                    viewModel.createBackup(path)
                }
            )
        }

        item {
            ToolCard(
                title = "Restore Backup",
                description = "Restore from a backup file",
                icon = Icons.Outlined.Restore,
                onClick = { }
            )
        }

        item {
            Spacer(Modifier.height(rDp(8.dp)))
            Text(
                "DANGER ZONE",
                fontFamily = ManropeFontFamily,
                fontSize = rSp(11.sp),
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.error,
                letterSpacing = 1.sp
            )
        }

        item {
            ToolCard(
                title = "Clear All Data",
                description = "Permanently delete everything",
                icon = Icons.Outlined.Delete,
                isDangerous = true,
                onClick = { }
            )
        }
    }
}

@Composable
private fun ToolCard(
    title: String,
    description: String,
    icon: ImageVector,
    isDangerous: Boolean = false,
    isProcessing: Boolean = false,
    progress: Float = 0f,
    onClick: () -> Unit
) {
    val color = if (isDangerous) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Surface(
        onClick = if (!isProcessing) onClick else ({}),
        modifier = Modifier.fillMaxWidth(),
        color = if (isDangerous) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
               else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(rDp(12.dp))
    ) {
        Column(
            modifier = Modifier.padding(rDp(14.dp)),
            verticalArrangement = Arrangement.spacedBy(rDp(8.dp))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(rDp(12.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon, null,
                    modifier = Modifier.size(rDp(22.dp)),
                    tint = color
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        fontFamily = ManropeFontFamily,
                        fontSize = rSp(14.sp),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        description,
                        fontFamily = ManropeFontFamily,
                        fontSize = rSp(12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isProcessing) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@SuppressLint("DefaultLocale")
fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
