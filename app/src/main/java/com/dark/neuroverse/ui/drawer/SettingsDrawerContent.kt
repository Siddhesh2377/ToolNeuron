package com.dark.neuroverse.ui.drawer

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowCircleDown
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.neuroverse.R
import com.dark.neuroverse.ui.theme.SkyBlue
import com.dark.neuroverse.ui.theme.rDP
import com.dark.neuroverse.viewModel.chatViewModel.ChatScreenViewModel
import com.dark.neuroverse.viewModel.chatViewModel.ChatUiState
import kotlinx.coroutines.launch

@Composable
fun SettingsDrawerContent(
    modifier: Modifier = Modifier,
    viewModel: ChatScreenViewModel,
    onSettingsClick: () -> Unit,
    onModelsClick: () -> Unit,
    onChatSelected: () -> Unit,
    onPluginStoreClick: () -> Unit,
    onDataHubClick: () -> Unit,
) {
    val chatList by viewModel.chatList.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentChatId by viewModel.chatId.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // Track deletion states to prevent UI issues
    var deletingChatIds by remember { mutableStateOf(setOf<String>()) }

    // Clear deletion tracking when UI state changes
    LaunchedEffect(uiState) {
        if (uiState !is ChatUiState.Loading) {
            deletingChatIds = emptySet()
        }
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(280.dp) // Slightly wider for better UX
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .padding(vertical = 24.dp)
    ) {
        // Header
        Text(
            text = "Chat Management",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.padding(top = 24.dp, bottom = 24.dp)
        )

        // Chat History Section
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Chat History",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                    )

                    // Show loading indicator if any chat operations are in progress
                    if (uiState is ChatUiState.Loading && (uiState as ChatUiState.Loading).operation.contains("chat", ignoreCase = true)) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }

            if (chatList.isEmpty()) {
                item {
                    Text(
                        text = "No chat history yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            } else {
                items(
                    items = chatList,
                    key = { it.id }
                ) { chat ->
                    ChatHistoryItem(
                        chat = chat,
                        isCurrentChat = chat.id == currentChatId,
                        isDeleting = chat.id in deletingChatIds,
                        onChatClick = {
                            // Prevent loading same chat
                            if (chat.id != currentChatId && uiState !is ChatUiState.Loading) {
                                scope.launch {
                                    viewModel.loadChatById(chat.id)
                                    onChatSelected()
                                }
                            }
                        },
                        onDeleteClick = {
                            // Prevent multiple deletion attempts
                            if (chat.id !in deletingChatIds && uiState !is ChatUiState.Loading) {
                                deletingChatIds = deletingChatIds + chat.id
                                scope.launch {
                                    viewModel.deleteChatById(chat.id)
                                    // Note: Don't call onChatSelected() here as it might interfere with deletion
                                }
                            }
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Action Buttons
        ActionButton(
            text = "Data Hub",
            icon = R.drawable.database_zap,
            onClick = onDataHubClick,
            enabled = uiState !is ChatUiState.Loading
        )

        Spacer(Modifier.height(16.dp))

        // Action Buttons
        ActionButton(
            text = "Plugin Store",
            icon = Icons.Outlined.GridView,
            onClick = onPluginStoreClick,
            enabled = uiState !is ChatUiState.Loading
        )

        Spacer(Modifier.height(16.dp))

        ActionButton(
            text = "Models",
            icon = Icons.Outlined.ArrowCircleDown,
            onClick = onModelsClick,
            enabled = uiState !is ChatUiState.Loading
        )

        Spacer(Modifier.height(16.dp))

        ActionButton(
            text = "Settings",
            icon = Icons.Outlined.Settings,
            onClick = onSettingsClick,
            enabled = uiState !is ChatUiState.Loading
        )
    }
}

@Composable
private fun ChatHistoryItem(
    chat: com.dark.neuroverse.model.ChatINFO,
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
                    SkyBlue.copy(0.1f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
                shape = MaterialTheme.shapes.small
            )
            .padding(horizontal = rDP(12.dp), vertical = rDP(8.dp))
    ) {
        // Chat name
        Text(
            text = chat.name.ifBlank { "Untitled Chat" },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = if (isCurrentChat) FontWeight.Medium else FontWeight.Normal
            ),
            color = if (isCurrentChat) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f)
        )

        // Delete button with loading state
        Box(
            modifier = Modifier.padding(start = 8.dp)
        ) {
            if (isDeleting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.TwoTone.Delete,
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
private fun ActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .clickable(enabled = enabled) { onClick() }
            .fillMaxWidth()
            .background(
                color = if (enabled) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                },
                shape = MaterialTheme.shapes.medium
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif),
            color = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            }
        )
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            }
        )
    }
}

@Composable
private fun ActionButton(
    text: String,
    icon: Int,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .clickable(enabled = enabled) { onClick() }
            .fillMaxWidth()
            .background(
                color = if (enabled) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                },
                shape = MaterialTheme.shapes.medium
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif),
            color = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            }
        )
        Icon(
            painter = painterResource(icon),
            contentDescription = text,
            tint = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            }
        )
    }
}