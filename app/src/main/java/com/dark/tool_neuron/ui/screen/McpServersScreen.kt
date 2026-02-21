package com.dark.tool_neuron.ui.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.models.table_schema.McpConnectionStatus
import com.dark.tool_neuron.models.table_schema.McpServer
import com.dark.tool_neuron.models.table_schema.McpTransportType
import com.dark.tool_neuron.service.McpTestResult
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.ActionTextButton
import com.dark.tool_neuron.ui.components.CuteSwitch
import com.dark.tool_neuron.ui.theme.rDp
import com.dark.tool_neuron.viewmodel.McpServerUiState
import com.dark.tool_neuron.viewmodel.McpServerViewModel
import java.text.SimpleDateFormat
import java.util.*

// Success color for connected/successful states
private val SuccessGreen = Color(0xFF4CAF50)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpServersScreen(
    onBackClick: () -> Unit,
    onStoreClick: () -> Unit = {},
    viewModel: McpServerViewModel = hiltViewModel()
) {
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val serverCount by viewModel.serverCount.collectAsStateWithLifecycle()
    val enabledServerCount by viewModel.enabledServerCount.collectAsStateWithLifecycle()
    val showAddDialog by viewModel.showAddDialog.collectAsStateWithLifecycle()
    val showEditDialog by viewModel.showEditDialog.collectAsStateWithLifecycle()
    val selectedServer by viewModel.selectedServer.collectAsStateWithLifecycle()
    val testingServerId by viewModel.testingServerId.collectAsStateWithLifecycle()
    val testResult by viewModel.testResult.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "MCP Servers",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "$enabledServerCount active / $serverCount total",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                        onClickListener = onStoreClick,
                        icon = Icons.Default.Store,
                        modifier = Modifier.padding(end = rDp(4.dp))
                    )
                    ActionButton(
                        onClickListener = { viewModel.showAddServerDialog() },
                        icon = Icons.Default.Add,
                        modifier = Modifier.padding(end = rDp(6.dp))
                    )
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (servers.isEmpty()) {
                EmptyServersState(onAddServer = { viewModel.showAddServerDialog() })
            } else {
                ServersList(
                    servers = servers,
                    testingServerId = testingServerId,
                    onServerClick = { viewModel.showEditServerDialog(it.server) },
                    onToggleEnabled = { server, enabled -> 
                        viewModel.toggleServerEnabled(server.server.id, enabled) 
                    },
                    onTestConnection = { viewModel.testConnection(it.server) },
                    onDeleteServer = { viewModel.deleteServer(it.server.id) }
                )
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

            // Error snackbar
            error?.let { errorMessage ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(rDp(16.dp)),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("Dismiss")
                        }
                    }
                ) {
                    Text(errorMessage)
                }
            }
        }
    }

    // Add Server Dialog
    if (showAddDialog) {
        AddEditServerDialog(
            server = null,
            isTesting = testingServerId == "new",
            testResult = testResult,
            onDismiss = { viewModel.hideAddServerDialog() },
            onSave = { name, url, transportType, apiKey, description ->
                viewModel.addServer(name, url, transportType, apiKey, description)
            },
            onTestConnection = { name, url, transportType, apiKey ->
                viewModel.testConnectionWithParams(name, url, transportType, apiKey)
            },
            onClearTestResult = { viewModel.clearTestResult() }
        )
    }

    // Edit Server Dialog
    if (showEditDialog && selectedServer != null) {
        AddEditServerDialog(
            server = selectedServer,
            isTesting = testingServerId == selectedServer?.id,
            testResult = testResult,
            onDismiss = { viewModel.hideEditServerDialog() },
            onSave = { name, url, transportType, apiKey, description ->
                selectedServer?.let { server ->
                    viewModel.updateServer(
                        server.copy(
                            name = name,
                            url = url,
                            transportType = transportType,
                            apiKey = apiKey?.takeIf { it.isNotBlank() },
                            description = description
                        )
                    )
                }
            },
            onTestConnection = { name, url, transportType, apiKey ->
                viewModel.testConnectionWithParams(name, url, transportType, apiKey)
            },
            onClearTestResult = { viewModel.clearTestResult() }
        )
    }
}

