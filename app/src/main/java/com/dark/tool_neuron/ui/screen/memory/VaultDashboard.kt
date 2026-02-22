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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Terminal
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.tool_neuron.models.vault.ChatInfo
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.theme.ManropeFontFamily
import com.dark.tool_neuron.ui.theme.rDp
import com.dark.tool_neuron.ui.theme.rSp
import com.dark.tool_neuron.viewmodel.memory.VaultManagementViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VaultDashboard(onNavigateBack: () -> Unit) {
    val viewModel: VaultManagementViewModel = viewModel()
    var showLogs by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadVaultStats()
        viewModel.loadChatList()
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.surface) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = rDp(24.dp)),
            verticalArrangement = Arrangement.spacedBy(rDp(8.dp))
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = rDp(8.dp), vertical = rDp(8.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ActionButton(
                        onClickListener = onNavigateBack,
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                    Icon(
                        Icons.Outlined.Layers, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = rDp(8.dp), end = rDp(8.dp))
                    )
                    Text(
                        "Memory Vault",
                        fontFamily = ManropeFontFamily,
                        fontSize = rSp(18.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    ActionButton(
                        onClickListener = {
                            viewModel.loadVaultStats()
                            viewModel.loadChatList()
                        },
                        icon = Icons.Outlined.Refresh,
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
                        .padding(horizontal = rDp(16.dp)),
                    horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
                ) {
                    QuickStatChip(
                        label = "Chats",
                        value = "${viewModel.chatList.size}",
                        modifier = Modifier.weight(1f)
                    )
                    QuickStatChip(
                        label = "Messages",
                        value = "${stats?.messageCount ?: 0}",
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
                            .padding(horizontal = rDp(16.dp))
                    )
                }
            }

            // Section: Conversations
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = rDp(16.dp), vertical = rDp(4.dp)),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Conversations",
                        fontFamily = ManropeFontFamily,
                        fontSize = rSp(13.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "${viewModel.chatList.size} total",
                        fontFamily = ManropeFontFamily,
                        fontSize = rSp(11.sp),
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
                            .padding(vertical = rDp(32.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.ChatBubbleOutline, null,
                                modifier = Modifier.size(rDp(36.dp)),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Text(
                                "No conversations yet",
                                fontFamily = ManropeFontFamily,
                                fontSize = rSp(13.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = rDp(8.dp))
                            )
                        }
                    }
                }
            } else {
                items(viewModel.chatList, key = { it.chatId }) { chat ->
                    CompactChatCard(
                        chat = chat,
                        onDelete = { viewModel.deleteChat(chat.chatId) },
                        modifier = Modifier.padding(horizontal = rDp(16.dp))
                    )
                }
            }

            // Section: Tools
            item {
                Spacer(Modifier.height(rDp(8.dp)))
                Text(
                    "Tools",
                    fontFamily = ManropeFontFamily,
                    fontSize = rSp(13.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = rDp(16.dp))
                )
            }

            // Defragment
            item {
                ToolActionCard(
                    title = "Defragment",
                    description = "Reclaim unused space",
                    icon = Icons.Outlined.CleaningServices,
                    isProcessing = viewModel.isDefragging,
                    progress = viewModel.defragProgress,
                    onClick = { viewModel.performDefragmentation() },
                    modifier = Modifier.padding(horizontal = rDp(16.dp))
                )
            }

            // Collapsible Logs
            item {
                Spacer(Modifier.height(rDp(8.dp)))
                Surface(
                    onClick = { showLogs = !showLogs },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = rDp(16.dp)),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(rDp(12.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(rDp(12.dp)),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
                    ) {
                        Icon(
                            Icons.Outlined.Terminal, null,
                            modifier = Modifier.size(rDp(18.dp)),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Vault Logs",
                            fontFamily = ManropeFontFamily,
                            fontSize = rSp(13.sp),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            if (showLogs) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            null,
                            modifier = Modifier.size(rDp(18.dp)),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Expanded logs content
            if (showLogs) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rDp(300.dp))
                            .padding(horizontal = rDp(16.dp))
                    ) {
                        TerminalLoggerScreen()
                    }
                }
            }
        }
    }
}

// ==================== Compact Components ====================

@Composable
private fun QuickStatChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(rDp(10.dp))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = rDp(10.dp), vertical = rDp(8.dp)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                fontFamily = ManropeFontFamily,
                fontSize = rSp(15.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                label,
                fontFamily = ManropeFontFamily,
                fontSize = rSp(11.sp),
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
        shape = RoundedCornerShape(rDp(10.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = rDp(12.dp), vertical = rDp(10.dp)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
        ) {
            Icon(
                Icons.Outlined.ChatBubbleOutline, null,
                modifier = Modifier.size(rDp(16.dp)),
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Chat ${chat.chatId.take(8)}",
                    fontFamily = ManropeFontFamily,
                    fontSize = rSp(13.sp),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "${chat.messageCount} msgs  ·  ${formatCompactDate(chat.lastMessageTime ?: chat.createdAt)}",
                    fontFamily = ManropeFontFamily,
                    fontSize = rSp(11.sp),
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
        shape = RoundedCornerShape(rDp(10.dp))
    ) {
        Column(modifier = Modifier.padding(rDp(12.dp))) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
            ) {
                Icon(
                    icon, null,
                    modifier = Modifier.size(rDp(18.dp)),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        fontFamily = ManropeFontFamily,
                        fontSize = rSp(13.sp),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        description,
                        fontFamily = ManropeFontFamily,
                        fontSize = rSp(11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (isProcessing) {
                Spacer(Modifier.height(rDp(6.dp)))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ==================== Formatting ====================

private fun formatCompactDate(timestamp: Long): String {
    if (timestamp == 0L) return "N/A"
    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
