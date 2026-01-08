package com.dark.tool_neuron.ui.screen.home_screen

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.tool_neuron.R
import com.dark.tool_neuron.di.AppContainer
import com.dark.tool_neuron.models.vault.ChatInfo
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.ActionTextButton
import com.dark.tool_neuron.ui.theme.rDp
import com.dark.tool_neuron.viewmodel.ChatListViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeDrawerScreen(
    onChatSelected: (String) -> Unit,
    viewModel: ChatListViewModel = viewModel(factory = AppContainer.getChatListViewModelFactory())
) {
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isDialogOpen by viewModel.isDialogOpen.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize().then(
            if (isDialogOpen) Modifier.blur(rDp(4.dp)) else Modifier
        ),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopBar(
                searchQuery = searchQuery,
                onSearchQueryChange = { viewModel.searchMessages(it) },
                onClearSearch = { viewModel.clearSearch() })
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    viewModel.createNewChat { chatId ->
                        onChatSelected(chatId)
                    }
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Filled.Add, contentDescription = "New Chat")
            }
        }) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading && chats.isEmpty() -> {
                    LoadingState()
                }

                chats.isEmpty() -> {
                    EmptyState(
                        onCreateChat = {
                            viewModel.createNewChat { chatId ->
                                onChatSelected(chatId)
                            }
                        })
                }

                else -> {
                    ChatList(
                        chats = chats,
                        viewModel = viewModel,
                        onChatClick = onChatSelected,
                        onDeleteChat = { viewModel.deleteChat(it) })
                }
            }

            error?.let { errorMessage ->
                ErrorSnackbar(
                    message = errorMessage, onDismiss = { viewModel.clearError() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    searchQuery: String, onSearchQueryChange: (String) -> Unit, onClearSearch: () -> Unit
) {
    var isSearchActive by remember { mutableStateOf(false) }

    TopAppBar(title = {
        if (isSearchActive) {
            TextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search chats...") },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                singleLine = true
            )
        } else {
            Text(
                "Chats", style = MaterialTheme.typography.titleLarge
            )
        }
    }, actions = {
        if (isSearchActive) {
            ActionButton(
                onClickListener = {
                    isSearchActive = false
                    onClearSearch()
                }, icon = Icons.Filled.Close, modifier = Modifier.padding(end = rDp(6.dp))
            )
        } else {
            ActionButton(
                onClickListener = { isSearchActive = true },
                icon = Icons.Filled.Search,
                modifier = Modifier.padding(end = rDp(6.dp))
            )
        }
    })
}

@Composable
private fun ChatList(
    chats: List<ChatInfo>, viewModel: ChatListViewModel, onChatClick: (String) -> Unit, onDeleteChat: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = rDp(8.dp))
    ) {
        items(
            items = chats, key = { it.chatId }) { chat ->
            ChatListItem(
                chat = chat,
                viewModel = viewModel,
                onClick = { onChatClick(chat.chatId) },
                onDelete = { onDeleteChat(chat.chatId) })
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChatListItem(
    chat: ChatInfo, viewModel: ChatListViewModel, onClick: () -> Unit, onDelete: () -> Unit
) {
    val isDialogOpen by viewModel.isDialogOpen.collectAsStateWithLifecycle()

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = rDp(8.dp), vertical = rDp(4.dp)),
        shape = RoundedCornerShape(rDp(12.dp)),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDp(12.dp)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(rDp(4.dp))
            ) {
                Text(
                    text = "Chat ${chat.chatId.take(8)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(rDp(8.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${chat.messageCount} messages",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = formatTimestamp(chat.lastMessageTime ?: chat.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = { viewModel.openDialog() }) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete chat",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (isDialogOpen) {
        AlertDialog(
            onDismissRequest = { viewModel.closeDialog() },
            title = { Text("Delete Chat") },
            text = { Text("Are you sure you want to delete this chat? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        viewModel.closeDialog()
                    }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closeDialog() }) {
                    Text("Cancel")
                }
            })
    }
}

@Composable
private fun EmptyState(onCreateChat: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(rDp(16.dp))
        ) {
            Icon(
                painterResource(R.drawable.chats),
                contentDescription = null,
                modifier = Modifier.size(rDp(80.dp)),
                tint = MaterialTheme.colorScheme.primary.copy(0.4f)
            )

            Text(
                "No chats yet",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                "Start a new conversation",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(rDp(8.dp)))

            ActionTextButton(onClickListener = onCreateChat,
                Icons.Default.Add, "New Chat")
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(rDp(16.dp))
        ) {
            CircularProgressIndicator()
            Text(
                "Loading chats...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorSnackbar(
    message: String, onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(rDp(16.dp)),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(rDp(8.dp)),
            color = MaterialTheme.colorScheme.errorContainer
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(rDp(16.dp)),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        diff < 604800_000 -> "${diff / 86400_000}d ago"
        else -> {
            val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}