@Composable
private fun EmptyServersState(onAddServer: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(rDp(16.dp))
        ) {
            Icon(
                imageVector = Icons.Default.Cloud,
                contentDescription = null,
                modifier = Modifier.size(rDp(72.dp)),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Text(
                "No MCP Servers",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Connect to remote MCP servers to extend\nyour AI capabilities with external tools",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = rDp(32.dp))
            )
            Spacer(modifier = Modifier.height(rDp(8.dp)))
            ActionTextButton(
                onClickListener = onAddServer,
                icon = Icons.Default.Add,
                text = "Add Server",
                shape = RoundedCornerShape(rDp(12.dp))
            )
        }
    }
}

@Composable
private fun ServersList(
    servers: List<McpServerUiState>,
    testingServerId: String?,
    onServerClick: (McpServerUiState) -> Unit,
    onToggleEnabled: (McpServerUiState, Boolean) -> Unit,
    onTestConnection: (McpServerUiState) -> Unit,
    onDeleteServer: (McpServerUiState) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(rDp(16.dp)),
        verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
    ) {
        // Info card
        item {
            InfoCard()
        }

        items(servers, key = { it.server.id }) { serverState ->
            ServerCard(
                serverState = serverState,
                isTesting = testingServerId == serverState.server.id,
                onClick = { onServerClick(serverState) },
                onToggleEnabled = { enabled -> onToggleEnabled(serverState, enabled) },
                onTestConnection = { onTestConnection(serverState) },
                onDelete = { onDeleteServer(serverState) }
            )
        }
    }
}

