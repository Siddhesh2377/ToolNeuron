package com.dark.tool_neuron.ui.screen.files

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import com.dark.tool_neuron.R
import com.dark.tool_neuron.models.enums.ProviderType
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.CuteToggle
import com.dark.tool_neuron.ui.theme.rDp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow
import com.dark.tool_neuron.ui.icons.TnIcons

enum class PickerMode {
    FILE,      // Pick .gguf files
    FOLDER     // Pick directories (for diffusion models)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerScreen(
    finishWithPath: (String, ProviderType) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val rootPath = remember { Environment.getExternalStorageDirectory().absolutePath }

    var pickerMode by rememberSaveable { mutableStateOf(PickerMode.FILE) }
    var hasAllFiles by remember { mutableStateOf(hasAllFilesAccess()) }
    var currentPath by rememberSaveable { mutableStateOf(rootPath) }
    var listState by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var detailTarget by remember { mutableStateOf<FileItem?>(null) }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    LaunchedEffect(hasAllFiles, currentPath, pickerMode) {
        if (hasAllFiles) {
            loading = true
            error = null
            listState = try {
                withContext(Dispatchers.IO) {
                    listChildrenFiltered(currentPath, pickerMode)
                }
            } catch (t: Throwable) {
                error = t.message
                emptyList()
            }
            loading = false
        }
    }

