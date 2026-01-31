package com.dark.tool_neuron.ui.screen.memory

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.tool_neuron.global.Standards
import com.dark.tool_neuron.models.messages.Role
import com.dark.tool_neuron.models.vault.ChatInfo
import com.dark.tool_neuron.models.vault.MessageSearchResult
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.ActionTextButton
import com.dark.tool_neuron.ui.components.ActionToggleButton
import com.dark.tool_neuron.ui.components.SectionHeader
import com.dark.tool_neuron.ui.components.StandardCard
import com.dark.tool_neuron.ui.theme.rDp
import com.dark.tool_neuron.viewmodel.memory.VaultManagementViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultManagementScreen(
    viewModel: VaultManagementViewModel = viewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Overview", "Chats", "Search", "Maintenance")

    LaunchedEffect(Unit) {
        viewModel.loadVaultStats()
        viewModel.loadChatList()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = {
                Text(
                    "Memory Vault Manager",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }, actions = {
                ActionToggleButton(
                    checked = viewModel.autoRefresh,
                    onCheckedChange = { viewModel.toggleAutoRefresh() },
                    icon = Icons.Default.Refresh,
                    contentDescription = "Auto Refresh"
                )

                Spacer(Modifier.width(rDp(Standards.SpacingSm)))

                ActionButton(
                    onClickListener = { viewModel.loadVaultStats() },
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
            // Tab Row
            SecondaryTabRow(
                selectedTabIndex = selectedTab, modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = {
                        Text(
                            title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                        )
                    })
                }
            }

            // Status Bar
            AnimatedVisibility(
                visible = viewModel.statusMessage.isNotEmpty(),
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(rDp(Standards.SpacingMd)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(rDp(Standards.IconMd)),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.width(rDp(Standards.SpacingSm)))
                        Text(
                            viewModel.statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Loading Indicator
            if (viewModel.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Content
            when (selectedTab) {
                0 -> OverviewTab(viewModel)
                1 -> ChatsTab(viewModel)
                2 -> SearchTab(viewModel)
                3 -> MaintenanceTab(viewModel)
            }
        }

        // Error Dialog
        if (viewModel.showError) {
            AlertDialog(onDismissRequest = { viewModel.dismissError() }, icon = {
                Icon(Icons.Default.Warning, contentDescription = null)
            }, title = {
                Text(
                    "Error",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }, text = {
                Text(
                    viewModel.errorMessage,
                    style = MaterialTheme.typography.bodyMedium
                )
            }, confirmButton = {
                TextButton(onClick = { viewModel.dismissError() }) {
                    Text("OK")
                }
            })
        }
    }
}

@Composable
fun OverviewTab(viewModel: VaultManagementViewModel) {
    VaultStatsOverview(
        stats = viewModel.vaultStats,
        onRefresh = { viewModel.loadVaultStats() }
    )
}

@Composable
fun ChatsTab(viewModel: VaultManagementViewModel) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Chat List Header
        Surface(
            modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(rDp(Standards.SpacingMd)),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Chats (${viewModel.chatList.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                ActionButton(
                    onClickListener = { viewModel.loadChatList() },
                    icon = Icons.Default.Refresh,
                    contentDescription = "Refresh Chats"
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(rDp(Standards.SpacingLg)),
            verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingSm))
        ) {
            items(viewModel.chatList) { chat ->
                ChatCard(
                    chatInfo = chat,
                    isSelected = viewModel.selectedChatId == chat.chatId,
                    onClick = { viewModel.loadChatMessages(chat.chatId) },
                    onDelete = { viewModel.deleteChat(chat.chatId) })
            }

            if (viewModel.chatList.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.Email, message = "No chats found"
                    )
                }
            }
        }
    }
}

@Composable
fun SearchTab(viewModel: VaultManagementViewModel) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Search Bar
        Surface(
            modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(rDp(Standards.SpacingMd)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingSm))
            ) {
                OutlinedTextField(
                    value = viewModel.searchQuery,
                    onValueChange = { viewModel.performSearch(it) },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            "Search messages...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (viewModel.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.performSearch("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(rDp(Standards.CardCornerRadius))
                )
            }
        }

        // Search Results
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(rDp(Standards.SpacingLg)),
            verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingSm))
        ) {
            if (viewModel.searchQuery.isNotEmpty()) {
                item {
                    Text(
                        "Results (${viewModel.searchResults.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                items(viewModel.searchResults) { result ->
                    SearchResultCard(result)
                }

                if (viewModel.searchResults.isEmpty() && !viewModel.isLoading) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Default.Search,
                            message = "No results found for \"${viewModel.searchQuery}\""
                        )
                    }
                }
            } else {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.Search,
                        message = "Enter a search query to find messages"
                    )
                }
            }
        }
    }
}

