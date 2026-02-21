package com.dark.tool_neuron.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.models.McpStoreCategories
import com.dark.tool_neuron.models.McpStoreEntry
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.ActionTextButton
import com.dark.tool_neuron.ui.theme.rDp
import com.dark.tool_neuron.viewmodel.McpStoreViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpStoreScreen(
    onBackClick: () -> Unit,
    viewModel: McpStoreViewModel = hiltViewModel()
) {
    val entries by viewModel.filteredEntries.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val installedIds by viewModel.installedIds.collectAsStateWithLifecycle()
    val installMessage by viewModel.installMessage.collectAsStateWithLifecycle()
    val showTermuxDialog by viewModel.showTermuxDialog.collectAsStateWithLifecycle()
    val pendingTermuxEntry by viewModel.pendingTermuxEntry.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "MCP Store",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    ActionTextButton(
                        onClickListener = onBackClick,
                        icon = Icons.Default.ChevronLeft,
                        text = "Back",
                        modifier = Modifier.padding(start = rDp(6.dp))
                    )
                },
                actions = {
                    ActionButton(
                        onClickListener = { viewModel.refresh() },
                        icon = Icons.Default.Refresh,
                        modifier = Modifier.padding(end = rDp(6.dp))
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rDp(16.dp), vertical = rDp(8.dp)),
                placeholder = { Text("Search MCP servers...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(rDp(12.dp))
            )

            // Category chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rDp(12.dp)),
                horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
            ) {
                items(McpStoreCategories.all) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { viewModel.setSelectedCategory(category) },
                        label = { Text(category) },
                        leadingIcon = if (selectedCategory == category) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }

            Spacer(modifier = Modifier.height(rDp(8.dp)))

            // Content
            Box(modifier = Modifier.fillMaxSize()) {
                if (entries.isEmpty() && !isLoading) {
                    EmptyStoreState()
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = rDp(16.dp), vertical = rDp(8.dp)),
                        verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
                    ) {
                        items(entries, key = { it.id }) { entry ->
                            StoreEntryCard(
                                entry = entry,
                                isInstalled = entry.id in installedIds,
                                isTermuxAvailable = viewModel.isTermuxInstalled,
                                onInstall = { viewModel.installEntry(entry) }
                            )
                        }
                    }
                }

                // Loading overlay
                AnimatedVisibility(
                    visible = isLoading,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                // Error/install message snackbar
                val message = error ?: installMessage
                message?.let { msg ->
                    Snackbar(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(rDp(16.dp)),
                        action = {
                            TextButton(onClick = {
                                viewModel.clearError()
                                viewModel.clearInstallMessage()
                            }) {
                                Text("Dismiss")
                            }
                        }
                    ) {
                        Text(msg)
                    }
                }
            }
        }
    }

    // Termux setup dialog
    if (showTermuxDialog) {
        TermuxSetupDialog(
            entry = pendingTermuxEntry,
            onDismiss = { viewModel.dismissTermuxDialog() },
            onDownloadTermux = { viewModel.openTermuxDownload(it) },
            onProceed = { viewModel.proceedWithTermuxInstall() },
            isTermuxInstalled = viewModel.isTermuxInstalled
        )
    }
}

@Composable
private fun StoreEntryCard(
    entry: McpStoreEntry,
    isInstalled: Boolean,
    isTermuxAvailable: Boolean,
    onInstall: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(rDp(12.dp))
    ) {
        Column(
            modifier = Modifier.padding(rDp(16.dp))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(rDp(12.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Icon
                    Box(
                        modifier = Modifier
                            .size(rDp(40.dp))
                            .clip(RoundedCornerShape(rDp(8.dp)))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = getIconForEntry(entry),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(rDp(24.dp))
                        )
                    }

                    Column {
                        Text(
                            text = entry.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "by ${entry.author}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Install button
                if (isInstalled) {
                    FilledTonalButton(
                        onClick = {},
                        enabled = false
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Added")
                    }
                } else {
                    Button(onClick = onInstall) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Install")
                    }
                }
            }

            Spacer(modifier = Modifier.height(rDp(8.dp)))

            Text(
                text = entry.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(rDp(8.dp)))

            // Badges row
            Row(
                horizontalArrangement = Arrangement.spacedBy(rDp(6.dp))
            ) {
                CategoryBadge(text = entry.category)

                if (entry.requiresApiKey) {
                    CategoryBadge(text = "API Key", color = MaterialTheme.colorScheme.tertiaryContainer)
                }

                if (entry.requiresTermux) {
                    CategoryBadge(
                        text = if (isTermuxAvailable) "Termux" else "Termux Required",
                        color = if (isTermuxAvailable)
                            MaterialTheme.colorScheme.secondaryContainer
                        else
                            MaterialTheme.colorScheme.errorContainer
                    )
                }

                Text(
                    text = entry.transportType,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Setup instructions
            entry.setupInstructions?.let { instructions ->
                Spacer(modifier = Modifier.height(rDp(6.dp)))
                Text(
                    text = instructions,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CategoryBadge(
    text: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.secondaryContainer
) {
    Surface(
        shape = RoundedCornerShape(rDp(4.dp)),
        color = color
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = rDp(6.dp), vertical = rDp(2.dp)),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun EmptyStoreState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Store,
                contentDescription = null,
                modifier = Modifier.size(rDp(64.dp)),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(rDp(16.dp)))
            Text(
                text = "No servers found",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Try a different search or category",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun TermuxSetupDialog(
    entry: McpStoreEntry?,
    onDismiss: () -> Unit,
    onDownloadTermux: (android.content.Context) -> Unit,
    onProceed: () -> Unit,
    isTermuxInstalled: Boolean
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Terminal, contentDescription = null) },
        title = { Text("Termux Required") },
        text = {
            Column {
                if (!isTermuxInstalled) {
                    Text("This MCP server (${entry?.name ?: "unknown"}) runs locally on your device using Termux.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Termux is a free terminal emulator that lets you run Python and other tools on Android.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Please install Termux from GitHub releases or F-Droid (not Play Store).",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Text("Termux is installed. The pip package '${entry?.pipPackage ?: ""}' will be installed in Termux.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Make sure Python is installed in Termux (run: pkg install python)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            if (isTermuxInstalled) {
                TextButton(onClick = onProceed) {
                    Text("Install")
                }
            } else {
                TextButton(onClick = { onDownloadTermux(context) }) {
                    Text("Download Termux")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun getIconForEntry(entry: McpStoreEntry): ImageVector {
    return when (entry.iconName) {
        "Search" -> Icons.Default.Search
        "Code" -> Icons.Default.Code
        "Language" -> Icons.Default.Language
        "Folder" -> Icons.Default.Folder
        "Storage" -> Icons.Default.Storage
        "Psychology" -> Icons.Default.Psychology
        "Science" -> Icons.Default.Science
        "Cloud" -> Icons.Default.Cloud
        "OndemandVideo" -> Icons.Default.OndemandVideo
        else -> Icons.Default.Extension
    }
}