    BackHandler(true) {
        if (currentPath != rootPath) {
            currentPath = File(currentPath).parentFile?.absolutePath ?: rootPath
        } else onClose()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentPath != rootPath) {
                            currentPath = File(currentPath).parentFile?.absolutePath ?: rootPath
                        } else onClose()
                    }) {
                        Icon(TnIcons.ArrowLeft, "Back")
                    }
                },
                title = { PathBreadcrumbText(currentPath) },
                actions = {
                    if (!hasAllFiles) {
                        ActionButton(
                            onClickListener = { openAllFilesAccessSettings(context) },
                            icon = TnIcons.Settings,
                            contentDescription = "Grant access"
                        )
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(rDp(8.dp)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Mode toggle using CuteToggle
                            CuteToggle(
                                checked = pickerMode == PickerMode.FOLDER,
                                onCheckedChange = { isFolder ->
                                    pickerMode = if (isFolder) PickerMode.FOLDER else PickerMode.FILE
                                },
                                text = if (pickerMode == PickerMode.FILE) "File" else "Folder",
                                icon = if (pickerMode == PickerMode.FILE) TnIcons.Brain else null,
                                iconChecked = TnIcons.Upload
                            )

                            ActionButton(
                                onClickListener = { currentPath = rootPath },
                                icon = TnIcons.Home,
                                contentDescription = "Home"
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { inner ->
        val blurRadius = if (detailTarget != null) 12.dp else 0.dp
        Box(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .blur(blurRadius)
        ) {
            when {
                !hasAllFiles -> PermissionGate(
                    modifier = Modifier.fillMaxSize(),
                    pickerMode = pickerMode
                ) { hasAllFiles = hasAllFilesAccess() }

                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                error != null -> ErrorState(
                    error = error!!,
                    onRetry = { hasAllFiles = hasAllFilesAccess() }
                )

                else -> FileList(
                    modifier = Modifier.fillMaxSize(),
                    items = listState,
                    rootPath = rootPath,
                    currentPath = currentPath,
                    pickerMode = pickerMode,
                    onNavigate = { folder -> currentPath = folder.absolutePath },
                    onPick = { file, type -> finishWithPath(file.absolutePath, type) },
                    onShowInfo = { detailTarget = it }
                )
            }
        }

        detailTarget?.let { item ->
            FileDetailDialog(
                item = item,
                pickerMode = pickerMode,
                onDismiss = { detailTarget = null },
                onSelect = {
                    val type = if (item.isDir) ProviderType.DIFFUSION else ProviderType.GGUF
                    finishWithPath(item.file.absolutePath, type)
                }
            )
        }
    }
}

@Composable
private fun PathBreadcrumbText(path: String) {
    val scroll = rememberScrollState()
    val normalized = remember(path) { path.removeSuffix("/") }
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Crossfade(normalized, label = "path") {
            Text(
                text = it,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun PermissionGate(
    modifier: Modifier = Modifier,
    pickerMode: PickerMode,
    onCheck: () -> Unit
) {
    val ctx = LocalContext.current
    Box(modifier, contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDp(24.dp)),
            shape = RoundedCornerShape(rDp(20.dp)),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = rDp(2.dp)
        ) {
            Column(
                Modifier.padding(rDp(24.dp)),
                verticalArrangement = Arrangement.spacedBy(rDp(12.dp)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    TnIcons.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(rDp(48.dp)),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Storage Access Required",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    when (pickerMode) {
                        PickerMode.FILE -> "Allow 'All files access' to browse and select .gguf model files"
                        PickerMode.FOLDER -> "Allow 'All files access' to browse and select model folders"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(rDp(8.dp)))
                Row(horizontalArrangement = Arrangement.spacedBy(rDp(12.dp))) {
                    ActionButton(
                        onClickListener = { openAllFilesAccessSettings(ctx) },
                        icon = TnIcons.Settings,
                        contentDescription = "Open Settings"
                    )
                    ActionButton(
                        onClickListener = onCheck,
                        icon = TnIcons.Refresh,
                        contentDescription = "Refresh"
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorState(error: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.padding(rDp(24.dp)),
            shape = RoundedCornerShape(rDp(20.dp)),
            color = MaterialTheme.colorScheme.errorContainer
        ) {
            Column(
                Modifier.padding(rDp(24.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
            ) {
                Text(
                    "Error",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                ActionButton(
                    onClickListener = onRetry,
                    icon = TnIcons.Refresh,
                    contentDescription = "Retry"
                )
            }
        }
    }
}

@Composable
private fun FileList(
    modifier: Modifier,
    items: List<FileItem>,
    rootPath: String,
    currentPath: String,
    pickerMode: PickerMode,
    onNavigate: (File) -> Unit,
    onPick: (File, ProviderType) -> Unit,
    onShowInfo: (FileItem) -> Unit
) {
    val parent = File(currentPath).parentFile
    LazyColumn(
        modifier = modifier.padding(horizontal = rDp(12.dp)),
        verticalArrangement = Arrangement.spacedBy(rDp(6.dp))
    ) {
        if (parent != null && currentPath != rootPath) {
            item("..parent") {
                FileListItem(
                    icon = {
                        Icon(
                            TnIcons.Folder,
                            null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    title = "..",
                    subtitle = "Parent folder",
                    trailing = {
                        ActionButton(
                            onClickListener = { onNavigate(parent) },
                            icon = TnIcons.ArrowUp,
                            contentDescription = "Go up",
                            modifier = Modifier.size(rDp(32.dp))
                        )
                    }
                )
            }
        }

        items(items, key = { it.file.absolutePath }) { item ->
            val canSelect = when (pickerMode) {
                PickerMode.FILE -> !item.isDir
                PickerMode.FOLDER -> item.isDir
            }

            FileListItem(
                icon = {
                    Icon(
                        imageVector = if (item.isDir) TnIcons.Upload else TnIcons.Brain,
                        contentDescription = null,
                        tint = if (canSelect) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(rDp(20.dp))
                    )
                },
                title = item.name,
                subtitle = if (item.isDir) "Folder" else humanSize(item.size),
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))) {
                        // Info button for all items
                        if (canSelect) {
                            ActionButton(
                                onClickListener = { onShowInfo(item) },
                                icon = TnIcons.InfoCircle,
                                contentDescription = "Info",
                                modifier = Modifier.size(rDp(32.dp))
                            )
                        }

                        // Action button based on type and mode
                        when {
                            // Navigate into folder in FILE mode
                            item.isDir && pickerMode == PickerMode.FILE -> {
                                ActionButton(
                                    onClickListener = { onNavigate(item.file) },
                                    icon = TnIcons.ChevronRight,
                                    contentDescription = "Open",
                                    modifier = Modifier.size(rDp(32.dp))
                                )
                            }
                            // Select folder in FOLDER mode
                            item.isDir && pickerMode == PickerMode.FOLDER -> {
                                ActionButton(
                                    onClickListener = { onPick(item.file, ProviderType.DIFFUSION) },
                                    icon = TnIcons.Check,
                                    contentDescription = "Select",
                                    modifier = Modifier.size(rDp(32.dp))
                                )
                            }
                            // Select file in FILE mode
                            !item.isDir && pickerMode == PickerMode.FILE -> {
                                ActionButton(
                                    onClickListener = { onPick(item.file, ProviderType.GGUF) },
                                    icon = TnIcons.Check,
                                    contentDescription = "Select",
                                    modifier = Modifier.size(rDp(32.dp))
                                )
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun FileListItem(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(rDp(12.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = rDp(14.dp), vertical = rDp(12.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(rDp(32.dp))
                    .clip(RoundedCornerShape(rDp(8.dp)))
                    .background(MaterialTheme.colorScheme.primary.copy(0.08f)),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Spacer(Modifier.width(rDp(12.dp)))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(rDp(8.dp)))
            trailing()
        }
    }
}

@Composable
private fun FileDetailDialog(
    item: FileItem,
    pickerMode: PickerMode,
    onDismiss: () -> Unit,
    onSelect: () -> Unit
) {
    var quickSha by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(item.file) {
        if (!item.isDir) {
            quickSha = computeSha256Prefix(item.file, limitBytes = 4L * 1024 * 1024)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(rDp(24.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = rDp(3.dp)
        ) {
            Column(Modifier.padding(rDp(24.dp))) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        when (pickerMode) {
                            PickerMode.FILE -> "Model File Details"
                            PickerMode.FOLDER -> "Model Folder Details"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    ActionButton(
                        onClickListener = onDismiss,
                        icon = TnIcons.X,
                        contentDescription = "Close",
                        modifier = Modifier.size(rDp(32.dp))
                    )
                }

                Spacer(Modifier.height(rDp(16.dp)))

                DetailInfoCard {
                    InfoRow("Name", item.name)
                    InfoRow("Type", if (item.isDir) "Folder" else "File")
                    if (!item.isDir) {
                        InfoRow("Size", "${humanSize(item.size)} (${item.size} B)")
                    }
                    InfoRow("Permissions", "R: ${item.file.canRead()} / W: ${item.file.canWrite()}")

                    if (!item.isDir) {
                        guessQuant(item.name)?.let { InfoRow("Quantization", it) }
                        quickSha?.let { InfoRow("SHA-256 (4MB)", it) }
                    } else {
                        val contents = item.file.listFiles()
                        if (contents != null) {
                            InfoRow("Contents", "${contents.size} items")
                        }
                    }
                }

                Spacer(Modifier.height(rDp(20.dp)))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(rDp(12.dp))
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(rDp(48.dp)),
                        shape = RoundedCornerShape(rDp(12.dp))
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = onSelect,
                        modifier = Modifier
                            .weight(1f)
                            .height(rDp(48.dp)),
                        shape = RoundedCornerShape(rDp(12.dp))
                    ) {
                        Icon(
                            TnIcons.Check,
                            contentDescription = null,
                            modifier = Modifier.size(rDp(18.dp))
                        )
                        Spacer(Modifier.width(rDp(6.dp)))
                        Text(
                            when (pickerMode) {
                                PickerMode.FILE -> "Load File"
                                PickerMode.FOLDER -> "Load Folder"
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailInfoCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(rDp(16.dp)),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(rDp(16.dp)),
            verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
        ) {
            content()
        }
    }
}

@Composable
private fun InfoRow(k: String, v: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            k,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(rDp(4.dp)))
        Text(
            v,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// Data and utility functions
private data class FileItem(
    val file: File,
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val lastModified: Long
)

private suspend fun listChildrenFiltered(
    path: String,
    pickerMode: PickerMode
): List<FileItem> = withContext(Dispatchers.IO) {
    val dir = File(path)
    val children = dir.listFiles()?.toList().orEmpty()

    children.filter { file ->
        when (pickerMode) {
            PickerMode.FILE -> file.isDirectory || file.extension.equals("gguf", ignoreCase = true)
            PickerMode.FOLDER -> file.isDirectory
        }
    }.sortedWith(
        compareBy({ !it.isDirectory }, { it.name.lowercase(Locale.getDefault()) })
    ).map {
        FileItem(
            file = it,
            name = it.name,
            isDir = it.isDirectory,
            size = if (it.isFile) it.length() else 0L,
            lastModified = it.lastModified()
        )
    }
}

private fun hasAllFilesAccess(): Boolean = Environment.isExternalStorageManager()

private fun openAllFilesAccessSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
        intent.data = "package:${context.packageName}".toUri()
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            context.startActivity(intent)
        } catch (_: Throwable) {
        }
    }
}

private fun humanSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val group = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceAtMost(units.lastIndex)
    return String.format(
        Locale.getDefault(), "%.1f %s", bytes / 1024.0.pow(group.toDouble()), units[group]
    )
}

private fun guessQuant(name: String): String? {
    val rx = Regex("(?i)Q\\d+[_A-Z0-9]*")
    return rx.find(name)?.value
}

private suspend fun computeSha256Prefix(file: File, limitBytes: Long): String? =
    withContext(Dispatchers.IO) {
        try {
            val md = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(8192)
            var remaining = limitBytes
            FileInputStream(file).use { fis ->
                while (remaining > 0) {
                    val toRead = min(buf.size.toLong(), remaining).toInt()
                    val len = fis.read(buf, 0, toRead)
                    if (len <= 0) break
                    md.update(buf, 0, len)
                    remaining -= len
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Throwable) {
            null
        }
    }