@Composable
fun MaintenanceTab(viewModel: VaultManagementViewModel) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(rDp(Standards.SpacingLg)),
        verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingMd))
    ) {
        item {
            SectionHeader(title = "Maintenance Operations")
        }

        item {
            MaintenanceCard(
                title = "Defragmentation",
                description = "Reclaim wasted space and optimize storage",
                icon = Icons.Default.Build,
                buttonText = "Defragment",
                isProcessing = viewModel.isDefragging,
                progress = viewModel.defragProgress,
                onClick = { viewModel.performDefragmentation() })
        }

        item {
            MaintenanceCard(
                title = "Create Backup",
                description = "Create a compressed backup of the vault",
                icon = Icons.Default.Face,
                buttonText = "Backup",
                onClick = {
                    val path =
                        "/storage/emulated/0/Download/vault_backup_${System.currentTimeMillis()}.mvlt.gz"
                    viewModel.createBackup(path)
                })
        }

        item {
            MaintenanceCard(
                title = "Restore Backup",
                description = "Restore vault from a backup file",
                icon = Icons.Default.Build,
                buttonText = "Restore",
                onClick = {
                    // TODO: Implement file picker
                })
        }

        item {
            MaintenanceCard(
                title = "Clear Vault",
                description = "Delete all data from the vault (irreversible)",
                icon = Icons.Default.Delete,
                buttonText = "Clear All",
                buttonColor = MaterialTheme.colorScheme.error,
                onClick = {
                    // TODO: Add confirmation dialog
                })
        }

        item {
            StandardCard(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                icon = Icons.Default.Warning,
                iconTint = MaterialTheme.colorScheme.onErrorContainer,
                title = "Warning",
                description = "Maintenance operations may take time and temporarily block other operations. " +
                        "Always create a backup before performing destructive operations."
            )
        }
    }
}

@Composable
fun ChatCard(
    chatInfo: ChatInfo, isSelected: Boolean, onClick: () -> Unit, onDelete: () -> Unit
) {
    StandardCard(
        onClick = onClick,
        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingXs))
            ) {
                Text(
                    "Chat ID: ${chatInfo.chatId.take(8)}...",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${chatInfo.messageCount} messages",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Created: ${formatDate(chatInfo.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            ActionButton(
                onClickListener = onDelete,
                icon = Icons.Default.Delete,
                contentDescription = "Delete Chat",
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            )
        }
    }
}

@Composable
fun SearchResultCard(result: MessageSearchResult) {
    StandardCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingSm))
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingSm)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (result.message.role == Role.User) Icons.Default.Person else Icons.Default.Face,
                    contentDescription = null,
                    modifier = Modifier.size(rDp(Standards.IconMd))
                )
                Text(
                    result.message.role.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                Text(
                    formatDate(result.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                result.message.content.content.take(150) + if (result.message.content.content.length > 150) "..." else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                "Chat: ${result.chatId.take(8)}...",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MaintenanceCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    buttonText: String,
    buttonColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    isProcessing: Boolean = false,
    progress: Float = 0f,
    onClick: () -> Unit
) {
    StandardCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingMd))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingMd))
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(rDp(32.dp)),
                    tint = MaterialTheme.colorScheme.primary
                )

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isProcessing) {
                Column(verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingXs))) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "${(progress * 100).toInt()}% Complete",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                FilledTonalButton(
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = buttonColor.copy(alpha = 0.12f), contentColor = buttonColor
                    )
                ) {
                    Text(buttonText, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
fun EmptyStateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector, message: String
) {
    StandardCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDp(Standards.SpacingXl)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingMd))
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(rDp(48.dp)),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

// Utility Functions
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
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