@Composable
private fun InfoCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(rDp(12.dp)),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier.padding(rDp(16.dp)),
            horizontalArrangement = Arrangement.spacedBy(rDp(12.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(rDp(24.dp))
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "MCP (Model Context Protocol)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Connect to remote MCP servers to access external tools, resources, and capabilities for your AI conversations.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ServerCard(
    serverState: McpServerUiState,
    isTesting: Boolean,
    onClick: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onTestConnection: () -> Unit,
    onDelete: () -> Unit
) {
    val server = serverState.server
    val status = serverState.connectionStatus

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(rDp(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = when (status) {
                McpConnectionStatus.CONNECTED -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                McpConnectionStatus.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                McpConnectionStatus.CONNECTING -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDp(16.dp))
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Status indicator
                    StatusIndicator(status = status, isTesting = isTesting)
                    
                    Spacer(modifier = Modifier.width(rDp(12.dp)))
                    
                    Column {
                        Text(
                            text = server.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = server.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                CuteSwitch(
                    checked = server.isEnabled,
                    onCheckedChange = onToggleEnabled
                )
            }

            // Transport type badge
            Spacer(modifier = Modifier.height(rDp(12.dp)))
            Row(
                horizontalArrangement = Arrangement.spacedBy(rDp(8.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TransportBadge(transportType = server.transportType)
                
                if (server.apiKey != null) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                        contentColor = MaterialTheme.colorScheme.secondary
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(rDp(4.dp)),
                            modifier = Modifier.padding(horizontal = rDp(4.dp))
                        ) {
                            Icon(
                                Icons.Default.Key,
                                contentDescription = null,
                                modifier = Modifier.size(rDp(12.dp))
                            )
                            Text("Auth", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                server.lastConnectedAt?.let { lastConnected ->
                    Text(
                        text = "Last connected: ${formatTimestamp(lastConnected)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            // Description
            if (server.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(rDp(8.dp)))
                Text(
                    text = server.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Actions
            Spacer(modifier = Modifier.height(rDp(12.dp)))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(rDp(12.dp)))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActionTextButton(
                    onClickListener = onTestConnection,
                    icon = Icons.Default.Refresh,
                    text = if (isTesting) "Testing..." else "Test Connection",
                    shape = RoundedCornerShape(rDp(12.dp))
                )

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(rDp(36.dp))
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(rDp(20.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusIndicator(status: McpConnectionStatus, isTesting: Boolean) {
    val color = when {
        isTesting -> MaterialTheme.colorScheme.tertiary
        status == McpConnectionStatus.CONNECTED -> SuccessGreen
        status == McpConnectionStatus.ERROR -> MaterialTheme.colorScheme.error
        status == McpConnectionStatus.CONNECTING -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .size(rDp(12.dp))
            .clip(CircleShape)
            .background(
                if (isTesting || status == McpConnectionStatus.CONNECTING) {
                    color.copy(alpha = alpha)
                } else {
                    color
                }
            )
    )
}

@Composable
private fun TransportBadge(transportType: McpTransportType) {
    Badge(
        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        contentColor = MaterialTheme.colorScheme.primary
    ) {
        Text(
            text = when (transportType) {
                McpTransportType.SSE -> "SSE"
                McpTransportType.STREAMABLE_HTTP -> "HTTP"
            },
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = rDp(4.dp))
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditServerDialog(
    server: McpServer?,
    isTesting: Boolean,
    testResult: McpTestResult?,
    onDismiss: () -> Unit,
    onSave: (name: String, url: String, transportType: McpTransportType, apiKey: String?, description: String) -> Unit,
    onTestConnection: (name: String, url: String, transportType: McpTransportType, apiKey: String?) -> Unit,
    onClearTestResult: () -> Unit
) {
    var name by remember { mutableStateOf(server?.name ?: "") }
    var url by remember { mutableStateOf(server?.url ?: "") }
    var transportType by remember { mutableStateOf(server?.transportType ?: McpTransportType.SSE) }
    var apiKey by remember { mutableStateOf(server?.apiKey ?: "") }
    var description by remember { mutableStateOf(server?.description ?: "") }
    var showApiKey by remember { mutableStateOf(false) }

    val isValid = name.isNotBlank() && url.isNotBlank() && 
        (url.startsWith("http://") || url.startsWith("https://"))

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = rDp(12.dp))
                    .width(rDp(40.dp))
                    .height(rDp(4.dp))
                    .clip(RoundedCornerShape(rDp(2.dp)))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = rDp(24.dp))
                .padding(bottom = rDp(32.dp))
        ) {
            // Header
            Text(
                text = if (server == null) "Add MCP Server" else "Edit MCP Server",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Configure a remote MCP server connection",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(rDp(24.dp)))

            // Name field
            OutlinedTextField(
                value = name,
                onValueChange = { 
                    name = it
                    onClearTestResult()
                },
                label = { Text("Server Name") },
                placeholder = { Text("My MCP Server") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(rDp(12.dp)),
                leadingIcon = {
                    Icon(Icons.Default.Label, contentDescription = null)
                }
            )

            Spacer(modifier = Modifier.height(rDp(16.dp)))

            // URL field
            val isInsecureUrl = url.startsWith("http://") && !url.startsWith("https://")
            val showSecurityWarning = isInsecureUrl && apiKey.isNotBlank()
            
            OutlinedTextField(
                value = url,
                onValueChange = { 
                    url = it
                    onClearTestResult()
                },
                label = { Text("Server URL") },
                placeholder = { Text("https://api.example.com/mcp") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(rDp(12.dp)),
                leadingIcon = {
                    Icon(Icons.Default.Link, contentDescription = null)
                },
                trailingIcon = if (showSecurityWarning) {
                    {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Security warning",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                } else null,
                isError = url.isNotBlank() && !url.startsWith("http://") && !url.startsWith("https://"),
                supportingText = when {
                    url.isNotBlank() && !url.startsWith("http://") && !url.startsWith("https://") -> {
                        { Text("URL must start with http:// or https://") }
                    }
                    showSecurityWarning -> {
                        { 
                            Text(
                                "Warning: Using HTTP with an API key is insecure. Use HTTPS for secure connections.",
                                color = MaterialTheme.colorScheme.error
                            ) 
                        }
                    }
                    isInsecureUrl -> {
                        { 
                            Text(
                                "Consider using HTTPS for secure connections",
                                color = MaterialTheme.colorScheme.tertiary
                            ) 
                        }
                    }
                    else -> null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )

            Spacer(modifier = Modifier.height(rDp(16.dp)))

            // Transport type selector
            Text(
                text = "Transport Type",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(rDp(8.dp)))
            Row(
                horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
            ) {
                FilterChip(
                    selected = transportType == McpTransportType.SSE,
                    onClick = { 
                        transportType = McpTransportType.SSE
                        onClearTestResult()
                    },
                    label = { Text("SSE (Server-Sent Events)") },
                    leadingIcon = if (transportType == McpTransportType.SSE) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(rDp(16.dp))) }
                    } else null
                )
                FilterChip(
                    selected = transportType == McpTransportType.STREAMABLE_HTTP,
                    onClick = { 
                        transportType = McpTransportType.STREAMABLE_HTTP
                        onClearTestResult()
                    },
                    label = { Text("Streamable HTTP") },
                    leadingIcon = if (transportType == McpTransportType.STREAMABLE_HTTP) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(rDp(16.dp))) }
                    } else null
                )
            }

            Spacer(modifier = Modifier.height(rDp(16.dp)))

            // API Key field
            OutlinedTextField(
                value = apiKey,
                onValueChange = { 
                    apiKey = it
                    onClearTestResult()
                },
                label = { Text("API Key (Optional)") },
                placeholder = { Text("Bearer token or API key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(rDp(12.dp)),
                leadingIcon = {
                    Icon(Icons.Default.Key, contentDescription = null)
                },
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }) {
                        Icon(
                            if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showApiKey) "Hide" else "Show"
                        )
                    }
                },
                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation()
            )

            Spacer(modifier = Modifier.height(rDp(16.dp)))

            // Description field
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (Optional)") },
                placeholder = { Text("What this server provides...") },
                minLines = 2,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(rDp(12.dp))
            )

            Spacer(modifier = Modifier.height(rDp(16.dp)))

            // Test result
            AnimatedVisibility(
                visible = testResult != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                testResult?.let { result ->
                    TestResultCard(result = result)
                    Spacer(modifier = Modifier.height(rDp(16.dp)))
                }
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(rDp(12.dp))
            ) {
                OutlinedButton(
                    onClick = { 
                        onTestConnection(name, url, transportType, apiKey.takeIf { it.isNotBlank() })
                    },
                    enabled = isValid && !isTesting,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(rDp(12.dp))
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(rDp(16.dp)),
                            strokeWidth = rDp(2.dp)
                        )
                        Spacer(modifier = Modifier.width(rDp(8.dp)))
                    }
                    Text(if (isTesting) "Testing..." else "Test Connection")
                }

                Button(
                    onClick = { 
                        onSave(name, url, transportType, apiKey.takeIf { it.isNotBlank() }, description)
                    },
                    enabled = isValid,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(rDp(12.dp))
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(rDp(8.dp)))
                    Text(if (server == null) "Add Server" else "Save Changes")
                }
            }
        }
    }
}

@Composable
private fun TestResultCard(result: McpTestResult) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(rDp(12.dp)),
        color = if (result.success) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        }
    ) {
        Column(
            modifier = Modifier.padding(rDp(16.dp))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
            ) {
                Icon(
                    imageVector = if (result.success) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (result.success) {
                        SuccessGreen
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.size(rDp(20.dp))
                )
                Text(
                    text = if (result.success) "Connection Successful" else "Connection Failed",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (result.success) {
                        SuccessGreen
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }

            if (result.serverInfo != null) {
                Spacer(modifier = Modifier.height(rDp(4.dp)))
                Text(
                    text = "Server: ${result.serverInfo}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!result.success) {
                Spacer(modifier = Modifier.height(rDp(4.dp)))
                Text(
                    text = result.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (result.tools.isNotEmpty()) {
                Spacer(modifier = Modifier.height(rDp(8.dp)))
                Text(
                    text = "Available Tools (${result.tools.size}):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(rDp(4.dp)))
                result.tools.take(5).forEach { tool ->
                    Text(
                        text = "• ${tool.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (result.tools.size > 5) {
                    Text(
                        text = "... and ${result.tools.size - 5} more",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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
        diff < 60_000 -> "just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        diff < 604800_000 -> "${diff / 86400_000}d ago"
        else -> {
            val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}
