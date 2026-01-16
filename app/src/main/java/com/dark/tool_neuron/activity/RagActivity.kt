package com.dark.tool_neuron.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.R
import com.dark.tool_neuron.models.table_schema.InstalledRag
import com.dark.tool_neuron.models.table_schema.RagSourceType
import com.dark.tool_neuron.models.table_schema.RagStatus
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.ActionTextButton
import com.dark.tool_neuron.ui.screen.SecureRagCreationScreen
import com.dark.tool_neuron.ui.theme.NeuroVerseTheme
import com.dark.tool_neuron.ui.theme.rDp
import com.dark.tool_neuron.viewmodel.RagViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class RagActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            NeuroVerseTheme {
                RagScreen(onClose = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RagScreen(
    ragViewModel: RagViewModel = hiltViewModel(),
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showCreateSheet by remember { mutableStateOf(false) }
    var showDetailSheet by remember { mutableStateOf(false) }
    var selectedRagDetail by remember { mutableStateOf<InstalledRag?>(null) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var ragToLoad by remember { mutableStateOf<String?>(null) }

    val installedRags by ragViewModel.installedRags.collectAsStateWithLifecycle()
    val loadedRags by ragViewModel.loadedRags.collectAsStateWithLifecycle()
    val isLoading by ragViewModel.isLoading.collectAsStateWithLifecycle()
    val error by ragViewModel.error.collectAsStateWithLifecycle()
    val installedCount by ragViewModel.installedCount.collectAsStateWithLifecycle()
    val loadedCount by ragViewModel.loadedCount.collectAsStateWithLifecycle()

    // SAF file picker for RAG installation
    val ragFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            ragViewModel.installRagFromUri(uri)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column {
                        Text(
                            "RAG Manager",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "$loadedCount loaded / $installedCount installed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
//                    IconButton(onClick = { ragFilePicker.launch(arrayOf("*/*")) }) {
//                        Icon(Icons.Default.Download, contentDescription = "Install RAG")
//                    }
//                    IconButton(onClick = { showCreateSheet = true }) {
//                        Icon(Icons.Default.Add, contentDescription = "Create RAG")
//                    }
                    ActionTextButton(
                        onClickListener = onClose,
                        icon = Icons.Default.ChevronLeft,
                        text = "Back",
                        contentDescription = "Close",
                        shape = RoundedCornerShape(rDp(12.dp))
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Installed ($installedCount)") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Loaded ($loadedCount)") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Create") }
                )
            }

            // Loading indicator
            AnimatedVisibility(visible = isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(rDp(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(rDp(24.dp)))
                }
            }

            // Error display
            error?.let { errorMsg ->
                if (errorMsg != null){
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(rDp(8.dp)),
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(rDp(8.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(rDp(12.dp)),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = errorMsg,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { ragViewModel.clearError() }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            // Tab Content
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally { it } + fadeIn() togetherWith
                                slideOutHorizontally { -it } + fadeOut()
                    } else {
                        slideInHorizontally { -it } + fadeIn() togetherWith
                                slideOutHorizontally { it } + fadeOut()
                    }
                },
                label = "tab_content"
            ) { tab ->
                when (tab) {
                    0 -> RagListContent(
                        rags = installedRags,
                        emptyMessage = "No RAGs installed",
                        emptySubMessage = "Install a RAG package or create one from the Create tab",
                        onRagClick = { rag ->
                            selectedRagDetail = rag
                            showDetailSheet = true
                        },
                        onToggleEnabled = { id, enabled -> ragViewModel.toggleRagEnabled(id, enabled) },
                        onLoad = { ragId ->
                            val rag = installedRags.find { it.id == ragId }
                            if (rag?.isEncrypted == true) {
                                ragToLoad = ragId
                                showPasswordDialog = true
                            } else {
                                ragViewModel.loadRag(ragId)
                            }
                        },
                        onUnload = { ragViewModel.unloadRag(it) },
                        onDelete = { ragViewModel.deleteRag(it) },
                        onShare = { rag -> shareRag(context, rag) }
                    )
                    1 -> RagListContent(
                        rags = loadedRags,
                        emptyMessage = "No RAGs loaded",
                        emptySubMessage = "Load a RAG from the Installed tab to use it in chats",
                        onRagClick = { rag ->
                            selectedRagDetail = rag
                            showDetailSheet = true
                        },
                        onToggleEnabled = { id, enabled -> ragViewModel.toggleRagEnabled(id, enabled) },
                        onLoad = { ragId ->
                            val rag = loadedRags.find { it.id == ragId }
                            if (rag?.isEncrypted == true) {
                                ragToLoad = ragId
                                showPasswordDialog = true
                            } else {
                                ragViewModel.loadRag(ragId)
                            }
                        },
                        onUnload = { ragViewModel.unloadRag(it) },
                        onDelete = { ragViewModel.deleteRag(it) },
                        onShare = { rag -> shareRag(context, rag) }
                    )
                    2 -> SecureRagCreationScreen(
                        ragViewModel = ragViewModel,
                        padding = PaddingValues(0.dp),
                        onRagCreated = { selectedTab = 0 }
                    )
                }
            }
        }
    }

    // Create RAG Bottom Sheet
    if (showCreateSheet) {
        CreateRagBottomSheet(
            ragViewModel = ragViewModel,
            onDismiss = { showCreateSheet = false },
            onCreated = {
                showCreateSheet = false
            }
        )
    }

    // RAG Detail Bottom Sheet
    selectedRagDetail?.let { rag ->
        if (showDetailSheet) {
            RagDetailBottomSheet(
                rag = rag,
                onDismiss = {
                    showDetailSheet = false
                    selectedRagDetail = null
                },
                onLoad = {
                    if (rag.isEncrypted) {
                        ragToLoad = rag.id
                        showPasswordDialog = true
                    } else {
                        ragViewModel.loadRag(rag.id)
                    }
                },
                onUnload = { ragViewModel.unloadRag(rag.id) },
                onDelete = {
                    ragViewModel.deleteRag(rag.id)
                    showDetailSheet = false
                    selectedRagDetail = null
                },
                onShare = {
                    shareRag(context, rag)
                }
            )
        }
    }

    // Password Dialog for encrypted RAGs
    if (showPasswordDialog && ragToLoad != null) {
        PasswordDialog(
            onDismiss = {
                showPasswordDialog = false
                ragToLoad = null
            },
            onConfirm = { password ->
                ragViewModel.loadRag(ragToLoad!!, password)
                showPasswordDialog = false
                ragToLoad = null
            }
        )
    }
}

private fun shareRag(context: android.content.Context, rag: InstalledRag) {
    val ragFile = File(rag.filePath)
    if (!ragFile.exists()) {
        android.widget.Toast.makeText(context, "RAG file not found", android.widget.Toast.LENGTH_SHORT).show()
        return
    }

    val uri = androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        ragFile
    )

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/octet-stream"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, rag.name)
        putExtra(Intent.EXTRA_TEXT, buildString {
            append("${rag.name}\n")
            if (rag.description.isNotBlank()) {
                append("${rag.description}\n\n")
            }
            append("Nodes: ${rag.nodeCount}\n")
            append("Size: ${rag.getFormattedSize()}\n")
            append("Domain: ${rag.domain}")
        })
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context.startActivity(Intent.createChooser(shareIntent, "Share RAG"))
}

@Composable
private fun RagListContent(
    rags: List<InstalledRag>,
    emptyMessage: String,
    emptySubMessage: String,
    onRagClick: (InstalledRag) -> Unit,
    onToggleEnabled: (String, Boolean) -> Unit,
    onLoad: (String) -> Unit,
    onUnload: (String) -> Unit,
    onDelete: (String) -> Unit,
    onShare: ((InstalledRag) -> Unit)? = null
) {
    if (rags.isEmpty()) {
        EmptyRagListState(message = emptyMessage, subMessage = emptySubMessage)
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(rDp(16.dp)),
            verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
        ) {
            items(rags, key = { it.id }) { rag ->
                RagCard(
                    rag = rag,
                    onClick = { onRagClick(rag) },
                    onToggleEnabled = { onToggleEnabled(rag.id, it) },
                    onLoad = { onLoad(rag.id) },
                    onUnload = { onUnload(rag.id) },
                    onDelete = { onDelete(rag.id) },
                    onShare = onShare?.let { { it(rag) } }
                )
            }
        }
    }
}

