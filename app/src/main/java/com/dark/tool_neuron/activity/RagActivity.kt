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
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.Close
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
import com.dark.tool_neuron.ui.theme.NeuroVerseTheme
import com.dark.tool_neuron.ui.theme.rDp
import com.dark.tool_neuron.viewmodel.RagViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
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
                        onLoad = { ragViewModel.loadRag(it) },
                        onUnload = { ragViewModel.unloadRag(it) },
                        onDelete = { ragViewModel.deleteRag(it) }
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
                        onLoad = { ragViewModel.loadRag(it) },
                        onUnload = { ragViewModel.unloadRag(it) },
                        onDelete = { ragViewModel.deleteRag(it) }
                    )
                    2 -> CreateRagContent(
                        ragViewModel = ragViewModel,
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
                onLoad = { ragViewModel.loadRag(rag.id) },
                onUnload = { ragViewModel.unloadRag(rag.id) },
                onDelete = {
                    ragViewModel.deleteRag(rag.id)
                    showDetailSheet = false
                    selectedRagDetail = null
                }
            )
        }
    }
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
    onDelete: (String) -> Unit
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
                    onDelete = { onDelete(rag.id) }
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
    onDelete: () -> Unit
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

@Composable
private fun CreateRagContent(
    ragViewModel: RagViewModel,
    onRagCreated: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedSourceType by remember { mutableStateOf<RagSourceType?>(null) }
    var ragName by remember { mutableStateOf("") }
    var ragDescription by remember { mutableStateOf("") }
    var ragContent by remember { mutableStateOf("") }
    var ragDomain by remember { mutableStateOf("general") }
    var ragTags by remember { mutableStateOf("") }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var filePreviewContent by remember { mutableStateOf<String?>(null) }
    var selectedChatId by remember { mutableStateOf<String?>(null) }
    var selectedChatMessages by remember { mutableStateOf<List<com.dark.tool_neuron.models.messages.Messages>>(emptyList()) }

    val isLoading by ragViewModel.isLoading.collectAsStateWithLifecycle()
    val availableChats by ragViewModel.availableChats.collectAsStateWithLifecycle()
    val embeddingStatus by ragViewModel.embeddingStatus.collectAsStateWithLifecycle()
    val isEmbeddingInitialized by ragViewModel.isEmbeddingInitialized.collectAsStateWithLifecycle()

    // Load chats when Chat source type is selected
    LaunchedEffect(selectedSourceType) {
        if (selectedSourceType == RagSourceType.CHAT) {
            ragViewModel.loadAvailableChats()
        }
    }

    // Read file content when file is selected
    LaunchedEffect(selectedFileUri) {
        selectedFileUri?.let { uri ->
            filePreviewContent = ragViewModel.readFileContent(uri)
        }
    }

    // File picker for file-based RAG creation
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            selectedFileUri = uri
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(rDp(16.dp)),
        verticalArrangement = Arrangement.spacedBy(rDp(16.dp))
    ) {
        // Header
        item {
            Text(
                text = "Create New RAG",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(rDp(4.dp)))
            Text(
                text = "Select a source type and provide content to create a RAG package",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Embedding Status Banner
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (isEmbeddingInitialized)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                shape = RoundedCornerShape(rDp(8.dp))
            ) {
                Row(
                    modifier = Modifier.padding(rDp(12.dp)),
                    horizontalArrangement = Arrangement.spacedBy(rDp(8.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isEmbeddingInitialized) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(rDp(20.dp)),
                        tint = if (isEmbeddingInitialized)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Embedding Model",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = embeddingStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!isEmbeddingInitialized) {
                        TextButton(onClick = { ragViewModel.initializeEmbeddingFromAssets() }) {
                            Text("Initialize")
                        }
                    }
                }
            }
        }

        // Source Type Selection
        item {
            Text(
                text = "Source Type",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(rDp(8.dp)))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
            ) {
                SourceTypeCard(
                    title = "Text",
                    icon = Icons.Default.Book,
                    description = "Plain text content",
                    isSelected = selectedSourceType == RagSourceType.TEXT,
                    onClick = { selectedSourceType = RagSourceType.TEXT },
                    modifier = Modifier.weight(1f)
                )
                SourceTypeCard(
                    title = "File",
                    icon = Icons.Default.Description,
                    description = "From text file",
                    isSelected = selectedSourceType == RagSourceType.FILE,
                    onClick = { selectedSourceType = RagSourceType.FILE },
                    modifier = Modifier.weight(1f)
                )
                SourceTypeCard(
                    title = "Chat",
                    icon = Icons.Default.Chat,
                    description = "From conversations",
                    isSelected = selectedSourceType == RagSourceType.CHAT,
                    onClick = { selectedSourceType = RagSourceType.CHAT },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Form Fields
        item {
            AnimatedVisibility(visible = selectedSourceType != null) {
                Column(verticalArrangement = Arrangement.spacedBy(rDp(16.dp))) {
                    OutlinedTextField(
                        value = ragName,
                        onValueChange = { ragName = it },
                        label = { Text("Name") },
                        placeholder = { Text("Enter RAG name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = ragDescription,
                        onValueChange = { ragDescription = it },
                        label = { Text("Description") },
                        placeholder = { Text("Brief description of this RAG") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4
                    )

                    OutlinedTextField(
                        value = ragDomain,
                        onValueChange = { ragDomain = it },
                        label = { Text("Domain") },
                        placeholder = { Text("e.g., general, technical, personal") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = ragTags,
                        onValueChange = { ragTags = it },
                        label = { Text("Tags (comma separated)") },
                        placeholder = { Text("tag1, tag2, tag3") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Content input based on source type
                    when (selectedSourceType) {
                        RagSourceType.TEXT -> {
                            OutlinedTextField(
                                value = ragContent,
                                onValueChange = { ragContent = it },
                                label = { Text("Content") },
                                placeholder = { Text("Enter or paste your text content here...") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 6,
                                maxLines = 12
                            )
                        }
                        RagSourceType.FILE -> {
                            Column(verticalArrangement = Arrangement.spacedBy(rDp(8.dp))) {
                                Button(
                                    onClick = { filePicker.launch(arrayOf("text/*", "application/pdf")) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Description, contentDescription = null)
                                    Spacer(modifier = Modifier.width(rDp(8.dp)))
                                    Text(if (selectedFileUri != null) "Change File" else "Select File")
                                }

                                selectedFileUri?.let { uri ->
                                    Text(
                                        text = "Selected: ${uri.lastPathSegment}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    // File content preview
                                    filePreviewContent?.let { content ->
                                        Text(
                                            text = "Preview",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(rDp(8.dp))
                                        ) {
                                            Column(modifier = Modifier.padding(rDp(12.dp))) {
                                                Text(
                                                    text = content.take(1000) + if (content.length > 1000) "..." else "",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 15,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (content.length > 1000) {
                                                    Spacer(modifier = Modifier.height(rDp(4.dp)))
                                                    Text(
                                                        text = "${content.length} characters total",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        RagSourceType.CHAT -> {
                            Column(verticalArrangement = Arrangement.spacedBy(rDp(8.dp))) {
                                Text(
                                    text = "Select a Chat",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )

                                if (availableChats.isEmpty()) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(rDp(8.dp))
                                    ) {
                                        Text(
                                            text = "No chats available. Start a conversation first.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(rDp(16.dp)),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    availableChats.forEach { chat ->
                                        ChatSelectionCard(
                                            chatInfo = chat,
                                            isSelected = selectedChatId == chat.chatId,
                                            onClick = {
                                                selectedChatId = chat.chatId
                                                scope.launch {
                                                    selectedChatMessages = ragViewModel.getChatMessages(chat.chatId)
                                                    if (ragName.isBlank()) {
                                                        ragName = chat.chatId.ifBlank { "Chat ${chat.chatId.take(8)}" }
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }

                                // Show selected chat preview
                                if (selectedChatId != null && selectedChatMessages.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(rDp(8.dp)))
                                    Text(
                                        text = "Chat Preview (${selectedChatMessages.size} messages)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(rDp(8.dp))
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(rDp(12.dp)),
                                            verticalArrangement = Arrangement.spacedBy(rDp(8.dp))
                                        ) {
                                            selectedChatMessages.take(5).forEach { msg ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
                                                ) {
                                                    Text(
                                                        text = if (msg.role == com.dark.tool_neuron.models.messages.Role.User) "You:" else "AI:",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (msg.role == com.dark.tool_neuron.models.messages.Role.User)
                                                            MaterialTheme.colorScheme.primary
                                                        else
                                                            MaterialTheme.colorScheme.tertiary
                                                    )
                                                    Text(
                                                        text = msg.content.content.take(100) + if (msg.content.content.length > 100) "..." else "",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                            if (selectedChatMessages.size > 5) {
                                                Text(
                                                    text = "... and ${selectedChatMessages.size - 5} more messages",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        else -> {}
                    }
                }
            }
        }

        // Create Button
        item {
            AnimatedVisibility(visible = selectedSourceType != null) {
                val canCreate = !isLoading && ragName.isNotBlank() && isEmbeddingInitialized && when (selectedSourceType) {
                    RagSourceType.TEXT -> ragContent.isNotBlank()
                    RagSourceType.FILE -> selectedFileUri != null
                    RagSourceType.CHAT -> selectedChatId != null && selectedChatMessages.isNotEmpty()
                    else -> false
                }

                Button(
                    onClick = {
                        val tags = ragTags.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        when (selectedSourceType) {
                            RagSourceType.TEXT -> {
                                ragViewModel.createRagFromText(
                                    name = ragName,
                                    description = ragDescription,
                                    text = ragContent,
                                    domain = ragDomain,
                                    tags = tags
                                ) { result ->
                                    if (result.isSuccess) {
                                        onRagCreated()
                                    }
                                }
                            }
                            RagSourceType.FILE -> {
                                selectedFileUri?.let { uri ->
                                    ragViewModel.createRagFromFile(
                                        name = ragName,
                                        description = ragDescription,
                                        fileUri = uri,
                                        domain = ragDomain,
                                        tags = tags
                                    ) { result ->
                                        if (result.isSuccess) {
                                            onRagCreated()
                                        }
                                    }
                                }
                            }
                            RagSourceType.CHAT -> {
                                selectedChatId?.let { chatId ->
                                    ragViewModel.createRagFromChat(
                                        name = ragName,
                                        description = ragDescription,
                                        chatId = chatId,
                                        messages = selectedChatMessages,
                                        domain = ragDomain,
                                        tags = tags
                                    ) { result ->
                                        if (result.isSuccess) {
                                            onRagCreated()
                                        }
                                    }
                                }
                            }
                            else -> {}
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canCreate
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(rDp(20.dp)),
                            strokeWidth = rDp(2.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(rDp(8.dp)))
                        Text("Create RAG")
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatSelectionCard(
    chatInfo: com.dark.tool_neuron.models.vault.ChatInfo,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(rDp(8.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDp(12.dp)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(rDp(12.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Chat,
                    contentDescription = null,
                    modifier = Modifier.size(rDp(24.dp)),
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column {
                    Text(
                        text = chatInfo.chatId.ifBlank { "Chat ${chatInfo.chatId.take(8)}" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${chatInfo.messageCount} messages",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(rDp(20.dp)),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SourceTypeCard(
    title: String,
    icon: ImageVector,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(rDp(12.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDp(16.dp)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(rDp(32.dp)),
                tint = if (isSelected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(rDp(8.dp)))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
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
        CreateRagContent(
            ragViewModel = ragViewModel,
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
    onDelete: () -> Unit
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