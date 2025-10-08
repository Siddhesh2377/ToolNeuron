package com.dark.neuroverse.ui.drawer

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowCircleDown
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.neuroverse.R
import com.dark.neuroverse.model.ChatINFO
import com.dark.neuroverse.ui.theme.rDP
import com.dark.neuroverse.viewModel.chatViewModel.ChatScreenViewModel
import com.dark.neuroverse.viewModel.chatViewModel.ChatUiState
import kotlinx.coroutines.launch

@Composable
fun SettingsDrawerContent(
    modifier: Modifier = Modifier,
    viewModel: ChatScreenViewModel,
    onSettingsClick: () -> Unit,
    onChatSelected: () -> Unit,
    onNewChatClick: () -> Unit,
    onDataHubClick: () -> Unit,
    onPluginStoreClick: () -> Unit,
    onModelsClick: () -> Unit
) {
    val chatList by viewModel.chatList.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentChatId by viewModel.chatId.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var deletingChatIds by remember { mutableStateOf(setOf<String>()) }
    var showSettingsMenu by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        if (uiState !is ChatUiState.Loading) {
            deletingChatIds = emptySet()
        }
    }

    LaunchedEffect(chatList) {
        Log.d("SettingsDrawerContent", "Chat list updated: $chatList")
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(rDP(300.dp))
            .background(MaterialTheme.colorScheme.background)
            .padding(top = rDP(12.dp))
            .padding(rDP(16.dp))
    ) {
        // Compact Header with Settings Menu Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = rDP(16.dp), bottom = rDP(12.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tool-Neuron", style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                ), modifier = Modifier.weight(1f)
            )

            IconButton(onClick = {
                onNewChatClick()
            }) {
                Icon(
                    Icons.Rounded.AddCircleOutline,
                    contentDescription = "New Chat",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Box {
                IconButton(onClick = {
                    showSettingsMenu = true
                }) {
                    Icon(
                        Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                SettingsDropdownMenu(
                    expanded = showSettingsMenu,
                    onDismiss = { showSettingsMenu = false },
                    onDataHubClick = {
                        showSettingsMenu = false
                        onDataHubClick()
                    },
                    onPluginStoreClick = {
                        showSettingsMenu = false
                        onPluginStoreClick()
                    },
                    onModelsClick = {
                        showSettingsMenu = false
                        onModelsClick()
                    },
                    onSettingsClick = {
                        showSettingsMenu = false
                        onSettingsClick()
                    },
                    enabled = uiState !is ChatUiState.Loading
                )
            }
        }

        Spacer(Modifier.height(rDP(12.dp)))

        // Chat History Header
        Text(
            text = "Recent Chats", style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant
            ), modifier = Modifier.padding(bottom = rDP(8.dp))
        )

        // Chat History List
        LazyColumn(
            modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(rDP(4.dp))
        ) {
            if (chatList.isEmpty()) {
                item {
                    Text(
                        text = "No chats yet\nStart a new conversation!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(vertical = rDP(24.dp), horizontal = rDP(8.dp))
                    )
                }
            } else {
                items(
                    items = chatList, key = { it.id }) { chat ->
                    CompactChatHistoryItem(
                        chat = chat,
                        isCurrentChat = chat.id == currentChatId,
                        isDeleting = chat.id in deletingChatIds,
                        onChatClick = {
                            if (chat.id != currentChatId && uiState !is ChatUiState.Loading) {
                                scope.launch {
                                    viewModel.loadChatById(chat.id)
                                    onChatSelected()
                                }
                            }
                        },
                        onDeleteClick = {
                            if (chat.id !in deletingChatIds && uiState !is ChatUiState.Loading) {
                                deletingChatIds = deletingChatIds + chat.id
                                scope.launch {
                                    viewModel.deleteChatById(chat.id)
                                }
                            }
                        })
                }
            }
        }
    }
}

@Composable
private fun SettingsDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onDataHubClick: () -> Unit,
    onPluginStoreClick: () -> Unit,
    onModelsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    enabled: Boolean
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
    ) {
        DropdownMenuItem(
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(rDP(12.dp))
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.database_zap),
                        contentDescription = "Data Hub",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(rDP(20.dp))
                    )
                    Text(
                        text = "Data Hub", style = MaterialTheme.typography.bodyLarge
                    )
                }
            },
            onClick = onDataHubClick,
            enabled = enabled,
        )

        DropdownMenuItem(
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(rDP(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.Outlined.GridView,
                        contentDescription = "Plugins",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(rDP(20.dp))
                    )
                    Text(
                        text = "Plugin Store", style = MaterialTheme.typography.bodyLarge
                    )
                }
            }, onClick = onPluginStoreClick, enabled = enabled
        )

        DropdownMenuItem(
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(rDP(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ArrowCircleDown,
                        contentDescription = "Models",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(rDP(20.dp))
                    )
                    Text(
                        text = "Models", style = MaterialTheme.typography.bodyLarge
                    )
                }
            }, onClick = onModelsClick, enabled = enabled
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = rDP(4.dp)),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        DropdownMenuItem(
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(rDP(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(rDP(20.dp))
                    )
                    Text(
                        text = "Settings", style = MaterialTheme.typography.bodyLarge
                    )
                }
            }, onClick = onSettingsClick, enabled = enabled
        )
    }
}

@Composable
private fun CompactSearchBox(
    onClick: () -> Unit, enabled: Boolean
) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .clickable(enabled = enabled) { onClick() }
        .background(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = MaterialTheme.shapes.medium
        )
        .padding(horizontal = rDP(12.dp), vertical = rDP(10.dp)),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = "Search",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(rDP(18.dp))
        )
        Spacer(Modifier.width(rDP(8.dp)))
        Text(
            text = "Search chats...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun CompactChatHistoryItem(
    chat: ChatINFO,
    isCurrentChat: Boolean,
    isDeleting: Boolean,
    onChatClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isDeleting) { onChatClick() }
            .background(
                color = if (isCurrentChat) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                }, shape = MaterialTheme.shapes.small
            )
            .padding(horizontal = rDP(12.dp), vertical = rDP(4.dp))) {
        Text(
            text = chat.name.ifBlank { "Untitled Chat" },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = if (isCurrentChat) FontWeight.Medium else FontWeight.Normal
            ),
            color = if (isCurrentChat) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier
                .weight(1f)
                .padding(start = rDP(4.dp))
        )

        if (isDeleting) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(start = rDP(8.dp))
                    .size(rDP(16.dp)),
                strokeWidth = rDP(2.dp),
                color = MaterialTheme.colorScheme.error
            )
        } else {
            IconButton(
                onClick = onDeleteClick, modifier = Modifier.padding(start = rDP(4.dp))
            ) {
                Icon(
                    imageVector = Icons.TwoTone.Delete,
                    contentDescription = "Delete chat",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(rDP(16.dp))
                )
            }
        }
    }
}