package com.dark.tool_neuron.ui.screens.home_screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.dark.tool_neuron.model.Chat
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.ActionTextButton
import com.dark.tool_neuron.ui.components.InfoBadge
import com.dark.tool_neuron.ui.components.SectionHeader
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.ui.theme.Motion

@Composable
fun ChatDrawerContent(
    chats: List<Chat>,
    currentChatId: String?,
    onChatSelected: (String) -> Unit,
    onNewChat: () -> Unit,
    onDeleteChat: (String) -> Unit,
    onPinChat: (String, Boolean) -> Unit,
    onNavigateToStore: () -> Unit,
    onNavigateToGuide: () -> Unit,
    onNavigateToDevNotes: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToServer: () -> Unit = {},
) {
    val dimens = LocalDimens.current
    val pinnedChats = remember(chats) { chats.filter { it.isPinned } }
    val recentChats = remember(chats) { chats.filter { !it.isPinned } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = dimens.screenPadding)
    ) {
        Spacer(Modifier.height(dimens.spacingXxl))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm)
            ) {
                Text(
                    text = "ToolNeuron",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                InfoBadge(text = "v2")
            }
        }

        Spacer(Modifier.height(dimens.spacingMd))

        ActionTextButton(
            onClickListener = onNewChat,
            icon = TnIcons.Plus,
            text = "New Chat",
            contentDescription = "New Chat",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(dimens.spacingLg))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingXxs)
        ) {
            if (pinnedChats.isNotEmpty()) {
                item { SectionHeader(title = "Pinned") }
                items(pinnedChats, key = { it.id }) { chat ->
                    ChatItem(
                        chat = chat,
                        isSelected = chat.id == currentChatId,
                        onSelect = { onChatSelected(chat.id) },
                        onDelete = { onDeleteChat(chat.id) },
                        onPin = { onPinChat(chat.id, !chat.isPinned) }
                    )
                }
                item { Spacer(Modifier.height(dimens.spacingSm)) }
            }

            if (recentChats.isNotEmpty()) {
                item { SectionHeader(title = "Recent") }
                items(recentChats, key = { it.id }) { chat ->
                    ChatItem(
                        chat = chat,
                        isSelected = chat.id == currentChatId,
                        onSelect = { onChatSelected(chat.id) },
                        onDelete = { onDeleteChat(chat.id) },
                        onPin = { onPinChat(chat.id, !chat.isPinned) }
                    )
                }
            }

            if (chats.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = dimens.spacingXxl),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No chats yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(dimens.spacingSm))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = dimens.spacingLg),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DrawerQuickLink(icon = TnIcons.Download, label = "Store", onClick = onNavigateToStore)
            DrawerQuickLink(icon = TnIcons.BookOpen, label = "Guide", onClick = onNavigateToGuide)
            DrawerQuickLink(icon = TnIcons.Info, label = "Dev Notes", onClick = onNavigateToDevNotes)
            DrawerQuickLink(icon = TnIcons.Server, label = "Server", onClick = onNavigateToServer)
            DrawerQuickLink(icon = TnIcons.Settings, label = "Settings", onClick = onNavigateToSettings)
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ChatItem(
    chat: Chat,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    var showMenu by remember { mutableStateOf(false) }

    val containerColor by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else
            MaterialTheme.colorScheme.surface,
        animationSpec = Motion.state(),
        label = "chatItemBg"
    )

    val isImageModel = chat.modelName.contains("image", ignoreCase = true)
        || chat.modelName.contains("diffusion", ignoreCase = true)
        || chat.modelName.contains("flux", ignoreCase = true)

    Box {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onSelect,
                    onLongClick = { showMenu = true }
                ),
            color = containerColor,
            shape = tnShapes.cardSmall
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = dimens.cardPadding,
                    vertical = dimens.spacingSm
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm)
            ) {
                Icon(
                    imageVector = if (isImageModel) TnIcons.Photo else TnIcons.Sparkles,
                    contentDescription = null,
                    modifier = Modifier.size(dimens.iconMd),
                    tint = if (isSelected)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = chat.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = chat.modelName,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = formatRelativeTime(chat.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(if (chat.isPinned) "Unpin" else "Pin") },
                onClick = {
                    showMenu = false
                    onPin()
                },
                leadingIcon = {
                    Icon(TnIcons.Sparkles, contentDescription = null, modifier = Modifier.size(dimens.iconMd))
                }
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = {
                    showMenu = false
                    onDelete()
                },
                leadingIcon = {
                    Icon(TnIcons.Trash, contentDescription = null, modifier = Modifier.size(dimens.iconMd))
                }
            )
        }
    }
}

@Composable
private fun DrawerQuickLink(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val dimens = LocalDimens.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.spacingXs),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        ActionButton(
            onClickListener = onClick,
            icon = icon,
            contentDescription = label
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatRelativeTime(timestampMs: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestampMs
    val minutes = diff / 60_000
    val hours = diff / 3_600_000
    val days = diff / 86_400_000
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        hours < 24 -> "${hours}h"
        days < 2 -> "Yesterday"
        days < 7 -> "${days}d"
        else -> "${days / 7}w"
    }
}