@Composable
private fun EmptyRagListState(message: String, subMessage: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(rDp(16.dp))
        ) {
            Icon(
                Icons.Default.Memory,
                contentDescription = null,
                modifier = Modifier.size(rDp(64.dp)),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = subMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = rDp(32.dp))
            )
        }
    }
}

@Composable
private fun RagCard(
    rag: InstalledRag,
    onClick: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
    onDelete: () -> Unit,
    onShare: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = when (rag.status) {
                RagStatus.LOADED -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                RagStatus.LOADING -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                RagStatus.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        shape = RoundedCornerShape(rDp(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDp(16.dp))
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
                    Box(
                        modifier = Modifier
                            .size(rDp(48.dp))
                            .clip(RoundedCornerShape(rDp(12.dp)))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = getRagSourceIcon(rag.sourceType),
                            contentDescription = null,
                            modifier = Modifier.size(rDp(24.dp)),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Column {
                        Text(
                            text = rag.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(rDp(8.dp)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${rag.nodeCount} nodes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = rag.getFormattedSize(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Status badge
                StatusBadge(status = rag.status)
            }

            if (rag.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(rDp(8.dp)))
                Text(
                    text = rag.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Tags
            if (rag.getTagsList().isNotEmpty()) {
                Spacer(modifier = Modifier.height(rDp(8.dp)))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(rDp(6.dp))
                ) {
                    rag.getTagsList().take(4).forEach { tag ->
                        TagChip(tag = tag)
                    }
                }
            }

            // Actions
            Spacer(modifier = Modifier.height(rDp(12.dp)))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(rDp(12.dp)))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDate(rag.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))) {
                    when (rag.status) {
                        RagStatus.LOADED -> {
                            TextButton(onClick = onUnload) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(rDp(16.dp))
                                )
                                Spacer(modifier = Modifier.width(rDp(4.dp)))
                                Text("Unload")
                            }
                        }
                        RagStatus.LOADING -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(rDp(20.dp)),
                                strokeWidth = rDp(2.dp)
                            )
                        }
                        else -> {
                            TextButton(onClick = onLoad) {
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(rDp(16.dp))
                                )
                                Spacer(modifier = Modifier.width(rDp(4.dp)))
                                Text("Load")
                            }
                        }
                    }

                    onShare?.let { shareAction ->
                        IconButton(onClick = shareAction) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Share",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: RagStatus) {
    val (color, text) = when (status) {
        RagStatus.LOADED -> MaterialTheme.colorScheme.primary to "Loaded"
        RagStatus.LOADING -> MaterialTheme.colorScheme.tertiary to "Loading"
        RagStatus.ERROR -> MaterialTheme.colorScheme.error to "Error"
        RagStatus.INSTALLED -> MaterialTheme.colorScheme.onSurfaceVariant to "Ready"
    }

    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(rDp(4.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = rDp(8.dp), vertical = rDp(4.dp)),
            horizontalArrangement = Arrangement.spacedBy(rDp(4.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (status == RagStatus.LOADED) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(rDp(12.dp)),
                    tint = color
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun TagChip(tag: String) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        shape = RoundedCornerShape(rDp(4.dp))
    ) {
        Text(
            text = tag,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = rDp(8.dp), vertical = rDp(4.dp))
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateRagBottomSheet(
    ragViewModel: RagViewModel,
    onDismiss: () -> Unit,
    onCreated: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        SecureRagCreationScreen(
            ragViewModel = ragViewModel,
            padding = PaddingValues(),
            onRagCreated = onCreated
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RagDetailBottomSheet(
    rag: InstalledRag,
    onDismiss: () -> Unit,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDp(24.dp))
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rag.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = rag.sourceType.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                StatusBadge(status = rag.status)
            }

            Spacer(modifier = Modifier.height(rDp(16.dp)))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(rDp(16.dp)))

            // Description
            if (rag.description.isNotBlank()) {
                Text(
                    text = "Description",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(rDp(4.dp)))
                Text(
                    text = rag.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(rDp(16.dp)))
            }

            // Stats
            Text(
                text = "Statistics",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(rDp(8.dp)))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(rDp(12.dp))
            ) {
                Column(modifier = Modifier.padding(rDp(16.dp))) {
                    DetailRow("Nodes", "${rag.nodeCount}")
                    DetailRow("Embedding Dimension", "${rag.embeddingDimension}")
                    DetailRow("Size", rag.getFormattedSize())
                    DetailRow("Domain", rag.domain)
                    DetailRow("Language", rag.language)
                    DetailRow("Version", rag.version)
                    DetailRow("Created", formatDate(rag.createdAt))
                    rag.lastLoadedAt?.let {
                        DetailRow("Last Loaded", formatDate(it))
                    }
                }
            }

            // Tags
            if (rag.getTagsList().isNotEmpty()) {
                Spacer(modifier = Modifier.height(rDp(16.dp)))
                Text(
                    text = "Tags",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(rDp(8.dp)))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(rDp(8.dp)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    rag.getTagsList().forEach { tag ->
                        TagChip(tag = tag)
                    }
                }
            }

            Spacer(modifier = Modifier.height(rDp(24.dp)))

            // Actions
            Column(verticalArrangement = Arrangement.spacedBy(rDp(12.dp))) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(rDp(12.dp))
                ) {
                    when (rag.status) {
                        RagStatus.LOADED -> {
                            Button(
                                onClick = onUnload,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null)
                                Spacer(modifier = Modifier.width(rDp(8.dp)))
                                Text("Unload")
                            }
                        }
                        RagStatus.LOADING -> {
                            Button(
                                onClick = {},
                                modifier = Modifier.weight(1f),
                                enabled = false
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(rDp(20.dp)),
                                    strokeWidth = rDp(2.dp)
                                )
                                Spacer(modifier = Modifier.width(rDp(8.dp)))
                                Text("Loading...")
                            }
                        }
                        else -> {
                            Button(
                                onClick = onLoad,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(rDp(8.dp)))
                                Text("Load")
                            }
                        }
                    }

                    Button(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(rDp(8.dp)))
                        Text("Delete")
                    }
                }

                Button(
                    onClick = onShare,
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(rDp(8.dp)))
                    Text("Share RAG")
                }
            }

            Spacer(modifier = Modifier.height(rDp(24.dp)))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = rDp(4.dp)),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun getRagSourceIcon(sourceType: RagSourceType): ImageVector = when (sourceType) {
    RagSourceType.TEXT -> Icons.Default.Book
    RagSourceType.CHAT -> Icons.Default.Chat
    RagSourceType.FILE -> Icons.Default.Description
    RagSourceType.MEDICAL_TEXT -> Icons.Default.Description  // Legacy support
    RagSourceType.NEURON_PACKET -> Icons.Default.Memory
    RagSourceType.MEMORY_VAULT -> Icons.Default.Storage
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Composable
private fun PasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.Lock, contentDescription = null)
        },
        title = { Text("Enter Password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(rDp(12.dp))) {
                Text(
                    text = "This RAG is encrypted. Enter the password to load it.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (showPassword)
                        VisualTransformation.None
                    else
                        PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(rDp(12.dp))
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(password) },
                enabled = password.isNotBlank()
            ) {
                Text("Load")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}