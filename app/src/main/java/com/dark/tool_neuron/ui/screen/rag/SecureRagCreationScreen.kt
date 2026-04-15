package com.dark.tool_neuron.ui.screen.rag

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.models.table_schema.RagSourceType
import com.dark.tool_neuron.util.DocumentParser
import com.dark.tool_neuron.viewmodel.RagViewModel
import com.neuronpacket.LoadingMode
import com.neuronpacket.Permission
import com.neuronpacket.UserCredentials
import kotlinx.coroutines.launch
import com.dark.tool_neuron.ui.components.ExpandCollapseIcon
import com.dark.tool_neuron.ui.components.PasswordTextField
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.global.Standards

enum class DocumentType(val label: String, val mimeTypes: Array<String>, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    TEXT("Text", arrayOf("text/plain"), TnIcons.FileText),
    PDF("PDF", arrayOf(DocumentParser.MimeTypes.PDF), TnIcons.File),
    WORD("Word", arrayOf(DocumentParser.MimeTypes.DOCX, DocumentParser.MimeTypes.DOC), TnIcons.FileText),
    EXCEL("Excel", arrayOf(DocumentParser.MimeTypes.XLSX, DocumentParser.MimeTypes.XLS), TnIcons.LayoutGrid),
    EPUB("EPUB", arrayOf(DocumentParser.MimeTypes.EPUB), TnIcons.Books)
}

