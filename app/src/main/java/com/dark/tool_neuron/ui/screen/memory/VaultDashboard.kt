package com.dark.tool_neuron.ui.screen.memory

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.tool_neuron.models.vault.ChatInfo
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.global.formatBytes
import com.dark.tool_neuron.global.formatCompactDate
import com.dark.tool_neuron.viewmodel.memory.VaultManagementViewModel
import com.dark.tool_neuron.ui.components.ExpandCollapseIcon
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.global.Standards

@Composable
fun VaultDashboard(onNavigateBack: () -> Unit) {
    val viewModel: VaultManagementViewModel = viewModel()
    var showLogs by remember { mutableStateOf(false) }

    val dedupedChats = remember(viewModel.chatList) { viewModel.chatList.distinctBy { it.chatId } }

    LaunchedEffect(Unit) {
        viewModel.loadVaultStats()
        viewModel.loadChatList()
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.surface) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = Standards.SpacingXl),
            verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Standards.SpacingSm, vertical = Standards.SpacingSm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ActionButton(
                        onClickListener = onNavigateBack,
                        icon = TnIcons.ArrowLeft,
                        contentDescription = "Back"
                    )
                    Icon(
                        TnIcons.ShieldLock, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = Standards.SpacingSm, end = Standards.SpacingSm)
                    )
                    Text(
                        "Memory Vault",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    ActionButton(
                        onClickListener = {
                            viewModel.loadVaultStats()
                            viewModel.loadChatList()
                        },
                        icon = TnIcons.Refresh,
                        contentDescription = "Refresh"
                    )
                }
            }

            // Quick Stats
            item {
                val stats = viewModel.vaultStats
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Standards.SpacingLg),
                    horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
                ) {
                    QuickStatChip(
                        label = "Chats",
                        value = "${viewModel.chatList.size}",
                        modifier = Modifier.weight(1f)
                    )
                    QuickStatChip(
                        label = "Messages",
                        value = "${stats?.totalMessages ?: 0}",
                        modifier = Modifier.weight(1f)
                    )
                    QuickStatChip(
                        label = "Size",
                        value = formatBytes(stats?.totalSizeBytes ?: 0),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Loading indicator
            if (viewModel.isLoading) {
                item {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Standards.SpacingLg)
                    )
                }
            }

            // Section: Conversations
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Standards.SpacingLg, vertical = Standards.SpacingXs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Conversations",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "${viewModel.chatList.size} total",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Chat list or empty state
            if (viewModel.chatList.isEmpty() && !viewModel.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Standards.SpacingXxl),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                TnIcons.Message, null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Text(
                                "No conversations yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = Standards.SpacingSm)
                            )
                        }
                    }
                }
            } else {
                items(dedupedChats, key = { it.chatId }) { chat ->
                    CompactChatCard(
                        chat = chat,
                        onDelete = { viewModel.deleteChat(chat.chatId) },
                        modifier = Modifier.padding(horizontal = Standards.SpacingLg)
                    )
                }
            }

            // Section: Tools
            item {
                Spacer(Modifier.height(Standards.SpacingSm))
                Text(
                    "Tools",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = Standards.SpacingLg)
                )
            }

            // Defragment
            item {
                ToolActionCard(
                    title = "Defragment",
                    description = "Reclaim unused space",
                    icon = TnIcons.Eraser,
                    isProcessing = viewModel.isDefragging,
                    progress = viewModel.defragProgress,
                    onClick = { viewModel.performDefragmentation() },
                    modifier = Modifier.padding(horizontal = Standards.SpacingLg)
                )
            }

            // Collapsible Logs
            item {
                Spacer(Modifier.height(Standards.SpacingSm))
                Surface(
                    onClick = { showLogs = !showLogs },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Standards.SpacingLg),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(Standards.RadiusLg)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Standards.SpacingMd),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
                    ) {
                        Icon(
                            TnIcons.Terminal, null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Vault Logs",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        ExpandCollapseIcon(isExpanded = showLogs, size = 18.dp)
                    }
                }
            }

            // Expanded logs content
            if (showLogs) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .padding(horizontal = Standards.SpacingLg)
                    ) {
                        TerminalLoggerScreen()
                    }
                }
            }
        }
    }
}

// ── Compact Components ──

@Composable
private fun QuickStatChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = Standards.SpacingSm),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CompactChatCard(
    chat: ChatInfo,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Standards.SpacingMd, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
        ) {
            Icon(
                TnIcons.Message, null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Chat ${chat.chatId.take(8)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "${chat.messageCount} msgs  ·  ${formatCompactDate(chat.lastMessageTime ?: chat.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ActionButton(
                onClickListener = onDelete,
                icon = TnIcons.Trash,
                contentDescription = "Delete"
            )
        }
    }
}

@Composable
private fun ToolActionCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isProcessing: Boolean = false,
    progress: Float = 0f,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = if (!isProcessing) onClick else ({}),
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(Standards.SpacingMd)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
            ) {
                Icon(
                    icon, null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (isProcessing) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

