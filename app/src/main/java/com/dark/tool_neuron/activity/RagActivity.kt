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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.SecondaryTabRow
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
import com.dark.tool_neuron.ui.components.PasswordTextField
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.R
import com.dark.tool_neuron.global.formatDateWithTime
import com.dark.tool_neuron.models.table_schema.InstalledRag
import com.dark.tool_neuron.models.table_schema.RagSourceType
import com.dark.tool_neuron.models.table_schema.RagStatus
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.ActionTextButton
import com.dark.tool_neuron.ui.screen.rag.SecureRagCreationScreen
import com.dark.tool_neuron.ui.theme.NeuroVerseTheme
import com.dark.tool_neuron.viewmodel.RagViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.dark.tool_neuron.ui.icons.TnIcons

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RagScreen(
    ragViewModel: RagViewModel = hiltViewModel(),
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }

    // Auto-initialize embedding engine when Create tab is selected
    LaunchedEffect(selectedTab) {
        if (selectedTab == 2 && !ragViewModel.isEmbeddingReady) {
            ragViewModel.initializeEmbeddingFromFiles()
        }
    }

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
            android.widget.Toast.makeText(
                context,
                "Importing RAG package...",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Show success message when a RAG is imported
    LaunchedEffect(installedCount) {
        // This will trigger when installed count changes (new RAG added)
        if (installedCount > 0) {
            // Don't show on initial load
            kotlinx.coroutines.delay(500)
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
                    ActionTextButton(
                        onClickListener = onClose,
                        icon = TnIcons.ChevronLeft,
                        text = "Back",
                        contentDescription = "Close",
                        shape = RoundedCornerShape(12.dp)
                    )
                },
                actions = {
                    // Import Neuron Button
                    ActionButton(
                        onClickListener = {
                            ragFilePicker.launch(arrayOf(
                                "application/octet-stream",
                                "application/x-neuron",
                                "*/*"
                            ))
                        },
                        icon = TnIcons.Download,
                        contentDescription = "Import Neuron Package",
                        shape = RoundedCornerShape(12.dp)
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
            SecondaryTabRow(
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
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator(modifier = Modifier.size(24.dp))
                }
            }

            // Error display
            error?.let { errorMsg ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = errorMsg,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        ActionButton(
                            onClickListener = { ragViewModel.clearError() },
                            icon = TnIcons.X,
                            contentDescription = "Dismiss",
                            shape = RoundedCornerShape(8.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(0.3f),
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        )
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
                        onShare = { rag -> shareRag(context, rag) },
                        onViewData = { rag -> openRagDataReader(context, rag) }
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
                        onShare = { rag -> shareRag(context, rag) },
                        onViewData = { rag -> openRagDataReader(context, rag) }
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
                ragToLoad?.let { rag ->
                    ragViewModel.loadRag(rag, password)
                }
                showPasswordDialog = false
                ragToLoad = null
            }
        )
    }
}

private fun openRagDataReader(context: android.content.Context, rag: InstalledRag) {
    if (rag.filePath == null) {
        android.widget.Toast.makeText(context, "RAG file path not found", android.widget.Toast.LENGTH_SHORT).show()
        return
    }

    val intent = Intent(context, RagDataReaderActivity::class.java).apply {
        putExtra(RagDataReaderActivity.EXTRA_RAG_FILE_PATH, rag.filePath)
        putExtra(RagDataReaderActivity.EXTRA_RAG_NAME, rag.name)
        putExtra(RagDataReaderActivity.EXTRA_IS_ENCRYPTED, rag.isEncrypted)

        // For now, don't pass password - user will need to re-enter it
        // This is for security reasons
    }
    context.startActivity(intent)
}

private fun shareRag(context: android.content.Context, rag: InstalledRag) {
    if (rag.filePath == null) {
        android.widget.Toast.makeText(context, "RAG file path not found", android.widget.Toast.LENGTH_SHORT).show()
        return
    }

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
    onShare: ((InstalledRag) -> Unit)? = null,
    onViewData: ((InstalledRag) -> Unit)? = null
) {
    if (rags.isEmpty()) {
        EmptyRagListState(message = emptyMessage, subMessage = emptySubMessage)
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(rags, key = { it.id }) { rag ->
                RagCard(
                    rag = rag,
                    onClick = { onRagClick(rag) },
                    onToggleEnabled = { onToggleEnabled(rag.id, it) },
                    onLoad = { onLoad(rag.id) },
                    onUnload = { onUnload(rag.id) },
                    onDelete = { onDelete(rag.id) },
                    onShare = onShare?.let { { it(rag) } },
                    onViewData = onViewData?.let { { it(rag) } }
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                TnIcons.Cpu,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
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
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RagCard(
    rag: InstalledRag,
    onClick: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
    onDelete: () -> Unit,
    onShare: (() -> Unit)? = null,
    onViewData: (() -> Unit)? = null
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
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = getRagSourceIcon(rag.sourceType),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                Spacer(modifier = Modifier.height(8.dp))
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
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rag.getTagsList().take(4).forEach { tag ->
                        TagChip(tag = tag)
                    }
                }
            }

            // Actions
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDateWithTime(rag.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (rag.status) {
                        RagStatus.LOADED -> {
                            ActionTextButton(
                                onClickListener = onUnload,
                                icon = TnIcons.X,
                                text = "Unload",
                                contentDescription = "Unload RAG",
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                        RagStatus.LOADING -> {
                            LoadingIndicator(
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        else -> {
                            ActionTextButton(
                                onClickListener = onLoad,
                                icon = TnIcons.Download,
                                text = "Load",
                                contentDescription = "Load RAG",
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }

                    onViewData?.let { viewDataAction ->
                        ActionButton(
                            onClickListener = viewDataAction,
                            icon = TnIcons.Eye,
                            contentDescription = "View Data",
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    onShare?.let { shareAction ->
                        ActionButton(
                            onClickListener = shareAction,
                            icon = TnIcons.Share,
                            contentDescription = "Share",
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    ActionButton(
                        onClickListener = onDelete,
                        icon = TnIcons.Trash,
                        contentDescription = "Delete",
                        shape = RoundedCornerShape(8.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(0.1f),
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    )
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
        shape = RoundedCornerShape(4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (status == RagStatus.LOADED) {
                Icon(
                    TnIcons.Check,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
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
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = tag,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
                .padding(24.dp)
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

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Description
            if (rag.description.isNotBlank()) {
                Text(
                    text = "Description",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = rag.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Stats
            Text(
                text = "Statistics",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DetailRow("Nodes", "${rag.nodeCount}")
                    DetailRow("Embedding Dimension", "${rag.embeddingDimension}")
                    DetailRow("Size", rag.getFormattedSize())
                    DetailRow("Domain", rag.domain)
                    DetailRow("Language", rag.language)
                    DetailRow("Version", rag.version)
                    DetailRow("Created", formatDateWithTime(rag.createdAt))
                    rag.lastLoadedAt?.let {
                        DetailRow("Last Loaded", formatDateWithTime(it))
                    }
                }
            }

            // Tags
            if (rag.getTagsList().isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Tags",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    rag.getTagsList().forEach { tag ->
                        TagChip(tag = tag)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Actions
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (rag.status) {
                        RagStatus.LOADED -> {
                            FilledTonalButton(
                                onClick = onUnload,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(0.1f),
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(TnIcons.X, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Unload")
                            }
                        }
                        RagStatus.LOADING -> {
                            FilledTonalButton(
                                onClick = {},
                                modifier = Modifier.weight(1f),
                                enabled = false,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                LoadingIndicator(
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Loading...")
                            }
                        }
                        else -> {
                            FilledTonalButton(
                                onClick = onLoad,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(0.1f),
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(TnIcons.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Load")
                            }
                        }
                    }

                    FilledTonalButton(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(0.1f),
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(TnIcons.Trash, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete")
                    }
                }

                FilledTonalButton(
                    onClick = onShare,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(0.5f),
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(TnIcons.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share RAG")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
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
    RagSourceType.TEXT -> TnIcons.Books
    RagSourceType.CHAT -> TnIcons.MessageCircle
    RagSourceType.FILE -> TnIcons.FileText
    RagSourceType.MEDICAL_TEXT -> TnIcons.FileText  // Legacy support
    RagSourceType.NEURON_PACKET -> TnIcons.Cpu
    RagSourceType.MEMORY_VAULT -> TnIcons.Database
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
            Icon(TnIcons.Lock, contentDescription = null)
        },
        title = { Text("Enter Password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "This RAG is encrypted. Enter the password to load it.",
                    style = MaterialTheme.typography.bodyMedium
                )
                PasswordTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    showPasswordState = showPassword,
                    onToggleVisibility = { showPassword = !showPassword }
                )
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = { onConfirm(password) },
                enabled = password.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(0.1f),
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Load")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}