package com.dark.tool_neuron.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.models.vault.ChatInfo
import com.dark.tool_neuron.global.formatRelativeTime
import com.dark.tool_neuron.state.AppStateManager
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.viewmodel.ChatListViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.dark.tool_neuron.ui.icons.TnIcons
import androidx.compose.ui.graphics.vector.rememberVectorPainter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeDrawerScreen(
    onChatSelected: (String) -> Unit,
    onVaultManagerClick: () -> Unit,
    chatViewModel: com.dark.tool_neuron.viewmodel.ChatViewModel,
    viewModel: ChatListViewModel = hiltViewModel()
) {
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val isDialogOpen by viewModel.isDialogOpen.collectAsStateWithLifecycle()

    val isChatRefreshed by AppStateManager.isChatRefreshed.collectAsStateWithLifecycle()
    val currentChatId by chatViewModel.currentChatId.collectAsStateWithLifecycle()

    LaunchedEffect(isChatRefreshed) {
        if (isChatRefreshed) {
            viewModel.loadChats()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (isDialogOpen) Modifier.blur(4.dp) else Modifier
            ),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Chats",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    Row{
                        ActionButton(
                            onClickListener = onVaultManagerClick,
                            icon = TnIcons.Sparkles,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        ActionButton(
                            onClickListener = {
                                viewModel.createNewChat { chatId ->
                                    onChatSelected(chatId)
                                }
                            },
                            icon = TnIcons.Plus,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
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
                    EmptyState()
                }

                else -> {
                    ChatList(
                        chats = chats,
                        isRefreshing = isLoading,
                        onRefresh = { viewModel.loadChats() },
                        onChatClick = onChatSelected,
                        onDeleteChat = { chatId ->
                            viewModel.deleteChat(chatId)
                            // If deleting the currently loaded chat, start a new conversation
                            if (chatId == currentChatId) {
                                chatViewModel.startNewConversation()
                            }
                        }
                    )
                }
            }

            error?.let { errorMessage ->
                ErrorSnackbar(
                    message = errorMessage,
                    onDismiss = { viewModel.clearError() }
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChatList(
    chats: List<ChatInfo>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onChatClick: (String) -> Unit,
    onDeleteChat: (String) -> Unit
) {
    var isManualRefreshing by remember { mutableStateOf(false) }
    val dedupedChats = remember(chats) { chats.distinctBy { it.chatId } }

    // Reset manual refresh flag when real loading completes
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing && isManualRefreshing) {
            delay(300) // Brief visual delay so spinner doesn't vanish instantly
            isManualRefreshing = false
        }
    }

    PullToRefreshBox(
        isRefreshing = isManualRefreshing,
        onRefresh = {
            isManualRefreshing = true
            onRefresh()
        },
        indicator = {
            AnimatedVisibility(isManualRefreshing, modifier = Modifier.align(Alignment.Center)) {
                LoadingIndicator()
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isManualRefreshing) Modifier.blur(16.dp) else Modifier
                ),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(
                items = dedupedChats,
                key = { it.chatId }
            ) { chat ->
                ChatListItem(
                    chat = chat,
                    onClick = { onChatClick(chat.chatId) },
                    onDelete = { onDeleteChat(chat.chatId) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChatListItem(
    chat: ChatInfo,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var isDeleting by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Chat ${chat.chatId.take(8)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "${chat.messageCount} msgs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = formatRelativeTime(chat.lastMessageTime ?: chat.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isDeleting) {
                LoadingIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                IconButton(
                    onClick = {
                        isDeleting = true
                        onDelete()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        TnIcons.Trash,
                        contentDescription = "Delete chat",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = TnIcons.Messages,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(0.4f)
            )

            Text(
                "No chats yet",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                "Tap + to start a new conversation",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LoadingIndicator()
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
    message: String,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.errorContainer
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        TnIcons.X,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