data class RagCreationState(
    val name: String = "",
    val description: String = "",
    val content: String = "",
    val domain: String = "general",
    val tags: String = "",
    val sourceType: RagSourceType? = null,
    val fileUri: Uri? = null,
    val chatId: String? = null,
    val selectedDocumentType: DocumentType = DocumentType.TEXT,
    val isEncrypted: Boolean = false,
    val adminPassword: String = "",
    val loadingMode: LoadingMode = LoadingMode.EMBEDDED,
    val readOnlyUsers: List<UserCredentials> = emptyList(),
    val creationProgress: Float = 0f,
    val creationStatus: String = "",
    val isCreating: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SecureRagCreationScreen(
    ragViewModel: RagViewModel,
    padding: PaddingValues,
    onRagCreated: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf(RagCreationState()) }
    var showAddUserDialog by remember { mutableStateOf(false) }
    var showPasswordVisibility by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }

    val embeddingStatus by ragViewModel.embeddingStatus.collectAsStateWithLifecycle()
    val isEmbeddingReady by ragViewModel.isEmbeddingInitialized.collectAsStateWithLifecycle()
    val isEmbeddingDownloaded by ragViewModel.isEmbeddingModelDownloaded.collectAsStateWithLifecycle()
    val isEmbeddingDownloading by ragViewModel.isEmbeddingModelDownloading.collectAsStateWithLifecycle()
    val downloadProgress by ragViewModel.embeddingDownloadProgress.collectAsStateWithLifecycle()

    // Auto-initialize if model is downloaded but not initialized
    LaunchedEffect(isEmbeddingDownloaded) {
        if (isEmbeddingDownloaded && !ragViewModel.isEmbeddingReady) {
            ragViewModel.initializeEmbeddingFromFiles()
        }
    }

    // File picker for all supported formats
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            state = state.copy(fileUri = uri, sourceType = RagSourceType.FILE)
            // Auto-populate name from filename if empty
            if (state.name.isBlank()) {
                val fileName = uri.lastPathSegment
                    ?.substringAfterLast('/')
                    ?.substringBeforeLast('.') ?: ""
                if (fileName.isNotBlank()) {
                    state = state.copy(name = fileName)
                }
            }
            // Auto-detect document type from MIME
            val mimeType = context.contentResolver.getType(uri)
            if (mimeType != null) {
                val detected = DocumentType.entries.find { docType ->
                    docType.mimeTypes.any { it == mimeType }
                }
                if (detected != null) {
                    state = state.copy(selectedDocumentType = detected)
                }
            }
        }
    }

    // Determine effective source type
    val effectiveSourceType = when {
        state.fileUri != null -> RagSourceType.FILE
        state.content.isNotBlank() -> RagSourceType.TEXT
        state.sourceType != null -> state.sourceType
        else -> null
    }

    val canCreate = !state.isCreating && state.name.isNotBlank() &&
            isEmbeddingReady && when (effectiveSourceType) {
        RagSourceType.TEXT -> state.content.isNotBlank()
        RagSourceType.FILE -> state.fileUri != null
        RagSourceType.CHAT -> state.chatId != null
        else -> false
    } && (!state.isEncrypted || state.adminPassword.isNotBlank())

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .imePadding(),
        contentPadding = PaddingValues(horizontal = Standards.SpacingLg, vertical = Standards.SpacingMd),
        verticalArrangement = Arrangement.spacedBy(Standards.SpacingMd)
    ) {
        // Embedding model card — shown when model needs download, is downloading, or needs init
        if (!isEmbeddingReady) {
            item {
                EmbeddingModelCard(
                    isDownloaded = isEmbeddingDownloaded,
                    isDownloading = isEmbeddingDownloading,
                    downloadProgress = downloadProgress,
                    status = embeddingStatus,
                    onDownload = { ragViewModel.startEmbeddingDownload() },
                    onInitialize = { ragViewModel.initializeEmbeddingFromFiles() }
                )
            }
        }

        // Name field
        item {
            OutlinedTextField(
                value = state.name,
                onValueChange = { state = state.copy(name = it) },
                label = { Text("Name") },
                placeholder = { Text("My Knowledge Base") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(Standards.RadiusLg),
                leadingIcon = {
                    Icon(
                        TnIcons.Tag,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }

        // Content text area
        item {
            OutlinedTextField(
                value = state.content,
                onValueChange = {
                    state = state.copy(content = it)
                    if (it.isNotBlank() && state.fileUri != null) {
                        state = state.copy(fileUri = null, sourceType = RagSourceType.TEXT)
                    }
                },
                label = { Text("Content") },
                placeholder = { Text("Paste or type your knowledge here...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                minLines = 4,
                shape = RoundedCornerShape(Standards.RadiusLg)
            )
        }

        // Divider with "or"
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Text(
                    "  or  ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }

        // File drop zone card with dashed border
        item {
            FileDropZone(
                fileUri = state.fileUri,
                documentType = state.selectedDocumentType,
                onPickFile = {
                    filePicker.launch(arrayOf(
                        "text/plain",
                        DocumentParser.MimeTypes.PDF,
                        DocumentParser.MimeTypes.DOCX,
                        DocumentParser.MimeTypes.DOC,
                        DocumentParser.MimeTypes.XLSX,
                        DocumentParser.MimeTypes.XLS,
                        DocumentParser.MimeTypes.EPUB
                    ))
                },
                onClearFile = {
                    state = state.copy(fileUri = null, sourceType = null)
                },
                context = context
            )
        }

        // Creation progress
        if (state.isCreating) {
            item {
                CreationProgressCard(
                    progress = state.creationProgress,
                    status = state.creationStatus
                )
            }
        }

        // Create button
        item {
            Button(
                onClick = {
                    if (!canCreate) return@Button
                    scope.launch {
                        state = state.copy(
                            isCreating = true,
                            creationProgress = 0f,
                            creationStatus = "Starting..."
                        )

                        val finalSourceType = effectiveSourceType ?: return@launch
                        val tags = state.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }

                        if (state.isEncrypted) {
                            when (finalSourceType) {
                                RagSourceType.TEXT -> {
                                    ragViewModel.createSecureRagFromText(
                                        name = state.name,
                                        description = state.description,
                                        text = state.content,
                                        domain = state.domain,
                                        tags = tags,
                                        adminPassword = state.adminPassword,
                                        readOnlyUsers = state.readOnlyUsers,
                                        loadingMode = state.loadingMode,
                                        onProgress = { progress, status ->
                                            state = state.copy(
                                                creationProgress = progress,
                                                creationStatus = status
                                            )
                                        },
                                        onComplete = { result ->
                                            state = state.copy(isCreating = false)
                                            if (result.isSuccess) onRagCreated()
                                        }
                                    )
                                }
                                RagSourceType.FILE -> {
                                    state.fileUri?.let { uri ->
                                        ragViewModel.createSecureRagFromFile(
                                            name = state.name,
                                            description = state.description,
                                            fileUri = uri,
                                            domain = state.domain,
                                            tags = tags,
                                            adminPassword = state.adminPassword,
                                            readOnlyUsers = state.readOnlyUsers,
                                            loadingMode = state.loadingMode,
                                            onProgress = { progress, status ->
                                                state = state.copy(
                                                    creationProgress = progress,
                                                    creationStatus = status
                                                )
                                            },
                                            onComplete = { result ->
                                                state = state.copy(isCreating = false)
                                                if (result.isSuccess) onRagCreated()
                                            }
                                        )
                                    }
                                }
                                else -> state = state.copy(isCreating = false)
                            }
                        } else {
                            when (finalSourceType) {
                                RagSourceType.TEXT -> {
                                    ragViewModel.createRagFromText(
                                        name = state.name,
                                        description = state.description,
                                        text = state.content,
                                        domain = state.domain,
                                        tags = tags
                                    ) { result ->
                                        state = state.copy(isCreating = false)
                                        if (result.isSuccess) onRagCreated()
                                    }
                                }
                                RagSourceType.FILE -> {
                                    state.fileUri?.let { uri ->
                                        ragViewModel.createRagFromFile(
                                            name = state.name,
                                            description = state.description,
                                            fileUri = uri,
                                            domain = state.domain,
                                            tags = tags
                                        ) { result ->
                                            state = state.copy(isCreating = false)
                                            if (result.isSuccess) onRagCreated()
                                        }
                                    }
                                }
                                else -> state = state.copy(isCreating = false)
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = canCreate,
                shape = RoundedCornerShape(Standards.RadiusLg),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            ) {
                Icon(
                    TnIcons.Plus,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(Standards.SpacingSm))
                Text(
                    text = when {
                        state.isCreating -> "Creating..."
                        canCreate -> "Create RAG"
                        state.name.isBlank() && effectiveSourceType == null -> "Enter name & add content"
                        state.name.isBlank() -> "Enter a name"
                        !isEmbeddingReady -> "Embedding not ready"
                        effectiveSourceType == null -> "Add content or pick a file"
                        else -> "Fill required fields"
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Advanced Options toggle
        item {
            Surface(
                onClick = { showAdvanced = !showAdvanced },
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            TnIcons.Adjustments,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Advanced Options",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ExpandCollapseIcon(isExpanded = showAdvanced, size = 20.dp)
                }
            }
        }

        // Advanced section
        if (showAdvanced) {
            item {
                OutlinedTextField(
                    value = state.description,
                    onValueChange = { state = state.copy(description = it) },
                    label = { Text("Description") },
                    placeholder = { Text("What is this RAG about?") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    shape = RoundedCornerShape(Standards.RadiusLg)
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
                ) {
                    OutlinedTextField(
                        value = state.domain,
                        onValueChange = { state = state.copy(domain = it) },
                        label = { Text("Domain") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(Standards.RadiusLg)
                    )
                    OutlinedTextField(
                        value = state.tags,
                        onValueChange = { state = state.copy(tags = it) },
                        label = { Text("Tags") },
                        placeholder = { Text("comma, separated") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(Standards.RadiusLg)
                    )
                }
            }

            // Document type selector (only when file is selected)
            if (state.fileUri != null) {
                item {
                    Text(
                        "Document Type",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DocumentType.entries.forEach { docType ->
                            FilterChip(
                                selected = state.selectedDocumentType == docType,
                                onClick = { state = state.copy(selectedDocumentType = docType) },
                                label = { Text(docType.label, style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = if (state.selectedDocumentType == docType) {
                                    {
                                        Icon(
                                            TnIcons.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else null
                            )
                        }
                    }
                }
            }

            // Encryption section
            item {
                EncryptionSection(
                    state = state,
                    onStateChange = { state = it },
                    showPasswordVisibility = showPasswordVisibility,
                    onTogglePasswordVisibility = { showPasswordVisibility = !showPasswordVisibility },
                    onAddUser = { showAddUserDialog = true }
                )
            }
        }

        // Bottom spacer
        item { Spacer(modifier = Modifier.height(Standards.SpacingLg)) }
    }

    if (showAddUserDialog) {
        AddUserDialog(
            onDismiss = { showAddUserDialog = false },
            onAddUser = { user ->
                state = state.copy(readOnlyUsers = state.readOnlyUsers + user)
                showAddUserDialog = false
            }
        )
    }
}

// ==================== File Drop Zone ====================

@Composable
private fun FileDropZone(
    fileUri: Uri?,
    documentType: DocumentType,
    onPickFile: () -> Unit,
    onClearFile: () -> Unit,
    context: android.content.Context
) {
    val borderColor = if (fileUri != null)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    else
        MaterialTheme.colorScheme.outlineVariant

    val bgColor = if (fileUri != null)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f)
    else
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)

    Surface(
        onClick = if (fileUri == null) onPickFile else ({}),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Standards.RadiusLg),
        color = bgColor,
        border = BorderStroke(
            width = 1.5f.dp,
            color = borderColor
        )
    ) {
        if (fileUri == null) {
            // Empty state — pick file prompt
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp, horizontal = Standards.SpacingLg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
            ) {
                Icon(
                    TnIcons.FileUpload,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
                Text(
                    "Pick a file",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "PDF, Word, Excel, EPUB, or plain text",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        } else {
            // File selected state
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Standards.SpacingMd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // File type icon
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(Standards.RadiusMd)
                ) {
                    Icon(
                        documentType.icon,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(Standards.SpacingSm)
                            .size(22.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // File info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fileUri.lastPathSegment ?: "Selected file",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val mimeType = context.contentResolver.getType(fileUri)
                    Text(
                        text = DocumentParser.getFileTypeName(mimeType),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Change file button
                FilledTonalIconButton(
                    onClick = onPickFile,
                    modifier = Modifier.size(32.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Icon(
                        TnIcons.ArrowsExchange,
                        contentDescription = "Change file",
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Remove file button
                FilledTonalIconButton(
                    onClick = onClearFile,
                    modifier = Modifier.size(32.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        TnIcons.X,
                        contentDescription = "Remove file",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ==================== Supporting Composables ====================

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmbeddingModelCard(
    isDownloaded: Boolean,
    isDownloading: Boolean,
    downloadProgress: Float,
    status: String,
    onDownload: () -> Unit,
    onInitialize: () -> Unit
) {
    val containerColor = when {
        isDownloading -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
        isDownloaded -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val iconTint = when {
        isDownloading -> MaterialTheme.colorScheme.primary
        isDownloaded -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(Standards.RadiusLg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = when {
                            isDownloading -> TnIcons.CloudDownload
                            isDownloaded -> TnIcons.Cpu
                            else -> TnIcons.CloudDownload
                        },
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = iconTint
                    )
                    Column {
                        Text(
                            "Embedding Model",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = when {
                                isDownloading -> status
                                isDownloaded -> "Downloaded — tap Initialize"
                                else -> "Required for RAG features"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                when {
                    isDownloading -> {
                        LoadingIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    isDownloaded -> {
                        FilledTonalButton(
                            onClick = onInitialize,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("Initialize", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    else -> {
                        FilledTonalButton(
                            onClick = onDownload,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                TnIcons.Download,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(Standards.SpacingXs))
                            Text("Download", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // Download progress bar
            if (isDownloading && downloadProgress > 0f) {
                LinearProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            } else if (isDownloading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            }
        }
    }
}

@Composable
private fun EncryptionSection(
    state: RagCreationState,
    onStateChange: (RagCreationState) -> Unit,
    showPasswordVisibility: Boolean,
    onTogglePasswordVisibility: () -> Unit,
    onAddUser: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Encryption",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Password-protect this RAG",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = state.isEncrypted,
                onCheckedChange = { onStateChange(state.copy(isEncrypted = it)) }
            )
        }

        AnimatedVisibility(visible = state.isEncrypted) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PasswordTextField(
                    value = state.adminPassword,
                    onValueChange = { onStateChange(state.copy(adminPassword = it)) },
                    label = "Admin Password",
                    modifier = Modifier.fillMaxWidth(),
                    showPasswordState = showPasswordVisibility,
                    onToggleVisibility = onTogglePasswordVisibility
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
                ) {
                    OutlinedButton(
                        onClick = {
                            onStateChange(state.copy(
                                loadingMode = if (state.loadingMode == LoadingMode.EMBEDDED)
                                    LoadingMode.TRANSIENT
                                else
                                    LoadingMode.EMBEDDED
                            ))
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(Standards.RadiusLg)
                    ) {
                        Icon(TnIcons.Database, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(Standards.SpacingXs))
                        Text(
                            if (state.loadingMode == LoadingMode.EMBEDDED) "Embedded" else "Transient",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    OutlinedButton(
                        onClick = onAddUser,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(Standards.RadiusLg)
                    ) {
                        Icon(TnIcons.UserPlus, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(Standards.SpacingXs))
                        Text("Add User", style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (state.readOnlyUsers.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                "Users (${state.readOnlyUsers.size})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            state.readOnlyUsers.forEach { user ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        user.label,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Surface(
                                        color = if (user.permissions == Permission.ADMIN)
                                            MaterialTheme.colorScheme.primaryContainer
                                        else
                                            MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(Standards.SpacingXs)
                                    ) {
                                        Text(
                                            user.permissions.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = Standards.SpacingXxs)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreationProgressCard(
    progress: Float,
    status: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Standards.SpacingMd)
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(36.dp),
                strokeWidth = 3.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    status,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (progress > 0f) {
                    Text(
                        "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun AddUserDialog(
    onDismiss: () -> Unit,
    onAddUser: (UserCredentials) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var selectedPermission by remember { mutableStateOf(Permission.READ) }
    var showPassword by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add User") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Add a user who can access this RAG",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    placeholder = { Text("e.g., Reader, Guest") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(Standards.RadiusLg)
                )

                PasswordTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password",
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
                ) {
                    FilterChip(
                        selected = selectedPermission == Permission.READ,
                        onClick = { selectedPermission = Permission.READ },
                        label = { Text("Read") }
                    )
                    FilterChip(
                        selected = selectedPermission == Permission.ADMIN,
                        onClick = { selectedPermission = Permission.ADMIN },
                        label = { Text("Admin") }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (label.isNotBlank() && password.isNotBlank()) {
                        onAddUser(UserCredentials(password, label, selectedPermission))
                    }
                },
                enabled = label.isNotBlank() && password.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
