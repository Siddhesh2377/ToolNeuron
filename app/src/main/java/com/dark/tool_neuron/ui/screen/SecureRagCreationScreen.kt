package com.dark.tool_neuron.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.models.table_schema.RagSourceType
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.ActionTextButton
import com.dark.tool_neuron.ui.components.ActionToggleButton
import com.dark.tool_neuron.ui.theme.rDp
import com.dark.tool_neuron.util.DocumentParser
import com.dark.tool_neuron.viewmodel.RagViewModel
import com.neuronpacket.LoadingMode
import com.neuronpacket.Permission
import com.neuronpacket.UserCredentials
import kotlinx.coroutines.launch

enum class DocumentType(val label: String, val mimeTypes: Array<String>, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    TEXT("Text", arrayOf("text/plain"), Icons.Default.Description),
    PDF("PDF", arrayOf(DocumentParser.MimeTypes.PDF), Icons.AutoMirrored.Filled.InsertDriveFile),
    WORD("Word", arrayOf(DocumentParser.MimeTypes.DOCX, DocumentParser.MimeTypes.DOC), Icons.Default.Description),
    EXCEL("Excel", arrayOf(DocumentParser.MimeTypes.XLSX, DocumentParser.MimeTypes.XLS), Icons.Default.GridOn),
    EPUB("EPUB", arrayOf(DocumentParser.MimeTypes.EPUB), Icons.AutoMirrored.Filled.MenuBook)
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

@OptIn(ExperimentalMaterial3Api::class)
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

    val embeddingStatus by ragViewModel.embeddingStatus.collectAsStateWithLifecycle()
    val isEmbeddingReady by ragViewModel.isEmbeddingInitialized.collectAsStateWithLifecycle()

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            state = state.copy(fileUri = uri)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .imePadding(),
        contentPadding = PaddingValues(rDp(16.dp)),
        verticalArrangement = Arrangement.spacedBy(rDp(16.dp))
    ) {
        item {
            EmbeddingStatusCard(
                isReady = isEmbeddingReady,
                status = embeddingStatus,
                onInitialize = { ragViewModel.initializeEmbeddingFromFiles() }
            )
        }

        item {
            Text(
                "Source Type",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(rDp(8.dp)))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
            ) {
                SourceTypeButton(
                    title = "Text",
                    icon = Icons.Default.Description,
                    isSelected = state.sourceType == RagSourceType.TEXT,
                    onClick = { state = state.copy(sourceType = RagSourceType.TEXT) },
                    modifier = Modifier.weight(1f)
                )
                SourceTypeButton(
                    title = "File",
                    icon = Icons.Default.Folder,
                    isSelected = state.sourceType == RagSourceType.FILE,
                    onClick = { state = state.copy(sourceType = RagSourceType.FILE) },
                    modifier = Modifier.weight(1f)
                )
                SourceTypeButton(
                    title = "Chat",
                    icon = Icons.AutoMirrored.Filled.Chat,
                    isSelected = state.sourceType == RagSourceType.CHAT,
                    onClick = { state = state.copy(sourceType = RagSourceType.CHAT) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (state.sourceType != null) {
            item {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { state = state.copy(name = it) },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(rDp(12.dp))
                )
            }

            item {
                OutlinedTextField(
                    value = state.description,
                    onValueChange = { state = state.copy(description = it) },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    shape = RoundedCornerShape(rDp(12.dp))
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
                ) {
                    OutlinedTextField(
                        value = state.domain,
                        onValueChange = { state = state.copy(domain = it) },
                        label = { Text("Domain") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(rDp(12.dp))
                    )
                    OutlinedTextField(
                        value = state.tags,
                        onValueChange = { state = state.copy(tags = it) },
                        label = { Text("Tags") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(rDp(12.dp))
                    )
                }
            }

            when (state.sourceType) {
                RagSourceType.TEXT -> {
                    item {
                        OutlinedTextField(
                            value = state.content,
                            onValueChange = { state = state.copy(content = it) },
                            label = { Text("Content") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 6,
                            shape = RoundedCornerShape(rDp(12.dp))
                        )
                    }
                }
                RagSourceType.FILE -> {
                    item {
                        Text(
                            "Document Type",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(rDp(8.dp)))

                        // Document type selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
                        ) {
                            DocumentType.entries.take(3).forEach { docType ->
                                FilterChip(
                                    selected = state.selectedDocumentType == docType,
                                    onClick = {
                                        state = state.copy(
                                            selectedDocumentType = docType,
                                            fileUri = null // Reset file selection when type changes
                                        )
                                    },
                                    label = { Text(docType.label) },
                                    leadingIcon = {
                                        Icon(
                                            docType.icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(rDp(16.dp))
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(rDp(4.dp)))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
                        ) {
                            DocumentType.entries.drop(3).forEach { docType ->
                                FilterChip(
                                    selected = state.selectedDocumentType == docType,
                                    onClick = {
                                        state = state.copy(
                                            selectedDocumentType = docType,
                                            fileUri = null // Reset file selection when type changes
                                        )
                                    },
                                    label = { Text(docType.label) },
                                    leadingIcon = {
                                        Icon(
                                            docType.icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(rDp(16.dp))
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    item {
                        val selectedFileName = state.fileUri?.lastPathSegment ?: "Select ${state.selectedDocumentType.label} File"
                        val mimeType = state.fileUri?.let { context.contentResolver.getType(it) }

                        Column(verticalArrangement = Arrangement.spacedBy(rDp(8.dp))) {
                            ActionTextButton(
                                onClickListener = {
                                    // Launch file picker with only the selected document type
                                    filePicker.launch(state.selectedDocumentType.mimeTypes)
                                },
                                icon = state.selectedDocumentType.icon,
                                text = selectedFileName,
                                shape = RoundedCornerShape(rDp(12.dp)),
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (state.fileUri != null) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    ),
                                    shape = RoundedCornerShape(rDp(8.dp))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(rDp(12.dp)),
                                        horizontalArrangement = Arrangement.spacedBy(rDp(8.dp)),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "File: ${state.fileUri?.lastPathSegment}",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                "Type: ${DocumentParser.getFileTypeName(mimeType)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {}
            }

            item {
                EncryptionSection(
                    state = state,
                    onStateChange = { state = it },
                    showPasswordVisibility = showPasswordVisibility,
                    onTogglePasswordVisibility = { showPasswordVisibility = !showPasswordVisibility },
                    onAddUser = { showAddUserDialog = true }
                )
            }

            if (state.isCreating) {
                item {
                    CreationProgressCard(
                        progress = state.creationProgress,
                        status = state.creationStatus
                    )
                }
            }

            item {
                val canCreate = !state.isCreating && state.name.isNotBlank() &&
                        isEmbeddingReady && when (state.sourceType) {
                    RagSourceType.TEXT -> state.content.isNotBlank()
                    RagSourceType.FILE -> state.fileUri != null
                    RagSourceType.CHAT -> state.chatId != null
                    else -> false
                } && (!state.isEncrypted || state.adminPassword.isNotBlank())

                ActionTextButton(
                    onClickListener = {
                        scope.launch {
                            state = state.copy(
                                isCreating = true,
                                creationProgress = 0f,
                                creationStatus = "Starting..."
                            )

                            val tags = state.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }

                            if (state.isEncrypted) {
                                when (state.sourceType) {
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
                                                if (result.isSuccess) {
                                                    onRagCreated()
                                                }
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
                                                    if (result.isSuccess) {
                                                        onRagCreated()
                                                    }
                                                }
                                            )
                                        }
                                    }
                                    else -> {
                                        state = state.copy(isCreating = false)
                                    }
                                }
                            } else {
                                when (state.sourceType) {
                                    RagSourceType.TEXT -> {
                                        ragViewModel.createRagFromText(
                                            name = state.name,
                                            description = state.description,
                                            text = state.content,
                                            domain = state.domain,
                                            tags = tags
                                        ) { result ->
                                            state = state.copy(isCreating = false)
                                            if (result.isSuccess) {
                                                onRagCreated()
                                            }
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
                                                if (result.isSuccess) {
                                                    onRagCreated()
                                                }
                                            }
                                        }
                                    }
                                    else -> {
                                        state = state.copy(isCreating = false)
                                    }
                                }
                            }
                        }
                    },
                    icon = Icons.Default.Add,
                    text = if (canCreate) "Create RAG" else "Fill Required Fields",
                    shape = RoundedCornerShape(rDp(12.dp)),
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (canCreate) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
        }
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

@Composable
private fun EmbeddingStatusCard(
    isReady: Boolean,
    status: String,
    onInitialize: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isReady)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(rDp(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDp(12.dp)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(rDp(8.dp)),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    if (isReady) Icons.Default.Check else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isReady)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
                )
                Column {
                    Text(
                        "Embedding Model",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!isReady) {
                TextButton(onClick = onInitialize) {
                    Text("Initialize")
                }
            }
        }
    }
}

@Composable
private fun SourceTypeButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(rDp(80.dp))
            .clickable { onClick() },
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
                .fillMaxSize()
                .padding(rDp(12.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(rDp(24.dp)),
                tint = if (isSelected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(rDp(4.dp)))
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
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
    Column(verticalArrangement = Arrangement.spacedBy(rDp(12.dp))) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Encryption",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Secure your RAG with password-based encryption",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = state.isEncrypted,
                onCheckedChange = { onStateChange(state.copy(isEncrypted = it)) }
            )
        }

        AnimatedVisibility(visible = state.isEncrypted) {
            Column(verticalArrangement = Arrangement.spacedBy(rDp(12.dp))) {
                OutlinedTextField(
                    value = state.adminPassword,
                    onValueChange = { onStateChange(state.copy(adminPassword = it)) },
                    label = { Text("Admin Password") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showPasswordVisibility)
                        VisualTransformation.None
                    else
                        PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = onTogglePasswordVisibility) {
                            Icon(
                                if (showPasswordVisibility)
                                    Icons.Default.VisibilityOff
                                else
                                    Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    shape = RoundedCornerShape(rDp(12.dp))
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
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
                        shape = RoundedCornerShape(rDp(12.dp))
                    ) {
                        Icon(Icons.Default.Storage, contentDescription = null)
                        Spacer(modifier = Modifier.width(rDp(4.dp)))
                        Text(if (state.loadingMode == LoadingMode.EMBEDDED) "Embedded" else "Transient")
                    }

                    OutlinedButton(
                        onClick = onAddUser,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(rDp(12.dp))
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null)
                        Spacer(modifier = Modifier.width(rDp(4.dp)))
                        Text("Add User")
                    }
                }

                if (state.readOnlyUsers.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(rDp(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(rDp(12.dp))) {
                            Text(
                                "Additional Users (${state.readOnlyUsers.size})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(rDp(8.dp)))
                            state.readOnlyUsers.forEach { user ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = rDp(4.dp)),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            user.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            "Password: ${"•".repeat(user.password.length.coerceAtMost(8))}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Surface(
                                        color = if (user.permissions == Permission.ADMIN)
                                            MaterialTheme.colorScheme.primaryContainer
                                        else
                                            MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(rDp(4.dp))
                                    ) {
                                        Text(
                                            user.permissions.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(
                                                horizontal = rDp(8.dp),
                                                vertical = rDp(4.dp)
                                            )
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
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(rDp(12.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDp(16.dp)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(rDp(48.dp))
            )
            Spacer(modifier = Modifier.height(rDp(12.dp)))
            Text(
                status,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            if (progress > 0f) {
                Text(
                    "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
            Column(verticalArrangement = Arrangement.spacedBy(rDp(12.dp))) {
                Text(
                    text = "Add a user who can access this RAG with their own password",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("User Label") },
                    placeholder = { Text("e.g., Reader, Guest, John") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(rDp(12.dp))
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("User Password") },
                    placeholder = { Text("Password for this user") },
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

                Text(
                    text = "Permission Level",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
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

                Text(
                    text = if (selectedPermission == Permission.READ)
                        "Can only read and query the RAG"
                    else
                        "Full admin access",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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